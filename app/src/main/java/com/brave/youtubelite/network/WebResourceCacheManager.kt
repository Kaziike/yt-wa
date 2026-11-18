package com.brave.youtubelite.network

import android.content.Context
import android.util.Log
import android.util.LruCache
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

/**
 * Quản lý lưu đệm tài nguyên tĩnh (Static Asset Cache) 2 tầng (Memory LRU + Disk Cache):
 * - Chống tải trùng lặp đồng thời (Single-flight deduplication) giữa 2 WebView.
 * - Hỗ trợ nén Gzip và kiểm tra Content-Length trước khi đọc để tránh tải đúp các bundle quá khổ.
 * - Xác thực MIME Type nghiêm ngặt nhằm chống lỗi Captive Portal lưu trang HTML lỗi vào cache 7 ngày.
 * - Theo dõi dung lượng đĩa bằng AtomicLong thay vì quét listFiles() trên luồng mạng.
 */
object WebResourceCacheManager {
    private const val TAG = "WebResourceCache"

    @Volatile
    var isEnabled: Boolean = true

    private const val MAX_MEMORY_CACHE_BYTES = 12 * 1024 * 1024     // 12MB RAM
    private const val MAX_DISK_CACHE_BYTES = 50L * 1024 * 1024      // 50MB Disk
    private const val TARGET_DISK_CACHE_BYTES = 40L * 1024 * 1024   // 40MB sau khi cắt tỉa
    private const val MAX_FILE_SIZE_BYTES = 4 * 1024 * 1024         // 4MB / file
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000       // 7 ngày
    const val CACHE_DIR_NAME = "yt_static_cache"

    data class CachedResource(
        val mimeType: String,
        val encoding: String?,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CachedResource
            return mimeType == other.mimeType &&
                encoding == other.encoding &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + (encoding?.hashCode() ?: 0)
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private val memoryCache = object : LruCache<String, CachedResource>(MAX_MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedResource): Int = value.data.size
    }

    private val inFlightLocks = ConcurrentHashMap<String, Any>()
    private val diskSizeBytes = AtomicLong(-1L)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var diskCacheDir: File? = null

    @Volatile
    private var initialized: Boolean = false

    private val ALLOWED_HOSTS = setOf(
        "m.youtube.com",
        "www.youtube.com",
        "youtube.com",
        "s.ytimg.com",
        "i.ytimg.com",
        "fonts.gstatic.com",
        "fonts.googleapis.com",
        "www.gstatic.com"
    )

    private val STATIC_PATH_PATTERNS = listOf(
        "/s/player/",
        "/s/desktop/",
        "/s/mobile/",
        "/yts/jsbin/",
        "/yts/cssbin/"
    )

