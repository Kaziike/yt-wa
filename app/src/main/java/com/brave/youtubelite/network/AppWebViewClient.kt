package com.brave.youtubelite.network

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.brave.youtubelite.settings.AppSettings
import java.io.ByteArrayInputStream

/**
 * WebViewClient bảo mật & tinh gọn:
 * 1. Giới hạn origin tiêm scriptlet chỉ trong hệ sinh thái YouTube (không tiêm tràn lan setOf("*")).
 * 2. Bóc tách link /redirect và đẩy link bên ngoài ra trình duyệt hệ thống (bảo vệ AndroidBridge).
 * 3. Tránh biên dịch lại hàng chục KB JS trên mỗi sự kiện SPA khi DOCUMENT_START_SCRIPT đã kích hoạt.
 * 4. Hỗ trợ gỡ bỏ ScriptHandler sạch sẽ để tránh rò rỉ bộ nhớ khi hủy WebView.
 */
class AppWebViewClient(
    context: Context,
    val isBrowseMode: Boolean = false
) : WebViewClient() {

    companion object {
        private const val TAG = "AppWebViewClient"
        private val EMPTY_BYTES = ByteArray(0)
        private val BLOCKED_HEADERS = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-store"
        )
        private val YOUTUBE_SCRIPT_ORIGINS = setOf(
            "https://*.youtube.com",
            "https://youtube.com",
            "https://youtu.be"
        )
    }

    private val appContext: Context = context.applicationContext
    private val scriptHandlers = mutableListOf<ScriptHandler>()
    private var settingsBootstrapHandler: ScriptHandler? = null
    private var documentStartInstalled = false

    var onPageFinishedCallback: ((url: String?) -> Unit)? = null
    var onInterceptWatchUrl: ((url: String) -> Boolean)? = null

    init {
        WebResourceCacheManager.init(appContext)
        WebAssetRepository.init(appContext)
    }

    /**
     * Đăng ký bộ scriptlet chạy ở DOCUMENT_START trên đúng origin của YouTube.
     */
    fun installDocumentStartScripts(webView: WebView) {
        clearDocumentStartScripts()
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartInstalled = false
                return
            }

            updateSettingsBootstrap(webView)

            if (WebAssetRepository.coreBridgeJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.coreBridgeJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            if (WebAssetRepository.pipControllerJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.pipControllerJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            if (WebAssetRepository.youtubeDefuserJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.youtubeDefuserJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            if (WebAssetRepository.browseInterceptorJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.browseInterceptorJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            if (WebAssetRepository.injectBaseUxCssJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.injectBaseUxCssJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            if (WebAssetRepository.injectCosmeticCssJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.injectCosmeticCssJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            if (!isBrowseMode && WebAssetRepository.injectPlayerUiCssJs.isNotEmpty()) {
                scriptHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        WebAssetRepository.injectPlayerUiCssJs,
                        YOUTUBE_SCRIPT_ORIGINS
                    )
                )
            }
            documentStartInstalled = true
        } catch (e: Exception) {
            documentStartInstalled = false
            Log.e(TAG, "Không thể đăng ký document-start script: ${e.message}")
        }
    }

    /**
     * Cập nhật cấu hình ban đầu tại DOCUMENT_START mỗi khi người dùng thay đổi Cài đặt.
     */
    fun updateSettingsBootstrap(webView: WebView) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
            settingsBootstrapHandler?.remove()
            val settingsJson = AppSettings.toJson(appContext)
            val bootstrapJs = "window.__liteTubeIsBrowse = $isBrowseMode; window.__liteTubeInitialSettings = $settingsJson;"
            settingsBootstrapHandler = WebViewCompat.addDocumentStartJavaScript(
                webView,
                bootstrapJs,
                YOUTUBE_SCRIPT_ORIGINS
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi cập nhật bootstrap settings: ${e.message}")
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request == null) return false
        // Cho phép toàn bộ iframe con (như widget xác minh reCAPTCHA / đăng nhập Google) tải tự nhiên
        if (!request.isForMainFrame) return false
        val rawUrl = request.url?.toString() ?: return false
        return handleNavigation(rawUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        return handleNavigation(url)
    }

    private fun handleNavigation(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false

        // Chặn các intent:// tự mở ứng dụng YouTube chính thức hoặc scheme lạ
        if (!rawUrl.startsWith("https://", ignoreCase = true) &&
            !rawUrl.startsWith("http://", ignoreCase = true)
        ) {
            return rawUrl != "about:blank"
        }

        // Bóc link /redirect?q=... của YouTube để mở trình duyệt ngoài
        UrlUtils.unwrapYouTubeRedirect(rawUrl)?.let { externalUrl ->
            openExternalBrowser(externalUrl)
            return true
        }

        // Ở chế độ Browse: nếu là URL xem video thường (/watch hoặc youtu.be) -> mở trên lớp Player nổi
        if (isBrowseMode) {
            val normalizedWatch = UrlUtils.normalizeWatchUrl(rawUrl)
            if (normalizedWatch != null && onInterceptWatchUrl?.invoke(normalizedWatch) == true) {
                return true
            }
        }

        // Chỉ cho phép mở trong WebView các domain thuộc hệ sinh thái YouTube / Google Login
        val parsed = UrlUtils.parseHttpUrl(rawUrl) ?: return true
        if (!UrlUtils.isAllowedInAppHost(parsed.host)) {
            openExternalBrowser(rawUrl)
            return true
        }

        return false
    }

    private fun openExternalBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Không thể mở trình duyệt ngoài cho $url: ${e.message}")
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        // Không can thiệp bất kỳ tài nguyên xác thực đăng nhập hoặc reCAPTCHA của Google
        if (url.contains("/recaptcha/", ignoreCase = true) ||
            url.contains("recaptcha.net", ignoreCase = true) ||
            url.contains("accounts.google.", ignoreCase = true)
        ) {
            return super.shouldInterceptRequest(view, request)
        }

        if (AdBlockEngine.isBlocked(url)) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                200,
                "OK",
                BLOCKED_HEADERS,
                ByteArrayInputStream(EMPTY_BYTES)
            )
        }

        WebResourceCacheManager.intercept(request)?.let { cached ->
            return cached
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (!documentStartInstalled) {
            injectFallbackScripts(view, url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (!documentStartInstalled) {
            injectFallbackScripts(view, url)
        } else {
            syncRuntimeSettingsAndStyles(view, url)
        }
        onPageFinishedCallback?.invoke(url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // Khi YouTube chuyển route SPA, chỉ đồng bộ nhẹ trạng thái CSS/Settings (< 200 bytes) thay vì nạp lại 50KB script
        syncRuntimeSettingsAndStyles(view, url)
    }

    fun syncRuntimeSettingsAndStyles(webView: WebView?, currentUrl: String? = webView?.url) {
        webView ?: return
        val parsed = UrlUtils.parseHttpUrl(currentUrl) ?: return
        if (!UrlUtils.isYouTubeHost(parsed.host)) return

        val settingsJson = AppSettings.toJson(appContext)
        webView.evaluateJavascript(
            "if(window.__liteTube)window.__liteTube.applySettings($settingsJson);",
            null
        )

        if (WebAssetRepository.injectBaseUxCssJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.injectBaseUxCssJs, null)
        }

        if (!isBrowseMode && WebAssetRepository.injectPlayerUiCssJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.injectPlayerUiCssJs, null)
        }

        if (AdBlockEngine.isEnabled && WebAssetRepository.injectCosmeticCssJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.injectCosmeticCssJs, null)
        } else if (!AdBlockEngine.isEnabled) {
            webView.evaluateJavascript(WebAssetRepository.REMOVE_COSMETIC_CSS_JS, null)
        }
    }

    private fun injectFallbackScripts(webView: WebView?, url: String?) {
        webView ?: return
        val parsed = UrlUtils.parseHttpUrl(url) ?: return
        if (!UrlUtils.isYouTubeHost(parsed.host)) return

        val settingsJson = AppSettings.toJson(appContext)
        webView.evaluateJavascript(
            "window.__liteTubeIsBrowse = $isBrowseMode; window.__liteTubeInitialSettings = $settingsJson;",
            null
        )
        if (WebAssetRepository.coreBridgeJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.coreBridgeJs, null)
        }
        if (WebAssetRepository.pipControllerJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.pipControllerJs, null)
        }
        if (WebAssetRepository.youtubeDefuserJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.youtubeDefuserJs, null)
        }
        if (WebAssetRepository.browseInterceptorJs.isNotEmpty()) {
            webView.evaluateJavascript(WebAssetRepository.browseInterceptorJs, null)
        }
        syncRuntimeSettingsAndStyles(webView, url)
    }

    private fun clearDocumentStartScripts() {
        try {
            settingsBootstrapHandler?.remove()
            settingsBootstrapHandler = null
            scriptHandlers.forEach { it.remove() }
            scriptHandlers.clear()
        } catch (_: Exception) {}
    }

    fun release() {
        onPageFinishedCallback = null
        onInterceptWatchUrl = null
        clearDocumentStartScripts()
    }
}
