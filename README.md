# YouTube Lite (Brave Shields Architecture)

Ứng dụng Android chạy YouTube siêu nhẹ, tối ưu tài nguyên và sạch bóng quảng cáo theo kiến trúc 3 tầng của **Brave Browser**.

---

## 🚀 Tính năng nổi bật

- **Tối ưu tài nguyên:** Sử dụng WebView phần cứng với cấu hình chặn ngoài màn hình (`offscreenPreRaster = false`), RAM tiêu thụ chỉ khoảng ~100MB - 140MB.
- **Chặn quảng cáo tầng mạng (Native):** Sử dụng `AdBlockEngine` đối chiếu Domain/Endpoint trong $O(1)$ tại `shouldInterceptRequest`.
- **Khắc chế SSAI (Server-Side Ad Insertion):** Tự động phát hiện quảng cáo ghép luồng, bật cơ chế **Tua siêu tốc 16x + Tắt tiếng + Tự động Skip** chỉ trong 0.2s.
- **Cosmetic Filtering:** Ẩn sạch các banner quảng cáo, thông báo làm phiền "Tải app YouTube", và tối ưu giao diện m.youtube.com.
- **Hỗ trợ Native UX:** Fullscreen xoay ngang tự động, Picture-in-Picture (PiP), và không ngắt âm thanh khi tắt màn hình (Background Playback).

---

## 🛠️ Cách Build ra file APK

### Cách 1: Dùng GitHub Actions (Không cần cài bất kỳ phần mềm nào trên máy)
1. Tạo một repository mới trên GitHub (public hoặc private).
2. Đẩy toàn bộ thư mục này lên GitHub:
   ```bash
   git add .
   git commit -m "Initial YouTubeLite commit"
   git remote add origin https://github.com/TÊN_BẠN/TÊN_REPO.git
   git branch -M main
   git push -u origin main
   ```
3. Vào tab **Actions** trên GitHub, bạn sẽ thấy tiến trình **Build YouTube Lite APK** tự động chạy.
4. Sau khoảng 2 phút, bấm vào tiến trình đó và tải file `.apk` trong mục **Artifacts** về cài vào điện thoại!

---

### Cách 2: Tự build trên máy tính (Bằng Command-line)
Yêu cầu đã cài đặt Java 17 và Android SDK:
```powershell
.\gradlew.bat assembleDebug
```
File APK sẽ xuất hiện tại:
`app/build/outputs/apk/debug/app-debug.apk`

---

### Cách 3: Mở bằng Android Studio
Mở thư mục này trực tiếp bằng Android Studio và bấm nút **Run** (màu xanh lá) để chạy trên máy ảo hoặc thiết bị thật.
