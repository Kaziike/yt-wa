# Hướng Dẫn Phát Triển & Đóng Gói (Development Guide)

Tài liệu này cung cấp hướng dẫn từng bước để thiết lập môi trường, biên dịch, gỡ lỗi và mở rộng các chức năng trong dự án **LiteTube**.

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

- **Hệ điều hành:** Windows 10/11, macOS hoặc Linux.
- **Java Development Kit (JDK):** JDK 17 (khuyên dùng Eclipse Temurin 17 LTS).
- **Android SDK:**
  - `Android SDK Platform 34`
  - `Android SDK Build-Tools 34.0.0`
- **IDE khuyên dùng:** Android Studio Hedgehog, Iguana, Jellyfish hoặc phiên bản mới hơn.

---

## 2. Quy Trình Biên Dịch & Đóng Gói (Build Process)

### A. Biên dịch qua Dòng Lệnh (Local CLI)

1. Mở terminal tại thư mục gốc của dự án.
2. Chạy lệnh đóng gói bản Debug APK:
   ```powershell
   # Trên hệ điều hành Windows:
   .\gradlew.bat assembleDebug

   # Trên hệ điều hành Linux / macOS:
   chmod +x gradlew
   ./gradlew assembleDebug
   ```
3. Sau khi hoàn tất thành công (`BUILD SUCCESSFUL`), file APK nằm tại:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### B. Biên dịch bản Release
Bản Release được cấu hình bật R8 tối ưu hóa và co gọn tài nguyên (`isMinifyEnabled = true`, `isShrinkResources = true`):
```powershell
.\gradlew.bat assembleRelease
```
File APK sẽ xuất hiện tại `app/build/outputs/apk/release/app-release.apk` (mặc định cấu hình ký bằng debug keystore theo `app/build.gradle.kts` dòng 28).

### C. Đóng gói tự động bằng GitHub Actions (Không cần cài SDK)
1. Đẩy mã nguồn lên repository GitHub của bạn (nhánh `main`).
2. Vào tab **Actions** $\rightarrow$ chọn workflow **Build LiteTube APK**.
3. Bạn có thể bấm nút **Run workflow** thủ công (`workflow_dispatch`).
4. Khi tiến trình hoàn thành (khoảng 1.5 - 2 phút), tải file APK trong mục **Artifacts** (`LiteTube-Debug-APK`).

---

## 3. Hướng Dẫn Gỡ Lỗi Qua `chrome://inspect` (Remote Debugging)

Vì toàn bộ giao diện và trình phát YouTube chạy trên WebView phần cứng, bạn có thể xem trực tiếp `console.log`, kiểm tra DOM và đo hiệu năng bằng DevTools của Google Chrome:

```
+-------------------+      Cáp USB (ADB)       +-------------------+
|  Điện thoại       | <=====================> |  Máy tính PC      |
|  - Chạy app       |                         |  - Mở Chrome      |
|  - Bật USB Debug  |                         |  - chrome://inspect
+-------------------+                         +-------------------+
```

### Các bước thực hiện:
1. **Trên điện thoại:**
   - Vào *Cài đặt* $\rightarrow$ *Thông tin điện thoại* $\rightarrow$ Bấm 7 lần vào *Số bản dựng* để bật *Tùy chọn nhà phát triển*.
   - Vào *Tùy chọn nhà phát triển* $\rightarrow$ Bật **Gỡ lỗi qua USB (USB debugging)**.
2. **Kết nối máy tính:**
   - Cắm cáp USB và chọn **Cho phép gỡ lỗi USB** trên màn hình điện thoại.
3. **Mở DevTools:**
   - Mở Chrome trên máy tính và truy cập: `chrome://inspect/#devices` (hoặc `brave://inspect/#devices` nếu dùng Brave).
   - Tích chọn ô **Discover USB devices**.
   - Tìm thiết bị của bạn và bấm nút **inspect** bên dưới tab `LiteTube`.
   - Một cửa sổ DevTools sẽ bật lên cho phép bạn xem toàn bộ log JavaScript (`[BraveShield]`, `[WebConsole]`) trong tab **Console**.

---

## 4. Hướng Dẫn Mở Rộng Tính Năng

### A. Thêm Domain hoặc Endpoint Quảng Cáo Mới
1. Mở file `app/src/main/assets/filters/blocked_domains.txt`.
2. Thêm domain hoặc path mới vào dòng riêng biệt, ví dụ:
   ```text
   # Chặn thêm domain quảng cáo mới
   new-ad-tracker.example.com
   ```
3. Không cần sửa code Kotlin; `AdBlockEngine` sẽ tự động nạp danh sách mới vào bộ nhớ khi khởi động.

### B. Bổ sung Luật Ẩn Giao Diện (Cosmetic Filter)
1. Mở file `app/src/main/assets/filters/cosmetic_rules.css`.
2. Bổ sung các class hoặc selector mới vào nhóm cần ẩn:
   ```css
   .new-unwanted-banner-class {
       display: none !important;
   }
   ```
3. Cơ chế tiêm Base64 trong `AppWebViewClient.kt` sẽ tự động áp dụng style mới mà không vi phạm Trusted Types CSP.

### C. Quy Tắc Bảo Vệ Tính Toàn Vẹn Của Hệ Thống
1. **Không can thiệp vào `KeepAliveWebView.kt`:** Việc sửa các hàm `dispatchWindowVisibilityChanged` hoặc `onWindowVisibilityChanged` sẽ làm mất tính năng phát âm thanh nền khi tắt màn hình.
2. **Luôn giữ `setLayerType(View.LAYER_TYPE_NONE)`:** Không đổi sang `LAYER_TYPE_HARDWARE` trên WebView trong `MainActivity.kt` để tránh lỗi màn hình đen khi chuyển sang PiP.
3. **Giữ cờ `RECEIVER_EXPORTED`:** Khi đăng ký `pipActionReceiver`, bắt buộc giữ `ContextCompat.RECEIVER_EXPORTED` để hệ thống Android 13/14 SystemUI không chặn các lệnh điều khiển từ cửa sổ nổi.
