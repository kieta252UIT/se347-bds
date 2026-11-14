-- SE214 - Flyway V1: 12 bang cua Database Design Sprint 1 (ban 0.2).
-- Flyway quan ly schema se214, transaction va lich su; khong chay DDL tham khao song song.
-- Giu nguyen DDL da chot ngay 13/11/2026; khong tu sua migration da ap dung.
-- Chi schema ung dung; khong doc/ghi auth.users, khong trigger/Auth hook.
-- UUID do backend cap; numeric khong ap tran hoac so chu so nghiep vu moi.

REVOKE ALL ON SCHEMA se214 FROM PUBLIC;

CREATE TABLE se214.tai_khoan (
    id uuid PRIMARY KEY,
    supabase_auth_user_id uuid NOT NULL UNIQUE,
    email text NOT NULL UNIQUE CHECK (btrim(email) <> ''),
    ho_ten text CHECK (ho_ten IS NULL OR btrim(ho_ten) <> ''),
    so_dien_thoai text CHECK (so_dien_thoai IS NULL OR btrim(so_dien_thoai) <> ''),
    vai_tro text NOT NULL DEFAULT 'nguoi_dung'
        CHECK (vai_tro IN ('nguoi_dung','kiem_duyet_vien','quan_tri_vien')),
    trang_thai text NOT NULL DEFAULT 'hoat_dong'
        CHECK (trang_thai IN ('hoat_dong','bi_khoa')),
    email_da_xac_minh boolean NOT NULL DEFAULT false,
    moc_thu_hoi bigint NOT NULL DEFAULT 0 CHECK (moc_thu_hoi >= 0),
    trang_thai_dong_bo_auth text NOT NULL DEFAULT 'san_sang'
        CHECK (trang_thai_dong_bo_auth IN ('san_sang','dang_dat_lai','can_khoi_phuc')),
    ma_thao_tac_auth uuid,
    cap_nhat_auth_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tai_khoan_ma_auth CHECK (
        (trang_thai_dong_bo_auth = 'san_sang' AND ma_thao_tac_auth IS NULL)
        OR (trang_thai_dong_bo_auth <> 'san_sang' AND ma_thao_tac_auth IS NOT NULL)
    )
);

CREATE TABLE se214.phien_xac_thuc (
    id uuid PRIMARY KEY,
    tai_khoan_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    ma_cookie_bam text NOT NULL UNIQUE CHECK (btrim(ma_cookie_bam) <> ''),
    moc_thu_hoi_luc_cap bigint NOT NULL CHECK (moc_thu_hoi_luc_cap >= 0),
    tao_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    thu_hoi_luc timestamptz,
    ly_do_thu_hoi text,
    supabase_session_id uuid NOT NULL UNIQUE,
    access_token_ma_hoa bytea,
    refresh_token_ma_hoa bytea,
    access_token_het_han_luc timestamptz,
    phien_ban_khoa_ma_hoa text,
    phien_ban_cap_nhat bigint NOT NULL DEFAULT 0 CHECK (phien_ban_cap_nhat >= 0),
    refresh_lease_id uuid,
    refresh_lease_den_luc timestamptz,
    can_thu_hoi_provider boolean NOT NULL DEFAULT false,
    CONSTRAINT ck_phien_lease_cap CHECK (
        (refresh_lease_id IS NULL) = (refresh_lease_den_luc IS NULL)
    ),
    CONSTRAINT ck_phien_credential_cap CHECK (
        (access_token_ma_hoa IS NULL AND refresh_token_ma_hoa IS NULL
         AND access_token_het_han_luc IS NULL AND phien_ban_khoa_ma_hoa IS NULL)
        OR (access_token_ma_hoa IS NOT NULL AND refresh_token_ma_hoa IS NOT NULL
            AND access_token_het_han_luc IS NOT NULL
            AND phien_ban_khoa_ma_hoa IS NOT NULL AND btrim(phien_ban_khoa_ma_hoa) <> '')
    ),
    CONSTRAINT ck_phien_don_credential CHECK (
        thu_hoi_luc IS NOT NULL OR refresh_token_ma_hoa IS NOT NULL
    ),
    CONSTRAINT ck_phien_ly_do CHECK (
        ly_do_thu_hoi IS NULL OR (thu_hoi_luc IS NOT NULL AND btrim(ly_do_thu_hoi) <> '')
    )
);
CREATE INDEX ix_phien_tai_khoan ON se214.phien_xac_thuc(tai_khoan_id);

