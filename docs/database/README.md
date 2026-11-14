# Migration database Sprint 1

Migration chuẩn nằm ở [backend/src/main/resources/db/migration](../../backend/src/main/resources/db/migration). Dùng **Flyway**, schema ứng dụng `se214`; Hibernate chỉ kiểm tra mapping bằng `ddl-auto=validate`. V1 áp dụng cho database ứng dụng trống, theo Database Design Sprint 1 bản 0.2 đã chốt ngày 13/11/2026.

## 1. Phạm vi V1

[V1__khoi_tao_schema_sprint_1.sql](../../backend/src/main/resources/db/migration/V1__khoi_tao_schema_sprint_1.sql) giữ nguyên DDL đã duyệt. Flyway đảm nhiệm tạo schema, transaction và bảng lịch sử; file SQL không chứa `BEGIN`, `COMMIT` hoặc `CREATE SCHEMA` riêng.

| Nhóm công việc | Bảng | Nội dung chính |
|---|---|---|
| Tài khoản | `tai_khoan` | Hồ sơ, UUID Supabase Auth, vai trò, trạng thái, mốc thu hồi và dấu đồng bộ Auth |
| Phiên | `phien_xac_thuc` | Hash cookie, credential mã hóa, mốc thu hồi lúc cấp, lease refresh và version CAS |
| Tin và phiên bản | `tin_bat_dong_san`, `phien_ban_tin` | Chủ tin/vòng đời tách khỏi nội dung; giữ lịch sử và người duyệt |
| Ảnh | `anh_tin` | Metadata ảnh tạm/đã gắn, thứ tự, MIME, kích thước và object key |
| Hạn mức | `han_muc_thang` | Khóa tài khoản–tháng; bộ đếm từ 0 đến 3 |
| Yêu thích | `yeu_thich_tin` | Khóa tài khoản–tin chống lưu trùng |

Bảy bảng trên đi cùng năm bảng hỗ trợ đã có trong thiết kế Sprint 1: `goi_day_tin`, `su_kien_goi`, `bao_cao_vi_pham`, `yeu_cau_ghi`, `nhat_ky_kiem_toan`. Chúng phục vụ tác động sửa–duyệt–xóa, chống thực thi lặp và truy vết trong cùng transaction. V1 có **12 bảng nghiệp vụ**, ngoài bảng kỹ thuật `flyway_schema_history`; chưa triển khai ví, thanh toán hoặc đầy đủ quy trình báo cáo của sprint sau.

Migration chỉ tạo cấu trúc. Không tạo tài khoản Supabase, không lưu mật khẩu, không thêm trigger vào `auth.users`, không sửa schema `auth`/`storage`, không tạo dữ liệu mẫu hoặc object ảnh.

## 2. Cấu hình kết nối

Cần Java 21 và PostgreSQL 17 dùng cho kiểm thử của dự án. Maven Wrapper có sẵn trong `backend/`.

| Biến môi trường | Mục đích |
|---|---|
| `DB_URL` | JDBC URL PostgreSQL cho kết nối ứng dụng |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản DB chạy ứng dụng |
| `FLYWAY_DB_URL` | JDBC URL cho lệnh Maven Flyway/migration khi khởi động |
| `FLYWAY_DB_USERNAME`, `FLYWAY_DB_PASSWORD` | Tài khoản DB có quyền migration |
| `FLYWAY_ENABLED` | `true` để migrate khi khởi động; `false` khi đã migrate riêng trước khi chạy ứng dụng |

Ví dụ định dạng URL local: `jdbc:postgresql://127.0.0.1:5432/se214`. Địa chỉ, database và credential thực tế lấy từ môi trường được cấp. Biến Supabase API key không thay thế username/password PostgreSQL.

Spring có thể dùng `DB_*` làm giá trị dự phòng cho `FLYWAY_DB_*` khi chạy local bằng một tài khoản có quyền tạo schema. **Maven Flyway yêu cầu đủ ba biến `FLYWAY_DB_*`**. Với môi trường dùng chung, tách quyền migration và runtime theo [hướng dẫn phân quyền](../../backend/scripts/database/README.md).

Xem [backend/.env.example](../../backend/.env.example) để biết tên biến. Spring Boot và Maven không tự nạp file `.env`; phải truyền các biến vào môi trường tiến trình/IDE. Không đưa credential vào SQL, `pom.xml`, Git hoặc lệnh được lưu trong tài liệu.

JVM/Hibernate dùng UTC để kết nối và lưu thời điểm. Use case vẫn chọn tháng nghiệp vụ theo `Asia/Ho_Chi_Minh` trước khi ghi ngày đầu tháng vào `han_muc_thang.thang`.

## 3. Áp dụng lần đầu

1. Kiểm tra đích kết nối. Database ứng dụng phải trống hoặc chưa có schema `se214` chứa dữ liệu; không dùng `baseline` để bỏ qua schema không rõ nguồn gốc.
2. Chuẩn bị role migration/runtime theo [scripts/database/README.md](../../backend/scripts/database/README.md). Role migration cần quyền tạo schema hoặc sở hữu schema `se214`; không cần quyền sửa Auth/Storage của Supabase.
3. Cấp các biến `FLYWAY_DB_*` cho tiến trình rồi chạy từ thư mục `backend/`:

