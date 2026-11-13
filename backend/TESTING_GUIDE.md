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
          /          \ Integration DB Tests (Testcontainers PostgreSQL 17) - Locking/ACID
         /------------\
        /              \ Unit Tests (JUnit 5, AssertJ) - Domain Entities & Price Converters
       /----------------\
```

### Các công cụ kiểm thử chuẩn hóa:
- **Unit Testing (Mục 7.1):** JUnit Jupiter 5, AssertJ, Mockito. Chạy kiểm thử logic Domain POJO độc lập, tốc độ siêu tốc (< 100ms).
- **Database Integration Testing (Mục 7.2):** Spring Boot Test, Testcontainers PostgreSQL 17, Flyway Migration. Chạy trực tiếp trên PostgreSQL 17 container thật, tuyệt đối không dùng H2 in-memory nhằm bảo toàn các tính năng khóa dòng (`SELECT ... FOR UPDATE`), số thực chính xác `NUMERIC`, ràng buộc CHECK và giao dịch ACID.
- **API Contract & Security Testing (Mục 7.3):** Spring MockMvc, RestAssured, Bean Validation (`@Valid`). Kiểm tra hợp đồng DTO, mã trạng thái HTTP chuẩn, format lỗi RFC 7807 (`ApiErrorResponse`), CSRF và HTTP headers.
- **Concurrency & Race Condition Testing (Mục 7.5):** Java `ExecutorService` phối hợp `CountDownLatch(1)` chạy trên container PostgreSQL 17 để bắt triệt để các lỗi tranh chấp tài nguyên (đua hạn mức đăng tin TS1-29, 2 KDV duyệt cùng lúc TS1-55).
- **Độ bao phủ mã nguồn (Code Coverage):** JaCoCo Maven Plugin đo lường nhánh nghiệp vụ (Branch Coverage $\ge 90\%$).

---

## 2. YÊU CẦU MÔI TRƯỜNG THỰC THI (ENVIRONMENT PREREQUISITES)

Theo quy định tại Mục 12.1 của `testplan-v1.1.md`:
1. **Java Development Kit (JDK):** Yêu cầu chuẩn **JDK 21 LTS** (`<java.version>21</java.version>`).
   - Nếu máy cài nhiều phiên bản Java (ví dụ mặc định là Java 26), hãy trỏ `JAVA_HOME` về JDK 21 có sẵn trên máy (`C:\Program Files\Java\jdk-21`):
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
2. **Docker Engine:** Docker Desktop v24+ (chỉ bắt buộc khi chạy các test tích hợp CSDL PostgreSQL 17 và Concurrency).
   - Hãy mở ứng dụng Docker Desktop trên Windows và đợi biểu tượng góc dưới chuyển sang màu xanh ("Engine running").
   - Kiểm tra bằng lệnh: `docker ps`.
   - *Lưu ý:* Lệnh chạy Unit Test (`.\mvnw.cmd test -Dtest=*UnitTest`) là POJO thuần túy, **hoạt động ngay mà không cần bật Docker**.
3. **Maven Wrapper:** Đã tích hợp sẵn trong thư mục `backend/` (`./mvnw` trên Git Bash/Linux hoặc `.\mvnw.cmd` trên Windows PowerShell/CMD).

---

## 3. CẤU TRÚC THƯ MỤC BỘ KIỂM THỬ

Toàn bộ mã nguồn kiểm thử được bố trí phân lớp rõ ràng trong thư mục `backend/src/test/`:

```
backend/
├── pom.xml                                      # Quản lý dependencies (Testcontainers, Flyway, JaCoCo, v.v.)
├── TESTING_GUIDE.md                             # Tài liệu hướng dẫn thực thi này
└── src/
    ├── main/
    │   ├── java/com/realestates/backend/
    │   │   ├── domain/model/PriceConverter.java # Domain Service quy đổi 7 đơn vị giá chuẩn hóa
    │   │   └── ...
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/
    │           └── V1__init_schema.sql          # Flyway migration DDL khởi tạo 6 bảng nghiệp vụ
    └── test/
        ├── resources/
        │   └── application-test.yaml            # Profile test cấu hình Flyway & PostgreSQL Testcontainers
        └── java/com/realestates/backend/
            ├── common/
            │   └── AbstractPostgresIntegrationTest.java # Base test class khởi tạo Testcontainers Postgres 17
            ├── unit/
            │   └── PriceConverterUnitTest.java          # Unit test 7 đơn vị giá (TS1-63, TS1-64, BR-03)
            ├── api/
            │   └── ApiContractAndSecurityTest.java      # Test hợp đồng API & format lỗi RFC 7807
            ├── integration/
            │   └── PostgresDatabaseIntegrationTest.java # Test tích hợp Flyway & Constraints trên Postgres 17
            ├── concurrency/
            │   └── ConcurrencyRaceConditionTest.java    # Test đa luồng CountDownLatch đua hạn mức (TS1-29)
            └── HttpsSameOriginAndApiRoutingTests.java   # Test định tuyến HTTPS, Cookie & CORS cùng nguồn
