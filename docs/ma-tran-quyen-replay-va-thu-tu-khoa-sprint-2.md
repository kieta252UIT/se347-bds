# Ma trận quyền, quy tắc Replay và thứ tự khóa — Sprint 2 (S2-02)

- **Mã công việc:** `S2-02` (Backlog Sprint 2 — Gói Nền tảng)
- **Ngày chốt:** 27/11/2026
- **Đối tượng nhận bàn giao:**
  - **Nhánh Báo cáo & Vi phạm** (`S2-06`, `S2-08`, migration `V2/V3`)
  - **Nhánh Quản trị** (`S2-07`, `S2-09`, migration `V2/V3`)
  - **Nhánh Ví & Gói đẩy tin** (`S2-03`, `S2-11`, migration `V4`)
  - **Nhánh Gợi ý & Kiểm thử tích hợp** (`S2-04`, `S2-05`, `S2-10`, `S2-12..S2-17`)
- **Căn cứ đặc tả:** `SRS 3.1`, `SDD 0.15`, 40 câu làm rõ (`Q01–Q40` và quyết định bổ sung), tiêu chí nghiệm thu `AC-03`, `AC-04.3`, `AC-06`, `AC-08`, `AC-09`, `AC-11.4–11.6`, `AC-15`, cùng nền tảng Sprint 1 (`V1__khoi_tao_schema_sprint_1.sql`, `CurrentPermissionAndCsrfService`, `MaTranQuyenSprint1`, `QuyenDocAnh`).

---

## 0. Bốn quyết định thiết kế liên nhánh đã chốt

| Mã quyết định | Nội dung chốt | Áp dụng cho nhánh |
|---|---|---|
| **QD-S2-01 (1A)** | **Chặn chủ tin tự báo cáo vi phạm tin của chính mình:** Khi `nguoi_gui_id == tin.chu_id`, từ chối ngay với `403 KHONG_DU_QUYEN` (không tăng bộ đếm 24h, không ghi báo cáo). | Báo cáo (`UC-09`), Kiểm thử (`TS2-02..04`) |
| **QD-S2-02 (2A)** | **Quyền đọc ảnh bằng chứng báo cáo riêng tư:** Khách vãng lai, người dùng khác và **kể cả chủ tin bị báo cáo** đều bị chặn với `404 KHONG_TIM_THAY` (chống dò IDOR). Chỉ cho phép đọc (`200 OK`, `Cache-Control: no-store`, `Vary: Cookie`) với:<br>1. `nguoi_dung` là người tải/gửi chính ảnh/báo cáo đó;<br>2. `kiem_duyet_vien` khi ảnh đã gắn vào báo cáo vi phạm;<br>3. `quan_tri_vien` (chỉ đọc) khi ảnh thuộc báo cáo đã xác nhận nằm trong căn cứ của phiếu đề xuất khóa tài khoản (`UC-17`). | Báo cáo (`UC-09`, `UC-14`), Quản trị (`UC-17`), Kiểm thử (`TS2-02..08`) |
| **QD-S2-03 (3A)** | **Nơi lưu mã chống gửi lặp (`Idempotency-Key` / `ma_y_dinh`):**<br>- **Nhánh Ví & Gói (`V4`):** Lưu trực tiếp trên bảng giao dịch ví / ý định mua (`se214.giao_dich_vi`), ràng buộc duy nhất theo phạm vi ví/loại thao tác + `ma_y_dinh` kèm `du_lieu_bam` và snapshot kết quả mua.<br>- **Nhánh Báo cáo & Quản trị (`V2/V3`):** Mở rộng bảng `se214.yeu_cau_ghi` (cho phép `tin_ket_qua_id NULL`, thêm `ket_qua_json jsonb` và mở rộng `CHECK (loai_thao_tac IN (...))`) để dùng chung thống nhất cho gửi/xử lý báo cáo, phiếu khóa và quản trị tài khoản. | DDL `V2/V3` (Báo cáo & Quản trị), DDL `V4` (Ví & Gói) |
| **QD-S2-04 (4A)** | **Quy ước đường dẫn API Sprint 2:** Giữ đúng chuẩn Sprint 1 — tiền tố `/api/...` tiếng Anh chữ thường, tài nguyên cá nhân dưới `/api/me/...`, hành động chốt quyết định dùng hậu tố `/decision`, đọc nhị phân ảnh dùng hậu tố `/{imageId}/content`, tham số và JSON body tiếng Việt `camelCase`. | Toàn bộ Backend Controller, Frontend Service & Test Plan `TSP-S2` |

---

## 1. Bảng 1 — Ma trận phân quyền RBAC, Ownership và tiền điều kiện Sprint 2

### Nguyên tắc bắt buộc kế thừa từ Sprint 1
1. **Nguồn sự thật duy nhất về quyền:** Vai trò (`vai_tro`), trạng thái (`trang_thai`), `email_da_xac_minh` và `moc_thu_hoi` bắt buộc đọc từ bảng `se214.tai_khoan` và đối chiếu với `se214.phien_xac_thuc` trên kho lưu trữ chính; **tuyệt đối không tin dữ liệu tự khai từ client hoặc state cũ trên UI**.
2. **Kiểm lại quyền dưới khóa trong giao dịch ghi:** Mọi thao tác ghi (`POST`, `PUT`, `PATCH`, `DELETE`) phải qua bộ lọc Origin + Double-Submit CSRF (`CsrfAndOriginProtectionFilter` $\rightarrow$ `403 CSRF_KHONG_HOP_LE` nếu sai/thiếu) và kiểm lại hiệu lực phiên + vai trò + trạng thái tài khoản sau khi giữ khóa dòng trong transaction (`TS1-019`, `AC-09.1`).
3. **Thứ tự ưu tiên mã lỗi phiên/quyền:**
   - Thiếu cookie phiên, phiên đã thu hồi (`thu_hoi_luc IS NOT NULL`), hoặc `phien.moc_thu_hoi_luc_cap != tai_khoan.moc_thu_hoi` $\rightarrow$ `401 CHUA_DANG_NHAP` (UI tự xóa trạng thái đăng nhập).
   - Tài khoản có `trang_thai = 'bi_khoa'` $\rightarrow$ `403 TAI_KHOAN_BI_KHOA` (UI buộc đăng xuất ngay).
   - Tài khoản có `email_da_xac_minh = false` $\rightarrow$ `403 EMAIL_CHUA_XAC_MINH`.
   - Sai vai trò RBAC hoặc vi phạm quyền sở hữu/ràng buộc chủ thể $\rightarrow$ `403 KHONG_DU_QUYEN` (trừ trường hợp đọc URL trực tiếp tài nguyên riêng tư trả `404 KHONG_TIM_THAY` theo Bảng 2 để chống lộ sự tồn tại).
4. **Nguyên tắc quyền tối thiểu (Least Privilege):**
   - `quan_tri_vien` **không** kế thừa quyền duyệt tin/xử lý báo cáo/đề xuất khóa của `kiem_duyet_vien`, và **không** kế thừa quyền đăng/sửa/xóa tin, gửi báo cáo hay mua gói cá nhân của `nguoi_dung`.
   - `kiem_duyet_vien` **không** có quyền khóa/mở khóa hay đổi vai trò tài khoản của `quan_tri_vien`.

### Bảng 1.1 — Phân quyền theo Endpoint và Thao tác nghiệp vụ Sprint 2

