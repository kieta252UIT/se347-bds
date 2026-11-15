# HƯỚNG DẪN THỰC THI BỘ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TESTING GUIDE)
## DỰ ÁN: HỆ THỐNG TÌM KIẾM VÀ GỢI Ý BẤT ĐỘNG SẢN (SE214 / SE113 / SE347)
### Hạng mục: TASK-07 — Thiết lập bộ chạy unit/API/PostgreSQL và hướng dẫn thực thi
**Tài liệu tham chiếu:** `testplan-v1.1.md` (chuẩn IEEE 829-2008)

---

## 1. TỔNG QUAN VÀ CHIẾN LƯỢC KIỂM THỬ

Hệ thống áp dụng mô hình **Kim tự tháp kiểm thử (Test Pyramid)** theo quy chuẩn tại Mục 7 của `testplan-v1.1.md`:

```
               /\
              /  \     E2E / Browser Tests (Playwright) - 11 bước cốt lõi
             /----\
            /      \   API & Contract Tests (MockMvc, RestAssured) - 30+ endpoints
           /--------\
          /          \ Integration DB Tests (PostgreSQL 17 Test Instance) - Locking/ACID
         /------------\
        /              \ Unit Tests (JUnit 5, AssertJ) - Domain Entities & Price Converters
       /----------------\
```

### Các công cụ kiểm thử chuẩn hóa:
- **Unit Testing (Mục 7.1):** JUnit Jupiter 5, AssertJ, Mockito. Chạy kiểm thử logic Domain POJO độc lập, tốc độ siêu tốc (< 100ms).
- **Database Integration Testing (Mục 7.2):** Spring Boot Test, Flyway Migration kết nối trực tiếp đến PostgreSQL 17 test chuyên dụng (thông qua `TestDatabaseSettings.SpringInitializer`), tuyệt đối không dùng H2 in-memory nhằm bảo toàn các tính năng khóa dòng (`SELECT ... FOR UPDATE`), số thực chính xác `NUMERIC`, ràng buộc CHECK và giao dịch ACID.
- **API Contract & Security Testing (Mục 7.3):** Spring MockMvc, RestAssured, Bean Validation (`@Valid`). Kiểm tra hợp đồng DTO, mã trạng thái HTTP chuẩn, format lỗi RFC 7807 (`ApiErrorResponse`), CSRF và HTTP headers.
- **Concurrency & Race Condition Testing (Mục 7.5):** Java `ExecutorService` phối hợp `CountDownLatch(1)` chạy trên PostgreSQL 17 test để bắt triệt để các lỗi tranh chấp tài nguyên (đua hạn mức đăng tin TS1-29, 2 KDV duyệt cùng lúc TS1-55). Sau test tự động dọn dẹp dữ liệu (cleanup) để không tồn đọng dữ liệu rác.
- **Độ bao phủ mã nguồn (Code Coverage):** JaCoCo Maven Plugin đo lường nhánh nghiệp vụ (Branch Coverage $\ge 90\%$).

---

## 2. YÊU CẦU MÔI TRƯỜNG THỰC THI (ENVIRONMENT PREREQUISITES)

Theo quy định tại Mục 12.1 của `testplan-v1.1.md`:
1. **Java Development Kit (JDK):** Yêu cầu chuẩn **JDK 21 LTS** (`<java.version>21</java.version>`).
   - Nếu máy cài nhiều phiên bản Java, hãy trỏ `JAVA_HOME` về JDK 21 có sẵn trên máy (`C:\Program Files\Java\jdk-21`):
     - **Trong Git Bash (MINGW64):**
       ```bash
       export JAVA_HOME="/c/Program Files/Java/jdk-21"
       export PATH="$JAVA_HOME/bin:$PATH"
       java -version
       ```
     - **Trong PowerShell:**
       ```powershell
       $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
       $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
       java -version
       ```
     - **Trong Command Prompt (CMD):**
       ```cmd
       set JAVA_HOME=C:\Program Files\Java\jdk-21
       set PATH=%JAVA_HOME%\bin;%PATH%
       java -version
       ```