CREATE TABLE se214.tin_bat_dong_san (
    id uuid PRIMARY KEY,
    chu_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    trang_thai text NOT NULL DEFAULT 'cho_kiem_duyet'
        CHECK (trang_thai IN ('cho_kiem_duyet','da_duyet','bi_tu_choi','da_xoa','bi_go_vi_pham')),
    ngay_dang timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    phien_ban_hien_tai_id uuid NOT NULL,
    phien_ban_da_duyet_id uuid,
    ly_do_ngung_hien_thi text,
    phien_ban_cap_nhat bigint NOT NULL DEFAULT 0 CHECK (phien_ban_cap_nhat >= 0),
    CONSTRAINT ck_tin_ly_do CHECK (
        ly_do_ngung_hien_thi IS NULL OR btrim(ly_do_ngung_hien_thi) <> ''
    )
);

CREATE TABLE se214.phien_ban_tin (
    id uuid PRIMARY KEY,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE RESTRICT,
    so_phien_ban integer NOT NULL CHECK (so_phien_ban >= 1),
    tieu_de text NOT NULL CHECK (btrim(tieu_de) <> ''),
    mo_ta text NOT NULL CHECK (btrim(mo_ta) <> ''),
    loai_giao_dich text NOT NULL CHECK (loai_giao_dich IN ('ban','cho_thue')),
    loai_bds text NOT NULL CHECK (btrim(loai_bds) <> ''),
    gia_goc numeric NOT NULL CHECK (gia_goc > 0 AND gia_goc::text NOT IN ('NaN','Infinity','-Infinity')),
    don_vi_gia text NOT NULL,
    dien_tich_m2 numeric NOT NULL CHECK (dien_tich_m2 > 0 AND dien_tich_m2::text NOT IN ('NaN','Infinity','-Infinity')),
    dia_chi text NOT NULL CHECK (btrim(dia_chi) <> ''),
    khu_vuc text NOT NULL CHECK (btrim(khu_vuc) <> ''),
    vi_do numeric NOT NULL CHECK (vi_do BETWEEN -90 AND 90 AND vi_do::text NOT IN ('NaN','Infinity','-Infinity')),
    kinh_do numeric NOT NULL CHECK (kinh_do BETWEEN -180 AND 180 AND kinh_do::text NOT IN ('NaN','Infinity','-Infinity')),
    ket_qua_so_bo text NOT NULL CHECK (btrim(ket_qua_so_bo) <> ''),
    ket_qua_kiem_duyet text NOT NULL DEFAULT 'cho_kiem_duyet'
        CHECK (ket_qua_kiem_duyet IN ('cho_kiem_duyet','da_duyet','bi_tu_choi')),
    nguoi_duyet_id uuid REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    thoi_diem_quyet_dinh timestamptz,
    ly_do_tu_choi text,
    tao_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_phien_ban_so UNIQUE (tin_id, so_phien_ban),
    CONSTRAINT uq_phien_ban_tin_id UNIQUE (tin_id, id),
    CONSTRAINT ck_phien_ban_don_vi CHECK (
        (loai_giao_dich = 'ban' AND don_vi_gia IN ('vnd_tong','vnd_m2'))
        OR (loai_giao_dich = 'cho_thue' AND don_vi_gia IN
            ('vnd_ngay','vnd_thang','vnd_nam','vnd_m2_ngay','vnd_m2_thang'))
    ),
    CONSTRAINT ck_phien_ban_quyet_dinh CHECK (
        (ket_qua_kiem_duyet = 'cho_kiem_duyet' AND nguoi_duyet_id IS NULL
         AND thoi_diem_quyet_dinh IS NULL AND ly_do_tu_choi IS NULL)
        OR (ket_qua_kiem_duyet = 'da_duyet' AND nguoi_duyet_id IS NOT NULL
            AND thoi_diem_quyet_dinh IS NOT NULL AND ly_do_tu_choi IS NULL)
        OR (ket_qua_kiem_duyet = 'bi_tu_choi' AND nguoi_duyet_id IS NOT NULL
            AND thoi_diem_quyet_dinh IS NOT NULL AND ly_do_tu_choi IS NOT NULL
            AND btrim(ly_do_tu_choi) <> '')
    )
);

ALTER TABLE se214.tin_bat_dong_san ADD CONSTRAINT fk_tin_ban_hien_tai
    FOREIGN KEY (id, phien_ban_hien_tai_id)
    REFERENCES se214.phien_ban_tin(tin_id, id)
    ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE se214.tin_bat_dong_san ADD CONSTRAINT fk_tin_ban_da_duyet
    FOREIGN KEY (id, phien_ban_da_duyet_id)
    REFERENCES se214.phien_ban_tin(tin_id, id)
    ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX ix_tin_chu_ngay ON se214.tin_bat_dong_san(chu_id, ngay_dang DESC, id);
