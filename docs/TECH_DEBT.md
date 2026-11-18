# Báo Cáo Nợ Kỹ Thuật & Trạng Thái Sau Tái Cấu Trúc (Tech Debt)

Tài liệu này ghi nhận kết quả xử lý nợ kỹ thuật sau đợt kiểm toán toàn diện và các hướng nâng cấp mở rộng trong tương lai cho **LiteTube**.

---

## 1. Các Hạng Mục Nợ Kỹ Thuật Đã Giải Quyết Triệt Để

| Hạng mục trước đây | Trạng thái | Kết quả sau tái cấu trúc |
| :--- | :---: | :--- |
| **`MainActivity.kt` God Activity (`1.473` dòng)** | **Đã hoàn tất** | Rút gọn `MainActivity.kt` còn ~260 dòng, dùng `ViewBinding` và tách thành 5 lớp chuyên biệt (`PlayerSheetController`, `PipController`, `FullscreenController`, `PlaybackCoordinator`, `SettingsBottomSheet`). |
| **Thiếu Unit Test cho `AdBlockEngine` & Cache** | **Đã hoàn tất** | Bổ sung `AdBlockEngineTest` và `UrlAndCacheValidationTest` trong `app/src/test/`, chạy tự động trên GitHub Actions CI. |
| **Chuỗi JS/CSS nhúng cứng & 12 cờ global chồng chéo** | **Đã hoàn tất** | Chuyển 100% script/CSS sang `assets/scriptlets/` & `assets/filters/`, hợp nhất thành State Machine `window.__liteTube` và thay các vòng lặp `setInterval` bằng kiến trúc Event-Driven (`MutationObserver` + Media Events). |
| **Lỗi xóa Cache, `domain/path` AdBlock & bảo mật IPC** | **Đã hoàn tất** | Sửa xóa cache bất đồng bộ an toàn, hỗ trợ đối chiếu `domain + path` trong `O(k)` thread-safe, bảo vệ PiP bằng `EXTRA_PIP_TOKEN` và giới hạn origin cho `DOCUMENT_START_SCRIPT`. |

---

## 2. Đề Xuất Mở Rộng Tương Lai (Tùy Chọn P3)

1. **Cập nhật bộ lọc từ xa (Remote Filter Update):** Cho phép tải bản cập nhật có chữ ký của `blocked_domains.txt` và `cosmetic_rules.css` từ kho lưu trữ chính thức mà không cần phát hành lại APK khi YouTube thay đổi cấu trúc DOM.
2. **Tích hợp engine lọc nâng cao (Tùy chọn):** Nếu cần hỗ trợ cú pháp lọc phức tạp của uBlock Origin / EasyList đầy đủ, có thể đóng gói thư viện Rust độc lập qua JNI/UniFFI.
