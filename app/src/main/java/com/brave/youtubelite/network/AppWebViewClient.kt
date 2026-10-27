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
import com.brave.youtubelite.settings.AppSettings
import java.io.ByteArrayInputStream

/**
 * WebViewClient tích hợp:
 * 1. Chặn request mạng bằng AdBlockEngine
 * 2. Tiêm Scriptlet SSAI Defuser & PiP Controller
 * 3. Tiêm bộ luật CSS Cosmetic Filtering bằng textContent (Trusted Types CSP)
 * 4. Nhét mục "Cài đặt LiteTube" vào trong trang select_site (giữ nguyên trang gốc, không ghi đè)
 */
open class AppWebViewClient(
    private val context: Context,
    val isBrowseMode: Boolean = false
) : WebViewClient() {

    private val tag = "AppWebViewClient"
    var onPageFinishedCallback: ((url: String?) -> Unit)? = null
    var onInterceptWatchUrl: ((url: String) -> Boolean)? = null

    private var pipControllerCode: String = ""
    private var scriptletCode: String = ""
    private var injectCssJs: String = ""

    private val browseWatchInterceptorJs: String = """
        (function() {
            if (window.__braveWatchInterceptorInstalled) return;
            window.__braveWatchInterceptorInstalled = true;

            function isWatchUrl(url) {
                if (!url || typeof url !== 'string') return false;
                return url.indexOf('/watch') !== -1 && url.indexOf('/shorts/') === -1;
            }

            function dispatchWatchUrl(url) {
                if (!url || typeof url !== 'string') return false;
                var fullUrl = url;
                if (fullUrl.indexOf('http') !== 0) {
                    var origin = window.location.origin || 'https://m.youtube.com';
                    fullUrl = origin + (fullUrl.indexOf('/') === 0 ? '' : '/') + fullUrl;
                }
                if (isWatchUrl(fullUrl)) {
                    if (window.AndroidBridge && typeof window.AndroidBridge.openWatchLayer === 'function') {
                        window.AndroidBridge.openWatchLayer(fullUrl);
                        return true;
                    }
                }
                return false;
            }

            // 1. Hook history.pushState & replaceState (Lưới an toàn bắt SPA Router của YouTube)
            try {
                var origPushState = history.pushState;
                history.pushState = function(state, title, url) {
                    if (url && isWatchUrl(String(url))) {
                        if (dispatchWatchUrl(String(url))) {
                            return; // Ngăn browse webview chuyển sang URL watch
                        }
                    }
                    return origPushState.apply(history, arguments);
                };

                var origReplaceState = history.replaceState;
                history.replaceState = function(state, title, url) {
                    if (url && isWatchUrl(String(url))) {
                        if (dispatchWatchUrl(String(url))) {
                            return;
                        }
                    }
                    return origReplaceState.apply(history, arguments);
                };
            } catch(e) {}

            // 2. Bắt sự kiện click ở Capture Phase (useCapture = true) đón đầu mọi touch/click
            function onDocClick(e) {
                try {
                    var el = e.target;
                    if (!el) return;

                    var anchor = el.closest ? el.closest('a') : null;
                    if (anchor && anchor.href && isWatchUrl(anchor.href)) {
                        e.preventDefault();
                        e.stopPropagation();
                        e.stopImmediatePropagation();
                        dispatchWatchUrl(anchor.href);
                        return false;
                    }

                    var videoContainer = el.closest ? el.closest('ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-rich-item-renderer, ytm-item-section-renderer, ytm-media-item') : null;
                    if (videoContainer) {
                        var innerA = videoContainer.querySelector('a[href*="/watch"]');
                        if (innerA && innerA.href && isWatchUrl(innerA.href)) {
                            e.preventDefault();
                            e.stopPropagation();
                            e.stopImmediatePropagation();
                            dispatchWatchUrl(innerA.href);
                            return false;
                        }
                    }
                } catch(err) {}
            }

            document.addEventListener('click', onDocClick, true);

            // 3. Lắng nghe yt-navigate Polymer event
            window.addEventListener('yt-navigate', function(e) {
                try {
                    if (e && e.detail && e.detail.url && isWatchUrl(e.detail.url)) {
                        e.preventDefault();
                        e.stopPropagation();
                        dispatchWatchUrl(e.detail.url);
                    }
                } catch(err) {}
            }, true);
        })();
    """.trimIndent()

    private val selectSiteSettingsScriptletJs: String = """
        (function() {
            function injectSelectSiteItem() {
                try {
                    if (window.location.href.indexOf('select_site') === -1) return;
                    if (document.getElementById('ytlite-select-site-item')) return;

                    var container = document.querySelector('ytm-settings-options, ytm-item-section-renderer, #contents, .item-section-renderer-contents, div[role="navigation"]');
                    if (!container) {
                        var firstRow = document.querySelector('ytm-compact-link-renderer, ytm-settings-compact-link-renderer, [role="button"], div.menu-item');
                        if (firstRow && firstRow.parentNode) {
                            container = firstRow.parentNode;
                        }
                    }
                    if (!container) return;

                    var row = document.createElement('div');
                    row.id = 'ytlite-select-site-item';
                    row.setAttribute('role', 'button');
                    row.setAttribute('tabindex', '0');
                    row.style.cssText = 'display:flex;align-items:center;padding:14px 16px;cursor:pointer;background:transparent;border-bottom:1px solid rgba(255,255,255,0.08);-webkit-tap-highlight-color:rgba(255,255,255,0.1);user-select:none;box-sizing:border-box;';

                    var svgNs = 'http://www.w3.org/2000/svg';
                    var svgIcon = document.createElementNS(svgNs, 'svg');
                    svgIcon.setAttribute('viewBox', '0 0 24 24');
                    svgIcon.setAttribute('width', '24');
                    svgIcon.setAttribute('height', '24');
                    svgIcon.style.cssText = 'fill:#fff;margin-right:16px;flex-shrink:0;';
                    var path = document.createElementNS(svgNs, 'path');
                    path.setAttribute('d', 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z');
                    svgIcon.appendChild(path);

                    var content = document.createElement('div');
                    content.style.cssText = 'flex:1;min-width:0;';

                    var title = document.createElement('div');
                    title.style.cssText = 'font-size:15px;color:#fff;font-weight:500;line-height:20px;';
                    title.textContent = 'Cài đặt LiteTube';

                    var subtitle = document.createElement('div');
                    subtitle.style.cssText = 'font-size:12px;color:#aaa;line-height:16px;margin-top:2px;';
                    subtitle.textContent = 'Chặn quảng cáo, phát nền, popup, cache...';

                    content.appendChild(title);
                    content.appendChild(subtitle);

                    var chevron = document.createElementNS(svgNs, 'svg');
                    chevron.setAttribute('viewBox', '0 0 24 24');
                    chevron.setAttribute('width', '20');
                    chevron.setAttribute('height', '20');
                    chevron.style.cssText = 'fill:#aaa;flex-shrink:0;';
                    var chevronPath = document.createElementNS(svgNs, 'path');
                    chevronPath.setAttribute('d', 'M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z');
                    chevron.appendChild(chevronPath);

                    row.appendChild(svgIcon);
                    row.appendChild(content);
                    row.appendChild(chevron);

                    row.onclick = function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        if (window.AndroidBridge && typeof window.AndroidBridge.openSettings === 'function') {
                            window.AndroidBridge.openSettings();
                        }
                    };

                    if (container.firstChild) {
                        container.insertBefore(row, container.firstChild);
                    } else {
                        container.appendChild(row);
                    }
                } catch(e) {}
            }

            if (!window.__ytLiteSelectSiteInjected) {
                window.__ytLiteSelectSiteInjected = true;
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function() {
                        injectSelectSiteItem();
                    });
                    var startObs = function() {
                        if (document.body) {
                            observer.observe(document.body, { childList: true, subtree: true });
                        }
                    };
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', startObs);
                    } else {
                        startObs();
                    }
                }
            }
            injectSelectSiteItem();
        })();
    """.trimIndent()

    init {
        WebResourceCacheManager.init(context)
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
     */
    fun installDocumentStartScripts(webView: WebView) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                val origins = setOf("*")
                if (WebResourceCacheManager.isEnabled) {
                    val preloadScript = WebResourceCacheManager.getPreloadScript()
                    if (preloadScript.isNotEmpty()) {
                        WebViewCompat.addDocumentStartJavaScript(webView, preloadScript, origins)
                    }
                }
                if (pipControllerCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, pipControllerCode, origins)
                }
                if (scriptletCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, scriptletCode, origins)
                }
                if (injectCssJs.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, injectCssJs, origins)
                }
                if (selectSiteSettingsScriptletJs.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, selectSiteSettingsScriptletJs, origins)
                }
                if (isBrowseMode && browseWatchInterceptorJs.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, browseWatchInterceptorJs, origins)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Không thể đăng ký document-start script: ${e.message}")
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: ""
        if (onInterceptWatchUrl?.invoke(url) == true) {
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url != null && onInterceptWatchUrl?.invoke(url) == true) {
            return true
        }
        return super.shouldOverrideUrlLoading(view, url)
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

        val cachedResponse = WebResourceCacheManager.intercept(request)
        if (cachedResponse != null) {
            return cachedResponse
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
        onPageFinishedCallback?.invoke(url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // YouTube là SPA (chuyển trang không tải lại toàn bộ), tiêm lại để đảm bảo style & controller luôn sống
        injectShields(view)
    }

    private fun injectShields(webView: WebView?) {
        webView ?: return

        if (WebResourceCacheManager.isEnabled) {
            val preloadScript = WebResourceCacheManager.getPreloadScript()
            if (preloadScript.isNotEmpty()) {
                webView.evaluateJavascript(preloadScript, null)
            }
        }
        if (pipControllerCode.isNotEmpty()) {
            webView.evaluateJavascript(pipControllerCode, null)
        }
        if (scriptletCode.isNotEmpty()) {
            webView.evaluateJavascript(scriptletCode, null)
        }
        if (selectSiteSettingsScriptletJs.isNotEmpty()) {
            webView.evaluateJavascript(selectSiteSettingsScriptletJs, null)
        }
        if (isBrowseMode && browseWatchInterceptorJs.isNotEmpty()) {
            webView.evaluateJavascript(browseWatchInterceptorJs, null)
        }
        // Đồng bộ cấu hình từ AppSettings xuống tầng JS
        val settingsJson = AppSettings.toJson(context)
        webView.evaluateJavascript(
            "if (window.__bravePip && window.__bravePip.applySettings) window.__bravePip.applySettings($settingsJson); else window.__braveSettings = $settingsJson;",
            null
        )
        if (AdBlockEngine.isEnabled && injectCssJs.isNotEmpty()) {
            webView.evaluateJavascript(injectCssJs, null)
        } else if (!AdBlockEngine.isEnabled) {
            webView.evaluateJavascript(
                "(function(){var s=document.getElementById('__brave_cosmetic_style');if(s&&s.parentNode)s.parentNode.removeChild(s);})();",
                null
            )
        }
    }
}
