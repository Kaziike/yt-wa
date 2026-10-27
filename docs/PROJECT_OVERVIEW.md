# Tổng Quan Dự Án (Project Overview)

## 1. Giới Thiệu Dự Án

**YouTube Lite** là ứng dụng Android chuyên dụng mang lại trải nghiệm xem YouTube siêu nhẹ, mượt mà và hoàn toàn không bị làm phiền bởi quảng cáo. Thay vì dựa vào ứng dụng chính thức cồng kềnh (thường tiêu tốn 400MB - 1GB RAM) hoặc các bản mod phức tạp, YouTube Lite sử dụng công nghệ Android WebView phần cứng kết hợp với **Kiến trúc Brave Shields 3 tầng** và kiến trúc **Native In-App MiniPlayer 2 tầng** để tái lập trải nghiệm duyệt web an toàn, tiết kiệm tài nguyên và bảo vệ quyền riêng tư.

### Mục Tiêu Cốt Lõi
1. **Siêu nhẹ (Ultra Lightweight):** Mức chiếm dụng RAM chỉ duy trì khoảng ~100MB - 140MB trong suốt phiên xem.
2. **Triệt tiêu quảng cáo toàn diện (Brave Shields Architecture):**
   - Chặn domain/endpoint quảng cáo và telemetry từ tầng mạng native trước khi request được gửi đi.
   - Vô hiệu hóa quảng cáo ghép luồng (SSAI - Server-Side Ad Insertion) bằng cơ chế tua tốc độ 16x + Mute + Auto-skip chỉ trong ~0.2s.
   - Làm sạch giao diện (Cosmetic Filtering) loại bỏ toàn bộ banner tài trợ và hộp thoại nhắc "Mở trong ứng dụng YouTube".
3. **Trải nghiệm Native cao cấp:**
   - **In-App MiniPlayer 2 Tầng (Sliding Sheet):** Cho phép xem video thu nhỏ ở đáy màn hình trong khi vẫn duyệt trang chủ, tìm kiếm, kênh đăng ký bên dưới mà không bị tải lại trang.
   - **Hệ thống điều khiển MediaSession toàn diện:** Điều khiển phát, tạm dừng, chuyển bài, tua thời gian thực trên Màn hình khóa (Lockscreen), Thanh thông báo (Notification Shade), Tai nghe Bluetooth và Smartwatch.
   - **Picture-in-Picture (PiP) phần cứng:** Cửa sổ nổi tự động thích ứng với tỉ lệ thật của video (16:9, 4:3, dọc 9:16) kết hợp cơ chế Micro-Seek bảo vệ phát liên tục, không bị kẹt nút play/pause.
   - **Phát âm thanh trong nền (Background Audio):** Giữ âm thanh liên tục khi khóa màn hình hoặc chuyển ứng dụng khác thông qua Foreground Service và cơ chế chống ngắt WebView.
   - **Kéo để làm mới (Swipe-to-Refresh):** Dễ dàng kéo từ trên xuống để reload bảng tin trang chủ.
   - **Menu Cài đặt Native BottomSheet:** Tích hợp tùy biến AdBlock, Background Play, Auto PiP, Cache tĩnh ngay trong mục Cài đặt của YouTube (`m.youtube.com/select_site`).
   - **Tối ưu hóa tài nguyên tĩnh (Static Preload Cache):** Lưu trữ cục bộ các script, font, icon tĩnh giúp giảm tối đa thời gian tải trang lặp lại.

---

## 2. Công Nghệ & Thư Viện Sử Dụng

### A. Môi Trường Phát Triển & Công Cụ Build
- **Hệ điều hành hỗ trợ:** Android 8.0 (API Level 26) trở lên đến Android 14+ (API Level 34).
- **Ngôn ngữ lập trình chính:** Kotlin `1.9.23`, Java `17`.
- **Android Gradle Plugin (AGP):** `8.3.2`.
- **Gradle Version:** `8.4` (phân phối qua Gradle Wrapper).
- **Build Tools / SDK:**
  - `compileSdk`: `34`
  - `targetSdk`: `34`
  - `minSdk`: `26`
  - `versionCode`: `1`
  - `versionName`: `"1.0.0"`

