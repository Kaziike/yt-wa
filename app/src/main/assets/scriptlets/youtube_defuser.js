/**
 * LITETUBE SHIELDS DEFUSER (EVENT-DRIVEN)
 * 1. Tẩy sạch metadata quảng cáo trong phản hồi InnerTube API (JSON.parse & fetch).
 * 2. Vượt quảng cáo ghép luồng SSAI theo sự kiện MutationObserver (không chạy setInterval mù,
 *    không bắt nhầm .video-ads rỗng, giữ nguyên tốc độ xem 1.25x/1.5x/2x của người dùng).
 * 3. Chống tự động ngắt âm thanh khi chạy nền / thu nhỏ PiP.
 * 4. Tự động bật tiếng video mới (Auto-Unmute) và đồng bộ trạng thái media theo DOM events.
 */
(function() {
    'use strict';

    if (window.__liteTubeDefuserActive) return;
    window.__liteTubeDefuserActive = true;

    var core = window.__liteTube;
    if (!core) return;

    function isAdblockEnabled() {
        return core.state.settings.adblock !== false;
    }

    function isBgPlayEnabled() {
        return core.state.settings.backgroundPlay !== false;
    }

    function isPipMode() {
        return core.state.mode === 'PIP' ||
            !!(window.__bravePip && typeof window.__bravePip.isActive === 'function' && window.__bravePip.isActive());
    }

    // ==============================================================
    // 1. TẨY SẠCH DỮ LIỆU QUẢNG CÁO (INNERTUBE API)
    // ==============================================================
    function sanitizePlayerObj(obj) {
        if (!obj || typeof obj !== 'object' || !isAdblockEnabled()) return obj;

        if (obj.playerResponse && typeof obj.playerResponse === 'object') {
            delete obj.playerResponse.adPlacements;
            delete obj.playerResponse.adSlots;
            delete obj.playerResponse.playerAds;
            delete obj.playerResponse.adBreakHeartbeatParams;
        }
        delete obj.adPlacements;
        delete obj.adSlots;
        delete obj.playerAds;
        delete obj.adBreakHeartbeatParams;

        return obj;
    }

    var originalJSONParse = JSON.parse;
    JSON.parse = function() {
        var result = originalJSONParse.apply(this, arguments);
        try {
            return sanitizePlayerObj(result);
        } catch (e) {
            return result;
        }
    };

    try {
        var initialPlayerResponseVal = sanitizePlayerObj(window.ytInitialPlayerResponse);
        Object.defineProperty(window, 'ytInitialPlayerResponse', {
            get: function() { return initialPlayerResponseVal; },
            set: function(val) { initialPlayerResponseVal = sanitizePlayerObj(val); },
            configurable: true
        });
    } catch (e) {
        try { sanitizePlayerObj(window.ytInitialPlayerResponse); } catch (e2) {}
    }

    if (window.fetch) {
        var originalFetch = window.fetch;
        window.fetch = async function() {
            var args = arguments;
            var response = await originalFetch.apply(this, args);
            if (!isAdblockEnabled()) return response;

            var firstArg = args[0];
            var url = firstArg ? (typeof firstArg === 'string' ? firstArg : (firstArg.url || '')) : '';
            if (url && url.indexOf('/youtubei/v1/player') !== -1) {
                try {
                    var clone = response.clone();
                    var json = await clone.json();
                    var sanitized = sanitizePlayerObj(json);
                    var cleanHeaders = new Headers(response.headers);
                    cleanHeaders.delete('content-length');
                    cleanHeaders.delete('content-encoding');
                    return new Response(JSON.stringify(sanitized), {
                        status: response.status,
                        statusText: response.statusText,
                        headers: cleanHeaders
                    });
                } catch (e) {
                    return response;
                }
            }
            return response;
        };
    }

    // ==============================================================
    // 2. KHẮC CHẾ QUẢNG CÁO GHÉP LUỒNG (SSAI) — EVENT-DRIVEN
    // ==============================================================
    var isAdPlaying = false;
    var savedVolume = 1.0;
    var savedPlaybackRate = 1.0;
    var adFastForwardTimer = null;
    var userMutedVideo = false;
    var lastAutoUnmuteKey = '';
    var autoUnmuteWindowUntil = 0;

    function isElementVisible(el) {
        if (!el) return false;
        try {
            if (el.offsetWidth <= 0 && el.offsetHeight <= 0) return false;
            var st = window.getComputedStyle(el);
            return st.display !== 'none' && st.visibility !== 'hidden' && st.opacity !== '0';
        } catch (e) {
            return false;
        }
    }

    // Chỉ nhận diện quảng cáo thật, TUYỆT ĐỐI KHÔNG bắt thẻ .video-ads rỗng
    function isAdShowingNow() {
        var player = core.getPlayer();
        if (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
            return true;
        }
        if (document.querySelector('.html5-video-player.ad-showing, .html5-video-player.ad-interrupting')) {
            return true;
        }
        var skipBtn = document.querySelector(
            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, .ytp-skip-ad-button'
        );
        if (isElementVisible(skipBtn)) return true;
        var overlay = document.querySelector('.ytp-ad-player-overlay, .ytp-ad-player-overlay-layout');
        return isElementVisible(overlay);
    }

    function stopAdTimer() {
        if (adFastForwardTimer !== null) {
            clearInterval(adFastForwardTimer);
            adFastForwardTimer = null;
        }
    }

    function restoreAfterAd(video) {
        if (!isAdPlaying) return;
        isAdPlaying = false;
        stopAdTimer();
        if (video) {
            try {
                video.playbackRate = (savedPlaybackRate > 0 && savedPlaybackRate < 8) ? savedPlaybackRate : 1.0;
                if (!userMutedVideo) {
                    video.muted = false;
                    video.volume = savedVolume;
                }
            } catch (e) {}
        }
    }

    function handleAdTick() {
        if (!isAdblockEnabled()) {
            restoreAfterAd(core.getMainVideo());
            return;
        }

        var video = core.getMainVideo();
        if (isAdShowingNow() && video) {
            if (!isAdPlaying) {
                isAdPlaying = true;
                savedVolume = isFinite(video.volume) && video.volume > 0 ? video.volume : 1.0;
                savedPlaybackRate = (isFinite(video.playbackRate) && video.playbackRate > 0 && video.playbackRate < 8)
                    ? video.playbackRate
                    : 1.0;
            }

            try {
                video.muted = true;
                video.playbackRate = 16.0;
                // Chỉ nhảy đến cuối khi thời lượng là clip quảng cáo ngắn hợp lệ (<= 300s), tránh tua mất video chính/live
                if (isFinite(video.duration) && video.duration > 0 && video.duration <= 300) {
                    video.currentTime = Math.max(0, video.duration - 0.1);
                }
            } catch (e) {}

            var skipButtons = document.querySelectorAll(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button[class*="ad-skip-button"]'
            );
            for (var i = 0; i < skipButtons.length; i++) {
                try { skipButtons[i].click(); } catch (err) {}
            }

            if (adFastForwardTimer === null) {
                adFastForwardTimer = setInterval(handleAdTick, 150);
            }
        } else {
            restoreAfterAd(video);
        }
    }

    // Giám sát thay đổi class trên player bằng MutationObserver (0% CPU khi xem bình thường)
    var observedPlayer = null;
    var playerClassObserver = window.MutationObserver ? new MutationObserver(function() {
        handleAdTick();
    }) : null;

    function attachPlayerObserver() {
        if (!playerClassObserver) return;
        var player = core.getPlayer();
        if (player && player !== observedPlayer) {
            observedPlayer = player;
            playerClassObserver.observe(player, {
                attributes: true,
                attributeFilter: ['class']
            });
            handleAdTick();
        }
    }

    // ==============================================================
    // 3. DUY TRÌ ÂM THANH NỀN & CHỐNG AUTO-PAUSE (PLAYER_VIEW_TOO_SMALL)
    // ==============================================================
    ['visibilitychange', 'webkitvisibilitychange', 'pagehide'].forEach(function(eventType) {
        window.addEventListener(eventType, function(e) {
            if (isBgPlayEnabled() || isPipMode()) {
                e.stopImmediatePropagation();
            }
        }, true);
        document.addEventListener(eventType, function(e) {
            if (isBgPlayEnabled() || isPipMode()) {
                e.stopImmediatePropagation();
            }
        }, true);
    });

    try {
        Object.defineProperty(document, 'hidden', {
            get: function() {
                if (isBgPlayEnabled() || isPipMode()) return false;
                return core.state.mode === 'BACKGROUND_AUDIO';
            },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function() {
                if (isBgPlayEnabled() || isPipMode()) return 'visible';
                return core.state.mode === 'BACKGROUND_AUDIO' ? 'hidden' : 'visible';
            },
            configurable: true
        });
    } catch (e) {}

    if (window.HTMLMediaElement && core.nativePauseFn) {
        HTMLMediaElement.prototype.pause = function() {
            var st = core.state;
            var now = Date.now();

            // 1. Thao tác chủ động từ người dùng hoặc lệnh pause hợp lệ
            if (st.userPaused === true || (st.allowPauseUntil > 0 && now < st.allowPauseUntil)) {
                st.protectPlayUntil = 0;
                return core.nativePauseFn.apply(this, arguments);
            }

            // 2. Đang trong thời gian bảo vệ phát (chuyển PiP / mới bấm Play)
            if (st.protectPlayUntil > 0 && now < st.protectPlayUntil) {
                return;
            }

            var inPip = isPipMode();
            var inBg = st.mode === 'BACKGROUND_AUDIO';

            // 3. Đang xem trực tiếp trên màn hình chính -> cho phép YouTube Player điều khiển tự nhiên
            if (!inPip && !inBg) {
                return core.nativePauseFn.apply(this, arguments);
            }

            // 4. Đang ở nền nhưng người dùng đã tắt Phát nhạc nền
            if (inBg && !inPip && !isBgPlayEnabled()) {
                return core.nativePauseFn.apply(this, arguments);
            }

            // 5. Chặn mọi lệnh auto-pause của YouTube khi đang ở PiP hoặc chạy nền hợp lệ
        };
    }

    // Ghi nhận thao tác chạm trực tiếp của người dùng lên trình phát
    ['touchstart', 'pointerdown', 'mousedown'].forEach(function(type) {
        window.addEventListener(type, function(e) {
            if (isPipMode()) return;
            var t = e.target;
            if (!t || !t.closest) return;

            var muteBtn = t.closest('.ytp-mute-button');
            if (muteBtn) {
                var v = core.getMainVideo();
                if (v) userMutedVideo = !v.muted;
            }

            if (t.closest('#movie_player, .html5-video-player, #player-container-id, #player, .player-container, ytm-player, .player-controls-content, .player-controls-background-container')) {
                core.state.lastUserTouchAt = Date.now();
                core.state.protectPlayUntil = 0;
                core.state.playPendingUntil = 0;
                core.state.allowPauseUntil = Date.now() + 1500;
            }
        }, true);
    });

    // ==============================================================
    // 4. AUTO-UNMUTE THEO SỰ KIỆN (KHÔNG DÙNG SETINTERVAL 250MS)
    // ==============================================================
    function tryAutoUnmute() {
        try {
            if (isAdPlaying || isAdShowingNow()) return;

            var video = core.getMainVideo();
            if (!video) return;

            var curVid = core.extractVideoId();
            var key = (curVid || (location.pathname + location.search)) + '|' + (video.currentSrc || '');
            if (key && key !== lastAutoUnmuteKey) {
                lastAutoUnmuteKey = key;
                autoUnmuteWindowUntil = Date.now() + 6000;
                userMutedVideo = false;
            }

            if (userMutedVideo) return;

            var candidates = document.querySelectorAll(
                '.ytp-unmute, button.ytp-unmute, .ytm-unmute-button, button[class*="unmute" i], button[aria-label*="Tap to unmute" i], button[aria-label*="Bật tiếng" i]'
            );
            var visibleUnmuteBtn = null;
            for (var i = 0; i < candidates.length; i++) {
                var btn = candidates[i];
                if (!btn.classList.contains('ytp-mute-button') && isElementVisible(btn)) {
                    visibleUnmuteBtn = btn;
                    break;
                }
            }

            if (!visibleUnmuteBtn && Date.now() >= autoUnmuteWindowUntil) return;

            if (visibleUnmuteBtn) {
                try { visibleUnmuteBtn.click(); } catch (eClick) {}
            }
            var player = core.getPlayer();
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
            if (video.muted) video.muted = false;
            if (video.volume === 0) video.volume = 1.0;
        } catch (e) {}
    }

    // ==============================================================
    // 5. ĐỒNG BỘ MEDIA STATE THEO SỰ KIỆN THỰC TẾ (0% CPU KHI IDLE)
    // ==============================================================
    ['play', 'playing'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                core.state.userPaused = false;
                attachPlayerObserver();
                handleAdTick();
                tryAutoUnmute();
                core.syncMediaState(true);
            }
        }, true);
    });

    ['loadedmetadata', 'durationchange', 'seeked', 'resize'].forEach(function(eventType) {
        document.addEventListener(eventType, function(e) {
            if (e.target && e.target.tagName === 'VIDEO') {
                attachPlayerObserver();
                handleAdTick();
                tryAutoUnmute();
                core.syncMediaState(true);
            }
        }, true);
    });

    document.addEventListener('timeupdate', function(e) {
        if (e.target && e.target.tagName === 'VIDEO' && !e.target.paused) {
            if (Date.now() < autoUnmuteWindowUntil && !userMutedVideo) {
                tryAutoUnmute();
            }
            // core.syncMediaState tự động tiết lưu >= 950ms khi signature không đổi
            core.syncMediaState(false);
        }
    }, true);

    document.addEventListener('volumechange', function(e) {
        if (e.target && e.target.tagName === 'VIDEO' && Date.now() < autoUnmuteWindowUntil && !userMutedVideo) {
            tryAutoUnmute();
        }
    }, true);

    document.addEventListener('pause', function(e) {
        if (e.target && e.target.tagName === 'VIDEO') {
            var inProtect = core.state.protectPlayUntil > Date.now();
            if (!inProtect && !e.target.ended && e.isTrusted) {
                core.state.userPaused = true;
            }
            setTimeout(function() { core.syncMediaState(true); }, 120);
        }
    }, true);

    document.addEventListener('ended', function(e) {
        if (e.target && e.target.tagName === 'VIDEO') {
            core.syncMediaState(true);
        }
    }, true);

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', attachPlayerObserver, { once: true });
    } else {
        attachPlayerObserver();
    }
})();
