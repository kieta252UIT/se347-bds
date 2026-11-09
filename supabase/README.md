# Hướng Dẫn Quản Lý Cơ Sở Dữ Liệu Supabase (Supabase Database Guide)

## A. Mục Đích (Purpose)

Thư mục này lưu trữ **cấu hình môi trường và các file SQL migration được quản lý phiên bản qua Git** cho dự án Hệ Thống Tìm Kiếm và Gợi Ý Bất Động Sản. 

Toàn bộ thao tác được quản lý thông qua công cụ [Supabase CLI](https://supabase.com/docs/guides/cli), đã được cài đặt trong dependencies của dự án và chạy bằng lệnh `npx supabase <lệnh>` từ thư mục gốc của repo.

### Tại Sao Cần Quản Lý Phiên Bản Cho Thay Đổi Database?

Khi làm việc nhóm nhiều người trên cùng một dự án phần mềm, cấu trúc bảng (schema) gồm bảng, cột, khóa ngoại, index sẽ liên tục thay đổi. Nếu không có quy trình quản lý:

- Một bạn tạo bảng trên máy cá nhân nhưng máy các bạn khác không có bảng đó.
- Khi merge code, cấu trúc database của các thành viên bị lệch và xung đột.
- Không có cách nào tái tạo chính xác cơ sở dữ liệu trên máy tính mới hoặc khi deploy lên server.

Bằng cách lưu trữ mọi thay đổi cấu trúc bảng dưới dạng **các file SQL migration** trong Git, tất cả thành viên trong nhóm chỉ cần chạy một lệnh là có thể đồng bộ cấu trúc database giống nhau 100%.

---

## B. Cấu Trúc Thư Mục (Directory Structure)

```text
supabase/
├── config.toml       # ✅ ĐÃ CÓ — File cấu hình dịch vụ Supabase local
├── seed.sql          # ✅ ĐÃ CÓ — Dữ liệu mẫu khởi tạo cho database local
├── migrations/       # ✅ ĐÃ CÓ (hiện đang trống) — Nơi chứa các file SQL migration
└── functions/        # ✅ ĐÃ CÓ (hiện đang trống) — Nơi chứa Supabase Edge Functions (dự phòng)
```

### Chi Tiết Từng Tệp Và Thư Mục

| Đường dẫn | Mô tả chức năng |
|---|---|
| `config.toml` | File cấu hình Supabase chạy local. Khai báo cổng kết nối của các dịch vụ (API, Studio, Auth,...), phiên bản PostgreSQL (hiện cấu hình bản 17). **File này bắt buộc phải commit lên Git.** |
| `migrations/` | Chứa các file SQL migration được đánh số theo thời gian (timestamp). Mỗi file đại diện cho một thay đổi cấu trúc bảng cụ thể. Khi khởi tạo database, các file này sẽ được chạy tuần tự từ cũ đến mới. |
| `seed.sql` | File SQL chứa dữ liệu mẫu thử nghiệm (seed data). File này sẽ tự động chạy sau khi toàn bộ migrations đã chạy xong mỗi khi reset database local. |
| `functions/` | Thư mục dành cho [Supabase Edge Functions](https://supabase.com/docs/guides/functions) — các hàm backend serverless viết bằng TypeScript/Deno. Thư mục này hiện tại chưa sử dụng đến, phục vụ mở rộng trong tương lai. |

---

## C. Quản Lý Migration (Database Migrations)

### 1. Migration Là Gì?

**Migration** là một file script SQL mô tả một thay đổi cụ thể trên cấu trúc cơ sở dữ liệu, chẳng hạn như:

- Tạo một bảng mới (ví dụ: bảng người dùng, bảng bất động sản).
- Thêm một cột mới vào bảng hiện có (ví dụ: thêm cột số điện thoại liên hệ).
- Tạo index để tối ưu tốc độ tìm kiếm.
- Tạo function, trigger hoặc quy tắc phân quyền bảo mật RLS.

Các file migration được **thực thi theo thứ tự thời gian** dựa vào tiền tố timestamp ở đầu tên file để xây dựng hoàn chỉnh cơ sở dữ liệu từ con số không.

### 2. Cách Tạo File Migration Mới

Mở terminal từ **thư mục gốc dự án** (`bds_repo_2/`) và chạy lệnh:

```bash
npx supabase migration new tao_bang_bat_dong_san
```

Lệnh này sẽ tự động sinh ra một file SQL mới trong thư mục `supabase/migrations/` kèm theo timestamp:

```text
supabase/migrations/20261109120000_tao_bang_bat_dong_san.sql
```

Sau đó, bạn chỉ cần mở file SQL này lên và viết các câu lệnh SQL thay đổi cấu trúc.

### 3. Quy Ước Đặt Tên File Migration

- Đặt tên theo dạng **snake_case** rõ ràng, thể hiện đúng mục đích thay đổi.
- Tiền tố timestamp sẽ được CLI tự động tạo, bạn chỉ cần gõ phần mô tả.

**Ví dụ đặt tên chuẩn:**

| Tên | Mục đích |
|---|---|
| `create_users_table` | Tạo bảng lưu trữ thông tin người dùng |
| `create_properties_table` | Tạo bảng lưu thông tin bất động sản |
| `add_price_column_to_properties` | Thêm cột giá vào bảng bất động sản |
| `create_search_index_on_city` | Tạo index tìm kiếm theo tỉnh/thành phố |
| `add_foreign_key_user_to_properties` | Tạo quan hệ khóa ngoại giữa user và tin đăng |

### 4. Cú Pháp Viết Mã SQL Trong Migration

Ví dụ nội dung file migration tạo bảng thông tin bất động sản:

```sql
-- Tạo bảng properties lưu trữ danh sách tin bất động sản
CREATE TABLE IF NOT EXISTS properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT,
    price NUMERIC(15, 2) NOT NULL,
    address TEXT NOT NULL,
    city TEXT NOT NULL,
    district TEXT,
    area NUMERIC(10, 2),
    bedrooms INTEGER,
    bathrooms INTEGER,
    property_type TEXT NOT NULL,
    listing_type TEXT NOT NULL CHECK (listing_type IN ('sale', 'rent')),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);
```

### 5. Nguyên Tắc Vàng: Tuyệt Đối KHÔNG Sửa File Migration Đã Đẩy Lên Git

> [!CAUTION]
> **Một khi file migration đã được commit và push lên GitHub, tuyệt đối KHÔNG được sửa lại nội dung file đó.** 
> Nếu bạn muốn thay đổi cấu trúc (ví dụ: đổi kiểu dữ liệu, thêm cột, xóa cột), hãy tạo một **file migration MỚI** chứa câu lệnh chỉnh sửa (như `ALTER TABLE ...`). Việc sửa các file migration cũ đã chia sẻ sẽ làm hỏng tính đồng bộ và gây lỗi xung đột không thể cứu vãn trên máy của các thành viên khác.

### 6. Cách Áp Dụng (Chạy) Migration

| Trường hợp | Lệnh thực hiện | Chi tiết hành động |
|---|---|---|
| **Làm mới database local** | `npx supabase db reset` | Xóa sạch database local, chạy lại toàn bộ các file migration từ đầu đến cuối theo thứ tự thời gian, sau đó nạp dữ liệu từ `seed.sql`. |
| **Đẩy lên Supabase Cloud (tương lai)** | `npx supabase db push` | Áp dụng các file migration mới lên database trên đám mây. Cần liên kết với project cloud trước. |

> [!WARNING]
> Lệnh `npx supabase db reset` có tính chất **xóa dữ liệu** (destructive) trên môi trường local. Toàn bộ dữ liệu bạn nhập thủ công sẽ mất và được nạp lại theo file `seed.sql`. Hãy lưu các dữ liệu kiểm thử quan trọng vào `seed.sql`.

### 7. Đồng Bộ Database Khi Làm Việc Nhóm

Khi một bạn trong nhóm tạo migration mới và push lên:

1. Bạn kéo code mới về bằng lệnh `git pull origin main`.
2. Chạy `npx supabase db reset` để áp dụng các migration mới đó vào database trên máy bạn.
3. Khi đó, tất cả thành viên trong nhóm sẽ có cấu trúc bảng giống nhau hoàn toàn.

---

## D. Quy Ước Phát Triển SQL (SQL Development Conventions)

Bảng phân loại nơi định nghĩa các đối tượng cơ sở dữ liệu:

| Đối tượng SQL | Nơi khai báo | Ví dụ minh họa |
|---|---|---|
| Bảng (Tables) | Trong file migration | `CREATE TABLE properties (...)` |
| Khóa ngoại (Foreign Keys) | Trong file tạo bảng hoặc migration riêng | `ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY ...` |
| Chỉ mục (Indexes) | Trong file migration | `CREATE INDEX idx_properties_city ON properties(city)` |
| Hàm PostgreSQL (Functions) | Trong file migration | `CREATE OR REPLACE FUNCTION ...` |
| Trigger | Trong file migration (ngay sau hàm xử lý) | `CREATE TRIGGER ...` |
| Chính sách bảo mật hàng (RLS Policies) | Trong file migration (sau khi enable RLS) | `ALTER TABLE properties ENABLE ROW LEVEL SECURITY; CREATE POLICY ...` |
| Dữ liệu mẫu thử nghiệm (Seed data) | File `seed.sql` | `INSERT INTO properties VALUES (...)` |

### Thực Hành Tốt Nhất (Best Practices)

- **Mỗi migration chỉ xử lý một thay đổi logic duy nhất.** Ví dụ: 1 migration để tạo bảng cùng các index của bảng đó. Tránh gom nhiều thay đổi không liên quan vào chung 1 file.
- **Luôn dùng `IF NOT EXISTS` / `IF EXISTS`** để tránh phát sinh lỗi nếu câu lệnh vô tình chạy lại.
- **Viết chú thích (comment) rõ ràng** trong file SQL để giải thích ý nghĩa các trường dữ liệu và ràng buộc.
- **Luôn kiểm tra cục bộ** bằng lệnh `npx supabase db reset` trước khi commit và push code lên GitHub.

---

## E. Phục Hồi Dữ Liệu Cơ Sở Dữ Liệu Đám Mây (Cloud Database Recovery)

Cần hiểu rõ ranh giới khác biệt giữa **Migration** và **Bản sao lưu (Backup)**:

### Migration: Bảo Toàn CẤU TRÚC (Structure)
- Định nghĩa bảng, kiểu dữ liệu các cột.
- Khóa chính, khóa ngoại, ràng buộc check, indexes.
- Hàm, trigger, chính sách phân quyền RLS.
- Chạy migration trên database trống sẽ tạo ra một **bộ khung cơ sở dữ liệu chuẩn chỉnh** — nhưng **không có dữ liệu nào bên trong**.

### Database Backup: Bảo Toàn DỮ LIỆU THỰC TẾ (Data)
- Danh sách tài khoản người dùng thực tế.
- Các bài đăng bất động sản người dùng đã tải lên.
- Danh sách lưu yêu thích, lịch sử tìm kiếm.
- Tất cả các dòng (rows) dữ liệu trong các bảng.

> [!IMPORTANT]
> Nếu bạn vô tình làm mất hoặc xóa nhầm dữ liệu trong cơ sở dữ liệu cloud, **các file migration KHÔNG THỂ khôi phục lại dữ liệu đó**. Migration chỉ khôi phục khung bảng rỗng. Để khôi phục dữ liệu, bạn cần tới các bản Backup dữ liệu định kỳ của Supabase.

---

## F. Quy Tắc Phối Hợp Làm Việc Nhóm (Team Collaboration Rules)

### Quy Trình Thay Đổi Database Chuẩn

1. **Pull code mới nhất** từ nhánh `main` trước khi làm bất kỳ thao tác nào.
2. **Tạo file migration mới** với tên mô tả rõ ràng: `npx supabase migration new <ten_thay_doi>`.
3. **Viết mã SQL** vào file vừa sinh ra.
4. **Kiểm tra chạy thử** trên máy cá nhân bằng lệnh `npx supabase db reset` (yêu cầu máy có cài Docker để chạy Supabase local).
5. **Commit file migration** lên nhánh tính năng của bạn.
6. **Tạo Pull Request** và nhờ đồng đội review kỹ lưỡng mã SQL.
7. **Không bao giờ chỉnh sửa** file migration đã được gộp (merge) vào nhánh chính.

### Nguyên Tắc Phối Hợp Trong Nhóm

- **Mỗi thời điểm chỉ một người tác động vào một bảng cụ thể.** Tránh việc hai bạn cùng lúc tạo migration sửa cùng một bảng để hạn chế tối đa xung đột.
- **Trao đổi trước trên nhóm chat** khi chuẩn bị sửa đổi cấu trúc bảng để các bạn làm frontend/backend cùng biết và cập nhật code tương ứng.
- **Review kỹ file migration trong Pull Request** tương tự như khi review code Java hay React.
- **Chia nhỏ migration:** Giữ các file migration ngắn gọn, rõ ràng theo từng đầu việc.

---

## G. Bảng Tra Cứu Lệnh Supabase CLI Hữu Ích

Tất cả các lệnh dưới đây đều thực thi tại **thư mục gốc dự án** (`bds_repo_2/`):

| Lệnh | Ý nghĩa chức năng | Điều kiện yêu cầu |
|---|---|---|
| `npx supabase init` | Khởi tạo cấu hình Supabase (dự án đã làm sẵn) | Không cần gì |
| `npx supabase migration new <tên>` | Tạo một file migration SQL rỗng mới | Không cần gì |
| `npx supabase start` | Khởi động các dịch vụ Supabase local (DB, Auth, Studio,...) | Cần Docker Desktop |
| `npx supabase stop` | Dừng các dịch vụ Supabase local đang chạy | Cần Docker Desktop |
| `npx supabase db reset` | Reset lại database local: chạy lại toàn bộ migrations và nạp `seed.sql` | Cần Docker và local Supabase đang bật |
| `npx supabase status` | Hiển thị thông tin cổng, URL, key kết nối của Supabase local | Cần Docker và local Supabase đang bật |
| `npx supabase migration list` | Liệt kê danh sách các migration và trạng thái đã chạy hay chưa | Cần local DB đang bật hoặc đã link cloud |
| `npx supabase db push` | Đẩy các file migration mới lên database trên Supabase Cloud | Đã liên kết với project cloud |
| `npx supabase link --project-ref <id>` | Liên kết thư mục dự án với một project Supabase Cloud cụ thể | Đã tạo project trên Supabase Cloud |

> [!NOTE]
> **Hiện tại nhóm chưa tạo project trên Supabase Cloud.** Các lệnh yêu cầu kết nối cloud (như `db push` hay `link`) sẽ chưa sử dụng được ở thời điểm này. Việc chạy local với Docker là tùy chọn khuyến nghị để kiểm thử cú pháp SQL trước khi áp dụng vào dự án.
