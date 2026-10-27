/**
 * IN-APP MINIPLAYER CONTROLLER - YOUTUBE LITE
 * Quản lý trình phát thu nhỏ cố định ở đáy màn hình bên trong app YouTube Lite.
 *
 * Chức năng cốt lõi:
 * 1. Quản lý 2 trạng thái: EXPANDED (xem full trang /watch) và MINIPLAYER (dock 64px ở đáy).
 * 2. Dock thanh điều khiển mượt mà:
 *    - Khung video tỉ lệ 16:9 ở cạnh trái (104x58px, border-radius: 8px, margin-left: 3px).
 *    - Phần giữa: Tiêu đề video + tên kênh (trích xuất từ metadata hoặc document.title), màu trắng #fff, cỡ chữ 12-13px, 1 dòng.
 *    - Cụm điều khiển bên phải:
 *      + Nút Play / Pause (SVG mượt mà, bấm toggle phát/dừng ngay lập tức).
 *      + Nút Đóng [X] (SVG, bấm vào sẽ pause video, ẩn MiniPlayer và gỡ dock).
 * 3. Chạm vào bất kỳ vị trí nào trên thanh MiniPlayer (ngoài 2 nút Play và Close) -> gọi expand()
 *    khôi phục giao diện full size ban đầu, điều hướng lại URL /watch?v=... tương ứng nếu đang ở trang khác, cuộn lên đầu màn hình.
 * 4. Nút Minimize (mũi tên chúc xuống v) ở góc trên bên trái của player khi đang xem video để người dùng có thể bấm vào thu nhỏ bất kỳ lúc nào.
 * 5. Bắt cử chỉ vuốt xuống (Swipe down gesture) trên player khi đang xem để tự động kích hoạt minimize().
 * 6. Toàn bộ trang web bên dưới (m.youtube.com Feed, Search, Channel) vẫn cuộn, chạm, tương tác 100% bình thường.
 * 7. Tuân thủ 100% Trusted Types CSP của YouTube: hoàn toàn không dùng innerHTML, chỉ dùng textContent, createElement, DOM methods chuẩn và createElementNS cho SVG.
 * 8. API toàn cục: window.__braveMiniPlayer.
 */

