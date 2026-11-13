# Cấu Hình HTTPS Cùng Nguồn (Same-Origin) & Định Tuyến `/api`

Thư mục `proxy/` chứa cấu hình Reverse Proxy phục vụ kiến trúc **React Frontend và Spring Boot API cùng nguồn HTTPS (Same-Origin)** theo quyết định kiến trúc **ADR-0013**, **SDD-11**, **SDD-20**, **SDD-28** và đặc tả phiên **PHIEN-01 / PHIEN-06**.

---

## 1. Quy Ước Định Tuyến (Routing Rules)

| Đường dẫn (Path) | Đích định tuyến (Upstream) | Hành vi & Bảo mật |
|---|---|---|
| `/api/*` (bao gồm `/api/csrf`, `/api/me`, `/api/auth/*`, `/api/listings/*`, ...) | Backend Spring Boot (`http://127.0.0.1:8080`) | Giữ nguyên tiền tố `/api`, method, status, body, header (`X-CSRF-Token`, `Idempotency-Key`, `If-Match`, `ETag`) và cookie (`__Host-se214_session`, `__Host-se214_csrf`). **Tuyệt đối không fallback sang HTML của React.** Mọi lỗi `/api/*` (400/401/403/404/503) đều trả JSON theo schema `LoiApi`. |
| `/` và mọi SPA deep link (bao gồm `/tim-kiem`, `/dang-nhap`, `/auth/verify-email`, `/auth/reset-password`, ...) | Frontend React / Vite (`http://127.0.0.1:5173`) | Phục vụ giao diện React SPA và hỗ trợ nâng cấp kết nối WebSocket cho Vite HMR. |

> **Lưu ý quan trọng:** Các đường dẫn `/auth/verify-email#token_hash=...` và `/auth/reset-password#token_hash=...` là **route giao diện React SPA** nhận liên kết xác minh/khôi phục mật khẩu từ email (mục 8.5 `api-specification-sprint-1.md`). Toàn bộ API xác thực của Backend đều nằm dưới `/api/auth/*`, `/api/csrf` và `/api/me`.

---

## 2. Cách Chạy Môi Trường HTTPS Cùng Nguồn (`https://localhost`)

### Cách 1: Sử dụng Caddy (Khuyên dùng theo SDD-28)

Caddy tự động cấp chứng chỉ TLS nội bộ cho `https://localhost` mà không cần tạo file chứng chỉ thủ công:

```bash
cd proxy
caddy run --config Caddyfile
```

### Cách 2: Sử dụng Nginx (`nginx.conf`)

1. **Tạo chứng chỉ TLS local bằng `mkcert`** (chứng chỉ nằm trong `proxy/certs/` đã được loại trừ khỏi Git trong `.gitignore`):
   ```bash
   mkcert -install
   mkcert -cert-file proxy/certs/localhost.pem -key-file proxy/certs/localhost-key.pem localhost 127.0.0.1 ::1
   ```
2. **Khởi động Nginx với file cấu hình của dự án:**
   ```bash
   nginx -p . -c proxy/nginx.conf
   ```

---

## 3. Chế Độ Phát Triển Nhanh Qua Vite Proxy

Khi phát triển giao diện cục bộ chưa bật Caddy/Nginx, `frontend/vite.config.ts` đã cấu hình sẵn proxy chuyển mọi yêu cầu `/api` từ trình duyệt về `http://localhost:8080` cùng nguồn với Vite dev server. Tuy nhiên, để kiểm thử đầy đủ cookie có tiền tố `__Host-` (`__Host-se214_session`, `__Host-se214_csrf` yêu cầu cờ `Secure` trên HTTPS) và ca kiểm thử `TS1-004`, cần truy cập qua cổng HTTPS `https://localhost`.
