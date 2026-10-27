# YouTube Lite 🚀
### Trình Phát YouTube Tối Giản, Siêu Nhẹ & Sạch Bóng Quảng Cáo Dành Cho Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Architecture-Brave_Shields_3--Tier-FF1B2D?style=for-the-badge&logo=brave&logoColor=white" alt="Brave Shields" />
  <img src="https://img.shields.io/badge/RAM_Usage-~100MB-00C853?style=for-the-badge" alt="RAM Usage" />
  <img src="https://img.shields.io/badge/Ads_Blocked-100%25_Zero_Ads-blue?style=for-the-badge" alt="Zero Ads" />
  <img src="https://img.shields.io/badge/License-MIT-orange?style=for-the-badge" alt="License" />
</p>

---

## 📖 Giới Thiệu Sản Phẩm

**YouTube Lite** là ứng dụng Android thế hệ mới được thiết kế để mang đến trải nghiệm giải trí video đỉnh cao: **Siêu mượt, Siêu nhẹ, Tiết kiệm pin tối đa và Tuyệt đối không bị làm phiền bởi bất kỳ loại quảng cáo nào**.

Thay vì phải gánh một ứng dụng chính thức cồng kềnh tiêu tốn từ **500MB đến 1GB RAM**, làm nóng máy và ngập tràn nội dung quảng cáo tài trợ, **YouTube Lite** kết hợp sức mạnh tối ưu phần cứng của Chromium WebView với **Kiến trúc Phòng thủ 3 Tầng của Brave Browser (Brave Shields Architecture)** và kiến trúc **Native In-App MiniPlayer 2 tầng độc quyền**.

---

## ✨ Tính Năng Đột Phá

### 🎬 1. Trình Phát Thu Nhỏ Trong App (In-App MiniPlayer 2 Tầng Độc Quyền)
* **Xem đa nhiệm mượt mà:** Chạm vào bất kỳ video nào trên trang chủ, tìm kiếm hay kênh đăng ký để mở lớp phát video nổi độc lập (`playerPanel`).
* **Vuốt xuống để thu nhỏ:** Thao tác vuốt nhẹ mép video hoặc nhấn nút mũi tên để thu nhỏ trình phát thành thanh MiniPlayer cao 64dp ở đáy màn hình.
* **Lướt trang chủ nguyên vẹn 100%:** Trang chủ bên dưới giữ nguyên vị trí cuộn, không hề bị tải lại hay gián đoạn. Bạn vừa có thể nghe/xem video nhỏ ở góc đáy, vừa tiếp tục lướt tìm video mới.
* **Chạm để bung to (Expand) & Nút Back thông minh:** Chạm vào thanh MiniPlayer để phóng to lại toàn màn hình; nút Back tự động thu nhỏ video hoặc lùi trang chủ cực kỳ tự nhiên.

---

### 🛡️ 2. Hệ Thống Chặn Quảng Cáo 3 Lớp (Brave Shields Architecture)
Không chỉ là bộ lọc thông thường, YouTube Lite loại bỏ quảng cáo từ trong gốc rễ:
* **Lớp 1 - Chặn mạng Native ($O(1)$ Network Filter):** Triệt tiêu ngay lập tức các yêu cầu kết nối đến máy chủ quảng cáo và telemetry (`googleads`, `doubleclick`, `/api/stats/ads`...) trước khi request rời khỏi thiết bị. Tiết kiệm tối đa dung lượng 4G/5G.
* **Lớp 2 - Khắc chế triệt để quảng cáo ghép luồng (SSAI Defuser):** Tự động phát hiện các đoạn quảng cáo được nhúng thẳng vào luồng phát video, kích hoạt cơ chế **Tua siêu tốc 16x + Tắt tiếng (Mute) + Tự động Skip** chỉ trong vỏn vẹn **0.2 giây**!
* **Lớp 3 - Làm sạch giao diện (Cosmetic DOM Filter):** Ẩn sạch banner tài trợ, bài đăng quảng cáo dạo và các thông báo phiền toái nhắc "Cài đặt ứng dụng YouTube".

---

### 🎛️ 3. Hệ Thống Điều Khiển Đa Phương Tiện Toàn Diện (System Media Controls)
* **Tích hợp MediaSession chuẩn Android:** Đồng bộ đầy đủ thông tin tên video, nghệ sĩ/kênh sáng tạo, trạng thái phát và thanh thời gian thực.
* **Điều khiển mọi nơi:** Tạm dừng, phát tiếp, chuyển bài hoặc tua trực tiếp ngay trên:
  * 🔔 **Thanh thông báo hệ thống (Notification Shade)**
  * 🔒 **Màn hình khóa (Lock Screen)**
  * 🎧 **Nút bấm / Cảm ứng trên tai nghe Bluetooth (TWS / Headphone)**
  * ⌚ **Đồng hồ thông minh (Smartwatch / Wear OS)**

---