2. **Cơ sở dữ liệu PostgreSQL 17 & 4 biến môi trường kiểm thử:**
   Bộ test tích hợp DB sử dụng cơ chế bảo vệ an toàn `TestDatabaseSettings`, yêu cầu kết nối trực tiếp vào một PostgreSQL database riêng biệt phục vụ kiểm thử và **bắt buộc phải khai báo 4 biến môi trường**:
   - `ALLOW_TEST_SCHEMA_RESET=true` *(Bắt buộc gán `true` để xác nhận quyền reset schema `se214` trên DB test)*.
   - `DB_TEST_URL`: JDBC URL trỏ đến DB thử nghiệm, bắt buộc bắt đầu bằng `jdbc:postgresql:` và **tên database thực tế phải có đuôi `_test`** (ví dụ: `jdbc:postgresql://localhost:5432/se214_test`).
   - `DB_TEST_USERNAME`: Tài khoản DB test (ví dụ: `se214_test`).
   - `DB_TEST_PASSWORD`: Mật khẩu DB test (ví dụ: `local-ci-only`).

   > [!TIP]
   > **Khởi chạy nhanh PostgreSQL 17 test bằng Docker (chỉ chạy 1 lần):**
   > ```bash
   > docker run -d --name postgres-test -p 5432:5432 \
   >   -e POSTGRES_DB=se214_test \
   >   -e POSTGRES_USER=se214_test \
   >   -e POSTGRES_PASSWORD=local-ci-only \
   >   postgres:17-alpine
   > ```

   **Thiết lập 4 biến môi trường trong terminal trước khi chạy test:**
   - **Trong PowerShell:**
     ```powershell
     $env:ALLOW_TEST_SCHEMA_RESET = "true"
     $env:DB_TEST_URL = "jdbc:postgresql://localhost:5432/se214_test"
     $env:DB_TEST_USERNAME = "se214_test"
     $env:DB_TEST_PASSWORD = "local-ci-only"
     ```
   - **Trong Command Prompt (CMD):**
     ```cmd
     set ALLOW_TEST_SCHEMA_RESET=true
     set DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test
     set DB_TEST_USERNAME=se214_test
     set DB_TEST_PASSWORD=local-ci-only
     ```
   - **Trong Git Bash / Linux / macOS:**
     ```bash
     export ALLOW_TEST_SCHEMA_RESET="true"
     export DB_TEST_URL="jdbc:postgresql://localhost:5432/se214_test"
     export DB_TEST_USERNAME="se214_test"
     export DB_TEST_PASSWORD="local-ci-only"
     ```

   *Lưu ý:* Lệnh chạy riêng Unit Test (`.\mvnw.cmd test -Dtest=*UnitTest`) là POJO thuần túy, **hoạt động ngay mà không cần bật PostgreSQL hay set biến môi trường**.

3. **Maven Wrapper:** Đã tích hợp sẵn trong thư mục `backend/` (`./mvnw` trên Git Bash/Linux hoặc `.\mvnw.cmd` trên Windows PowerShell/CMD).

---

## 3. CẤU TRÚC THƯ MỤC BỘ KIỂM THỬ

Toàn bộ mã nguồn kiểm thử được bố trí phân lớp rõ ràng trong thư mục `backend/src/test/`:

```
backend/
├── pom.xml                                      # Quản lý dependencies (Flyway, RestAssured, JaCoCo, v.v.)
├── TESTING_GUIDE.md                             # Tài liệu hướng dẫn thực thi này
└── src/
    ├── main/
    │   ├── java/com/realestates/backend/
    │   │   ├── domain/model/PriceConverter.java # Domain Service quy đổi 7 đơn vị giá chuẩn hóa
    │   │   └── ...
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/
    │           ├── V1__init_schema.sql          # Flyway migration DDL khởi tạo schema se214
    │           └── V2__...                      # Bổ sung bảng và ràng buộc toàn vẹn Sprint 1 (12 bảng)
    └── test/
        └── java/com/realestates/backend/
            ├── unit/
            │   └── PriceConverterUnitTest.java          # Unit test 7 đơn vị giá (TS1-63, TS1-64, BR-03)
            ├── api/
            │   └── ApiContractAndSecurityTest.java      # Test hợp đồng API & format lỗi RFC 7807
            ├── database/
            │   ├── TestDatabaseSettings.java            # Cấu hình SpringInitializer & kiểm tra an toàn DB_TEST_*
            │   └── FlywayMigrationTests.java            # Kiểm thử tích hợp Flyway & Migration rollback/baseline
            ├── integration/
            │   └── PostgresDatabaseIntegrationTest.java # Test ràng buộc CHECK, Foreign Key, 12 bảng PostgreSQL 17
            ├── concurrency/
            │   └── ConcurrencyRaceConditionTest.java    # Test đa luồng CountDownLatch đua hạn mức (TS1-29)
            ├── auth/
            │   └── SupabaseAuthResolverTests.java       # Test giải mã token & định danh người dùng
            └── HttpsSameOriginAndApiRoutingTests.java   # Test định tuyến HTTPS, Cookie & CORS cùng nguồn
```

