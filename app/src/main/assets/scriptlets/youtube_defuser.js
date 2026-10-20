/**
 * BRAVE SHIELDS SCRIPTLET DÀNH CHO YOUTUBE LITE
 * 1. Chặn và gọt sạch metadata quảng cáo (InnerTube & SSAI 16x)
 * 2. Cơ chế chạy nền: Ngăn YouTube tự dừng nhạc khi tắt màn hình
 * 3. Đồng bộ trạng thái phát với Android Native Bridge
 */

(function() {
    'use strict';
    if (window.__brave_yt_shield_active) return;
    window.__brave_yt_shield_active = true;

    console.log('[BraveShield] Khởi chạy YouTube Engine...');

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
    // 3. DUY TRÌ ÂM THANH KHI TẮT MÀN HÌNH (THEO CƠ CHẾ BRAVE)
    // ==============================================================
    ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur'].forEach(eventType => {
        window.addEventListener(eventType, function(e) {
            e.stopImmediatePropagation();
        }, true);
        document.addEventListener(eventType, function(e) {
            e.stopImmediatePropagation();
        }, true);
    });

    Object.defineProperty(document, 'hidden', { get: function() { return false; } });
    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });

    let isUserExplicitPause = false;
    window.addEventListener('pointerdown', function(e) {
        if (e.target && e.target.closest && e.target.closest('.ytp-play-button, button[aria-label*="Pause" i], button[aria-label*="Tạm dừng" i]')) {
            isUserExplicitPause = true;
            setTimeout(() => { isUserExplicitPause = false; }, 800);
        }
    }, true);

    const nativeMediaPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function() {
        if (document.hidden && !isUserExplicitPause) {
            console.log('[BraveShield] Bỏ qua lệnh ngắt âm thanh ngầm khi tắt màn hình!');
            return;
        }
        return nativeMediaPause.apply(this, arguments);
    };

    // ==============================================================
    // 4. ĐỒNG BỘ TRẠNG THÁI PHÁT
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