### 📱 4. Picture-in-Picture (PiP) Mượt Mà & Phát Âm Thanh Khi Tắt Màn Hình
* **Chế độ cửa sổ nổi PiP:** Vuốt về màn hình chính (Home) để video tự động thu nhỏ thành cửa sổ nổi. Cửa sổ PiP tự động khớp tỷ lệ chuẩn của video (16:9, 4:3 hoặc video dọc Shorts) và trang bị nút Play/Pause native độc lập.
* **Công nghệ chống ngắt Micro-Seek:** Thoát và vào PiP liên tục mà không bao giờ bị dừng hình hay kẹt nút phát/tạm dừng.
* **Phát trong nền (Background Playback):** Thoải mái nghe nhạc, podcast hoặc bài giảng khi tắt màn hình điện thoại mà âm thanh vẫn duy trì ổn định qua Foreground Service.

---

### ⚡ 5. Siêu Tối Giản Tài Nguyên & Bộ Nhớ Đệm Tải Trước (Static Cache)
* **Tiêu thụ RAM chỉ ~100MB:** Tối ưu hóa cấu hình phần cứng đồ họa, giải phóng tài nguyên nền để máy luôn mát và tiết kiệm pin.
* **Static Preload Cache Manager:** Tải trước và lưu trữ cục bộ các script, font chữ và biểu tượng tĩnh, giúp các lần mở app tiếp theo nhanh chóng gần như tức thì.
* **Kéo để làm mới (Swipe-to-Refresh):** Dễ dàng kéo từ mép trên xuống để cập nhật lại bảng tin video mới nhất.

---

### ⚙️ 6. Menu Cài Đặt Tích Hợp Native Tinh Tế
* Truy cập mục Cài đặt của ứng dụng ngay trong trang cài đặt tài khoản của YouTube (`https://m.youtube.com/select_site`).
* Giao diện BottomSheet chuẩn Material Design cho phép bạn:
  * Bật / tắt Chặn quảng cáo (AdBlock).
  * Bật / tắt Phát âm thanh khi tắt màn hình (Background Play).
  * Bật / tắt Tự động kích hoạt PiP khi ra màn hình chính.
  * Bật / tắt Bộ nhớ đệm tài nguyên tĩnh (Static Cache).
  * Xem dung lượng bộ nhớ đệm và 1 chạm xóa sạch cache để giải phóng bộ nhớ.

---

## 📊 Bảng So Sánh Tính Năng

| Tính năng | YouTube Lite | Ứng Dụng YouTube Gốc | Trình Duyệt Thường (Chrome/Brave) |
| :--- | :---: | :---: | :---: |
| **Dung lượng RAM tiêu thụ** | **~100MB - 140MB** | 500MB - 1.2GB | 350MB - 600MB |
| **Chặn 100% Quảng cáo (Video + Banner)** | ✅ **Miễn phí** | ❌ (Cần mua Premium) | ⚠️ (Cần cài thêm extension/addon) |
| **Khắc chế quảng cáo ghép luồng (SSAI 16x)**| ✅ **Có (0.2s Skip)** | ❌ | ❌ |
| **In-App MiniPlayer (Vừa xem vừa lướt)** | ✅ **Native 2 Tầng** | ✅ | ❌ (Mở video là mất trang chủ) |
| **Phát khi tắt màn hình (Background Play)** | ✅ **Có** | ❌ (Cần mua Premium) | ⚠️ (Dễ bị hệ điều hành tắt ngầm) |
| **Cửa sổ nổi Picture-in-Picture (PiP)** | ✅ **Tự động co giãn**| ❌ (Giới hạn khu vực/Premium) | ⚠️ (Thao tác thủ công) |
| **Media Controls (Lockscreen / Notification)**| ✅ **Đầy đủ MediaSession**| ✅ | ⚠️ (Không ổn định) |
| **Kéo để tải lại (Swipe-to-Refresh)** | ✅ **Có** | ✅ | ⚠️ (Dễ xung đột cử chỉ web) |
| **Quyền riêng tư & Không thu thập dữ liệu** | ✅ **Tuyệt đối** | ❌ (Theo dõi hành vi quảng cáo) | ⚠️ |

---

## 📥 Tải Về & Cài Đặt (Installation)

### Cách 1: Tải Bản Đóng Gói Sẵn (Khuyên dùng)
1. Truy cập mục [**Releases**](https://github.com/Kaziike/yt-wa/releases) hoặc tab [**Actions**](https://github.com/Kaziike/yt-wa/actions) của kho lưu trữ này.
2. Tải về file `.apk` mới nhất từ mục **Artifacts** (ví dụ: `YouTubeLite-Debug-APK`).
3. Mở file `.apk` trên điện thoại Android của bạn và chọn **Cài đặt** (Nếu có thông báo, cho phép cài đặt ứng dụng từ nguồn này).
4. Mở **YouTube Lite** và tận hưởng trải nghiệm xem video không giới hạn!

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

* **YouTube Lite** là một dự án phần mềm mã nguồn mở độc lập, phi thương mại phục vụ mục đích nghiên cứu công nghệ tối ưu hóa tài nguyên phần cứng di động và duyệt web an toàn.
* Dự án này **không liên kết, không được ủy quyền, không được tài trợ** bởi Google LLC hoặc YouTube. Nhãn hiệu YouTube và logo liên quan là tài sản thuộc quyền sở hữu của Google LLC.

---

<p align="center">
  Được phát triển với tâm huyết dành cho cộng đồng yêu thích sự tinh giản và tự do công nghệ. ⭐ Hãy nhấn <b>Star</b> cho dự án nếu bạn thấy hữu ích!
</p>
