/**
 * BRAVE SHIELDS SCRIPTLET DÀNH CHO YOUTUBE LITE
 * 1. Chặn và gọt sạch metadata quảng cáo (InnerTube & SSAI 16x)
 * 2. Cơ chế chạy nền & Chống tự động ngắt video khi ở kích thước nhỏ (PLAYER_VIEW_TOO_SMALL)
 * 3. Chế độ Video Popup PiP: xem scriptlets/pip_controller.js (được Kotlin chèn riêng)
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

    // Bọc try/catch: nếu trang đã khai báo `var ytInitialPlayerResponse` (thuộc tính non-configurable)
    // thì defineProperty sẽ ném lỗi và làm dừng toàn bộ scriptlet (mất cả đồng bộ Play/Pause với Android).
    try {
        let initialPlayerResponseVal = sanitizePlayerObj(window.ytInitialPlayerResponse);
        Object.defineProperty(window, 'ytInitialPlayerResponse', {
            get: function() { return initialPlayerResponseVal; },
            set: function(val) { initialPlayerResponseVal = sanitizePlayerObj(val); },
            configurable: true
        });
    } catch (e) {
        try { sanitizePlayerObj(window.ytInitialPlayerResponse); } catch (e2) {}
    }

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

    try {
        Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
    } catch (e) {}

    // Cờ kiểm soát: Chỉ cho phép pause khi người dùng hoặc nút Native PiP chủ động bấm
    // - __isUserExplicitPause: cho phép đúng 1 lệnh pause
    // - __braveAllowPauseUntil: cho phép pause trong một khoảng thời gian (nút Pause trên PiP)
    window.__isUserExplicitPause = false;
    window.__braveAllowPauseUntil = 0;
    window.addEventListener('pointerdown', function(e) {
        if (e.target && e.target.closest && e.target.closest('.ytp-play-button, button[aria-label*="Pause" i], button[aria-label*="Tạm dừng" i]')) {
            window.__isUserExplicitPause = true;
            setTimeout(() => { window.__isUserExplicitPause = false; }, 800);
        }
    }, true);

    if (!window.__braveNativePlay && window.HTMLMediaElement) {
        window.__braveNativePlay = HTMLMediaElement.prototype.play;
    }
    const nativeMediaPause = window.__braveNativePause || HTMLMediaElement.prototype.pause;
    window.__braveNativePause = nativeMediaPause;

    HTMLMediaElement.prototype.pause = function() {
        if (window.__braveUserPaused) {
            return nativeMediaPause.apply(this, arguments);
        }
        if (window.__braveAllowPauseUntil && Date.now() < window.__braveAllowPauseUntil) {
            window.__isUserExplicitPause = false;
            return nativeMediaPause.apply(this, arguments);
        }
        if (window.__isUserExplicitPause) {
            window.__isUserExplicitPause = false;
            return nativeMediaPause.apply(this, arguments);
        }

        // Bỏ qua tất cả các lệnh pause do YouTube tự gọi khi thu nhỏ PiP hoặc tắt màn hình!
        console.log('[BraveShield] Đã chặn lệnh tự dừng video do kích thước nhỏ hoặc chạy nền!');
        return;
    };

    // ==============================================================
    // 4. TỈ LỆ KHUNG HÌNH VIDEO CHO CỬA SỔ PIP
    //    (Phần cô lập video khi vào PiP nằm ở scriptlets/pip_controller.js)
    // ==============================================================
    // Chọn thẻ video chính: ưu tiên video đang phát, sau đó video có độ phân giải lớn nhất
    function pickMainVideo() {
        if (window.__bravePip && window.__bravePip.mainVideo) return window.__bravePip.mainVideo();
        const videos = Array.prototype.slice.call(document.querySelectorAll('video'));
        if (videos.length === 0) return null;
        const playing = videos.filter(function(v) { return !v.paused && !v.ended; });
        const pool = playing.length ? playing : videos;
        let best = pool[0];
        let bestScore = -1;
        pool.forEach(function(v) {
            const score = (v.videoWidth * v.videoHeight) || (v.offsetWidth * v.offsetHeight);
            if (score > bestScore) { best = v; bestScore = score; }
        });
        return best;
    }

    // Báo tỉ lệ khung hình thật của video để cửa sổ PiP khớp với video (không bị viền thừa)
    let lastReportedSize = '';
    function reportVideoSize(video) {
        if (!window.AndroidBridge || !video || !video.videoWidth || !video.videoHeight) return;
        const key = video.videoWidth + 'x' + video.videoHeight;
        if (key === lastReportedSize) return;
        lastReportedSize = key;
        try { window.AndroidBridge.onVideoSize(video.videoWidth, video.videoHeight); } catch (e) {}
    }

    ['loadedmetadata', 'resize'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') reportVideoSize(e.target);
        }, true);
    });

    // ==============================================================
    // 5. ĐỒNG BỘ TRẠNG THÁI PHÁT VỚI ANDROID NATIVE
    // ==============================================================
    function syncNativeState() {
        if (!window.AndroidBridge) return;

        const video = pickMainVideo();
        if (video) reportVideoSize(video);
        if (video && !video.paused && !video.ended) {
            window.__braveUserPaused = false;
            const titleEl = document.querySelector('h1.slim-video-metadata-title, .ytm-slim-video-metadata-renderer-title, .media-item-headline');
            const channelEl = document.querySelector('.ytm-channel-thumbnail-with-profile-name, .slim-owner-channel-name, .subhead');
            const title = titleEl ? titleEl.innerText.trim() : 'YouTube Lite';
            const channel = channelEl ? channelEl.innerText.trim() : '';

            try { window.AndroidBridge.onVideoPlaying(title, channel); } catch (e) {}
        } else {
            // Nếu vừa bấm Play trên PiP và video đang khởi chạy (Promise play chưa xong), không báo paused đè lên
            if (window.__bravePlayPendingUntil && Date.now() < window.__bravePlayPendingUntil) {
                return;
            }
            try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
        }
    }

    ['play', 'playing'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                window.__braveUserPaused = false;
                syncNativeState();
            }
        }, true);
    });

    document.addEventListener('pause', function(e) {
        if (e.target && e.target.tagName === 'VIDEO') {
            setTimeout(syncNativeState, 200);
        }
    }, true);

    setInterval(syncNativeState, 1000);

    console.log('[BraveShield] Đã kích hoạt toàn bộ tính năng!');
})();
