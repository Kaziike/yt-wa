# Hướng Dẫn Scriptlets & Bộ Lọc Web (Scriptlets & Filters Guide)

Tài liệu này phân tích chi tiết cơ chế hoạt động của các thành phần Web Shields nằm trong thư mục `app/src/main/assets/`.

---

## 1. `pip_controller.js` (Bộ Điều Khiển PiP Độc Lập)

- **Vị trí file:** `app/src/main/assets/scriptlets/pip_controller.js`
- **Mục tiêu:** Cô lập tuyệt đối thẻ video trong DOM khi chuyển sang cửa sổ PiP, loại bỏ 100% các thành phần rác (header, nút mở app, gợi ý, danh sách phát) và cung cấp API điều khiển Play/Pause không phụ thuộc vào YouTube.

### A. Cơ chế Cô Lập DOM (DOM Isolation)
Khi kích hoạt PiP (`enter()`), script gán các thuộc tính đặc biệt vào DOM:
1. Thẻ `<html>`: Nhận thuộc tính `data-brave-pip-mode="on"`.
2. Thẻ `<video>` đang phát: Nhận thuộc tính `data-brave-pip-role="video"`.
3. Toàn bộ chuỗi thẻ cha từ `<video>` lên tới `<body>`: Nhận `data-brave-pip-role="ancestor"`.

Luật CSS cô lập được áp dụng:
```css
/* 1. Ẩn toàn bộ các thẻ KHÔNG có thuộc tính role */
html[data-brave-pip-mode] body *:not([data-brave-pip-role]) {
    display: none !important;
    visibility: hidden !important;
    opacity: 0 !important;
    pointer-events: none !important;
}

/* 2. Đưa chuỗi thẻ cha thành trong suốt, phủ kín màn hình và gỡ bỏ containment */
html[data-brave-pip-mode] [data-brave-pip-role="ancestor"] {
    display: block !important;
    visibility: visible !important;
    position: fixed !important;
    top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important;
    width: 100vw !important; height: 100vh !important;
    background: transparent !important;
    transform: none !important;
    filter: none !important;
    contain: none !important;
}

/* 3. Thẻ video chính chiếm trọn vẹn 100% khung hình */
html[data-brave-pip-mode] video[data-brave-pip-role="video"] {
    display: block !important;
    position: fixed !important;
    top: 0 !important; left: 0 !important; right: 0 !important; bottom: 0 !important;
    width: 100vw !important; height: 100vh !important;
    object-fit: contain !important;
    background: #000 !important;
    z-index: 2147483647 !important;
}
```

### B. Lựa chọn Video Thông Minh (`mainVideo`)
Nếu trên trang có nhiều thẻ `<video>` (ví dụ thumbnail preview hoặc shorts), thuật toán lựa chọn:
1. Lọc danh sách video đang phát (`!paused && !ended`).
2. Tính điểm số kích thước: `score = (videoWidth * videoHeight) || (offsetWidth * offsetHeight)`.
3. Chọn video có điểm số cao nhất.

### C. Điều khiển Play / Pause Native
YouTube có cơ chế tự động tạm dừng khi kích thước người xem quá nhỏ (`PLAYER_VIEW_TOO_SMALL`). `pip_controller.js` giải quyết bằng cách lưu con trỏ hàm gốc của trình duyệt:
```javascript
window.__braveNativePlay = HTMLMediaElement.prototype.play;
window.__braveNativePause = HTMLMediaElement.prototype.pause;
```
Khi người dùng bấm Play/Pause từ PiP, script gọi trực tiếp hàm native này, vượt qua hoàn toàn các hàm bẫy của YouTube.

---

## 2. `youtube_defuser.js` (Bẻ Khóa Quảng Cáo & Chống Tự Dừng)

- **Vị trí file:** `app/src/main/assets/scriptlets/youtube_defuser.js`
- **Mục tiêu:** Xử lý triệt để quảng cáo InnerTube API, tua quảng cáo ghép luồng SSAI và duy trì âm thanh khi ẩn trang.

### A. Tẩy sạch dữ liệu quảng cáo InnerTube API
1. **Gọt sạch JSON Response:** Hàm `sanitizePlayerObj(obj)` loại bỏ các thuộc tính chứa thông tin quảng cáo:
   - `adPlacements`, `adSlots`, `playerAds`, `adBreakHeartbeatParams`.
2. **Ghi đè `JSON.parse`:** Bất kỳ chuỗi JSON nào được ứng dụng web parse đều đi qua bộ lọc làm sạch trước khi trả về.
3. **Proxy `window.ytInitialPlayerResponse`:** Thiết lập getter/setter để đảm bảo dữ liệu khởi tạo video luôn ở trạng thái sạch.
4. **Proxy `window.fetch`:** Chặn các request tới `/youtubei/v1/player`, clone response và thay thế body bằng JSON đã được tẩy sạch quảng cáo.