CREATE INDEX ix_tin_trang_thai_ngay ON se214.tin_bat_dong_san(trang_thai, ngay_dang DESC, id);
CREATE INDEX ix_phien_ban_bo_loc ON se214.phien_ban_tin(loai_giao_dich, loai_bds, khu_vuc);
CREATE INDEX ix_phien_ban_nguoi_duyet ON se214.phien_ban_tin(nguoi_duyet_id);

CREATE TABLE se214.anh_tin (
    id uuid PRIMARY KEY,
    chu_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    phien_ban_tin_id uuid REFERENCES se214.phien_ban_tin(id) ON DELETE RESTRICT,
    khoa_tep_goc text NOT NULL CHECK (btrim(khoa_tep_goc) <> ''),
    khoa_anh_hien_thi text CHECK (khoa_anh_hien_thi IS NULL OR btrim(khoa_anh_hien_thi) <> ''),
    mime_type text NOT NULL CHECK (mime_type IN ('image/jpeg','image/png','image/webp')),
    kich_thuoc_byte bigint NOT NULL CHECK (kich_thuoc_byte BETWEEN 1 AND 10000000),
    thu_tu integer,
    trang_thai text NOT NULL DEFAULT 'tam' CHECK (trang_thai IN ('tam','da_gan','cho_don')),
    tao_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_anh_thu_tu UNIQUE (phien_ban_tin_id, thu_tu),
    CONSTRAINT ck_anh_gan CHECK (
        (trang_thai = 'da_gan' AND phien_ban_tin_id IS NOT NULL
         AND thu_tu IS NOT NULL AND thu_tu BETWEEN 1 AND 12 AND khoa_anh_hien_thi IS NOT NULL)
        OR (trang_thai IN ('tam','cho_don') AND phien_ban_tin_id IS NULL AND thu_tu IS NULL)
    )
);
CREATE INDEX ix_anh_chu_trang_thai ON se214.anh_tin(chu_id, trang_thai);

CREATE TABLE se214.han_muc_thang (
    tai_khoan_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    thang date NOT NULL CHECK (EXTRACT(DAY FROM thang) = 1),
    so_tin_moi integer NOT NULL DEFAULT 0 CHECK (so_tin_moi BETWEEN 0 AND 3),
    PRIMARY KEY (tai_khoan_id, thang)
);
CREATE TABLE se214.yeu_thich_tin (
    tai_khoan_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE RESTRICT,
    luu_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tai_khoan_id, tin_id)
);
CREATE INDEX ix_yeu_thich_danh_sach ON se214.yeu_thich_tin(tai_khoan_id, luu_luc DESC, tin_id);
CREATE INDEX ix_yeu_thich_tin ON se214.yeu_thich_tin(tin_id);

CREATE TABLE se214.goi_day_tin (
    id uuid PRIMARY KEY,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE RESTRICT,
    trang_thai text NOT NULL CHECK (trang_thai IN
        ('dang_hoat_dong','tam_dung_cho_duyet','het_han','ket_thuc_do_go_tin')),
    thoi_luong_con_lai interval NOT NULL CHECK (
        isfinite(thoi_luong_con_lai) AND thoi_luong_con_lai >= interval '0 seconds'
        AND EXTRACT(YEAR FROM thoi_luong_con_lai) = 0
        AND EXTRACT(MONTH FROM thoi_luong_con_lai) = 0
    ),
    het_han_luc timestamptz,
    CONSTRAINT ck_goi_han CHECK (trang_thai <> 'dang_hoat_dong' OR het_han_luc IS NOT NULL)
);
CREATE UNIQUE INDEX uq_goi_chua_ket_thuc ON se214.goi_day_tin(tin_id)
    WHERE trang_thai IN ('dang_hoat_dong','tam_dung_cho_duyet');
CREATE INDEX ix_goi_tin ON se214.goi_day_tin(tin_id);

CREATE TABLE se214.su_kien_goi (
    id uuid PRIMARY KEY,
    goi_id uuid NOT NULL REFERENCES se214.goi_day_tin(id) ON DELETE RESTRICT,
    nguoi_thuc_hien_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    loai text NOT NULL CHECK (loai IN ('tam_dung','chay_tiep','ket_thuc_do_xoa')),
    thoi_luong_truoc interval NOT NULL,
    thoi_luong_sau interval NOT NULL,
    thoi_diem timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_su_kien_duration CHECK (
        isfinite(thoi_luong_truoc) AND isfinite(thoi_luong_sau)
        AND thoi_luong_truoc >= interval '0 seconds' AND thoi_luong_sau >= interval '0 seconds'
        AND EXTRACT(YEAR FROM thoi_luong_truoc) = 0 AND EXTRACT(MONTH FROM thoi_luong_truoc) = 0
        AND EXTRACT(YEAR FROM thoi_luong_sau) = 0 AND EXTRACT(MONTH FROM thoi_luong_sau) = 0
    )
);
CREATE INDEX ix_su_kien_goi ON se214.su_kien_goi(goi_id, thoi_diem, id);
CREATE INDEX ix_su_kien_nguoi ON se214.su_kien_goi(nguoi_thuc_hien_id);

