-- V4__vi_dien_tu_so_giao_dich_va_lan_mua_goi.sql
-- Migration bổ sung các bảng tài chính cho Module M05: Ví điện tử, Sổ giao dịch và Lần mua gói
-- Phục vụ Task S2-03 & S2-11 (Khánh).
-- Đặt phiên bản V4 theo đúng hợp đồng S2-02 Mục 5.2 (tránh đụng độ V2/V3 của Báo cáo, Quản trị và Gợi ý).

-- 1. BẢNG VÍ ĐIỆN TỬ (se214.vi_dien_tu)
CREATE TABLE IF NOT EXISTS se214.vi_dien_tu (
    id uuid PRIMARY KEY,
    tai_khoan_id uuid NOT NULL UNIQUE REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    so_du_vnd numeric NOT NULL DEFAULT 0 CHECK (
        so_du_vnd >= 0
        AND scale(so_du_vnd) = 0
        AND so_du_vnd::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    tao_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cap_nhat_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_vi_tai_khoan ON se214.vi_dien_tu(tai_khoan_id);

-- Backfill ví 0 VND cho toàn bộ tài khoản đã có trong se214.tai_khoan (sinh UUID v4 hợp lệ từ md5(tai_khoan.id) mà không cần extension ngoài)
INSERT INTO se214.vi_dien_tu (id, tai_khoan_id, so_du_vnd, tao_luc, cap_nhat_luc)
SELECT
    CAST(
        substr(md5('se214_vi_' || tk.id::text), 1, 8) || '-' ||
        substr(md5('se214_vi_' || tk.id::text), 9, 4) || '-4' ||
        substr(md5('se214_vi_' || tk.id::text), 14, 3) || '-a' ||
        substr(md5('se214_vi_' || tk.id::text), 18, 3) || '-' ||
        substr(md5('se214_vi_' || tk.id::text), 21, 12)
        AS uuid
    ),
    tk.id,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM se214.tai_khoan tk
ON CONFLICT (tai_khoan_id) DO NOTHING;

-- 2. BẢNG SỔ GIAO DỊCH VÍ (se214.giao_dich_vi)
CREATE TABLE IF NOT EXISTS se214.giao_dich_vi (
    id uuid PRIMARY KEY,
    vi_id uuid NOT NULL REFERENCES se214.vi_dien_tu(id) ON DELETE RESTRICT,
    loai text NOT NULL CHECK (
        loai IN ('nap_tien', 'mua_goi', 'hoan_tien', 'dieu_chinh_admin', 'khoi_tao_thu_nghiem', 'boi_hoan', 'dieu_chinh')
    ),
    trang_thai text NOT NULL DEFAULT 'thanh_cong' CHECK (
        trang_thai IN ('cho_thanh_toan', 'thanh_cong', 'that_bai', 'da_huy')
    ),
    chieu_bien_dong text NOT NULL CHECK (
        chieu_bien_dong IN ('cong', 'tru')
    ),
    so_tien numeric NOT NULL CHECK (
        so_tien > 0
        AND scale(so_tien) = 0
        AND so_tien::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    so_du_truoc numeric NOT NULL CHECK (
        so_du_truoc >= 0
        AND scale(so_du_truoc) = 0
        AND so_du_truoc::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    so_du_sau numeric NOT NULL CHECK (
        so_du_sau >= 0
        AND scale(so_du_sau) = 0
        AND so_du_sau::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    ma_y_dinh text CHECK (ma_y_dinh IS NULL OR btrim(ma_y_dinh) <> ''),
    du_lieu_bam text CHECK (du_lieu_bam IS NULL OR btrim(du_lieu_bam) <> ''),
    ket_qua_json jsonb CHECK (ket_qua_json IS NULL OR jsonb_typeof(ket_qua_json) = 'object'),
    giao_dich_goc_id uuid REFERENCES se214.giao_dich_vi(id) ON DELETE RESTRICT,
    ma_thu_tien_cong text UNIQUE,
    thoi_diem_ghi_nhan timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ghi_chu text,
    CONSTRAINT ck_giao_dich_so_du_khop CHECK (
        (chieu_bien_dong = 'cong' AND so_du_sau = so_du_truoc + so_tien)
        OR (chieu_bien_dong = 'tru' AND so_du_sau = so_du_truoc - so_tien)
    ),
    CONSTRAINT ck_giao_dich_mua_goi_replay CHECK (
        loai <> 'mua_goi'
        OR (
            ma_y_dinh IS NOT NULL AND btrim(ma_y_dinh) <> ''
            AND du_lieu_bam IS NOT NULL AND btrim(du_lieu_bam) <> ''
            AND ket_qua_json IS NOT NULL AND jsonb_typeof(ket_qua_json) = 'object'
        )
    )
);

CREATE INDEX IF NOT EXISTS ix_giao_dich_vi_thoi_diem
    ON se214.giao_dich_vi(vi_id, thoi_diem_ghi_nhan DESC, id);

-- Ràng buộc một ý định một lần thu trực tiếp trên sổ giao dịch ví:
CREATE UNIQUE INDEX IF NOT EXISTS uq_giao_dich_vi_y_dinh
    ON se214.giao_dich_vi(vi_id, loai, ma_y_dinh)
    WHERE ma_y_dinh IS NOT NULL;

-- 3. BẢNG LẦN MUA GÓI ĐẨY TIN (se214.lan_mua_goi - tách biệt khỏi goi_day_tin để bảo toàn BR-08)
CREATE TABLE IF NOT EXISTS se214.lan_mua_goi (
    id uuid PRIMARY KEY,
    goi_id uuid NOT NULL REFERENCES se214.goi_day_tin(id) ON DELETE RESTRICT,
    giao_dich_vi_id uuid NOT NULL UNIQUE REFERENCES se214.giao_dich_vi(id) ON DELETE RESTRICT,
    gia_da_mua numeric NOT NULL CHECK (gia_da_mua IN (49000, 179000)),
    thoi_luong_da_cap interval NOT NULL CHECK (
        thoi_luong_da_cap IN (interval '7 days', interval '30 days')
    ),
    thoi_luong_da_cap_giay bigint NOT NULL DEFAULT 604800 CHECK (
        thoi_luong_da_cap_giay IN (604800, 2592000)
    ),
    thoi_diem_mua timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_lan_mua_goi ON se214.lan_mua_goi(goi_id, thoi_diem_mua DESC);

-- 4. MỞ RỘNG CHECK CONSTRAINT TRÊN se214.su_kien_goi VÀ se214.nhat_ky_kiem_toan (kế thừa đủ giá trị V1-V3)
ALTER TABLE se214.su_kien_goi DROP CONSTRAINT IF EXISTS su_kien_goi_loai_check;
ALTER TABLE se214.su_kien_goi DROP CONSTRAINT IF EXISTS ck_su_kien_goi_loai;
ALTER TABLE se214.su_kien_goi ADD CONSTRAINT ck_su_kien_goi_loai CHECK (
    loai IN (
        'tam_dung',
        'chay_tiep',
        'ket_thuc_do_xoa',
        'ket_thuc_do_go_vi_pham',
        'mua_moi',
        'cong_don',
        'het_han'
    )
);

ALTER TABLE se214.nhat_ky_kiem_toan DROP CONSTRAINT IF EXISTS nhat_ky_kiem_toan_hanh_dong_check;
ALTER TABLE se214.nhat_ky_kiem_toan DROP CONSTRAINT IF EXISTS ck_nhat_ky_hanh_dong;
ALTER TABLE se214.nhat_ky_kiem_toan ADD CONSTRAINT ck_nhat_ky_hanh_dong CHECK (
    hanh_dong IN (
        'dang_tin',
        'sua_tin',
        'xoa_tin',
        'phe_duyet',
        'tu_choi',
        'gui_bao_cao',
        'xac_nhan_vi_pham',
        'bac_bo_bao_cao',
        'dong_bao_cao_tin_xoa',
        'tao_yeu_cau_khoa',
        'tao_tai_khoan',
        'cap_nhat_tai_khoan',
        'khoa_tai_khoan',
        'tu_choi_khoa_tai_khoan',
        'mo_khoa_tai_khoan',
        'mua_goi',
        'cong_don_goi'
    )
);