| Nhánh | Mã UC / AC | Thao tác nghiệp vụ (`ThaoTacNghiepVu`) | Method & Endpoint chuẩn | Khách | `nguoi_dung` | `kiem_duyet_vien` | `quan_tri_vien` | Ràng buộc chủ sở hữu & Tiền điều kiện đặc thù | Mã lỗi chính khi vi phạm |
|---|---|---|---|---:|---:|---:|---:|---|---|
| **Gợi ý** | `UC-03`<br>`AC-03.1..03.3` | `XEM_GOI_Y_BAT_DONG_SAN` | `GET /api/recommendations` | ✅ | ✅ | ✅ | ✅ | Cookie phiên tùy chọn (phiên hết hạn/bị khóa trên GET công khai vẫn hạ về ngữ cảnh Khách theo `PX-02`). Query đọc bắt buộc lọc trực tiếp `BR-01` (`tin.trang_thai = 'da_duyet'` VÀ `chu.trang_thai = 'hoat_dong'`) và `BR-06` (loại tin có gói đang hiệu lực thực tế khỏi danh sách tự nhiên). | `400 DU_LIEU_KHONG_HOP_LE` (tham số tọa độ/trang sai) |
| **Gợi ý** | `UC-03`<br>`AC-03.2` | `GHI_NHAN_LUOT_XEM_TIN` | `POST /api/listings/{listingId}/views` | ❌ | ✅ | ❌ | ❌ | Phiên `nguoi_dung` hợp lệ (`hoat_dong`, `email_da_xac_minh`). Tin mục tiêu phải đang công khai theo `BR-01` (tin không tồn tại hoặc không công khai trả `404 KHONG_TIM_THAY`). | `401`, `403`, `404 KHONG_TIM_THAY` |
| **Báo cáo** | `UC-09`<br>`AC-06.2` | `TAI_ANH_BANG_CHUNG_BAO_CAO` | `POST /api/report-images` | ❌ | ✅ | ❌ | ❌ | Chỉ `nguoi_dung` (`hoat_dong`, `email_da_xac_minh`). Kiểm nội dung ảnh thật `JPG/PNG/WebP`, kích thước `1..10.000.000` byte (từ chối PDF/sai MIME). Ảnh mới ở trạng thái `tam` thuộc `chu_id = principal.taiKhoanId`. | `401`, `403`, `400/413/415` (tệp không hợp lệ) |
| **Báo cáo** | `UC-09`<br>`AC-06.2`<br>`QD-S2-02` | `DOC_ANH_BANG_CHUNG_BAO_CAO` | `GET /api/report-images/{imageId}/content` | ❌ | ✅ *(chính chủ ảnh)* | ✅ *(ảnh đã gắn báo cáo)* | ✅ *(ảnh thuộc căn cứ phiếu khóa)* | Chi tiết theo **Bảng 2**. Mọi trường hợp không thỏa điều kiện đọc (kể cả Khách và **chủ tin bị báo cáo**) đều trả `404 KHONG_TIM_THAY` để không lộ mã ảnh. Luôn gắn `Cache-Control: no-store`, `Vary: Cookie`. | `404 KHONG_TIM_THAY` |
| **Báo cáo** | `UC-09`<br>`AC-06.1..06.4`<br>`QD-S2-01` | `GUI_BAO_CAO_VI_PHAM` | `POST /api/listings/{listingId}/reports` | ❌ | ✅ *(khác chủ tin)* | ❌ | ❌ | 1. Chỉ `nguoi_dung` (`hoat_dong`, `email_da_xac_minh`).<br>2. **Chặn tự báo cáo (`QD-S2-01`):** `principal.taiKhoanId != tin.chu_id` $\rightarrow$ `403 KHONG_DU_QUYEN`.<br>3. Sau khi tra Replay không trùng: nếu tin vừa chuyển `cho_kiem_duyet` $\rightarrow$ `409 TRANG_THAI_KHONG_HOP_LE` (báo gửi lại sau khi duyệt); nếu tin không tồn tại / `da_xoa` / `bi_go_vi_pham` / `bi_tu_choi` hoặc chủ tin `bi_khoa` $\rightarrow$ `404 KHONG_TIM_THAY` (không lộ trạng thái riêng tư).<br>4. Cửa sổ 24h trượt trên `(nguoi_gui_id, tin_id)` có `< 5` báo cáo; nếu `>= 5` $\rightarrow$ `429` / `409 VUOT_HAN_MUC_BAO_CAO`.<br>5. Tối đa 5 ảnh bằng chứng `tam` thuộc chính `nguoi_gui_id`. | `401`, `403 KHONG_DU_QUYEN`, `404 KHONG_TIM_THAY`, `409 TRANG_THAI_KHONG_HOP_LE`, `409/429 VUOT_HAN_MUC_BAO_CAO`, `409 XUNG_DOT_MA_Y_DINH` |
| **Báo cáo** | `UC-09`, `UC-14`<br>`Q06/Q08` | `XEM_THONG_BAO_CUA_TOI` | `GET /api/me/notifications` | ❌ | ✅ | ❌ | ❌ | Chỉ đọc thông báo có `nguoi_nhan_id == principal.taiKhoanId`. Không nhận `taiKhoanId` từ query param. | `401`, `403` |
| **Tin cá nhân** | `UC-04`<br>`AC-04.3`<br>`Q36` | *(Mở rộng)* `XEM_TIN_CUA_TOI_CHI_DOC` | `GET /api/me/listings`<br>`GET /api/me/listings/{listingId}` | ❌ | ✅ *(chính chủ)* | ❌ | ❌ | Chủ tin có tài khoản `hoat_dong` được xem tin `da_xoa` / `bi_go_vi_pham` của chính mình kèm `lyDoNgungHienThi` ở chế độ **chỉ đọc** (không sửa, không xóa tiếp, không khôi phục). Người khác gọi trả `404 KHONG_TIM_THAY`. Chủ tin `bi_khoa` bị chặn `403 TAI_KHOAN_BI_KHOA`. | `401`, `403`, `404 KHONG_TIM_THAY` |
| **Kiểm duyệt BC** | `UC-14`<br>`AC-08.2` | `XEM_VA_XU_LY_BAO_CAO_VI_PHAM` | `GET /api/moderation/reports`<br>`GET /api/moderation/reports/{reportId}` | ❌ | ❌ | ✅ | ❌ | Chỉ `kiem_duyet_vien` (`hoat_dong`, `email_da_xac_minh`). Chi tiết trả đúng **phiên bản đã duyệt gần nhất** (`tin.phien_ban_da_duyet_id`) tại thời điểm đọc (sửa chưa duyệt xét bản cũ; sửa đã duyệt xét bản mới) kèm `ETag`. | `401`, `403 KHONG_DU_QUYEN`, `404 KHONG_TIM_THAY` |
| **Kiểm duyệt BC** | `UC-14`<br>`AC-08.1..08.3` | `XEM_VA_XU_LY_BAO_CAO_VI_PHAM` | `POST /api/moderation/reports/{reportId}/decision` | ❌ | ❌ | ✅ | ❌ | Chỉ `kiem_duyet_vien`. Bắt buộc `Idempotency-Key`, `If-Match` và `phienBanDaDuyetDaXemId` + `ketLuan` (`xac_nhan_vi_pham` / `bac_bo`) + `lyDo`. Nếu `tin.trang_thai == 'da_xoa'` trước lúc quyết định $\rightarrow$ tự đóng báo cáo (`da_dong`, `ly_do_dong = 'tin_da_xoa'`), không tăng vi phạm (`AC-08.3`). Nếu `phien_ban_da_duyet_id` vừa đổi $\rightarrow$ `412 PHIEN_BAN_CU` yêu cầu tải lại (`AC-08.2`). | `401`, `403`, `404`, `409 BAO_CAO_DA_XU_LY`, `412 PHIEN_BAN_CU`, `428 THIEU_IF_MATCH` |
| **Kiểm duyệt BC** | `UC-14`<br>`AC-08.4` | `TAO_YEU_CAU_KHOA_TAI_KHOAN` | `POST /api/moderation/lock-requests` | ❌ | ❌ | ✅ | ❌ | Chỉ `kiem_duyet_vien`. Không được đề xuất khóa tài khoản `quan_tri_vien`. Chỉ tạo phiếu `cho_admin` khi: (a) tổng `su_kien_vi_pham` của chủ tin `>= 3`, (b) chưa có phiếu `cho_admin` đang mở, (c) nếu từng có phiếu `bi_tu_choi` thì phải có ít nhất 1 `su_kien_vi_pham` phát sinh mới sau mốc căn cứ của phiếu bị từ chối gần nhất (`Q03`, `AC-08.4`). | `401`, `403 KHONG_DU_QUYEN`, `409 CHUA_DU_DIEU_KIEN_DE_XUAT_KHOA`, `409 YEU_CAU_KHOA_DANG_CHO` |
| **Quản trị** | `UC-15`, `UC-16`<br>`AC-09`, `AC-15.1` | `TAO_CAP_NHAT_KHOA_MO_KHOA_TAI_KHOAN` | `GET /api/admin/accounts`<br>`POST /api/admin/accounts` | ❌ | ❌ | ❌ | ✅ | Chỉ `quan_tri_vien` (`hoat_dong`, `email_da_xac_minh`). Tạo tài khoản hoặc cấp vai trò `nguoi_dung` / `kiem_duyet_vien` / `quan_tri_vien` có ghi `nhat_ky_kiem_toan`. | `401`, `403 KHONG_DU_QUYEN`, `409 EMAIL_DA_TON_TAI` |
| **Quản trị** | `UC-16`<br>`S2-07`, `S2-14` | `CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI` | `PATCH /api/admin/accounts/{accountId}` | ❌ | ❌ | ❌ | ✅ | Chỉ `quan_tri_vien`. **Chốt chặn tự hạ quyền:** Nếu `accountId == principal.taiKhoanId` và `vaiTroMoi != 'quan_tri_vien'` $\rightarrow$ từ chối `409 KHONG_THE_TU_HA_QUYEN` (hoặc `403`). Ghi `nhat_ky_kiem_toan` trước/sau. Phiên đang mở của tài khoản bị đổi vai trò sẽ thấy vai trò mới ngay ở lần kiểm quyền kế tiếp. | `401`, `403`, `404`, `409 KHONG_THE_TU_HA_QUYEN` |
| **Quản trị** | `UC-17`<br>`AC-09.1`, `AC-09.3` | `XEM_VA_QUYET_DINH_YEU_CAU_KHOA` | `GET /api/admin/lock-requests`<br>`POST /api/admin/lock-requests/{requestId}/decision` | ❌ | ❌ | ❌ | ✅ | Chỉ `quan_tri_vien`. **Chốt chặn tự khóa:** `phieu.tai_khoan_bi_de_xuat_id != principal.taiKhoanId` ($\rightarrow$ `409 KHONG_THE_TU_KHOA`). Bắt buộc `lyDo`. Khi `chap_thuan`: đổi phiếu `da_duyet`, tài khoản `bi_khoa`, `moc_thu_hoi + 1`, thu hồi mọi phiên chưa thu hồi (`ly_do_thu_hoi = 'khoa_tai_khoan'`), ghi audit cùng transaction; **giữ nguyên trạng thái tin & gói**. Khi `tu_choi`: đổi phiếu `bi_tu_choi`, lưu mốc căn cứ vi phạm đã xét. | `401`, `403`, `404`, `409 YEU_CAU_KHOA_DA_XU_LY`, `409 KHONG_THE_TU_KHOA` |
| **Quản trị** | `UC-18`<br>`AC-09.2`, `AC-09.3` | `MO_KHOA_TAI_KHOAN_CO_CAN_CU` | `POST /api/admin/accounts/{accountId}/unlock` | ❌ | ❌ | ❌ | ✅ | Chỉ `quan_tri_vien`. Tài khoản mục tiêu phải đang `bi_khoa`. Bắt buộc có `lyDo` và `canCuEmail` (căn cứ kháng cáo qua email ngoài hệ thống được chấp thuận — `Q34`). Đổi `trang_thai = 'hoat_dong'`, **giữ nguyên `moc_thu_hoi` (không khôi phục phiên/token cũ)**, giữ nguyên trạng thái tin/gói/số dư ví, ghi audit. | `401`, `403`, `404`, `400 DU_LIEU_KHONG_HOP_LE` (thiếu căn cứ email), `409 TRANG_THAI_KHONG_HOP_LE` |
| **Ví & Gói** | `UC-11`<br>`AC-11.4` | `XEM_VI_VA_SO_GIAO_DICH` | `GET /api/me/wallet` | ❌ | ✅ | ❌ | ❌ | Chỉ `nguoi_dung` (`hoat_dong`, `email_da_xac_minh`). Đọc ví và lịch sử biến động của chính `principal.taiKhoanId`. Tài khoản `bi_khoa` bị chặn `403 TAI_KHOAN_BI_KHOA` / `401`. | `401`, `403` |
| **Ví & Gói** | `UC-11`<br>`AC-11.5`, `AC-11.6` | `XEM_BAO_GIA_GOI_DAY_TIN` | `GET /api/listings/{listingId}/boost-quote` | ❌ | ✅ *(chính chủ tin)* | ❌ | ❌ | Chỉ `nguoi_dung` (`hoat_dong`, `email_da_xac_minh`) và `tin.chu_id == principal.taiKhoanId` (khác chủ trả `403 KHONG_DU_QUYEN`, không tồn tại trả `404`). Báo giá gói `7_ngay` (`49.000` VND) / `30_ngay` (`179.000` VND), trạng thái gói hiện tại và cờ `canXacNhanCongDon` (chỉ `true` nếu gói hiện có còn hiệu lực thực tế `het_han_luc > now`). | `401`, `403 KHONG_DU_QUYEN`, `404 KHONG_TIM_THAY` |
| **Ví & Gói** | `UC-11`<br>`AC-11.4..11.6`<br>`BR-07` | `KICH_HOAT_GOI_DAY_TIN` | `POST /api/listings/{listingId}/boost-purchases` | ❌ | ✅ *(chính chủ tin)* | ❌ | ❌ | 1. Chỉ `nguoi_dung` (`hoat_dong`, `email_da_xac_minh`) và `tin.chu_id == principal.taiKhoanId`.<br>2. Sau khi tra Replay không trùng: tin phải đang `da_duyet` (`cho_kiem_duyet` / `bi_tu_choi` / `da_xoa` / `bi_go_vi_pham` $\rightarrow$ `409 TRANG_THAI_KHONG_HOP_LE`).<br>3. Nếu tin đang có gói `dang_hoat_dong` còn hạn thực tế (`het_han_luc > now`) mà `dongYCongDon != true` $\rightarrow$ `409 CAN_XAC_NHAN_CONG_DON`.<br>4. Số dư ví `>= giaGoi` (nếu thiếu $\rightarrow$ `409 SO_DU_KHONG_DU`, tuyệt đối không âm ví). | `401`, `403`, `404`, `409 TRANG_THAI_KHONG_HOP_LE`, `409 CAN_XAC_NHAN_CONG_DON`, `409 SO_DU_KHONG_DU`, `409 XUNG_DOT_MA_Y_DINH` |

