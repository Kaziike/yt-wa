/**
 * BRAVE PIP CONTROLLER - YOUTUBE LITE
 * Bộ điều khiển Video Popup (PiP) & Media Control độc lập.
 *
 * 1. Cô lập video tuyệt đối khi vào PiP:
 *    - Đánh dấu thẻ <video> chính bằng [data-brave-pip-role="video"]
 *    - Đánh dấu chuỗi thẻ cha từ video lên tới body bằng [data-brave-pip-role="ancestor"]
 *    - Ẩn hoàn toàn (display:none) mọi phần tử khác trong body (:not([data-brave-pip-role]))
 *      như thanh header YouTube, nút "Mở ứng dụng", phụ đề, thanh "Tiếp theo", gợi ý...
 *    - Cho chuỗi ancestor trong suốt (background:transparent) và phủ kín 100vw x 100vh
 *      để không một lớp nền đen nào đè lên video và không làm lệch vị trí fixed.
 *
 * 2. Điều khiển Play / Pause / Next / Previous / Seek trực tiếp qua native HTMLMediaElement
 *    kết hợp cờ bảo vệ __braveProtectPlayUntil để không bị YouTube chặn bởi kiểm tra kích thước nhỏ
 *    (PLAYER_VIEW_TOO_SMALL) hoặc bị lệch trạng thái với hook của youtube_defuser.js.
 */
