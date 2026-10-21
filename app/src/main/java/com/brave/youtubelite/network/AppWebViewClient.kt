package com.brave.youtubelite.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

/**
 * WebViewClient tích hợp cơ chế:
 * 1. Chặn request mạng bằng AdBlockEngine
 * 2. Tiêm Scriptlet SSAI Defuser & PiP Controller (ngay từ document-start + dự phòng khi tải trang)
 * 3. Tiêm bộ luật CSS Cosmetic Filtering bằng textContent (tương thích Trusted Types CSP của YouTube)
 */
class AppWebViewClient(private val context: Context) : WebViewClient() {

    private val tag = "AppWebViewClient"
    private var pipControllerCode: String = ""
    private var scriptletCode: String = ""
    private var injectCssJs: String = ""

    init {
        loadAssets()
    }

    private fun loadAssets() {
        try {
            pipControllerCode = context.assets.open("scriptlets/pip_controller.js")
                .bufferedReader().use { it.readText() }

            scriptletCode = context.assets.open("scriptlets/youtube_defuser.js")
                .bufferedReader().use { it.readText() }

            val cosmeticCss = context.assets.open("filters/cosmetic_rules.css")
                .bufferedReader().use { it.readText() }

            if (cosmeticCss.isNotEmpty()) {
                val encodedCss = Base64.encodeToString(cosmeticCss.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                // Dùng textContent thay vì innerHTML để không bị chặn bởi TrustedHTML CSP trên m.youtube.com
                injectCssJs = """
                    (function() {
                        try {
                            var styleId = '__brave_cosmetic_style';
                            var style = document.getElementById(styleId);
                            if (!style) {
                                style = document.createElement('style');
                                style.id = styleId;
                                style.type = 'text/css';
                                var bin = window.atob('$encodedCss');
                                var bytes = new Uint8Array(bin.length);
                                for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
                                style.textContent = (window.TextDecoder ? new TextDecoder('utf-8').decode(bytes) : bin);
                            }
                            var target = document.head || document.documentElement;
                            if (target && style.parentNode !== target) {
                                target.appendChild(style);
                            }
                        } catch (e) {}
                    })();
                """.trimIndent()
            }
        } catch (e: Exception) {
            Log.e(tag, "Lỗi đọc file assets: ${e.message}")
        }
    }

    /**
     * Đăng ký scriptlet chạy ở thời điểm document-start (trước mọi script của YouTube).
     * Nhờ vậy việc hook `ytInitialPlayerResponse`, `pause()`, và `__bravePip` luôn sẵn sàng
     * ngay từ đầu, không phụ thuộc thời điểm onPageStarted/onPageFinished.
     */
    fun installDocumentStartScripts(webView: WebView) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                val origins = setOf("*")
                if (pipControllerCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, pipControllerCode, origins)
                }
                if (scriptletCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, scriptletCode, origins)
                }
                if (injectCssJs.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, injectCssJs, origins)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Không thể đăng ký document-start script: ${e.message}")
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

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // YouTube là SPA (chuyển trang không tải lại toàn bộ), tiêm lại để đảm bảo style & controller luôn sống
        injectShields(view)
    }

    private fun injectShields(webView: WebView?) {
        webView ?: return

        if (pipControllerCode.isNotEmpty()) {
            webView.evaluateJavascript(pipControllerCode, null)
        }
        if (scriptletCode.isNotEmpty()) {
            webView.evaluateJavascript(scriptletCode, null)
        }
        if (injectCssJs.isNotEmpty()) {
            webView.evaluateJavascript(injectCssJs, null)
        }
    }
}
