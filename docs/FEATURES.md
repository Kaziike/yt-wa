# Danh Sách Tính Năng & Trạng Thái Triển Khai (Features)

Tài liệu này tổng hợp toàn bộ các tính năng đã được triển khai trong ứng dụng **LiteTube**, phân tích chi tiết dữ liệu vào/ra, file phụ trách và các lưu ý kỹ thuật khi bảo trì.

---

## 1. Ma Trận Tính Năng Tổng Quát

| Tên Tính Năng | Mô Tả Ngắn Gọn | Trạng Thái | File Liên Quan |
| :--- | :--- | :--- | :--- |
| **1. In-App MiniPlayer 2 Tầng** | Lớp xem video trượt nổi độc lập, vuốt xuống thu nhỏ ở đáy, giữ nguyên trang chủ bên dưới. | **Đã triển khai** | `MainActivity.kt`<br>`AppWebViewClient.kt`<br>`activity_main.xml` |
| **2. Bắt chặn SPA Watch Router** | Bắt click Capture Phase và hook `pushState` để mở video trên tầng nổi mà không reload trang chủ. | **Đã triển khai** | `AppWebViewClient.kt`<br>`MainActivity.kt` |
| **3. Chặn quảng cáo tầng mạng (Native)** | Chặn các request HTTP/HTTPS tới các domain quảng cáo và telemetry từ tầng native. | **Đã triển khai** | `AdBlockEngine.kt`<br>`AppWebViewClient.kt`<br>`filters/blocked_domains.txt` |
| **4. Gọt sạch API InnerTube** | Chặn các payload quảng cáo lồng trong phản hồi JSON của YouTube Player API. | **Đã triển khai** | `youtube_defuser.js`<br>`AppWebViewClient.kt` |
| **5. Khắc chế SSAI (Quảng cáo ghép luồng)** | Tự phát hiện quảng cáo nhúng trong video stream, tua nhanh 16x, ngắt tiếng và bấm skip tự động chỉ trong 0.2s. | **Đã triển khai** | `youtube_defuser.js` |
| **6. Điều khiển MediaSession toàn diện** | Điều khiển Play/Pause/Next/Prev/Seek trên Lockscreen, Notification, Bluetooth và Smartwatch. | **Đã triển khai** | `MediaService.kt`<br>`MainActivity.kt` |
| **7. Picture-in-Picture (PiP) linh hoạt** | Cửa sổ nổi tự động co giãn theo tỉ lệ thật của video kèm nút điều khiển Play/Pause native. | **Đã triển khai** | `MainActivity.kt`<br>`pip_controller.js`<br>`activity_main.xml` |
| **8. Bảo vệ phát PiP & Micro-Seek** | Ngăn auto-pause khi chuyển đổi PiP, đánh thức pipeline re-render của YouTube không bị kẹt nút. | **Đã triển khai** | `pip_controller.js`<br>`youtube_defuser.js`<br>`MainActivity.kt` |
| **9. Phát âm thanh khi tắt màn hình** | Giữ âm thanh tiếp tục phát thông qua Foreground Service và WakeLock tối đa 2 giờ. | **Đã triển khai** | `MediaService.kt`<br>`MainActivity.kt`<br>`AndroidManifest.xml` |
| **10. Kéo để tải lại (Swipe-to-Refresh)** | Kéo từ trên xuống để làm mới trang chủ mượt mà theo phong cách Dark Mode. | **Đã triển khai** | `MainActivity.kt`<br>`activity_main.xml` |
| **11. Menu Cài đặt Native trong `select_site`** | Tích hợp mục "Cài đặt LiteTube" vào trang `select_site` mở BottomSheet tùy biến. | **Đã triển khai** | `MainActivity.kt`<br>`AppSettings.kt`<br>`AppWebViewClient.kt`<br>`dialog_settings.xml` |
| **12. Bộ nhớ đệm tài nguyên tĩnh (Static Cache)** | Lưu trữ cục bộ các script, font, icon tĩnh giúp tăng tốc mở trang và tiết kiệm 4G/5G. | **Đã triển khai** | `WebResourceCacheManager.kt`<br>`AppWebViewClient.kt` |
| **13. Cosmetic Filtering (Làm sạch DOM)** | Ẩn banner quảng cáo, gợi ý cài đặt ứng dụng YouTube và hộp thoại làm phiền. | **Đã triển khai** | `cosmetic_rules.css`<br>`AppWebViewClient.kt` |
| **14. Tự động xoay toàn màn hình** | Tự động chuyển orientation sang LANDSCAPE khi bấm nút Fullscreen của video web. | **Đã triển khai** | `MainActivity.kt`<br>`activity_main.xml` |
| **15. Tối ưu bộ nhớ & Chống đen hình** | Tắt `offscreenPreRaster` và đặt `setLayerType(LAYER_TYPE_NONE)` để tiết kiệm RAM. | **Đã triển khai** | `MainActivity.kt` |

---

## 2. Chi Tiết Từng Tính Năng Mới

### Tính năng 1: In-App MiniPlayer 2 Tầng (Sliding Sheet)
- **Mục đích:** Mang lại trải nghiệm vừa xem video vừa duyệt trang chủ y hệt ứng dụng YouTube Native, khắc phục nhược điểm mất trang chủ của trình duyệt web thông thường.
- **Cấu trúc:**
  - `swipeRefreshLayout` chứa `webView` (Browse View): Duyệt trang chủ, tab đăng ký, tìm kiếm.
  - `playerPanel` chứa `playerWebView` (Player Layer): Chuyên dụng phát video.
