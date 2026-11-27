-- V2__thiet_ke_vi_va_so_giao_dich.sql
-- Migration bổ sung các bảng tài chính cho Module M05: Ví điện tử, Sổ giao dịch và Lần mua gói
-- Phục vụ Task S2-03 (Khoa Nguyễn) và hiện thực S2-11 (Khánh, Khôi).
-- Sử dụng schema se214 đồng bộ toàn dự án; không sửa đổi 12 bảng đã có ở Sprint 1.

-- 1. BẢNG VÍ ĐIỆN TỬ
CREATE TABLE IF NOT EXISTS se214.vi_dien_tu (
    id uuid PRIMARY KEY,
    tai_khoan_id uuid NOT NULL UNIQUE REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    so_du_vnd numeric NOT NULL DEFAULT 0 CHECK (
        so_du_vnd >= 0 
        AND so_du_vnd::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    tao_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cap_nhat_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_vi_tai_khoan ON se214.vi_dien_tu(tai_khoan_id);

-- 2. BẢNG SỔ GIAO DỊCH VÍ (TRANSACTION LEDGER)
CREATE TABLE IF NOT EXISTS se214.giao_dich_vi (
    id uuid PRIMARY KEY,
    vi_id uuid NOT NULL REFERENCES se214.vi_dien_tu(id) ON DELETE RESTRICT,
    loai text NOT NULL CHECK (
        loai IN ('mua_goi', 'khoi_tao_thu_nghiem', 'nap_tien', 'boi_hoan', 'dieu_chinh')
    ),
    trang_thai text NOT NULL DEFAULT 'thanh_cong' CHECK (
        trang_thai IN ('thanh_cong', 'that_bai', 'cho_thanh_toan', 'da_huy')
    ),
    chieu_bien_dong text NOT NULL CHECK (
        chieu_bien_dong IN ('cong', 'tru')
    ),
    so_tien numeric NOT NULL CHECK (
        so_tien > 0 
        AND so_tien::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    so_du_truoc numeric NOT NULL CHECK (
        so_du_truoc >= 0 
        AND so_du_truoc::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    so_du_sau numeric NOT NULL CHECK (
        so_du_sau >= 0 
        AND so_du_sau::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    ma_y_dinh text NOT NULL CHECK (btrim(ma_y_dinh) <> ''),
    giao_dich_goc_id uuid REFERENCES se214.giao_dich_vi(id) ON DELETE RESTRICT,
    ma_thu_tien_cong text UNIQUE,
    thoi_diem_ghi_nhan timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ghi_chu text,
    CONSTRAINT ck_giao_dich_so_du_khop CHECK (
        (chieu_bien_dong = 'cong' AND so_du_sau = so_du_truoc + so_tien)
        OR (chieu_bien_dong = 'tru' AND so_du_sau = so_du_truoc - so_tien)
    )
);

CREATE INDEX IF NOT EXISTS ix_giao_dich_vi_thoi_diem 
    ON se214.giao_dich_vi(vi_id, thoi_diem_ghi_nhan DESC, id);

-- Ràng buộc chống trừ tiền trùng lặp cùng một ý định mua thành công:
CREATE UNIQUE INDEX IF NOT EXISTS uq_giao_dich_vi_y_dinh 
    ON se214.giao_dich_vi(vi_id, loai, ma_y_dinh)
    WHERE trang_thai = 'thanh_cong';

-- 3. BẢNG LẦN MUA GÓI ĐẨY TIN (Tách biệt khỏi goi_day_tin để bảo toàn BR-08)
CREATE TABLE IF NOT EXISTS se214.lan_mua_goi (
    id uuid PRIMARY KEY,
    goi_id uuid NOT NULL REFERENCES se214.goi_day_tin(id) ON DELETE RESTRICT,
    giao_dich_vi_id uuid NOT NULL UNIQUE REFERENCES se214.giao_dich_vi(id) ON DELETE RESTRICT,
    gia_da_mua numeric NOT NULL CHECK (gia_da_mua IN (49000, 179000)),
    thoi_luong_da_cap interval NOT NULL CHECK (
        thoi_luong_da_cap IN (interval '7 days', interval '30 days')
    ),
    thoi_diem_mua timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_lan_mua_goi ON se214.lan_mua_goi(goi_id, thoi_diem_mua DESC);