---

## 2. Bảng 2 — Quyền đọc tài nguyên riêng tư & Chống tự khai đặc quyền (Mass Assignment)

### 2.1. Quy tắc truy cập trực tiếp URL tài nguyên riêng tư

Mọi phản hồi đọc tài nguyên không công khai hoặc phụ thuộc phiên bắt buộc đính kèm `Cache-Control: no-store` và `Vary: Cookie`. Khi từ chối quyền đọc tài nguyên định danh bằng UUID trên URL trực tiếp, trả **`404 KHONG_TIM_THAY`** (nhất quán với `QuyenDocAnh` và `AnhTinApiTests` của Sprint 1) để không tiết lộ tài nguyên có tồn tại hay không.

| Tài nguyên | Trạng thái tài nguyên | Ai được phép đọc (`200 OK`) | Ai bị chặn (`404 KHONG_TIM_THAY` / `403`) |
|---|---|---|---|
| **Ảnh tin đăng** (`/api/images/{imageId}/content`) | `tam` | Chỉ `nguoi_dung` có `taiKhoanId == anh.chu_id`. | Khách, `nguoi_dung` khác, `kiem_duyet_vien`, `quan_tri_vien`, ảnh `cho_don`. |
| **Ảnh tin đăng** (`/api/images/{imageId}/content`) | `da_gan` | 1. Công khai (mọi vai trò + Khách) khi `tin.trang_thai == 'da_duyet'` VÀ `chuTin.trang_thai == 'hoat_dong'` VÀ `anh.phien_ban_tin_id == tin.phien_ban_da_duyet_id`.<br>2. `nguoi_dung` chính chủ (`taiKhoanId == anh.chu_id`, tài khoản `hoat_dong`).<br>3. `kiem_duyet_vien`: (a) bản hiện tại khi `tin.trang_thai == 'cho_kiem_duyet'`, **hoặc** (b) bản `phien_ban_da_duyet_id` khi đang xử lý báo cáo vi phạm (`UC-14`, kể cả lúc tin đang `cho_kiem_duyet` bản sửa hoặc `bi_tu_choi` bản sửa). | Khách và người dùng khác khi tin `cho_kiem_duyet`, `bi_tu_choi`, `da_xoa`, `bi_go_vi_pham`, hoặc khi chủ tin `bi_khoa`, hoặc ảnh thuộc bản cũ không còn là `phien_ban_da_duyet_id`. |
| **Ảnh bằng chứng báo cáo** (`/api/report-images/{imageId}/content`) — **`QD-S2-02`** | `tam` | Chỉ `nguoi_dung` có `taiKhoanId == anh.nguoi_tai_id` (tài khoản `hoat_dong`). | Khách, mọi người dùng khác, **chủ tin bị báo cáo**, `kiem_duyet_vien`, `quan_tri_vien`. |
| **Ảnh bằng chứng báo cáo** (`/api/report-images/{imageId}/content`) — **`QD-S2-02`** | `da_gan` (đã đính vào báo cáo) | 1. `nguoi_dung` là người gửi báo cáo (`taiKhoanId == bao_cao.nguoi_gui_id`, tài khoản `hoat_dong`).<br>2. `kiem_duyet_vien` (`hoat_dong`).<br>3. `quan_tri_vien` (`hoat_dong`) **chỉ khi** báo cáo chứa ảnh đó đã `da_xac_nhan` và nằm trong danh sách căn cứ của một `yeu_cau_khoa_tai_khoan`. | Khách, người dùng khác, **chủ tin bị báo cáo** (dù tin là của họ, họ tuyệt đối không được xem ảnh bằng chứng hay danh tính người tố cáo), và `quan_tri_vien` đối với báo cáo chưa thuộc căn cứ phiếu khóa. |
| **Tin đã xóa / bị gỡ vi phạm** | `da_xoa`, `bi_go_vi_pham` | Chỉ `nguoi_dung` chính chủ (`taiKhoanId == tin.chu_id`, tài khoản `hoat_dong`) qua `/api/me/listings...` ở chế độ chỉ đọc (xem được `lyDoNgungHienThi`). | Khách, người dùng khác trên `/api/listings/{listingId}` (trả `404`), danh sách yêu thích `/api/me/favorites` (tự động ẩn theo `AC-04.2`), tìm kiếm và gợi ý (`AC-02.1`, `AC-03.3`). |
| **Phiếu đề xuất khóa tài khoản** | `cho_admin`, `da_duyet`, `bi_tu_choi` | `kiem_duyet_vien` (xem trạng thái đề xuất tại luồng xử lý vi phạm) và `quan_tri_vien` (xem/xử lý tại `/api/admin/lock-requests`). | Khách và mọi `nguoi_dung` (kể cả người bị đề xuất khóa). |

