# Quy Tắc Cập Nhật & Duy Trì Tài Liệu (Documentation Rules)

Tài liệu này quy định quy trình bắt buộc dành cho các lập trình viên hoặc AI Agent khi thực hiện bất kỳ thay đổi nào trong mã nguồn dự án **LiteTube**, nhằm đảm bảo tài liệu trong `docs/` luôn phản ánh chính xác trạng thái thực tế.

---

## 1. Nguyên Tắc Cốt Lõi

1. **Mã nguồn là chân lý:** Mọi mô tả trong tài liệu phải được kiểm chứng trực tiếp từ code thực tế, không dựa vào suy đoán.
2. **Cập nhật đồng thời (Single PR/Commit):** Khi code thay đổi chức năng, tài liệu tương ứng trong `docs/` bắt buộc phải được cập nhật trong cùng một lần commit/PR.
3. **Không ghi thông tin bí mật:** Tuyệt đối không đưa API Key, Token, mật khẩu, khóa ký keystore vào tài liệu.
4. **Giữ nguyên thuật ngữ code:** Tên class, tên hàm, ID tài nguyên, biến số phải giữ nguyên định dạng tiếng Anh gốc.

---

## 2. Bảng Ánh Xạ Thay Đổi Code $\rightarrow$ Tài Liệu Cần Cập Nhật

Khi bạn thực hiện chỉnh sửa trong mã nguồn, hãy tra cứu bảng dưới đây để cập nhật đúng file tài liệu:

| Thành phần mã nguồn thay đổi | File tài liệu bắt buộc cập nhật |
| :--- | :--- |
| **Thêm / Sửa / Xóa class Kotlin** (`MainActivity`, `AdBlockEngine`, `MediaService`...) | `docs/KOTLIN_ANDROID_GUIDE.md`<br>`docs/FILE_STRUCTURE.md`<br>`docs/ARCHITECTURE.md` (nếu đổi kiến trúc) |
| **Sửa đổi logic PiP** (`pip_controller.js`, `updatePipParams`, `handlePipPlay`...) | `docs/SCRIPTLETS_AND_FILTERS_GUIDE.md`<br>`docs/KOTLIN_ANDROID_GUIDE.md`<br>`docs/DATA_FLOW.md` |
| **Sửa đổi cơ chế tua SSAI hoặc InnerTube** (`youtube_defuser.js`) | `docs/SCRIPTLETS_AND_FILTERS_GUIDE.md`<br>`docs/FEATURES.md` |
| **Thêm domain hoặc luật CSS** (`blocked_domains.txt`, `cosmetic_rules.css`) | `docs/SCRIPTLETS_AND_FILTERS_GUIDE.md`<br>`docs/FEATURES.md` |
| **Thay đổi quyền hoặc Intent trong `AndroidManifest.xml`** | `docs/KOTLIN_ANDROID_GUIDE.md`<br>`docs/PROJECT_OVERVIEW.md` |
| **Thay đổi dependencies, Gradle, SDK version** (`build.gradle.kts`) | `docs/PROJECT_OVERVIEW.md`<br>`docs/DEVELOPMENT_GUIDE.md` |
| **Phát hiện hoặc giải quyết lỗi mới** | `docs/TROUBLESHOOTING.md` |
| **Thêm tính năng mới hoặc thay đổi trạng thái tính năng** | `docs/FEATURES.md`<br>`docs/PROJECT_OVERVIEW.md` |

---

## 3. Danh Sách Kiểm Tra Sau Khi Cập Nhật (Verification Checklist)

Trước khi hoàn tất công việc, hãy kiểm tra lần lượt các mục sau:
- [ ] Tất cả đường dẫn file trong tài liệu Markdown là đường dẫn tương đối và trỏ chính xác đến file đang tồn tại.
- [ ] Tất cả sơ đồ Mermaid render không bị lỗi cú pháp.
- [ ] Không có file mã nguồn nào bị tự ý sửa đổi ngoài phạm vi cho phép.
- [ ] Bảng tổng hợp tính năng trong `docs/FEATURES.md` đã phản ánh đúng trạng thái mới nhất.
