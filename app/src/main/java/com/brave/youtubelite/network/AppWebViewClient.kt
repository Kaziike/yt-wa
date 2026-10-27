package com.brave.youtubelite.network

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
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
 * WebViewClient tích hợp cơ chế:
 * 1. Chặn request mạng bằng AdBlockEngine
 * 2. Tiêm Scriptlet SSAI Defuser & PiP Controller (ngay từ document-start + dự phòng khi tải trang)
 * 3. Tiêm bộ luật CSS Cosmetic Filtering bằng textContent (tương thích Trusted Types CSP của YouTube)
 * 4. Tiêm nút/mục "Cài đặt YouTube Lite" và chặn điều hướng select_site để mở Settings Dialog native
 */
class AppWebViewClient(private val context: Context) : WebViewClient() {

    private val tag = "AppWebViewClient"
    var onOpenSettings: (() -> Unit)? = null
    var onPageFinishedCallback: ((url: String?) -> Unit)? = null

    private var pipControllerCode: String = ""
    private var miniPlayerControllerCode: String = ""
    private var scriptletCode: String = ""
    private var injectCssJs: String = ""

    private val settingsScriptletJs: String = """
        (function() {
            function triggerSettings(e) {
                if (e) {
                    try {
                        e.preventDefault();
                        e.stopPropagation();
                    } catch(err) {}
                }
                if (window.AndroidBridge && typeof window.AndroidBridge.openSettings === 'function') {
                    window.AndroidBridge.openSettings();
                } else {
                    window.location.href = 'https://m.youtube.com/select_site';
                }
            }

            function injectSettingsElements() {
                try {
                    // 1. Chèn mục Cài đặt vào các menu popup / account menu của YouTube
                    var menuSelectors = [
                        'ytm-menu-popup-renderer',
                        'ytm-settings-menu',
                        'ytm-account-menu',
                        'ytm-multi-page-menu-renderer',
                        'dialog[role="dialog"]',
                        'div[role="menu"]',
                        '.dialog-content',
                        '.menu-content'
                    ];
                    var menus = document.querySelectorAll(menuSelectors.join(','));
                    for (var i = 0; i < menus.length; i++) {
                        var m = menus[i];
                        if (m.querySelector('#ytlite-settings-item')) continue;

                        var item = document.createElement('div');
                        item.id = 'ytlite-settings-item';
                        item.setAttribute('role', 'menuitem');
                        item.setAttribute('tabindex', '0');
                        item.style.cssText = 'display:flex;align-items:center;padding:12px 16px;cursor:pointer;font-size:14px;color:var(--yt-spec-text-primary,#fff);background:transparent;border-bottom:1px solid rgba(255,255,255,0.08);font-weight:500;-webkit-tap-highlight-color:rgba(255,255,255,0.1);user-select:none;';

                        var icon = document.createElement('span');
                        icon.style.cssText = 'font-size:18px;margin-right:16px;display:inline-flex;align-items:center;';
                        icon.textContent = '⚙️';

                        var label = document.createElement('span');
                        label.style.cssText = 'flex:1;';
                        label.textContent = 'Cài đặt YouTube Lite';

                        item.appendChild(icon);
                        item.appendChild(label);
                        item.onclick = triggerSettings;

                        if (m.firstChild) {
                            m.insertBefore(item, m.firstChild);
                        } else {
                            m.appendChild(item);
                        }
                    }

                    // 2. Chèn icon cài đặt lên thanh điều hướng trên cùng (header)
                    var header = document.querySelector('header, ytm-header-bar, #header-bar, .mobile-topbar-header');
                    if (header && !document.getElementById('ytlite-header-settings-btn')) {
                        var target = header.querySelector('.header-end, .end, .topbar-icons') || header;
                        var btn = document.createElement('button');
                        btn.id = 'ytlite-header-settings-btn';
                        btn.setAttribute('type', 'button');
                        btn.setAttribute('aria-label', 'Cài đặt YouTube Lite');
                        btn.style.cssText = 'background:transparent;border:none;cursor:pointer;padding:8px;font-size:18px;display:inline-flex;align-items:center;justify-content:center;color:var(--yt-spec-text-primary,#fff);outline:none;vertical-align:middle;';
                        btn.textContent = '⚙️';
                        btn.onclick = triggerSettings;
                        target.appendChild(btn);
                    }
                } catch (e) {}
            }

            if (!window.__ytLiteSettingsInitialized) {
                window.__ytLiteSettingsInitialized = true;

                // Lắng nghe click nếu bất kỳ liên kết nào trỏ đến select_site
                document.addEventListener('click', function(e) {
                    try {
                        var target = e.target && e.target.closest ? e.target.closest('a') : null;
                        if (target && target.href && target.href.indexOf('select_site') !== -1) {
                            triggerSettings(e);
                        }
                    } catch (err) {}
                }, true);

                if (window.MutationObserver) {
                    var observer = new MutationObserver(function() {
                        injectSettingsElements();
                    });
                    var startObserve = function() {
                        if (document.body) {
                            observer.observe(document.body, { childList: true, subtree: true });
                        }
                    };
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', startObserve);
                    } else {
                        startObserve();
                    }
                }
            }

            injectSettingsElements();
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

            miniPlayerControllerCode = context.assets.open("scriptlets/miniplayer_controller.js")
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
                if (WebResourceCacheManager.isEnabled) {
                    val preloadScript = WebResourceCacheManager.getPreloadScript()
                    if (preloadScript.isNotEmpty()) {
                        WebViewCompat.addDocumentStartJavaScript(webView, preloadScript, origins)
                    }
                }
                if (pipControllerCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, pipControllerCode, origins)
                }
                if (miniPlayerControllerCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, miniPlayerControllerCode, origins)
                }
                if (scriptletCode.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, scriptletCode, origins)
                }
                if (injectCssJs.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, injectCssJs, origins)
                }
                if (settingsScriptletJs.isNotEmpty()) {
                    WebViewCompat.addDocumentStartJavaScript(webView, settingsScriptletJs, origins)
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
        if (url.contains("select_site")) {
            Handler(Looper.getMainLooper()).post {
                onOpenSettings?.invoke()
            }
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url != null && url.contains("select_site")) {
            Handler(Looper.getMainLooper()).post {
                onOpenSettings?.invoke()
            }
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
        if (miniPlayerControllerCode.isNotEmpty()) {
            webView.evaluateJavascript(miniPlayerControllerCode, null)
        }
        if (scriptletCode.isNotEmpty()) {
            webView.evaluateJavascript(scriptletCode, null)
        }
        if (settingsScriptletJs.isNotEmpty()) {
            webView.evaluateJavascript(settingsScriptletJs, null)
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
