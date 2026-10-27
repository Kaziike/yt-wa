# Hướng Dẫn Chi Tiết Tầng Native Android (Kotlin)

Tài liệu này phân tích chi tiết logic lập trình, kiến trúc hướng đối tượng và các kỹ thuật chuyên sâu được áp dụng trong tầng mã nguồn Native Kotlin của ứng dụng **YouTube Lite**.

---

## 1. `MainActivity.kt` - Trình Điều Phối 2 Tầng View & In-App MiniPlayer

`MainActivity` là lớp trung tâm điều phối toàn bộ vòng đời, quản lý 2 tầng View độc lập, điều khiển chuyển động của MiniPlayer, xử lý Picture-in-Picture (PiP) và các kết nối IPC giữa JavaScript và Native.

### A. Quản Lý Trạng Thái Panel (`PanelState`)
```kotlin
enum class PanelState {
    HIDDEN,     // Chưa mở video nào, playerPanel ẩn hoàn toàn (GONE)
    EXPANDED,   // Đang xem video toàn màn hình, che kín tầng dưới
    COLLAPSED   // Đang thu nhỏ thành MiniPlayer Bar (cao 64dp) ở sát đáy
}
```

### B. Logic Chuyển Đổi Trạng Thái MiniPlayer
1. **`openVideoPlayer(url: String)`:**
   - Kích hoạt khi người dùng bấm vào một video trên Browse View (thông qua `AndroidBridge.openWatchLayer(url)`).
   - Thiết lập `playerPanel.layoutParams.height = MATCH_PARENT` và `gravity = Gravity.TOP`.
   - Hiển thị `playerPanel.visibility = View.VISIBLE`, reset translation Y về 0f.
   - Ẩn `miniPlayerBar`, hiện `btnMinimizePlayer` và `playerDragArea`.
   - Gọi `playerWebView.loadUrl(fullUrl)`.
   - Đặt `panelState = PanelState.EXPANDED`.

2. **`collapsePlayer()`:**
   - Kích hoạt khi bấm nút mũi tên xuống (`btnMinimizePlayer`), vuốt xuống trên `playerDragArea` hoặc bấm nút Back khi đang xem toàn màn hình.
   - Tính toán khoảng cách trượt: `targetY = (mainContainer.height - dpToPx(64f)).coerceAtLeast(0f)`.
   - Animate `playerPanel` trượt xuống `targetY` trong 260ms bằng `DecelerateInterpolator`.
   - **Kỹ thuật giải phóng cảm ứng:** Trong `withEndAction`, thay đổi `layoutParams.height = dpToPx(64f).toInt()` và `gravity = Gravity.BOTTOM`, đồng thời đặt `translationY = 0f` và `setBackgroundColor(Color.TRANSPARENT)`. Nhờ đó, toàn bộ diện tích màn hình phía trên hoàn toàn thuộc về `webView` (Browse View), người dùng lướt trang chủ mà không bị nuốt touch.
   - Animate `playerWebView` co tỉ lệ (`scaleX`, `scaleY`) và dịch chuyển `(translationX = 4dp, translationY = 3dp)` để hình ảnh video lọt vừa khít vào khoảng trống 104dp bên trái của `miniPlayerBar`.

3. **`expandPlayer()`:**
   - Kích hoạt khi chạm vào `miniPlayerBar` hoặc vuốt lên từ thanh MiniPlayer.
   - Trước khi animate, khôi phục `layoutParams.height = MATCH_PARENT`, `gravity = Gravity.TOP`, nhưng gán `translationY = targetY` (vị trí đáy).
   - Animate `playerPanel` trượt ngược lên `0f` và animate `playerWebView` bung to về `scale = 1f`.

4. **`closePlayer()`:**
   - Kích hoạt khi nhấn nút [X] (`btnMiniPlayerClose`) trên thanh MiniPlayer.
   - Animate trượt hẳn xuống dưới mép màn hình, ẩn `playerPanel`, nạp `about:blank` vào `playerWebView`, giải phóng tài nguyên và dừng `MediaService`.

---

## 2. `network/AppWebViewClient.kt` - Tích Hợp Lưới Chặn Mạng & SPA Router

