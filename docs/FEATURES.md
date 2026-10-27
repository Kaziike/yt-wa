# Danh Sách Tính Năng & Trạng Thái Triển Khai (Features)

Tài liệu này tổng hợp toàn bộ các tính năng đã được triển khai trong ứng dụng **YouTube Lite**, phân tích chi tiết dữ liệu vào/ra, file phụ trách và các lưu ý kỹ thuật khi bảo trì.

---

## 1. Ma Trận Tính Năng Tổng Quát

| Tên Tính Năng | Mô Tả Ngắn Gọn | Trạng Thái | File Liên Quan |
| :--- | :--- | :--- | :--- |
| **1. Chặn quảng cáo tầng mạng (Native Filter)** | Chặn các request HTTP/HTTPS tới các domain quảng cáo và telemetry từ tầng native. | **Đã triển khai** | `AdBlockEngine.kt`<br>`AppWebViewClient.kt`<br>`filters/blocked_domains.txt` |
| **2. Gọt sạch API InnerTube** | Chặn các payload quảng cáo lồng trong phản hồi JSON của YouTube Player API. | **Đã triển khai** | `youtube_defuser.js`<br>`AppWebViewClient.kt` |
| **3. Khắc chế SSAI (Quảng cáo ghép luồng)** | Tự phát hiện quảng cáo nhúng trong video stream, tua nhanh 16x, ngắt tiếng và bấm skip tự động. | **Đã triển khai** | `youtube_defuser.js` |
| **4. Chống dừng video ngầm** | Bẻ khóa Page Visibility API và chặn các lệnh `pause()` tự động phát sinh khi ẩn màn hình. | **Đã triển khai** | `youtube_defuser.js`<br>`pip_controller.js`<br>`KeepAliveWebView.kt` |
| **5. Cosmetic Filtering (Làm sạch DOM)** | Ẩn banner quảng cáo, gợi ý cài đặt ứng dụng YouTube và hộp thoại làm phiền. | **Đã triển khai** | `cosmetic_rules.css`<br>`AppWebViewClient.kt` |
| **6. Picture-in-Picture (PiP) tự động & thủ công** | Chuyển video thành cửa sổ nổi khi vuốt Home hoặc thu nhỏ màn hình. | **Đã triển khai** | `MainActivity.kt`<br>`pip_controller.js`<br>`activity_main.xml` |
| **7. Nút điều khiển Play/Pause trên PiP** | Nút RemoteAction trên cửa sổ PiP điều khiển phát/dừng chính xác trên Android 13/14. | **Đã triển khai** | `MainActivity.kt`<br>`pip_controller.js` |
| **8. Khớp tỉ lệ PiP linh hoạt (Dynamic Aspect Ratio)** | Cửa sổ PiP tự động co giãn theo tỉ lệ thật của video (16:9, 4:3, dọc 9:16). | **Đã triển khai** | `MainActivity.kt`<br>`youtube_defuser.js`<br>`pip_controller.js` |
| **9. Phát âm thanh khi tắt màn hình** | Giữ âm thanh tiếp tục phát thông qua Foreground Service và WakeLock. | **Đã triển khai** | `MediaService.kt`<br>`MainActivity.kt`<br>`AndroidManifest.xml` |
| **10. Xoay ngang toàn màn hình tự động** | Tự động chuyển orientation sang LANDSCAPE khi bấm nút Fullscreen của video web. | **Đã triển khai** | `MainActivity.kt`<br>`activity_main.xml` |
| **11. Bắt liên kết YouTube (Deep Links)** | Mở trực tiếp các URL YouTube (`m.youtube.com`, `youtu.be`) trong ứng dụng. | **Đã triển khai** | `AndroidManifest.xml`<br>`MainActivity.kt` |
| **12. Tối ưu bộ nhớ & Chống đen hình** | Tắt `offscreenPreRaster` và đặt `setLayerType(LAYER_TYPE_NONE)` để tiết kiệm RAM và hiển thị mượt mà. | **Đã triển khai** | `MainActivity.kt` |

---

## 2. Chi Tiết Từng Tính Năng

### Tính năng 1: Chặn quảng cáo tầng mạng (Native Filter)
- **Mục đích:** Ngăn các request quảng cáo rời khỏi thiết bị, tiết kiệm băng thông 4G/5G và giảm tải xử lý cho CPU.
- **Dữ liệu vào:** Chuỗi URL từ `WebResourceRequest.url.toString()`.
- **Xử lý:**
  1. `AdBlockEngine.isBlocked(url)` bóc tách host và path.
  2. Tra cứu $O(1)$ trong `HashSet<String>`.
  3. Kiểm tra đuôi subdomain và các path telemetry đặc thù (`/api/stats/ads`, `/pagead/`, `/youtubei/v1/log_event`).
- **Dữ liệu ra:** Trả về `WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))` (chặn) hoặc `super.shouldInterceptRequest()` (cho qua).
- **Lưu ý bảo trì:** Khi bổ sung domain quảng cáo mới, chỉ cần thêm vào file `app/src/main/assets/filters/blocked_domains.txt`.

---

