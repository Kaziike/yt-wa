# Giao Diện Giao Tiếp & Lưu Trữ (API & Storage)

Tài liệu này mô tả các giao thức giao tiếp liên tiến trình (IPC), giao diện JavaScript $\leftrightarrow$ Native, Broadcast Intents, MediaSession Actions và cơ chế lưu trữ dữ liệu của ứng dụng **LiteTube**.

---

## 1. Giao Tiếp Hai Chiều JavaScript $\leftrightarrow$ Kotlin Native

Hệ thống sử dụng cơ chế lai kết hợp giữa **JavascriptInterface** (chiều từ Web lên Android) và **`evaluateJavascript`** (chiều từ Android xuống Web).

### A. Chiều Web $\rightarrow$ Android: `AndroidBridge`
Được đăng ký trong `MainActivity.kt` cho cả 2 cá thể WebView (`webView` và `playerWebView`) thông qua:
```kotlin
view.addJavascriptInterface(MediaBridge(), "AndroidBridge")
```

| Phương Thức | Tham Số | Mục Đích | Luồng Xử Lý Phía Native |
| :--- | :--- | :--- | :--- |
| `openWatchLayer(url)` | `url: String` | Browse View thông báo người dùng bấm vào video hoặc pushState URL `/watch`. | Gọi `runOnUiThread { openVideoPlayer(url) }`, bung `playerPanel` lên toàn màn hình và nạp video vào `playerWebView`. |
| `openSettings()` | *(Không có)* | Web thông báo người dùng bấm vào mục "Cài đặt LiteTube" trong trang `select_site`. | Gọi `runOnUiThread { showSettingsDialog() }`, mở BottomSheet Cài đặt Native. |
| `onVideoPlaying(title, channel)` | `title: String`<br>`channel: String` | Web thông báo video bắt đầu phát. | Chuyển sang luồng UI, cập nhật `isVideoPlaying = true`, tiêu đề, tên kênh trên MiniPlayer Bar, PiP và Notification. |
| `onVideoPaused()` | *(Không có)* | Web thông báo video đã dừng. | Chuyển sang luồng UI, cập nhật `isVideoPlaying = false`, đổi icon sang Play trên MiniPlayer Bar, PiP và Notification. |
| `onMediaStateChanged(...)` | `playing: Boolean`<br>`title: String`<br>`channel: String`<br>`posMs: Long`<br>`durMs: Long` | Web đồng bộ toàn diện trạng thái phát, thời gian hiện tại và tổng thời lượng. | Cập nhật `MediaSessionCompat` (tiến trình thanh tua LockScreen/Notification) và `MediaService`. |
| `onVideoSize(width, height)` | `width: Int`<br>`height: Int` | Web gửi kích thước thực tế của thẻ video (`videoWidth`, `videoHeight`). | Tính `Rational(width, height)`, cập nhật lại tỉ lệ co giãn khung hình của `PictureInPictureParams`. |

---

### B. Chiều Android $\rightarrow$ Web: `runPipCommand`
Được định nghĩa trong `MainActivity.kt` để gửi lệnh tới đối tượng `window.__bravePip` trong `pip_controller.js`:
```kotlin
private fun runPipCommand(method: String, onResult: ((String?) -> Unit)? = null)
```

