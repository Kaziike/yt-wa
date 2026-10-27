# Tài Liệu Kỹ Thuật YouTube Lite (Brave Shields Architecture)

Chào mừng bạn đến với hệ thống tài liệu kỹ thuật toàn diện của dự án **YouTube Lite**. Tài liệu này được biên soạn cho các kỹ sư phần mềm, kiến trúc sư Android và AI Agent nhằm hiểu rõ toàn bộ kiến trúc, luồng hoạt động, logic mã nguồn và quy trình bảo trì ứng dụng mà không cần phải phân tích lại từ đầu.

---

## 📌 Bản Đồ Tài Liệu (Document Map)

| Tài liệu | Mô tả nội dung chính |
| :--- | :--- |
| **[PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md)** | Giới thiệu dự án, mục tiêu kiến trúc Brave Shields 3 tầng, công nghệ sử dụng, cấu hình build và trạng thái phát triển. |
| **[ARCHITECTURE.md](./ARCHITECTURE.md)** | Kiến trúc tổng thể, mô hình kết hợp Native (Kotlin) + Web Engine (Chromium WebView), cơ chế phòng thủ 3 lớp và sơ đồ kiến trúc Mermaid. |
| **[FILE_STRUCTURE.md](./FILE_STRUCTURE.md)** | Bản đồ cây thư mục chi tiết, giải thích vai trò của từng file mã nguồn, file cấu hình, assets và tài nguyên giao diện. |
| **[FEATURES.md](./FEATURES.md)** | Bảng tổng hợp tính năng chi tiết: Trạng thái triển khai, file phụ thuộc, dữ liệu I/O, điều kiện kích hoạt và lưu ý kỹ thuật. |
| **[DATA_FLOW.md](./DATA_FLOW.md)** | Các luồng dữ liệu quan trọng: Luồng khởi động, luồng IPC (JavaScript $\leftrightarrow$ Kotlin), luồng chặn mạng $O(1)$, luồng chuyển đổi PiP và phát âm thanh nền. |
| **[KOTLIN_ANDROID_GUIDE.md](./KOTLIN_ANDROID_GUIDE.md)** | Phân tích chi tiết các lớp Kotlin Native: `MainActivity`, `KeepAliveWebView`, `MediaService`, `AdBlockEngine`, `AppWebViewClient`. |
| **[SCRIPTLETS_AND_FILTERS_GUIDE.md](./SCRIPTLETS_AND_FILTERS_GUIDE.md)** | Phân tích chi tiết các Scriptlet và Bộ lọc: `pip_controller.js`, `youtube_defuser.js`, `cosmetic_rules.css`, `blocked_domains.txt`. |
| **[API_AND_STORAGE.md](./API_AND_STORAGE.md)** | Giao thức truyền thông IPC (`AndroidBridge`), Broadcast Intents, Service Actions, cấu hình bộ nhớ đệm và can thiệp InnerTube API. |
| **[DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md)** | Hướng dẫn môi trường, quy trình build APK (Local CLI & GitHub Actions CI/CD), quy tắc thêm bộ lọc và debug qua `chrome://inspect`. |
| **[TROUBLESHOOTING.md](./TROUBLESHOOTING.md)** | Tổng hợp các lỗi thực tế đã gặp trong quá trình phát triển (AAPT linking, PiP màn hình đen, Android 14 `RECEIVER_EXPORTED`, Trusted Types CSP) và cách xử lý. |
| **[TECH_DEBT.md](./TECH_DEBT.md)** | Báo cáo nợ kỹ thuật, đánh giá rủi ro, hiện trạng module độc lập `adblock-rust` và lộ trình nâng cấp đề xuất. |
| **[DOCUMENTATION_RULES.md](./DOCUMENTATION_RULES.md)** | Quy tắc cập nhật tài liệu khi có thay đổi mã nguồn trong tương lai để tránh tài liệu bị lỗi thời. |

---

## ⚡ Hướng Dẫn Nhanh Cho Lập Trình Viên Mới

1. **Hiểu Kiến Trúc Nhanh**: Đọc [PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md) $\rightarrow$ [ARCHITECTURE.md](./ARCHITECTURE.md).
2. **Khám Phá Mã Nguồn**:
   - Tầng Native Android: Xem [KOTLIN_ANDROID_GUIDE.md](./KOTLIN_ANDROID_GUIDE.md).
   - Tầng Web Shields & PiP: Xem [SCRIPTLETS_AND_FILTERS_GUIDE.md](./SCRIPTLETS_AND_FILTERS_GUIDE.md).
3. **Debug & Chạy Ứng Dụng**: Xem [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md).

---

## 🔒 Quy Định Bảo Vệ Toàn Vẹn Mã Nguồn
- Tài liệu này chỉ được cập nhật khi có sự thay đổi trực tiếp trong mã nguồn.
- Không tự ý chỉnh sửa mã nguồn ứng dụng khi chỉ thực hiện tác vụ tài liệu hóa.
- Mọi thắc mắc và đóng góp tuân theo [DOCUMENTATION_RULES.md](./DOCUMENTATION_RULES.md).
