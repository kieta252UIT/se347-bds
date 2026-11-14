\set ON_ERROR_STOP on

-- Chay bang tai khoan quan tri co CREATEROLE va quyen cap CREATE tren database.
-- Khong nhung mat khau. Role moi la NOLOGIN; quan tri vien dat credential rieng.
BEGIN;

DO $bootstrap$
DECLARE
    role_name text;
    target_role pg_roles%ROWTYPE;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['se214_migrator', 'se214_runtime'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format(
                'CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS',
                role_name
            );
        END IF;

        SELECT * INTO STRICT target_role FROM pg_roles WHERE rolname = role_name;
        IF target_role.rolsuper OR target_role.rolcreatedb OR target_role.rolcreaterole
           OR target_role.rolreplication OR target_role.rolbypassrls THEN
            RAISE EXCEPTION 'Role % dang co thuoc tinh quan tri; khong tu dong thay doi role nay.', role_name;
        END IF;
        -- Kiem membership ma role dich NHAN. Khong cam quan tri vien la thanh vien
        -- cua role dich: PostgreSQL 17 co the tu cap ADMIN cho nguoi tao role.
        IF EXISTS (SELECT 1 FROM pg_auth_members WHERE member = target_role.oid) THEN
            RAISE EXCEPTION 'Role % dang la thanh vien cua role khac; can ra soat quyen truoc.', role_name;
        END IF;
    END LOOP;

    EXECUTE format('GRANT CONNECT, CREATE ON DATABASE %I TO se214_migrator', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO se214_runtime', current_database());
    EXECUTE format('REVOKE CREATE ON DATABASE %I FROM se214_runtime', current_database());
    IF has_database_privilege('se214_runtime', current_database(), 'CREATE') THEN
        RAISE EXCEPTION 'se214_runtime van co CREATE database qua PUBLIC/quyen so huu; can quan tri vien ra soat.';
    END IF;
END
$bootstrap$;

COMMIT;