| Tên Lệnh (`method`) | Đối Tượng Thực Thi Trên Web | Kết Quả Trả Về |
| :--- | :--- | :--- |
| `"enter"` | `window.__bravePip.enter()` | Kích hoạt CSS cô lập video, gán `data-brave-pip-mode="on"`, trả về `'pip:on'`. |
| `"exit"` | `window.__bravePip.exit()` | Gỡ bỏ thuộc tính cô lập, kích hoạt Micro-Seek kick và phát chuỗi resize event, trả về `'pip:off'`. |
| `"play"` | `window.__bravePip.play()` | Mở khóa bảo vệ `__braveProtectPlayUntil` và gọi `HTMLMediaElement.prototype.play()`, trả về `'play'`. |
| `"pause"` | `window.__bravePip.pause()` | Gọi `HTMLMediaElement.prototype.pause()` cho tất cả thẻ video, trả về `'pause'`. |
| `"toggle"` | `window.__bravePip.toggle()` | Đảo ngược trạng thái phát / tạm dừng của video chính. |
| `"next"` | `window.__bravePip.next()` | Gọi `player.nextVideo()` hoặc mô phỏng click nút Next trên giao diện YouTube. |
| `"previous"` | `window.__bravePip.previous()` | Gọi `player.previousVideo()` hoặc mô phỏng click nút Previous. |
| `"seekTo(ms)"` | `window.__bravePip.seekTo(ms)` | Tua video đến vị trí mili-giây tương ứng thông qua `video.currentTime = ms / 1000`. |
| `"applySettings(json)"` | `window.__bravePip.applySettings(json)` | Cập nhật cấu hình Adblock, Background Play, Auto PiP thời gian thực xuống tầng JS. |

---

## 2. Hệ Thống Broadcast Intents & Service Actions

### A. PiP Actions
- `com.brave.LiteTube.PIP_PLAY`:
  - **Mã yêu cầu (Request Code):** `1001`
  - **Icon:** `android.R.drawable.ic_media_play`
- `com.brave.LiteTube.PIP_PAUSE`:
  - **Mã yêu cầu (Request Code):** `1002`
  - **Icon:** `android.R.drawable.ic_media_pause`

### B. MediaService Actions
Dùng để quản lý Foreground Service và điều khiển `MediaSessionCompat`:

| Tên Action | Mục Đích |
| :--- | :--- |
| `MediaService.ACTION_START` | Bắt đầu Foreground Service và xin cấp CPU `WakeLock`. |
| `MediaService.ACTION_UPDATE` | Cập nhật tiêu đề, tên kênh, trạng thái phát hoặc thanh tiến trình trên Notification. |
| `MediaService.ACTION_STOP` | Dừng Foreground Service, thu hồi `WakeLock` và ẩn Notification. |
| `MediaService.ACTION_MEDIA_PLAY` | Lệnh phát từ nút trên LockScreen/Notification/Tai nghe. |
| `MediaService.ACTION_MEDIA_PAUSE`| Lệnh tạm dừng từ LockScreen/Notification/Tai nghe. |
| `MediaService.ACTION_MEDIA_NEXT` | Lệnh chuyển bài tiếp theo từ LockScreen/Tai nghe. |
| `MediaService.ACTION_MEDIA_PREV` | Lệnh chuyển bài trước đó từ LockScreen/Tai nghe. |
| `MediaService.ACTION_MEDIA_SEEK` | Lệnh tua video từ thanh trượt LockScreen (`EXTRA_SEEK_POS_MS`). |

---

## 3. Cơ Chế Lưu Trữ Dữ Liệu (Storage)

### A. Cấu Hình Ứng Dụng (`SharedPreferences`)
- **Tên file:** `youtube_lite_settings.xml`.
- **Các khóa lưu trữ:**
  - `adblock_enabled` (Boolean - Mặc định: `true`).
  - `background_play_enabled` (Boolean - Mặc định: `true`).
  - `auto_pip_enabled` (Boolean - Mặc định: `true`).
  - `preload_cache_enabled` (Boolean - Mặc định: `true`).

### B. Bộ Nhớ Đệm Tài Nguyên Tĩnh (Disk Cache)
- **Đường dẫn thư mục:** `context.cacheDir/web_static/`.
- **Cấu trúc lưu trữ:** Mỗi file tĩnh được lưu bằng tên mã băm MD5 của URL (`MD5(requestUrl)`).
- **Loại tài nguyên:** JavaScript bundles, stylesheet CSS, Web Fonts (`.woff2`), hình ảnh biểu tượng SVG.
- **Quản lý dung lượng:**
  - `WebResourceCacheManager.getCacheSizeBytes(context)`: Tính tổng dung lượng thư mục cache.
  - `WebResourceCacheManager.clearCache(context)`: Xóa sạch toàn bộ file trong thư mục để giải phóng bộ nhớ.