---

## 4. HƯỚNG DẪN CÂU LỆNH THỰC THI (TEST EXECUTION COMMANDS)

> [!IMPORTANT]
> **Điều kiện tiên quyết trước khi chạy Test Suite hoặc các test API / DB / Concurrency:**
> Các lớp kiểm thử Spring context sử dụng `TestDatabaseSettings` kết nối trực tiếp đến PostgreSQL 17 thật và yêu cầu database riêng kết thúc bằng hậu tố `_test`.
>
> 1. **Bật nhanh PostgreSQL 17 test container (nếu chưa có):**
>    ```bash
>    docker run -d --name postgres-test -p 5432:5432 \
>      -e POSTGRES_DB=se214_test \
>      -e POSTGRES_USER=se214_test \
>      -e POSTGRES_PASSWORD=local-ci-only \
>      postgres:17-alpine
>    ```
> 2. **Thiết lập 4 biến môi trường bắt buộc trong terminal hiện tại:**
>    - **PowerShell:**
>      ```powershell
>      $env:ALLOW_TEST_SCHEMA_RESET = "true"
>      $env:DB_TEST_URL = "jdbc:postgresql://localhost:5432/se214_test"
>      $env:DB_TEST_USERNAME = "se214_test"
>      $env:DB_TEST_PASSWORD = "local-ci-only"
>      ```
>    - **Command Prompt (CMD):**
>      ```cmd
>      set ALLOW_TEST_SCHEMA_RESET=true
>      set DB_TEST_URL=jdbc:postgresql://localhost:5432/se214_test
>      set DB_TEST_USERNAME=se214_test
>      set DB_TEST_PASSWORD=local-ci-only
>      ```
>    - **Git Bash / Linux / macOS:**
>      ```bash
>      export ALLOW_TEST_SCHEMA_RESET="true"
>      export DB_TEST_URL="jdbc:postgresql://localhost:5432/se214_test"
>      export DB_TEST_USERNAME="se214_test"
>      export DB_TEST_PASSWORD="local-ci-only"
>      ```
> *(Riêng lệnh chạy Unit Test `.\mvnw.cmd test -Dtest=*UnitTest` là POJO thuần túy, hoạt động ngay mà không cần thiết lập trên).*

Mở terminal tại thư mục `backend/` và thực thi các lệnh tương ứng:

### 4.1. Chạy toàn bộ các ca kiểm thử (Full Test Suite)
Lệnh này sẽ biên dịch, chạy toàn bộ Unit test, API test, Database Integration test và Concurrency test:
```powershell
# Trên Windows PowerShell:
.\mvnw.cmd clean test

# Trên Linux / macOS:
./mvnw clean test
```

### 4.2. Chạy nhanh riêng Unit Test (Không cần DB / không cần set env)
Dành cho lập trình viên muốn phản hồi tức thì (feedback loop < 3 giây) khi viết logic Domain/POJO:
```powershell
.\mvnw.cmd test -Dtest=*UnitTest
```

### 4.3. Chạy riêng Database Integration Test trên PostgreSQL 17
Kiểm tra Flyway Migration 12 bảng, khóa dòng và các ràng buộc dữ liệu toàn vẹn:
```powershell
.\mvnw.cmd test -Dtest=PostgresDatabaseIntegrationTest
```

### 4.4. Chạy riêng API Contract & Security Test
Kiểm tra tính tuân thủ mã lỗi JSON theo RFC 7807 và phân quyền truy cập:
```powershell
.\mvnw.cmd test -Dtest=ApiContractAndSecurityTest
```