### B. Khắc chế quảng cáo ghép luồng (SSAI - Server-Side Ad Insertion)
Một số quảng cáo được YouTube mã hóa trực tiếp vào luồng video gốc. Bộ defuser chạy một chu kỳ giám sát 150ms:
```javascript
const adShowingElement = document.querySelector('.ad-showing, .ad-interrupting, .video-ads, [class*="ytp-ad-player-overlay"]');
if (adShowingElement && video) {
    video.muted = true;
    video.playbackRate = 16.0; // Tua tốc độ tối đa
    if (isFinite(video.duration) && video.duration > 0) {
        video.currentTime = video.duration; // Nhảy ngay đến cuối
    }
    // Tự động nhấn tất cả nút Bỏ qua quảng cáo
    document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern').forEach(btn => btn.click());
}
```

### C. Bẻ khóa Page Visibility API & Lệnh Pause ngầm
- Chặn sự kiện: Ngăn các sự kiện `visibilitychange`, `webkitvisibilitychange`, `pagehide` tiếp cận mã JavaScript của YouTube bằng `e.stopImmediatePropagation()`.
- Giả lập trạng thái hiển thị:
  ```javascript
  Object.defineProperty(document, 'hidden', { get: () => false });
  Object.defineProperty(document, 'visibilityState', { get: () => 'visible' });
  ```
- Kiểm soát lệnh Pause: Ghi đè `HTMLMediaElement.prototype.pause`. Nếu lệnh pause không xuất phát từ việc người dùng chủ động chạm vào nút tạm dừng (`__isUserExplicitPause`), lệnh pause sẽ bị hủy bỏ và ghi log:
  `[BraveShield] Đã chặn lệnh tự dừng video do kích thước nhỏ hoặc chạy nền!`

---

## 3. `cosmetic_rules.css` (Làm Sạch Giao Diện)

- **Vị trí file:** `app/src/main/assets/filters/cosmetic_rules.css`
- **Mục tiêu:** Ẩn toàn bộ các thành phần quảng cáo dạng thẻ hiển thị, banner ứng dụng và tối ưu độ mượt.

### Nhóm luật chính:
1. **Banner và Thẻ tài trợ:**
   - `.ytm-promoted-sparkles-web-renderer`
   - `.ytm-companion-ad-renderer`
   - `ytm-promoted-video-renderer`
   - `div[class*="ad-display"]`, `.ytp-ad-overlay-container`
2. **Thông báo ép tải App YouTube:**
   - `.mealbar-promo-renderer`
   - `.open-in-app-banner`
   - `ytm-pivot-bar-item-renderer[aria-label*="Get app"]`
   - `ytm-pivot-bar-item-renderer[aria-label*="Tải ứng dụng"]`
   - `ytm-upsell-dialog-renderer`
3. **Tối ưu hóa cuộn trang:**
   - Tắt `backdrop-filter: none !important` trên toàn bộ phần tử để giảm tải GPU khi cuộn danh sách video.

### Kỹ thuật tiêm CSS vượt rào cản Trusted Types CSP
YouTube áp dụng chính sách Content Security Policy (CSP) nghiêm ngặt ngăn cản việc gán chuỗi trực tiếp vào `innerHTML`. `AppWebViewClient.kt` khắc phục bằng cách:
1. Đọc nội dung CSS từ assets và mã hóa thành chuỗi Base64.
2. Tiêm script giải mã Base64 thành byte array và gán qua `style.textContent`:
   ```javascript
   style.textContent = (window.TextDecoder ? new TextDecoder('utf-8').decode(bytes) : bin);
   ```
   Thao tác này hoàn toàn hợp lệ theo chuẩn Trusted Types và không bao giờ bị trình duyệt từ chối.

---

## 4. `blocked_domains.txt` (Danh Sách Đen Domain)

- **Vị trí file:** `app/src/main/assets/filters/blocked_domains.txt`
- **Cấu trúc:** Mỗi dòng là một tên miền hoặc endpoint cần chặn. Hỗ trợ chú thích bằng ký tự `#` ở đầu dòng.
- **Danh sách tiêu biểu:**
  - `googleads.g.doubleclick.net`
  - `pagead2.googlesyndication.com`
  - `securepubads.g.doubleclick.net`
  - `www.google-analytics.com`
  - `youtube.com/api/stats/ads`
  - `youtube.com/api/stats/qoe`
  - `youtube.com/youtubei/v1/log_event`
  - `video-ad-stats.googlevideo.com`