### Tính năng 2: Gọt sạch API InnerTube
- **Mục đích:** Loại bỏ thông tin quảng cáo ngay khi YouTube tải dữ liệu video, ngăn trình phát Web kích hoạt chế độ chạy quảng cáo.
- **Xử lý:**
  - Tiêm scriptlet tại thời điểm `DOCUMENT_START` bằng `WebViewCompat.addDocumentStartJavaScript`.
  - Override hàm `JSON.parse` của trình duyệt.
  - Proxy `window.ytInitialPlayerResponse` bằng `Object.defineProperty`.
  - Can thiệp `window.fetch` đối với endpoint `/youtubei/v1/player`.
  - Hàm `sanitizePlayerObj` xóa sạch các khóa: `adPlacements`, `adSlots`, `playerAds`, `adBreakHeartbeatParams`.
- **Dữ liệu ra:** JSON sạch được trả về cho trình phát YouTube.

---

### Tính năng 3: Khắc chế SSAI (Server-Side Ad Insertion)
- **Mục đích:** Xử lý các quảng cáo được chèn trực tiếp vào luồng stream video mà bộ lọc mạng không thể chặn theo URL.
- **Cơ chế kích hoạt:** Vòng lặp `setInterval(..., 150ms)` trong `youtube_defuser.js` kiểm tra sự tồn tại của selector `.ad-showing, .ad-interrupting, .video-ads, [class*="ytp-ad-player-overlay"]`.
- **Hành động:**
  1. Đặt `video.muted = true` (lưu lại âm lượng gốc).
  2. Đặt `video.playbackRate = 16.0`.
  3. Gán `video.currentTime = video.duration`.
  4. Mô phỏng sự kiện bấm chuột (`btn.click()`) trên tất cả các nút skip (`.ytp-ad-skip-button`).
- **Khôi phục:** Khi hết quảng cáo, trả lại `playbackRate = 1.0`, `muted = false` và khôi phục âm lượng gốc.

---

### Tính năng 4: Chống tự dừng video ngầm & Giả lập Page Visibility
- **Mục đích:** YouTube thường dừng video khi tab bị ẩn hoặc khi kích thước khung nhìn quá nhỏ (lỗi `PLAYER_VIEW_TOO_SMALL`).
- **Xử lý:**
  - Chặn lan truyền các sự kiện `visibilitychange`, `webkitvisibilitychange`, `pagehide` bằng `e.stopImmediatePropagation()`.
  - Ghi đè `document.hidden = false` và `document.visibilityState = 'visible'`.
  - Ghi đè `HTMLMediaElement.prototype.pause`: Chỉ cho phép lệnh `pause` thực thi nếu có cờ `__isUserExplicitPause` (người dùng bấm nút tạm dừng) hoặc cờ thời gian `__braveAllowPauseUntil` (bấm tạm dừng từ PiP Native). Mọi lệnh `pause` do YouTube tự phát sinh đều bị bỏ qua.

---

### Tính năng 5: Picture-in-Picture (PiP) và Bộ điều khiển `pip_controller.js`
- **Mục đích:** Đảm bảo khi vào PiP, video hiển thị trọn vẹn 100% khung hình, không bị các thành phần web khác đè lên.
- **Xử lý:**
  - Gán `data-brave-pip-mode="on"` vào thẻ `<html>`.
  - Gán `data-brave-pip-role="video"` vào thẻ `<video>` chính.
  - Gán `data-brave-pip-role="ancestor"` vào chuỗi thẻ cha từ video lên `<body>`.
  - Áp dụng luật CSS: Mọi phần tử `:not([data-brave-pip-role])` đều bị `display: none !important`.
  - Thẻ cha được đặt `position: fixed !important; background: transparent !important`.
  - Video phủ kín `100vw x 100vh; object-fit: contain !important`.
- **Cơ chế thoát:** Gọi `pip_controller.js exit()`, gỡ bỏ class/attribute và phát sự kiện `resize` sau các nhịp 0ms, 150ms, 350ms để YouTube tính lại khung player.

---

### Tính năng 6: Nút Play/Pause trên PiP & Android 14 SystemUI Exported Receiver
- **Mục đích:** Điều khiển tạm dừng và phát tiếp trực tiếp trên cửa sổ PiP của Android.
- **Xử lý:**
  - Tạo `RemoteAction` với `PendingIntent.getBroadcast` trỏ đến `ACTION_PIP_PLAY` hoặc `ACTION_PIP_PAUSE`.
  - Đăng ký `BroadcastReceiver` với `ContextCompat.RECEIVER_EXPORTED` (bắt buộc cho Android 13/14 vì sự kiện phát sinh từ SystemUI).
  - Khi nhận intent:
    - Bấm Play $\rightarrow$ Gửi `runPipCommand("play")` $\rightarrow$ Gọi native `HTMLMediaElement.prototype.play()`.
    - Bấm Pause $\rightarrow$ Gửi `runPipCommand("pause")` $\rightarrow$ Mở khóa cờ `allowPause(2500)` $\rightarrow$ Gọi native `HTMLMediaElement.prototype.pause()`.

---

### Tính năng 7: Phát âm thanh trong nền qua Foreground Service (`MediaService`)
- **Mục đích:** Người dùng có thể nghe podcast/nhạc khi tắt màn hình điện thoại mà không lo bị hệ điều hành tắt ứng dụng.
- **Xử lý:**
  - Trong `MainActivity.onStop()`: Nếu `isVideoPlaying == true`, kích hoạt `startMediaService()`.
  - `MediaService` khởi chạy dưới dạng `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
  - Kích hoạt `PowerManager.PARTIAL_WAKE_LOCK` (giữ CPU hoạt động tối đa 2 giờ).
  - Khi người dùng mở lại app (`MainActivity.onStart()`), `MediaService` tự động dừng (`stopMediaService()`) để tối ưu pin.
