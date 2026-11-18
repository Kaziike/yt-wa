package com.brave.youtubelite.network

import java.net.URLDecoder
import java.util.Locale

/**
 * Tiện ích phân tích và chuẩn hóa URL thuần Kotlin (không phụ thuộc android.net.Uri),
 * tối ưu tốc độ trên luồng IO của shouldInterceptRequest và hỗ trợ kiểm thử JUnit trực tiếp.
 */
object UrlUtils {

    data class ParsedHttpUrl(
        val scheme: String,
        val host: String,
        val path: String,
        val query: String?
    )

    private val ALLOWED_IN_APP_DOMAINS = listOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "google.com",
        "gstatic.com",
        "ggpht.com",
        "ytimg.com"
    )

    fun parseHttpUrl(rawUrl: String?): ParsedHttpUrl? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        val schemeIdx = trimmed.indexOf("://")
        if (schemeIdx <= 0) return null

        val scheme = trimmed.substring(0, schemeIdx).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null

        val authorityStart = schemeIdx + 3
        if (authorityStart >= trimmed.length) return null

        var authorityEnd = trimmed.length
        for (i in authorityStart until trimmed.length) {
            val c = trimmed[i]
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i
                break
            }
        }

        val authority = trimmed.substring(authorityStart, authorityEnd)
        val hostPort = authority.substringAfterLast('@')
        val rawHost = if (hostPort.startsWith("[")) {
            val closeBracket = hostPort.indexOf(']')
            if (closeBracket > 0) hostPort.substring(0, closeBracket + 1) else hostPort
        } else {
            hostPort.substringBefore(':')
        }

        val host = rawHost.trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty()) return null

        var path = "/"
        var query: String? = null
        if (authorityEnd < trimmed.length) {
            val rest = trimmed.substring(authorityEnd).substringBefore('#')
            val qIdx = rest.indexOf('?')
            if (qIdx >= 0) {
                val rawPath = rest.substring(0, qIdx)
                path = if (rawPath.isEmpty()) "/" else rawPath
                query = rest.substring(qIdx + 1)
            } else if (rest.isNotEmpty()) {
                path = rest
            }
        }

        return ParsedHttpUrl(scheme = scheme, host = host, path = path, query = query)
    }

    fun isYouTubeHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return h == "youtube.com" || h.endsWith(".youtube.com") || h == "youtu.be"
    }

    fun isAllowedInAppHost(host: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return ALLOWED_IN_APP_DOMAINS.any { domain ->
            h == domain || h.endsWith(".$domain")
        }
    }

    fun getQueryParameter(query: String?, key: String): String? {
        if (query.isNullOrEmpty()) return null
        val prefix = "$key="
        for (pair in query.split('&')) {
            if (pair.startsWith(prefix)) {
                val encoded = pair.substring(prefix.length)
                return try {
                    URLDecoder.decode(encoded, "UTF-8")
                } catch (_: Exception) {
                    encoded
                }
            }
        }
        return null
    }

    /**
     * Giải mã link chuyển hướng /redirect?q=... của YouTube để mở thẳng bằng trình duyệt ngoài.
     */
    fun unwrapYouTubeRedirect(rawUrl: String?): String? {
        val parsed = parseHttpUrl(rawUrl) ?: return null
        if (!isYouTubeHost(parsed.host)) return null
        if (!parsed.path.equals("/redirect", ignoreCase = true)) return null
        val target = getQueryParameter(parsed.query, "q") ?: return null
        val targetParsed = parseHttpUrl(target) ?: return null
        return if (targetParsed.scheme == "http" || targetParsed.scheme == "https") target else null
    }

    /**
     * Kiểm tra URL có phải trang xem video thường (/watch hoặc youtu.be/<id>, không phải /shorts/).
     */
    fun isYouTubeWatchUrl(rawUrl: String?): Boolean {
        return normalizeWatchUrl(rawUrl) != null
    }

    /**
     * Chuẩn hóa và xác thực nghiêm ngặt URL xem video trước khi nạp vào playerWebView.
     * Chặn hoàn toàn các scheme độc hại (javascript:, file:, data:) hoặc domain lạ.
     */
    fun normalizeWatchUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()

        val candidate = if (trimmed.startsWith("/")) {
            "https://m.youtube.com$trimmed"
        } else {
            trimmed
        }

        val parsed = parseHttpUrl(candidate) ?: return null
        if (!isYouTubeHost(parsed.host)) return null

        val lowerPath = parsed.path.lowercase(Locale.ROOT)
        if (lowerPath.contains("/shorts/")) return null

        if (parsed.host == "youtu.be") {
            val id = parsed.path.trim('/').substringBefore('/')
            if (id.isBlank()) return null
            val listParam = getQueryParameter(parsed.query, "list")
            val listSuffix = if (!listParam.isNullOrBlank()) "&list=$listParam" else ""
            return "https://m.youtube.com/watch?v=$id$listSuffix"
        }

        if (lowerPath == "/watch" || lowerPath.startsWith("/watch/")) {
            val v = getQueryParameter(parsed.query, "v")
            if (v.isNullOrBlank()) return null
            val querySuffix = if (!parsed.query.isNullOrBlank()) "?${parsed.query}" else ""
            return "https://m.youtube.com${parsed.path}$querySuffix"
        }

        return null
    }

    fun extractVideoId(rawUrl: String?): String {
        val parsed = parseHttpUrl(rawUrl) ?: return ""
        if (!isYouTubeHost(parsed.host)) return ""

        val v = getQueryParameter(parsed.query, "v")
        if (!v.isNullOrBlank()) return v

        val segments = parsed.path.trim('/').split('/').filter { it.isNotEmpty() }
        val shortsIdx = segments.indexOfFirst { it.equals("shorts", ignoreCase = true) }
        if (shortsIdx >= 0 && shortsIdx + 1 < segments.size) {
            return segments[shortsIdx + 1]
        }

        if (parsed.host == "youtu.be" && segments.isNotEmpty()) {
            return segments[0]
        }
        return ""
    }
}
