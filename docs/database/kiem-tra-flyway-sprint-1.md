# Kết quả kiểm tra migration Sprint 1 — 14/11/2026

Thực hiện trên thay đổi của nhánh `sprint1/api_documents`, nền commit `0bc5c5d`. PostgreSQL 17.11 chạy trong container local riêng; không kết nối hoặc áp dụng migration vào Supabase cloud.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| Flyway Maven `migrate`, `validate`, `info` | Thành công; V1 tạo 12 bảng nghiệp vụ và bảng lịch sử riêng |
| Maven `verify` | **21/21 test pass**, không lỗi hoặc bỏ qua; gồm 11 test migration và 10 test backend hiện có |
| Dựng schema trống và chạy lại | Đủ bảng; lần chạy lại không thực thi V1 lần nữa, dữ liệu được giữ |
| Nâng cấp V2 thử nghiệm | Thành công và giữ dữ liệu V1; V2 chỉ nằm trong thư mục tạm của test |
| Migration V2 lỗi giữa chừng | Rollback cả DDL và dữ liệu; không ghi V2 thành công |
| Sửa nội dung migration đã áp dụng | Flyway từ chối vì checksum không khớp |
| Ràng buộc PostgreSQL | Kiểm UNIQUE tài khoản, trạng thái/lease phiên, FK ghép phiên bản đúng tin, giá/diện tích, ảnh, hạn mức và yêu thích |
| Chạy lại bootstrap/cấp quyền | Thành công; schema và bảng thuộc `se214_migrator` |
| Quyền runtime | 7 kiểm tra được phép và 14 kiểm tra bị từ chối đều đúng; SQLSTATE `42501` ở các thao tác bị cấm |
| Khởi động JAR bằng `se214_runtime` | Thành công khi Flyway đã migrate riêng; health `UP`, `/api/hello` trả HTTP 200 |

Bằng chứng tổng hợp: [Maven](evidence/maven-summary.json), [phân quyền](evidence/permissions-summary.json), [khởi động runtime](evidence/runtime-summary.json). CI đã được cấu hình PostgreSQL 17 và lưu Surefire reports; kết quả ở đây là chạy local, chưa phải kết quả GitHub Actions.

## Phạm vi kết luận

- Migration và cấu hình chạy được trên PostgreSQL thật. DDL giữ nguyên thiết kế đã duyệt; Flyway quản lý schema/transaction thay `BEGIN`, `CREATE SCHEMA`, `COMMIT` của SQL tham khảo.
- Không tạo/sửa dữ liệu trong `auth` hoặc `storage`. Các UUID trong test SQL chỉ là dữ liệu kiểm tra ràng buộc, không phải danh tính Supabase đã xác thực.
- Chưa kiểm thử Supabase Auth/Storage thật, đường truyền triển khai hoặc toàn bộ transaction nghiệp vụ đồng thời.
- `ddl-auto=validate` đã cấu hình nhưng chưa có đầy đủ JPA entities/repositories. Luồng đăng ký vẫn dùng repository RAM; cần task tích hợp JPA để hồ sơ đăng ký được lưu vào bảng vừa tạo.
- Test migration chỉ xóa/tạo lại schema trên DB thử riêng, bắt buộc `ALLOW_TEST_SCHEMA_RESET=true` và tên DB thực tế kết thúc `_test`. Runtime probe đã dừng; container kiểm tra được dọn sau khi hoàn tất.

Hướng dẫn áp dụng vào môi trường nhóm: [runbook database](README.md). V1 đã áp dụng ở môi trường dùng chung thì phải giữ checksum; thay đổi sau dùng V2 trở đi.