Lớp kế thừa `WebViewClient` chịu trách nhiệm chặn các truy vấn mạng và tiêm các scriptlet phòng thủ.

### A. Phân Tách Hai Chế Độ (`isBrowseMode`)
- `isBrowseMode = true` (Dành cho `webView` - Browse View):
  - Tiêm `browseWatchInterceptorJs` để bắt link video và pushState SPA.
  - Ngăn Browse View tự chuyển trang sang `/watch`.
- `isBrowseMode = false` (Dành cho `playerWebView` - Player View):
  - Không chặn URL `/watch` để cho phép người dùng chuyển tiếp video gợi ý bên dưới trình phát.

### B. Logic Tại `shouldInterceptRequest`
```kotlin
override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
    val url = request?.url.toString()

    // 1. Kiểm tra bộ lọc quảng cáo native O(1)
    if (AdBlockEngine.isBlocked(url)) {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    // 2. Kiểm tra bộ nhớ đệm tài nguyên tĩnh
    val cachedResponse = WebResourceCacheManager.intercept(request)
    if (cachedResponse != null) {
        return cachedResponse
    }

    return super.shouldInterceptRequest(view, request)
}
```

---

## 3. `network/WebResourceCacheManager.kt` - Quản Lý Bộ Nhớ Đệm Tĩnh

Quản lý việc lưu trữ và nạp các tài nguyên tĩnh của YouTube để giảm thời gian tải lặp lại:
- **Tài nguyên hỗ trợ:** Các file `.js` (Polymer, MWeb bundle), `.css`, `.woff2` (Roboto fonts), `.svg` và ảnh tĩnh.
- **Lưu trữ:** Lưu trong thư mục `cache/web_static/` với tên file là mã băm `MD5(url)`.
- **Độ tin cậy:** Kiểm tra tính toàn vẹn của file (dung lượng $>0$), trả về `WebResourceResponse` với mã HTTP 200 và MIME Type phù hợp.
- **Tiện ích hệ thống:** Cung cấp các hàm `getCacheSizeBytes(context)` và `clearCache(context)` được liên kết trực tiếp với menu Cài đặt Native.

---

## 4. `service/MediaService.kt` - Quản Lý MediaSession & Background Audio

Đảm bảo âm thanh phát liên tục khi tắt màn hình điện thoại và kết nối với các thiết bị ngoại vi:
- **Loại Service:** `android:foregroundServiceType="mediaPlayback"`.
- **MediaSessionCompat:**
  - Khởi tạo session token và đăng ký `MediaSessionCompat.Callback()`.
  - Hỗ trợ đầy đủ các lệnh: `onPlay()`, `onPause()`, `onSkipToNext()`, `onSkipToPrevious()`, `onSeekTo(pos)`.
  - Bắn Broadcast Intent tương ứng (`ACTION_MEDIA_PLAY`, `ACTION_MEDIA_PAUSE`, `ACTION_MEDIA_SEEK`...) về `MainActivity` để điều khiển `playerWebView`.
- **NotificationCompat.MediaStyle:**
  - Hiển thị giao diện thông báo chuẩn Android với nút Play/Pause, Next, Previous và thanh tua thời gian thực.
  - Tự động ghim trên màn hình khóa (Lockscreen).
- **PowerManager WakeLock:** Giữ `PARTIAL_WAKE_LOCK` tối đa 2 giờ, ngăn CPU đi vào chế độ ngủ sâu làm nghẽn luồng giải mã âm thanh của WebView.

---

## 5. `settings/AppSettings.kt` - Quản Lý Cấu Hình Ứng Dụng

Đóng gói việc truy xuất `SharedPreferences` cho các tùy chọn:
- `isAdBlockEnabled` (Mặc định: `true`)
- `isBackgroundPlayEnabled` (Mặc định: `true`)
- `isAutoPipEnabled` (Mặc định: `true`)
- `isPreloadCacheEnabled` (Mặc định: `true`)
- **Đồng bộ xuống Web:** Hàm `toJson(context)` đóng gói cài đặt dạng JSON để truyền xuống `window.__bravePip.applySettings()` và `window.__braveSettings`.
