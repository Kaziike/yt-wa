# LiteTube 🚀
### Trình Phát Video Tối Giản, Siêu Nhẹ & Sạch Bóng Quảng Cáo Dành Cho Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Ads_Blocked-100%25_Zero_Ads-blue?style=for-the-badge" alt="Zero Ads" />
</p>

---

## 📖 Giới Thiệu Sản Phẩm

**LiteTube** là ứng dụng Android gọn nhẹ mang lại trải nghiệm xem video mượt mà, tiết kiệm RAM/Pin tối đa và không bị làm phiền bởi quảng cáo. Ứng dụng kết hợp sức mạnh giải mã phần cứng của Chromium WebView với **kiến trúc phòng thủ Brave Shields 3 tầng** và **giao diện Native Sliding Sheet 2 tầng**.

---

## ✨ Tính Năng Nổi Bật

### 🎬 1. Trình Phát Thu Nhỏ Trong App (In-App MiniPlayer 2 Tầng)
* **2 Tầng WebView Độc Lập:** Tầng dưới (`Browse`) giữ nguyên vị trí cuộn bảng tin Trang chủ, Tìm kiếm và Kênh đăng ký; tầng trên (`Player Sheet`) trượt nổi mượt mà để phát video chất lượng cao.
* **Vuốt Thu Nhỏ Thông Minh:** Kéo nhẹ mép trên video hoặc bấm nút thu nhỏ để co trình phát về thanh MiniPlayer gọn gàng ngay phía trên thanh điều hướng, vừa nghe/xem tiếp vừa tự do lướt tìm video khác.
* **Hỗ Trợ Deep Link Chuẩn Native:** Mở link `youtube.com/watch` hoặc `youtu.be` từ bên ngoài sẽ tự động bật lớp Player nổi đè lên Trang chủ mà không nhân bản cửa sổ ứng dụng.

---

### 🛡️ 2. Hệ Thống Chặn Quảng Cáo 3 Tầng (Brave Shields Model)
* **Tầng 1 — Lọc Mạng Native `O(k)` (`AdBlockEngine`):** Chặn tức thì các tên miền và đường dẫn quảng cáo/telemetry (`doubleclick.net`, `google-analytics.com`, `youtube.com/api/stats/ads`, `qoe`, `play.google.com/log`...) ngay tại `shouldInterceptRequest`.
* **Tầng 2 — Scriptlet Khử Quảng Cáo Event-Driven (`youtube_defuser.js`):** Tẩy sạch trường quảng cáo trong phản hồi JSON của InnerTube API và tự động vượt quảng cáo ghép luồng (SSAI) qua `MutationObserver` mà không tiêu tốn CPU khi xem bình thường, giữ nguyên tốc độ xem (`1.25x`, `1.5x`, `2x`) của người dùng.
* **Tầng 3 — Làm Sạch Giao Diện (`cosmetic_rules.css`):** Loại bỏ banner tài trợ, thẻ quảng cáo xen kẽ và các hộp thoại nhắc mở ứng dụng chính thức, tuân thủ chính sách bảo mật TrustedHTML CSP.

---

### 📱 3. Picture-in-Picture (PiP) & Phát Âm Thanh Khi Tắt Màn Hình
* **Cửa Sổ Nổi PiP 3 Nút Tiện Dụng:** Tự động khớp tỷ lệ khung hình thực tế của video với 3 nút điều khiển được bảo vệ bằng khóa phiên: **Nghe nền (Tai nghe) | Phát/Tạm dừng | Video tiếp theo**.
* **Phát Trong Nền & MediaSession Toàn Diện (`MediaService`):** Điều khiển trực tiếp từ Màn hình khóa, Thanh thông báo (kèm ảnh bìa thumbnail nét cao), tai nghe Bluetooth/Smartwatch, và tự động tạm dừng khi rút tai nghe.
* **Tiết Kiệm Pin Thông Minh (`KeepAliveWebView`):** Chỉ duy trì giải mã nền trên đúng WebView đang phát nhạc; tự động cho WebView ngủ và giải phóng bộ nhớ GPU khi tạm dừng hoặc đóng trình phát.

---

