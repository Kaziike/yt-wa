# Luồng Dữ Liệu & Quy Trình Tương Tác (Data Flow)

Tài liệu này mô tả chi tiết các chuỗi sự kiện, luồng xử lý dữ liệu và giao thức IPC (Inter-Process Communication) giữa người dùng, hệ điều hành Android, Native Kotlin và Web Engine của ứng dụng **YouTube Lite**.

---

## 1. Luồng 1: Bấm Xem Video & Kích Hoạt In-App MiniPlayer 2 Tầng

Quy trình người dùng bấm vào một video trên trang chủ và hệ thống mở lớp phát nổi đè lên mà không làm mất trang chủ bên dưới:

```mermaid
sequenceDiagram
    autonumber
    actor User as Người Dùng
    participant BrowseWeb as webView (Browse)
    participant Interceptor as browseWatchInterceptorJs
    participant NativeBridge as AndroidBridge (IPC)
    participant Main as MainActivity
    participant PlayerPanel as playerPanel (Layer 2)
    participant PlayerWeb as playerWebView (Player)

    User->>BrowseWeb: Chạm vào thumbnail video (/watch?v=...)
    BrowseWeb->>Interceptor: Bắt sự kiện click tại Capture Phase
    Note over Interceptor: Kiểm tra URL chứa /watch & không chứa /shorts/
    Interceptor->>BrowseWeb: e.preventDefault() & stopImmediatePropagation()
    Note over BrowseWeb: Giữ nguyên vị trí cuộn & feed trang chủ 100%!
    Interceptor->>NativeBridge: window.AndroidBridge.openWatchLayer(fullUrl)
    NativeBridge->>Main: runOnUiThread { openVideoPlayer(url) }
    
    Main->>PlayerPanel: Đặt layoutParams.height = MATCH_PARENT, gravity = TOP
    Main->>PlayerPanel: visibility = VISIBLE, translationY = 0f
    Main->>Main: panelState = PanelState.EXPANDED
    Main->>PlayerWeb: loadUrl(fullUrl)
    Note over PlayerWeb: Bắt đầu phát video trên tầng nổi!
```

---

## 2. Luồng 2: Thu Nhỏ Video Thành MiniPlayer & Bung To Lại

```mermaid
sequenceDiagram
    autonumber
    actor User as Người Dùng
    participant Main as MainActivity
    participant PlayerPanel as playerPanel
    participant PlayerWeb as playerWebView
    participant MiniBar as miniPlayerBar
    participant BrowseWeb as webView (Browse)

    rect rgb(30, 30, 45)
    note right of User: Giai đoạn 1: Thu nhỏ (Collapse)
    User->>Main: Vuốt mép video xuống hoặc bấm nút mũi tên [∨]
    Main->>Main: panelState = PanelState.COLLAPSED
    Main->>MiniBar: visibility = VISIBLE
    Main->>PlayerPanel: Animate trượt xuống targetY (H - 64dp) trong 260ms
    Main->>PlayerWeb: Animate scale thu nhỏ vào khung 104dp bên trái
    Main->>PlayerPanel: withEndAction: height = 64dp, gravity = BOTTOM, translationY = 0f
    Note over BrowseWeb: Toàn bộ vùng màn hình phía trên hoàn toàn tự do nhận cảm ứng!
    end

    rect rgb(30, 45, 30)
    note right of User: Giai đoạn 2: Bung to lại (Expand)
    User->>MiniBar: Chạm vào thanh MiniPlayer hoặc vuốt lên
    Main->>Main: panelState = PanelState.EXPANDED
    Main->>PlayerPanel: height = MATCH_PARENT, gravity = TOP, translationY = targetY
    Main->>PlayerPanel: Animate trượt lên translationY = 0f trong 260ms
    Main->>PlayerWeb: Animate scale to về 1.0f
    Main->>MiniBar: visibility = GONE
    end

    rect rgb(45, 30, 30)
    note right of User: Giai đoạn 3: Đóng hẳn (Close)
    User->>MiniBar: Nhấn nút đóng [✕]
    Main->>PlayerPanel: Animate trượt hẳn xuống mép dưới màn hình (H)
    Main->>PlayerPanel: withEndAction: visibility = GONE
    Main->>PlayerWeb: loadUrl("about:blank")
    Main->>Main: stopMediaService() & updatePipParams()
    end
```

---

## 3. Luồng 3: Điều Khiển Media Từ Hệ Thống (Lockscreen, Notification, Bluetooth)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người Dùng
    participant SystemUI as Màn hình khóa / Notification / Tai nghe
    participant MediaSvc as MediaService (MediaSessionCompat)
    participant Receiver as mediaActionReceiver (MainActivity)
    participant PiPJs as pip_controller.js
    participant Video as HTMLMediaElement (<video>)

    User->>SystemUI: Nhấn nút Play / Pause / Next / Seek
    SystemUI->>MediaSvc: MediaSessionCompat.Callback (onPlay, onPause, onSeekTo...)
    MediaSvc->>Receiver: Gửi Broadcast (ACTION_MEDIA_PLAY / PAUSE / SEEK)
    Receiver->>PiPJs: runPipCommand("play" / "pause" / "seekTo(pos)")
    PiPJs->>Video: Gọi native HTMLMediaElement.prototype.play() / pause()
    Video-->>PiPJs: Bắn sự kiện timeupdate / play / pause
    PiPJs->>MediaSvc: Đồng bộ trạng thái mới qua AndroidBridge
    MediaSvc->>SystemUI: Cập nhật PlaybackState & Notification thời gian thực
```

---

## 4. Luồng 4: Bộ Nhớ Đệm Tài Nguyên Tĩnh (Static Preload Cache)

```mermaid
sequenceDiagram
    autonumber
    participant WebView as Chromium Web Engine
    participant Client as AppWebViewClient (shouldInterceptRequest)
    participant CacheMgr as WebResourceCacheManager
    participant Disk as Bộ nhớ cục bộ (cache/web_static/)
    participant Network as Máy chủ YouTube CDN

    WebView->>Client: shouldInterceptRequest(url)
    Client->>CacheMgr: WebResourceCacheManager.intercept(request)
    CacheMgr->>CacheMgr: Tính MD5(url) & kiểm tra loại file tĩnh
    alt Đã tồn tại trong Disk Cache
        CacheMgr->>Disk: Đọc file nhị phân từ ổ đĩa
        Disk-->>CacheMgr: Trả về InputStream
        CacheMgr-->>Client: WebResourceResponse(mime, "UTF-8", stream) (0ms delay)
        Client-->>WebView: Trả dữ liệu ngay lập tức, không tốn data mạng!
    else Chưa có trong Cache
        CacheMgr-->>Client: Trả về null
        Client->>Network: Tải dữ liệu từ mạng qua Chromium Engine
        Network-->>CacheMgr: Tải hoàn tất -> Lưu vào Disk Cache cho các lần sau
    end
```