    private val STATIC_EXTENSIONS = listOf(
        ".js",
        ".css",
        ".woff2",
        ".woff",
        ".ttf",
        ".svg",
        ".ico"
    )

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val dir = ensureCacheDir(context)
            initialized = true
            ioExecutor.execute {
                warmupDns()
                trimDiskCache()
            }
            Log.d(TAG, "Đã khởi tạo WebResourceCacheManager (Dir: ${dir.absolutePath})")
        }
    }

    /**
     * Đảm bảo thư mục cache luôn tồn tại (kể cả sau khi người dùng xóa bộ nhớ đệm ứng dụng).
     */
    fun ensureCacheDir(context: Context): File {
        val app = context.applicationContext
        val dir = diskCacheDir ?: File(app.cacheDir, CACHE_DIR_NAME).also { diskCacheDir = it }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun warmupDns() {
        val domains = listOf(
            "m.youtube.com",
            "i.ytimg.com",
            "s.ytimg.com",
            "yt3.ggpht.com",
            "fonts.gstatic.com"
        )
        for (domain in domains) {
            try {
                InetAddress.getByName(domain)
            } catch (_: Exception) {}
        }
    }

    internal fun isCacheableStaticUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val parsed = UrlUtils.parseHttpUrl(url) ?: return false
        val host = parsed.host
        val lowerPath = parsed.path.lowercase(Locale.ROOT)

        if (host.contains("googlevideo.com") || lowerPath.contains("/videoplayback")) {
            return false
        }

        if (lowerPath.contains("/youtubei/v1/") ||
            lowerPath.contains("/api/") ||
            lowerPath.contains("/ptracking") ||
            lowerPath.contains("/generate_204") ||
            lowerPath.contains("/recaptcha/") ||
            lowerPath.contains("/accounts/") ||
            lowerPath.contains("/boq-identity/")
        ) {
            return false
        }

        if (!ALLOWED_HOSTS.contains(host)) {
            return false
        }

        val matchesPattern = STATIC_PATH_PATTERNS.any { lowerPath.contains(it) }
        val matchesExtension = STATIC_EXTENSIONS.any { lowerPath.endsWith(it) }
        return matchesPattern || matchesExtension
    }

    fun isCacheableStaticAsset(url: String, request: WebResourceRequest? = null): Boolean {
        if (!isEnabled) return false
        if (request != null) {
            if (!request.method.equals("GET", ignoreCase = true)) return false
            if (request.isForMainFrame) return false
        }
        return isCacheableStaticUrl(url)
    }

    /**
     * Kiểm tra MIME Type trả về có hợp lệ với tài nguyên tĩnh không (chống Captive Portal trả text/html 200 OK).
     */
    internal fun isCompatibleMimeType(url: String, mimeType: String): Boolean {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        if (lowerMime.contains("text/html") || lowerMime.contains("application/xhtml")) {
            return false
        }
        val lowerPath = UrlUtils.parseHttpUrl(url)?.path?.lowercase(Locale.ROOT) ?: url.lowercase(Locale.ROOT)
        return when {
            lowerPath.endsWith(".js") -> lowerMime.contains("javascript") || lowerMime.contains("ecmascript")
            lowerPath.endsWith(".css") -> lowerMime.contains("text/css")
            lowerPath.endsWith(".woff2") || lowerPath.endsWith(".woff") || lowerPath.endsWith(".ttf") ->
                lowerMime.startsWith("font/") || lowerMime.contains("woff") || lowerMime.contains("truetype") || lowerMime.contains("octet-stream")
            lowerPath.endsWith(".svg") -> lowerMime.contains("svg")
            lowerPath.endsWith(".ico") -> lowerMime.contains("icon") || lowerMime.startsWith("image/")
            else -> !lowerMime.startsWith("text/plain")
        }
    }

    fun getCacheKey(url: String): String {
        val parsed = UrlUtils.parseHttpUrl(url)
        val normalizedUrl = if (parsed != null) {
            val lowerPath = parsed.path.lowercase(Locale.ROOT)
            if (lowerPath.contains("/s/") &&
                (lowerPath.endsWith(".woff2") || lowerPath.endsWith(".js") || lowerPath.endsWith(".css"))
            ) {
                "${parsed.scheme}://${parsed.host}${parsed.path}"
            } else {
                url
            }
        } else {
            url
        }
        return sha256(normalizedUrl)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b))
        }
        return sb.toString()
    }

    fun intercept(request: WebResourceRequest?): WebResourceResponse? {
        if (!isEnabled || request == null) return null
        val url = request.url?.toString() ?: return null
        if (!isCacheableStaticAsset(url, request)) return null

        val key = getCacheKey(url)
        val requestOrigin = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value

        memoryCache.get(key)?.let { cached ->
            return createWebResourceResponse(cached, requestOrigin)
        }

        // Single-flight lock theo từng key để tránh 2 WebView tải đúp cùng lúc
        val lock = inFlightLocks.computeIfAbsent(key) { Any() }
        try {
            synchronized(lock) {
                memoryCache.get(key)?.let { cached ->
                    return createWebResourceResponse(cached, requestOrigin)
                }

                readFromDisk(key)?.let { diskCached ->
                    memoryCache.put(key, diskCached)
                    return createWebResourceResponse(diskCached, requestOrigin)
                }

                val fetched = fetchAndCache(url, request, key)
                if (fetched != null) {
                    return createWebResourceResponse(fetched, requestOrigin)
                }
            }
        } finally {
            inFlightLocks.remove(key, lock)
        }
        return null
    }

    private fun fetchAndCache(
        url: String,
        request: WebResourceRequest,
        key: String
    ): CachedResource? {
        var connection: HttpURLConnection? = null
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            connection = conn
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true

            request.requestHeaders?.forEach { (headerKey, headerValue) ->
                if (!headerKey.equals("Host", ignoreCase = true) &&
                    !headerKey.equals("Content-Length", ignoreCase = true) &&
                    !headerKey.equals("Accept-Encoding", ignoreCase = true) &&
                    !headerKey.equals("Range", ignoreCase = true)
                ) {
                    conn.setRequestProperty(headerKey, headerValue)
                }
            }
            conn.setRequestProperty("Accept-Encoding", "gzip")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            // Kiểm tra trước kích thước để không tải về RAM rồi vứt bỏ nếu vượt ngưỡng 4MB
            val declaredLength = conn.contentLengthLong
            if (declaredLength > MAX_FILE_SIZE_BYTES) {
                return null
            }

            val (mimeType, encoding) = parseContentType(conn.contentType, url)
            if (!isCompatibleMimeType(url, mimeType)) {
                return null
            }

            val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
            val stream = if (isGzip) GZIPInputStream(conn.inputStream) else conn.inputStream
            val bytes = stream.use { input -> readBoundedBytes(input, MAX_FILE_SIZE_BYTES) } ?: return null

            if (bytes.isNotEmpty()) {
                val cached = CachedResource(
                    mimeType = mimeType,
                    encoding = encoding,
                    data = bytes
                )
                memoryCache.put(key, cached)
                saveToDisk(key, cached)
                return cached
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi nạp tài nguyên tĩnh ($url): ${e.message}")
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
        return null
    }

    private fun readBoundedBytes(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream(16 * 1024)
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                return null
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun createWebResourceResponse(
        cached: CachedResource,
        requestOrigin: String?
    ): WebResourceResponse {
        val allowedOrigin = if (!requestOrigin.isNullOrBlank()) {
            val parsedOrigin = UrlUtils.parseHttpUrl(requestOrigin)
            if (parsedOrigin != null && UrlUtils.isAllowedInAppHost(parsedOrigin.host)) {
                "${parsedOrigin.scheme}://${parsedOrigin.host}"
            } else {
                "https://m.youtube.com"
            }
        } else {
            "*"
        }

        val responseHeaders = mapOf(
            "Access-Control-Allow-Origin" to allowedOrigin,
            "Access-Control-Allow-Methods" to "GET, OPTIONS",
            "Vary" to "Origin",
            "Cache-Control" to "public, max-age=604800",
            "X-Cache-Lookup" to "HIT"
        )
        return WebResourceResponse(
            cached.mimeType,
            cached.encoding,
            200,
            "OK",
            responseHeaders,
            ByteArrayInputStream(cached.data)
        )
    }

    private fun readFromDisk(key: String): CachedResource? {
        val dir = diskCacheDir ?: return null
        val binFile = File(dir, "$key.bin")
        val metaFile = File(dir, "$key.meta")

        if (!binFile.exists() || !metaFile.exists()) {
            return null
        }

        try {
            val metaJson = JSONObject(metaFile.readText(Charsets.UTF_8))
            val timestamp = metaJson.optLong("timestamp", 0L)
            val now = System.currentTimeMillis()

            if (now - timestamp > CACHE_TTL_MS) {
                val removedBytes = binFile.length() + metaFile.length()
                binFile.delete()
                metaFile.delete()
                addDiskDelta(-removedBytes)
                return null
            }

            val mimeType = metaJson.optString("mimeType", "application/octet-stream")
            val encoding = metaJson.optString("encoding").ifEmpty { null }
            val bytes = binFile.readBytes()
            binFile.setLastModified(now)

            return CachedResource(
                mimeType = mimeType,
                encoding = encoding,
                data = bytes
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi đọc disk cache ($key): ${e.message}")
            binFile.delete()
            metaFile.delete()
            return null
        }
    }

    private fun saveToDisk(key: String, cached: CachedResource) {
        val dir = diskCacheDir ?: return
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val tid = Thread.currentThread().id
            val tempBin = File(dir, "$key.$tid.bin.tmp")
            val finalBin = File(dir, "$key.bin")
            val tempMeta = File(dir, "$key.$tid.meta.tmp")
            val finalMeta = File(dir, "$key.meta")

            val previousBytes = (if (finalBin.exists()) finalBin.length() else 0L) +
                (if (finalMeta.exists()) finalMeta.length() else 0L)

            tempBin.writeBytes(cached.data)
            val metaBytes = JSONObject().apply {
                put("mimeType", cached.mimeType)
                put("encoding", cached.encoding ?: "")
                put("timestamp", System.currentTimeMillis())
            }.toString().toByteArray(Charsets.UTF_8)
            tempMeta.writeBytes(metaBytes)

            if (!tempBin.renameTo(finalBin)) {
                tempBin.copyTo(finalBin, overwrite = true)
                tempBin.delete()
            }
            if (!tempMeta.renameTo(finalMeta)) {
                tempMeta.copyTo(finalMeta, overwrite = true)
                tempMeta.delete()
            }

            val addedBytes = (cached.data.size + metaBytes.size).toLong() - previousBytes
            val estimatedTotal = addDiskDelta(addedBytes)
            if (estimatedTotal > MAX_DISK_CACHE_BYTES) {
                ioExecutor.execute { trimDiskCache() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi ghi disk cache ($key): ${e.message}")
        }
    }

    private fun addDiskDelta(delta: Long): Long {
        val current = diskSizeBytes.get()
        if (current < 0L) return 0L
        return diskSizeBytes.addAndGet(delta).coerceAtLeast(0L)
    }

    @Synchronized
    fun trimDiskCache() {
        val dir = diskCacheDir ?: return
        try {
            if (!dir.exists()) {
                diskSizeBytes.set(0L)
                return
            }
            val files = dir.listFiles() ?: return
            val now = System.currentTimeMillis()

            for (file in files) {
                if (file.name.endsWith(".tmp")) {
                    if (now - file.lastModified() > 60_000L) file.delete()
                    continue
                }
                if (file.name.endsWith(".meta")) {
                    try {
                        val metaJson = JSONObject(file.readText(Charsets.UTF_8))
                        val timestamp = metaJson.optLong("timestamp", 0L)
                        if (now - timestamp > CACHE_TTL_MS) {
                            val baseName = file.name.substringBeforeLast(".meta")
                            file.delete()
                            File(dir, "$baseName.bin").delete()
                        }
                    } catch (_: Exception) {
                        file.delete()
                    }
                }
            }

            val currentFiles = dir.listFiles() ?: return
            var totalSize = currentFiles.sumOf { it.length() }

            if (totalSize > MAX_DISK_CACHE_BYTES) {
                val binFiles = currentFiles.filter { it.name.endsWith(".bin") }
                    .sortedBy { it.lastModified() }

                for (bin in binFiles) {
                    if (totalSize <= TARGET_DISK_CACHE_BYTES) break
                    val baseName = bin.name.substringBeforeLast(".bin")
                    val meta = File(dir, "$baseName.meta")
                    totalSize -= bin.length()
                    if (meta.exists()) {
                        totalSize -= meta.length()
                        meta.delete()
                    }
                    bin.delete()
                }
            }
            diskSizeBytes.set(totalSize.coerceAtLeast(0L))
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi dọn dẹp disk cache: ${e.message}")
        }
    }

    fun clearCache(context: Context) {
        try {
            memoryCache.evictAll()
            val dir = ensureCacheDir(context)
            dir.listFiles()?.forEach { it.deleteRecursively() }
            diskSizeBytes.set(0L)
            Log.d(TAG, "Đã xóa toàn bộ WebResourceCache (Memory + Disk)")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xóa cache: ${e.message}")
        }
    }

    private fun parseContentType(rawContentType: String?, url: String): Pair<String, String?> {
        var mimeType: String? = null
        var encoding: String? = null

        if (!rawContentType.isNullOrBlank()) {
            val parts = rawContentType.split(";").map { it.trim() }
            if (parts.isNotEmpty()) {
                mimeType = parts[0].lowercase(Locale.ROOT)
            }
            for (part in parts) {
                if (part.startsWith("charset=", ignoreCase = true)) {
                    encoding = part.substringAfter("=").trim().trim('"', '\'')
                }
            }
        }

        if (mimeType.isNullOrBlank()) {
            mimeType = guessMimeType(url)
        }

        if (encoding.isNullOrBlank() && (
                mimeType.startsWith("text/") ||
                    mimeType.contains("javascript") ||
                    mimeType.contains("json") ||
                    mimeType.contains("svg")
                )
        ) {
            encoding = "UTF-8"
        }

        return Pair(mimeType, encoding)
    }

    private fun guessMimeType(url: String): String {
        val lowerPath = UrlUtils.parseHttpUrl(url)?.path?.lowercase(Locale.ROOT) ?: url.lowercase(Locale.ROOT)
        return when {
            lowerPath.endsWith(".js") -> "application/javascript"
            lowerPath.endsWith(".css") -> "text/css"
            lowerPath.endsWith(".woff2") -> "font/woff2"
            lowerPath.endsWith(".woff") -> "font/woff"
            lowerPath.endsWith(".ttf") -> "font/ttf"
            lowerPath.endsWith(".svg") -> "image/svg+xml"
            lowerPath.endsWith(".ico") -> "image/x-icon"
            lowerPath.endsWith(".png") -> "image/png"
            lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") -> "image/jpeg"
            lowerPath.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
