# Supabase local và migration ứng dụng

Thư mục này cấu hình dịch vụ Supabase local (PostgreSQL, Auth, Storage). **Flyway là công cụ duy nhất quản lý schema ứng dụng `se214`.**

- Migration SQL: [`../backend/src/main/resources/db/migration/`](../backend/src/main/resources/db/migration/).
- Cách chạy, thêm phiên bản và kiểm thử: [runbook database](../docs/database/README.md).
- Vai trò migrator/runtime: [hướng dẫn phân quyền](../backend/scripts/database/README.md).

Trong `config.toml`, `db.migrations.enabled=false`, `db.seed.enabled=false` và `sql_paths=[]`. Không đặt migration ứng dụng vào `supabase/migrations/` hoặc dùng `supabase db push` để triển khai bảng ứng dụng. Không dùng `db reset` trên database đang giữ dữ liệu; thao tác reset local cũng có thể xóa schema do Flyway quản lý.

Có thể dùng `npx supabase start`, `status`, `stop` để quản lý dịch vụ local. Sau khi PostgreSQL sẵn sàng, cấu hình kết nối JDBC trực tiếp cho Flyway và chạy theo runbook. Backend kết nối PostgreSQL bằng tài khoản SQL; Supabase Auth/Storage vẫn do provider quản lý, migration không tạo/sửa các bảng của họ.

`seed.sql` chưa được bật tự động. Khi bổ sung fixture, chạy riêng trên local/test sau migration; danh tính phải được tạo bằng Supabase Auth, không chèn trực tiếp vào `auth.users` hoặc tự coi UUID bất kỳ là user hợp lệ. Metadata ảnh trong PostgreSQL không thay thế object trong Storage.

Cấu hình SMTP, callback email và luồng Auth/Storage cần kiểm thử tích hợp riêng; migration thành công không xác nhận các dịch vụ đó đã hoàn tất cấu hình.