### ⚙️ 4. Menu Cài Đặt Native & Bộ Nhớ Đệm Tĩnh Đa Tầng
* Truy cập mục **Cài đặt LiteTube** ngay trong trang cài đặt tài khoản (`https://m.youtube.com/select_site`).
* Tùy chỉnh tức thì qua `BottomSheetDialog` chuẩn Material:
  * Bật / tắt Chặn quảng cáo & Tua SSAI.
  * Bật / tắt Phát nhạc trong nền.
  * Bật / tắt Tự động mở Video Popup (PiP).
  * Bật / tắt Lưu đệm tài nguyên tĩnh (`WebResourceCacheManager` hỗ trợ Gzip, Single-flight & chống lỗi Captive Portal).
  * Xem dung lượng thực tế và xóa sạch bộ nhớ đệm bất đồng bộ không gây giật giao diện.

---

## 📥 Tải Về & Cài Đặt (Installation)

### Cách 1: Tải Bản Đóng Gói Sẵn (Khuyên dùng)
1. Truy cập mục [**Releases**](https://github.com/Kaziike/yt-wa/releases) hoặc tab [**Actions**](https://github.com/Kaziike/yt-wa/actions) của kho lưu trữ này.
2. Tải về file `.apk` mới nhất từ mục **Artifacts** (`LiteTube-Debug-APK`).
3. Mở file `.apk` trên điện thoại Android (8.0+) để cài đặt và trải nghiệm!

### Cách 2: Tự Biên Dịch Từ Mã Nguồn
Yêu cầu: **JDK 17** và **Android SDK Platform 34**.

```bash
git clone https://github.com/Kaziike/yt-wa.git
cd yt-wa

# Chạy kiểm thử đơn vị và đóng gói Debug APK
chmod +x ./gradlew
./gradlew testDebugUnitTest assembleDebug
```

File APK đầu ra nằm tại: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🏗️ Kiến Trúc Kỹ Thuật (Architecture Overview)

```
┌──────────────────────────────────────────────────────────────┐
│                     HỆ ĐIỀU HÀNH ANDROID                     │
│     SystemUI (PiP Actions) • MediaSession & LockScreen       │
│     AudioNoisy Broadcast   • PowerManager Partial WakeLock   │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                  TẦNG NATIVE ANDROID (KOTLIN)                │
│  • MainActivity: Điều phối vòng đời singleTask & Deep Link   │
│  • PlayerSheetController: Sliding Sheet, MiniPlayer Gestures │
│  • PipController & FullscreenController: PiP & Xoay màn hình │
│  • PlaybackCoordinator & MediaService: Đồng bộ MediaSession  │
│  • AdBlockEngine & WebResourceCacheManager: Lọc O(k) & Cache │
└──────────────────────────────┬───────────────────────────────┘
                               │ WebBridge (JavascriptInterface)
┌──────────────────────────────▼───────────────────────────────┐
│              TẦNG WEB SHIELDS (ASSETS SCRIPTLETS)            │
│  • yt_core_bridge.js: State Machine & IPC tiết lưu sự kiện   │
│  • youtube_defuser.js: Khử InnerTube, SSAI & Auto-Unmute     │
│  • pip_controller.js: Cô lập DOM PiP & Resync trình phát     │
│  • browse_interceptor.js: Bắt SPA Watch & Cài đặt select_site│
│  • cosmetic_rules.css & player_ui.css: Làm sạch giao diện    │
└──────────────────────────────────────────────────────────────┘
```

> 📚 Xem tài liệu kiến trúc, luồng dữ liệu và hướng dẫn bảo trì chi tiết tại [**Thư mục Tài Liệu Kỹ Thuật (`docs/`)**](./docs/README.md).

---

## 🔒 Quyền Riêng Tư & Miễn Trừ Trách Nhiệm

* **Không theo dõi (Zero Telemetry):** Ứng dụng không chứa bất kỳ SDK phân tích hay thu thập dữ liệu bên thứ ba nào; thông tin đăng nhập (nếu sử dụng) nằm hoàn toàn trong hộp cát Cookie của Android System WebView trên thiết bị của bạn.
* **Mã nguồn mở phi thương mại:** Dự án phục vụ mục đích học tập, nghiên cứu tối ưu hóa hiệu năng WebView và kiến trúc trình duyệt an toàn trên thiết bị di động.
