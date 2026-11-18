package com.brave.youtubelite.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.brave.youtubelite.network.WebResourceCacheManager
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Quản lý cài đặt cấu hình cho LiteTube qua SharedPreferences,
 * đồng thời xử lý tính toán & dọn dẹp bộ nhớ đệm an toàn trên luồng nền (không chặn UI Thread).
 */
object AppSettings {
    private const val PREFS_NAME = "youtube_lite_prefs"

    private const val KEY_ADBLOCK = "adblock_enabled"
    private const val KEY_BACKGROUND_PLAY = "background_play_enabled"
    private const val KEY_AUTO_PIP = "auto_pip_enabled"
    private const val KEY_PRELOAD_CACHE = "preload_cache_enabled"

    private val diskExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAdBlockEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_ADBLOCK, true)

    fun setAdBlockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ADBLOCK, enabled).apply()
    }

    fun isBackgroundPlayEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_BACKGROUND_PLAY, true)

    fun setBackgroundPlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply()
    }

    fun isAutoPipEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_PIP, true)

    fun setAutoPipEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PIP, enabled).apply()
    }

    fun isPreloadCacheEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_PRELOAD_CACHE, true)

    fun setPreloadCacheEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PRELOAD_CACHE, enabled).apply()
    }

    fun toJson(context: Context): String {
        return JSONObject().apply {
            put("adblock", isAdBlockEnabled(context))
            put("backgroundPlay", isBackgroundPlayEnabled(context))
            put("autoPip", isAutoPipEnabled(context))
            put("preloadCache", isPreloadCacheEnabled(context))
        }.toString()
    }

    /**
     * Tính tổng dung lượng cache thực tế của ứng dụng (không cộng trùng yt_static_cache vì đã nằm trong cacheDir).
     */
    fun getTotalCacheSizeBytes(context: Context): Long {
        val app = context.applicationContext
        var size = 0L
        try {
            app.cacheDir?.let { size += calculateFolderSize(it) }
            app.externalCacheDir?.let { size += calculateFolderSize(it) }
        } catch (_: Exception) {}
        return size
    }

    fun formatBytesToMb(bytes: Long): String {
        val mb = bytes.coerceAtLeast(0L).toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun computeFormattedCacheSizeAsync(context: Context, onResult: (String) -> Unit) {
        val app = context.applicationContext
        diskExecutor.execute {
            val formatted = formatBytesToMb(getTotalCacheSizeBytes(app))
            mainHandler.post { onResult(formatted) }
        }
    }

    /**
     * Xóa các file bên trong cacheDir & externalCacheDir mà KHÔNG xóa mất thư mục gốc cacheDir
     * và KHÔNG xóa codeCacheDir đang sử dụng bởi hệ thống/Chromium, sau đó tái tạo yt_static_cache.
     */
    fun clearAppCacheAsync(context: Context, onCleared: (String) -> Unit) {
        val app = context.applicationContext
        diskExecutor.execute {
            try {
                WebResourceCacheManager.clearCache(app)
                clearDirectoryChildren(app.cacheDir)
                clearDirectoryChildren(app.externalCacheDir)
                WebResourceCacheManager.ensureCacheDir(app)
            } catch (_: Exception) {}
            val formatted = formatBytesToMb(getTotalCacheSizeBytes(app))
            mainHandler.post { onCleared(formatted) }
        }
    }

    private fun clearDirectoryChildren(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { child ->
            try {
                child.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    private fun calculateFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) calculateFolderSize(child) else child.length()
        }
        return total
    }
}
