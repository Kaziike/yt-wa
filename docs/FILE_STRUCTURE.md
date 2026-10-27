# Cấu Trúc Thư Mục & Phân Tích File (File Structure)

## 1. Sơ Đồ Cây Thư Mục Toàn Dự Án

```
YouTubeLite/
├── .github/
│   └── workflows/
│       └── build-apk.yml                  # CI/CD workflow build APK tự động qua GitHub Actions
├── adblock-rust/                          # Thư mục mã nguồn thư viện adblock-rust (độc lập, chưa liên kết vào build)
│   └── adblock-rust/
│       ├── Cargo.toml
│       ├── src/
│       └── ...
├── app/                                   # Module ứng dụng Android chính
│   ├── build.gradle.kts                   # Cấu hình build của module app (dependencies, sdk, signing)
│   ├── proguard-rules.pro                 # Luật ProGuard / R8 tối ưu hóa và làm mờ mã nguồn
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml        # Khai báo cấu hình, quyền, Activity, Service, Intent-filters
│           ├── assets/                    # Tài nguyên tĩnh nạp động vào Web Engine
│           │   ├── filters/
│           │   │   ├── blocked_domains.txt   # Danh sách domain và endpoint quảng cáo/telemetry
│           │   │   └── cosmetic_rules.css    # Bộ luật CSS ẩn quảng cáo và banner ứng dụng YouTube
│           │   └── scriptlets/
│           │       ├── pip_controller.js     # Scriptlet cô lập video và điều khiển PiP
│           │       └── youtube_defuser.js    # Scriptlet bẻ khóa InnerTube API, SSAI 16x, chống dừng nền
│           ├── java/com/brave/youtubelite/
│           │   ├── MainActivity.kt           # Activity điều phối chính, quản lý vòng đời, PiP và IPC
│           │   ├── network/
│           │   │   ├── AdBlockEngine.kt      # Singleton kiểm tra và chặn URL quảng cáo tầng mạng O(1)
│           │   │   └── AppWebViewClient.kt   # WebViewClient chặn request và tiêm Document-Start Scriptlets
│           │   ├── service/
│           │   │   └── MediaService.kt       # Foreground Service duy trì âm thanh và CPU WakeLock
│           │   └── view/
│           │       └── KeepAliveWebView.kt   # Custom WebView giả lập View.VISIBLE chống đình chỉ Chromium
│           └── res/                       # Tài nguyên giao diện Native Android
│               ├── layout/
│               │   └── activity_main.xml     # Layout chính: FrameLayout, KeepAliveWebView, ProgressBar, FullscreenContainer
│               ├── values/
│               │   ├── colors.xml            # Bảng màu hệ thống (black, white, red, gray)
│               │   ├── strings.xml           # Chuỗi tài nguyên ứng dụng (app_name)
│               │   └── themes.xml            # Theme nền tối không có ActionBar
│               ├── drawable/
│               └── mipmap-*/
├── docs/                                  # Hệ thống tài liệu kỹ thuật của dự án (Markdown)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties      # Định nghĩa phiên bản Gradle (8.4)
├── .gitignore                             # Cấu hình bỏ qua các file tạm của Git
├── build.gradle.kts                       # Cấu hình build gốc dự án
├── error.txt                              # Nhật ký build lỗi AAPT lịch sử (lưu trữ phục vụ tra cứu)
├── gradle.properties                      # Thuộc tính JVM và AndroidX của Gradle
├── gradlew                                # Script chạy Gradle Wrapper trên Linux/macOS
├── gradlew.bat                            # Script chạy Gradle Wrapper trên Windows
├── README.md                              # Giới thiệu dự án và hướng dẫn build nhanh
└── settings.gradle.kts                    # Khai báo repository và include module :app
```

---

## 2. Bảng Phân Tích Chi Tiết Các File

### A. Nhóm Khởi Chạy & Cấu Hình Build (Build & Config)

| Đường dẫn file | Vai trò & Trách nhiệm |
| :--- | :--- |
| `settings.gradle.kts` | Thiết lập cơ chế giải quyết repository (`FAIL_ON_PROJECT_REPOS`), kích hoạt `google()`, `mavenCentral()`, đặt tên root project là `YouTubeLite` và include module `:app`. |
| `build.gradle.kts` | Cấu hình cấp project: Đăng ký Android Application Plugin `8.3.2` và Kotlin Android Plugin `1.9.23`. |
| `gradle.properties` | Bật các thuộc tính môi trường: `android.useAndroidX=true`, `kotlin.code.style=official`, cấp phát bộ nhớ JVM cho Gradle daemon. |
| `app/build.gradle.kts` | Thiết lập `namespace = "com.brave.youtubelite"`, `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`, `viewBinding = true`, cấu hình ProGuard cho bản Release, và khai báo các dependencies `androidx.webkit:1.10.0`, `androidx.media:1.7.0`. |
| `.github/workflows/build-apk.yml` | Pipeline CI/CD tự động build file APK mỗi khi có commit lên `main` hoặc `master`, dùng runner Ubuntu và Java 17 Temurin. |

