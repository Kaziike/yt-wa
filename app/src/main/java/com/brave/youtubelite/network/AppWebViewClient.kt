package com.brave.youtubelite.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * WebViewClient tích hợp cơ chế:
 * 1. Chặn request mạng bằng AdBlockEngine
 * 2. Tiêm Scriptlet SSAI Defuser
 * 3. Tiêm bộ luật CSS Cosmetic Filtering an toàn (hỗ trợ cả document.documentElement khi head chưa sẵn sàng)
 */
class AppWebViewClient(private val context: Context) : WebViewClient() {

    private val tag = "AppWebViewClient"
    private var scriptletCode: String = ""
    private var cosmeticCss: String = ""

    init {
        loadAssets()
    }

    private fun loadAssets() {
        try {
            val jsStream: InputStream = context.assets.open("scriptlets/youtube_defuser.js")
            scriptletCode = jsStream.bufferedReader().use { it.readText() }

            val cssStream: InputStream = context.assets.open("filters/cosmetic_rules.css")
            cosmeticCss = cssStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(tag, "Lỗi đọc file assets: ${e.message}")
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url.toString()

        if (AdBlockEngine.isBlocked(url)) {
            Log.d(tag, "🛡️ [Blocked]: $url")
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectShields(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectShields(view)
    }

    private fun injectShields(webView: WebView?) {
        webView ?: return

        // 1. Tiêm Scriptlet JS
        if (scriptletCode.isNotEmpty()) {
            webView.evaluateJavascript(scriptletCode, null)
        }

        // 2. Tiêm CSS Cosmetic Filter (dùng document.head || document.documentElement để không bị lỗi null)
        if (cosmeticCss.isNotEmpty()) {
            val encodedCss = Base64.encodeToString(cosmeticCss.toByteArray(), Base64.NO_WRAP)
            val injectCssJs = """
                (function() {
                    const styleId = '__brave_cosmetic_style';
                    let style = document.getElementById(styleId);
                    if (!style) {
                        style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        style.innerHTML = window.atob('$encodedCss');
                        const target = document.head || document.documentElement;
                        if (target) {
                            target.appendChild(style);
                        }
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(injectCssJs, null)
        }
    }
}