### 2.2. Các trường tự khai bị cấm trong Request Payload từ Client

- **Áp dụng cho mọi endpoint của Khách, `nguoi_dung` và `kiem_duyet_vien`:** Bất kỳ trường nào trong danh sách `CAC_TRUONG_TU_KHAI_BI_CAM` xuất hiện trong JSON body đều bị từ chối ngay với `400 DU_LIEU_KHONG_HOP_LE` (`truongLoi[].ma = "TRUONG_KHONG_DUOC_PHEP"`):
  - Nhóm định danh & quyền hạn (đã có ở S1): `vaiTro`, `vai_tro`, `role`, `roles`, `isAdmin`, `is_admin`, `trangThai`, `trang_thai`, `emailDaXacMinh`, `email_da_xac_minh`, `mocThuHoi`, `moc_thu_hoi`, `chuId`, `chu_id`, `ownerId`, `owner_id`, `taiKhoanId`, `tai_khoan_id`, `nguoiKiemDuyetId`, `nguoi_kiem_duyet_id`, `nguoiXuLyId`, `nguoi_xu_ly_id`.
  - Nhóm bổ sung Sprint 2 (Báo cáo, Quản trị, Ví & Gói): `nguoiGuiId`, `nguoi_gui_id`, `nguoiQuyetDinhId`, `nguoi_quyet_dinh_id`, `nguoiThucHienId`, `nguoi_thuc_hien_id`, `soDu`, `so_du`, `soDuVnd`, `so_du_vnd`, `giaDaMua`, `gia_da_mua`, `thoiLuongConLai`, `thoi_luong_con_lai`, `hetHanLuc`, `het_han_luc`, `soLanViPham`, `so_lan_vi_pham`.
- **Ngoại lệ có kiểm soát duy nhất:** Hai endpoint quản trị `POST /api/admin/accounts` và `PATCH /api/admin/accounts/{accountId}` (sau khi đã xác nhận người gọi là `quan_tri_vien`) được phép đọc trường `vaiTro` từ DTO quản trị chuyên biệt (`TaoTaiKhoanQuanTriRequest` / `CapNhatTaiKhoanQuanTriRequest`), nhưng vẫn cấm client tự truyền `id`, `mocThuHoi`, `soDuVnd`, `supabaseAuthUserId`.

---

## 3. Bảng 3 — Quy tắc Replay (`Idempotency-Key` / `ma_y_dinh`) giữa các nhánh

### 3.1. Giao thức chung cho mọi thao tác ghi có Replay

1. **Header bắt buộc:** `Idempotency-Key` (chuỗi trim khác rỗng, tối đa 128 ký tự; thiếu hoặc rỗng trả `400 DU_LIEU_KHONG_HOP_LE`).
2. **Cách tính `du_lieu_bam`:** Băm `SHA-256` (hex chữ thường) trên chuỗi JSON đã chuẩn hóa thứ tự khóa (`ChuanHoaJson.toJsonString(...)`) bao gồm **định danh tài nguyên trên URL** (ví dụ `listingId`, `reportId`, `requestId`, `accountId`) + **các trường nghiệp vụ đã chuẩn hóa của body** (không băm `If-Match` vào `du_lieu_bam` ở những luồng cần cho phép retry bất chấp version đã tăng sau lần ghi đầu, hoặc nếu client giữ nguyên `If-Match` của lần gọi đầu thì vẫn khớp).
   > **Lưu ý quan trọng về `If-Match` khi Replay:** Kiểm tra Replay phải diễn ra **TRƯỚC** bước kiểm tra `If-Match` so với `phien_ban_cap_nhat` hiện tại của bản ghi! Nhờ đó, khi lần gọi 1 đã commit làm `phien_ban_cap_nhat` tăng từ `0` lên `1` nhưng mất mạng, client gửi lại nguyên request cũ (kèm `If-Match: "0"` và cùng `Idempotency-Key`) sẽ nhận lại đúng kết quả `ETag: "1"` thay vì bị báo nhầm `412 PHIEN_BAN_CU`.
3. **Hành vi khi tìm thấy bản ghi `ma_y_dinh` đã commit của cùng `(tai_khoan_id, loai_thao_tac, ma_y_dinh)`:**
   - **`du_lieu_bam` khớp:** Trả lại đúng HTTP status (`200` / `201`) và payload kết quả từ snapshot đã lưu; **không** cập nhật lại bảng nghiệp vụ, **không** trừ tiền/tăng bộ đếm lần hai, **không** ghi thêm dòng `nhat_ky_kiem_toan` hay `su_kien_goi` / `su_kien_vi_pham`.
   - **`du_lieu_bam` khác:** Trả lỗi **`409 XUNG_DOT_MA_Y_DINH`** (*"Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."*), rollback không ghi gì cả.