```powershell
.\mvnw.cmd flyway:info
.\mvnw.cmd flyway:migrate
.\mvnw.cmd flyway:validate
.\mvnw.cmd flyway:info
```

Trên Linux/macOS thay `.\mvnw.cmd` bằng `./mvnw`. `info` sau migrate phải ghi V1 thành công; chạy migrate lần nữa không tạo lại bảng hoặc xóa dữ liệu.

4. Chạy script cấp quyền runtime sau khi V1 đã tạo bảng, theo runbook phân quyền. V1 thu hồi quyền schema khỏi `PUBLIC`; dùng role runtime riêng cần được cấp quyền cụ thể trước khi chạy ứng dụng.
5. Đặt `DB_*` của role runtime và `FLYWAY_ENABLED=false`, rồi chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Nếu chọn migrate trong lúc khởi động local, giữ `FLYWAY_ENABLED=true` và cấp `FLYWAY_DB_*` phù hợp. Không dùng `ddl-auto=create/update`, không chạy bản `schema-sprint-1.sql` tham khảo hoặc Supabase CLI migrations song song để quản lý các bảng ứng dụng.

## 4. Ràng buộc và trách nhiệm của backend

V1 bảo vệ PK/FK/UNIQUE/CHECK và các chỉ mục theo thiết kế:

- UUID Supabase và email tài khoản duy nhất; backend chuẩn hóa email trước khi lưu. Cột email không tự biến dữ liệu ghi SQL trực tiếp thành chữ thường.
- Phiên lưu hash cookie và các trường credential mã hóa đồng bộ; lease phải đủ cặp. SQL không tự xác minh token, mã hóa, refresh hoặc thu hồi phiên.
- Hai con trỏ phiên bản hiện tại/đã duyệt dùng FK ghép, bắt buộc phiên bản thuộc đúng tin. FK được hoãn đến commit để tạo tin và bản đầu trong cùng transaction.
- Giá, diện tích dùng `numeric` dương và hữu hạn; đơn vị giá khớp loại giao dịch. Không thêm giới hạn precision/scale nghiệp vụ.
- Ảnh tối đa 10.000.000 byte; vị trí 1–12 duy nhất theo phiên bản. Backend kiểm nội dung ảnh thật, đủ ít nhất một ảnh và chủ ảnh trùng chủ tin.
- Hạn mức có CHECK 0–3 và khóa tài khoản–tháng. Backend phải khóa tài khoản trước khi tạo/cập nhật counter, ghi tin/bản/ảnh/counter/ý định/audit cùng transaction; sửa/xóa không hoàn lượt.
- FK dùng `ON DELETE RESTRICT`; xóa tin nghiệp vụ là đổi trạng thái `da_xoa`. Bỏ yêu thích xóa quan hệ, không xóa tin.
- `yeu_cau_ghi` giữ snapshot trạng thái/version kết quả để phát lại đúng body và ETag cũ. SQL không tự triển khai quy trình retry.

Các luật liên bảng như quyền người thao tác, điều kiện công khai, trạng thái chủ tin hoặc gói đang hiệu lực vẫn phải được kiểm trong use case/query. Migration thành công chưa chứng minh API, phiên, Storage hay toàn bộ mapping JPA hoạt động.

## 5. Kiểm thử và thay đổi về sau

Kết quả kiểm tra local ngày 14/11/2026: [21 test và kiểm tra quyền/runtime](kiem-tra-flyway-sprint-1.md).

Kiểm thử migration chạy trên **database PostgreSQL riêng có tên kết thúc bằng `_test`**. Cấp `DB_TEST_URL`, `DB_TEST_USERNAME`, `DB_TEST_PASSWORD` và `ALLOW_TEST_SCHEMA_RESET=true` cho tiến trình test. Bộ test được phép xóa/tạo lại schema `se214` trong database test đó; không trỏ các biến này vào DB dùng chung hoặc môi trường demo có dữ liệu.

```powershell
.\mvnw.cmd verify
```

Các bằng chứng cần kiểm gồm dựng trống đủ bảng, chạy lại giữ dữ liệu, FK phiên bản đúng/sai tin, các CHECK/UNIQUE và rollback khi migration lỗi. Test database không thay test giao tiếp Supabase thật hoặc kiểm thử transaction nghiệp vụ đồng thời.

Sau khi V1 đã áp dụng ở môi trường dùng chung, giữ nguyên nội dung/checksum. Thay đổi tiếp theo tạo `V2__mo_ta_thay_doi.sql`, rồi kiểm upgrade từ V1 có dữ liệu trên DB test. `clean` và tự baseline được tắt; không dùng `repair` để che sai checksum. Migration PostgreSQL thất bại được rollback trong transaction; xác định và sửa nguyên nhân trước khi chạy lại. Thay đổi đã áp dụng thành công cần migration tiếp theo, không tự rollback bằng xóa schema.
