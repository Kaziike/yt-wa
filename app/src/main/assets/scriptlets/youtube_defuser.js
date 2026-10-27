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
    function isPipOrMiniActive() {
        const isPip = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
        const isMini = !!(window.__braveMiniPlayer && typeof window.__braveMiniPlayer.isMinimized === 'function' && window.__braveMiniPlayer.isMinimized());
        return isPip || isMini;
    }

    ['visibilitychange', 'webkitvisibilitychange', 'pagehide'].forEach(eventType => {
        window.addEventListener(eventType, function(e) {
            const isPipOrMini = isPipOrMiniActive();
            if (isBgPlayEnabled() || isPipOrMini) {
                e.stopImmediatePropagation();
            }
        }, true);
        document.addEventListener(eventType, function(e) {
            const isPipOrMini = isPipOrMiniActive();
            if (isBgPlayEnabled() || isPipOrMini) {
                e.stopImmediatePropagation();
            }
        }, true);
    });

    try {
        Object.defineProperty(document, 'hidden', {
            get: function() {
                const isPipOrMini = isPipOrMiniActive();
                if (isBgPlayEnabled() || isPipOrMini) return false;
                return !!window.__braveAppInBackground;
            },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function() {
                const isPipOrMini = isPipOrMiniActive();
                if (isBgPlayEnabled() || isPipOrMini) return 'visible';
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
    // 3.5. RE-RENDER KICK & TAP FALLBACK INTERCEPTOR CHO PLAYER CONTROLS
    // Giải quyết triệt để lỗi nút Play/Pause bị đơ / kẹt layer khi mở từ PiP ra full app
    // ==============================================================
    function kickPlayerReRender() {
        try {
            const video = pickMainVideo() || document.querySelector('video');
            if (video && isFinite(video.currentTime)) {
                const cur = video.currentTime;
                video.currentTime = cur;
                video.dispatchEvent(new Event('seeking'));
                video.dispatchEvent(new Event('seeked'));
                video.dispatchEvent(new Event('timeupdate'));
            }
        } catch (e) {}
        try { window.dispatchEvent(new Event('resize')); } catch (e) {}
        try { window.dispatchEvent(new Event('orientationchange')); } catch (e) {}
    }
    window.__braveKickReRender = kickPlayerReRender;

    // Đánh thức controls re-render khi app focus hoặc tab hiển thị trở lại
    window.addEventListener('focus', function() {
        const isPip = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
        if (!isPip && !window.__braveAppInBackground) {
            setTimeout(kickPlayerReRender, 100);
            setTimeout(kickPlayerReRender, 350);
        }
    }, true);

    document.addEventListener('visibilitychange', function() {
        if (!document.hidden) {
            const isPip = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
            if (!isPip && !window.__braveAppInBackground) {
                setTimeout(kickPlayerReRender, 100);
                setTimeout(kickPlayerReRender, 350);
            }
        }
    }, true);

    function isPlayPauseElement(target) {
        if (!target || !target.closest) return false;

        // Bỏ qua nếu chạm vào các nút điều hướng / tính năng khác
        if (target.closest(
            '.player-control-next-icon, .player-control-previous-icon, ' +
            '.ytp-next-button, .ytp-prev-button, ' +
            'button[aria-label*="Next" i], button[aria-label*="Previous" i], ' +
            'button[aria-label*="Tiếp theo" i], button[aria-label*="Trước" i], ' +
            'button[aria-label*="Tiếp" i], ' +
            '[class*="seek"], [class*="progress"], [class*="slider"], ' +
            '[class*="settings"], [class*="subtitle"], [class*="caption"]'
        )) {
            return false;
        }

        // Nhận diện các selector nút Play/Pause phổ biến của YouTube Mobile Web & Desktop
        if (target.closest(
            '.player-control-play-pause-icon, .ytp-play-button, ' +
            'button[aria-label*="Pause" i], button[aria-label*="Play" i], ' +
            'button[aria-label*="Tạm dừng" i], button[aria-label*="Phát" i], ' +
            '[aria-label*="Pause" i], [aria-label*="Play" i], ' +
            '[aria-label*="Tạm dừng" i], [aria-label*="Phát" i], ' +
            '[class*="play-pause-icon"], [class*="play-pause-button"], ' +
            '[data-action="play-pause"]'
        )) {
            return true;
        }

        // Nếu chạm trực tiếp vào container giữa (.player-controls-middle)
        const middle = target.closest('.player-controls-middle');
        if (middle) {
            const rect = middle.getBoundingClientRect();
            if (rect.width > 0 && rect.height > 0) {
                return true;
            }
        }

        return false;
    }

    let fallbackCheckTimer = null;
    let lastFallbackTapTime = 0;

    function setupPlayPauseFallback() {
        function handleControlsTap(e) {
            try {
                const target = e.target;
                if (!isPlayPauseElement(target)) return;

                const now = Date.now();
                // Bỏ qua nếu là sự kiện lặp lại trong chuỗi pointerdown -> touchend -> click (trong vòng 300ms)
                if (now - lastFallbackTapTime < 300) {
                    return;
                }
                lastFallbackTapTime = now;

                const video = pickMainVideo() || document.querySelector('video');
                if (!video) return;

                const wasPaused = video.paused;

                // Đánh dấu người dùng đang chủ động thao tác nút controls
                window.__isUserExplicitPause = true;
                window.__braveAllowPauseUntil = Date.now() + 1000;
                if (!wasPaused) {
                    // Người dùng muốn pause: gỡ bỏ ngay cờ bảo vệ play
                    window.__braveProtectPlayUntil = 0;
                    window.__bravePlayPendingUntil = 0;
                }

                if (fallbackCheckTimer) {
                    clearTimeout(fallbackCheckTimer);
                    fallbackCheckTimer = null;
                }

                // Cho phép sự kiện truyền tự nhiên đến YouTube Player Core.
                // Sau 120ms kiểm tra lại: Nếu video.paused === wasPaused (YouTube bị kẹt layer / không xử lý):
                // Tự động can thiệp gọi trực tiếp play/pause native và kick re-render!
                fallbackCheckTimer = setTimeout(function() {
                    fallbackCheckTimer = null;
                    try {
                        const curVideo = pickMainVideo() || video;
                        if (!curVideo) return;

                        if (curVideo.paused === wasPaused) {
                            console.log('[BraveShield] Phát hiện nút controls bị kẹt layer! Kích hoạt Tap Fallback Interceptor ->', wasPaused ? 'Play' : 'Pause');

                            if (wasPaused) {
                                // Đang dừng -> Chuyển sang Phát
                                window.__braveUserPaused = false;
                                window.__braveProtectPlayUntil = Date.now() + 1500;
                                const playFn = window.__braveNativePlay || curVideo.play;
                                if (playFn) {
                                    try {
                                        const r = playFn.call(curVideo);
                                        if (r && typeof r.catch === 'function') r.catch(function() {});
                                    } catch (errPlay) {}
                                }
                                try { curVideo.dispatchEvent(new Event('play')); } catch (e1) {}
                                try { curVideo.dispatchEvent(new Event('playing')); } catch (e2) {}
                            } else {
                                // Đang phát -> Chuyển sang Tạm dừng
                                window.__braveUserPaused = true;
                                window.__braveProtectPlayUntil = 0;
                                window.__braveAllowPauseUntil = Date.now() + 1000;
                                const pauseFn = window.__braveNativePause || curVideo.pause;
                                if (pauseFn) {
                                    try { pauseFn.call(curVideo); } catch (errPause) {}
                                }
                                try { curVideo.dispatchEvent(new Event('pause')); } catch (e3) {}
                            }

                            // Đánh thức pipeline re-render của YouTube Player
                            kickPlayerReRender();

                            syncNativeState();
                        }
                    } catch (errFallback) {
                        console.error('[BraveShield] Tap fallback error:', errFallback);
                    }
                }, 120);
            } catch (errMain) {}
        }

        // Lắng nghe ở capture phase (true)
        ['pointerdown', 'touchend', 'click'].forEach(function(eventType) {
            window.addEventListener(eventType, handleControlsTap, true);
        });
    }

    setupPlayPauseFallback();

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

        const isPipMode = !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
        const isMiniPlayer = !!(window.__braveMiniPlayer && typeof window.__braveMiniPlayer.isMinimized === 'function' && window.__braveMiniPlayer.isMinimized());
        const isBackground = !!window.__braveAppInBackground;
        const bgPlayEnabled = isBgPlayEnabled();

        // 2. KHI ĐANG MỞ FULL ỨNG DỤNG BÌNH THƯỜNG TRÊN MÀN HÌNH (!isPipMode && !isBackground && !isMiniPlayer):
        // Mọi lệnh pause phải được thông qua nativeMediaPause 100% tự nhiên, KHÔNG bị chặn bởi bất kỳ cờ thời gian nào!
        if (!isPipMode && !isBackground && !isMiniPlayer) {
            if (window.__braveProtectPlayUntil && Date.now() < window.__braveProtectPlayUntil) {
                console.log('[BraveShield] Đã chặn lệnh auto-pause trong thời gian bảo vệ Play!');
                return;
            }
            return nativeMediaPause.apply(this, arguments);
        }

        // 3. Nếu đang ở dưới nền nhưng người dùng đã tắt tính năng phát trong nền (và không ở chế độ PiP)
        if (isBackground && !isPipMode && !bgPlayEnabled) {
            return nativeMediaPause.apply(this, arguments);
        }

        // 4. Nếu vừa bấm Play từ PiP / Notification -> CHẶN TUYỆT ĐỐI mọi lệnh auto-pause của YouTube khi ở PiP hoặc chạy nền
        if (window.__braveProtectPlayUntil && Date.now() < window.__braveProtectPlayUntil) {
            console.log('[BraveShield] Đã chặn lệnh auto-pause trong thời gian bảo vệ Play (PiP/Background)!');
            return;
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