### 3.2. Phân bổ bảng lưu trữ Replay theo quyết định `QD-S2-03 (3A)`

#### A. Nhánh Báo cáo & Nhánh Quản trị (Migration `V2/V3` — mở rộng `se214.yeu_cau_ghi`)
Cập nhật bảng `se214.yeu_cau_ghi` tương thích ngược với 4 thao tác Sprint 1:
- Cho phép `tin_ket_qua_id`, `trang_thai_ket_qua`, `phien_ban_cap_nhat_ket_qua` nhận `NULL` đối với các thao tác không phải thao tác tin Sprint 1 (giữ `CHECK` bắt buộc `NOT NULL` khi `loai_thao_tac IN ('dang_tin','sua_tin','xoa_tin','quyet_dinh')`).
- Thêm cột `ket_qua_json jsonb CHECK (ket_qua_json IS NULL OR jsonb_typeof(ket_qua_json) = 'object')` (bắt buộc `NOT NULL` với các `loai_thao_tac` mới của Sprint 2).
- Mở rộng `CHECK` của `loai_thao_tac` gồm:
  - Sprint 1 (giữ nguyên): `'dang_tin'`, `'sua_tin'`, `'xoa_tin'`, `'quyet_dinh'`
  - Sprint 2 (bổ sung): `'gui_bao_cao'`, `'xu_ly_bao_cao'`, `'tao_yeu_cau_khoa'`, `'quyet_dinh_yeu_cau_khoa'`, `'mo_khoa_tai_khoan'`, `'tao_tai_khoan_quan_tri'`, `'cap_nhat_tai_khoan_quan_tri'`
- Giữ nguyên khóa duy nhất: `UNIQUE (tai_khoan_id, loai_thao_tac, ma_y_dinh)`.

#### B. Nhánh Ví & Gói đẩy tin (Migration `V4` — lưu tại `se214.giao_dich_vi`)
- Trên bảng `se214.giao_dich_vi` (hoặc bảng ý định mua đi kèm trong `V4`):
  - Lưu `ma_y_dinh text NOT NULL`, `du_lieu_bam text NOT NULL`, `ket_qua_json jsonb` (snapshot DTO kết quả mua gói: `giaoDichId`, `lanMuaId`, `goiId`, `soDuSauVnd`, `trangThaiGoi`, `thoiLuongConLaiGiay`, `hetHanLuc`).
  - Ràng buộc duy nhất: `UNIQUE (vi_id, loai, ma_y_dinh)` (với `loai = 'mua_goi'`).

### 3.3. Bảng chi tiết quy tắc Replay theo từng thao tác ghi Sprint 2

| Nhánh | Thao tác | Hằng `loai_thao_tac` / Khóa Unique | Thành phần đưa vào `du_lieu_bam` | Điểm đặt bước kiểm tra Replay & Yêu cầu AC đặc biệt |
|---|---|---|---|---|
| **Báo cáo** | Gửi báo cáo vi phạm (`UC-09`) | `yeu_cau_ghi`: `(nguoi_gui_id, 'gui_bao_cao', ma_y_dinh)` | `{ listingId, lyDo, moTa, anhIds }` | **Bắt buộc kiểm tra Replay ngay sau khi khóa dòng và TRƯỚC khi kiểm tra `tin.trang_thai == 'da_duyet'`, `chu.trang_thai == 'hoat_dong'` và bộ đếm `< 5` trong 24h (`AC-06.4`).**<br>Hệ quả: Nếu lần 1 đã lưu báo cáo rồi chủ tin sửa tin sang `cho_kiem_duyet` (hoặc xóa tin), lần retry cùng `ma_y_dinh` **vẫn trả lại đúng báo cáo cũ đã nhận**, không báo lỗi tin hết công khai và không tạo báo cáo thứ hai. |
| **Kiểm duyệt BC** | Kết luận báo cáo (`UC-14`) | `yeu_cau_ghi`: `(kdv_id, 'xu_ly_bao_cao', ma_y_dinh)` | `{ reportId, phienBanDaDuyetDaXemId, ketLuan, lyDo }` | Kiểm tra Replay ngay sau khi khóa dòng tin & báo cáo, **trước** khi kiểm `bao_cao.trang_thai == 'cho_xu_ly'` và `If-Match`. Nếu khác `ma_y_dinh` mà báo cáo đã xử lý bởi KDV khác (hoặc lần khác) $\rightarrow$ trả `409 BAO_CAO_DA_XU_LY`, không nhân đôi `su_kien_vi_pham` (`AC-08.1`). |
| **Kiểm duyệt BC** | Gửi phiếu đề xuất khóa (`UC-14`) | `yeu_cau_ghi`: `(kdv_id, 'tao_yeu_cau_khoa', ma_y_dinh)` | `{ taiKhoanBiDeXuatId, lyDo, danhSachSuKienViPhamIds }` | Kiểm tra Replay sau khi khóa `tai_khoan` mục tiêu và `yeu_cau_khoa_tai_khoan`, **trước** khi kiểm tra đã có phiếu `cho_admin` hay chưa. KDV bấm đúp chỉ tạo 1 phiếu, nhận lại cùng phiếu `cho_admin` (`AC-08.4`). |
| **Quản trị** | Tạo tài khoản / cấp quyền (`UC-15/16`) | `yeu_cau_ghi`: `(admin_id, 'tao_tai_khoan_quan_tri', ma_y_dinh)` | `{ email, hoTen, soDienThoai, vaiTro }` | Tránh tạo trùng hoặc báo lỗi `EMAIL_DA_TON_TAI` giả khi retry sau khi đã tạo thành công. (Lưu ý: gọi Supabase Auth tạo user ngoài DB lock, đối chiếu email nếu retry). |
| **Quản trị** | Cập nhật hồ sơ & vai trò (`UC-16`) | `yeu_cau_ghi`: `(admin_id, 'cap_nhat_tai_khoan_quan_tri', ma_y_dinh)` | `{ accountId, hoTen, soDienThoai, vaiTro }` | Kiểm tra Replay sau khi khóa `tai_khoan`, tránh ghi 2 dòng `nhat_ky_kiem_toan` trùng lặp khi retry. |
| **Quản trị** | Duyệt / từ chối phiếu khóa (`UC-17`) | `yeu_cau_ghi`: `(admin_id, 'quyet_dinh_yeu_cau_khoa', ma_y_dinh)` | `{ requestId, quyetDinh, lyDo }` | Kiểm tra Replay ngay sau khi khóa `tai_khoan` & phiếu khóa, **trước** khi kiểm `phieu.trang_thai == 'cho_admin'`. Đảm bảo retry sau khi duyệt khóa **không tăng `moc_thu_hoi` lần thứ hai** và không ghi đúp audit. |
| **Quản trị** | Mở khóa tài khoản (`UC-18`) | `yeu_cau_ghi`: `(admin_id, 'mo_khoa_tai_khoan', ma_y_dinh)` | `{ accountId, lyDo, canCuEmail }` | Kiểm tra Replay ngay sau khi khóa `tai_khoan`, **trước** khi kiểm `tai_khoan.trang_thai == 'bi_khoa'`. Retry sau khi đã mở khóa thành công vẫn trả `200 OK` kết quả cũ thay vì lỗi `409`. |
| **Ví & Gói** | Mua / cộng dồn gói đẩy tin (`UC-11`) | `giao_dich_vi`: `(vi_id, 'mua_goi', ma_y_dinh)` | `{ listingId, maGoi, dongYCongDon }` | **Bắt buộc kiểm tra Replay ngay sau khi giữ khóa `tai_khoan` + `vi_dien_tu` + `tin_bat_dong_san` và TRƯỚC khi kiểm số dư, trạng thái tin `da_duyet` hay `dongYCongDon` (`AC-11.5`).**<br>Hệ quả: Một `ma_y_dinh` chỉ trừ ví và cộng thời lượng **đúng 1 lần duy nhất**; kể cả khi sau lần mua đầu số dư ví đã về `0` hoặc chủ tin đã sửa tin sang `cho_kiem_duyet`, retry cùng `ma_y_dinh` vẫn trả lại biên nhận lần mua đầu. |

