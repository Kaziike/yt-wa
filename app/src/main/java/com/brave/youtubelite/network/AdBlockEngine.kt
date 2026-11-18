package com.brave.youtubelite.network

import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Engine lọc URL mạng theo mô hình Brave Shields Native:
 * - Hỗ trợ cả quy tắc tên miền thuần túy (doubleclick.net) lẫn quy tắc domain + path (youtube.com/api/stats/qoe).
 * - Đối chiếu domain và mọi cấp cha (subdomain) trong O(k) theo số dấu chấm thay vì duyệt tuyến tính O(N).
 * - Sử dụng RuleSnapshot bất biến (@Volatile) đảm bảo an toàn đa luồng 100% không cần khóa đồng bộ.
 */
object AdBlockEngine {
    private const val TAG = "AdBlockEngine"

    data class RuleSnapshot(
        val blockedDomains: Set<String>,
        val hostPathRules: Map<String, List<String>>,
        val globalPathPatterns: List<String>
    )

    private val DEFAULT_GLOBAL_PATHS = listOf(
        "/api/stats/ads",
        "/pagead/",
        "/youtubei/v1/log_event"
    )

    @Volatile
    var isEnabled: Boolean = true

    @Volatile
    private var snapshot: RuleSnapshot = RuleSnapshot(
        blockedDomains = emptySet(),
        hostPathRules = emptyMap(),
        globalPathPatterns = DEFAULT_GLOBAL_PATHS
    )

    @Volatile
    private var initialized: Boolean = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                val parsed = context.assets.open("filters/blocked_domains.txt")
                    .bufferedReader(Charsets.UTF_8)
                    .useLines { lines -> parseRules(lines) }
                snapshot = parsed
                initialized = true
                Log.d(
                    TAG,
                    "Đã nạp ${parsed.blockedDomains.size} domain & ${parsed.hostPathRules.size} nhóm path quảng cáo."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi nạp danh sách chặn: ${e.message}")
            }
        }
    }

    /**
     * Phân tích danh sách quy tắc từ dòng văn bản thành RuleSnapshot bất biến.
     */
    internal fun parseRules(lines: Sequence<String>): RuleSnapshot {
        val domains = HashSet<String>()
        val hostPaths = HashMap<String, MutableList<String>>()

        for (rawLine in lines) {
            var cleaned = rawLine.substringBefore('#').trim().lowercase(Locale.ROOT)
            if (cleaned.isEmpty()) continue

            cleaned = cleaned.removePrefix("||")
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('^')
                .trim()

            if (cleaned.isEmpty()) continue

            val slashIdx = cleaned.indexOf('/')
            if (slashIdx < 0) {
                val domain = cleaned.trimStart('.').trimEnd('.')
                if (domain.isNotEmpty()) {
                    domains.add(domain)
                }
            } else {
                val domain = cleaned.substring(0, slashIdx).trimStart('.').trimEnd('.')
                val pathPattern = cleaned.substring(slashIdx) // Giữ dấu '/' ở đầu
                if (domain.isNotEmpty() && pathPattern.length > 1) {
                    hostPaths.getOrPut(domain) { ArrayList() }.add(pathPattern)
                }
            }
        }

        val immutableHostPaths = hostPaths.mapValues { (_, list) -> list.toList() }
        return RuleSnapshot(
            blockedDomains = domains.toSet(),
            hostPathRules = immutableHostPaths,
            globalPathPatterns = DEFAULT_GLOBAL_PATHS
        )
    }

    internal fun setSnapshotForTesting(customSnapshot: RuleSnapshot) {
        snapshot = customSnapshot
        initialized = true
    }

    fun isBlocked(url: String): Boolean {
        if (!isEnabled || url.isEmpty()) return false
        val parsed = UrlUtils.parseHttpUrl(url) ?: return false
        val host = parsed.host
        val lowerPath = parsed.path.lowercase(Locale.ROOT)
        val snap = snapshot

        // 1. Duyệt các cấp tên miền từ chi tiết đến gốc trong O(k) (k = số cấp dấu chấm, thường <= 4)
        var cursor = host
        while (cursor.isNotEmpty()) {
            if (snap.blockedDomains.contains(cursor)) {
                return true
            }
            val pathRules = snap.hostPathRules[cursor]
            if (pathRules != null) {
                for (rule in pathRules) {
                    if (lowerPath.startsWith(rule) || lowerPath.contains(rule)) {
                        return true
                    }
                }
            }
            val dotIdx = cursor.indexOf('.')
            if (dotIdx < 0 || dotIdx + 1 >= cursor.length) break
            cursor = cursor.substring(dotIdx + 1)
        }

        // 2. Kiểm tra các mẫu đường dẫn quảng cáo/telemetry toàn cục
        for (pattern in snap.globalPathPatterns) {
            if (lowerPath.contains(pattern)) {
                return true
            }
        }

        return false
    }
}
