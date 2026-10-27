/**
 * BRAVE SHIELDS SCRIPTLET DÀNH CHO YOUTUBE LITE
 * 1. Chặn và gọt sạch metadata quảng cáo (InnerTube & SSAI 16x)
 * 2. Cơ chế chạy nền & Chống tự động ngắt video khi ở kích thước nhỏ (PLAYER_VIEW_TOO_SMALL)
 * 3. Chế độ Video Popup PiP: xem scriptlets/pip_controller.js (được Kotlin chèn riêng)
 * 4. Đồng bộ trạng thái phát & Media Metadata với Android Native Bridge
 */

(function() {
    'use strict';
    if (window.__brave_yt_shield_active) return;
    window.__brave_yt_shield_active = true;

    window.__braveSettings = Object.assign({
        adblock: true,
        backgroundPlay: true,
        autoPip: true
    }, window.__braveSettings || {});

    if (typeof window.__braveAppInBackground === 'undefined') {
        window.__braveAppInBackground = false;
    }

    function isAdblockEnabled() {
        return !(window.__braveSettings && window.__braveSettings.adblock === false);
    }

    function isBgPlayEnabled() {
        return !(window.__braveSettings && window.__braveSettings.backgroundPlay === false);
    }

    console.log('[BraveShield] Khởi chạy YouTube Engine 4.0...');

    // ==============================================================
    // 1. TẨY SẠCH DỮ LIỆU QUẢNG CÁO (INNERTUBE API)
    // ==============================================================
    function sanitizePlayerObj(obj) {
        if (!obj || typeof obj !== 'object') return obj;
        if (!isAdblockEnabled()) return obj;

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
            if (!isAdblockEnabled()) return response;

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
        if (!isAdblockEnabled()) return;

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
            const isPipMode = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
            if (isBgPlayEnabled() || isPipMode) {
                e.stopImmediatePropagation();
            }
        }, true);
        document.addEventListener(eventType, function(e) {
            const isPipMode = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
            if (isBgPlayEnabled() || isPipMode) {
                e.stopImmediatePropagation();
            }
        }, true);
    });

    try {
        Object.defineProperty(document, 'hidden', {
            get: function() {
                const isPipMode = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
                if (isBgPlayEnabled() || isPipMode) return false;
                return !!window.__braveAppInBackground;
            },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function() {
                const isPipMode = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
                if (isBgPlayEnabled() || isPipMode) return 'visible';
                return window.__braveAppInBackground ? 'hidden' : 'visible';
            },
            configurable: true
        });
    } catch (e) {}

    // Cờ kiểm soát:
    // - __braveUserPaused: người dùng chủ động bấm Pause từ PiP / Notification
    // - __braveProtectPlayUntil: khoảng thời gian bảo vệ tuyệt đối sau khi bấm Play từ PiP / Notification
    // - __isUserExplicitPause & __braveAllowPauseUntil: cho phép pause khi người dùng thao tác trực tiếp
    window.__isUserExplicitPause = false;
    window.__braveAllowPauseUntil = 0;

    window.addEventListener('pointerdown', function(e) {
        if (e.target && e.target.closest && e.target.closest(
            '.ytp-play-button, .player-control-play-pause-icon, #player-control-overlay button, ' +
            'button[aria-label*="Pause" i], button[aria-label*="Tạm dừng" i], ' +
            '[aria-label*="Pause" i], [aria-label*="Tạm dừng" i]'
        )) {
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
        // 1. Nếu người dùng chủ động bấm Pause từ PiP / Notification -> cho phép ngay
        if (window.__braveUserPaused === true) {
            return nativeMediaPause.apply(this, arguments);
        }

        // 2. Nếu vừa bấm Play từ PiP / Notification -> CHẶN TUYỆT ĐỐI mọi lệnh auto-pause của YouTube
        if (window.__braveProtectPlayUntil && Date.now() < window.__braveProtectPlayUntil) {
            console.log('[BraveShield] Đã chặn lệnh auto-pause trong thời gian bảo vệ Play!');
            return;
        }

        const isPipMode = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
        const isPipTransition = !!(window.__bravePipTransitionUntil && Date.now() < window.__bravePipTransitionUntil);
        const isBackground = !!window.__braveAppInBackground;
        const bgPlayEnabled = isBgPlayEnabled();

        // 3. KHI ĐANG MỞ FULL ỨNG DỤNG BÌNH THƯỜNG TRÊN MÀN HÌNH:
        // Luôn cho phép nativeMediaPause để mọi nút Play/Pause trên giao diện web hoạt động tự nhiên 100%, không bao giờ bị khóa!
        if (!isPipMode && !isPipTransition && !isBackground) {
            return nativeMediaPause.apply(this, arguments);
        }

        // Nếu đang ở dưới nền nhưng người dùng đã tắt tính năng phát trong nền (và không ở chế độ PiP)
        if (isBackground && !isPipMode && !isPipTransition && !bgPlayEnabled) {
            return nativeMediaPause.apply(this, arguments);
        }

        // 4. Khi đang ở PiP, đang chuyển tiếp PiP, hoặc chạy nền (với bgPlayEnabled):
        // Chỉ cho phép pause nếu có cờ chủ động từ người dùng
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
    // 5. ĐỒNG BỘ TRẠNG THÁI PHÁT & MEDIA METADATA VỚI ANDROID NATIVE
    // ==============================================================
    function extractMetadata() {
        const titleEl = document.querySelector(
            'h1.slim-video-metadata-title, .ytm-slim-video-metadata-renderer-title, .media-item-headline, .ytp-title-link'
        );
        const channelEl = document.querySelector(
            '.ytm-channel-thumbnail-with-profile-name, .slim-owner-channel-name, .subhead, .ytd-channel-name'
        );
        const rawTitle = titleEl && titleEl.innerText ? titleEl.innerText.trim() : '';
        const docTitle = document.title ? document.title.replace(/\s*-\s*YouTube$/i, '').trim() : '';
        const title = rawTitle || docTitle || 'YouTube Lite';
        const channel = channelEl && channelEl.innerText ? channelEl.innerText.trim() : '';
        return { title: title, channel: channel };
    }

    function syncNativeState() {
        if (!window.AndroidBridge) return;

        const video = pickMainVideo();
        if (video) reportVideoSize(video);

        const meta = extractMetadata();
        const isPlaying = !!(video && !video.paused && !video.ended);
        const positionMs = video && isFinite(video.currentTime) ? Math.round(video.currentTime * 1000) : 0;
        const durationMs = video && isFinite(video.duration) && video.duration > 0 ? Math.round(video.duration * 1000) : 0;

        if (isPlaying) {
            window.__braveUserPaused = false;
            try { window.AndroidBridge.onVideoPlaying(meta.title, meta.channel); } catch (e) {}
            try {
                if (typeof window.AndroidBridge.onMediaStateChanged === 'function') {
                    window.AndroidBridge.onMediaStateChanged(true, meta.title, meta.channel, positionMs, durationMs);
                }
            } catch (e) {}
        } else {
            // Nếu vừa bấm Play trên PiP và video đang khởi chạy (Promise play chưa xong), không báo paused đè lên
            if (window.__bravePlayPendingUntil && Date.now() < window.__bravePlayPendingUntil) {
                return;
            }
            try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
            try {
                if (typeof window.AndroidBridge.onMediaStateChanged === 'function') {
                    window.AndroidBridge.onMediaStateChanged(false, meta.title, meta.channel, positionMs, durationMs);
                }
            } catch (e) {}
        }
    }

    window.__braveSyncMediaState = syncNativeState;

    ['play', 'playing'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                window.__braveUserPaused = false;
                syncNativeState();
            }
        }, true);
    });

    ['loadedmetadata', 'durationchange', 'seeked', 'ratechange'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
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