---

## 4. Bảng 4 — Thứ tự khóa (`Lock Order`) toàn cục chống Deadlock & Race Condition

### 4.1. Thứ tự phân cấp khóa dòng toàn cục (9 bậc bất biến)

Mọi giao dịch ghi trong **cả 4 nhánh (Tin đăng S1, Báo cáo, Quản trị, Ví & Gói)** khi cần khóa nhiều bảng bằng `SELECT ... FOR UPDATE` trong PostgreSQL **BẮT BUỘC** đi theo chiều mũi tên từ **Bậc 1 xuống Bậc 9** (được phép bỏ qua bậc không dùng tới, nhưng **tuyệt đối cấm khóa ngược từ bậc lớn về bậc nhỏ**):

```text
[1] se214.tai_khoan                  (nếu khóa >= 2 tài khoản: BẮT BUỘC WHERE id IN (...) ORDER BY id ASC FOR UPDATE)
 ↓
[2] se214.phien_xac_thuc             (ORDER BY id ASC FOR UPDATE)
 ↓
[3] se214.yeu_cau_khoa_tai_khoan     (ORDER BY id ASC FOR UPDATE)
 ↓
[4] se214.vi_dien_tu                 (FOR UPDATE)
 ↓
[5] se214.han_muc_thang              (FOR UPDATE)
 ↓
[6] se214.tin_bat_dong_san           (ORDER BY id ASC FOR UPDATE)
 ↓
[7] se214.anh_tin / anh_bang_chung   (ORDER BY id ASC FOR UPDATE)
 ↓
[8] se214.goi_day_tin                (FOR UPDATE — gói chưa kết thúc của tin)
 ↓
[9] se214.bao_cao_vi_pham            (ORDER BY id ASC FOR UPDATE) / se214.su_kien_vi_pham
```

### 4.2. Năm quy tắc an toàn khóa bắt buộc cho mọi nhánh

1. **Khóa đa tài khoản theo `ORDER BY id ASC`:** Khi người thực hiện `A` tác động lên tài khoản `B` (ví dụ: Admin `A` đổi vai trò/khóa/mở khóa tài khoản `B`; hoặc người gửi `A` báo cáo tin của chủ `B`), nếu giao dịch cần `FOR UPDATE` trên cả dòng `A` (kiểm lại quyền người gọi) lẫn dòng `B` (kiểm trạng thái mục tiêu), **phải khóa theo thứ tự `UUID` tăng dần (`ORDER BY id ASC`)** hoặc dùng một câu `SELECT ... FROM se214.tai_khoan WHERE id IN (?, ?) ORDER BY id ASC FOR UPDATE`. Không bao giờ khóa `A` rồi mới khóa `B` theo vai trò vì nếu `A` và `B` tác động chéo sẽ gây Deadlock.
2. **Đồng bộ khóa JVM khi chạy chung `CurrentPermissionAndCsrfService`:**
   - Trong Sprint 1, `CurrentPermissionAndCsrfService` giữ `ReentrantLock` theo thứ tự `accountLock(principalId)` $\rightarrow$ `sessionLock(phienId)` $\rightarrow$ `resourceLock(taiNguyenId)` rồi mới mở transaction SQL khóa `tai_khoan` $\rightarrow$ `phien_xac_thuc`.
   - Với các luồng Quản trị ở Sprint 2 (`UC-16/17/18`) khi Admin `A` khóa hoặc đổi vai trò tài khoản `B`: nếu dùng `sharedAccountLock(UUID)` trong JVM thì cũng phải acquire khóa của `min(A, B)` trước, `max(A, B)` sau (nếu `A != B`), rồi khóa `sessionLock`, đồng nhất với thứ tự SQL ở Quy tắc 1.
3. **Chống Race Condition trên tập rỗng (Empty-Set Lock Pitfall):**
   - Trong PostgreSQL (`READ COMMITTED`), câu lệnh `SELECT COUNT(*) ... FROM bao_cao_vi_pham WHERE ...` hoặc `SELECT ... FROM yeu_cau_khoa_tai_khoan WHERE trang_thai = 'cho_admin' FOR UPDATE` khi **chưa có dòng nào tồn tại** sẽ **không khóa khoảng trống (no gap lock)**!
   - Vì vậy, **bắt buộc `SELECT ... FOR UPDATE` trên dòng cha luôn tồn tại** trước khi đếm hoặc chèn:
     - Đếm 5 báo cáo/24h (`AC-06.1`): Khóa dòng `tai_khoan` (người gửi) và dòng `tin_bat_dong_san` (tin bị báo cáo) trước $\rightarrow$ hai request gửi đồng thời bắt buộc xếp hàng tuần tự tại dòng cha, cái thứ 5 vào trước thấy `count = 4` lưu thành `5`, cái thứ 6 vào sau thấy `count = 5` bị chặn chính xác 100%.
     - Tạo sự kiện vi phạm (`AC-08.1`): Khóa dòng `tin_bat_dong_san` trước + ràng buộc DB `UNIQUE (tin_id)` trên `su_kien_vi_pham`.
     - Tạo phiếu đề xuất khóa (`AC-08.4`): Khóa dòng `tai_khoan` (chủ tin bị đề xuất) trước + partial unique index `UNIQUE (tai_khoan_bi_de_xuat_id) WHERE trang_thai = 'cho_admin'` trên `yeu_cau_khoa_tai_khoan`.
     - Tạo/cộng dồn gói đẩy tin (`AC-11.5`): Khóa dòng `vi_dien_tu` và `tin_bat_dong_san` trước + partial unique index `uq_goi_chua_ket_thuc` đã có sẵn ở `V1`.
4. **Không gọi dịch vụ ngoài trong lúc giữ khóa DB:**
   - Upload file lên Supabase Storage (`POST /api/report-images`), gọi Supabase Auth tạo tài khoản hoặc thu hồi token phía provider **phải thực hiện ngoài phạm vi giữ khóa DB** (nếu ghi DB rollback thì đánh dấu ảnh tạm `cho_don` hoặc dọn bất đồng bộ như cơ chế `AnhTinService` của Sprint 1; thu hồi phiên provider dùng cờ `can_thu_hoi_provider = true` sau commit).
5. **Giải thích tại sao thứ tự này triệt tiêu Deadlock ở các ca kiểm thử chéo (`TS2-08`, `TS2-11..13`):**
   - *Ca "Admin khóa tài khoản xen lúc User đang mua gói" (`TS2-11..13`):*
     - Luồng Admin khóa khóa `[1] tai_khoan` $\rightarrow$ `[2] phien_xac_thuc` $\rightarrow$ `[3] yeu_cau_khoa_tai_khoan`.
     - Luồng User mua gói khóa `[1] tai_khoan` $\rightarrow$ `[2] phien_xac_thuc` $\rightarrow$ `[4] vi_dien_tu` $\rightarrow$ `[6] tin_bat_dong_san` $\rightarrow$ `[8] goi_day_tin`.
     - Cả hai đều chạm `[1] tai_khoan` đầu tiên $\rightarrow$ giao dịch đến sau chờ ngay ở `[1]`: nếu Admin commit trước, User vào sau thấy `trang_thai = 'bi_khoa'` / phiên đã thu hồi nên rollback không trừ ví; nếu User commit trước, Admin vào sau khóa tài khoản và giữ nguyên gói vừa mua theo đúng `AC-09.3`. **Không bao giờ có chu trình chờ.**
   - *Ca "Chủ tin sửa/xóa tin xen lúc KDV xử lý báo cáo hoặc User gửi báo cáo" (`TS2-03..06`):*
     - Mọi luồng đều đi qua `[1] tai_khoan` $\rightarrow$ `[6] tin_bat_dong_san` $\rightarrow$ `[8] goi_day_tin` $\rightarrow$ `[9] bao_cao_vi_pham` cùng một chiều từ trên xuống dưới.

