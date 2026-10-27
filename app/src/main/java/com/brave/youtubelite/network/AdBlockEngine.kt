package com.brave.youtubelite.network

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Engine lọc URL mạng siêu tốc theo mô hình Brave Shields Native.
 * Sử dụng HashSet để đối chiếu Domain và Path trong O(1).
 */
object AdBlockEngine {
    private const val TAG = "AdBlockEngine"
    @Volatile var isEnabled: Boolean = true
    private val blockedDomains = HashSet<String>()
    private val blockedPathPatterns = listOf(
        "/api/stats/ads",
        "/pagead/",
        "/youtubei/v1/log_event"
    )

    fun init(context: Context) {
        try {
            val inputStream = context.assets.open("filters/blocked_domains.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    blockedDomains.add(trimmed.lowercase())
                }
            }
            reader.close()
            Log.d(TAG, "Đã nạp ${blockedDomains.size} quy tắc chặn quảng cáo vào bộ nhớ.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi nạp danh sách chặn: ${e.message}")
        }
    }

    fun isBlocked(url: String): Boolean {
        if (!isEnabled) return false
        if (url.isEmpty()) return false

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path?.lowercase() ?: ""

            // 1. Kiểm tra đối chiếu Domain trực tiếp
            if (blockedDomains.contains(host)) {
                return true
            }

            // 2. Kiểm tra đối chiếu Subdomain (vd: ads.google.com -> google.com)
            for (blocked in blockedDomains) {
                if (host.endsWith(".$blocked")) {
                    return true
                }
            }

            // 3. Kiểm tra các endpoint ngốn băng thông & telemetry ngầm
            for (pattern in blockedPathPatterns) {
                if (path.contains(pattern)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Không chặn nếu URL không hợp lệ để tránh crash
            return false
        }

        return false
    }
}
