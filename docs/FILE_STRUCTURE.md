# Cấu Trúc Thư Mục & Phân Tích File (File Structure)

## 1. Sơ Đồ Cây Thư Mục Toàn Dự Án

```
LiteTube/
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
│           │       ├── pip_controller.js     # Scriptlet cô lập video, điều khiển PiP & Micro-Seek
│           │       └── youtube_defuser.js    # Scriptlet bẻ khóa InnerTube API, SSAI 16x, chống dừng nền
│           ├── java/com/brave/LiteTube/
│           │   ├── MainActivity.kt           # Activity điều phối 2 tầng View, MiniPlayer, PiP và IPC
│           │   ├── network/
│           │   │   ├── AdBlockEngine.kt      # Singleton kiểm tra và chặn URL quảng cáo tầng mạng O(1)
│           │   │   ├── AppWebViewClient.kt   # WebViewClient chặn request, tiêm scriptlets và SPA interceptor
│           │   │   └── WebResourceCacheManager.kt # Quản lý bộ nhớ đệm tài nguyên tĩnh (Preload Cache)
│           │   ├── service/
│           │   │   └── MediaService.kt       # Foreground Service duy trì MediaSession, WakeLock và Notification
│           │   ├── settings/
│           │   │   └── AppSettings.kt        # Quản lý cấu hình SharedPreferences và đồng bộ JSON xuống Web
│           │   └── view/
│           │       └── KeepAliveWebView.kt   # Custom WebView giả lập View.VISIBLE chống đình chỉ Chromium
│           └── res/                       # Tài nguyên giao diện Native Android
│               ├── drawable/
│               │   └── ic_chevron_down.xml   # Icon mũi tên chúc xuống cho nút thu nhỏ MiniPlayer
│               ├── layout/
│               │   ├── activity_main.xml     # Bố cục 2 tầng: SwipeRefreshLayout + playerPanel (MiniPlayer)
│               │   └── dialog_settings.xml   # Giao diện BottomSheet Cài đặt ứng dụng
│               ├── values/
│               │   ├── colors.xml            # Bảng màu hệ thống (black, white, red, gray)
│               │   ├── strings.xml           # Chuỗi tài nguyên ứng dụng (app_name, settings strings)
│               │   └── themes.xml            # Theme nền tối không có ActionBar
│               └── mipmap-*/
├── docs/                                  # Hệ thống tài liệu kỹ thuật toàn diện của dự án (Markdown)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties      # Định nghĩa phiên bản Gradle (8.4)
├── .gitignore                             # Cấu hình bỏ qua các file tạm của Git
├── build.gradle.kts                       # Cấu hình build gốc dự án
├── gradle.properties                      # Thuộc tính JVM và AndroidX của Gradle
├── gradlew                                # Script chạy Gradle Wrapper trên Linux/macOS
├── gradlew.bat                            # Script chạy Gradle Wrapper trên Windows
├── README.md                              # Trang giới thiệu sản phẩm (Product Showcase & Guide)
└── settings.gradle.kts                    # Khai báo repository và include module :app
```

---

## 2. Phân Tích Vai Trò Từng File Mã Nguồn

### A. Tầng Native Kotlin (`app/src/main/java/com/brave/LiteTube/`)