### 4.3. Kịch bản khóa chi tiết theo từng Use Case Sprint 2

| Use Case | Chuỗi bước trong 1 Transaction (`@Transactional`) theo đúng thứ tự khóa |
|---|---|
| **`UC-09` — Gửi báo cáo vi phạm**<br>(`POST /api/listings/{listingId}/reports`) | 1. Đọc `chu_id` của `tin_bat_dong_san` (nếu không có tin $\rightarrow$ chỉ khóa `tai_khoan` người gửi để check Replay `AC-06.4`; nếu không có Replay $\rightarrow$ trả `404`).<br>2. **[Bậc 1]** Khóa `tai_khoan` của `nguoi_gui_id` và `tin.chu_id` theo `ORDER BY id ASC FOR UPDATE`;**[Bậc 2]** khóa `phien_xac_thuc` của người gửi. Kiểm lại phiên người gửi còn hiệu lực, `vai_tro = 'nguoi_dung'`, `hoat_dong`, `email_da_xac_minh`.<br>3. **[Bậc 6]** Khóa `tin_bat_dong_san` (`FOR UPDATE`).<br>4. **Kiểm tra Replay** trong `yeu_cau_ghi` `(nguoi_gui_id, 'gui_bao_cao', ma_y_dinh)` $\rightarrow$ nếu có & khớp hash trả ngay báo cáo cũ (`AC-06.4`).<br>5. Kiểm `nguoi_gui_id != tin.chu_id` (`QD-S2-01` $\rightarrow$ `403`); kiểm `tin.trang_thai == 'da_duyet'` và `chuTin.trang_thai == 'hoat_dong'` (nếu `tin.trang_thai == 'cho_kiem_duyet'` $\rightarrow$ `409`; còn lại $\rightarrow$ `404`).<br>6. Đếm báo cáo của `(nguoi_gui_id, tin_id)` có `gui_luc >= now - interval '24 hours'`; nếu `>= 5` $\rightarrow$ chặn `409/429 VUOT_HAN_MUC_BAO_CAO` (`AC-06.1`).<br>7. **[Bậc 7]** Nếu có `anhIds`, khóa các dòng ảnh bằng chứng (`ORDER BY id ASC FOR UPDATE`), kiểm thuộc `nguoi_gui_id` và trạng thái `tam`, gắn vào báo cáo.<br>8. Chèn `bao_cao_vi_pham` (`cho_xu_ly`, `phien_ban_tham_chieu_id = tin.phien_ban_da_duyet_id`) + chèn snapshot vào `yeu_cau_ghi`. Commit. |
| **`UC-14a` — KDV kết luận báo cáo**<br>(`POST /api/moderation/reports/{reportId}/decision`) | 1. Đọc `tin_id` từ `bao_cao_vi_pham` (không thấy $\rightarrow$ `404`).<br>2. **[Bậc 1 & 2]** Khóa `tai_khoan` và `phien_xac_thuc` của KDV (`FOR UPDATE`), kiểm còn quyền `kiem_duyet_vien` + `hoat_dong`.<br>3. **[Bậc 6]** Khóa `tin_bat_dong_san` (`FOR UPDATE`).<br>4. **[Bậc 8]** Khóa `goi_day_tin` chưa kết thúc của tin (`FOR UPDATE`).<br>5. **[Bậc 9]** Khóa dòng `bao_cao_vi_pham` mục tiêu (hoặc các báo cáo `cho_xu_ly` cùng tin theo `ORDER BY id ASC FOR UPDATE`).<br>6. **Kiểm tra Replay** trong `yeu_cau_ghi` `(kdv_id, 'xu_ly_bao_cao', ma_y_dinh)`.<br>7. Kiểm `bao_cao.trang_thai == 'cho_xu_ly'` (nếu đã xử lý $\rightarrow$ `409 BAO_CAO_DA_XU_LY`).<br>8. **Nhánh 1 — Chủ đã xóa tin trước (`tin.trang_thai == 'da_xoa'`, `AC-08.3`):** Cập nhật báo cáo sang `da_dong`, `ly_do_dong = 'tin_da_xoa'`, không tăng vi phạm, lưu `yeu_cau_ghi` + audit, Commit.<br>9. Kiểm `If-Match` / `phienBanDaDuyetDaXemId == tin.phien_ban_da_duyet_id` (lệch $\rightarrow$ `412 PHIEN_BAN_CU`, `AC-08.2`).<br>10. **Nhánh 2 — Bác bỏ (`ketLuan == 'bac_bo'`):** Đổi báo cáo `da_bac_bo` kèm `lyDo`, giữ nguyên tin & gói, tạo thông báo cho người gửi, lưu `yeu_cau_ghi` + audit, Commit.<br>11. **Nhánh 3 — Xác nhận vi phạm (`ketLuan == 'xac_nhan_vi_pham'`):**<br>    - Đổi báo cáo `da_xac_nhan`; đổi `tin.trang_thai = 'bi_go_vi_pham'`, `ly_do_ngung_hien_thi = lyDo`, `phien_ban_cap_nhat + 1` (kể cả khi tin đang `cho_kiem_duyet` hoặc `bi_tu_choi` bản sửa).<br>    - Nếu có gói `dang_hoat_dong` / `tam_dung_cho_duyet` $\rightarrow$ ghi `su_kien_goi` và đổi gói sang `ket_thuc_do_go_tin` (`thoi_luong_con_lai = 0`, `het_han_luc = NULL`).<br>    - Chèn `su_kien_vi_pham (tin_id, ...)` với `ON CONFLICT (tin_id) DO NOTHING` (đảm bảo tối đa 1 vi phạm mỗi tin — `AC-08.1`).<br>    - Tính tổng `su_kien_vi_pham` của `tin.chu_id` và kiểm tra căn cứ mới sau phiếu `bi_tu_choi` gần nhất để trả cờ `coTheDeXuatKhoa` cho UI KDV; tạo thông báo cho người gửi, lưu `yeu_cau_ghi` + audit. Commit. |
| **`UC-14b` — KDV gửi phiếu đề xuất khóa**<br>(`POST /api/moderation/lock-requests`) | 1. **[Bậc 1]** Khóa 2 dòng `tai_khoan` gồm `kdv_id` và `tai_khoan_bi_de_xuat_id` theo **`ORDER BY id ASC FOR UPDATE`**; **[Bậc 2]** khóa `phien_xac_thuc` của KDV.<br>2. Kiểm quyền KDV hiện tại; kiểm mục tiêu tồn tại, không phải `quan_tri_vien`, đang `hoat_dong`.<br>3. **[Bậc 3]** Khóa các dòng `yeu_cau_khoa_tai_khoan` của tài khoản mục tiêu (`ORDER BY id ASC FOR UPDATE`).<br>4. **Kiểm tra Replay** `(kdv_id, 'tao_yeu_cau_khoa', ma_y_dinh)`.<br>5. Kiểm chưa có phiếu `cho_admin`; đếm `su_kien_vi_pham` của chủ tin `>= 3` và nếu có phiếu `bi_tu_choi` trước đó thì tồn tại ít nhất 1 `su_kien_vi_pham` mới chưa nằm trong snapshot căn cứ bị từ chối (`AC-08.4`).<br>6. Lưu phiếu `cho_admin` kèm snapshot danh sách `su_kien_vi_pham` & báo cáo xác nhận làm căn cứ + lưu `yeu_cau_ghi` + audit. Commit. |
| **`UC-16/17/18` — Admin đổi vai trò, quyết định phiếu khóa, mở khóa**<br>(`/api/admin/...`) | 1. **[Bậc 1]** Khóa dòng `tai_khoan` của `admin_id` và `target_account_id` theo **`ORDER BY id ASC FOR UPDATE`** (nếu trùng ID vẫn 1 dòng, sau đó chặn tự hạ quyền / tự khóa ở bước kiểm luật).<br>2. **[Bậc 2]** Khóa `phien_xac_thuc` của Admin (kiểm lại quyền `quan_tri_vien` + `hoat_dong`); nếu là luồng **Duyệt khóa (`chap_thuan`)**, khóa tiếp tất cả `phien_xac_thuc` chưa thu hồi của `target_account_id` theo `ORDER BY id ASC FOR UPDATE`.<br>3. **[Bậc 3]** Nếu là `UC-17`, khóa dòng `yeu_cau_khoa_tai_khoan` (`FOR UPDATE`).<br>4. **Kiểm tra Replay** trong `yeu_cau_ghi` tương ứng.<br>5. Kiểm luật miền:<br>   - `UC-16`: Chặn `admin_id == target_account_id && vaiTroMoi != 'quan_tri_vien'` (`409 KHONG_THE_TU_HA_QUYEN`).<br>   - `UC-17`: Kiểm `phieu.trang_thai == 'cho_admin'`, bắt buộc `lyDo`; nếu `chap_thuan` $\rightarrow$ cập nhật `target.trang_thai = 'bi_khoa'`, `moc_thu_hoi = moc_thu_hoi + 1`, thu hồi mọi phiên đang mở của `target` (`ly_do_thu_hoi = 'khoa_tai_khoan'`, `can_thu_hoi_provider = true`), đổi phiếu `da_duyet`; nếu `tu_choi` $\rightarrow$ đổi phiếu `bi_tu_choi`, lưu mốc vi phạm.<br>   - `UC-18`: Kiểm `target.trang_thai == 'bi_khoa'`, bắt buộc `lyDo` + `canCuEmail`, đổi `target.trang_thai = 'hoat_dong'` (**không đổi `moc_thu_hoi`, không đụng `tin_bat_dong_san` hay `goi_day_tin`**).<br>6. Ghi `nhat_ky_kiem_toan` (trước/sau + lý do) + lưu `yeu_cau_ghi`. Commit. |
| **`UC-11` — Mua và cộng dồn gói đẩy tin**<br>(`POST /api/listings/{listingId}/boost-purchases`) | 1. **[Bậc 1 & 2]** Khóa `tai_khoan` và `phien_xac_thuc` của người mua (`FOR UPDATE`); kiểm phiên hợp lệ, `vai_tro = 'nguoi_dung'`, `trang_thai = 'hoat_dong'`, `email_da_xac_minh` (`AC-11.4`).<br>2. **[Bậc 4]** Khóa `vi_dien_tu` của người mua (`FOR UPDATE`).<br>3. **[Bậc 6]** Khóa `tin_bat_dong_san` (`FOR UPDATE`, không có $\rightarrow$ `404`; `tin.chu_id != nguoi_mua_id` $\rightarrow$ `403 KHONG_DU_QUYEN`).<br>4. **[Bậc 8]** Khóa dòng `goi_day_tin` đang `dang_hoat_dong` hoặc `tam_dung_cho_duyet` của tin (`FOR UPDATE`).<br>5. **Kiểm tra Replay** trong `giao_dich_vi` `(vi_id, 'mua_goi', ma_y_dinh)` $\rightarrow$ nếu có & khớp hash trả ngay snapshot lần mua cũ (`AC-11.5`).<br>6. Kiểm `tin.trang_thai == 'da_duyet'` (`409 TRANG_THAI_KHONG_HOP_LE`).<br>7. Nếu có gói `dang_hoat_dong` nhưng đã hết hạn thực tế (`het_han_luc <= now`), chuyển gói cũ sang `het_han` (`thoi_luong_con_lai = 0`) ngay trong transaction trước khi xét mua mới (`AC-02.2`, `seq-mua-goi-VIP.mmd` dòng 33).<br>8. Nếu vẫn còn gói `dang_hoat_dong` còn hạn (`het_han_luc > now`): bắt buộc `dongYCongDon == true` (nếu `false` $\rightarrow$ `409 CAN_XAC_NHAN_CONG_DON`); cộng dồn `het_han_luc = het_han_luc + thoi_luong_mua`, cập nhật `thoi_luong_con_lai`. Nếu chưa có gói chưa kết thúc $\rightarrow$ tạo gói `dang_hoat_dong` mới (`het_han_luc = now + thoi_luong_mua`).<br>9. Kiểm `vi.so_du_vnd >= gia_goi` (thiếu $\rightarrow$ `409 SO_DU_KHONG_DU`), trừ `so_du_vnd = so_du_vnd - gia_goi` (kèm `CHECK (so_du_vnd >= 0)` ở DB).<br>10. Ghi `giao_dich_vi` (`loai = 'mua_goi'`, `trang_thai = 'thanh_cong'`, `chieu_bien_dong = 'tru'`, `ma_y_dinh`, `du_lieu_bam`, `ket_qua_json`) + ghi `lan_mua_goi` + ghi `su_kien_goi` + audit. Commit. |

