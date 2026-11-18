package com.brave.youtubelite.network

import android.content.Context
import android.util.Base64
import android.util.Log

/**
 * Kho lưu trữ tập trung các file Scriptlet & CSS trong bộ nhớ.
 * Chỉ đọc file từ đĩa đúng 1 lần duy nhất cho cả ứng dụng (tránh nhân 2-3 bản sao trên Heap).
 */
object WebAssetRepository {
    private const val TAG = "WebAssetRepository"

    @Volatile
    private var loaded = false

    var coreBridgeJs: String = ""
        private set
    var pipControllerJs: String = ""
        private set
    var youtubeDefuserJs: String = ""
        private set
    var browseInterceptorJs: String = ""
        private set
    var injectCosmeticCssJs: String = ""
        private set
    var injectPlayerUiCssJs: String = ""
        private set

    const val REMOVE_COSMETIC_CSS_JS =
        "(function(){var s=document.getElementById('__brave_cosmetic_style');if(s&&s.parentNode)s.parentNode.removeChild(s);})();"

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val app = context.applicationContext
            coreBridgeJs = readAssetText(app, "scriptlets/yt_core_bridge.js")
            pipControllerJs = readAssetText(app, "scriptlets/pip_controller.js")
            youtubeDefuserJs = readAssetText(app, "scriptlets/youtube_defuser.js")
            browseInterceptorJs = readAssetText(app, "scriptlets/browse_interceptor.js")

            val cosmeticCss = readAssetText(app, "filters/cosmetic_rules.css")
            if (cosmeticCss.isNotEmpty()) {
                injectCosmeticCssJs = buildTrustedCssInjector("__brave_cosmetic_style", cosmeticCss, conditionalAdblock = true)
            }

            val playerUiCss = readAssetText(app, "filters/player_ui.css")
            if (playerUiCss.isNotEmpty()) {
                injectPlayerUiCssJs = buildTrustedCssInjector("__brave_player_ui_style", playerUiCss, conditionalAdblock = false)
            }

            loaded = true
        }
    }

    private fun readAssetText(context: Context, path: String): String {
        return try {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Không đọc được asset $path: ${e.message}")
            ""
        }
    }

    /**
     * Đóng gói CSS qua Base64 + TextDecoder gán vào textContent để tuân thủ TrustedHTML CSP của YouTube.
     */
    private fun buildTrustedCssInjector(
        styleId: String,
        rawCss: String,
        conditionalAdblock: Boolean
    ): String {
        val encoded = Base64.encodeToString(rawCss.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val guard = if (conditionalAdblock) {
            "if(window.__liteTube && window.__liteTube.state.settings.adblock === false) return;"
        } else {
            ""
        }
        return """
            (function() {
                function mountStyle() {
                    try {
                        $guard
                        var target = document.head || document.documentElement;
                        if (!target) return false;
                        var style = document.getElementById('$styleId');
                        if (!style) {
                            style = document.createElement('style');
                            style.id = '$styleId';
                            style.type = 'text/css';
                            var bin = window.atob('$encoded');
                            var bytes = new Uint8Array(bin.length);
                            for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                            style.textContent = (window.TextDecoder ? new TextDecoder('utf-8').decode(bytes) : bin);
                        }
                        if (style.parentNode !== target) {
                            target.appendChild(style);
                        }
                        return true;
                    } catch (e) {
                        return false;
                    }
                }
                if (!mountStyle() && document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', mountStyle, { once: true });
                }
            })();
        """.trimIndent()
    }
}
