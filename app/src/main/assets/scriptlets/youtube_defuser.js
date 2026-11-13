/**
 * BRAVE SHIELDS SCRIPTLET DÀNH CHO LITETUBE
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
    function isPipActive() {
        return !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive()) || !!window.__braveEnteringPip;
    }

    ['visibilitychange', 'webkitvisibilitychange', 'pagehide'].forEach(eventType => {
        window.addEventListener(eventType, function(e) {
            if (isBgPlayEnabled() || isPipActive()) {
                e.stopImmediatePropagation();
            }
        }, true);
        document.addEventListener(eventType, function(e) {
            if (isBgPlayEnabled() || isPipActive()) {
                e.stopImmediatePropagation();
            }
        }, true);
    });

    try {
        Object.defineProperty(document, 'hidden', {
            get: function() {
                if (isBgPlayEnabled() || isPipActive()) return false;
                return !!window.__braveAppInBackground;
            },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function() {
                if (isBgPlayEnabled() || isPipActive()) return 'visible';
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

    if (!window.__braveNativePlay && window.HTMLMediaElement) {
        window.__braveNativePlay = HTMLMediaElement.prototype.play;
    }
    if (!window.__braveNativePause && window.HTMLMediaElement) {
        window.__braveNativePause = HTMLMediaElement.prototype.pause;
    }
    const nativeMediaPlay = window.__braveNativePlay || HTMLMediaElement.prototype.play;
    const nativeMediaPause = window.__braveNativePause || HTMLMediaElement.prototype.pause;

    // ==============================================================
    // 3.5. ĐÁNH THỨC CONTROLS KHI RESIZE
    // ==============================================================
    function kickPlayerReRender() {
        try {
            const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (player) {
                if (typeof player.wakeUpControls === 'function') player.wakeUpControls();
                if (typeof player.updateDimensions === 'function') player.updateDimensions();
            }
        } catch (e) {}
        try { window.dispatchEvent(new Event('resize')); } catch (e) {}
    }
    window.__braveKickReRender = kickPlayerReRender;

    HTMLMediaElement.prototype.pause = function() {
        // 1. Thao tác chủ động từ người dùng (nút PiP, Notification, hoặc tap vào player controls):
        // Luôn cho phép pause 100%, không bị bất kỳ cờ bảo vệ nào chặn
        if (window.__braveUserPaused === true ||
            window.__isUserExplicitPause === true ||
            (window.__braveAllowPauseUntil && Date.now() < window.__braveAllowPauseUntil)) {
            window.__isUserExplicitPause = false;
            window.__braveProtectPlayUntil = 0;
            return nativeMediaPause.apply(this, arguments);
        }

        // 2. Nếu đang trong thời gian bảo vệ phát (khi vừa vào PiP, vừa từ PiP phóng to lên, hoặc vừa bấm Play):
        // Chặn tuyệt đối mọi lệnh auto-pause ngầm của YouTube!
        if (window.__braveProtectPlayUntil && Date.now() < window.__braveProtectPlayUntil) {
            console.log('[BraveShield] Đã chặn lệnh auto-pause trong thời gian bảo vệ phát (PiP/Transition/Play)!');
            return;
        }

        const isPipMode = isPipActive();
        const isBackground = !!window.__braveAppInBackground;
        const bgPlayEnabled = isBgPlayEnabled();

        // 3. KHI ĐANG MỞ FULL ỨNG DỤNG BÌNH THƯỜNG TRÊN MÀN HÌNH (!isPipMode && !isBackground):
        // Mọi lệnh pause từ player web được thông qua nativeMediaPause tự nhiên
        if (!isPipMode && !isBackground) {
            return nativeMediaPause.apply(this, arguments);
        }

        // 4. Nếu đang ở dưới nền nhưng người dùng đã tắt tính năng phát trong nền (và không ở chế độ PiP)
        if (isBackground && !isPipMode && !bgPlayEnabled) {
            return nativeMediaPause.apply(this, arguments);
        }

        // 5. Bỏ qua tất cả các lệnh pause do YouTube tự gọi khi thu nhỏ PiP hoặc tắt màn hình!
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
    // 5. ĐỒNG BỘ TRẠNG THÁI PHÁT, AUTO-UNMUTE & MEDIA METADATA VỚI ANDROID NATIVE
    // ==============================================================
    let lastAutoUnmuteKey = '';
    let autoUnmuteWindowUntil = 0;
    let userMutedVideo = false;

    function extractVideoId() {
        try {
            const u = new URL(window.location.href);
            const v = u.searchParams.get('v');
            if (v) return v;
            const m = window.location.pathname.match(/\/shorts\/([a-zA-Z0-9_-]{6,})/);
            if (m && m[1]) return m[1];
        } catch (e) {}
        try {
            const pr = window.ytInitialPlayerResponse;
            if (pr && pr.videoDetails && pr.videoDetails.videoId) {
                return String(pr.videoDetails.videoId);
            }
        } catch (e) {}
        try {
            const p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (p && typeof p.getVideoData === 'function') {
                const vd = p.getVideoData();
                if (vd && vd.video_id) return String(vd.video_id);
            }
        } catch (e) {}
        return '';
    }

    function isElementVisible(el) {
        if (!el) return false;
        try {
            if (el.offsetWidth <= 0 && el.offsetHeight <= 0) return false;
            const st = window.getComputedStyle(el);
            return st.display !== 'none' && st.visibility !== 'hidden' && st.opacity !== '0';
        } catch (e) {
            return false;
        }
    }

    // Ghi nhận nếu người dùng chủ động nhấn nút Mute trên UI player
    ['touchstart', 'pointerdown', 'mousedown', 'click'].forEach(function(type) {
        window.addEventListener(type, function(e) {
            const t = e.target;
            if (!t || !t.closest) return;
            const muteBtn = t.closest('.ytp-mute-button');
            if (muteBtn) {
                const v = pickMainVideo();
                if (v && !v.muted) {
                    userMutedVideo = true;
                } else if (v && v.muted) {
                    userMutedVideo = false;
                }
            }
        }, true);
    });

    function isAdShowingNow() {
        if (document.querySelector('.ad-showing, .ad-interrupting')) return true;
        const overlay = document.querySelector('[class*="ytp-ad-player-overlay"]');
        return isElementVisible(overlay);
    }

    function tryAutoUnmute() {
        try {
            if (isAdPlaying || isAdShowingNow()) return;

            const video = pickMainVideo();
            if (!video) return;

            const curVid = extractVideoId();
            const key = (curVid || (location.pathname + location.search)) + '|' + (video.currentSrc || '');
            if (key && key !== lastAutoUnmuteKey) {
                lastAutoUnmuteKey = key;
                autoUnmuteWindowUntil = Date.now() + 8000;
                userMutedVideo = false;
            }

            if (userMutedVideo) return;

            const candidates = document.querySelectorAll(
                '.ytp-unmute, button.ytp-unmute, .ytm-unmute-button, button[class*="unmute" i], button[aria-label*="Tap to unmute" i], button[aria-label*="Bật tiếng" i]'
            );
            let visibleUnmuteBtn = null;
            for (let i = 0; i < candidates.length; i++) {
                const btn = candidates[i];
                if (!btn.classList.contains('ytp-mute-button') && isElementVisible(btn)) {
                    visibleUnmuteBtn = btn;
                    break;
                }
            }

            const inWindow = Date.now() < autoUnmuteWindowUntil;
            if (!visibleUnmuteBtn && !inWindow) return;

            const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (visibleUnmuteBtn) {
                try { visibleUnmuteBtn.click(); } catch (eClick) {}
            }
            if (player) {
                try {
                    if (typeof player.isMuted === 'function' && player.isMuted() && typeof player.unMute === 'function') {
                        player.unMute();
                    }
                    if (typeof player.getVolume === 'function' && player.getVolume() === 0 && typeof player.setVolume === 'function') {
                        player.setVolume(100);
                    }
                } catch (ePl) {}
            }
            if (video.muted) {
                video.muted = false;
            }
            if (video.volume === 0) {
                video.volume = 1.0;
            }
        } catch (e) {}
    }

    window.__braveTryAutoUnmute = tryAutoUnmute;

    function extractMetadata() {
        const videoId = extractVideoId();
        let apiTitle = '';
        let apiAuthor = '';
        try {
            const p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (p && typeof p.getVideoData === 'function') {
                const vd = p.getVideoData();
                if (vd && (!videoId || !vd.video_id || vd.video_id === videoId)) {
                    if (vd.title) apiTitle = String(vd.title).trim();
                    if (vd.author) apiAuthor = String(vd.author).trim();
                }
            }
        } catch (e) {}

        const titleEl = document.querySelector(
            'h1.slim-video-metadata-title, .ytm-slim-video-metadata-renderer-title, .media-item-headline, .ytp-title-link'
        );
        const channelEl = document.querySelector(
            '.ytm-channel-thumbnail-with-profile-name, .slim-owner-channel-name, .subhead, .ytd-channel-name'
        );
        const rawTitle = titleEl && titleEl.innerText ? titleEl.innerText.trim() : '';
        const docTitle = document.title ? document.title.replace(/\s*-\s*YouTube$/i, '').trim() : '';
        const title = apiTitle || rawTitle || docTitle || 'LiteTube';
        const channel = apiAuthor || (channelEl && channelEl.innerText ? channelEl.innerText.trim() : '');
        return { videoId: videoId, title: title, channel: channel };
    }

    function reportMediaToBridge(isPlaying, meta, positionMs, durationMs) {
        if (!window.AndroidBridge) return;
        try {
            if (typeof window.AndroidBridge.onMediaStateWithId === 'function') {
                window.AndroidBridge.onMediaStateWithId(isPlaying, meta.videoId || '', meta.title, meta.channel, positionMs, durationMs);
            } else if (typeof window.AndroidBridge.onMediaStateChanged === 'function') {
                window.AndroidBridge.onMediaStateChanged(isPlaying, meta.title, meta.channel, positionMs, durationMs);
            }
        } catch (e) {}
    }

    function syncNativeState() {
        if (!window.AndroidBridge) return;

        tryAutoUnmute();

        const video = pickMainVideo();
        if (video) reportVideoSize(video);

        const meta = extractMetadata();
        const isPlaying = !!(video && !video.paused && !video.ended);
        const positionMs = video && isFinite(video.currentTime) ? Math.round(video.currentTime * 1000) : 0;
        const durationMs = video && isFinite(video.duration) && video.duration > 0 ? Math.round(video.duration * 1000) : 0;

        if (isPlaying) {
            window.__braveUserPaused = false;
            try { window.AndroidBridge.onVideoPlaying(meta.title, meta.channel); } catch (e) {}
            reportMediaToBridge(true, meta, positionMs, durationMs);
        } else {
            // Nếu vừa bấm Play trên PiP và video đang khởi chạy (Promise play chưa xong), không báo paused đè lên
            if (window.__bravePlayPendingUntil && Date.now() < window.__bravePlayPendingUntil) {
                return;
            }
            try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
            reportMediaToBridge(false, meta, positionMs, durationMs);
        }
    }

    window.__braveSyncMediaState = syncNativeState;

    ['play', 'playing'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                window.__braveUserPaused = false;
                tryAutoUnmute();
                syncNativeState();
            }
        }, true);
    });

    ['loadedmetadata', 'durationchange', 'seeked', 'ratechange'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                tryAutoUnmute();
                syncNativeState();
            }
        }, true);
    });

    document.addEventListener('volumechange', function(e) {
        if (e.target && e.target.tagName === 'VIDEO' && Date.now() < autoUnmuteWindowUntil && !userMutedVideo) {
            tryAutoUnmute();
        }
    }, true);

    document.addEventListener('pause', function(e) {
        if (e.target && e.target.tagName === 'VIDEO') {
            // Sự kiện pause thật chỉ xảy ra khi lệnh pause đã được cho phép (người dùng chủ động dừng)
            // → ghi nhận để khi mở lại app / thoát popup không tự ép phát lại video
            const inProtect = window.__braveProtectPlayUntil && Date.now() < window.__braveProtectPlayUntil;
            if (!inProtect && !e.target.ended && e.isTrusted) {
                window.__braveUserPaused = true;
            }
            setTimeout(syncNativeState, 200);
        }
    }, true);

    setInterval(function() {
        if (Date.now() < autoUnmuteWindowUntil && !userMutedVideo) {
            tryAutoUnmute();
        }
    }, 250);
    setInterval(syncNativeState, 1000);

    console.log('[BraveShield] Đã kích hoạt toàn bộ tính năng!');
})();