CREATE TABLE se214.bao_cao_vi_pham (
    id uuid PRIMARY KEY,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE RESTRICT,
    nguoi_gui_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    phien_ban_tham_chieu_id uuid NOT NULL,
    ly_do text NOT NULL CHECK (btrim(ly_do) <> ''),
    trang_thai text NOT NULL DEFAULT 'cho_xu_ly' CHECK (trang_thai IN ('cho_xu_ly','da_dong')),
    ly_do_dong text,
    gui_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dong_luc timestamptz,
    CONSTRAINT fk_bao_cao_ban FOREIGN KEY (tin_id, phien_ban_tham_chieu_id)
        REFERENCES se214.phien_ban_tin(tin_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_bao_cao_dong CHECK (
        (trang_thai = 'cho_xu_ly' AND ly_do_dong IS NULL AND dong_luc IS NULL)
        OR (trang_thai = 'da_dong' AND ly_do_dong IS NOT NULL
            AND ly_do_dong = 'tin_da_xoa' AND dong_luc IS NOT NULL)
    )
);
CREATE INDEX ix_bao_cao_tin_trang_thai ON se214.bao_cao_vi_pham(tin_id, trang_thai, id);
CREATE INDEX ix_bao_cao_nguoi ON se214.bao_cao_vi_pham(nguoi_gui_id);

CREATE TABLE se214.yeu_cau_ghi (
    id uuid PRIMARY KEY,
    tai_khoan_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    loai_thao_tac text NOT NULL CHECK (loai_thao_tac IN ('dang_tin','sua_tin','xoa_tin','quyet_dinh')),
    ma_y_dinh text NOT NULL CHECK (btrim(ma_y_dinh) <> ''),
    du_lieu_bam text NOT NULL CHECK (btrim(du_lieu_bam) <> ''),
    tin_ket_qua_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE RESTRICT,
    phien_ban_ket_qua_id uuid,
    trang_thai_ket_qua text NOT NULL CHECK (trang_thai_ket_qua IN
        ('cho_kiem_duyet','da_duyet','bi_tu_choi','da_xoa','bi_go_vi_pham')),
    phien_ban_cap_nhat_ket_qua bigint NOT NULL CHECK (phien_ban_cap_nhat_ket_qua >= 0),
    tao_luc timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_yeu_cau_y_dinh UNIQUE (tai_khoan_id, loai_thao_tac, ma_y_dinh),
    CONSTRAINT fk_yeu_cau_ban FOREIGN KEY (tin_ket_qua_id, phien_ban_ket_qua_id)
        REFERENCES se214.phien_ban_tin(tin_id, id) ON DELETE RESTRICT
);
CREATE INDEX ix_yeu_cau_tin ON se214.yeu_cau_ghi(tin_ket_qua_id);

CREATE TABLE se214.nhat_ky_kiem_toan (
    id uuid PRIMARY KEY,
    nguoi_thuc_hien_id uuid NOT NULL REFERENCES se214.tai_khoan(id) ON DELETE RESTRICT,
    tin_id uuid NOT NULL REFERENCES se214.tin_bat_dong_san(id) ON DELETE RESTRICT,
    phien_ban_tin_id uuid,
    hanh_dong text NOT NULL CHECK (hanh_dong IN ('dang_tin','sua_tin','xoa_tin','phe_duyet','tu_choi')),
    du_lieu_truoc_sau jsonb NOT NULL CHECK (jsonb_typeof(du_lieu_truoc_sau) = 'object'),
    ly_do text,
    thoi_diem timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nhat_ky_ban FOREIGN KEY (tin_id, phien_ban_tin_id)
        REFERENCES se214.phien_ban_tin(tin_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_nhat_ky_ly_do CHECK (
        (ly_do IS NULL OR btrim(ly_do) <> '')
        AND (hanh_dong <> 'tu_choi' OR (ly_do IS NOT NULL AND btrim(ly_do) <> ''))
    )
);
CREATE INDEX ix_nhat_ky_tin ON se214.nhat_ky_kiem_toan(tin_id, thoi_diem, id);
CREATE INDEX ix_nhat_ky_nguoi ON se214.nhat_ky_kiem_toan(nguoi_thuc_hien_id, thoi_diem);

-- RLS/GRANT cho role backend, Storage va API exposure phai duoc thiet ke/test
-- theo moi truong truoc deploy. Khong cap quyen SQL cho anon/authenticated o day.
