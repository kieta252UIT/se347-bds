-- ==============================================================================
-- V1__init_schema.sql
-- Hệ thống Tìm kiếm và Gợi ý Bất động sản (SE214 / SE113 / SE347)
-- Khởi tạo Schema PostgreSQL 17 tuân thủ IEEE 829 Test Plan Sprint 1 & ADR-0012/0013
-- ==============================================================================

-- 1. Bảng tài khoản người dùng và phân quyền (LOG-01, LOG-02, AC-15.1, AC-09)
CREATE TABLE IF NOT EXISTS tai_khoan (
    id BIGSERIAL PRIMARY KEY,
    supabase_auth_user_id VARCHAR(64) UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    ho_ten VARCHAR(255) NOT NULL,
    so_dien_thoai VARCHAR(32),
    vai_tro VARCHAR(32) NOT NULL DEFAULT 'NGUOI_DUNG' CHECK (vai_tro IN ('NGUOI_DUNG', 'KDV', 'ADMIN')),
    trang_thai VARCHAR(32) NOT NULL DEFAULT 'HOAT_DONG' CHECK (trang_thai IN ('HOAT_DONG', 'BI_KHOA', 'CHUA_XAC_MINH')),
    email_da_xac_minh BOOLEAN NOT NULL DEFAULT FALSE,
    moc_thu_hoi BIGINT NOT NULL DEFAULT 0,
    ngay_tao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng phiên đăng nhập ứng dụng phía PostgreSQL (ADR-0012, PHIEN-02, AC-15.4)
CREATE TABLE IF NOT EXISTS phien_xac_thuc (
    id UUID PRIMARY KEY,
    tai_khoan_id BIGINT NOT NULL REFERENCES tai_khoan(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    ngay_tao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_het_han TIMESTAMPTZ NOT NULL,
    da_thu_hoi BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_phien_tai_khoan_id ON phien_xac_thuc(tai_khoan_id);
CREATE INDEX IF NOT EXISTS idx_phien_token_hash ON phien_xac_thuc(token_hash);

-- 3. Bảng hạn mức đăng tin hàng tháng (BR-09, AC-01.1, tối đa 3 tin/tháng)
CREATE TABLE IF NOT EXISTS han_muc_thang (
    id BIGSERIAL PRIMARY KEY,
    tai_khoan_id BIGINT NOT NULL REFERENCES tai_khoan(id) ON DELETE CASCADE,
    thang_nam VARCHAR(7) NOT NULL, -- Định dạng YYYY-MM
    so_tin_da_dang INT NOT NULL DEFAULT 0 CHECK (so_tin_da_dang >= 0 AND so_tin_da_dang <= 3),
    CONSTRAINT uq_han_muc_tai_khoan_thang UNIQUE (tai_khoan_id, thang_nam)
);

CREATE INDEX IF NOT EXISTS idx_han_muc_thang_user ON han_muc_thang(tai_khoan_id, thang_nam);

-- 4. Bảng tin bất động sản (BR-01, AC-01.1, AC-05.4, AC-07)
CREATE TABLE IF NOT EXISTS tin_bat_dong_san (
    id BIGSERIAL PRIMARY KEY,
    chu_tin_id BIGINT NOT NULL REFERENCES tai_khoan(id),
    trang_thai VARCHAR(32) NOT NULL DEFAULT 'CHO_KIEM_DUYET' CHECK (trang_thai IN ('CHO_KIEM_DUYET', 'DA_DUYET', 'TU_CHOI', 'DA_XOA', 'TAM_DUNG')),
    phien_ban_hien_tai INT NOT NULL DEFAULT 1,
    phien_ban_da_duyet_id BIGINT,
    ngay_tao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ngay_cap_nhat TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tin_chu_tin ON tin_bat_dong_san(chu_tin_id);
CREATE INDEX IF NOT EXISTS idx_tin_trang_thai ON tin_bat_dong_san(trang_thai);

-- 5. Bảng phiên bản tin bất động sản (AC-01.2, AC-02.3, BR-03: diện tích > 0, 1-12 ảnh)
CREATE TABLE IF NOT EXISTS phien_ban_tin (
    id BIGSERIAL PRIMARY KEY,
    tin_id BIGINT NOT NULL REFERENCES tin_bat_dong_san(id) ON DELETE CASCADE,
    phien_ban INT NOT NULL DEFAULT 1,
    tieu_de VARCHAR(255) NOT NULL,
    mo_ta TEXT,
    loai_giao_dich VARCHAR(32) NOT NULL CHECK (loai_giao_dich IN ('BAN', 'CHO_THUE')),
    loai_bat_dong_san VARCHAR(64) NOT NULL,
    dien_tich NUMERIC(10, 2) NOT NULL CHECK (dien_tich > 0),
    gia_goc NUMERIC(18, 2) NOT NULL CHECK (gia_goc > 0),
    don_vi_gia VARCHAR(32) NOT NULL,
    gia_tong_vnd NUMERIC(18, 2) NOT NULL CHECK (gia_tong_vnd > 0),
    gia_m2_vnd NUMERIC(18, 2) NOT NULL CHECK (gia_m2_vnd > 0),
    so_luong_anh INT NOT NULL DEFAULT 1 CHECK (so_luong_anh >= 1 AND so_luong_anh <= 12),
    toa_do_lat NUMERIC(9, 6),
    toa_do_lng NUMERIC(9, 6),
    dia_chi TEXT,
    ket_qua_so_bo TEXT,
    ngay_tao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tin_phien_ban UNIQUE (tin_id, phien_ban)
);

CREATE INDEX IF NOT EXISTS idx_phien_ban_tin_id ON phien_ban_tin(tin_id);
CREATE INDEX IF NOT EXISTS idx_phien_ban_gia_tong ON phien_ban_tin(gia_tong_vnd);
CREATE INDEX IF NOT EXISTS idx_phien_ban_gia_m2 ON phien_ban_tin(gia_m2_vnd);

-- 6. Bảng hình ảnh tin bất động sản (AC-01.2, TS1-23 - TS1-27)
CREATE TABLE IF NOT EXISTS anh_tin (
    id BIGSERIAL PRIMARY KEY,
    phien_ban_tin_id BIGINT NOT NULL REFERENCES phien_ban_tin(id) ON DELETE CASCADE,
    chu_id BIGINT NOT NULL REFERENCES tai_khoan(id),
    duong_dan_anh VARCHAR(500) NOT NULL,
    thu_tu INT NOT NULL DEFAULT 1,
    khoa_anh_hien_thi VARCHAR(255),
    ngay_tao TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_anh_phien_ban ON anh_tin(phien_ban_tin_id);
CREATE INDEX IF NOT EXISTS idx_anh_chu_id ON anh_tin(chu_id);
