/**
 * BRAVE SHIELDS SCRIPTLET DÀNH RIÊNG CHO YOUTUBE MOBILE (M.YOUTUBE.COM)
 * Chức năng:
 * 1. Hook JSON.parse & fetch để loại bỏ metadata quảng cáo
 * 2. Khắc chế SSAI (Server-Side Ads) bằng cơ chế Tua siêu tốc 16x + Auto-skip
 * 3. Chống ngắt video khi chạy nền (Bypass Background Pause)
 */

(function() {
    'use strict';
    if (window.__brave_yt_shield_active) return;
    window.__brave_yt_shield_active = true;

    console.log('[BraveShield] Khởi chạy YouTube Defuser Engine...');

    // ==============================================================
    // 1. TẨY SẠCH DỮ LIỆU QUẢNG CÁO TRONG OBJECT
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

    // Ghi đè JSON.parse để chặn ad metadata nạp vào web
    const originalJSONParse = JSON.parse;
    JSON.parse = function() {
        const result = originalJSONParse.apply(this, arguments);
        try {
            return sanitizePlayerObj(result);
        } catch (e) {
            return result;
        }
    };

    // Khóa thuộc tính ytInitialPlayerResponse
    let initialPlayerResponseVal = window.ytInitialPlayerResponse;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
        get: function() {
            return initialPlayerResponseVal;
        },
        set: function(val) {
            initialPlayerResponseVal = sanitizePlayerObj(val);
        },
        configurable: true
    });

    // Hook window.fetch để lọc gói tin /youtubei/v1/player
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
    // 2. KHẮC CHẾ SSAI (SERVER-SIDE AD INSERTION) - AUTO-SKIP & 16X
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

            // Tắt tiếng tức thì để không gây khó chịu
            video.muted = true;

            // Đẩy tốc độ tua tối đa để quét sạch quảng cáo
            video.playbackRate = 16.0;

            // Nhảy tới mốc kết thúc quảng cáo
            if (isFinite(video.duration) && video.duration > 0) {
                video.currentTime = video.duration;
            }

            // Tự click nút Skip ngay khi xuất hiện
            const skipButtons = document.querySelectorAll(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, button[class*="skip-button"], .ytp-ad-skip-button-text'
            );
            skipButtons.forEach(btn => {
                try { btn.click(); } catch (err) {}
            });
        } else if (isAdPlaying && video) {
            // Quảng cáo đã hết -> phục hồi trạng thái phát chuẩn
            isAdPlaying = false;
            video.playbackRate = 1.0;
            video.muted = false;
            video.volume = savedVolume;
        }
    }, 150);

    // ==============================================================
    // 3. BYPASS BACKGROUND PAUSE (CHO PHÉP PHÁT KHI TẮT MÀN HÌNH)
    // ==============================================================
    // Chặn sự kiện visibilitychange để YouTube không phát hiện màn hình bị tắt
    document.addEventListener('visibilitychange', function(e) {
        e.stopImmediatePropagation();
    }, true);

    Object.defineProperty(document, 'hidden', {
        get: function() { return false; }
    });
    Object.defineProperty(document, 'visibilityState', {
        get: function() { return 'visible'; }
    });

    console.log('[BraveShield] YouTube Defuser Engine đã kích hoạt thành công!');
})();
