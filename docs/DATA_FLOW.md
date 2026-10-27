# Luồng Dữ Liệu & Trình Tự Hoạt Động (Data Flow)

Tài liệu này mô tả chi tiết các luồng sự kiện, luồng dữ liệu mạng, vòng đời tương tác giữa người dùng, hệ điều hành Android, mã nguồn Kotlin Native và các Scriptlet Web.

---

## 1. Luồng Khởi Động Ứng Dụng (Application Startup Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Main as MainActivity
    participant Engine as AdBlockEngine
    participant Client as AppWebViewClient
    participant View as KeepAliveWebView
    participant Svc as MediaService

    User->>Main: Mở ứng dụng (onCreate)
    Main->>Main: Đăng ký pipActionReceiver (RECEIVER_EXPORTED)
    Main->>Main: Yêu cầu quyền POST_NOTIFICATIONS (Android 13+)
    Main->>Engine: AdBlockEngine.init(context)
    Engine->>Engine: Đọc blocked_domains.txt vào HashSet
    Main->>Main: Nạp trước pip_controller.js vào bộ nhớ
    Main->>View: Cấu hình WebSettings (JS on, offscreenPreRaster off)
    Main->>View: webView.addJavascriptInterface(MediaBridge, "AndroidBridge")
    Main->>Client: Khởi tạo AppWebViewClient(context)
    Client->>Client: Nạp pip_controller.js, youtube_defuser.js, cosmetic_rules.css
    Client->>View: installDocumentStartScripts() (DOCUMENT_START)
    Main->>View: webView.loadUrl("https://m.youtube.com")
```

---

## 2. Luồng Chặn Mạng Tầng Native (Network Interception Flow)

Mỗi khi trình duyệt nội bộ gửi yêu cầu tải bất kỳ tài nguyên nào (HTML, script, ảnh, video chunk, telemetry), yêu cầu đó đều đi qua bộ lọc `shouldInterceptRequest`:

```mermaid
flowchart TD
    Req["Request WebResourceRequest(URL)"] --> Client["AppWebViewClient.shouldInterceptRequest()"]
    Client --> Parse["Uri.parse(url)<br>Trích xuất host và path"]
    Parse --> CheckDomain{"host có trong HashSet<br>hoặc trùng subdomain?"}
    
    CheckDomain -->|Có| Block["Ghi log 🛡️ [Blocked]<br>Trả về WebResourceResponse rỗng<br>(text/plain, 0 byte)"]
    CheckDomain -->|Không| CheckPath{"path có chứa pattern<br>/api/stats/ads<br>/pagead/<br>/youtubei/v1/log_event?"}
    
    CheckPath -->|Có| Block
    CheckPath -->|Không| Allow["Gọi super.shouldInterceptRequest()<br>Tiếp tục tải tài nguyên bình thường"]
```

---

## 3. Luồng Đồng Bộ Trạng Thái JavaScript $\leftrightarrow$ Kotlin Native

Trạng thái của video đang phát được đồng bộ liên tục để phục vụ cho cửa sổ PiP và Foreground Service:

```mermaid
sequenceDiagram
    autonumber
    participant Video as HTML5 Video Element
    participant Defuser as youtube_defuser.js
    participant PiPJS as pip_controller.js
    participant Bridge as AndroidBridge (JavascriptInterface)
    participant Main as MainActivity

    Video->>Defuser: Sự kiện "play" hoặc "playing"
    Defuser->>Defuser: Trích xuất Title & Channel từ DOM
    Defuser->>Bridge: AndroidBridge.onVideoPlaying(title, channel)
    Bridge->>Main: runOnUiThread { isVideoPlaying = true; currentTitle; currentChannel; }
    Main->>Main: updatePipParams() (Nút đổi thành PAUSE)

    Video->>PiPJS: Sự kiện "loadedmetadata" / "resize"
    PiPJS->>Bridge: AndroidBridge.onVideoSize(width, height)
    Bridge->>Main: runOnUiThread { videoAspectRatio = Rational(w, h); }
    Main->>Main: updatePipParams() (Khung PiP khớp tỉ lệ thật)

    Video->>Defuser: Sự kiện "pause"
    Defuser->>Bridge: AndroidBridge.onVideoPaused()
    Bridge->>Main: runOnUiThread { isVideoPlaying = false; }
    Main->>Main: updatePipParams() (Nút đổi thành PLAY)
```

---

## 4. Luồng Chuyển Đổi Sang Picture-in-Picture (PiP Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Main as MainActivity
    participant View as KeepAliveWebView
    participant PiP as pip_controller.js
    participant OS as Hệ điều hành Android

    User->>Main: Bấm Home hoặc vuốt về màn hình chính
    Main->>Main: onUserLeaveHint() kích hoạt
    alt isVideoPlaying == true hoặc isFullscreen == true
        Main->>Main: enterPipLayout()
        Main->>PiP: runPipCommand("enter")
        Note over PiP: Bật CSS cô lập [data-brave-pip-mode="on"]<br/>Ẩn header, bottom bar, gợi ý<br/>Phủ kín thẻ video 100vw x 100vh
        Main->>OS: enterPictureInPictureMode(buildPipParams())
        OS->>Main: onPictureInPictureModeChanged(true)
        Main->>View: postDelayed(enterPipLayout, 150ms)
        Main->>View: postDelayed(enterPipLayout, 450ms)
        Main->>Main: updatePipParams()
    else Video không phát
        Main->>OS: Thoát ra màn hình chính bình thường
    end
```

---

## 5. Luồng Duy Trì Âm Thanh Khi Khóa Màn Hình (Screen Off Lifecycle Flow)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Main as MainActivity
    participant Svc as MediaService
    participant OS as PowerManager & Android Audio

    User->>OS: Nhấn nút nguồn tắt màn hình
    OS->>Main: onPause() $\rightarrow$ onStop()
    alt isVideoPlaying == true
        Main->>Svc: startForegroundService(ACTION_START, title, channel)
        Svc->>OS: WakeLock.acquire(2 giờ)
        Svc->>OS: startForeground(NOTIFICATION_ID, MediaNotification)
        Note over OS: CPU giữ trạng thái hoạt động một phần (PARTIAL_WAKE_LOCK)<br/>Chromium tiếp tục giải mã AudioTrack mà không bị ngắt
    end

    User->>OS: Bật sáng màn hình & mở khóa điện thoại
    OS->>Main: onStart() $\rightarrow$ onResume()
    Main->>Svc: stopMediaService(ACTION_STOP)
    Svc->>OS: WakeLock.release()
    Svc->>Svc: stopForeground(STOP_FOREGROUND_REMOVE) & stopSelf()
```
