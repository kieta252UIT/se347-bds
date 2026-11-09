# Hệ Thống Tìm Kiếm và Gợi Ý Bất Động Sản (Real Estate Finding and Recommendation System)

Dự án website tìm kiếm, tra cứu và gợi ý bất động sản. Được xây dựng theo kiến trúc full-stack hiện đại phục vụ đồ án môn học nhóm gồm 5 thành viên.

## Công Nghệ Sử Dụng (Technology Stack)

| Thành phần | Công nghệ | Phiên bản |
|---|---|---|
| Frontend | React + TypeScript + Vite | React 19, TypeScript 5.8, Vite 7 |
| Backend | Java + Spring Boot + Maven | Java 21, Spring Boot 4.1.2, Maven 3.10 |
| Database | Supabase (PostgreSQL) | PostgreSQL 17 |
| Quản lý mã nguồn | Git + GitHub | — |
| CI (Tích hợp liên tục) | GitHub Actions | — |

---

## Cấu Trúc Thư Mục Dự Án (Repository Structure)

```text
project-root/
├── frontend/                          # Ứng dụng frontend React
│   ├── src/                           # Mã nguồn frontend
│   │   ├── assets/                    # Tài nguyên tĩnh (ảnh, font, icons)
│   │   ├── components/                # Các React component tái sử dụng
│   │   ├── hooks/                     # Custom React hooks
│   │   ├── layouts/                   # Các component bố cục (layout) giao diện
│   │   ├── pages/                     # Các trang ứng dụng (gắn với route)
│   │   ├── services/                  # Xử lý gọi API và tích hợp dịch vụ
│   │   │   └── api.ts                 # Cấu hình HTTP client Axios
│   │   ├── stores/                    # Quản lý state toàn cục với Zustand
│   │   ├── types/                     # Định nghĩa kiểu dữ liệu TypeScript
│   │   ├── utils/                     # Các hàm tiện ích / helper
│   │   ├── App.tsx                    # Component gốc ứng dụng
│   │   ├── App.css                    # CSS giao diện chính
│   │   └── main.tsx                   # Điểm khởi chạy ứng dụng frontend
│   ├── public/                        # File tĩnh public
│   ├── eslint.config.js               # Cấu hình kiểm tra cú pháp ESLint
│   ├── index.html                     # File HTML chính của ứng dụng
│   ├── package.json                   # Quản lý dependencies & scripts npm frontend
│   ├── package-lock.json              # Khóa phiên bản dependencies frontend
│   ├── tsconfig.json                  # Cấu hình TypeScript references
│   ├── tsconfig.app.json              # Cấu hình TypeScript cho mã nguồn app
│   ├── tsconfig.node.json             # Cấu hình TypeScript cho Vite/Node
│   └── vite.config.ts                 # Cấu hình bundler Vite
│
├── backend/                           # Ứng dụng backend Spring Boot
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/realestates/backend/
│   │   │   │   ├── BackendApplication.java      # Điểm khởi chạy Spring Boot
│   │   │   │   └── controller/
│   │   │   │       └── HelloController.java     # REST Controller mẫu
│   │   │   └── resources/
│   │   │       ├── application.yaml              # Cấu hình Spring Boot
│   │   │       ├── static/                       # Tài nguyên web tĩnh
│   │   │       └── templates/                    # Template render phía server
│   │   └── test/
│   │       └── java/com/realestates/backend/
│   │           └── BackendApplicationTests.java  # Kiểm thử tự động (Unit / Integration tests)
│   ├── .mvn/wrapper/
│   │   └── maven-wrapper.properties   # Cấu hình Maven Wrapper (tự động tải Maven 3.10)
│   ├── mvnw                           # Script chạy Maven Wrapper (Linux / macOS)
│   ├── mvnw.cmd                       # Script chạy Maven Wrapper (Windows)
│   ├── pom.xml                        # Cấu hình dự án & quản lý dependencies Maven
│   ├── .gitignore                     # Quy tắc bỏ qua file của backend
│   └── .gitattributes                 # Quy tắc chuẩn hóa xuống dòng cho Maven Wrapper
│
├── supabase/                          # Cấu hình cơ sở dữ liệu Supabase
│   ├── config.toml                    # Cấu hình dịch vụ Supabase local
│   ├── seed.sql                       # Dữ liệu mẫu (seed data) cho local database
│   ├── migrations/                    # Chứa các file SQL migration (hiện tại chưa có file nào)
│   └── functions/                     # Chứa Supabase Edge Functions (dự phòng tương lai)
│
├── .github/
│   └── workflows/                     # Cấu hình quy trình CI tự động với GitHub Actions
│       ├── frontend-ci.yml            # CI kiểm tra lint và build Frontend
│       └── backend-ci.yml             # CI chạy test và build Backend
│
├── package.json                       # Quản lý npm ở thư mục gốc (Supabase CLI)
├── package-lock.json                  # Khóa phiên bản dependencies thư mục gốc
└── node_modules/                      # Thư viện npm (không commit lên Git)
```

