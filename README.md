# LiteTube 🚀
### Trình Phát Video Tối Giản, Siêu Nhẹ & Sạch Bóng Quảng Cáo Dành Cho Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Ads_Blocked-100%25_Zero_Ads-blue?style=for-the-badge" alt="Zero Ads" />
</p>

---

## 📖 Giới Thiệu Sản Phẩm

**LiteTube** là ứng dụng Android thế hệ mới được thiết kế để mang đến trải nghiệm giải trí video đỉnh cao: **Siêu mượt, Siêu nhẹ, Tiết kiệm pin tối đa và Tuyệt đối không bị làm phiền bởi bất kỳ loại quảng cáo nào**.

Thay vì phải gánh một ứng dụng chính thức cồng kềnh, và ngập tràn nội dung quảng cáo tài trợ, **LiteTube** kết hợp sức mạnh tối ưu phần cứng của Chromium WebView với **ý tưởng của Brave Browser**.

---

## ✨ Tính Năng

### 🎬 1. Trình Phát Thu Nhỏ Trong App (In-App MiniPlayer 2 Tầng Độc Quyền)


---

### 🛡️ 2. Hệ Thống Chặn Quảng Cáo





---

### 📱 3. Picture-in-Picture (PiP) Mượt Mà & Phát Âm Thanh Khi Tắt Màn Hình
* **Chế độ cửa sổ nổi PiP:** Vuốt về màn hình chính (Home) để video tự động thu nhỏ thành cửa sổ nổi. Cửa sổ PiP tự động khớp tỷ lệ chuẩn của video (16:9, 4:3 hoặc video dọc Shorts) và trang bị nút Play/Pause native độc lập.
* **Công nghệ chống ngắt Micro-Seek:** Thoát và vào PiP liên tục mà không bao giờ bị dừng hình hay kẹt nút phát/tạm dừng.
* **Phát trong nền (Background Playback):** Thoải mái nghe nhạc, podcast hoặc bài giảng khi tắt màn hình điện thoại mà âm thanh vẫn duy trì ổn định qua Foreground Service.



---

### ⚙️ 4. Menu Cài Đặt Tích Hợp Native Tinh Tế
* Truy cập mục Cài đặt của ứng dụng ngay trong trang cài đặt tài khoản của YouTube (`https://m.youtube.com/select_site`).
* Giao diện BottomSheet chuẩn Material Design cho phép bạn:
  * Bật / tắt Chặn quảng cáo (AdBlock).
  * Bật / tắt Phát âm thanh khi tắt màn hình (Background Play).
  * Bật / tắt Tự động kích hoạt PiP khi ra màn hình chính.
  * Bật / tắt Bộ nhớ đệm tài nguyên tĩnh (Static Cache).
  * Xem dung lượng bộ nhớ đệm và 1 chạm xóa sạch cache để giải phóng bộ nhớ.

---



## 📥 Tải Về & Cài Đặt (Installation)

### Cách 1: Tải Bản Đóng Gói Sẵn (Khuyên dùng)
1. Truy cập mục [**Releases**](https://github.com/Kaziike/yt-wa/releases) hoặc tab [**Actions**](https://github.com/Kaziike/yt-wa/actions) của kho lưu trữ này.
2. Tải về file `.apk` mới nhất từ mục **Artifacts** (ví dụ: `YouTubeLite-Debug-APK`).
3. Mở file `.apk` trên điện thoại Android của bạn và chọn **Cài đặt** (Nếu có thông báo, cho phép cài đặt ứng dụng từ nguồn này).
4. Mở **LiteTube** và tận hưởng trải nghiệm xem video không giới hạn!

---

### Cách 2: Tự Biên Dịch Từ Mã Nguồn (Dành cho Lập trình viên)
Yêu cầu: Máy tính đã cài đặt **Java 17 (JDK 17)** và **Android SDK Platform 34**.

```bash
# 1. Clone kho lưu trữ về máy tính
git clone https://github.com/Kaziike/yt-wa.git
cd yt-wa

# 2. Biên dịch bản Debug APK
# Trên Windows (PowerShell):
.\gradlew.bat assembleDebug

# Trên macOS / Linux:
chmod +x ./gradlew
./gradlew assembleDebug
```

File APK sau khi biên dịch sẽ xuất hiện tại:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🏗️ Kiến Trúc Kỹ Thuật (Architecture Overview)

```
┌────────────────────────────────────────────────────────┐
│                   HỆ ĐIỀU HÀNH ANDROID                 │
│      SystemUI (PiP Controls)  •  Notification Shade    │
│      LockScreen MediaControls  •  PowerManager Lock    │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                  TẦNG NATIVE ANDROID (KOTLIN)          │
│   • MainActivity: Điều phối 2 Tầng View & Cử chỉ       │
│   • Layer 1 (Browse): SwipeRefreshLayout + KeepAlive   │
│   • Layer 2 (Player): playerPanel + MiniPlayerBar      │
│   • AdBlockEngine: Chặn domain quảng cáo O(1)          │
│   • WebResourceCacheManager: Cache tài nguyên tĩnh     │
│   • MediaService: Quản lý MediaSession & WakeLock      │
│   • AppSettings: Lưu cấu hình SharedPreferences        │
└───────────────────────────┬────────────────────────────┘
                            │ IPC Bridge (JavascriptInterface)
┌───────────────────────────▼────────────────────────────┐
│               TẦNG WEB SHIELDS (JAVASCRIPT / CSS)      │
│   • browseWatchInterceptor: Bắt click & SPA pushState  │
│   • youtube_defuser: Tua 16x SSAI, gọt sạch InnerTube  │
│   • pip_controller: Cô lập video PiP & Micro-Seek      │
│   • cosmetic_rules: Xóa bỏ banner tài trợ bằng CSS     │
│   • selectSiteSettings: Nhét menu cài đặt vào web      │
└────────────────────────────────────────────────────────┘
```

> 📚 Xem toàn bộ tài liệu kiến trúc, hướng dẫn mã nguồn và luồng dữ liệu chuyên sâu tại [**Thư mục Tài Liệu Kỹ Thuật (`docs/`)**](./docs/README.md).

---

## 🔒 Cam Kết Quyền Riêng Tư & Bảo Mật

* **Không theo dõi (Zero Telemetry):** Ứng dụng không chứa bất kỳ SDK phân tích hành vi người dùng, theo dõi vị trí hay gửi dữ liệu về máy chủ bên thứ ba.
* **Mã nguồn mở minh bạch:** Toàn bộ mã nguồn Kotlin, JavaScript và CSS đều mở và có thể kiểm chứng độc lập.
* **Tài khoản an toàn:** Mọi thao tác đăng nhập Google (nếu bạn sử dụng) đều diễn ra trực tiếp bên trong Chromium Web Engine của Google, thông tin tài khoản được lưu trữ an toàn trong Cookie Container của thiết bị.

---

## ⚖️ Tuyên Bố Miễn Trừ Trách Nhiệm (Disclaimer)

* **LiteTube** là một dự án phần mềm mã nguồn mở độc lập, phi thương mại phục vụ mục đích nghiên cứu công nghệ tối ưu hóa tài nguyên phần cứng di động và duyệt web an toàn.


---

<p align="center">
  Được phát triển với tâm huyết dành cho cộng đồng yêu thích sự tinh giản và tự do công nghệ. ⭐ Hãy nhấn <b>Star</b> cho dự án nếu bạn thấy hữu ích!
</p>
