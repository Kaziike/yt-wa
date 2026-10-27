# Hướng Dẫn Mã Nguồn Native Android (Kotlin Logic Guide)

Tài liệu này phân tích chi tiết toàn bộ các lớp mã nguồn Kotlin trong package `com.brave.youtubelite`, nhiệm vụ từng thành phần, các phương thức cốt lõi và các mối phụ thuộc quan trọng.

---

## 1. `MainActivity.kt`

- **Vị trí file:** `app/src/main/java/com/brave/youtubelite/MainActivity.kt`
- **Kế thừa:** `androidx.appcompat.app.AppCompatActivity`
- **Vai trò:** Trung tâm điều phối giao diện, quản lý trạng thái phát video, xử lý sự kiện hệ thống (PiP, xoay màn hình, phím back) và cầu nối IPC với JavaScript.

### Các biến trạng thái quan trọng
- `isVideoPlaying: Boolean`: Theo dõi video hiện tại có đang phát hay không (cập nhật từ `MediaBridge`).
- `videoAspectRatio: Rational`: Tỉ lệ khung hình thật của video (mặc định 16:9, tự động cập nhật khi video nạp metadata).
- `isFullscreen: Boolean`: Đánh dấu trạng thái phóng to toàn màn hình của web player.
- `pipActionReceiver: BroadcastReceiver`: Lắng nghe 2 hành động `ACTION_PIP_PLAY` và `ACTION_PIP_PAUSE` từ cửa sổ PiP.

### Chi tiết các phương thức cốt lõi

#### 1. Khởi tạo & Cấu hình WebView (`setupWebView`)
- **Tắt Layer Type phần cứng riêng cho View:** `webView.setLayerType(View.LAYER_TYPE_NONE, null)`
  > *Lý do kỹ thuật:* Cửa sổ Activity đã được bật `android:hardwareAccelerated="true"`. Nếu ép thêm `LAYER_TYPE_HARDWARE` trên View, WebView sẽ vẽ vào một offscreen buffer riêng; khi cửa sổ thu nhỏ thành PiP, Android có thể không cập nhật buffer này dẫn đến lỗi **màn hình đen nhưng vẫn có tiếng**.
- **Cấu hình WebSettings:**
  - `javaScriptEnabled = true`, `domStorageEnabled = true`, `databaseEnabled = true`.
  - `allowFileAccess = false`, `allowContentAccess = false`: Đóng quyền truy cập file cục bộ để đảm bảo an toàn tuyệt đối.
  - `mediaPlaybackRequiresUserGesture = false`: Cho phép video tự động phát lại khi chuyển sang PiP hoặc background.
  - `offscreenPreRaster = false`: Không vẽ trước nội dung ngoài màn hình, tiết kiệm 30-50MB RAM.
  - `userAgentString`: Loại bỏ chuỗi `; wv` khỏi User Agent để YouTube nhận diện trình duyệt là Chrome Mobile chuẩn thay vì WebView của app bên thứ ba.

#### 2. Xử lý Toàn màn hình (`WebChromeClient`)
- `onShowCustomView(view, callback)`: Khi người dùng bấm nút Fullscreen trên player:
  1. Ẩn `webView`, đưa `view` vào `customViewContainer`.
  2. Bật cờ `WindowManager.LayoutParams.FLAG_FULLSCREEN`.
  3. Đổi hướng màn hình `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE`.
- `onHideCustomView()`: Thu hồi view, trả lại hướng `SCREEN_ORIENTATION_PORTRAIT`.

#### 3. Bộ điều phối phím Quay lại (`setupBackPressedHandler`)
Sử dụng `OnBackPressedCallback` hiện đại:
1. Nếu đang Fullscreen $\rightarrow$ Thoát Fullscreen (`onHideCustomView()`).
2. Nếu `webView.canGoBack()` $\rightarrow$ Quay lại trang trước trong lịch sử (`webView.goBack()`).
3. Nếu ở trang chủ $\rightarrow$ Thoát Activity theo mặc định.

#### 4. Quản lý Picture-in-Picture (PiP)
- `clampedPipAspectRatio(): Rational`:
  > *Lưu ý quan trọng:* Android quy định tỉ lệ PiP bắt buộc nằm trong khoảng **1:2.39 đến 2.39:1**. Nếu video có tỉ lệ vượt quá khoảng này, `setAspectRatio()` sẽ ném ngoại lệ `IllegalArgumentException` gây văng app. Hàm này kẹp giá trị an toàn trong khoảng `[100/239 .. 239/100]`.
- `buildPipParams(): PictureInPictureParams?`:
  - Khởi tạo `RemoteAction` với icon `ic_media_pause` hoặc `ic_media_play`.
  - Trên Android 12+ (API 31+): Kích hoạt `setAutoEnterEnabled(isVideoPlaying || isFullscreen)` và `setSeamlessResizeEnabled(false)` (tránh làm nhòe khung hình video khi co giãn cửa sổ).
- `runPipCommand(method: String, onResult)`:
  - Chèn mã `pip_controller.js` (cơ chế idempotent) và thực thi `window.__bravePip[method]()`.
  - Hoạt động độc lập, không phụ thuộc vào `youtube_defuser.js`.
- `onUserLeaveHint()`: Khi người dùng nhấn nút Home hoặc vuốt về màn hình chính, lập tức gọi `enterPipLayout()` và `enterPictureInPictureMode()`.
- `onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)`:
  - Khi vào PiP: Gọi `enterPipLayout()`, đồng thời dùng `postDelayed` gọi lại sau 150ms và 450ms để bắt kịp kích thước cửa sổ sau khi hiệu ứng thu nhỏ hoàn tất.
  - Khi thoát PiP: Gọi `exitPipLayout()`.

