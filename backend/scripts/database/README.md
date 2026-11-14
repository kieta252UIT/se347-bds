# Phân quyền PostgreSQL cho migration Sprint 1

Hai script này chạy bằng **psql**, không phải migration Flyway. Chúng chỉ tạo role/cấp quyền cho schema ứng dụng `se214`; không sửa `auth`, `storage`, danh tính Supabase hoặc dữ liệu nghiệp vụ.

| Role | Mục đích |
|---|---|
| `se214_migrator` | Sở hữu schema/bảng và chạy Flyway. Có `CONNECT`, `CREATE` trên database đích. |
| `se214_runtime` | Kết nối của ứng dụng. Chỉ có các quyền dữ liệu liệt kê bên dưới, không sở hữu schema/bảng và không có DDL trong `se214`. |

## Trình tự chạy

1. Kết nối database đích bằng tài khoản quản trị có `CREATEROLE` và quyền cấp `CREATE` trên database. Chạy `bootstrap-roles.sql`. Role mới được tạo với `NOLOGIN`, không có mật khẩu trong script. Script có thể chạy lại, giữ credential của role đã tồn tại; dừng nếu phát hiện thuộc tính quản trị hoặc membership ngoài dự kiến.
2. Trong phiên psql quản trị, dùng `\password se214_migrator` và `\password se214_runtime` để đặt mật khẩu qua lời nhắc. Sau đó bật đăng nhập bằng hai lệnh bên dưới. Lưu mật khẩu trong cấu hình bí mật của môi trường; không đưa vào Git, SQL hay lịch sử lệnh.
3. Chạy Flyway bằng credential `se214_migrator` theo [runbook database](../../../docs/database/README.md). Flyway tạo schema `se214` và các bảng dưới quyền sở hữu của role này.
4. **Mở kết nối mới bằng `se214_migrator`** rồi chạy `cap-quyen-runtime.sql`. Không mặc định dùng lại tài khoản đã tạo role: trên PostgreSQL 17/Supabase, quyền quản trị role không đồng nghĩa được kế thừa hoặc `SET ROLE` sang role đó.
5. Cấu hình `DB_USERNAME=se214_runtime`, `DB_PASSWORD` cho ứng dụng. Credential `FLYWAY_DB_USERNAME=se214_migrator` và `FLYWAY_DB_PASSWORD` chỉ dùng ở bước migration. Nếu Spring tự chạy Flyway khi khởi động, tiến trình đó cần cả hai bộ credential; để tách hoàn toàn, chạy Flyway trước rồi đặt `FLYWAY_ENABLED=false` cho tiến trình ứng dụng.

```sql
ALTER ROLE se214_migrator LOGIN;
ALTER ROLE se214_runtime LOGIN;
```

Ví dụ chạy từ thư mục `backend/` trong PowerShell, với URL PostgreSQL **không chứa mật khẩu** đã được cấu hình:

```powershell
psql "$env:DB_ADMIN_URL" -W -f scripts/database/bootstrap-roles.sql
# Chạy Flyway migrate theo runbook, sau khi đã đặt credential cho hai role.
psql "$env:DB_MIGRATOR_URL" -W -f scripts/database/cap-quyen-runtime.sql
```

`DB_ADMIN_URL` và `DB_MIGRATOR_URL` là connection string dành cho psql (`postgresql://...`), khác URL JDBC `jdbc:postgresql://...` dùng bởi Java. Sử dụng địa chỉ kết nối PostgreSQL phù hợp môi trường Supabase; không dùng REST/Data API để chạy migration.

## Quyền runtime sau khi chạy script

| Bảng | Quyền |
|---|---|
| `tai_khoan`, `phien_xac_thuc`, `tin_bat_dong_san`, `phien_ban_tin`, `han_muc_thang` | SELECT, INSERT, UPDATE |
| `anh_tin` | SELECT, INSERT, UPDATE, DELETE; cần DELETE để dọn metadata ảnh tạm |
| `yeu_thich_tin` | SELECT, INSERT, DELETE |
| `goi_day_tin`, `bao_cao_vi_pham` | SELECT, UPDATE; khởi tạo fixture bằng migrator/quản trị |
| `su_kien_goi`, `yeu_cau_ghi`, `nhat_ky_kiem_toan` | SELECT, INSERT; chỉ thêm lịch sử |
| `flyway_schema_history` | Không có quyền |

Không cấp `TRUNCATE`, `REFERENCES`, `TRIGGER`, quyền sequence hoặc quyền tạo đối tượng trong `se214` cho runtime. Các bảng mới ở migration sau cần được bổ sung có chủ ý vào danh sách quyền. Không cấp mặc định mọi quyền trên bảng tương lai.

Hai script dùng transaction và `ON_ERROR_STOP`: lỗi thì không áp dụng một phần. Bootstrap cho phép tài khoản quản trị là thành viên của role vừa tạo (hành vi PostgreSQL 17); nó chỉ cấm **role ứng dụng nhận membership của role khác**. Phân quyền này là ranh giới giữa tiến trình backend và DDL, không thay thế kiểm tra vai trò Người dùng/KDV/Admin, quyền sở hữu tin và trạng thái tại Application.

Kiểm tra riêng việc `se214` không được expose qua Supabase Data API và không có grant cho `anon`/`authenticated` từ cấu hình môi trường khác. Các script không tự thu hồi quyền ngoài phạm vi quản lý của dự án.