### 4.5. Chạy kiểm thử đồng thời (Concurrency & Race Condition)
Kích hoạt đa luồng bắn đồng thời để kiểm tra cơ chế chống đua hạn mức đăng tin (tự động dọn sạch dữ liệu sau test):
```powershell
.\mvnw.cmd test -Dtest=ConcurrencyRaceConditionTest
```

### 4.6. Xuất báo cáo độ bao phủ mã nguồn (JaCoCo Coverage Report)
```powershell
.\mvnw.cmd clean test jacoco:report
```
- Sau khi chạy xong, mở file báo cáo HTML tại: `backend/target/site/jacoco/index.html`.
- Báo cáo sẽ hiển thị chi tiết độ bao phủ dòng code (Instruction Coverage) và nhánh logic (Branch Coverage $\ge 90\%$).

---

## 5. TÍCH HỢP CI/CD (GITHUB ACTIONS PIPELINE)

Quy trình kiểm thử tự động trên GitHub Actions đã được đồng bộ chuẩn hóa theo Mục 12.4 của `testplan-v1.1.md`.
File cấu hình `.github/workflows/backend-ci.yml` thực thi các bước:
1. Checkout mã nguồn.
2. Thiết lập OpenJDK 21 LTS (`temurin`).
3. Khởi chạy GitHub Actions service container `postgres:17` (`POSTGRES_DB: se214_test`, `POSTGRES_USER: se214_test`).
4. Truyền 4 biến môi trường `DB_TEST_URL`, `DB_TEST_USERNAME`, `DB_TEST_PASSWORD`, `ALLOW_TEST_SCHEMA_RESET: 'true'`.
5. Chạy lệnh `./mvnw --batch-mode verify`.
6. Đẩy kết quả báo cáo Surefire lên artifact để lưu trữ làm bằng chứng môn học SE113/SE347.

---

## 6. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

1. **Lỗi `IllegalStateException: Test yêu cầu ALLOW_TEST_SCHEMA_RESET=true trên DB thử riêng.`**
   - *Nguyên nhân:* Biến môi trường `ALLOW_TEST_SCHEMA_RESET` chưa được đặt thành `true`. Đây là chốt an toàn ngăn ngừa bộ test vô tình xóa trắng schema của database phát triển hoặc sản phẩm.
   - *Khắc phục:* Chạy `$env:ALLOW_TEST_SCHEMA_RESET = "true"` (PowerShell) hoặc `set ALLOW_TEST_SCHEMA_RESET=true` (CMD) hoặc `export ALLOW_TEST_SCHEMA_RESET=true` (Bash) trước khi chạy lệnh test.

2. **Lỗi `IllegalStateException: Từ chối chạy test: tên DB thực tế phải kết thúc bằng _test.`**
   - *Nguyên nhân:* Tên database trong `DB_TEST_URL` hoặc database thực tế mà PostgreSQL đang kết nối không có hậu tố `_test`.
   - *Khắc phục:* Đảm bảo tên database kết thúc bằng `_test`, ví dụ `se214_test` hoặc `bds_test`, và URL có dạng `jdbc:postgresql://localhost:5432/se214_test`.

3. **Lỗi `IllegalStateException: Thiếu biến môi trường thử DB_TEST_...`**
   - *Nguyên nhân:* Chưa khai báo đủ một trong các biến: `DB_TEST_URL`, `DB_TEST_USERNAME`, `DB_TEST_PASSWORD`.
   - *Khắc phục:* Kiểm tra lại và gán đủ 3 biến môi trường kết nối CSDL theo hướng dẫn tại Mục 2.

4. **Lỗi `Không kết nối được PostgreSQL thử` hoặc `Connection refused: connect`:**
   - *Nguyên nhân:* PostgreSQL test instance chưa được khởi chạy hoặc sai cổng kết nối `5432`.
   - *Khắc phục:* Kiểm tra container docker test đang chạy (`docker ps`) hoặc khởi chạy lại container theo lệnh tại Mục 2.

5. **Cần làm sạch cache khi sửa đổi migration SQL:**
   - Chạy lệnh `.\mvnw.cmd clean test-compile` để làm sạch thư mục `target` và biên dịch lại các tài nguyên mới.
