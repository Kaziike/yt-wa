# Xử Lý Sự Cố & Lỗi Thực Tế (Troubleshooting)

Tài liệu này tổng hợp toàn bộ các sự cố kỹ thuật thực tế đã được phát hiện, điều tra và giải quyết triệt để trong quá trình phát triển và kiểm toán mã nguồn dự án **LiteTube**.

---

## 1. Lỗi AAPT: `attribute android:layoutHeight not found`

### Hiện tượng
Khi chạy `./gradlew assembleDebug`, bước `:app:processDebugResources` báo lỗi không tìm thấy thuộc tính `android:layoutHeight` / `android:layoutWidth`.

### Nguyên nhân & Cách khắc phục
Trong file XML layout Android, các thuộc tính kích thước bắt buộc viết theo chuẩn **snake_case** (`android:layout_width`, `android:layout_height`) thay vì camelCase.

---

## 2. PiP Bị Màn Hình Đen Nhưng Vẫn Có Tiếng (Black Screen on PiP)

### Hiện tượng
Cửa sổ Picture-in-Picture nổi lên chỉ hiển thị màu đen nhưng âm thanh vẫn phát bình thường.

### Nguyên nhân & Cách khắc phục
Đặt `LAYER_TYPE_HARDWARE` trên `WebView` tạo offscreen buffer xung đột khi cửa sổ co nhỏ đột ngột về khung PiP (trong khi Activity đã bật `android:hardwareAccelerated="true"`). Khắc phục bằng cách giữ `view.setLayerType(View.LAYER_TYPE_NONE, null)`.

---

## 3. Nút Điều Khiển Trên Cửa Sổ PiP & Bảo Mật Broadcast Trên Android 13/14

### Hiện tượng
Các nút `RemoteAction` trên cửa sổ PiP được gọi từ tiến trình hệ thống (`com.android.systemui`), nên `RECEIVER_NOT_EXPORTED` sẽ làm liệt nút; ngược lại nếu dùng `RECEIVER_EXPORTED` cho toàn bộ action mà không xác thực thì ứng dụng bên thứ 3 có thể giả mạo lệnh điều khiển.

### Cách khắc phục
1. Toàn bộ điều khiển giữa `MediaService` và tầng phát sử dụng giao tiếp nội bộ trực tiếp trong tiến trình (`MediaService.CommandListener` + `PendingIntent.getService` với `android:exported="false"`).
2. Chỉ đăng ký `BroadcastReceiver` (`RECEIVER_EXPORTED`) trong `PipController` cho các nút của cửa sổ PiP và đính kèm khóa phiên ngẫu nhiên `EXTRA_PIP_TOKEN` (`UUID` sinh mới trong RAM mỗi lần chạy), từ chối mọi broadcast không khớp token.

---

## 4. Văng Ứng Dụng `IllegalArgumentException` Khi Vào PiP Với Video Siêu Dọc / Siêu Rộng

### Nguyên nhân & Cách khắc phục
Android giới hạn tỷ lệ `PictureInPictureParams.Builder().setAspectRatio(...)` trong đoạn `[1 / 2.39, 2.39]`. `PipController.clampedAspectRatio()` tự động kẹp mọi tỷ lệ khung hình vào khoảng an toàn này trước khi truyền cho hệ điều hành.

---

## 5. Vi Phạm Chính Sách Trusted Types CSP Khi Tiêm CSS Trên `m.youtube.com`

### Nguyên nhân & Cách khắc phục
YouTube Mobile chặn việc gán chuỗi HTML trực tiếp (`innerHTML`). `WebAssetRepository` mã hóa CSS sang Base64 và giải mã qua `TextDecoder` gán vào `style.textContent`, tuân thủ tuyệt đối TrustedHTML CSP.

---

## 6. Tự Tua Mất Video Chính & Ẩn Mất Trình Phát Khi Có Quảng Cáo SSAI

### Hiện tượng
Video thật đôi lúc tự nhảy thẳng về cuối hoặc mất tốc độ xem `1.5x/2x`; khung trình phát bị đen 0px khi chớm có quảng cáo.

### Nguyên nhân đã xác minh
1. Thẻ `<div class="video-ads ytp-ad-module"></div>` tồn tại rỗng thường trực trong DOM của `#movie_player`. Nếu bộ lọc SSAI kiểm tra `.video-ads`, script sẽ lầm tưởng đang phát quảng cáo suốt thời gian xem video thật.
2. Class `.ad-showing` và `.ad-interrupting` được gắn trực tiếp lên `#movie_player`; đặt `display: none !important` cho hai class này trong `cosmetic_rules.css` sẽ làm biến mất toàn bộ trình phát video.

### Cách khắc phục
- Gỡ `.ad-showing` và `.ad-interrupting` khỏi `cosmetic_rules.css`.
- Trong `youtube_defuser.js`, sử dụng `MutationObserver` kết hợp `isAdShowingNow()` chỉ nhận diện khi `#movie_player` mang class `.ad-showing`/`.ad-interrupting` hoặc nút/lớp phủ quảng cáo thực sự hiển thị; lưu và khôi phục đúng `savedPlaybackRate` của người dùng, đồng thời chỉ tua nhanh đối với clip quảng cáo ngắn (`duration <= 300s`).

---

## 7. Hỏng Ghi Đĩa `yt_static_cache` Sau Khi Bấm "Xóa Bộ Nhớ Đệm"

### Nguyên nhân & Cách khắc phục
Gọi `context.cacheDir.deleteRecursively()` xóa mất cả thư mục gốc `cacheDir` lẫn thư mục con `yt_static_cache`, gây lỗi `FileNotFoundException` cho các lần lưu sau. `AppSettings.clearAppCacheAsync()` đã được sửa để chỉ xóa các phần tử con bên trong `cacheDir` trên luồng nền và gọi `WebResourceCacheManager.ensureCacheDir()` tái tạo thư mục ngay lập tức.

---

## 8. Quy Tắc `domain/path` Trong `blocked_domains.txt` Không Khớp

### Nguyên nhân & Cách khắc phục
`uri.host` không bao giờ chứa phần đường dẫn `/path` (như `play.google.com/log` hay `youtube.com/api/stats/qoe`). `AdBlockEngine` đã tách riêng tập `blockedDomains` và bảng `hostPathRules`, đồng thời đối chiếu từng cấp tên miền cha trong `O(k)` với `RuleSnapshot` bất biến an toàn đa luồng.
