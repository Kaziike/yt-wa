# Hướng Dẫn Scriptlets & Bộ Lọc Web (Scriptlets & Filters Guide)

Tài liệu này phân tích chi tiết cơ chế hoạt động của các thành phần Web Shields và Scriptlets được nạp vào Chromium Web Engine của ứng dụng **LiteTube**.

---

## 1. `pip_controller.js` (Bộ Điều Khiển PiP & Micro-Seek Re-render Kick)

- **Vị trí file:** `app/src/main/assets/scriptlets/pip_controller.js`
- **Mục tiêu:** Cô lập tuyệt đối thẻ video trong DOM khi chuyển sang cửa sổ PiP, loại bỏ 100% các thành phần rác (header, nút mở app, gợi ý, danh sách phát), đồng thời duy trì phát liên tục không bị kẹt nút Play/Pause khi thoát/vào PiP.

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

### B. Micro-Seek Re-render Kick & Cờ Bảo Vệ Phát
- **`__braveProtectPlayUntil`:** Khi chuyển đổi giữa PiP và ứng dụng đầy đủ, YouTube thường kích hoạt kiểm tra kích thước nhỏ (`PLAYER_VIEW_TOO_SMALL`) làm dừng video ngầm. Cờ thời gian này vô hiệu hóa mọi lệnh auto-pause trong 2.5 - 3.5 giây sau khi chuyển đổi.
- **`triggerMicroSeek()`:**
  ```javascript
  var cur = v.currentTime;
  v.currentTime = cur; // Micro-seek không làm gián đoạn phát
  v.dispatchEvent(new Event('seeking'));
  v.dispatchEvent(new Event('seeked'));
  v.dispatchEvent(new Event('timeupdate'));
  if (player && typeof player.wakeUpControls === 'function') player.wakeUpControls();
  if (player && typeof player.updateDimensions === 'function') player.updateDimensions();
  ```
  Thao tác này đánh thức ngay pipeline render và hiển thị controls của YouTube Player, triệt tiêu hoàn toàn lỗi kẹt nút Play/Pause khi phóng to lại app.

---

## 2. `browseWatchInterceptorJs` (Bộ Chặn SPA Watch Router)

- **Vị trí tích hợp:** Khai báo trực tiếp trong `AppWebViewClient.kt` (chỉ chạy khi `isBrowseMode == true`).
- **Mục tiêu:** Chặn router nội bộ của YouTube Mobile Web (`m.youtube.com`) tự ý điều hướng trang chủ khi người dùng chạm vào video, chuyển URL video sang Native để mở trên `playerPanel`.

### Cơ chế hoạt động:
1. **Lưới 1 - Capture Phase Click Interceptor:**
   Lắng nghe sự kiện `click` trên `document` với `useCapture = true`:
   ```javascript
   var anchor = el.closest ? el.closest('a') : null;
   if (anchor && anchor.href && isWatchUrl(anchor.href)) {
       e.preventDefault();
       e.stopPropagation();
       e.stopImmediatePropagation();
       dispatchWatchUrl(anchor.href);
       return false;
   }
   ```
2. **Lưới 2 - Hook History API:**
   Ghi đè `history.pushState` và `history.replaceState`. Nếu URL chứa `/watch` và không chứa `/shorts/`:
   ```javascript
   if (url && isWatchUrl(String(url))) {
       if (dispatchWatchUrl(String(url))) return;
   }
   ```
3. **Lưới 3 - Lắng nghe sự kiện Polymer `yt-navigate`:** Bắt sự kiện chuyển trang nội bộ của web components.

---

## 3. `selectSiteSettingsScriptletJs` (Tích Hợp Cài Đặt Vào Trang `select_site`)

- **Vị trí tích hợp:** Khai báo trong `AppWebViewClient.kt`.
- **Mục tiêu:** Nhét mục "Cài đặt LiteTube" vào đầu danh sách cài đặt của YouTube tại `https://m.youtube.com/select_site` mà không làm thay đổi thanh công cụ mặc định của YouTube.
- **Cơ chế:**
  - Giám sát DOM bằng `MutationObserver` khi URL khớp `select_site`.
  - Tạo phần tử HTML hàng danh sách có biểu tượng bánh răng SVG và nhãn "Cài đặt LiteTube".
  - Khi người dùng chạm vào, gọi `window.AndroidBridge.openSettings()` để mở `BottomSheetDialog` Native.

---

## 4. `youtube_defuser.js` (Bẻ Khóa Quảng Cáo & Chống Tự Dừng)

- **Vị trí file:** `app/src/main/assets/scriptlets/youtube_defuser.js`
- **Mục tiêu:** Xử lý triệt để quảng cáo InnerTube API, tua quảng cáo ghép luồng SSAI và duy trì âm thanh khi ẩn trang.

### A. Tẩy sạch dữ liệu quảng cáo InnerTube API
1. **Gọt sạch JSON Response:** Hàm `sanitizePlayerObj(obj)` loại bỏ các thuộc tính chứa thông tin quảng cáo:
   - `adPlacements`, `adSlots`, `playerAds`, `adBreakHeartbeatParams`.
2. **Ghi đè `JSON.parse`:** Bất kỳ chuỗi JSON nào được ứng dụng web parse đều đi qua bộ lọc làm sạch trước khi trả về.
3. **Proxy `window.ytInitialPlayerResponse`:** Thiết lập getter/setter để đảm bảo dữ liệu khởi tạo video luôn ở trạng thái sạch.
4. **Proxy `window.fetch`:** Chặn các request tới `/youtubei/v1/player`, clone response và thay thế body bằng JSON đã được tẩy sạch quảng cáo.

### B. Khắc chế quảng cáo ghép luồng (SSAI - Server-Side Ad Insertion)
Vòng lặp giám sát 150ms:
```javascript
const adShowingElement = document.querySelector('.ad-showing, .ad-interrupting, .video-ads, [class*="ytp-ad-player-overlay"]');
if (adShowingElement && video) {
    video.muted = true;
    video.playbackRate = 16.0; // Tua tốc độ tối đa
    if (isFinite(video.duration) && video.duration > 0) {
        video.currentTime = video.duration; // Nhảy ngay đến cuối
    }
    document.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern').forEach(btn => btn.click());
}
```

---

## 5. `cosmetic_rules.css` (Làm Sạch Giao Diện)

- **Vị trí file:** `app/src/main/assets/filters/cosmetic_rules.css`
- **Mục tiêu:** Ẩn toàn bộ các thành phần quảng cáo dạng thẻ hiển thị, banner ứng dụng và tối ưu độ mượt.
- **Tiêm an toàn:** Mã hóa Base64 và gán qua `style.textContent` tuân thủ Content Security Policy (Trusted Types) của YouTube.

---

## 6. `blocked_domains.txt` (Danh Sách Đen Domain)

- **Vị trí file:** `app/src/main/assets/filters/blocked_domains.txt`
- **Cấu trúc:** Mỗi dòng là một tên miền hoặc endpoint cần chặn tầng mạng. Tra cứu trong thời gian $O(1)$ tại `AdBlockEngine.kt`.