---

### B. Nhóm Mã Nguồn Native Android (Kotlin)

| Đường dẫn file | Vai trò & Trách nhiệm |
| :--- | :--- |
| `app/.../MainActivity.kt` | **Trung tâm điều phối ứng dụng:** Khởi tạo WebView, đăng ký `BroadcastReceiver` cho các nút bấm PiP, lắng nghe sự kiện xoay màn hình, điều hướng quay lại (`OnBackPressedCallback`), kết nối cầu nối JavaScript `AndroidBridge`, và quản lý vòng đời chuyển sang PiP / Foreground Service. |
| `app/.../view/KeepAliveWebView.kt` | Kế thừa từ `android.webkit.WebView`, ghi đè `dispatchWindowVisibilityChanged()` và `onWindowVisibilityChanged()` để luôn trả về `View.VISIBLE`, ngăn Chromium ngắt luồng giải mã audio khi tab/app bị ẩn. |
| `app/.../network/AdBlockEngine.kt` | Singleton nạp dữ liệu từ `filters/blocked_domains.txt` vào `HashSet<String>`. Cung cấp phương thức `isBlocked(url)` kiểm tra domain, subdomain và pattern đường dẫn theo độ phức tạp $O(1)$. |
| `app/.../network/AppWebViewClient.kt` | `WebViewClient` tùy biến: Chặn các request quảng cáo tại `shouldInterceptRequest()`, đăng ký các scriptlet chạy từ thời điểm sớm nhất thông qua `WebViewCompat.addDocumentStartJavaScript()`, và tiêm lại scriptlet khi có điều hướng SPA (`doUpdateVisitedHistory()`). |
| `app/.../service/MediaService.kt` | `Foreground Service` loại `mediaPlayback`. Khi điện thoại tắt màn hình, service này được gọi để cấp `PARTIAL_WAKE_LOCK` và hiển thị Notification media, giữ cho CPU không rơi vào trạng thái ngủ đông. |

---

### C. Nhóm Web Assets, Scriptlets & Bộ Lọc (Web Engine)

| Đường dẫn file | Vai trò & Trách nhiệm |
| :--- | :--- |
| `app/src/main/assets/scriptlets/pip_controller.js` | **Bộ điều khiển PiP độc lập:** Thực hiện cô lập DOM (gắn `[data-brave-pip-role]`), ẩn tất cả phần tử thừa khác video trong `<body>`, đưa video lên lớp trên cùng phủ 100vw x 100vh, đồng thời điều khiển gọi hàm play/pause trực tiếp thông qua native media elements. |
| `app/src/main/assets/scriptlets/youtube_defuser.js` | **Bộ bẻ khóa và bảo vệ phát nhạc:** Proxy `JSON.parse` và `window.fetch` để gọt sạch quảng cáo InnerTube API; giám sát và tua quảng cáo ghép luồng (SSAI) với tốc độ 16x; chặn sự kiện `visibilitychange` và ghi đè `document.hidden = false`; đồng bộ trạng thái phát về `AndroidBridge`. |
| `app/src/main/assets/filters/blocked_domains.txt` | File văn bản chứa danh sách các máy chủ quảng cáo và telemetry (Doubleclick, Google Adservices, Google Analytics, YouTube log events...). |
| `app/src/main/assets/filters/cosmetic_rules.css` | File định dạng CSS ẩn các thẻ quảng cáo, thẻ tài trợ, nút tải ứng dụng YouTube và loại bỏ hiệu ứng mờ nền (`backdrop-filter`) để cuộn mượt hơn. |

---

### D. Nhóm Giao Diện & Tài Nguyên Native (XML Resources)

| Đường dẫn file | Vai trò & Trách nhiệm |
| :--- | :--- |
| `app/src/main/AndroidManifest.xml` | Khai báo các quyền hệ thống (`INTERNET`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`), cấu hình `MainActivity` hỗ trợ `supportsPictureInPicture="true"`, bắt Deep Link các URL YouTube (`www.youtube.com`, `m.youtube.com`, `youtu.be`), và đăng ký `MediaService`. |
| `app/src/main/res/layout/activity_main.xml` | Bố cục FrameLayout gồm: `KeepAliveWebView` hiển thị web, `ProgressBar` hiển thị tiến trình tải 3dp màu đỏ, và `customViewContainer` để hiển thị video full-screen khi người dùng bấm phóng to. |
| `app/src/main/res/values/themes.xml` | Định nghĩa theme `Theme.YouTubeLite` với thanh trạng thái và màu nền đen hoàn toàn (`#FF0F0F0F`). |

---

### E. Thư Mục Độc Lập `adblock-rust/`
- Thư mục này là mã nguồn của dự án engine chặn quảng cáo bằng ngôn ngữ Rust của Brave Software.
- **Trạng thái thực tế:** Thư mục này hiện tại nằm độc lập trong workspace và **chưa được biên dịch vào mã máy của file APK**. Ứng dụng hiện đang chạy ổn định nhờ engine Kotlin native `AdBlockEngine.kt`.
