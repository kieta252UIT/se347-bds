-- V2__thiet_ke_goi_y_va_job_lease.sql
-- Migration bổ sung các bảng cho Module Gợi ý (M02 / Task S2-04, S2-10):
-- Bảng điểm gợi ý nền (diem_goi_y), bảng lịch sử tương tác xem tin (lich_su_xem)
-- và bảng distributed job lease (job_lease) theo kiến trúc SDD-23.
-- Toàn bộ bảng nằm trong schema se214.

-- 1. BẢNG ĐIỂM GỢI Ý NỀN CHO CẶP (TÀI KHOẢN, TIN ĐĂNG)
CREATE TABLE IF NOT EXISTS se214.diem_goi_y (
    tai_khoan_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE CASCADE,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE CASCADE,
    diem numeric NOT NULL CHECK (
        diem >= 0 AND diem <= 100 
        AND diem::text NOT IN ('NaN', 'Infinity', '-Infinity')
    ),
    cap_nhat_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tai_khoan_id, tin_id)
);

CREATE INDEX IF NOT EXISTS ix_diem_goi_y_diem 
    ON se214.diem_goi_y(tai_khoan_id, diem DESC);

-- 2. BẢNG LỊCH SỬ TƯƠNG TÁC XEM TIN
CREATE TABLE IF NOT EXISTS se214.lich_su_xem (
    id uuid PRIMARY KEY,
    tai_khoan_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE CASCADE,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE CASCADE,
    thoi_diem_xem timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_lich_su_xem_tai_khoan 
    ON se214.lich_su_xem(tai_khoan_id, thoi_diem_xem DESC);

CREATE INDEX IF NOT EXISTS ix_lich_su_xem_thoi_diem 
    ON se214.lich_su_xem(thoi_diem_xem);

-- 3. BẢNG DISTRIBUTED JOB LEASE (SDD-23: CAS update chống đụng độ nhiều tiến trình)
CREATE TABLE IF NOT EXISTS se214.job_lease (
    job_name text PRIMARY KEY,
    instance_id text NOT NULL,
    lease_until timestamptz NOT NULL,
    last_heartbeat timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
