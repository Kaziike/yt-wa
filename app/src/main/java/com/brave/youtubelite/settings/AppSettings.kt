package com.brave.youtubelite.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Quản lý cài đặt cấu hình cho YouTube Lite lưu trữ qua SharedPreferences.
 * Đồng thời cung cấp phương thức toJson() để đồng bộ trạng thái cấu hình sang JavaScript.
 */
object AppSettings {
    private const val PREFS_NAME = "youtube_lite_prefs"

    private const val KEY_ADBLOCK = "adblock_enabled"
    private const val KEY_BACKGROUND_PLAY = "background_play_enabled"
    private const val KEY_AUTO_PIP = "auto_pip_enabled"
    private const val KEY_PRELOAD_CACHE = "preload_cache_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Chặn quảng cáo & tự động tua SSAI 16x (Mặc định: true)
     */
    fun isAdBlockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ADBLOCK, true)
    }

    fun setAdBlockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ADBLOCK, enabled).apply()
    }

    /**
     * Phát âm thanh nền khi tắt màn hình hoặc chuyển app (Mặc định: true)
     */
    fun isBackgroundPlayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_PLAY, true)
    }

    fun setBackgroundPlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply()
    }

    /**
     * Tự động mở Video Popup (PiP) khi thoát ra màn hình chính (Mặc định: true)
     */
    fun isAutoPipEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_PIP, true)
    }

    fun setAutoPipEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
    }

    /**
     * Tải trước & Lưu đệm tài nguyên Web tránh tải lặp lại (Mặc định: true)
     */
    fun isPreloadCacheEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PRELOAD_CACHE, true)
    }

    fun setPreloadCacheEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PRELOAD_CACHE, enabled).apply()
    }

    /**
     * Xuất cài đặt ra định dạng JSON cho web scriptlet (`window.__bravePip.applySettings(...)`)
     * Ví dụ: {"adblock":true,"backgroundPlay":true,"autoPip":true,"preloadCache":true}
     */
    fun toJson(context: Context): String {
        val json = JSONObject().apply {
            put("adblock", isAdBlockEnabled(context))
            put("backgroundPlay", isBackgroundPlayEnabled(context))
            put("autoPip", isAutoPipEnabled(context))
            put("preloadCache", isPreloadCacheEnabled(context))
        }
        return json.toString()
    }

    /**
     * Tính toán tổng dung lượng bộ nhớ đệm (cache) của ứng dụng theo byte.
     */
    fun getCacheSizeBytes(context: Context): Long {
        var size: Long = 0L
        try {
            context.cacheDir?.let { size += calculateFolderSize(it) }
            context.codeCacheDir?.let { size += calculateFolderSize(it) }
            context.externalCacheDir?.let { size += calculateFolderSize(it) }
        } catch (_: Exception) {}
        return size
    }

    /**
     * Định dạng kích thước cache thành chuỗi hiển thị thân thiện (ví dụ: "0.0 MB" hoặc "12.4 MB").
     */
    fun getFormattedCacheSize(context: Context): String {
        val bytes = getCacheSizeBytes(context)
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    /**
     * Xóa các thư mục cache của ứng dụng.
     */
    fun clearAppCache(context: Context) {
        try {
            context.cacheDir?.deleteRecursively()
            context.codeCacheDir?.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        } catch (_: Exception) {}
    }

    private fun calculateFolderSize(dir: File): Long {
        var total: Long = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) calculateFolderSize(child) else child.length()
        }
        return total
    }
}
