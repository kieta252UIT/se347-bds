\set ON_ERROR_STOP on

-- Chay SAU Flyway migrate, ket noi truc tiep bang se214_migrator (chu schema).
-- Khong gia dinh tai khoan tao role tren Supabase co SET/INHERIT role migrator.
BEGIN;

DO $verify$
DECLARE
    runtime_role pg_roles%ROWTYPE;
    schema_owner oid;
BEGIN
    SELECT * INTO STRICT runtime_role FROM pg_roles WHERE rolname = 'se214_runtime';
    SELECT nspowner INTO STRICT schema_owner FROM pg_namespace WHERE nspname = 'se214';
    IF schema_owner = runtime_role.oid THEN
        RAISE EXCEPTION 'se214_runtime khong duoc so huu schema se214.';
    END IF;
    IF runtime_role.rolsuper OR runtime_role.rolcreatedb OR runtime_role.rolcreaterole
       OR runtime_role.rolreplication OR runtime_role.rolbypassrls
       OR EXISTS (SELECT 1 FROM pg_auth_members WHERE member = runtime_role.oid) THEN
        RAISE EXCEPTION 'se214_runtime co quyen quan tri/membership ngoai du kien.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'se214' AND c.relowner = runtime_role.oid
    ) THEN
        RAISE EXCEPTION 'se214_runtime dang so huu doi tuong trong se214; REVOKE khong loai bo quyen chu so huu.';
    END IF;
END
$verify$;

REVOKE ALL ON SCHEMA se214 FROM PUBLIC;
REVOKE ALL ON SCHEMA se214 FROM se214_runtime;
GRANT USAGE ON SCHEMA se214 TO se214_runtime;

-- Khoi tao lai quyen cua runtime theo danh sach cho phep; ca bang lich su Flyway
-- va bang moi chua duoc ra soat deu khong tu dong co quyen runtime.
REVOKE ALL ON ALL TABLES IN SCHEMA se214 FROM PUBLIC, se214_runtime;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA se214 FROM PUBLIC, se214_runtime;

GRANT SELECT, INSERT, UPDATE ON
    se214.tai_khoan,
    se214.phien_xac_thuc,
    se214.tin_bat_dong_san,
    se214.phien_ban_tin,
    se214.han_muc_thang
TO se214_runtime;

GRANT SELECT, INSERT, UPDATE, DELETE ON se214.anh_tin TO se214_runtime;
GRANT SELECT, INSERT, DELETE ON se214.yeu_thich_tin TO se214_runtime;

-- Sprint 1 chi doc/cap nhat goi va bao cao da khoi tao boi fixture/quan tri.
GRANT SELECT, UPDATE ON se214.goi_day_tin, se214.bao_cao_vi_pham TO se214_runtime;
GRANT SELECT, INSERT ON
    se214.su_kien_goi,
    se214.yeu_cau_ghi,
    se214.nhat_ky_kiem_toan
TO se214_runtime;

-- UUID do backend cap: khong can cap USAGE sequence. Khong cap quyen lich su
-- Flyway, TRUNCATE, CREATE, DELETE tin/tai khoan, hoac UPDATE/DELETE audit.
COMMIT;
