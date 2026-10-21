/**
 * BRAVE SHIELDS SCRIPTLET DÀNH CHO YOUTUBE LITE
 * 1. Chặn và gọt sạch metadata quảng cáo (InnerTube & SSAI 16x)
 * 2. Cơ chế chạy nền & Chống tự động ngắt video khi ở kích thước nhỏ (PLAYER_VIEW_TOO_SMALL)
 * 3. Chế độ Video Popup PiP thực thụ: Cô lập thẻ Video phủ 100% khung nổi PiP
 * 4. Đồng bộ trạng thái phát với Android Native Bridge
 */

(function() {
    'use strict';
    if (window.__brave_yt_shield_active) return;
    window.__brave_yt_shield_active = true;

    console.log('[BraveShield] Khởi chạy YouTube Engine 4.0...');

    // ==============================================================
    // 1. TẨY SẠCH DỮ LIỆU QUẢNG CÁO (INNERTUBE API)
    // ==============================================================
    function sanitizePlayerObj(obj) {
        if (!obj || typeof obj !== 'object') return obj;

        if (obj.playerResponse) {
            delete obj.playerResponse.adPlacements;
            delete obj.playerResponse.adSlots;
            delete obj.playerResponse.playerAds;
        }
        delete obj.adPlacements;
        delete obj.adSlots;
        delete obj.playerAds;
        delete obj.adBreakHeartbeatParams;

        return obj;
    }

    const originalJSONParse = JSON.parse;
    JSON.parse = function() {
        const result = originalJSONParse.apply(this, arguments);
        try {
            return sanitizePlayerObj(result);
        } catch (e) {
            return result;
        }
    };

    let initialPlayerResponseVal = window.ytInitialPlayerResponse;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
        get: function() { return initialPlayerResponseVal; },
        set: function(val) { initialPlayerResponseVal = sanitizePlayerObj(val); },
        configurable: true
    });

    if (window.fetch) {
        const originalFetch = window.fetch;
        window.fetch = async function(...args) {
            const response = await originalFetch.apply(this, args);
            const url = args[0] ? (typeof args[0] === 'string' ? args[0] : args[0].url) : '';

            if (url && url.includes('/youtubei/v1/player')) {
                try {
                    const clone = response.clone();
                    const json = await clone.json();
                    const sanitized = sanitizePlayerObj(json);
                    return new Response(JSON.stringify(sanitized), {
                        status: response.status,
                        statusText: response.statusText,
                        headers: response.headers
                    });
                } catch (e) {
                    return response;
                }
            }
            return response;
        };
    }

    // ==============================================================
    // 2. KHẮC CHẾ QUẢNG CÁO GHÉP LUỒNG (SSAI) - TUA 16X & AUTO-SKIP
    // ==============================================================
    let isAdPlaying = false;
    let savedVolume = 1.0;

    setInterval(function() {
        const video = document.querySelector('video');
        const adShowingElement = document.querySelector('.ad-showing, .ad-interrupting, .video-ads, [class*="ytp-ad-player-overlay"]');

        if (adShowingElement && video) {
            if (!isAdPlaying) {
                isAdPlaying = true;
                savedVolume = video.volume;
            }

            video.muted = true;
            video.playbackRate = 16.0;

            if (isFinite(video.duration) && video.duration > 0) {
                video.currentTime = video.duration;
            }

            const skipButtons = document.querySelectorAll(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, button[class*="skip-button"], .ytp-ad-skip-button-text'
            );
            skipButtons.forEach(btn => {
                try { btn.click(); } catch (err) {}
            });
        } else if (isAdPlaying && video) {
            isAdPlaying = false;
            video.playbackRate = 1.0;
            video.muted = false;
            video.volume = savedVolume;
        }
    }, 150);

    // ==============================================================
    // 3. DUY TRÌ ÂM THANH KHI TẮT MÀN HÌNH & CHỐNG TỰ DỪNG KHI THU NHỎ (PLAYER_VIEW_TOO_SMALL)
    // ==============================================================
    ['visibilitychange', 'webkitvisibilitychange', 'pagehide'].forEach(eventType => {
        window.addEventListener(eventType, function(e) {
            e.stopImmediatePropagation();
        }, true);
        document.addEventListener(eventType, function(e) {
            e.stopImmediatePropagation();
        }, true);
    });

    Object.defineProperty(document, 'hidden', { get: function() { return false; } });
    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });

    // Cờ kiểm soát: Chỉ cho phép pause khi người dùng hoặc nút Native PiP chủ động bấm
    window.__isUserExplicitPause = false;
    window.addEventListener('pointerdown', function(e) {
        if (e.target && e.target.closest && e.target.closest('.ytp-play-button, button[aria-label*="Pause" i], button[aria-label*="Tạm dừng" i]')) {
            window.__isUserExplicitPause = true;
            setTimeout(() => { window.__isUserExplicitPause = false; }, 800);
        }
    }, true);

    const nativeMediaPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function() {
        if (window.__isUserExplicitPause) {
            window.__isUserExplicitPause = false;
            return nativeMediaPause.apply(this, arguments);
        }

        // Bỏ qua tất cả các lệnh pause do YouTube tự gọi khi thu nhỏ PiP hoặc tắt màn hình!
        console.log('[BraveShield] Đã chặn lệnh tự dừng video do kích thước nhỏ hoặc chạy nền!');
        return;
    };

    // ==============================================================
    // 4. CƠ CHẾ VIDEO POPUP PIP THỰC THỤ (CÔ LẬP THẺ VIDEO KHỎI TRANG WEB)
    // ==============================================================
    window.__enterPipMode = function() {
        console.log('[BraveShield] Bật chế độ Video Popup PiP thực thụ...');
        document.documentElement.classList.add('pip-active');
        document.body.classList.add('pip-active');

        const video = document.querySelector('video');
        if (video) {
            // Xóa bỏ thuộc tính transform của tất cả thẻ cha để thẻ video có thể bung tràn màn hình
            let parent = video.parentElement;
            while (parent && parent !== document.body) {
                parent.style.transform = 'none';
                parent.style.webkitTransform = 'none';
                parent = parent.parentElement;
            }

            // Ép thẻ video tràn 100% khung hình PiP và đè lên toàn bộ các thành phần web
            video.style.cssText = 'position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;z-index:2147483647!important;object-fit:contain!important;background:#000000!important;';
        }
    };

    window.__exitPipMode = function() {
        console.log('[BraveShield] Thoát chế độ Video Popup PiP...');
        document.documentElement.classList.remove('pip-active');
        document.body.classList.remove('pip-active');

        const video = document.querySelector('video');
        if (video) {
            video.style.cssText = '';
            let parent = video.parentElement;
            while (parent && parent !== document.body) {
                parent.style.transform = '';
                parent.style.webkitTransform = '';
                parent = parent.parentElement;
            }
        }
    };

    // ==============================================================
    // 5. ĐỒNG BỘ TRẠNG THÁI PHÁT VỚI ANDROID NATIVE
    // ==============================================================
    function syncNativeState() {
        if (!window.AndroidBridge) return;

        const video = document.querySelector('video');
        if (video && !video.paused && !video.ended) {
            const titleEl = document.querySelector('h1.slim-video-metadata-title, .ytm-slim-video-metadata-renderer-title, .media-item-headline');
            const channelEl = document.querySelector('.ytm-channel-thumbnail-with-profile-name, .slim-owner-channel-name, .subhead');
            const title = titleEl ? titleEl.innerText.trim() : 'YouTube Lite';
            const channel = channelEl ? channelEl.innerText.trim() : '';

            try { window.AndroidBridge.onVideoPlaying(title, channel); } catch (e) {}
        } else {
            try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
        }
    }

    document.addEventListener('play', function(e) {
        if (e.target && e.target.tagName === 'VIDEO') syncNativeState();
    }, true);

    document.addEventListener('pause', function(e) {
        if (e.target && e.target.tagName === 'VIDEO') {
            setTimeout(syncNativeState, 200);
        }
    }, true);

    setInterval(syncNativeState, 1500);

    console.log('[BraveShield] Đã kích hoạt toàn bộ tính năng!');
})();