```

---

## 4. HƯỚNG DẪN CÂU LỆNH THỰC THI (TEST EXECUTION COMMANDS)

Mở terminal tại thư mục `backend/` và thực thi các lệnh tương ứng:

### 4.1. Chạy toàn bộ các ca kiểm thử (Full Test Suite)
Lệnh này sẽ biên dịch, chạy toàn bộ Unit test, API test, Database Integration test và Concurrency test:
```powershell
# Trên Windows PowerShell:
.\mvnw.cmd clean test

# Trên Linux / macOS:
./mvnw clean test
```

### 4.2. Chạy nhanh riêng Unit Test (Không cần bật Docker)
Dành cho lập trình viên muốn phản hồi tức thì (feedback loop < 3 giây) khi viết logic Domain/POJO:
```powershell
.\mvnw.cmd test -Dtest=*UnitTest
```

### 4.3. Chạy riêng Database Integration Test trên PostgreSQL 17
Kiểm tra Flyway Migration, khóa dòng và các ràng buộc dữ liệu toàn vẹn:
```powershell
.\mvnw.cmd test -Dtest=PostgresDatabaseIntegrationTest
```

### 4.4. Chạy riêng API Contract & Security Test
Kiểm tra tính tuân thủ mã lỗi JSON theo RFC 7807 và phân quyền truy cập:
```powershell
.\mvnw.cmd test -Dtest=ApiContractAndSecurityTest
```

### 4.5. Chạy kiểm thử đồng thời (Concurrency & Race Condition)
Kích hoạt đa luồng bắn đồng thời để kiểm tra cơ chế chống đua hạn mức đăng tin:
```powershell
.\mvnw.cmd test -Dtest=ConcurrencyRaceConditionTest
```

### 4.6. Xuất báo cáo độ bao phủ mã nguồn (JaCoCo Coverage Report)
```powershell
.\mvnw.cmd clean test jacoco:report
```
- Sau khi chạy xong, mở file báo cáo HTML tại: `backend/target/site/jacoco/index.html`.
- Báo cáo sẽ hiển thị chi tiết độ bao phủ dòng code (Instruction Coverage) và nhánh logic (Branch Coverage).

---

## 5. TÍCH HỢP CI/CD (GITHUB ACTIONS PIPELINE)

Quy trình kiểm thử tự động trên GitHub Actions đã được đồng bộ chuẩn hóa theo Mục 12.4 của `testplan-v1.1.md`.
File cấu hình `.github/workflows/ci.yml` thực thi các bước:
1. Checkout mã nguồn.
2. Thiết lập OpenJDK 21 LTS (`temurin`).
3. Khởi chạy Docker Daemon (có sẵn trên runner `ubuntu-latest`).
4. Chạy lệnh `./mvnw clean test jacoco:report`.
5. Đẩy kết quả báo cáo Surefire & JaCoCo lên artifact để lưu trữ làm bằng chứng môn học SE113.

---

## 6. XỬ LÝ SỰ CỐ THƯỜNG GẶP (TROUBLESHOOTING)

1. **Lỗi `Could not find a valid Docker environment` khi chạy Integration Test:**
   - *Nguyên nhân:* Docker Desktop chưa được bật hoặc Docker daemon chưa sẵn sàng.
   - *Khắc phục:* Mở ứng dụng Docker Desktop trên máy, đảm bảo biểu tượng Docker báo "Engine running", sau đó chạy lại lệnh test.
2. **Lỗi `Address already in use` hoặc xung đột cổng:**
   - Trong `application-test.yaml`, `server.port` đã được cấu hình bằng `0` (Random Port), và Testcontainers tự động gán cổng ngẫu nhiên cho container PostgreSQL, không bị xung đột với cổng `5432` cục bộ.
3. **Cần làm sạch cache khi sửa đổi migration SQL:**
   - Chạy lệnh `.\mvnw.cmd clean test-compile` để làm sạch target và biên dịch lại các tài nguyên mới.
