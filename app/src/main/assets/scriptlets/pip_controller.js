/**
 * BRAVE PIP CONTROLLER - YOUTUBE LITE
 * Bộ điều khiển Video Popup (PiP) độc lập.
 *
 * 1. Cô lập video tuyệt đối khi vào PiP:
 *    - Đánh dấu thẻ <video> chính bằng [data-brave-pip-role="video"]
 *    - Đánh dấu chuỗi thẻ cha từ video lên tới body bằng [data-brave-pip-role="ancestor"]
 *    - Ẩn hoàn toàn (display:none) mọi phần tử khác trong body (:not([data-brave-pip-role]))
 *      như thanh header YouTube, nút "Mở ứng dụng", phụ đề, thanh "Tiếp theo", gợi ý...
 *    - Cho chuỗi ancestor trong suốt (background:transparent) và phủ kín 100vw x 100vh
 *      để không một lớp nền đen nào đè lên video và không làm lệch vị trí fixed.
 *
 * 2. Điều khiển Play / Pause trực tiếp qua native HTMLMediaElement.prototype.play/pause
 *    để không bị YouTube chặn bởi kiểm tra kích thước nhỏ (PLAYER_VIEW_TOO_SMALL)
 *    hoặc bị chặn bởi hook chống auto-pause của youtube_defuser.js.
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
        active = true;
        apply();
        if (!timer) timer = setInterval(apply, 300);
    }

    function enter() {
        forcedByNative = true;
        startLoop();
        return currentVideo ? 'pip:on' : 'pip:on(no-video)';
    }

    function stopLoop() {
        var wasActive = active || document.documentElement.hasAttribute(MODE_ATTR);
        active = false;
        currentVideo = null;
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
        if (!wasActive) return 'pip:off';
        document.documentElement.removeAttribute(MODE_ATTR);
        clearMarks();
        removeStyle();
        // Báo resize nhiều nhịp để YouTube tính lại khung player sau khi hiệu ứng mở rộng cửa sổ kết thúc
        [0, 150, 350].forEach(function(delay) {
            setTimeout(function() {
                try { window.dispatchEvent(new Event('resize')); } catch (e) {}
            }, delay);
        });
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
            var isSmallPopup = !isTyping && h > 0 && h <= 280 && w > 0 && w <= 520;
            if (isSmallPopup && mainVideo()) {
                if (!active) startLoop();
            } else if (!forcedByNative && active && h > 340) {
                stopLoop();
            }
        } catch (e) {}
    }

    window.addEventListener('resize', checkViewportSize, true);

    // Mở khóa hook chặn pause của youtube_defuser.js trong một khoảng thời gian ngắn
    function allowPause(ms) {
        window.__isUserExplicitPause = true;
        window.__braveAllowPauseUntil = Date.now() + (ms || 2500);
    }

    function play() {
        window.__braveUserPaused = false;
        window.__bravePlayPendingUntil = Date.now() + 1200;

        var v = mainVideo();
        var nativePlay = window.__braveNativePlay || (v && v.play);
        if (v && nativePlay) {
            try {
                var r = nativePlay.call(v);
                if (r && typeof r.catch === 'function') {
                    r.catch(function() {
                        try {
                            var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                            if (player && typeof player.playVideo === 'function') player.playVideo();
                        } catch (e2) {}
                    });
                }
            } catch (e) {}
        } else {
            try {
                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (player && typeof player.playVideo === 'function') player.playVideo();
            } catch (e) {}
        }

        if (window.AndroidBridge) {
            try { window.AndroidBridge.onVideoPlaying('', ''); } catch (e) {}
        }
        return v ? 'play' : 'play(no-video)';
    }

    function pause() {
        window.__braveUserPaused = true;
        window.__bravePlayPendingUntil = 0;
        allowPause(2500);

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
        return videos.length ? 'pause' : 'pause(no-video)';
    }

    window.__bravePip = {
        enter: enter,
        exit: exit,
        play: play,
        pause: pause,
        mainVideo: mainVideo,
        isActive: function() { return active; }
    };

    // Tương thích với tên hàm cũ
    window.__enterPipMode = enter;
    window.__exitPipMode = exit;
    window.__braveMainVideo = mainVideo;
})();
