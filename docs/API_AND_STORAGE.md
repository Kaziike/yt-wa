# Giao Diện Giao Tiếp & Lưu Trữ (API & Storage)

Tài liệu này mô tả giao thức giao tiếp giữa JavaScript và Kotlin Native, cơ chế điều khiển `MediaService` nội bộ, bảo mật `RemoteAction` của cửa sổ PiP và cấu trúc lưu trữ dữ liệu trong **LiteTube**.

---

## 1. Giao Tiếp Hai Chiều JavaScript ↔ Kotlin Native

### A. Chiều Web → Android: `WebBridge` (`window.AndroidBridge`)
Lớp `com.brave.youtubelite.playback.WebBridge` được tách độc lập khỏi `MainActivity` (có phương thức `detach()` gỡ tham chiếu khi hủy) và rút gọn còn đúng **3 phương thức `@JavascriptInterface`**:

| Phương thức | Tham số | Chức năng |
| :--- | :--- | :--- |
| `openWatchLayer(url)` | `url: String` | Nhận URL video từ tầng Browse, xác thực domain YouTube qua `UrlUtils.normalizeWatchUrl()` trước khi mở trên `PlayerSheetController`. |
| `openSettings()` | *(Không có)* | Mở `SettingsBottomSheet` khi người dùng chọn mục *"Cài đặt LiteTube"* tại `m.youtube.com/select_site`. |
| `onMediaState(...)` | `playing: Boolean`, `videoId: String`, `title: String`, `channel: String`, `posMs: Long`, `durMs: Long`, `videoWidth: Int`, `videoHeight: Int` | Cổng đồng bộ duy nhất (đã chống trùng lặp và tiết lưu `>= 950ms` ở `yt_core_bridge.js`), cập nhật đồng thời `PlaybackState`, MiniPlayer, tỷ lệ PiP và `MediaService`. |

### B. Chiều Android → Web: `PlaybackCoordinator.runPipCommand`
Gửi biểu thức gọi hàm gọn nhẹ tới `window.__bravePip` (chỉ nạp lại script từ `WebAssetRepository` nếu trang trả về `'pip:missing'`):
- `enter()` / `exit()` / `enterAudioOnlyFromPip()`
- `play()` / `pause()` / `toggle()`
- `next()` / `previous()` / `seekTo(ms)`
- `setBackground(true|false)` / `resync()`

---

## 2. Cơ Chế Điều Khiển Media & Bảo Mật PiP Actions

### A. Giao tiếp nội bộ `PlaybackCoordinator` ↔ `MediaService`
- Không sử dụng global broadcast `RECEIVER_EXPORTED` cho điều khiển media.
- Các nút trên Notification (`ACTION_NOTIF_PREV`, `ACTION_NOTIF_PLAY`, `ACTION_NOTIF_PAUSE`, `ACTION_NOTIF_NEXT`) sử dụng `PendingIntent.getService(...)` gửi trực tiếp vào `MediaService` (`android:exported="false"`).
- `MediaService` chuyển lệnh về `PlaybackCoordinator` qua `MediaService.CommandListener` trong cùng tiến trình và tự động lắng nghe `AudioManager.ACTION_AUDIO_BECOMING_NOISY` để tạm dừng khi rút tai nghe.

### B. Khóa Phiên Cho Nút Điều Khiển PiP (`PipController`)
Do các `RemoteAction` của cửa sổ PiP được gọi bởi `com.android.systemui`, `PipController` sinh một khóa ngẫu nhiên `pipAuthToken = UUID.randomUUID().toString()` trong bộ nhớ cho mỗi phiên chạy và đính kèm vào `EXTRA_PIP_TOKEN` của 4 action:
- `com.brave.youtubelite.PIP_HEADPHONES`
- `com.brave.youtubelite.PIP_PLAY`
- `com.brave.youtubelite.PIP_PAUSE`
- `com.brave.youtubelite.PIP_NEXT`

Mọi Intent không mang đúng `EXTRA_PIP_TOKEN` đều bị từ chối ngay lập tức.

---

## 3. Cơ Chế Lưu Trữ Dữ Liệu (Storage)

### A. Cấu Hình Ứng Dụng (`SharedPreferences`)
- **Tên bảng lưu trữ:** `youtube_lite_prefs`.
- **Các khóa cấu hình:**
  - `adblock_enabled` (`Boolean`, mặc định `true`)
  - `background_play_enabled` (`Boolean`, mặc định `true`)
  - `auto_pip_enabled` (`Boolean`, mặc định `true`)
  - `preload_cache_enabled` (`Boolean`, mặc định `true`)

### B. Bộ Nhớ Đệm Tài Nguyên Tĩnh (`WebResourceCacheManager`)
- **Tầng RAM:** `LruCache` tối đa `12 MB`.
- **Tầng Đĩa:** `context.cacheDir/yt_static_cache/` (tối đa `50 MB`, cắt tỉa LRU về `40 MB`, thời hạn TTL `7 ngày`).
- **Định danh khóa:** Mã băm `SHA-256` của URL đã chuẩn hóa (`<key>.bin` và `<key>.meta`), ghi an toàn qua file tạm theo từng luồng kèm khóa Single-flight và kiểm tra MIME chống lỗi Captive Portal.