---

## 5. Checklist Ràng buộc CSDL cần đồng bộ vào Migration `V2/V3` và `V4`

Để bảo vệ lớp thứ hai ngay dưới cơ chế khóa của Bảng 3 & Bảng 4, các file migration cần có đủ các ràng buộc sau:

1. **Nhánh Báo cáo & Quản trị (`V2/V3`):**
   - `se214.su_kien_vi_pham`: `UNIQUE (tin_id)` — đảm bảo tối đa 1 sự kiện tăng vi phạm trên mỗi tin (`AC-08.1`).
   - `se214.yeu_cau_khoa_tai_khoan`: Partial unique index `CREATE UNIQUE INDEX uq_yeu_cau_khoa_cho_admin ON se214.yeu_cau_khoa_tai_khoan(tai_khoan_bi_de_xuat_id) WHERE trang_thai = 'cho_admin'` — đảm bảo tối đa 1 phiếu chờ trên mỗi tài khoản (`AC-08.4`).
   - `se214.bao_cao_vi_pham`: Mở rộng `trang_thai IN ('cho_xu_ly','da_xac_nhan','da_bac_bo','da_dong')`, thêm index `(nguoi_gui_id, tin_id, gui_luc DESC)` phục vụ đếm cửa sổ 24 giờ trượt (`AC-06.1`).
   - `se214.nhat_ky_kiem_toan`: Cho phép `tin_id NULL` khi hành động là quản trị tài khoản (`tao_tai_khoan`, `cap_nhat_vai_tro`, `khoa_tai_khoan`, `tu_choi_khoa_tai_khoan`, `mo_khoa_tai_khoan`), bổ sung `tai_khoan_muc_tieu_id uuid REFERENCES se214.tai_khoan(id)`.
   - `se214.yeu_cau_ghi`: Cho phép `tin_ket_qua_id NULL` cho thao tác ngoài tin đăng Sprint 1, bổ sung `ket_qua_json jsonb`, mở rộng `CHECK (loai_thao_tac IN (...))`.
2. **Nhánh Ví & Gói đẩy tin (`V4`):**
   - `se214.vi_dien_tu`: `UNIQUE (chu_id)` và `CHECK (so_du_vnd >= 0 AND scale(so_du_vnd) = 0)` — chặn tuyệt đối âm ví ở cấp CSDL (`AC-11.5`, `Q40`).
   - `se214.giao_dich_vi`: `UNIQUE (vi_id, loai, ma_y_dinh)` — chặn thu tiền lặp cùng một ý định (`AC-11.5`).
   - `se214.goi_day_tin`: Giữ nguyên `uq_goi_chua_ket_thuc` (`WHERE trang_thai IN ('dang_hoat_dong','tam_dung_cho_duyet')`) từ `V1` và mở rộng `su_kien_goi.loai` thêm `'mua_moi'`, `'cong_don'`, `'het_han'`, `'ket_thuc_do_go_vi_pham'`.
