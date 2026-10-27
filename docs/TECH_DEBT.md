# Báo Cáo Nợ Kỹ Thuật & Đề Xuất Nâng Cấp (Tech Debt)

Tài liệu này ghi nhận các vấn đề nợ kỹ thuật (Technical Debt), các điểm rủi ro tiềm ẩn và đề xuất lộ trình cải tiến cho dự án **YouTube Lite**.

---

## 1. Hiện Trạng & Rủi Ro Kỹ Thuật Đã Xác Minh

### A. Tình trạng thư mục `adblock-rust/`
- **Hiện trạng:** Thư mục `adblock-rust/` chứa toàn bộ mã nguồn của engine lọc quảng cáo Brave viết bằng Rust. Tuy nhiên, trong cấu hình `settings.gradle.kts` và `app/build.gradle.kts`, không có bất kỳ cấu hình NDK, CMake hoặc Cargo nào liên kết thư mục này vào quá trình biên dịch.
- **Tác động:** Tốn dung lượng ổ đĩa của repository (~3MB mã nguồn, chưa kể các file bench/test) nhưng không đóng góp trực tiếp vào file APK thực tế.
- **Đề xuất:**
  - *Ngắn hạn:* Đưa vào file `.gitignore` hoặc đóng gói thành thư viện phụ trợ bên ngoài nếu không dùng đến.
  - *Dài hạn:* Nếu dự án muốn hỗ trợ các bộ luật phức tạp uBlock Origin / AdGuard dạng regex nâng cao, có thể viết tầng JNI / UniFFI để tích hợp engine Rust này vào Android Native.

### B. Mức độ tập trung logic trong `MainActivity.kt` (God Activity Pattern)
- **Hiện trạng:** `MainActivity.kt` hiện có quy mô ~478 dòng mã và đảm nhiệm quá nhiều trách nhiệm cùng lúc:
  1. Khởi tạo và quản lý WebView.
  2. Quản lý tính năng toàn màn hình (Fullscreen) và xoay màn hình.
  3. Quản lý toàn bộ vòng đời và thông số Picture-in-Picture (PiP).
  4. Quản lý BroadcastReceiver cho RemoteAction.
  5. Điều phối Foreground Service (`MediaService`).
  6. Xử lý cầu nối JavascriptInterface (`MediaBridge`).
- **Tác động:** Khó viết Unit Test cho từng chức năng riêng biệt; nguy cơ phát sinh lỗi khi nhiều người cùng can thiệp vào `MainActivity.kt`.
- **Đề xuất tái cấu trúc:** Tách các module phụ trách chuyên biệt:
  - `PipManager.kt`: Chuyên quản lý AspectRatio, `PictureInPictureParams` và RemoteAction.
  - `FullscreenManager.kt`: Chuyên quản lý `onShowCustomView` và `onHideCustomView`.
  - `PlaybackCoordinator.kt`: Chuyên điều phối trạng thái giữa `MediaBridge` và `MediaService`.

### C. Thiếu hụt kiểm thử tự động (Unit Test / Integration Test)
- **Hiện trạng:** Lớp `AdBlockEngine.kt` có logic kiểm tra URL rất quan trọng nhưng hiện chưa có file kiểm thử nào trong thư mục `app/src/test/`.
- **Rủi ro:** Khi ai đó bổ sung các domain mới hoặc tối ưu thuật toán bóc tách URL, có thể vô tình làm hỏng các trường hợp biên (subdomain kép, query params phức tạp).
- **Đề xuất:** Viết một bộ JUnit test cho `AdBlockEngineTest.kt` kiểm tra các trường hợp:
  - Domain chính xác.
  - Subdomain (`ads.google.com`, `xyz.doubleclick.net`).
  - Path patterns (`/api/stats/ads`, `/pagead/`).
  - URL không hợp lệ hoặc rỗng.

### D. Độ phụ thuộc vào DOM của YouTube Mobile
- **Hiện trạng:** `youtube_defuser.js` và `cosmetic_rules.css` sử dụng các class của YouTube như `.ad-showing`, `.ytp-ad-skip-button`, `ytm-promoted-sparkles-web-renderer`.
- **Rủi ro:** Khi Google cập nhật cấu trúc DOM của `m.youtube.com`, một số bộ luật có thể không còn tác dụng.
- **Đề xuất:** Triển khai cơ chế cập nhật bộ lọc động từ xa (Remote Filter Update) để ứng dụng có thể tải file CSS/TXT mới từ GitHub mà không cần cập nhật cả file APK.

---

## 2. Bảng Phân Cấp Mức Độ Ưu Tiên Cải Tiến

| Hạng mục | Mức độ ưu tiên | Độ phức tạp | Mô tả tóm tắt |
| :--- | :---: | :---: | :--- |
| **Bổ sung Unit Test cho `AdBlockEngine`** | **Cao (P1)** | Thấp | Tạo các test case kiểm tra độ chính xác và an toàn của việc lọc URL. |
| **Tách `PipManager` khỏi `MainActivity`** | **Trung bình (P2)** | Trung bình | Tách riêng logic xử lý PiP để `MainActivity` gọn gàng, dễ bảo trì. |
| **Dọn dẹp hoặc tích hợp `adblock-rust`** | **Trung bình (P2)** | Cao | Quyết định tích hợp NDK hay loại bỏ thư mục Rust để tối ưu repository. |
| **Cơ chế cập nhật bộ lọc từ xa (Remote Update)** | **Thấp (P3)** | Trung bình | Tải danh sách `blocked_domains.txt` và `cosmetic_rules.css` mới từ GitHub raw mà không cần build lại APK. |
