# Xử Lý Sự Cố & Lỗi Thực Tế (Troubleshooting)

Tài liệu này tổng hợp toàn bộ các sự cố kỹ thuật thực tế đã được phát hiện, điều tra và giải quyết trong suốt quá trình phát triển mã nguồn của dự án **YouTube Lite**.

---

## 1. Lỗi AAPT: `attribute android:layoutHeight not found`

### Hiện tượng & Nhật ký lỗi
Khi chạy `./gradlew assembleDebug` trên CI hoặc máy trạm (xem chi tiết tại `error.txt` dòng 61-68):
```text
Execution failed for task ':app:processDebugResources'.
> Android resource linking failed
  /layout/activity_main.xml:6: error: attribute android:layoutHeight not found.
  /layout/activity_main.xml:6: error: attribute android:layoutWidth not found.
  error: failed linking file resources.
```

### Nguyên nhân đã xác minh
Trong file XML layout Android, các thuộc tính layout bắt buộc phải viết theo chuẩn **snake_case** (`android:layout_width`, `android:layout_height`). Việc viết nhầm thành định dạng **camelCase** (`android:layoutWidth`, `android:layoutHeight`) khiến trình biên dịch tài nguyên AAPT2 không tìm thấy định nghĩa thuộc tính trong Android SDK.

### Cách khắc phục (Đã sửa tại commit `7153bd7`)
Mở `app/src/main/res/layout/activity_main.xml` và đổi tất cả các thuộc tính layout về đúng chuẩn:
```xml
android:layout_width="match_parent"
android:layout_height="match_parent"
```

---

## 2. PiP Bị Màn Hình Đen Nhưng Vẫn Có Tiếng (Black Screen on PiP)

### Hiện tượng
Khi người dùng chuyển sang chế độ Picture-in-Picture, cửa sổ PiP nổi lên chỉ hiển thị một màu đen kịt, nhưng âm thanh video vẫn phát bình thường. Khi chạm vào mở lại toàn màn hình thì video lại hiển thị bình thường.

### Nguyên nhân đã xác minh
Trong `MainActivity.kt`, nếu đặt `webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)`, WebView sẽ được render vào một bộ đệm ngoài màn hình (offscreen buffer). Vì toàn bộ Activity đã có cờ `android:hardwareAccelerated="true"` trong `AndroidManifest.xml`, việc ép thêm hardware layer ở cấp View khiến bộ điều phối cửa sổ của Android không cập nhật các khung hình mới vào bộ đệm khi kích thước view bị thu nhỏ đột ngột về khung PiP.

### Cách khắc phục
Đặt layer type về chế độ mặc định:
```kotlin
webView.setLayerType(View.LAYER_TYPE_NONE, null)
```

---

## 3. Nút Play/Pause Trên PiP Không Hoạt Động Trên Android 13/14

### Hiện tượng
Cửa sổ PiP hiện ra có nút Play và Pause, nhưng khi người dùng bấm vào các nút này trên điện thoại chạy Android 13 hoặc 14, không có phản hồi nào xảy ra.

### Nguyên nhân đã xác minh
Bắt đầu từ Android 13 (API 33) và bắt buộc trên Android 14 (API 34), khi đăng ký `BroadcastReceiver`, nhà phát triển phải chỉ định cờ `RECEIVER_EXPORTED` hoặc `RECEIVER_NOT_EXPORTED`.
Các nút `RemoteAction` trên khung PiP được phát lệnh từ tiến trình hệ điều hành (`com.android.systemui`). Nếu đăng ký bằng `RECEIVER_NOT_EXPORTED`, hệ thống Android sẽ chặn các broadcast này từ SystemUI để đảm bảo an ninh, khiến receiver của ứng dụng không bao giờ nhận được sự kiện.

### Cách khắc phục
Sử dụng cờ `ContextCompat.RECEIVER_EXPORTED`:
```kotlin
ContextCompat.registerReceiver(
    this,
    mediaActionReceiver,
    filter,
    ContextCompat.RECEIVER_EXPORTED
)
```

---

## 4. Văng Ứng Dụng (Crash) `IllegalArgumentException` Khi Vào PiP

### Hiện tượng
Ứng dụng bị văng đột ngột khi mở một số video định dạng đặc thù (video siêu rộng tỉ lệ 21:9 hoặc video định dạng ngắn YouTube Shorts dọc).

### Nguyên nhân đã xác minh
Hệ điều hành Android quy định nghiêm ngặt: Tỉ lệ khung hình của `PictureInPictureParams.Builder().setAspectRatio(...)` chỉ được phép nằm trong khoảng:
$$\frac{1}{2.39} \le \text{ratio} \le 2.39$$
Nếu truyền một tỉ lệ vượt ra ngoài khoảng này, hàm native của Android sẽ ném `IllegalArgumentException`.

### Cách khắc phục
Tạo hàm kẹp tỉ lệ an toàn:
```kotlin
private fun clampedPipAspectRatio(): Rational {
    val ratio = videoAspectRatio.toFloat()
    if (ratio.isNaN() || ratio <= 0f) return Rational(16, 9)
    val maxRatio = 2.39f
    val minRatio = 1f / 2.39f
    return when {
        ratio > maxRatio -> Rational(239, 100)
        ratio < minRatio -> Rational(100, 239)
        else -> videoAspectRatio
    }
}
```

