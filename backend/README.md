# Backend Spring Boot

Java 21, Maven Wrapper, Spring Boot; PostgreSQL và migration Flyway cho schema `se214`.

## Chuẩn bị và chạy

1. Chuẩn bị database theo [runbook Flyway](../docs/database/README.md), tạo role migration/runtime theo [hướng dẫn phân quyền](scripts/database/README.md).
2. Cấu hình biến môi trường dựa trên [.env.example](.env.example). Spring Boot/Maven không tự đọc `.env`. Bắt buộc thiết lập `APP_REPOSITORY_TYPE=postgres` cho môi trường tích hợp, staging và production (do [application.yaml](src/main/resources/application.yaml) mặc định fallback về `in-memory` nếu thiếu biến này).
3. Chạy migration bằng credential migrator, rồi cấp quyền runtime. Nếu đã migrate riêng, đặt `FLYWAY_ENABLED=false` cho ứng dụng.
4. Từ thư mục `backend/`, chạy:

```powershell
.\mvnw.cmd spring-boot:run
```

Chạy JAR đã đóng gói:

```powershell
java -Duser.timezone=UTC -jar target/backend-0.0.1-SNAPSHOT.jar
```

Maven, test JVM và `spring-boot:run` đã đặt UTC. Tháng hạn mức nghiệp vụ vẫn tính bằng `Asia/Ho_Chi_Minh` tại Application. Frontend và `/api` truy cập qua HTTPS cùng nguồn theo [hướng dẫn proxy](../proxy/README.md).

## Kiểm thử

Chuẩn bị PostgreSQL thử riêng với tên database kết thúc `_test`, cấu hình `DB_TEST_URL`, `DB_TEST_USERNAME`, `DB_TEST_PASSWORD`, `ALLOW_TEST_SCHEMA_RESET=true` rồi chạy:

```powershell
.\mvnw.cmd verify
```

Bộ test migration xóa/tạo lại schema `se214` trong database thử; không dùng DB chứa dữ liệu cần giữ. Các test không tự dùng kết nối `DB_URL` của ứng dụng. CI tự dựng PostgreSQL 17 riêng và lưu báo cáo Surefire.

Migration V1 tạo 12 bảng đã thiết kế cho Sprint 1; Hibernate chỉ `validate`. Khi cấu hình `APP_REPOSITORY_TYPE=postgres`, ứng dụng kích hoạt các repository PostgreSQL (`PostgreSqlTaiKhoanRepository`, `PostgreSqlPhienXacThucRepository`, `PostgreSqlAnhTinRepository`, `PostgreSqlTinDangRepository`) và `SupabaseStorageRestClientAdapter`; nếu bỏ trống biến này, hệ thống chạy trên chế độ `in-memory` để thử nghiệm cục bộ. Test PostgreSQL không thay kiểm thử Supabase Auth/Storage thật.
