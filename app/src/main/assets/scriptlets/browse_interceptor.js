/**
 * LITETUBE BROWSE INTERCEPTOR, PRECONNECT & SELECT_SITE SETTINGS
 * 1. Preconnect / DNS-prefetch các CDN cốt lõi của YouTube (khi bật preloadCache).
 * 2. Nhét mục "Cài đặt LiteTube" vào trang m.youtube.com/select_site (chỉ bật MutationObserver
 *    khi đang ở đúng route select_site, ngắt ngay sau khi chèn xong để không tốn CPU).
 * 3. Khi ở Browse WebView (window.__liteTubeIsBrowse === true): chặn click & SPA pushState
 *    sang /watch để mở trên lớp Player nổi mà giữ nguyên feed trang chủ.
 */
(function() {
    'use strict';

    if (window.__liteTubeBrowseInstalled) return;
    window.__liteTubeBrowseInstalled = true;

    var core = window.__liteTube;

    // 1. Preconnect hints
    function installPreconnectHints() {
        try {
            if (core && core.state.settings.preloadCache === false) return;
            var target = document.head || document.documentElement;
            if (!target) return;
            var domains = [
                'https://m.youtube.com',
                'https://i.ytimg.com',
                'https://s.ytimg.com',
                'https://yt3.ggpht.com',
                'https://fonts.gstatic.com'
            ];
            for (var i = 0; i < domains.length; i++) {
                var d = domains[i];
                var lPre = document.createElement('link');
                lPre.rel = 'preconnect';
                lPre.href = d;
                lPre.crossOrigin = 'anonymous';
                target.appendChild(lPre);

                var lDns = document.createElement('link');
                lDns.rel = 'dns-prefetch';
                lDns.href = d;
                target.appendChild(lDns);
            }
        } catch (e) {}
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', installPreconnectHints, { once: true });
    } else {
        installPreconnectHints();
    }

    // 2. Mục "Cài đặt LiteTube" trong trang /select_site
    var selectSiteObserver = null;

    function stopSelectSiteObserver() {
        if (selectSiteObserver) {
            selectSiteObserver.disconnect();
            selectSiteObserver = null;
        }
    }

    function injectSelectSiteItem() {
        try {
            if (window.location.href.indexOf('select_site') === -1) {
                stopSelectSiteObserver();
                return false;
            }
            if (document.getElementById('ytlite-select-site-item')) {
                stopSelectSiteObserver();
                return true;
            }

            var container = document.querySelector(
                'ytm-settings-options, ytm-item-section-renderer, #contents, .item-section-renderer-contents, div[role="navigation"]'
            );
            if (!container) {
                var firstRow = document.querySelector(
                    'ytm-compact-link-renderer, ytm-settings-compact-link-renderer, [role="button"], div.menu-item'
                );
                if (firstRow && firstRow.parentNode) {
                    container = firstRow.parentNode;
                }
            }
            if (!container) return false;

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
            stopSelectSiteObserver();
            return true;
        } catch (e) {
            return false;
        }
    }

    function checkRouteForSelectSite() {
        if (window.location.href.indexOf('select_site') === -1) {
            stopSelectSiteObserver();
            return;
        }
        if (injectSelectSiteItem()) return;
        if (!selectSiteObserver && window.MutationObserver) {
            selectSiteObserver = new MutationObserver(function() {
                injectSelectSiteItem();
            });
            var root = document.body || document.documentElement;
            if (root) {
                selectSiteObserver.observe(root, { childList: true, subtree: true });
            }
        }
    }

    window.addEventListener('yt-navigate-finish', checkRouteForSelectSite, true);
    window.addEventListener('popstate', checkRouteForSelectSite, true);
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', checkRouteForSelectSite, { once: true });
    } else {
        checkRouteForSelectSite();
    }

    // 3. Bộ chặn SPA Watch Router (chỉ hoạt động trên Browse WebView)
    function isWatchUrl(url) {
        if (!url || typeof url !== 'string') return false;
        return url.indexOf('/watch') !== -1 && url.indexOf('/shorts/') === -1;
    }

    function dispatchWatchUrl(url) {
        if (!window.__liteTubeIsBrowse) return false;
        if (!url || typeof url !== 'string') return false;
        var fullUrl = url;
        if (fullUrl.indexOf('http') !== 0) {
            var origin = window.location.origin || 'https://m.youtube.com';
            fullUrl = origin + (fullUrl.indexOf('/') === 0 ? '' : '/') + fullUrl;
        }
        if (isWatchUrl(fullUrl) && window.AndroidBridge && typeof window.AndroidBridge.openWatchLayer === 'function') {
            window.AndroidBridge.openWatchLayer(fullUrl);
            return true;
        }
        return false;
    }

    try {
        var origPushState = history.pushState;
        history.pushState = function(stateObj, title, url) {
            if (url) {
                var s = String(url);
                if (window.__liteTubeIsBrowse && isWatchUrl(s) && dispatchWatchUrl(s)) {
                    return;
                }
                setTimeout(checkRouteForSelectSite, 50);
            }
            return origPushState.apply(history, arguments);
        };

        var origReplaceState = history.replaceState;
        history.replaceState = function(stateObj, title, url) {
            if (url) {
                var s = String(url);
                if (window.__liteTubeIsBrowse && isWatchUrl(s) && dispatchWatchUrl(s)) {
                    return;
                }
                setTimeout(checkRouteForSelectSite, 50);
            }
            return origReplaceState.apply(history, arguments);
        };
    } catch (e) {}

    if (window.__liteTubeIsBrowse) {
        document.addEventListener('click', function(e) {
            try {
                var el = e.target;
                if (!el || !el.closest) return;

                var anchor = el.closest('a');
                if (anchor && anchor.href && isWatchUrl(anchor.href)) {
                    e.preventDefault();
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    dispatchWatchUrl(anchor.href);
                    return;
                }

                var videoContainer = el.closest(
                    'ytm-compact-video-renderer, ytm-video-with-context-renderer, ytm-rich-item-renderer, ytm-item-section-renderer, ytm-media-item'
                );
                if (videoContainer) {
                    var innerA = videoContainer.querySelector('a[href*="/watch"]');
                    if (innerA && innerA.href && isWatchUrl(innerA.href)) {
                        e.preventDefault();
                        e.stopPropagation();
                        e.stopImmediatePropagation();
                        dispatchWatchUrl(innerA.href);
                    }
                }
            } catch (err) {}
        }, true);

        window.addEventListener('yt-navigate', function(e) {
            try {
                if (e && e.detail && e.detail.url && isWatchUrl(e.detail.url)) {
                    e.preventDefault();
                    e.stopPropagation();
                    dispatchWatchUrl(e.detail.url);
                }
            } catch (err) {}
        }, true);
    } else {
        // 4. Trên Player WebView: bấm vào Logo YouTube ở thanh topbar sẽ thu nhỏ trình phát thành MiniPlayer
        document.addEventListener('click', function(e) {
            try {
                var el = e.target;
                if (!el || !el.closest) return;
                var inTopbar = el.closest('ytm-mobile-topbar-renderer, #header-bar, header.mobile-topbar-header');
                if (!inTopbar) return;
                var logoHit = el.closest(
                    'ytm-home-logo, .mobile-topbar-header-endpoint, .logo-in-player-endpoint, #home-icon, .mobile-topbar-logo, a[href="/"]'
                );
                if (logoHit) {
                    e.preventDefault();
                    e.stopPropagation();
                    e.stopImmediatePropagation();
                    if (window.AndroidBridge && typeof window.AndroidBridge.minimizePlayer === 'function') {
                        window.AndroidBridge.minimizePlayer();
                    }
                }
            } catch (err) {}
        }, true);
    }
})();
