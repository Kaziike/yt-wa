/**
 * LITETUBE CORE BRIDGE & UNIFIED STATE MACHINE
 * - Lưu trữ tham chiếu HTMLMediaElement.play / pause gốc một lần duy nhất.
 * - Quản lý tập trung trạng thái (thay thế 12 biến global window.__brave* rời rạc).
 * - Cung cấp 1 cổng IPC duy nhất (onMediaState) có chống trùng lặp & tiết lưu theo sự kiện.
 */
(function() {
    'use strict';

    if (window.__liteTube) return;

    var nativePlayFn = window.HTMLMediaElement ? HTMLMediaElement.prototype.play : null;
    var nativePauseFn = window.HTMLMediaElement ? HTMLMediaElement.prototype.pause : null;

    var initialSettings = Object.assign({
        adblock: true,
        backgroundPlay: true,
        autoPip: true,
        preloadCache: true
    }, window.__liteTubeInitialSettings || {});

    var state = {
        settings: initialSettings,
        mode: 'FOREGROUND', // 'FOREGROUND' | 'PIP' | 'BACKGROUND_AUDIO'
        userPaused: false,
        protectPlayUntil: 0,
        allowPauseUntil: 0,
        playPendingUntil: 0,
        lastUserTouchAt: 0
    };

    var lastSyncSignature = '';
    var lastSyncAt = 0;

    function getPlayer() {
        return document.getElementById('movie_player') || document.querySelector('.html5-video-player');
    }

    function getMainVideo() {
        var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
        if (videos.length === 0) return null;
        var playing = videos.filter(function(v) { return !v.paused && !v.ended; });
        var pool = playing.length ? playing : videos;
        var best = pool[0];
        var bestScore = -1;
        for (var i = 0; i < pool.length; i++) {
            var v = pool[i];
            var score = (v.videoWidth * v.videoHeight) || (v.offsetWidth * v.offsetHeight);
            if (score > bestScore) {
                best = v;
                bestScore = score;
            }
        }
        return best;
    }

    function nativePlay(video) {
        if (!video) return null;
        var fn = nativePlayFn || video.play;
        if (!fn) return null;
        try {
            var p = fn.call(video);
            if (p && typeof p.catch === 'function') {
                p.catch(function() {
                    try {
                        var player = getPlayer();
                        if (player && typeof player.playVideo === 'function') player.playVideo();
                    } catch (e) {}
                });
            }
            return p;
        } catch (e) {
            return null;
        }
    }

    function nativePause(video) {
        if (!video) return;
        var fn = nativePauseFn || video.pause;
        if (fn) {
            try { fn.call(video); } catch (e) {}
        }
    }

    function extractVideoId() {
        try {
            var u = new URL(window.location.href);
            var v = u.searchParams.get('v');
            if (v) return v;
            var m = window.location.pathname.match(/\/shorts\/([a-zA-Z0-9_-]{6,})/);
            if (m && m[1]) return m[1];
        } catch (e) {}
        try {
            var p = getPlayer();
            if (p && typeof p.getVideoData === 'function') {
                var vd = p.getVideoData();
                if (vd && vd.video_id) return String(vd.video_id);
            }
        } catch (e2) {}
        try {
            var pr = window.ytInitialPlayerResponse;
            if (pr && pr.videoDetails && pr.videoDetails.videoId) {
                return String(pr.videoDetails.videoId);
            }
        } catch (e3) {}
        return '';
    }

    function extractMetadata() {
        var videoId = extractVideoId();
        var apiTitle = '';
        var apiAuthor = '';
        try {
            var p = getPlayer();
            if (p && typeof p.getVideoData === 'function') {
                var vd = p.getVideoData();
                if (vd && (!videoId || !vd.video_id || vd.video_id === videoId)) {
                    if (vd.title) apiTitle = String(vd.title).trim();
                    if (vd.author) apiAuthor = String(vd.author).trim();
                }
            }
        } catch (e) {}

        var titleEl = document.querySelector(
            'h1.slim-video-metadata-title, .ytm-slim-video-metadata-renderer-title, .media-item-headline, .ytp-title-link'
        );
        var channelEl = document.querySelector(
            '.ytm-channel-thumbnail-with-profile-name, .slim-owner-channel-name, .subhead, .ytd-channel-name'
        );
        var rawTitle = titleEl && titleEl.innerText ? titleEl.innerText.trim() : '';
        var docTitle = document.title ? document.title.replace(/\s*-\s*YouTube$/i, '').trim() : '';
        var title = apiTitle || rawTitle || docTitle || 'LiteTube';
        var channel = apiAuthor || (channelEl && channelEl.innerText ? channelEl.innerText.trim() : '');
        return { videoId: videoId, title: title, channel: channel };
    }

    function syncMediaState(force) {
        if (!window.AndroidBridge || typeof window.AndroidBridge.onMediaState !== 'function') return;

        var video = getMainVideo();
        var isPlaying = !!(video && !video.paused && !video.ended);
        if (!isPlaying && state.playPendingUntil > Date.now()) {
            return;
        }
        if (isPlaying) {
            state.userPaused = false;
        }

        var meta = extractMetadata();
        var posMs = video && isFinite(video.currentTime) ? Math.round(video.currentTime * 1000) : 0;
        var durMs = video && isFinite(video.duration) && video.duration > 0 ? Math.round(video.duration * 1000) : -1;
        var vw = video ? (video.videoWidth || 0) : 0;
        var vh = video ? (video.videoHeight || 0) : 0;

        var now = Date.now();
        var signature = [isPlaying ? 1 : 0, meta.videoId, meta.title, meta.channel, durMs, vw, vh].join('|');
        if (!force && signature === lastSyncSignature && (now - lastSyncAt) < 950) {
            return;
        }
        lastSyncSignature = signature;
        lastSyncAt = now;

        try {
            window.AndroidBridge.onMediaState(
                isPlaying,
                meta.videoId || '',
                meta.title,
                meta.channel,
                posMs,
                durMs,
                vw,
                vh
            );
        } catch (e) {}
    }

    function applySettings(settingsObj) {
        if (settingsObj && typeof settingsObj === 'object') {
            state.settings = Object.assign({}, state.settings, settingsObj);
        }
        return JSON.stringify(state.settings);
    }

    function setMode(newMode) {
        state.mode = newMode || 'FOREGROUND';
        if (state.mode === 'BACKGROUND_AUDIO') {
            if (state.settings.backgroundPlay !== false) {
                var v = getMainVideo();
                if (v && !v.paused && !v.ended) {
                    state.protectPlayUntil = Math.max(state.protectPlayUntil, Date.now() + 1800);
                }
            }
        } else if (state.mode === 'FOREGROUND') {
            state.protectPlayUntil = 0;
            state.playPendingUntil = 0;
            state.allowPauseUntil = 0;
        }
        return state.mode;
    }

    function measureVideoRect() {
        try {
            var el = getPlayer();
            var v = getMainVideo();
            var r = el ? el.getBoundingClientRect() : null;
            if ((!r || r.width < 20 || r.height < 20) && v) {
                r = v.getBoundingClientRect();
            }
            if (!r) return '';
            return [r.left, r.top, r.width, r.height, window.innerWidth || 0].join(',');
        } catch (e) {
            return '';
        }
    }

    function getBottomBarHeight() {
        try {
            var bar = document.querySelector('ytm-pivot-bar-renderer, [role="tablist"]');
            if (!bar) return 0;
            var st = window.getComputedStyle(bar);
            if (!st || st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') {
                return 0;
            }
            var r = bar.getBoundingClientRect();
            if (!r || r.height <= 0 || r.top >= (window.innerHeight || 0)) return 0;
            return Math.round(r.height);
        } catch (e) {
            return 48;
        }
    }

    function wakePlayerControls() {
        try {
            var player = getPlayer();
            if (player) {
                if (typeof player.wakeUpControls === 'function') player.wakeUpControls();
                if (typeof player.updateDimensions === 'function') player.updateDimensions();
            }
        } catch (e) {}
        try { window.dispatchEvent(new Event('resize')); } catch (e2) {}
    }

    window.__liteTube = {
        state: state,
        nativePlayFn: nativePlayFn,
        nativePauseFn: nativePauseFn,
        nativePlay: nativePlay,
        nativePause: nativePause,
        getPlayer: getPlayer,
        getMainVideo: getMainVideo,
        extractVideoId: extractVideoId,
        extractMetadata: extractMetadata,
        syncMediaState: syncMediaState,
        applySettings: applySettings,
        setMode: setMode,
        measureVideoRect: measureVideoRect,
        getBottomBarHeight: getBottomBarHeight,
        wakePlayerControls: wakePlayerControls
    };
})();