#### 5. Cầu nối JavaScript `MediaBridge`
Được đăng ký qua `webView.addJavascriptInterface(MediaBridge(), "AndroidBridge")`:
- `onVideoPlaying(title, channel)`: Nhận thông báo video đang phát, cập nhật tiêu đề bài hát và làm mới nút PiP sang trạng thái "Tạm dừng".
- `onVideoPaused()`: Nhận thông báo video dừng, cập nhật nút PiP sang trạng thái "Phát".
- `onVideoSize(width, height)`: Nhận độ phân giải thực của video, tính lại `videoAspectRatio` và cập nhật thông số PiP.

---

## 2. `KeepAliveWebView.kt`

- **Vị trí file:** `app/src/main/java/com/brave/youtubelite/view/KeepAliveWebView.kt`
- **Kế thừa:** `android.webkit.WebView`
- **Mục đích:** Ngăn Chromium Engine đình chỉ xử lý âm thanh khi Activity bị ẩn hoặc khóa màn hình.
- **Cơ chế hoạt động:**
  ```kotlin
  override fun dispatchWindowVisibilityChanged(visibility: Int) {
      super.dispatchWindowVisibilityChanged(View.VISIBLE)
  }

  override fun onWindowVisibilityChanged(visibility: Int) {
      super.onWindowVisibilityChanged(View.VISIBLE)
  }
  ```
  Khi hệ điều hành gửi thông báo cửa sổ bị `View.GONE` hoặc `View.INVISIBLE`, class này can thiệp và luôn báo cho engine Chromium bên dưới là `View.VISIBLE`. Nhờ đó, luồng render audio và các timer JavaScript tiếp tục chạy liên tục.

---

## 3. `AdBlockEngine.kt`

- **Vị trí file:** `app/src/main/java/com/brave/youtubelite/network/AdBlockEngine.kt`
- **Dạng lớp:** `object AdBlockEngine` (Singleton)
- **Mục đích:** Lọc URL mạng với hiệu năng tối đa trước khi gửi request đi.
- **Cấu trúc dữ liệu:**
  - `blockedDomains: HashSet<String>`: Chứa danh sách các domain quảng cáo nạp từ `filters/blocked_domains.txt`.
  - `blockedPathPatterns: List<String>`: Chứa các pattern đường dẫn telemetry (`/api/stats/ads`, `/pagead/`, `/youtubei/v1/log_event`).
- **Phương thức:**
  - `init(context: Context)`: Đọc file assets bằng `BufferedReader`, loại bỏ comment `#` và dòng trống, nạp vào `HashSet`.
  - `isBlocked(url: String): Boolean`:
    1. Kiểm tra đối chiếu chính xác domain trong `HashSet` $\rightarrow O(1)$.
    2. Kiểm tra đối chiếu subdomain (`host.endsWith(".$blocked")`).
    3. Kiểm tra các path telemetry ngốn băng thông.

---

## 4. `AppWebViewClient.kt`

- **Vị trí file:** `app/src/main/java/com/brave/youtubelite/network/AppWebViewClient.kt`
- **Kế thừa:** `android.webkit.WebViewClient`
- **Nhiệm vụ:**
  1. Chặn request mạng bằng `AdBlockEngine.isBlocked(url)` tại `shouldInterceptRequest()`.
  2. Cài đặt các Scriptlet vào thời điểm sớm nhất thông qua `installDocumentStartScripts(webView)`.
  3. Tiêm lại Scriptlet khi điều hướng trang nội bộ SPA (`doUpdateVisitedHistory()`).

### Kỹ thuật Document-Start Injection
```kotlin
if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
    val origins = setOf("*")
    WebViewCompat.addDocumentStartJavaScript(webView, pipControllerCode, origins)
    WebViewCompat.addDocumentStartJavaScript(webView, scriptletCode, origins)
    WebViewCompat.addDocumentStartJavaScript(webView, injectCssJs, origins)
}
```
Nhờ sử dụng `WebViewCompat.addDocumentStartJavaScript` của thư viện `androidx.webkit:1.10.0`, toàn bộ logic chặn quảng cáo InnerTube và cô lập PiP luôn sẵn sàng trước khi YouTube kịp tải và thực thi file HTML/JS đầu tiên.

---

## 5. `MediaService.kt`

- **Vị trí file:** `app/src/main/java/com/brave/youtubelite/service/MediaService.kt`
- **Kế thừa:** `android.app.Service`
- **Mục đích:** Duy trì luồng âm thanh và giữ CPU không bị rơi vào chế độ ngủ sâu (Deep Sleep) khi tắt màn hình điện thoại.
- **Cấu hình AndroidManifest:**
  - `android:foregroundServiceType="mediaPlayback"`
  - Quyền yêu cầu: `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`.
- **Hoạt động:**
  - `ACTION_START`:
    1. Giữ `PowerManager.PARTIAL_WAKE_LOCK` (giới hạn thời gian tối đa 2 giờ để bảo vệ pin).
    2. Tạo Notification Channel `youtube_playback_channel` với mức ưu tiên `IMPORTANCE_LOW` (không phát tiếng ting ting khi hiện).
    3. Gọi `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`.
  - `ACTION_STOP`:
    1. Giải phóng WakeLock an toàn (`wakeLock?.release()`).
    2. Dừng service và gỡ bỏ Notification (`stopForeground(STOP_FOREGROUND_REMOVE)` & `stopSelf()`).