| Đường dẫn file | Vai trò & Trách nhiệm chính |
| :--- | :--- |
| `MainActivity.kt` | - Điều phối toàn bộ vòng đời ứng dụng.<br>- Quản lý 2 tầng View độc lập: `webView` (Browse) và `playerPanel` (Player).<br>- Điều khiển chuyển động thu nhỏ/bung to của MiniPlayer (`collapsePlayer`, `expandPlayer`, `closePlayer`).<br>- Xử lý cử chỉ vuốt kéo trên thanh video và miniplayer bar.<br>- Quản lý Picture-in-Picture (PiP) và bắt phím Back thông minh.<br>- Khởi chạy BottomSheet Cài đặt (`showSettingsDialog`). |
| `network/AppWebViewClient.kt` | - Kế thừa `WebViewClient`.<br>- Hỗ trợ cờ phân biệt `isBrowseMode` (Browse View vs Player View).<br>- Tiêm `browseWatchInterceptorJs` trên Browse View để bắt link video và pushState SPA.<br>- Tiêm `selectSiteSettingsScriptletJs` để nhét mục Cài đặt vào trang `select_site`.<br>- Tích hợp `AdBlockEngine` và `WebResourceCacheManager` tại `shouldInterceptRequest`. |
| `network/AdBlockEngine.kt` | - Singleton nạp danh sách `blocked_domains.txt`.<br>- Tra cứu nhanh $O(1)$ bằng `HashSet<String>` để chặn các yêu cầu quảng cáo/telemetry. |
| `network/WebResourceCacheManager.kt` | - Quản lý lưu trữ cục bộ các tài nguyên tĩnh (JS, CSS, Font, SVG).<br>- Tự động chặn và trả dữ liệu từ bộ nhớ đệm tại `shouldInterceptRequest`.<br>- Cung cấp hàm xóa cache và tính dung lượng cache phục vụ menu Cài đặt. |
| `service/MediaService.kt` | - Foreground Service với loại `mediaPlayback`.<br>- Khởi tạo và quản lý `MediaSessionCompat` cho phép điều khiển phát/tạm dừng/chuyển bài/tua từ Lockscreen, Notification, Bluetooth, Smartwatch.<br>- Duy trì CPU WakeLock tối đa 2 giờ khi tắt màn hình. |
| `settings/AppSettings.kt` | - Quản lý đọc/ghi SharedPreferences: AdBlock, Background Play, Auto PiP, Preload Cache.<br>- Đóng gói cấu hình dạng JSON để đồng bộ xuống Web Engine (`syncSettingsToWeb`). |
| `view/KeepAliveWebView.kt` | - Kế thừa `WebView`, ghi đè `dispatchWindowVisibilityChanged` và `onWindowVisibilityChanged` luôn báo cáo `View.VISIBLE` để Chromium không ngắt phát âm thanh khi ẩn app. |

---

### B. Tầng Tài Nguyên & Layout (`app/src/main/res/`)

| Đường dẫn file | Vai trò & Trách nhiệm chính |
| :--- | :--- |
| `layout/activity_main.xml` | - Cấu trúc 2 tầng độc lập:<br>  1. `swipeRefreshLayout`: Bọc `webView` trang chủ, hỗ trợ kéo từ trên xuống để reload.<br>  2. `playerPanel`: Bọc `playerWebView`, nút mũi tên thu nhỏ `btnMinimizePlayer`, vùng kéo cử chỉ `playerDragArea` và thanh `miniPlayerBar`.<br>  3. `customViewContainer`: Hiển thị video Fullscreen khi xoay ngang. |
| `layout/dialog_settings.xml` | - Giao diện BottomSheet Cài đặt Native với các SwitchMaterial trực quan và nút Xóa bộ nhớ đệm (Clear Cache). |
| `drawable/ic_chevron_down.xml` | - Vector icon mũi tên chúc xuống phục vụ nút thu nhỏ MiniPlayer trên góc video. |

---

### C. Tầng Web Shields Scriptlets (`app/src/main/assets/`)

| Đường dẫn file | Vai trò & Trách nhiệm chính |
| :--- | :--- |
| `scriptlets/pip_controller.js` | - Cô lập video cho chế độ PiP bằng thuộc tính `[data-brave-pip-role]`.<br>- Cơ chế bảo vệ phát `__braveProtectPlayUntil` chống lỗi auto-pause.<br>- Cơ chế `triggerMicroSeek` đánh thức bộ render của YouTube khi vào/thoát PiP, triệt tiêu lỗi kẹt nút play/pause. |
| `scriptlets/youtube_defuser.js` | - Gọt sạch JSON API InnerTube (`/youtubei/v1/player`).<br>- Phát hiện quảng cáo ghép luồng SSAI và tua nhanh 16x + Mute + Skip trong 0.2s.<br>- Bẻ khóa Page Visibility API chống dừng video khi ẩn tab. |
| `filters/blocked_domains.txt` | - Danh sách các tên miền và đường dẫn máy chủ quảng cáo/telemetry cần chặn. |
| `filters/cosmetic_rules.css` | - Bộ luật CSS ẩn sạch banner tài trợ, gợi ý tải ứng dụng YouTube và hộp thoại làm phiền. |