(function() {
    'use strict';

    // Lưu hàm play/pause gốc của trình duyệt trước khi bất kỳ script nào ghi đè
    if (!window.__braveNativePlay && window.HTMLMediaElement) {
        window.__braveNativePlay = HTMLMediaElement.prototype.play;
    }
    if (!window.__braveNativePause && window.HTMLMediaElement) {
        window.__braveNativePause = HTMLMediaElement.prototype.pause;
    }

    // Khởi tạo trạng thái mặc định
    window.__braveSettings = Object.assign({
        adblock: true,
        backgroundPlay: true,
        autoPip: true
    }, window.__braveSettings || {});

    if (typeof window.__braveAppInBackground === 'undefined') {
        window.__braveAppInBackground = false;
    }

    if (window.__bravePip) return;

    var STYLE_ID = '__brave_pip_style';
    var MODE_ATTR = 'data-brave-pip-mode';   // đặt trên <html>
    var ROLE_ATTR = 'data-brave-pip-role';   // "video" | "ancestor"

    var CSS = [
        'html[' + MODE_ATTR + '], html[' + MODE_ATTR + '] body {',
        '  background:#000!important; margin:0!important; padding:0!important;',
        '  width:100vw!important; height:100vh!important; overflow:hidden!important;',
        '}',
        // Ẩn triệt để mọi thành phần không phải video và không phải tổ tiên trực tiếp của video
        'html[' + MODE_ATTR + '] body *:not([' + ROLE_ATTR + ']) {',
        '  display:none!important; visibility:hidden!important;',
        '  opacity:0!important; pointer-events:none!important;',
        '}',
        // Chuỗi thẻ cha của video: trong suốt, phủ kín khung nhìn, gỡ mọi thuộc tính gây giam position:fixed
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
        // Thẻ video chính phủ 100% cửa sổ PiP
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
    var forcedByNative = false;
    var timer = null;
    var currentVideo = null;
    var retryTimers = [];

    function clearRetryTimers() {
        while (retryTimers.length > 0) {
            clearTimeout(retryTimers.pop());
        }
    }

    // Ưu tiên video đang phát, sau đó video có độ phân giải / kích thước lớn nhất
    function mainVideo() {
        var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
        if (videos.length === 0) return null;
        var playing = videos.filter(function(v) { return !v.paused && !v.ended; });
        var pool = playing.length ? playing : videos;
        var best = pool[0];
        var bestScore = -1;
        pool.forEach(function(v) {
            var score = (v.videoWidth * v.videoHeight) || (v.offsetWidth * v.offsetHeight);
            if (score > bestScore) { best = v; bestScore = score; }
        });
        return best;
    }

    function getPlayer() {
        return document.getElementById('movie_player') || document.querySelector('.html5-video-player');
    }

    function ensureStyle() {
        var style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            style.textContent = CSS;
        } else if (style.textContent !== CSS) {
            style.textContent = CSS;
        }
        var parent = document.head || document.documentElement;
        if (parent && style.parentNode !== parent) parent.appendChild(style);
    }

    function removeStyle() {
        var style = document.getElementById(STYLE_ID);
        if (style && style.parentNode) style.parentNode.removeChild(style);
    }

    function setAttr(el, name, value) {
        if (el.getAttribute(name) !== value) el.setAttribute(name, value);
    }

    function clearMarks() {
        var marked = document.querySelectorAll('[' + ROLE_ATTR + ']');
        for (var i = 0; i < marked.length; i++) marked[i].removeAttribute(ROLE_ATTR);
    }

    function syncVideoToBridge(video) {
        if (!window.AndroidBridge || !video) return;
        try {
            if (video.videoWidth > 0 && video.videoHeight > 0) {
                window.AndroidBridge.onVideoSize(video.videoWidth, video.videoHeight);
            }
        } catch (e) {}
    }

    function notifyMediaState() {
        if (typeof window.__braveSyncMediaState === 'function') {
            try { window.__braveSyncMediaState(); } catch (e) {}
        }
    }

    function apply() {
        if (!active) return;
        setAttr(document.documentElement, MODE_ATTR, 'on');
        ensureStyle();

        var video = mainVideo();
        if (!video) return;
        if (video !== currentVideo) {
            clearMarks();
            currentVideo = video;
        }
        setAttr(video, ROLE_ATTR, 'video');
        for (var p = video.parentElement; p && p !== document.documentElement; p = p.parentElement) {
            setAttr(p, ROLE_ATTR, 'ancestor');
        }
        syncVideoToBridge(video);
    }

    function startLoop() {
        window.__bravePipTransitionUntil = Date.now() + 1000;
        active = true;
        apply();
        if (!timer) timer = setInterval(apply, 300);
    }

    function enter() {
        active = true;
        forcedByNative = true;
        window.__braveEnteringPip = true;
        window.__bravePipTransitionUntil = Date.now() + 3000;
        window.__braveProtectPlayUntil = Date.now() + 3500;
        startLoop();

        // Nếu người dùng không chủ động pause trước đó, đảm bảo video tiếp tục phát trơn tru khi vào PiP
        if (!window.__braveUserPaused) {
            var v = mainVideo();
            if (v) {
                triggerNativePlay(v);
                [80, 200, 500, 900].forEach(function(delay) {
                    setTimeout(function() {
                        if (!window.__braveUserPaused) {
                            var target = mainVideo() || v;
                            if (target && target.paused) {
                                triggerNativePlay(target);
                            }
                        }
                    }, delay);
                });
            }
        }
        return currentVideo ? 'pip:on' : 'pip:on(no-video)';
    }

    function triggerMicroSeek() {
        try {
            var v = mainVideo();
            if (v && isFinite(v.currentTime)) {
                // Micro-seek không làm gián đoạn phát (gán lại currentTime để kích hoạt pipeline re-render của YouTube)
                var cur = v.currentTime;
                v.currentTime = cur;
                // Bắn sự kiện seeking và seeked để đánh thức bộ re-render của YouTube Player
                v.dispatchEvent(new Event('seeking'));
                v.dispatchEvent(new Event('seeked'));
                v.dispatchEvent(new Event('timeupdate'));
            }
        } catch (e) {}
        try {
            var player = getPlayer();
            if (player) {
                if (typeof player.wakeUpControls === 'function') player.wakeUpControls();
                if (typeof player.updateDimensions === 'function') player.updateDimensions();
            }
        } catch (e2) {}
    }

    function stopLoop() {
        active = false;
        currentVideo = null;
        if (timer) {
            clearInterval(timer);
            timer = null;
        }

        // Gỡ sạch toàn bộ thuộc tính & style PiP
        document.documentElement.removeAttribute(MODE_ATTR);
        clearMarks();
        removeStyle();

        window.__braveEnteringPip = false;
        window.__bravePlayPendingUntil = 0;
        window.__bravePipTransitionUntil = Date.now() + 2500;

        var v = mainVideo();
        // Nếu người dùng KHÔNG chủ động bấm pause trong PiP -> DUY TRÌ PHÁT KHI PHÓNG TO!
        if (!window.__braveUserPaused) {
            // Bảo vệ phát trong 2.5 giây đầu: Chặn mọi lệnh auto-pause của YouTube khi resize/animate bung cửa sổ
            window.__braveProtectPlayUntil = Date.now() + 2500;
            if (v) {
                triggerNativePlay(v);
                [80, 200, 450, 800].forEach(function(delay) {
                    setTimeout(function() {
                        if (!window.__braveUserPaused) {
                            var target = mainVideo() || v;
                            if (target && target.paused) {
                                triggerNativePlay(target);
                            }
                        }
                    }, delay);
                });
            }
        } else {
            window.__braveProtectPlayUntil = 0;
        }

        window.__isUserExplicitPause = false;
        window.__braveAllowPauseUntil = 0;

        // Triển khai Micro-Seek Re-render Kick: đánh thức bộ re-render của YouTube Player ngay khi gỡ PiP
        triggerMicroSeek();

        // Dispatch chuỗi sự kiện resize và orientationchange sau [0, 80, 200, 450]ms
        [0, 80, 200, 450].forEach(function(delay) {
            setTimeout(function() {
                try { window.dispatchEvent(new Event('resize')); } catch (e) {}
                try { window.dispatchEvent(new Event('orientationchange')); } catch (e) {}
                if (delay === 80 || delay === 200) {
                    triggerMicroSeek();
                }
            }, delay);
        });
        notifyMediaState();
        return 'pip:off';
    }

    function exit() {
        forcedByNative = false;
        return stopLoop();
    }

    // Tự động nhận diện khi khung nhìn WebView bị thu nhỏ thành cửa sổ PiP / Popup nhỏ
    function checkViewportSize() {
        try {
            var w = window.innerWidth || 0;
            var h = window.innerHeight || 0;
            var activeEl = document.activeElement;
            var isTyping = activeEl && (
                activeEl.tagName === 'INPUT' ||
                activeEl.tagName === 'TEXTAREA' ||
                activeEl.isContentEditable
            );
            var autoPipEnabled = !(window.__braveSettings && window.__braveSettings.autoPip === false);
            var isSmallPopup = !isTyping && h > 0 && h <= 280 && w > 0 && w <= 520;
            if (isSmallPopup && mainVideo() && (forcedByNative || autoPipEnabled)) {
                if (!active) startLoop();
            } else if (!forcedByNative && active && h > 340) {
                stopLoop();
            }
        } catch (e) {}
    }

    window.addEventListener('resize', checkViewportSize, true);

    function triggerNativePlay(v) {
        if (!v) return;
        var nativePlay = window.__braveNativePlay || v.play;
        if (nativePlay) {
            try {
                var r = nativePlay.call(v);
                if (r && typeof r.catch === 'function') {
                    r.catch(function() {
                        try {
                            var player = getPlayer();
                            if (player && typeof player.playVideo === 'function') player.playVideo();
                        } catch (e2) {}
                    });
                }
            } catch (e) {}
        }
    }

    function play() {
        clearRetryTimers();
        window.__braveUserPaused = false;
        window.__isUserExplicitPause = false;
        window.__braveAllowPauseUntil = 0;
        window.__braveProtectPlayUntil = Date.now() + 2500;
        window.__bravePlayPendingUntil = Date.now() + 1500;

        var v = mainVideo();
        if (v) {
            triggerNativePlay(v);
            // Kiểm tra lại sau 150ms và 400ms để thắng hoàn toàn cơ chế auto-pause (PLAYER_VIEW_TOO_SMALL) của YouTube
            [150, 400].forEach(function(delay) {
                var t = setTimeout(function() {
                    if (window.__braveUserPaused) return;
                    var targetVideo = mainVideo() || v;
                    if (targetVideo && targetVideo.paused) {
                        window.__braveProtectPlayUntil = Math.max(window.__braveProtectPlayUntil || 0, Date.now() + 1500);
                        triggerNativePlay(targetVideo);
                    }
                    notifyMediaState();
                }, delay);
                retryTimers.push(t);
            });
        } else {
            try {
                var player = getPlayer();
                if (player && typeof player.playVideo === 'function') player.playVideo();
            } catch (e) {}
        }

        if (window.AndroidBridge) {
            try { window.AndroidBridge.onVideoPlaying('', ''); } catch (e) {}
        }
        notifyMediaState();
        return v ? 'play' : 'play(no-video)';
    }

    function pause() {
        clearRetryTimers();
        window.__braveUserPaused = true;
        window.__isUserExplicitPause = false;
        window.__braveAllowPauseUntil = 0;
        window.__braveProtectPlayUntil = 0;
        window.__bravePlayPendingUntil = 0;

        var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
        videos.forEach(function(v) {
            try {
                var nativePause = window.__braveNativePause || v.pause;
                if (nativePause) nativePause.call(v);
            } catch (e) {}
        });

        if (window.AndroidBridge) {
            try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
        }
        notifyMediaState();
        return videos.length ? 'pause' : 'pause(no-video)';
    }

    function toggle() {
        var v = mainVideo();
        if (v && !v.paused && !v.ended) {
            return pause();
        }
        return play();
    }

    function next() {
        window.__braveUserPaused = false;
        window.__isUserExplicitPause = false;
        window.__braveAllowPauseUntil = 0;
        window.__braveProtectPlayUntil = Date.now() + 2500;

        try {
            var player = getPlayer();
            if (player && typeof player.nextVideo === 'function') {
                player.nextVideo();
                return 'next:player-api';
            }
        } catch (e) {}

        var nextBtn = document.querySelector(
            '.ytp-next-button, button[aria-label*="Next" i], button[aria-label*="Tiếp theo" i], ' +
            '.player-control-next-icon, [aria-label*="Next video" i], [aria-label*="Video tiếp theo" i]'
        );
        if (nextBtn) {
            try {
                nextBtn.click();
                return 'next:button';
            } catch (e) {}
        }

        var nextLink = document.querySelector(
            'ytm-WatchNextSecondaryResultsRenderer a[href*="/watch?v="], ' +
            'ytm-item-section-renderer a[href*="/watch?v="], ' +
            'ytm-media-item a[href*="/watch?v="], ' +
            'ytm-video-with-context-renderer a[href*="/watch?v="], ' +
            'ytm-compact-video-renderer a[href*="/watch?v="]'
        );
        if (nextLink) {
            try {
                nextLink.click();
                return 'next:link';
            } catch (e) {}
        }

        return 'next:none';
    }

    function previous() {
        window.__braveUserPaused = false;
        window.__isUserExplicitPause = false;
        window.__braveAllowPauseUntil = 0;
        window.__braveProtectPlayUntil = Date.now() + 2500;

        var v = mainVideo();
        if (v && isFinite(v.currentTime) && v.currentTime > 5) {
            try {
                v.currentTime = 0;
                if (v.paused) play();
                notifyMediaState();
                return 'prev:restart';
            } catch (e) {}
        }

        try {
            var player = getPlayer();
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
                notifyMediaState();
                return 'prev:zero';
            } catch (e) {}
        }
        return 'prev:none';
    }

    function seekTo(ms) {
        var v = mainVideo();
        if (!v) return 'seek:no-video';
        var targetSec = Math.max(0, Number(ms) / 1000);
        if (!isFinite(targetSec)) return 'seek:invalid';
        try {
            if (isFinite(v.duration) && v.duration > 0) {
                targetSec = Math.min(targetSec, v.duration);
            }
            v.currentTime = targetSec;
            notifyMediaState();
            return 'seek:' + Math.round(targetSec * 1000);
        } catch (e) {
            return 'seek:error';
        }
    }

    function setBackground(isBg) {
        var bg = !!isBg;
        window.__braveAppInBackground = bg;
        var bgPlayEnabled = !(window.__braveSettings && window.__braveSettings.backgroundPlay === false);
        if (bg && bgPlayEnabled) {
            var v = mainVideo();
            if (v && !v.paused && !v.ended) {
                window.__braveProtectPlayUntil = Date.now() + 1500;
            }
        } else if (!bg && !active) {
            // Khi quay lại foreground full-app, mở khoá hoàn toàn các cờ pause và kick re-render
            window.__braveProtectPlayUntil = 0;
            window.__bravePlayPendingUntil = 0;
            window.__bravePipTransitionUntil = 0;
            window.__braveUserPaused = false;
            window.__isUserExplicitPause = false;
            window.__braveAllowPauseUntil = 0;
            triggerMicroSeek();
            [0, 80, 200, 450].forEach(function(delay) {
                setTimeout(function() {
                    try { window.dispatchEvent(new Event('resize')); } catch (e) {}
                    try { window.dispatchEvent(new Event('orientationchange')); } catch (e) {}
                }, delay);
            });
        }
        return bg ? 'bg:on' : 'bg:off';
    }

    function applySettings(settingsObj) {
        if (settingsObj && typeof settingsObj === 'object') {
            window.__braveSettings = Object.assign(window.__braveSettings || {}, settingsObj);
        }
        return JSON.stringify(window.__braveSettings);
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
        applySettings: applySettings,
        mainVideo: mainVideo,
        triggerMicroSeek: triggerMicroSeek,
        isActive: function() { return active; }
    };

    // Tương thích với tên hàm cũ
    window.__enterPipMode = enter;
    window.__exitPipMode = exit;
    window.__braveMainVideo = mainVideo;
    window.__braveMicroSeek = triggerMicroSeek;
})();