- **Trạng thái (PanelState):**
  - `EXPANDED`: `playerPanel` bung toàn màn hình (`MATCH_PARENT`), phát video chất lượng cao.
  - `COLLAPSED`: Trượt xuống đáy màn hình và thu gọn layout chỉ còn đúng **64dp**. Video thu nhỏ tỉ lệ 16:9 ở góc trái (104dp x 58dp); bên phải là thông tin tiêu đề, tên kênh, nút Play/Pause và nút Đóng [X].
  - `HIDDEN`: Ẩn hoàn toàn `playerPanel`, nạp `about:blank` vào `playerWebView` để giải phóng bộ nhớ.
- **Giải phóng touch:** Khi ở `COLLAPSED`, kích thước `playerPanel` co về đúng 64dp ở đáy (`Gravity.BOTTOM`), vùng màn hình từ 0 đến H-64dp hoàn toàn tự do cho `webView` trang chủ nhận cảm ứng.

---

### Tính năng 2: Bộ Chặn SPA Watch Router (`browseWatchInterceptorJs`)
- **Mục đích:** Chặn router nội bộ của YouTube Mobile Web (`m.youtube.com`) tự ý chuyển trang trên Browse View khi người dùng bấm vào video.
- **Cơ chế hoạt động:**
  1. **Capture Phase Click Listener:** Đăng ký listener trên `document` với `useCapture = true` để đón đầu mọi event trước khi YouTube xử lý. Kiểm tra `target.closest('a')` hoặc thẻ cha của video container.
  2. **Hook History API:** Ghi đè `history.pushState` và `history.replaceState`.
  3. **Lắng nghe sự kiện Polymer:** Bắt sự kiện `yt-navigate`.
  4. Nếu URL chứa `/watch` và **không chứa `/shorts/`**:
     - Gọi `e.preventDefault()`, `e.stopImmediatePropagation()`.
     - Gọi Native: `window.AndroidBridge.openWatchLayer(fullUrl)`.
     - Ngăn Browse View thay đổi URL, giữ nguyên feed trang chủ 100%!

---

### Tính năng 6: Hệ Thống Điều Khiển Đa Phương Tiện Toàn Diện (`MediaSession`)
- **Mục đích:** Đồng bộ trạng thái phát và cho phép điều khiển video từ Màn hình khóa, Thanh thông báo, Tai nghe Bluetooth và Đồng hồ thông minh.
- **Xử lý:**
  - `MediaService` khởi tạo `MediaSessionCompat`.
  - Thiết lập `PlaybackStateCompat` với các hành động: `ACTION_PLAY`, `ACTION_PAUSE`, `ACTION_SKIP_TO_NEXT`, `ACTION_SKIP_TO_PREVIOUS`, `ACTION_SEEK_TO`.
  - Cập nhật `MediaMetadataCompat`: Tên bài hát (`METADATA_KEY_TITLE`), Tên kênh (`METADATA_KEY_ARTIST`), Thời lượng (`METADATA_KEY_DURATION`).
  - Sử dụng `NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken)` hiển thị giao diện media notification chuẩn Android 13/14.

---

### Tính năng 8: Bảo Vệ Phát PiP & Micro-Seek Re-render Kick
- **Mục đích:** Triệt tiêu hoàn toàn lỗi kẹt nút Play/Pause hoặc video bị ngắt tiếng khi chuyển đổi qua lại giữa App và cửa sổ PiP.
- **Cơ chế:**
  - Cờ thời gian `__braveProtectPlayUntil`: Khi vào hoặc thoát PiP, cấp quyền bảo vệ 2.5 - 3.5 giây, vô hiệu hóa mọi lệnh tự động pause từ kiểm tra kích thước nhỏ (`PLAYER_VIEW_TOO_SMALL`) của YouTube.
  - Hàm `triggerMicroSeek`: Gán lại `video.currentTime = video.currentTime` kết hợp bắn sự kiện `seeking`, `seeked`, `timeupdate` và gọi `player.wakeUpControls()`, `player.updateDimensions()`. Điều này đánh thức bộ render của YouTube Player ngay lập tức khi kích thước màn hình thay đổi.

---

### Tính năng 11: Menu Cài Đặt Native Trong Trang `select_site`
- **Mục đích:** Cung cấp khu vực cấu hình tiện lợi mà không làm thay đổi thanh công cụ mặc định của YouTube.
- **Cơ chế:**
  - Scriptlet `selectSiteSettingsScriptletJs` lắng nghe URL `https://m.youtube.com/select_site` (trang Cài đặt của YouTube Mobile).
  - Tự động chèn một mục danh sách thanh lịch "Cài đặt LiteTube" có icon bánh răng vào đầu danh sách cài đặt của trang.
  - Khi người dùng bấm vào, gọi `window.AndroidBridge.openSettings()`.
  - Native `MainActivity` hiển thị một `BottomSheetDialog` (`dialog_settings.xml`) với các Switch Material để bật/tắt tính năng và nút xóa bộ nhớ đệm (Clear Cache).