### B. Danh Sách Thư Viện (Dependencies)
Dự án được tinh gọn tối đa, chỉ phụ thuộc vào các thư viện tiêu chuẩn chính thức của Google/AndroidX:

| Tên thư viện | Phiên bản | Vai trò & Mục đích sử dụng |
| :--- | :--- | :--- |
| `androidx.core:core-ktx` | `1.12.0` | Cung cấp extension Kotlin cho Android Core APIs và backward compatibility. |
| `androidx.appcompat:appcompat` | `1.6.1` | Hỗ trợ Activity hiện đại (`AppCompatActivity`), theme tương thích từ API 26. |
| `com.google.android.material:material` | `1.11.0` | Cung cấp BottomSheetDialog, MaterialButton, SwitchMaterial cho menu Cài đặt Native. |
| `androidx.swiperefreshlayout:swiperefreshlayout` | `1.1.0` | Cung cấp cử chỉ kéo từ trên xuống để làm mới trang (Swipe-to-Refresh). |
| `androidx.constraintlayout:constraintlayout` | `2.1.4` | Tùy chọn bố cục linh hoạt khi mở rộng giao diện. |
| `androidx.media:media` | `1.7.0` | Quản lý `MediaSessionCompat` và notification điều khiển media hệ thống (`NotificationCompat.MediaStyle`). |
| `androidx.webkit:webkit` | `1.10.0` | **Thành phần cốt lõi:** Cho phép sử dụng `WebViewCompat.addDocumentStartJavaScript` để tiêm Scriptlet tại `DOCUMENT_START`. |

---

## 3. Cách Khởi Chạy & Đóng Gói Ứng Dụng

### Cách 1: Đóng gói tự động qua CI/CD GitHub Actions
Dự án được cấu hình sẵn pipeline `.github/workflows/build-apk.yml`. Khi có thao tác đẩy code (`push`) hoặc tạo pull request lên nhánh `main` / `master`, hệ thống Ubuntu runner sẽ:
1. Thiết lập Java 17 Temurin.
2. Tự động cấp quyền và chạy `./gradlew assembleDebug --stacktrace`.
3. Đẩy file APK kết quả vào mục **Artifacts** (`YouTubeLite-Debug-APK`) với thời gian lưu trữ 14 ngày.

### Cách 2: Biên dịch qua dòng lệnh (Local CLI)
Yêu cầu máy tính đã cài đặt JDK 17 và cấu hình biến môi trường `ANDROID_HOME`:
```powershell
# Dành cho Windows:
.\gradlew.bat assembleDebug

# Dành cho macOS/Linux:
chmod +x ./gradlew
./gradlew assembleDebug
```
File APK đầu ra sẽ nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

### Cách 3: Mở bằng Android Studio
Mở thư mục gốc dự án bằng Android Studio, đồng bộ Gradle (Sync Project with Gradle Files) và bấm nút **Run** (`Shift + F10`).

---

## 4. Trạng Thái Hiện Tại Của Dự Án

- **Nhánh hoạt động chính:** `main`.
- **Trạng thái mã nguồn:** Mã nguồn hoàn chỉnh, kiến trúc phân tách rõ ràng giữa 2 tầng Native View (Browse View & Player View), tích hợp đầy đủ hệ sinh thái MediaSession, Static Cache và Brave Shields.
- **Trạng thái kiểm thử:**
  - Build Debug APK thành công 100% trên CI GitHub Actions.
  - Các tính năng In-App MiniPlayer, MediaSession Lockscreen/Notification, PiP Micro-Seek, Auto-Skip SSAI, Document-Start injection, Static Cache đã được xác minh hoạt động mượt mà.