(function() {
    'use strict';

    if (window.__braveMiniPlayerLoaded) return;
    window.__braveMiniPlayerLoaded = true;

    var DOCK_ID = '__brave_miniplayer_dock';
    var STYLE_ID = '__brave_miniplayer_style';
    var MINIMIZE_BTN_ID = '__brave_minimize_btn';
    var ATTR_MODE = 'data-brave-miniplayer'; // 'active' | ''

    var isMinimizedState = false;
    var savedVideoUrl = '';
    var activeVideo = null;
    var originalVideoParent = null;
    var originalVideoSibling = null;
    var isReparented = false;

    // SVG Path Constants chuẩn
    var SVG_PATH_PLAY = 'M8 5v14l11-7z';
    var SVG_PATH_PAUSE = 'M6 19h4V5H6v14zm8-14v14h4V5h-4z';
    var SVG_PATH_CLOSE = 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z';
    var SVG_PATH_CHEVRON_DOWN = 'M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z';

    // Tạo SVG element an toàn với CSP / Trusted Types
    function createSvgIcon(pathD, size) {
        var s = size || 24;
        var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('width', String(s));
        svg.setAttribute('height', String(s));
        svg.style.pointerEvents = 'none';
        svg.style.display = 'block';

        var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', pathD);
        path.setAttribute('fill', '#ffffff');
        svg.appendChild(path);
        return svg;
    }

    // CSS Rules chuẩn xác theo thiết kế kiến trúc
    var CSS_RULES = [
        '/* Dock thanh phát thu nhỏ */',
        '#' + DOCK_ID + ' {',
        '  position: fixed !important;',
        '  bottom: 12px !important;',
        '  left: 12px !important;',
        '  right: 12px !important;',
        '  height: 64px !important;',
        '  background: #212121 !important;',
        '  border-radius: 12px !important;',
        '  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.6) !important;',
        '  z-index: 999999 !important;',
        '  display: none !important;',
        '  align-items: center !important;',
        '  overflow: hidden !important;',
        '  box-sizing: border-box !important;',
        '  user-select: none !important;',
        '  -webkit-user-select: none !important;',
        '  -webkit-tap-highlight-color: transparent !important;',
        '  cursor: pointer !important;',
        '  padding: 0 !important;',
        '  margin: 0 !important;',
        '}',
        'html[' + ATTR_MODE + '="active"] #' + DOCK_ID + ' {',
        '  display: flex !important;',
        '}',
        '/* Khung chứa video 16:9 ở cạnh trái */',
        '#__brave_mini_slot {',
        '  width: 104px !important;',
        '  height: 58px !important;',
        '  min-width: 104px !important;',
        '  max-width: 104px !important;',
        '  min-height: 58px !important;',
        '  max-height: 58px !important;',
        '  border-radius: 8px !important;',
        '  margin-left: 3px !important;',
        '  overflow: hidden !important;',
        '  flex-shrink: 0 !important;',
        '  background: #000000 !important;',
        '  position: relative !important;',
        '  display: flex !important;',
        '  align-items: center !important;',
        '  justify-content: center !important;',
        '}',
        '#__brave_mini_slot video {',
        '  width: 104px !important;',
        '  height: 58px !important;',
        '  min-width: 104px !important;',
        '  max-width: 104px !important;',
        '  min-height: 58px !important;',
        '  max-height: 58px !important;',
        '  border-radius: 8px !important;',
        '  object-fit: cover !important;',
        '  display: block !important;',
        '  position: static !important;',
        '  margin: 0 !important;',
        '  padding: 0 !important;',
        '  pointer-events: none !important;',
        '}',
        '/* Phần giữa hiển thị Tiêu đề và Kênh */',
        '#__brave_mini_info {',
        '  flex: 1 1 0% !important;',
        '  min-width: 0 !important;',
        '  margin-left: 10px !important;',
        '  margin-right: 8px !important;',
        '  display: flex !important;',
        '  flex-direction: column !important;',
        '  justify-content: center !important;',
        '  overflow: hidden !important;',
        '  pointer-events: none !important;',
        '}',
        '#__brave_mini_title {',
        '  color: #ffffff !important;',
        '  font-size: 13px !important;',
        '  font-weight: 500 !important;',
        '  line-height: 16px !important;',
        '  overflow: hidden !important;',
        '  text-overflow: ellipsis !important;',
        '  white-space: nowrap !important;',
        '  font-family: Roboto, -apple-system, BlinkMacSystemFont, Arial, sans-serif !important;',
        '  margin: 0 !important;',
        '  padding: 0 !important;',
        '}',
        '#__brave_mini_channel {',
        '  color: #aaaaaa !important;',
        '  font-size: 11px !important;',
        '  line-height: 14px !important;',
        '  overflow: hidden !important;',
        '  text-overflow: ellipsis !important;',
        '  white-space: nowrap !important;',
        '  margin-top: 2px !important;',
        '  font-family: Roboto, -apple-system, BlinkMacSystemFont, Arial, sans-serif !important;',
        '  margin-bottom: 0 !important;',
        '  padding: 0 !important;',
        '}',
        '/* Cụm nút điều khiển bên phải */',
        '#__brave_mini_controls {',
        '  display: flex !important;',
        '  align-items: center !important;',
        '  flex-shrink: 0 !important;',
        '  margin-right: 6px !important;',
        '  gap: 2px !important;',
        '}',
        '.brave-mini-btn {',
        '  width: 38px !important;',
        '  height: 38px !important;',
        '  border-radius: 50% !important;',
        '  background: transparent !important;',
        '  border: none !important;',
        '  display: flex !important;',
        '  align-items: center !important;',
        '  justify-content: center !important;',
        '  cursor: pointer !important;',
        '  padding: 0 !important;',
        '  margin: 0 !important;',
        '  outline: none !important;',
        '  -webkit-tap-highlight-color: transparent !important;',
        '  color: #ffffff !important;',
        '  transition: background-color 0.15s ease !important;',
        '}',
        '.brave-mini-btn:active {',
        '  background-color: rgba(255, 255, 255, 0.2) !important;',
        '}',
        '/* Nút Minimize trên player full màn hình */',
        '#' + MINIMIZE_BTN_ID + ' {',
        '  position: absolute !important;',
        '  top: 10px !important;',
        '  left: 10px !important;',
        '  width: 36px !important;',
        '  height: 36px !important;',
        '  border-radius: 50% !important;',
        '  background: rgba(0, 0, 0, 0.55) !important;',
        '  border: 1px solid rgba(255, 255, 255, 0.2) !important;',
        '  display: flex !important;',
        '  align-items: center !important;',
        '  justify-content: center !important;',
        '  z-index: 2147483640 !important;',
        '  cursor: pointer !important;',
        '  padding: 0 !important;',
        '  margin: 0 !important;',
        '  outline: none !important;',
        '  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4) !important;',
        '  transition: transform 0.15s ease, background 0.15s ease !important;',
        '  -webkit-tap-highlight-color: transparent !important;',
        '}',
        '#' + MINIMIZE_BTN_ID + ':active {',
        '  transform: scale(0.9) !important;',
        '  background: rgba(0, 0, 0, 0.8) !important;',
        '}',
        '/* Ẩn nút minimize khi đang ở MiniPlayer hoặc PiP */',
        'html[' + ATTR_MODE + '="active"] #' + MINIMIZE_BTN_ID + ', html[data-brave-pip-mode] #' + MINIMIZE_BTN_ID + ' {',
        '  display: none !important;',
        '}',
        '/* Đảm bảo trang web bên dưới cuộn bình thường không bị dock che khuất nội dung dưới đáy */',
        'html[' + ATTR_MODE + '="active"] body {',
        '  padding-bottom: 84px !important;',
        '}',
        '/* Nếu PiP hệ thống được bật, ẩn MiniPlayer dock */',
        'html[data-brave-pip-mode] #' + DOCK_ID + ' {',
        '  display: none !important;',
        '}'
    ].join('\n');

    // Chèn hoặc cập nhật thẻ <style>
    function ensureStyle() {
        var style = document.getElementById(STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            style.type = 'text/css';
            style.textContent = CSS_RULES;
            var target = document.head || document.documentElement;
            if (target) target.appendChild(style);
        } else if (style.textContent !== CSS_RULES) {
            style.textContent = CSS_RULES;
        }
    }

    // Tìm video chính đang phát
    function findMainVideo() {
        if (window.__bravePip && typeof window.__bravePip.mainVideo === 'function') {
            var pipVid = window.__bravePip.mainVideo();
            if (pipVid) return pipVid;
        }
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

    // Tìm container chứa player
    function findPlayerContainer() {
        return document.getElementById('player-container-id') ||
               document.querySelector('.player-container') ||
               document.getElementById('movie_player') ||
               document.querySelector('.html5-video-player') ||
               document.getElementById('player');
    }

    // Trích xuất metadata tiêu đề và kênh
    function getMetadata() {
        var titleEl = document.querySelector(
            'h1.slim-video-metadata-title, .ytm-slim-video-metadata-renderer-title, .media-item-headline, .ytp-title-link'
        );
        var channelEl = document.querySelector(
            '.ytm-channel-thumbnail-with-profile-name, .slim-owner-channel-name, .subhead, .ytd-channel-name'
        );
        var rawTitle = titleEl && titleEl.textContent ? titleEl.textContent.trim() : '';
        var docTitle = document.title ? document.title.replace(/\s*-\s*YouTube$/i, '').trim() : '';
        var title = rawTitle || docTitle || 'YouTube Lite';
        var channel = channelEl && channelEl.textContent ? channelEl.textContent.trim() : '';
        return { title: title, channel: channel };
    }

    // Các phần tử của Dock
    var dockElement = null;
    var videoSlotElement = null;
    var titleElement = null;
    var channelElement = null;
    var playPauseBtn = null;
    var playPauseIconSlot = null;
    var closeBtn = null;

    function buildDockDOM() {
        if (dockElement && document.body && document.body.contains(dockElement)) return;

        dockElement = document.createElement('div');
        dockElement.id = DOCK_ID;

        // Slot video bên trái
        videoSlotElement = document.createElement('div');
        videoSlotElement.id = '__brave_mini_slot';
        dockElement.appendChild(videoSlotElement);

        // Phần thông tin ở giữa
        var info = document.createElement('div');
        info.id = '__brave_mini_info';

        titleElement = document.createElement('div');
        titleElement.id = '__brave_mini_title';
        titleElement.textContent = 'YouTube Lite';
        info.appendChild(titleElement);

        channelElement = document.createElement('div');
        channelElement.id = '__brave_mini_channel';
        channelElement.textContent = '';
        info.appendChild(channelElement);

        dockElement.appendChild(info);

        // Cụm nút điều khiển bên phải
        var controls = document.createElement('div');
        controls.id = '__brave_mini_controls';

        // Nút Play / Pause
        playPauseBtn = document.createElement('button');
        playPauseBtn.className = 'brave-mini-btn';
        playPauseBtn.setAttribute('aria-label', 'Phát hoặc Tạm dừng');
        playPauseIconSlot = document.createElement('span');
        playPauseIconSlot.style.display = 'block';
        playPauseIconSlot.style.pointerEvents = 'none';
        playPauseIconSlot.appendChild(createSvgIcon(SVG_PATH_PAUSE, 22));
        playPauseBtn.appendChild(playPauseIconSlot);

        playPauseBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            e.preventDefault();
            togglePlayPause();
        });
        controls.appendChild(playPauseBtn);

        // Nút Close [X]
        closeBtn = document.createElement('button');
        closeBtn.className = 'brave-mini-btn';
        closeBtn.setAttribute('aria-label', 'Đóng trình phát thu nhỏ');
        closeBtn.appendChild(createSvgIcon(SVG_PATH_CLOSE, 20));

        closeBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            e.preventDefault();
            closeMiniPlayer();
        });
        controls.appendChild(closeBtn);

        dockElement.appendChild(controls);

        // Khi chạm vào thanh MiniPlayer (ngoài 2 nút Play và Close) -> expand()
        dockElement.addEventListener('click', function(e) {
            if (e.target && e.target.closest && e.target.closest('.brave-mini-btn')) return;
            expandMiniPlayer();
        });

        var parent = document.body || document.documentElement;
        if (parent) parent.appendChild(dockElement);
    }

    // Cập nhật icon Play / Pause
    function updatePlayPauseIcon(isPlaying) {
        if (!playPauseIconSlot) return;
        while (playPauseIconSlot.firstChild) {
            playPauseIconSlot.removeChild(playPauseIconSlot.firstChild);
        }
        var path = isPlaying ? SVG_PATH_PAUSE : SVG_PATH_PLAY;
        playPauseIconSlot.appendChild(createSvgIcon(path, 22));
    }

    // Toggle Play / Pause
    function togglePlayPause() {
        var v = activeVideo || findMainVideo();
        if (!v) return;

        if (!v.paused && !v.ended) {
            // Tạm dừng
            window.__braveUserPaused = true;
            window.__braveProtectPlayUntil = 0;
            try {
                if (window.__braveNativePause) {
                    window.__braveNativePause.call(v);
                } else {
                    v.pause();
                }
            } catch (e) {}
            updatePlayPauseIcon(false);
            if (window.AndroidBridge && typeof window.AndroidBridge.onVideoPaused === 'function') {
                try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
            }
        } else {
            // Tiếp tục phát
            window.__braveUserPaused = false;
            window.__braveProtectPlayUntil = Date.now() + 2500;
            try {
                if (window.__braveNativePlay) {
                    window.__braveNativePlay.call(v);
                } else {
                    v.play();
                }
            } catch (e) {}
            updatePlayPauseIcon(true);
            if (window.AndroidBridge && typeof window.AndroidBridge.onVideoPlaying === 'function') {
                try { window.AndroidBridge.onVideoPlaying('', ''); } catch (e) {}
            }
        }
    }

    // Cập nhật metadata hiển thị
    function updateInfoDisplay() {
        var meta = getMetadata();
        if (titleElement) titleElement.textContent = meta.title;
        if (channelElement) {
            channelElement.textContent = meta.channel;
            channelElement.style.display = meta.channel ? 'block' : 'none';
        }
    }

    function onVideoPlayStateChange() {
        if (!activeVideo) return;
        updatePlayPauseIcon(!activeVideo.paused && !activeVideo.ended);
    }

    // ==============================================================
    // 1. MINIMIZE LOGIC
    // ==============================================================

    function minimizeMiniPlayer() {
        ensureStyle();
        buildDockDOM();

        var video = findMainVideo();
        if (!video) return 'miniplayer:no-video';

        activeVideo = video;
        if (window.location.href.indexOf('/watch') !== -1) {
            savedVideoUrl = window.location.href;
        }

        // Lưu vị trí DOM ban đầu của video và di chuyển vào slot Dock
        if (!isReparented) {
            originalVideoParent = video.parentNode;
            originalVideoSibling = video.nextSibling;

            var wasPlaying = !video.paused && !video.ended;
            window.__braveProtectPlayUntil = Date.now() + 2500;

            videoSlotElement.appendChild(video);
            isReparented = true;

            if (wasPlaying && video.paused) {
                try {
                    if (window.__braveNativePlay) {
                        window.__braveNativePlay.call(video);
                    } else {
                        video.play();
                    }
                } catch (e) {}
                [100, 300].forEach(function(delay) {
                    setTimeout(function() {
                        if (wasPlaying && video.paused && !window.__braveUserPaused) {
                            window.__braveProtectPlayUntil = Date.now() + 1500;
                            try {
                                if (window.__braveNativePlay) window.__braveNativePlay.call(video);
                                else video.play();
                            } catch (e2) {}
                        }
                    }, delay);
                });
            }
        }

        updateInfoDisplay();
        updatePlayPauseIcon(!video.paused && !video.ended);

        isMinimizedState = true;
        document.documentElement.setAttribute(ATTR_MODE, 'active');

        // Báo callback trạng thái về Native AndroidBridge
        if (window.AndroidBridge && typeof window.AndroidBridge.onMiniPlayerStateChanged === 'function') {
            try { window.AndroidBridge.onMiniPlayerStateChanged(true); } catch (e) {}
        }

        // Lắng nghe sự kiện play/pause trên video
        video.removeEventListener('play', onVideoPlayStateChange);
        video.removeEventListener('pause', onVideoPlayStateChange);
        video.addEventListener('play', onVideoPlayStateChange);
        video.addEventListener('pause', onVideoPlayStateChange);

        // Nếu đang ở /watch, chuyển hướng nhẹ về trang trước hoặc trang chủ để duyệt nội dung khác
        if (window.location.pathname.indexOf('/watch') !== -1) {
            if (window.history.length > 1) {
                try { window.history.back(); } catch (e) {}
            }
        }

        return 'miniplayer:minimized';
    }

    // ==============================================================
    // 2. EXPAND LOGIC
    // ==============================================================

    function expandMiniPlayer() {
        if (!isMinimizedState && !isReparented) return 'miniplayer:not-minimized';

        isMinimizedState = false;
        document.documentElement.removeAttribute(ATTR_MODE);

        // Khôi phục video về lại vị trí DOM ban đầu
        if (isReparented && activeVideo) {
            var wasPlaying = !activeVideo.paused && !activeVideo.ended;
            window.__braveProtectPlayUntil = Date.now() + 2500;

            if (originalVideoParent) {
                if (originalVideoSibling && originalVideoSibling.parentNode === originalVideoParent) {
                    originalVideoParent.insertBefore(activeVideo, originalVideoSibling);
                } else {
                    originalVideoParent.appendChild(activeVideo);
                }
            }
            isReparented = false;

            if (wasPlaying && activeVideo.paused) {
                try {
                    if (window.__braveNativePlay) {
                        window.__braveNativePlay.call(activeVideo);
                    } else {
                        activeVideo.play();
                    }
                } catch (e) {}
            }
        }

        // Báo callback về Native AndroidBridge
        if (window.AndroidBridge && typeof window.AndroidBridge.onMiniPlayerStateChanged === 'function') {
            try { window.AndroidBridge.onMiniPlayerStateChanged(false); } catch (e) {}
        }

        // Điều hướng lại URL /watch nếu đang ở trang khác, hoặc cuộn lên đầu màn hình
        if (savedVideoUrl && window.location.href !== savedVideoUrl) {
            window.location.href = savedVideoUrl;
        } else {
            try {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            } catch (e) {
                window.scrollTo(0, 0);
            }
        }

        // Kích hoạt resize event để YouTube tính toán lại khung hiển thị full
        [50, 200].forEach(function(d) {
            setTimeout(function() {
                try { window.dispatchEvent(new Event('resize')); } catch (e) {}
            }, d);
        });

        return 'miniplayer:expanded';
    }

    // ==============================================================
    // 3. CLOSE LOGIC
    // ==============================================================

    function closeMiniPlayer() {
        // Dừng phát video
        window.__braveUserPaused = true;
        if (activeVideo) {
            try {
                if (window.__braveNativePause) {
                    window.__braveNativePause.call(activeVideo);
                } else {
                    activeVideo.pause();
                }
            } catch (e) {}
        }

        // Khôi phục video về vị trí cũ
        if (isReparented && activeVideo && originalVideoParent) {
            if (originalVideoSibling && originalVideoSibling.parentNode === originalVideoParent) {
                originalVideoParent.insertBefore(activeVideo, originalVideoSibling);
            } else {
                originalVideoParent.appendChild(activeVideo);
            }
            isReparented = false;
        }

        isMinimizedState = false;
        document.documentElement.removeAttribute(ATTR_MODE);

        if (window.AndroidBridge && typeof window.AndroidBridge.onMiniPlayerStateChanged === 'function') {
            try { window.AndroidBridge.onMiniPlayerStateChanged(false); } catch (e) {}
        }
        if (window.AndroidBridge && typeof window.AndroidBridge.onVideoPaused === 'function') {
            try { window.AndroidBridge.onVideoPaused(); } catch (e) {}
        }

        activeVideo = null;
        savedVideoUrl = '';
        return 'miniplayer:closed';
    }

    // ==============================================================
    // 4. NÚT MINIMIZE (v) TRÊN PLAYER FULL
    // ==============================================================

    function ensureMinimizeButton() {
        if (isMinimizedState) return;

        var player = findPlayerContainer();
        if (!player) return;

        var existingBtn = document.getElementById(MINIMIZE_BTN_ID);
        if (!existingBtn) {
            var btn = document.createElement('button');
            btn.id = MINIMIZE_BTN_ID;
            btn.setAttribute('aria-label', 'Thu nhỏ trình phát');
            btn.appendChild(createSvgIcon(SVG_PATH_CHEVRON_DOWN, 22));

            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                e.preventDefault();
                minimizeMiniPlayer();
            });

            // Đảm bảo container có định vị phù hợp
            var compStyle = window.getComputedStyle(player);
            if (compStyle.position === 'static') {
                player.style.position = 'relative';
            }

            player.appendChild(btn);
        }
    }

    // ==============================================================
    // 5. CỬ CHỈ VUỐT XUỐNG (SWIPE DOWN GESTURE)
    // ==============================================================

    var touchStartX = 0;
    var touchStartY = 0;
    var touchStartTime = 0;
    var isSwiping = false;

    function attachSwipeGesture(target) {
        if (!target || target.__braveSwipeAttached) return;
        target.__braveSwipeAttached = true;

        target.addEventListener('touchstart', function(e) {
            if (isMinimizedState) return;
            if (e.touches.length !== 1) return;

            var touch = e.touches[0];
            var rect = target.getBoundingClientRect();

            // Bỏ qua nếu chạm vào thanh tua tiến trình (thường ở 35px dưới cùng của player)
            if (touch.clientY > rect.bottom - 40) return;

            touchStartX = touch.clientX;
            touchStartY = touch.clientY;
            touchStartTime = Date.now();
            isSwiping = true;
        }, { passive: true });

        target.addEventListener('touchmove', function(e) {
            if (!isSwiping || isMinimizedState) return;
        }, { passive: true });

        target.addEventListener('touchend', function(e) {
            if (!isSwiping || isMinimizedState) return;
            isSwiping = false;

            if (e.changedTouches.length !== 1) return;
            var touch = e.changedTouches[0];
            var deltaX = touch.clientX - touchStartX;
            var deltaY = touch.clientY - touchStartY;
            var duration = Date.now() - touchStartTime;

            // Vuốt xuống tối thiểu 50px trong vòng dưới 600ms và theo phương dọc
            if (deltaY > 50 && Math.abs(deltaY) > Math.abs(deltaX) * 1.3 && duration < 600) {
                minimizeMiniPlayer();
            }
        }, { passive: true });
    }

    // ==============================================================
    // 6. ĐIỀU HƯỚNG TỰ ĐỘNG & BẢO TRÌ ĐỊNH KỲ
    // ==============================================================

    // Khi người dùng bấm vào một video khác trong lúc MiniPlayer đang chạy -> Tự động mở full video mới
    document.addEventListener('click', function(e) {
        if (!isMinimizedState) return;
        var anchor = e.target && e.target.closest ? e.target.closest('a[href*="/watch"]') : null;
        if (anchor) {
            var href = anchor.getAttribute('href') || '';
            if (href.indexOf('/watch') !== -1) {
                savedVideoUrl = anchor.href || href;
                setTimeout(function() {
                    expandMiniPlayer();
                }, 50);
            }
        }
    }, true);

    window.addEventListener('popstate', function() {
        if (isMinimizedState && window.location.pathname.indexOf('/watch') !== -1) {
            if (!savedVideoUrl || window.location.href !== savedVideoUrl) {
                expandMiniPlayer();
            }
        }
    }, true);

    window.addEventListener('yt-navigate-finish', function() {
        if (isMinimizedState && window.location.pathname.indexOf('/watch') !== -1) {
            if (!savedVideoUrl || window.location.href !== savedVideoUrl) {
                expandMiniPlayer();
            }
        }
        setTimeout(ensureMinimizeButton, 400);
    }, true);

    function initLoop() {
        ensureStyle();
        ensureMinimizeButton();

        var player = findPlayerContainer();
        if (player) {
            attachSwipeGesture(player);
        }

        if (isMinimizedState) {
            updateInfoDisplay();
            var v = activeVideo || findMainVideo();
            if (v) updatePlayPauseIcon(!v.paused && !v.ended);
        }
    }

    // Đăng ký API toàn cục
    window.__braveMiniPlayer = {
        minimize: minimizeMiniPlayer,
        expand: expandMiniPlayer,
        close: closeMiniPlayer,
        isMinimized: function() { return isMinimizedState; },
        togglePlayPause: togglePlayPause
    };

    // Khởi chạy
    ensureStyle();
    setInterval(initLoop, 1000);
    initLoop();

    console.log('[BraveMiniPlayer] In-App MiniPlayer Controller đã sẵn sàng!');
})();
