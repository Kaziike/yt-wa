/**
 * LITETUBE PIP & PLAYBACK CONTROLLER
 * - Cô lập tuyệt đối thẻ <video> khi và chỉ khi Native Android kích hoạt PiP (không tự đoán theo
 *   kích thước màn hình, tránh lỗi đen hình khi chia đôi màn hình Split-Screen).
 * - Dùng MutationObserver chỉ trong lúc PiP đang bật (thay vì setInterval 300ms).
 * - Cung cấp bộ điều khiển thống nhất cho PiP, MiniPlayer và MediaService.
 */
(function() {
    'use strict';

    if (window.__bravePip) return;

    var core = window.__liteTube;
    if (!core) return;

    var STYLE_ID = '__brave_pip_style';
    var MODE_ATTR = 'data-brave-pip-mode';
    var ROLE_ATTR = 'data-brave-pip-role';

    var CSS = [
        'html[' + MODE_ATTR + '], html[' + MODE_ATTR + '] body {',
        '  background:#000!important; margin:0!important; padding:0!important;',
        '  width:100vw!important; height:100vh!important; overflow:hidden!important;',
        '}',
        'html[' + MODE_ATTR + '] body *:not([' + ROLE_ATTR + ']) {',
        '  display:none!important; visibility:hidden!important;',
        '  opacity:0!important; pointer-events:none!important;',
        '}',
        'html[' + MODE_ATTR + '] [' + ROLE_ATTR + '="ancestor"] {',
        '  display:block!important; visibility:visible!important; position:fixed!important;',
        '  top:0!important; left:0!important; right:0!important; bottom:0!important;',
        '  width:100vw!important; height:100vh!important;',
        '  max-width:100vw!important; max-height:100vh!important;',
        '  margin:0!important; padding:0!important; border:0!important;',
        '  background:transparent!important; background-color:transparent!important;',
        '  transform:none!important; -webkit-transform:none!important;',
        '  filter:none!important; backdrop-filter:none!important; -webkit-backdrop-filter:none!important;',
        '  perspective:none!important; contain:none!important; will-change:auto!important;',
        '  content-visibility:visible!important; clip-path:none!important;',
        '  -webkit-mask:none!important; mask:none!important; overflow:hidden!important;',
        '  opacity:1!important; pointer-events:none!important;',
        '}',
        'html[' + MODE_ATTR + '] [' + ROLE_ATTR + '="ancestor"]::before,',
        'html[' + MODE_ATTR + '] [' + ROLE_ATTR + '="ancestor"]::after {',
        '  display:none!important; content:none!important;',
        '}',
        'html[' + MODE_ATTR + '] video[' + ROLE_ATTR + '="video"] {',
        '  visibility:visible!important; display:block!important; position:fixed!important;',
        '  top:0!important; left:0!important; right:0!important; bottom:0!important;',
        '  width:100vw!important; height:100vh!important;',
        '  min-width:100vw!important; min-height:100vh!important;',
        '  max-width:100vw!important; max-height:100vh!important;',
        '  margin:0!important; padding:0!important; border:0!important;',
        '  transform:none!important; -webkit-transform:none!important;',
        '  object-fit:contain!important; background:#000!important; opacity:1!important;',
        '  z-index:2147483647!important; pointer-events:none!important;',
        '}'
    ].join('\n');

    var active = false;
    var currentVideo = null;
    var domObserver = null;
    var pendingTimers = [];

    function clearPendingTimers() {
        while (pendingTimers.length > 0) {
            clearTimeout(pendingTimers.pop());
        }
    }

    function schedule(fn, delay) {
        var id = setTimeout(fn, delay);
        pendingTimers.push(id);
        return id;
    }

    function setAttr(el, name, value) {
        if (el && el.getAttribute(name) !== value) {
            el.setAttribute(name, value);
        }
    }

    function clearMarks() {
        var marked = document.querySelectorAll('[' + ROLE_ATTR + ']');
        for (var i = 0; i < marked.length; i++) {
            marked[i].removeAttribute(ROLE_ATTR);
        }
    }

    function ensureStyle() {
        var style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            style.textContent = CSS;
        }
        var parent = document.head || document.documentElement;
        if (parent && style.parentNode !== parent) {
            parent.appendChild(style);
        }
    }

    function removeStyle() {
        var style = document.getElementById(STYLE_ID);
        if (style && style.parentNode) {
            style.parentNode.removeChild(style);
        }
    }

    function applyIsolation() {
        if (!active) return;
        setAttr(document.documentElement, MODE_ATTR, 'on');
        ensureStyle();

        var video = core.getMainVideo();
        if (!video) return;
        if (video !== currentVideo) {
            clearMarks();
            currentVideo = video;
        }
        setAttr(video, ROLE_ATTR, 'video');
        for (var p = video.parentElement; p && p !== document.documentElement; p = p.parentElement) {
            setAttr(p, ROLE_ATTR, 'ancestor');
        }
        core.syncMediaState(false);
    }

    function startObserver() {
        if (domObserver || !window.MutationObserver) return;
        var scheduled = false;
        domObserver = new MutationObserver(function() {
            if (!active || scheduled) return;
            scheduled = true;
            requestAnimationFrame(function() {
                scheduled = false;
                applyIsolation();
            });
        });
        var root = document.body || document.documentElement;
        if (root) {
            domObserver.observe(root, { childList: true, subtree: true });
        }
    }

    function stopObserver() {
        if (domObserver) {
            domObserver.disconnect();
            domObserver = null;
        }
    }

    function resyncPlayerState() {
        try {
            var v = core.getMainVideo();
            var p = core.getPlayer();
            if (!v) return 'resync:no-video';
            var actuallyPlaying = !v.paused && !v.ended;

            if (p && typeof p.getPlayerState === 'function') {
                var st = p.getPlayerState();
                if (actuallyPlaying) {
                    if (st !== 1) {
                        if (typeof p.playVideo === 'function') {
                            try { p.playVideo(); } catch (ePlay) {}
                        }
                        if (v.readyState >= 2) {
                            try {
                                v.dispatchEvent(new Event('canplay'));
                                v.dispatchEvent(new Event('play'));
                                v.dispatchEvent(new Event('playing'));
                            } catch (eEv) {}
                        }
                    }
                } else if (st === 1 || st === 3 || st === -1) {
                    core.state.allowPauseUntil = Date.now() + 800;
                    if (typeof p.pauseVideo === 'function') {
                        try { p.pauseVideo(); } catch (ePause) {}
                    }
                    try { v.dispatchEvent(new Event('pause')); } catch (eEv2) {}
                }
            } else {
                try {
                    if (actuallyPlaying) v.dispatchEvent(new Event('canplay'));
                    v.dispatchEvent(new Event(actuallyPlaying ? 'playing' : 'pause'));
                } catch (e1) {}
            }

            core.wakePlayerControls();
            return 'resync:' + (actuallyPlaying ? 'playing' : 'paused');
        } catch (e) {
            return 'resync:error';
        }
    }

    function enter() {
        clearPendingTimers();
        active = true;
        core.setMode('PIP');
        core.state.protectPlayUntil = Date.now() + 3000;
        applyIsolation();
        startObserver();

        if (!core.state.userPaused) {
            var v = core.getMainVideo();
            if (v) {
                core.nativePlay(v);
                schedule(function() {
                    if (!active || core.state.userPaused) return;
                    var target = core.getMainVideo() || v;
                    if (target && target.paused) core.nativePlay(target);
                }, 220);
            }
        }
        return currentVideo ? 'pip:on' : 'pip:on(no-video)';
    }

    function exit() {
        clearPendingTimers();
        var wasActive = active;
        active = false;
        currentVideo = null;
        stopObserver();

        document.documentElement.removeAttribute(MODE_ATTR);
        clearMarks();
        removeStyle();
        core.setMode('FOREGROUND');

        if (!wasActive) {
            schedule(resyncPlayerState, 120);
            core.syncMediaState(true);
            return 'pip:off(idle)';
        }

        var exitAt = Date.now();
        var v = core.getMainVideo();
        var timeBeforeExit = (v && isFinite(v.currentTime)) ? v.currentTime : -1;

        if (!core.state.userPaused && v) {
            core.state.protectPlayUntil = Date.now() + 1400;
            core.nativePlay(v);
            schedule(function() {
                if (active || core.state.userPaused || core.state.lastUserTouchAt > exitAt) return;
                var target = core.getMainVideo() || v;
                if (target && target.paused) {
                    core.nativePlay(target);
                } else if (target && !target.ended && timeBeforeExit >= 0 && isFinite(target.currentTime)) {
                    if (Math.abs(target.currentTime - timeBeforeExit) < 0.05) {
                        try { target.currentTime = target.currentTime; } catch (eSeek) {}
                        core.nativePlay(target);
                    }
                }
                resyncPlayerState();
            }, 500);
        }

        core.wakePlayerControls();
        schedule(resyncPlayerState, 200);
        schedule(resyncPlayerState, 900);
        core.syncMediaState(true);
        return 'pip:off';
    }

    function play() {
        clearPendingTimers();
        core.state.userPaused = false;
        core.state.allowPauseUntil = 0;
        core.state.protectPlayUntil = Date.now() + 2200;
        core.state.playPendingUntil = Date.now() + 1200;

        try {
            var apiPlayer = core.getPlayer();
            if (!active && apiPlayer && typeof apiPlayer.playVideo === 'function') {
                apiPlayer.playVideo();
            }
        } catch (eApi) {}

        var v = core.getMainVideo();
        if (v) {
            core.nativePlay(v);
            schedule(function() {
                if (core.state.userPaused) return;
                var target = core.getMainVideo() || v;
                if (target && target.paused) {
                    core.nativePlay(target);
                }
                core.syncMediaState(true);
            }, 220);
        }
        core.syncMediaState(true);
        return v ? 'play' : 'play(no-video)';
    }

    function pause() {
        clearPendingTimers();
        core.state.userPaused = true;
        core.state.protectPlayUntil = 0;
        core.state.playPendingUntil = 0;
        core.state.allowPauseUntil = Date.now() + 1000;

        try {
            var apiPlayer = core.getPlayer();
            if (apiPlayer && typeof apiPlayer.pauseVideo === 'function') {
                apiPlayer.pauseVideo();
            }
        } catch (eApi) {}

        var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
        for (var i = 0; i < videos.length; i++) {
            core.nativePause(videos[i]);
        }

        core.syncMediaState(true);
        return videos.length ? 'pause' : 'pause(no-video)';
    }

    function toggle() {
        var v = core.getMainVideo();
        return (v && !v.paused && !v.ended) ? pause() : play();
    }

    function next() {
        clearPendingTimers();
        core.state.userPaused = false;
        core.state.allowPauseUntil = 0;
        core.state.protectPlayUntil = Date.now() + 4000;

        var curId = core.extractVideoId();

        try {
            var player = core.getPlayer();
            if (player && typeof player.nextVideo === 'function') {
                var pd = (typeof player.getPlaylist === 'function') ? player.getPlaylist() : null;
                if (Array.isArray(pd) && pd.length > 1) {
                    player.nextVideo();
                    schedule(function() { core.syncMediaState(true); }, 500);
                    return 'next:player-api';
                }
            }
        } catch (e) {}

        var nextBtn = document.querySelector(
            '.ytp-next-button, button[aria-label*="Next" i], button[aria-label*="Tiếp theo" i], ' +
            '.player-control-next-icon, [aria-label*="Next video" i], [aria-label*="Video tiếp theo" i]'
        );
        if (nextBtn) {
            try {
                nextBtn.click();
                schedule(function() { core.syncMediaState(true); }, 600);
                return 'next:button';
            } catch (e) {}
        }

        var candidates = document.querySelectorAll(
            'ytm-watch-next-secondary-results-renderer a[href*="/watch?v="], ' +
            'ytm-item-section-renderer a[href*="/watch?v="], ' +
            'ytm-media-item a[href*="/watch?v="], ' +
            'ytm-video-with-context-renderer a[href*="/watch?v="], ' +
            'ytm-compact-video-renderer a[href*="/watch?v="], ' +
            'a[href*="/watch?v="]'
        );
        for (var i = 0; i < candidates.length; i++) {
            var a = candidates[i];
            var href = a.getAttribute('href') || a.href || '';
            if (!href || href.indexOf('/shorts/') !== -1) continue;
            if (curId && href.indexOf('v=' + curId) !== -1) continue;
            try {
                a.click();
                schedule(function() { core.syncMediaState(true); }, 700);
                return 'next:link';
            } catch (e) {}
        }

        return 'next:none';
    }

    function previous() {
        core.state.userPaused = false;
        core.state.allowPauseUntil = 0;
        core.state.protectPlayUntil = Date.now() + 2500;

        var v = core.getMainVideo();
        if (v && isFinite(v.currentTime) && v.currentTime > 5) {
            try {
                v.currentTime = 0;
                if (v.paused) play();
                core.syncMediaState(true);
                return 'prev:restart';
            } catch (e) {}
        }

        try {
            var player = core.getPlayer();
            if (player && typeof player.previousVideo === 'function') {
                player.previousVideo();
                return 'prev:player-api';
            }
        } catch (e) {}

        var prevBtn = document.querySelector(
            '.ytp-prev-button, button[aria-label*="Previous" i], button[aria-label*="Trước" i], ' +
            '.player-control-previous-icon, [aria-label*="Previous video" i], [aria-label*="Video trước" i]'
        );
        if (prevBtn) {
            try {
                prevBtn.click();
                return 'prev:button';
            } catch (e) {}
        }

        try {
            if (window.history.length > 1) {
                window.history.back();
                return 'prev:history';
            }
        } catch (e) {}

        if (v) {
            try {
                v.currentTime = 0;
                core.syncMediaState(true);
                return 'prev:zero';
            } catch (e) {}
        }
        return 'prev:none';
    }

    function seekTo(ms) {
        var v = core.getMainVideo();
        if (!v) return 'seek:no-video';
        var targetSec = Math.max(0, Number(ms) / 1000);
        if (!isFinite(targetSec)) return 'seek:invalid';
        try {
            if (isFinite(v.duration) && v.duration > 0) {
                targetSec = Math.min(targetSec, v.duration);
            }
            v.currentTime = targetSec;
            core.syncMediaState(true);
            return 'seek:' + Math.round(targetSec * 1000);
        } catch (e) {
            return 'seek:error';
        }
    }

    function setBackground(isBg) {
        if (isBg) {
            if (!active) core.setMode('BACKGROUND_AUDIO');
        } else {
            if (!active) core.setMode('FOREGROUND');
        }
        return isBg ? 'bg:on' : 'bg:off';
    }

    function enterAudioOnlyFromPip() {
        exit();
        core.state.userPaused = false;
        core.setMode('BACKGROUND_AUDIO');
        core.state.protectPlayUntil = Date.now() + 4000;
        var v = core.getMainVideo();
        if (v && v.paused) {
            core.nativePlay(v);
        }
        core.syncMediaState(true);
        return 'pip:audio-only';
    }

    window.__bravePip = {
        enter: enter,
        exit: exit,
        play: play,
        pause: pause,
        toggle: toggle,
        next: next,
        previous: previous,
        seekTo: seekTo,
        setBackground: setBackground,
        enterAudioOnlyFromPip: enterAudioOnlyFromPip,
        applySettings: core.applySettings,
        mainVideo: core.getMainVideo,
        resync: resyncPlayerState,
        isActive: function() { return active; }
    };
})();
