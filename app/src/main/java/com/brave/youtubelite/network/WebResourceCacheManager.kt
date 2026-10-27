package com.brave.youtubelite.network

import android.content.Context
import android.net.Uri
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

/**
 * Quản lý Preload / Warmup và Lưu đệm tài nguyên tĩnh (Static Asset Cache) đa tầng
 * (Memory LRU + Disk Cache) cho YouTube Lite, giúp tránh tải lại các bundle JS/CSS/Font nặng.
 */
object WebResourceCacheManager {
    private const val TAG = "WebResourceCache"

    @Volatile
    var isEnabled: Boolean = true

    // Giới hạn dung lượng bộ nhớ
    private const val MAX_MEMORY_CACHE_BYTES = 12 * 1024 * 1024 // 12MB RAM
    private const val MAX_DISK_CACHE_BYTES = 50L * 1024 * 1024  // 50MB Disk
    private const val TARGET_DISK_CACHE_BYTES = 40L * 1024 * 1024 // 40MB khi dọn dẹp
    private const val MAX_FILE_SIZE_BYTES = 4 * 1024 * 1024     // Tối đa 4MB / file
    private const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000   // 7 ngày

    private const val CACHE_DIR_NAME = "yt_static_cache"

    // Bộ nhớ đệm Memory LRU
    data class CachedResource(
        val mimeType: String,
        val encoding: String?,
        val data: ByteArray,
        val headers: Map<String, String> = emptyMap()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CachedResource
            if (mimeType != other.mimeType) return false
            if (encoding != other.encoding) return false
            if (!data.contentEquals(other.data)) return false
            if (headers != other.headers) return false
            return true
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + (encoding?.hashCode() ?: 0)
            result = 31 * result + data.contentHashCode()
            result = 31 * result + headers.hashCode()
            return result
        }
    }

    private val memoryCache = object : LruCache<String, CachedResource>(MAX_MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedResource): Int {
            return value.data.size
        }
    }

    private var diskCacheDir: File? = null
    private var appContext: Context? = null

    // Danh sách host tĩnh được phép cache
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

    // Danh sách pattern thư mục tài nguyên tĩnh
    private val STATIC_PATH_PATTERNS = listOf(
        "/s/player/",
        "/s/desktop/",
        "/s/mobile/",
        "/yts/jsbin/",
        "/yts/cssbin/"
    )

    // Danh sách đuôi file tài nguyên tĩnh
    private val STATIC_EXTENSIONS = listOf(
        ".js",
        ".css",
        ".woff2",
        ".woff",
        ".ttf",
        ".svg",
        ".ico"
    )

    /**
     * Khởi tạo bộ đệm đĩa và kích hoạt warmup DNS trong nền
     */
    fun init(context: Context) {
        if (appContext == null) {
            val app = context.applicationContext
            appContext = app
            val dir = File(app.cacheDir, CACHE_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            diskCacheDir = dir
            warmupConnections(app)
            Thread {
                trimDiskCache()
            }.start()
            Log.d(TAG, "Đã khởi tạo WebResourceCacheManager (Dir: ${dir.absolutePath})")
        }
    }

    /**
     * Khởi tạo các kết nối mạng quan trọng trước khi tải trang
     */
    fun warmup(context: Context) {
        warmupConnections(context)
    }

    /**
     * Phân giải DNS trước cho các domain thiết yếu của YouTube để triệt tiêu độ trễ DNS lần đầu
     */
    fun warmupConnections(context: Context) {
        Thread {
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
                } catch (_: Exception) {
                    // Lỗi phân giải DNS bỏ qua an toàn
                }
            }
        }.start()
    }

    /**
     * Kiểm tra URL và WebResourceRequest có phải là tài nguyên tĩnh bất biến cần cache hay không
     */
    fun isCacheableStaticAsset(url: String, request: WebResourceRequest? = null): Boolean {
        if (!isEnabled) return false
        if (url.isBlank()) return false
        if (request != null) {
            // Chỉ cache phương thức GET
            val method = request.method
            if (!method.equals("GET", ignoreCase = true)) return false

            // KHÔNG cache document HTML chính
            if (request.isForMainFrame) return false
        }

        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path?.lowercase() ?: ""

            // KHÔNG cache luồng video/audio
            if (host.contains("googlevideo.com") || path.contains("/videoplayback")) {
                return false
            }

            // KHÔNG cache API động
            if (path.contains("/youtubei/v1/") ||
                path.contains("/api/") ||
                path.contains("/ptracking") ||
                path.contains("/generate_204")
            ) {
                return false
            }

            // CHỈ cache các tài nguyên tĩnh bất biến của YouTube & Google
            if (!ALLOWED_HOSTS.contains(host)) {
                return false
            }

            // Khớp một trong các mẫu tài nguyên tĩnh cốt lõi
            val matchesPattern = STATIC_PATH_PATTERNS.any { path.contains(it) }
            val matchesExtension = STATIC_EXTENSIONS.any { path.endsWith(it) }

            matchesPattern || matchesExtension
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Tạo khóa băm SHA-256 từ URL (chuẩn hóa loại bỏ query không cần thiết đối với asset dưới /s/)
     */
    fun getCacheKey(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path?.lowercase() ?: ""
            val normalizedUrl = if (path.contains("/s/") && (path.endsWith(".woff2") || path.endsWith(".js") || path.endsWith(".css"))) {
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: ""
                "$scheme://$host${uri.path}"
            } else {
                url
            }
            sha256(normalizedUrl)
        } catch (_: Exception) {
            sha256(url)
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Đánh chặn request mạng từ WebView:
     * - Nếu có trong Memory hoặc Disk Cache -> Trả về WebResourceResponse ngay lập tức.
     * - Nếu chưa có -> Tải qua HttpURLConnection, lưu vào Cache rồi trả về.
     */
    fun intercept(request: WebResourceRequest?): WebResourceResponse? {
        if (!isEnabled || request == null) return null
        val url = request.url?.toString() ?: return null

        if (!isCacheableStaticAsset(url, request)) {
            return null
        }

        val key = getCacheKey(url)

        // 1. Kiểm tra Memory Cache
        val memoryCached = memoryCache.get(key)
        if (memoryCached != null) {
            return createWebResourceResponse(memoryCached)
        }

        // 2. Kiểm tra Disk Cache
        val diskCached = readFromDisk(key)
        if (diskCached != null) {
            memoryCache.put(key, diskCached)
            return createWebResourceResponse(diskCached)
        }

        // 3. Tải từ mạng và nạp vào bộ đệm
        return fetchAndCache(url, request, key)
    }

    private fun fetchAndCache(
        url: String,
        request: WebResourceRequest,
        key: String
    ): WebResourceResponse? {
        var connection: HttpURLConnection? = null
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            connection = conn
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true

            // Kế thừa các header cần thiết từ request WebView (User-Agent, v.v.)
            request.requestHeaders?.forEach { (headerKey, headerValue) ->
                if (!headerKey.equals("Host", ignoreCase = true) &&
                    !headerKey.equals("Content-Length", ignoreCase = true) &&
                    !headerKey.equals("Accept-Encoding", ignoreCase = true)
                ) {
                    conn.setRequestProperty(headerKey, headerValue)
                }
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val rawContentType = conn.contentType
                val (mimeType, encoding) = parseContentType(rawContentType, url)

                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty() && bytes.size <= MAX_FILE_SIZE_BYTES) {
                    val cached = CachedResource(
                        mimeType = mimeType,
                        encoding = encoding,
                        data = bytes
                    )
                    memoryCache.put(key, cached)
                    saveToDisk(key, cached)
                    return createWebResourceResponse(cached)
                }
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

    private fun createWebResourceResponse(cached: CachedResource): WebResourceResponse {
        val responseHeaders = mutableMapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, OPTIONS",
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

            // Kiểm tra TTL 7 ngày
            if (now - timestamp > CACHE_TTL_MS) {
                binFile.delete()
                metaFile.delete()
                return null
            }

            val mimeType = metaJson.optString("mimeType", "application/octet-stream")
            val encoding = metaJson.optString("encoding").ifEmpty { null }
            val bytes = binFile.readBytes()

            // Cập nhật timestamp sửa đổi để phục vụ cơ chế LRU đĩa
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
            val tempBin = File(dir, "$key.bin.tmp")
            val finalBin = File(dir, "$key.bin")
            val tempMeta = File(dir, "$key.meta.tmp")
            val finalMeta = File(dir, "$key.meta")

            tempBin.writeBytes(cached.data)

            val metaJson = JSONObject().apply {
                put("mimeType", cached.mimeType)
                put("encoding", cached.encoding ?: "")
                put("timestamp", System.currentTimeMillis())
            }
            tempMeta.writeText(metaJson.toString(), Charsets.UTF_8)

            tempBin.renameTo(finalBin)
            tempMeta.renameTo(finalMeta)

            if (getCacheSizeBytesInternal(dir) > MAX_DISK_CACHE_BYTES) {
                trimDiskCache()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi ghi disk cache ($key): ${e.message}")
        }
    }

    @Synchronized
    fun trimDiskCache() {
        val dir = diskCacheDir ?: return
        try {
            val files = dir.listFiles() ?: return
            val now = System.currentTimeMillis()

            // 1. Xóa file hết hạn TTL
            for (file in files) {
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

            // 2. Dọn bớt file cũ nhất theo LRU nếu tổng dung lượng vượt quá MAX_DISK_CACHE_BYTES
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
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi dọn dẹp disk cache: ${e.message}")
        }
    }

    /**
     * Trả về tổng dung lượng cache đã lưu trên đĩa (bytes)
     */
    fun getCacheSizeBytes(context: Context): Long {
        val dir = diskCacheDir ?: File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
        return getCacheSizeBytesInternal(dir)
    }

    private fun getCacheSizeBytesInternal(dir: File): Long {
        return try {
            if (!dir.exists()) return 0L
            dir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Xóa toàn bộ bộ nhớ đệm (Memory + Disk)
     */
    fun clearCache(context: Context) {
        try {
            memoryCache.evictAll()
            val dir = diskCacheDir ?: File(context.applicationContext.cacheDir, CACHE_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
            Log.d(TAG, "Đã xóa toàn bộ WebResourceCache (Memory + Disk)")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi xóa cache: ${e.message}")
        }
    }

    /**
     * Trả về đoạn mã JavaScript preload chạy tại DOCUMENT_START để thiết lập kết nối trước
     */
    fun getPreloadScript(): String {
        return """
            (function() {
                if (window.__brave_preload_installed) return;
                window.__brave_preload_installed = true;
                try {
                    var domains = [
                        'https://m.youtube.com',
                        'https://i.ytimg.com',
                        'https://s.ytimg.com',
                        'https://yt3.ggpht.com',
                        'https://fonts.gstatic.com'
                    ];
                    var target = document.head || document.documentElement;
                    if (target) {
                        for (var i = 0; i < domains.length; i++) {
                            var d = domains[i];
                            var lPre = document.createElement('link');
                            lPre.rel = 'preconnect';
                            lPre.href = d;
                            lPre.crossOrigin = 'anonymous';
                            target.appendChild(lPre);

                            var lDns = document.createElement('link');
                            lDns.rel = 'dns-prefetch';
                            lDns.href = d;
                            target.appendChild(lDns);
                        }
                    }
                } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun parseContentType(rawContentType: String?, url: String): Pair<String, String?> {
        var mimeType: String? = null
        var encoding: String? = null

        if (!rawContentType.isNullOrBlank()) {
            val parts = rawContentType.split(";").map { it.trim() }
            if (parts.isNotEmpty()) {
                mimeType = parts[0].lowercase()
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

        if (encoding.isNullOrBlank() && (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json") || mimeType.contains("svg"))) {
            encoding = "UTF-8"
        }

        return Pair(mimeType, encoding)
    }

    private fun guessMimeType(url: String): String {
        val path = try {
            Uri.parse(url).path?.lowercase() ?: ""
        } catch (_: Exception) {
            url.lowercase()
        }
        return when {
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