---

## Yêu Cầu Cài Đặt (Prerequisites)

Các thành viên cần cài đặt các công cụ sau trước khi bắt đầu lập trình.

### Phần Mềm Cần Thiết

| Công cụ | Phiên bản tối thiểu | Mục đích sử dụng | Nguồn tải |
|---|---|---|---|
| **Git** | 2.x | Quản lý mã nguồn | [git-scm.com](https://git-scm.com/) |
| **Node.js** | 20 LTS trở lên | Chạy môi trường Frontend & Supabase CLI | [nodejs.org](https://nodejs.org/) |
| **npm** | 10+ (đi kèm Node.js) | Quản lý thư viện JavaScript | Đi kèm với Node.js |
| **Java JDK** | 21 | Phát triển ứng dụng Backend | [Adoptium Temurin JDK 21](https://adoptium.net/) |

> **Lưu ý:** `npm` đã được tích hợp sẵn khi cài Node.js, bạn không cần cài riêng.

> **Lưu ý:** Bạn **không** cần phải cài đặt Maven toàn cục. Dự án đã có sẵn **Maven Wrapper** (`mvnw` / `mvnw.cmd`), tự động tải đúng phiên bản Maven (3.10.0) tương thích trong lần chạy đầu tiên.

### Kiểm Tra Môi Trường Sau Khi Cài Đặt

Mở terminal (PowerShell, CMD hoặc Git Bash) và chạy các lệnh sau:

```bash
git --version       # Kết quả: git version 2.x.x
node -v             # Kết quả: v20.x.x hoặc mới hơn (ví dụ: v22.x, v24.x)
npm -v              # Kết quả: 10.x.x hoặc mới hơn
java -version       # Kết quả: openjdk version "21.x.x"
```

### IDE (Môi Trường Phát Triển) Khuyên Dùng

| IDE | Phù hợp nhất cho | Extension / Plugin nên cài |
|---|---|---|
| **VS Code** | Frontend (React / TypeScript) | ESLint, Prettier, TypeScript, Vite |
| **IntelliJ IDEA** | Backend (Java / Spring Boot) | Lombok plugin (thường đã tích hợp sẵn) |

Bạn có thể dùng bất kỳ IDE nào thuận tiện cho công việc của mình.

---

## Hướng Dẫn Cài Đặt Ban Đầu (Initial Setup)

### 1. Clone Kho Chứa (Repository)

```bash
git clone <repository-url>
cd bds_repo_2
```

> Thay `<repository-url>` bằng link repo GitHub của nhóm khi đã khởi tạo remote.

### 2. Cài Đặt Dependencies Tại Thư Mục Gốc

Đứng tại thư mục **gốc của dự án** (`bds_repo_2/`):

```bash
npm install
```

Lệnh này sẽ cài đặt công cụ **Supabase CLI** cục bộ theo khai báo trong `package.json`. Bạn có thể chạy CLI này bằng cú pháp: `npx supabase <lệnh>`.

### 3. Cài Đặt Dependencies Cho Frontend

```bash
cd frontend
npm install
cd ..
```

### 4. Chuẩn Bị Cho Backend

Backend sử dụng **Maven Wrapper**, bạn không cần cài thêm công cụ phụ trợ nào khác. Maven và tất cả các thư viện Java khai báo trong `pom.xml` sẽ tự động tải về khi chạy lần đầu.

Kiểm tra biên dịch thử backend:

**Trên Windows (PowerShell hoặc Command Prompt):**
```cmd
cd backend
.\mvnw.cmd compile
cd ..
```

**Trên Linux / macOS / Git Bash:**
```bash
cd backend
./mvnw compile
cd ..
```

### 5. Kiểm Tra Tổng Thể Dự Án

Chạy các lệnh sau từ thư mục gốc để đảm bảo mọi thứ đã sẵn sàng:

```bash
# Kiểm tra Supabase CLI hoạt động
npx supabase --version

# Kiểm tra build Frontend thành công
cd frontend && npm run build && cd ..

# Kiểm tra biên dịch Backend thành công (Windows)
cd backend && .\mvnw.cmd compile && cd ..

# Kiểm tra biên dịch Backend thành công (Linux / macOS / Git Bash)
cd backend && ./mvnw compile && cd ..
```

---

## Cách Chạy Dự Án (Running the Project)

Để chạy đồng thời cả Frontend và Backend trên máy, bạn cần mở **2 cửa sổ terminal riêng biệt**.

### Terminal 1 — Chạy Máy Chủ Phát Triển Frontend (React + Vite)

```bash
cd frontend
npm run dev
```

Máy chủ Vite sẽ khởi động tại địa chỉ: **http://localhost:5173**. Vite hỗ trợ Hot Module Replacement (HMR), mọi thay đổi trong code sẽ phản hồi ngay lập tức trên trình duyệt.

### Terminal 2 — Chạy Máy Chủ Backend (Spring Boot)

**Trên Windows (PowerShell hoặc CMD):**
```cmd
cd backend
.\mvnw.cmd spring-boot:run
```

**Trên Linux / macOS / Git Bash:**
```bash
cd backend
./mvnw spring-boot:run
```

Máy chủ Spring Boot sẽ lắng nghe mặc định tại: **http://localhost:8080**.

### Bảng Tổng Hợp Địa Chỉ Cổng Dịch Vụ

| Dịch vụ | Địa chỉ URL | Cửa sổ Terminal |
|---|---|---|
| Frontend (Vite dev server) | http://localhost:5173 | Terminal 1 |
| Backend (Spring Boot API) | http://localhost:8080 | Terminal 2 |

---

## Quy Trình Phát Triển Frontend

### Cấu Trúc Mã Nguồn Frontend

Tất cả code của frontend nằm trong `frontend/src/`. Các thư mục được phân loại như sau:

| Thư mục | Mục đích | Ví dụ |
|---|---|---|
| `components/` | Các UI component dùng chung | `PropertyCard.tsx`, `SearchBar.tsx` |
| `pages/` | Các trang màn hình chính theo route | `HomePage.tsx`, `PropertyDetailPage.tsx` |
| `layouts/` | Component khung bố cục bao bọc trang | `MainLayout.tsx`, `AuthLayout.tsx` |
| `hooks/` | Custom hooks React | `useAuth.ts`, `useProperties.ts` |
| `services/` | Gọi API và tích hợp backend | `api.ts`, `propertyService.ts` |
| `stores/` | Quản lý state toàn cục (Zustand) | `useAuthStore.ts`, `useFilterStore.ts` |
| `types/` | Định nghĩa interface, type TypeScript | `Property.ts`, `User.ts` |
| `utils/` | Hàm tiện ích, định dạng, helper | `formatPrice.ts`, `validators.ts` |
| `assets/` | File hình ảnh, biểu tượng tĩnh | `logo.svg`, `placeholder.png` |

### Các Lệnh npm Có Sẵn Trong Frontend

Chạy các lệnh này từ thư mục `frontend/`:

| Lệnh | Mô tả |
|---|---|
| `npm run dev` | Khởi chạy máy chủ phát triển Vite |
| `npm run build` | Kiểm tra kiểu (TypeScript) và đóng gói production (`tsc -b && vite build`) |
| `npm run lint` | Chạy ESLint để quét lỗi cú pháp và quy chuẩn code (`eslint .`) |
| `npm run preview` | Xem trước bản build production trên máy cục bộ |

### Cài Đặt Thư Viện npm Mới

```bash
cd frontend
npm install <ten-goi>
```

Nếu là thư viện dùng trong lúc phát triển (linter, types, testing):

```bash
cd frontend
npm install --save-dev <ten-goi>
```

### Các Thư Viện Quan Trọng Được Cài Đặt

| Thư viện | Mục đích |
|---|---|
| **React 19** | Thư viện xây dựng giao diện người dùng |
| **TypeScript 5.8** | Kiểm soát kiểu dữ liệu tĩnh an toàn |
| **Vite 7** | Công cụ bundler và server phát triển tốc độ cao |
| **React Router 8** | Điều hướng và định tuyến các trang trong ứng dụng |
| **Axios** | Gửi request HTTP lên máy chủ Backend |
| **TanStack React Query 5** | Quản lý caching và đồng bộ dữ liệu từ server |
| **Zustand 5** | Quản lý trạng thái client-side gọn nhẹ |
| **ESLint 9** | Kiểm tra chất lượng và chuẩn mực code |

---

## Quy Trình Phát Triển Backend

### Vị Trí Viết Code Java

Mã nguồn Java chính được đặt tại thư mục: `backend/src/main/java/com/realestates/backend/`.

### Quy Chuẩn Tổ Chức Package Trong Spring Boot

Dự án áp dụng cấu trúc phân tầng chuẩn của Spring Boot:

| Package | Mục đích | Ví dụ |
|---|---|---|
| `controller/` | Tiếp nhận và xử lý REST API endpoints | `PropertyController.java` |
| `service/` | Xử lý logic nghiệp vụ (Business Logic) | `PropertyService.java` |
| `repository/` | Tầng truy vấn cơ sở dữ liệu (Spring Data) | `PropertyRepository.java` |
| `model/` | Các Entity ánh xạ bảng cơ sở dữ liệu (JPA) | `Property.java` |
| `dto/` | Đối tượng vận chuyển dữ liệu (Data Transfer Objects) | `PropertyDTO.java` |
| `config/` | Lớp cấu hình Spring (CORS, Security,...) | `CorsConfig.java`, `SecurityConfig.java` |
| `exception/` | Xử lý ngoại lệ tập trung | `ResourceNotFoundException.java` |

> **Lưu ý:** Hiện tại mới có thư mục `controller/` chứa file mẫu `HelloController.java`. Các thành viên chủ động tạo thêm các package tương ứng khi triển khai tính năng.

### Các Lệnh Biên Dịch Và Kiểm Thử

| Lệnh trên Windows | Lệnh trên Linux / macOS | Mô tả |
|---|---|---|
| `.\mvnw.cmd compile` | `./mvnw compile` | Biên dịch mã nguồn Java |
| `.\mvnw.cmd test` | `./mvnw test` | Chạy toàn bộ Unit Tests |
| `.\mvnw.cmd package` | `./mvnw package` | Đóng gói ứng dụng thành file JAR |
| `.\mvnw.cmd verify` | `./mvnw verify` | Chạy test và thẩm định chất lượng dự án |
| `.\mvnw.cmd clean package` | `./mvnw clean package` | Dọn dẹp thư mục build cũ và đóng gói lại từ đầu |

### Thêm Thư Viện Maven Mới

Khi cần bổ sung thư viện cho backend:

1. Tìm mã dependency tại [Maven Central](https://search.maven.org/).
2. Thêm thẻ `<dependency>` vào khối `<dependencies>` trong file `backend/pom.xml`.
3. Maven Wrapper sẽ tự động tải thư viện mới ở lần biên dịch hoặc chạy tiếp theo.

Ví dụ — Thêm PostgreSQL driver khi kết nối cơ sở dữ liệu:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Các Thư Viện Backend Đang Sử Dụng

| Thư viện | Mục đích |
|---|---|
| **spring-boot-starter-webmvc** | Xây dựng RESTful Web APIs |
| **spring-boot-starter-actuator** | Giám sát tình trạng sức khỏe (health metrics) của ứng dụng |
| **spring-boot-starter-validation** | Kiểm tra dữ liệu đầu vào (Validation `@NotNull`, `@Size`,...) |
| **spring-boot-devtools** | Tự động reload ứng dụng khi thay đổi code |
| **Lombok** | Tự động sinh getter, setter, constructor bằng Annotation (`@Data`, `@Builder`) |

---

## Quản Lý Cơ Sở Dữ Liệu Với Supabase

### Supabase Là Gì?

[Supabase](https://supabase.com/) là nền tảng mã nguồn mở phát triển trên nền tảng cơ sở dữ liệu quan hệ **PostgreSQL**. Nền tảng cung cấp dịch vụ Database, Authentication, Storage và Serverless Edge Functions. Nhóm chúng ta sử dụng PostgreSQL của Supabase làm cơ sở dữ liệu chính.

### Mục Đích Của Thư Mục `supabase/`

Thư mục `supabase/` trong dự án dùng để lưu trữ cấu hình môi trường local và **các file SQL migration được theo dõi qua Git**. Điều này đảm bảo tất cả thành viên trong nhóm đều có cấu trúc cơ sở dữ liệu đồng bộ với nhau.

### Database Migration Là Gì?

Migration là một file kịch bản SQL thể hiện một thay đổi cụ thể trên cấu trúc bảng (ví dụ: tạo bảng mới, thêm cột, tạo index, thiết lập khóa ngoại). Các file migration được đánh số thứ tự theo timestamp và được thực thi tuần tự.

### Phân Biệt Giữa Schema (Cấu Trúc) Và Data (Dữ Liệu)

| Khái niệm | Ý nghĩa | Ví dụ |
|---|---|---|
| **Schema** (Cấu trúc bảng) | Định nghĩa bảng, kiểu dữ liệu các cột, quan hệ khóa ngoại, index | `CREATE TABLE properties (...)` |
| **Data** (Dữ liệu thực tế) | Các dòng dữ liệu được lưu trữ trong bảng | Thông tin các bài đăng nhà đất cụ thể, danh sách tài khoản |

File migration chỉ lưu giữ **Schema** (khung bảng), không bảo toàn **Data**. Để bảo vệ dữ liệu người dùng thực tế trên môi trường chạy thật, cần sử dụng tính năng sao lưu (Backup) chứ không thể dựa vào migration.

### Nơi Quản Lý Cấu Trúc Database

Mọi thay đổi cấu trúc bảng, trigger, function hay chính sách phân quyền RLS phải được viết trong các file migration nằm ở thư mục `supabase/migrations/`.

Xem hướng dẫn chi tiết về cách tạo file migration, quy ước đặt tên và cách phối hợp làm việc tại **[Supabase README](supabase/README.md)**.

> **Quan trọng:** Hiện tại nhóm **chưa** tạo dự án Supabase trên đám mây (Supabase Cloud). Thư mục `supabase/` này hiện phục vụ chuẩn hóa quy trình cấu hình và migration local trước. Việc kết nối tới Supabase Cloud sẽ được thực hiện ở các bước tiếp theo.

---

## Quy Trình Phối Hợp Làm Việc Với Git (Git Collaboration)

### 1. Đồng Bộ Mã Nguồn Mới Nhất Trước Khi Bắt Đầu Làm

```bash
git checkout main
git pull origin main
```

### 2. Tạo Nhánh Mới (Feature Branch)

Tuyệt đối **không commit code trực tiếp lên nhánh `main`**. Luôn tạo một nhánh tính năng mới:

```bash
git checkout -b feature/ten-tinh-nang
```

Quy ước đặt tên nhánh:
- `feature/tim-kiem-nha` — khi làm tính năng mới
- `fix/loi-dang-nhap` — khi sửa lỗi
- `refactor/api-service` — khi tái cấu trúc code

### 3. Commit Và Đẩy Code Lên GitHub

```bash
git add .
git commit -m "Thêm component tìm kiếm bất động sản theo khoảng giá"
git push origin feature/ten-tinh-nang
```

Viết thông điệp commit rõ ràng, giải thích ngắn gọn những gì bạn đã thay đổi.

### 4. Tạo Pull Request (PR)

1. Đẩy nhánh lên GitHub.
2. Truy cập kho chứa của nhóm trên giao diện GitHub.
3. Nhấn nút **"Compare & pull request"**.
4. Điền mô tả những phần đã làm hoặc cần lưu ý.
5. Gán ít nhất một thành viên trong nhóm vào mục Reviewer.
6. Chờ hệ thống tự động kiểm tra CI pass (xanh).
7. Sau khi được duyệt và CI hợp lệ, tiến hành gộp (Merge) vào nhánh chính.

### Những Thư Mục / Tệp Tuyệt Đối KHÔNG Đưa Lên Git

Các file tự sinh hoặc chứa thông tin nhạy cảm đã được cấu hình loại trừ:

| Đường dẫn | Lý do không đưa lên Git |
|---|---|
| `node_modules/` | Thư viện npm đã cài (tự sinh từ `package-lock.json`) |
| `frontend/node_modules/` | Thư viện frontend cài đặt trên máy cá nhân |
| `backend/target/` | Thư mục chứa các file `.class` và file `.jar` đã build của Maven |
| `.env`, `.env.local` | File chứa mật khẩu, secret key, API key bí mật |
| `.idea/`, `.vscode/` | Cấu hình cài đặt riêng của từng IDE trên máy cá nhân |
| `dist/`, `build/` | Thư mục mã nguồn sau khi build production |

### Những Tệp BẮT BUỘC Phải Commit Lên Git

| Tệp | Lý do phải commit |
|---|---|
| `package-lock.json` | Đảm bảo mọi thành viên cài chính xác phiên bản npm giống nhau |
| `frontend/package-lock.json` | Khóa chính xác phiên bản thư viện giao diện |
| `backend/mvnw`, `backend/mvnw.cmd` | Bộ chạy Maven Wrapper đảm bảo môi trường build đồng nhất |
| `backend/.mvn/wrapper/maven-wrapper.properties` | Khai báo URL và phiên bản Maven tương thích |
| `supabase/config.toml` | Cấu hình chung cho môi trường database Supabase |
| `supabase/migrations/*.sql` | Lịch sử các bước thay đổi cấu trúc bảng database |

> **Lưu ý:** Hiện tại dự án đang **thiếu file `.gitignore` ở thư mục gốc**. Nhóm cần tạo file này (xem mẫu ở phần tiếp theo) để tránh vô tình commit nhầm `node_modules/` của thư mục gốc lên GitHub.

---

## Biến Môi Trường Và Bảo Mật (Environment Variables)

### Tại Sao Không Được Commit File `.env`?

File `.env` thường chứa các thông tin bảo mật nhạy cảm như mật khẩu cơ sở dữ liệu, secret key, token xác thực. Nếu commit file này lên Git công khai, bất kỳ ai có quyền truy cập repo đều có thể đánh cắp và xâm nhập hệ thống.

### Sử Dụng File Mẫu `.env.example`

Để các thành viên khác biết dự án cần những biến môi trường nào, hãy tạo file `.env.example` (chỉ chứa tên biến cùng giá trị mẫu, không điền secret thật) và commit file này lên Git. Mỗi thành viên khi clone repo về sẽ tự copy thành `.env` trên máy mình.

Ví dụ file `frontend/.env.example`:
```
VITE_API_URL=http://localhost:8080
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_ANON_KEY=your-anon-public-key-here
```

Ví dụ file `backend/.env.example`:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:54322/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-secret-password-here
SUPABASE_JWT_SECRET=your-jwt-secret-key-here
```

### Nội Dung File `.gitignore` Khuyên Dùng Cho Thư Mục Gốc

Tạo file `.gitignore` ngay tại thư mục gốc của dự án với nội dung tối thiểu:

```gitignore
# Thư viện npm
node_modules/

# Biến môi trường bí mật
.env
.env.local
.env.*.local

# File cấu hình riêng của IDE
.idea/
.vscode/
*.swp
*.swo
.DS_Store

# File hệ điều hành
Thumbs.db
```

---

## Hệ Thống CI Tự Động (Continuous Integration)

### CI Là Gì?

**Continuous Integration (CI)** là quy trình tự động hóa kiểm tra mã nguồn (lint, test, build) mỗi khi có lập trình viên đẩy code mới hoặc tạo Pull Request. Hệ thống này giúp phát hiện sớm các lỗi cú pháp, xung đột kiểu hoặc lỗi logic trước khi code được gộp vào nhánh chính.

Dự án sử dụng nền tảng **GitHub Actions** để thực thi CI hoàn toàn tự động.

### Cơ Chế Hoạt Động Của CI Trong Dự Án

Dự án được cấu hình **2 quy trình CI độc lập**:

| Tên quy trình | Tệp cấu hình | Điều kiện kích hoạt |
|---|---|---|
| **Frontend CI** | `.github/workflows/frontend-ci.yml` | Khi có thay đổi trong thư mục `frontend/**` |
| **Backend CI** | `.github/workflows/backend-ci.yml` | Khi có thay đổi trong thư mục `backend/**` |

### Cơ Chế Lọc Đường Dẫn (Path Filtering)

Mỗi workflow áp dụng bộ lọc đường dẫn (`paths:`) để tránh lãng phí thời gian và tài nguyên máy chủ:

| Tệp thay đổi trong Commit / PR | Frontend CI | Backend CI |
|---|---|---|
| `frontend/src/App.tsx` | Chạy ✅ | Bỏ qua ⏭️ |
| `frontend/package.json` | Chạy ✅ | Bỏ qua ⏭️ |
| `backend/src/main/java/...` | Bỏ qua ⏭️ | Chạy ✅ |
| `backend/pom.xml` | Bỏ qua ⏭️ | Chạy ✅ |
| Thay đổi cả thư mục frontend và backend | Chạy ✅ | Chạy ✅ |
| Chỉ sửa `README.md` | Bỏ qua ⏭️ | Bỏ qua ⏭️ |
| Sửa migration trong `supabase/migrations/...` | Bỏ qua ⏭️ | Bỏ qua ⏭️ |

Ngoài ra, khi có sửa đổi trực tiếp vào chính file workflow CI (ví dụ: sửa file `frontend-ci.yml`), workflow đó cũng sẽ được kích hoạt để kiểm tra tính hợp lệ.

### Nội Dung Các Bước Kiểm Tra Trong CI

**Frontend CI:**
1. Khởi tạo môi trường Node.js 22 trên máy chủ Ubuntu ảo.
2. Cài đặt chính xác các gói dependencies (`npm ci`) dựa trên `frontend/package-lock.json`.
3. Kiểm tra lỗi chuẩn code bằng ESLint (`npm run lint`).
4. Kiểm tra kiểu TypeScript và đóng gói thử nghiệm (`npm run build`).

**Backend CI:**
1. Khởi tạo môi trường Java JDK 21 (Temurin) có sẵn bộ nhớ đệm Maven cache.
2. Cấp quyền thực thi cho script Maven Wrapper (`chmod +x mvnw`).
3. Chạy toàn bộ test tự động và biên dịch ứng dụng (`./mvnw --batch-mode verify`).

### Xử Lý Khi CI Bị Báo Lỗi (Đánh Dấu ✗ Đỏ)

Nếu một bước nào đó thất bại, GitHub sẽ hiển thị dấu chéo đỏ ✗ ở cạnh commit hoặc Pull Request:

1. Vào tab **Actions** trên giao diện kho chứa GitHub.
2. Bấm vào lần chạy workflow bị báo đỏ.
3. Bấm vào job gặp sự cố để xem log chi tiết.
4. Đọc kỹ dòng thông báo lỗi (thường là lỗi type TypeScript, lỗi cú pháp ESLint, hoặc test JUnit bị fail).
5. Sửa lỗi trên máy cá nhân, commit và đẩy lại lên nhánh. CI sẽ tự động kích hoạt chạy lại.

### Cấu Hình Bảo Vệ Nhánh (Branch Protection Rules)

Để đảm bảo code chỉ được gộp khi đã vượt qua CI, trưởng nhóm (Admin repo) nên cấu hình:

1. Vào **Settings** trên GitHub → chọn mục **Rules** hoặc **Branches**.
2. Thêm quy tắc bảo vệ cho nhánh `main`.
3. Bật tùy chọn **"Require status checks to pass before merging"**.
4. Chọn 2 check: `Lint & Build` (Frontend) và `Test & Build` (Backend).
5. **Quan trọng:** Vì dự án có chia luồng Path Filtering (chỉ chạy CI tương ứng với phần code thay đổi), hãy bật tùy chọn **"Do not require workflows that are skipped"** để tránh trường hợp sửa frontend mà bị kẹt do backend CI bị bỏ qua (và ngược lại).

---

## Hướng Dẫn Khắc Phục Lỗi Thường Gặp (Troubleshooting)

### Lỗi `'node'` hoặc `'npm'` không nhận diện được lệnh

**Hiện tượng:** Terminal báo lỗi `'node' is not recognized as an internal or external command` hoặc `command not found`.

**Cách sửa:**
1. Tải và cài đặt Node.js từ trang chủ [nodejs.org](https://nodejs.org/).
2. Đóng và mở lại hoàn toàn cửa sổ terminal đang dùng.
3. Chạy `node -v` để kiểm tra. Trên Windows, chú ý tích chọn tùy chọn "Add to PATH" trong trình cài đặt.

### Lỗi không chạy được Maven Wrapper (`mvnw`)

**Hiện tượng:** Không chạy được lệnh `./mvnw` hoặc `.\mvnw.cmd`.

**Cách sửa:**
1. Đảm bảo bạn đang đứng đúng thư mục con `backend/`.
2. Trên Linux/macOS, nếu báo lỗi Permission Denied, cấp quyền thực thi bằng lệnh:
   ```bash
   chmod +x mvnw
   ```
3. Trên Windows, sử dụng cú pháp `.\mvnw.cmd` thay vì `./mvnw`.

### Frontend không gọi được API từ Backend

**Hiện tượng:** Các yêu cầu API từ trình duyệt bị lỗi Network Error hoặc bị chặn CORS.

**Cách sửa:**
1. Kiểm tra Backend Spring Boot đã được khởi động và đang chạy ở cổng 8080 chưa.
2. Kiểm tra xem Backend đã cấu hình cho phép CORS từ nguồn `http://localhost:5173` của Vite chưa.

### Báo lỗi cổng kết nối đã bị chiếm dụng (Port already in use)

**Hiện tượng:** Báo lỗi `Port 5173 is already in use` hoặc `Port 8080 is already in use`.

**Cách sửa:**

**Trên Windows:**
```cmd
netstat -ano | findstr :5173
taskkill /PID <ma-so-PID> /F
```

**Trên Linux / macOS:**
```bash
lsof -i :5173
kill -9 <ma-so-PID>
```
*(Thay `5173` bằng `8080` nếu cổng bị xung đột là backend).*

### Lỗi thiếu thư viện (Missing dependencies)

**Cách sửa:**
- **Frontend:** Di chuyển vào thư mục `frontend` và chạy `npm install`.
- **Backend:** Maven Wrapper sẽ tự đồng bộ thư viện khi bạn chạy `compile` hoặc `spring-boot:run`.
- **Supabase CLI:** Đứng tại thư mục gốc dự án và chạy `npm install`.

### Lỗi không chạy được lệnh Supabase CLI

**Hiện tượng:** Gõ `supabase` bị báo không tìm thấy lệnh.

**Cách sửa:**
1. Đảm bảo bạn đã chạy `npm install` tại thư mục gốc dự án.
2. Sử dụng lệnh kèm tiền tố `npx`: ví dụ `npx supabase status` thay vì gõ trực tiếp `supabase status`.

### Lỗi sai phiên bản Java (Java Version Mismatch)

**Hiện tượng:** Backend báo lỗi biên dịch liên quan tới phiên bản Java Runtime.

**Cách sửa:**
1. Kiểm tra phiên bản Java máy bạn: `java -version`.
2. Dự án yêu cầu chính xác **Java 21**. Hãy cài đặt bản [Adoptium Temurin JDK 21](https://adoptium.net/).
3. Kiểm tra biến môi trường `JAVA_HOME` để chắc chắn đường dẫn đang trỏ đúng tới thư mục JDK 21.

---

## Tài Liệu Tham Khảo Thêm

- [Tài liệu chính thức React](https://react.dev/)
- [Tài liệu chính thức Vite](https://vite.dev/)
- [Tài liệu Spring Boot](https://docs.spring.io/spring-boot/)
- [Tài liệu Supabase](https://supabase.com/docs)
- [Tài liệu GitHub Actions](https://docs.github.com/en/actions)
- [Hướng Dẫn Quản Lý Database Supabase](supabase/README.md) — Chi tiết về quy trình migration và cấu trúc database

---

## Giấy Phép & Mục Đích (License)

Dự án được xây dựng và phục vụ mục đích học tập môn học tại trường đại học.
