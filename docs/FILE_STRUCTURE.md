# Cấu Trúc Thư Mục & Phân Tích File (File Structure)

## 1. Sơ Đồ Cây Thư Mục Toàn Dự Án

```
LiteTube/
├── .github/
│   └── workflows/
│       └── build-apk.yml                         # CI/CD kiểm tra cú pháp JS, chạy Unit Test & build APK
├── app/
│   ├── build.gradle.kts                          # Cấu hình module Android (SDK 34, ViewBinding, JUnit)
│   ├── proguard-rules.pro                        # Luật R8 giữ gọn riêng WebBridge (@JavascriptInterface)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml               # Khai báo singleTask MainActivity, Deep Links & MediaService
│       │   ├── assets/
│       │   │   ├── filters/
│       │   │   │   ├── blocked_domains.txt       # Danh sách domain và đường dẫn quảng cáo/telemetry
│       │   │   │   ├── cosmetic_rules.css        # Luật CSS ẩn quảng cáo banner & hộp thoại làm phiền
│       │   │   │   └── player_ui.css             # Luật CSS ẩn thanh header web trên tầng Player
│       │   │   └── scriptlets/
│       │   │       ├── yt_core_bridge.js         # State Machine trung tâm, đo khung video & IPC tiết lưu
│       │   │       ├── youtube_defuser.js        # Lọc JSON InnerTube, vượt SSAI event-driven, Auto-Unmute
│       │   │       ├── pip_controller.js         # Cô lập DOM PiP & điều khiển phát/chuyển bài/resync
│       │   │       └── browse_interceptor.js     # Preconnect, chèn mục Cài đặt select_site & chặn SPA /watch
│       │   ├── java/com/brave/youtubelite/
│       │   │   ├── MainActivity.kt               # Điều phối vòng đời Activity, Deep Link & Back Navigation
│       │   │   ├── network/
│       │   │   │   ├── UrlUtils.kt               # Phân tích URL, kiểm tra origin YouTube & giải mã /redirect
│       │   │   │   ├── AdBlockEngine.kt          # Đối chiếu domain + path O(k) với RuleSnapshot bất biến
│       │   │   │   ├── WebAssetRepository.kt     # Nạp 1 lần các file JS/CSS từ assets vào bộ nhớ dùng chung
│       │   │   │   ├── WebResourceCacheManager.kt# Cache tĩnh 2 tầng (LRU + Disk), Gzip, Single-flight, MIME guard
│       │   │   │   └── AppWebViewClient.kt       # Lọc origin an toàn, quản lý ScriptHandler & chặn quảng cáo
│       │   │   ├── playback/
│       │   │   │   ├── PlaybackState.kt          # Data class bất biến lưu trạng thái phát hiện tại
│       │   │   │   ├── WebBridge.kt              # Cầu nối JavascriptInterface độc lập chống rò rỉ Activity
│       │   │   │   └── PlaybackCoordinator.kt    # Điều phối Browse/Player WebView, KeepAlive & MediaService
│       │   │   ├── service/
│       │   │   │   └── MediaService.kt           # Foreground Service, MediaSession, Thumbnail & AudioNoisy
│       │   │   ├── settings/
│       │   │   │   └── AppSettings.kt            # Lưu trữ SharedPreferences & dọn cache bất đồng bộ an toàn
│       │   │   ├── ui/
│       │   │   │   ├── PlayerSheetController.kt  # Quản lý Sliding Sheet, MiniPlayer gestures & xoay màn hình
│       │   │   │   ├── PipController.kt          # Quản lý PiP 3 nút kèm khóa xác thực EXTRA_PIP_TOKEN
│       │   │   │   ├── FullscreenController.kt   # Quản lý toàn màn hình video cho cả 2 tầng WebView
│       │   │   │   └── SettingsBottomSheet.kt    # Hộp thoại cài đặt Material sử dụng ViewBinding
│       │   │   └── view/
│       │   │       └── KeepAliveWebView.kt       # WebView giữ sống nền theo ngữ cảnh & hủy sạch chống leak
│       │   └── res/
│       │       ├── drawable/
│       │       │   ├── bg_bottom_sheet_handle.xml# Thanh kéo cho BottomSheet Cài đặt
│       │       │   ├── bg_cache_card.xml         # Nền khung thông tin bộ nhớ đệm
│       │       │   ├── ic_chevron_down.xml       # Nút thu nhỏ trình phát về MiniPlayer
│       │       │   ├── ic_pip_headphones.xml     # Nút chuyển từ PiP sang chỉ nghe âm thanh nền
│       │       │   ├── ic_stat_litetube.xml      # Icon đơn sắc trên thanh trạng thái cho Media Notification
│       │       │   └── ic_launcher_*.xml         # Adaptive icon của ứng dụng
│       │       ├── layout/
│       │       │   ├── activity_main.xml         # Bố cục 2 tầng (SwipeRefresh Browse + Sliding Player Sheet)
│       │       │   └── dialog_settings.xml       # Giao diện BottomSheet Cài đặt
│       │       └── values/
│       │           ├── colors.xml
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/java/com/brave/youtubelite/network/
│           ├── AdBlockEngineTest.kt              # Unit Test kiểm tra chặn domain, subdomain và domain/path
│           └── UrlAndCacheValidationTest.kt      # Unit Test chuẩn hóa URL, chống XSS/Captive Portal
├── docs/                                         # Hệ thống tài liệu kỹ thuật dự án
├── plan.md                                       # Báo cáo kiểm toán mã nguồn & kế hoạch tái cấu trúc
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```