---

## 5. Vi Phạm Chính Sách Bảo Mật Trusted Types CSP Khi Tiêm CSS

### Hiện tượng
Khi tiêm CSS ẩn quảng cáo vào `m.youtube.com`, trình duyệt báo lỗi console:
`This document requires 'TrustedHTML' assignment.` và từ chối nạp style.

### Nguyên nhân đã xác minh
Trang YouTube Mobile áp dụng chính sách CSP rất ngặt nghèo, chặn hoàn toàn việc gán chuỗi trực tiếp vào `style.innerHTML` hoặc `insertAdjacentHTML`.

### Cách khắc phục
Mã hóa CSS sang chuỗi Base64 và gán vào `style.textContent` thông qua `TextDecoder`:
```javascript
style.textContent = (window.TextDecoder ? new TextDecoder('utf-8').decode(bytes) : bin);
```
Thuộc tính `textContent` không thực thi phân tích cú pháp HTML nên hoàn toàn miễn nhiễm với chính sách TrustedHTML CSP.

---

## 6. YouTube Tự Dừng Video Khi Vào PiP (`PLAYER_VIEW_TOO_SMALL`)

### Hiện tượng
Khi Activity co nhỏ lại vào khung PiP, video chạy được 1 giây rồi tự động bị dừng lại.

### Nguyên nhân đã xác minh
Bộ mã nguồn web của YouTube có logic kiểm tra kích thước của trình phát: Nếu chiều cao hoặc chiều rộng của video nhỏ hơn ngưỡng quy định, script YouTube sẽ phát lệnh `video.pause()`.

### Cách khắc phục
Ghi đè `HTMLMediaElement.prototype.pause`: Chỉ cho phép lệnh dừng video nếu người dùng bấm trực tiếp vào nút tạm dừng hoặc nút Pause trên PiP Native. Mọi lệnh `pause` do YouTube tự gọi khi khung nhìn bị thu nhỏ đều bị scriptlet chặn lại.

---

## 7. Video Không Mở Được Tầng Nổi Vì YouTube SPA Router Bỏ Qua `shouldOverrideUrlLoading`

### Hiện tượng
Khi triển khai tính năng In-App MiniPlayer 2 tầng, người dùng bấm vào bất kỳ video nào trên trang chủ thì video vẫn mở thẳng trong WebView trang chủ, tầng phát nổi `playerPanel` không bao giờ được kích hoạt.

### Nguyên nhân đã xác minh
YouTube Mobile Web là Single Page Application (SPA). Khi người dùng bấm vào một video, router nội bộ của YouTube chặn sự kiện click và gọi `history.pushState(..., '/watch?v=...')`.
Android WebView **không kích hoạt callback `WebViewClient.shouldOverrideUrlLoading`** đối với các thay đổi URL nội bộ do `history.pushState` hoặc script SPA tạo ra.

### Cách khắc phục (Đã sửa tại commit `59747ff`)
Tiêm scriptlet `browseWatchInterceptorJs` vào Browse WebView:
1. Đăng ký listener tại **Capture Phase** (`useCapture = true`) trên `document` để đón đầu mọi click vào thẻ `<a>` chứa `/watch`.
2. Hook cả `history.pushState` và `history.replaceState`.
3. Khi phát hiện URL `/watch` (loại trừ `/shorts/`), gọi `e.preventDefault()`, `e.stopImmediatePropagation()` và chuyển quyền điều khiển sang Native thông qua `window.AndroidBridge.openWatchLayer(url)`.

---

## 8. Kẹt Nút Play/Pause & Ngắt Âm Thanh Khi Thoát PiP Phóng To Lại Ứng Dụng

### Hiện tượng
Khi video đang phát trong cửa sổ PiP, người dùng bấm phóng to trở lại ứng dụng thì nút Play/Pause bị kẹt (không thể bấm được hoặc hiển thị sai trạng thái), đôi khi video bị dừng tiếng cho đến khi người dùng tua video.

### Nguyên nhân đã xác minh
1. Quá trình resize khung nhìn đột ngột từ nhỏ lên toàn màn hình kích hoạt bẫy auto-pause của YouTube.
2. Trình phát Web component của YouTube rơi vào trạng thái ngủ đông controls khi thoát PiP.

### Cách khắc phục (Đã sửa tại `pip_controller.js`)
1. Cấp cờ bảo vệ `__braveProtectPlayUntil = Date.now() + 2500` chặn auto-pause trong 2.5 giây đầu sau khi phóng to.
2. Triển khai cơ chế **Micro-Seek Re-render Kick**: Gán lại `video.currentTime = video.currentTime` kết hợp dispatch sự kiện `seeking`, `seeked`, `timeupdate` và gọi `player.wakeUpControls()`, `player.updateDimensions()` để đánh thức ngay lập tức pipeline hiển thị và điều khiển của YouTube.
