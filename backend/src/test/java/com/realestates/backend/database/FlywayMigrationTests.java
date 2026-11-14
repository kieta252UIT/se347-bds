package com.realestates.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kiểm migration và bất biến SQL trên PostgreSQL thật, không thay test API/provider. */
class FlywayMigrationTests {
    private TestDatabaseSettings db;
    private boolean schemaPrepared;

    @TempDir
    Path temporaryMigrations;

    @BeforeEach
    void migrateEmptyDatabase() throws Exception {
        db = TestDatabaseSettings.fromEnvironment();
        db.resetSchema();
        db.flyway().migrate();
        schemaPrepared = true;
    }

    @AfterEach
    void restoreBaselineForOtherTestClasses() throws Exception {
        if (schemaPrepared) {
            db.resetSchema();
            db.flyway().migrate();
        }
    }

    @Test
    void emptyDatabaseHasTwelveBusinessTablesAndSuccessfulV1() throws Exception {
        var names = new ArrayList<String>();
        try (var connection = db.connect(); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT tablename FROM pg_tables WHERE schemaname='se214'
                     AND tablename <> 'flyway_schema_history'
                     """)) {
            while (result.next()) names.add(result.getString(1));
        }
        assertThat(names).containsExactlyInAnyOrder("tai_khoan", "phien_xac_thuc", "tin_bat_dong_san",
                "phien_ban_tin", "anh_tin", "han_muc_thang", "yeu_thich_tin", "goi_day_tin",
                "su_kien_goi", "bao_cao_vi_pham", "yeu_cau_ghi", "nhat_ky_kiem_toan");
        assertThat(count("SELECT count(*) FROM se214.flyway_schema_history WHERE version='1' AND success"))
                .isEqualTo(1);
        assertThat(db.flyway().validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void secondMigrationIsNoOpAndPreservesApplicationData() throws Exception {
        UUID account = createAccount();
        assertThat(db.flyway().migrate().migrationsExecuted).isZero();
        assertThat(count("SELECT count(*) FROM se214.tai_khoan WHERE id='" + account + "'"))
                .isEqualTo(1);
    }

    @Test
    void nextVersionUpgradesExistingDatabaseWithoutLosingData() throws Exception {
        createAccount();
        String location = copyBaseline();
        Files.writeString(temporaryMigrations.resolve("V2__test_upgrade.sql"),
                "ALTER TABLE se214.tai_khoan ADD COLUMN test_upgrade_marker integer NOT NULL DEFAULT 7;");
        assertThat(db.flyway(location).migrate().migrationsExecuted).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM se214.tai_khoan WHERE test_upgrade_marker=7")).isEqualTo(1);
    }

    @Test
    void failedMigrationRollsBackDdlAndData() throws Exception {
        createAccount();
        String location = copyBaseline();
        Files.writeString(temporaryMigrations.resolve("V2__test_rollback.sql"), """
                CREATE TABLE se214.test_partial_migration (id integer PRIMARY KEY);
                INSERT INTO se214.test_partial_migration VALUES (1);
                UPDATE se214.tai_khoan SET ho_ten='Không được commit';
                SELECT 1 / 0;
                """);
        assertThatThrownBy(() -> db.flyway(location).migrate()).isInstanceOf(FlywayException.class);
        assertThat(count("SELECT count(*) FROM pg_tables WHERE schemaname='se214' AND tablename='test_partial_migration'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM se214.tai_khoan WHERE ho_ten='Người thử'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM se214.flyway_schema_history WHERE version='2'" )).isZero();
    }

    @Test
    void changingAppliedMigrationFailsValidation() throws Exception {
        String location = copyBaseline();
        Path baseline = temporaryMigrations.resolve("V1__khoi_tao_schema_sprint_1.sql");
        Files.writeString(baseline, Files.readString(baseline) + "\n-- checksum thay đổi trong bản sao thử\n");
        assertThat(db.flyway(location).validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(() -> db.flyway(location).migrate()).isInstanceOf(FlywayException.class);
    }

    @Test
    void accountIdentityIsUniqueAndInvalidAuthStateIsRejected() throws Exception {
        UUID account = createAccount();
        reject("23505", "INSERT INTO se214.tai_khoan(id,supabase_auth_user_id,email) SELECT '" + UUID.randomUUID()
                + "',supabase_auth_user_id,'different@example.test' FROM se214.tai_khoan WHERE id='" + account + "'");
        reject("23505", "INSERT INTO se214.tai_khoan(id,supabase_auth_user_id,email) SELECT '" + UUID.randomUUID()
                + "','" + UUID.randomUUID() + "',email FROM se214.tai_khoan WHERE id='" + account + "'");
        reject("23514", "UPDATE se214.tai_khoan SET vai_tro='admin_tu_khai' WHERE id='" + account + "'");
        reject("23514", "UPDATE se214.tai_khoan SET trang_thai_dong_bo_auth='dang_dat_lai' WHERE id='" + account + "'");
    }

    @Test
    void sessionRequiresExistingOwnerAndConsistentRefreshState() throws Exception {
        UUID account = createAccount();
        UUID session = UUID.randomUUID();
        execute("INSERT INTO se214.phien_xac_thuc(id,tai_khoan_id,ma_cookie_bam,moc_thu_hoi_luc_cap,supabase_session_id,thu_hoi_luc) VALUES ('"
                + session + "','" + account + "','test-hash',0,'" + UUID.randomUUID() + "',CURRENT_TIMESTAMP)");
        reject("23503", "UPDATE se214.phien_xac_thuc SET tai_khoan_id='" + UUID.randomUUID() + "'");
        reject("23514", "UPDATE se214.phien_xac_thuc SET refresh_lease_id='" + UUID.randomUUID() + "'");
        reject("23514", "UPDATE se214.phien_xac_thuc SET thu_hoi_luc=NULL");
        reject("23514", "UPDATE se214.phien_xac_thuc SET access_token_ma_hoa=decode('01','hex')");
    }

    @Test
    void currentAndApprovedVersionMustBelongToTheSameListing() throws Exception {
        UUID owner = createAccount();
        Listing first = createListing(owner);
        Listing second = createListing(owner);
        for (String pointer : new String[]{"phien_ban_hien_tai_id", "phien_ban_da_duyet_id"}) {
            try (var connection = db.connect(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("UPDATE se214.tin_bat_dong_san SET " + pointer + "='" + second.version()
                        + "' WHERE id='" + first.id() + "'");
                assertThatThrownBy(connection::commit).isInstanceOf(SQLException.class)
                        .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("23503"));
                connection.rollback();
            }
        }
        reject("23503", "DELETE FROM se214.tin_bat_dong_san WHERE id='" + first.id() + "'");
    }

    @Test
    void listingPriceAndAreaArePositiveFiniteAndUnitMatchesTransactionType() throws Exception {
        Listing listing = createListing(createAccount());
        for (String column : new String[]{"gia_goc", "dien_tich_m2"}) {
            for (String invalid : new String[]{"0", "-1", "'NaN'", "'Infinity'"}) {
                reject("23514", "UPDATE se214.phien_ban_tin SET " + column + "=" + invalid
                        + " WHERE id='" + listing.version() + "'");
            }
        }
        reject("23514", "UPDATE se214.phien_ban_tin SET don_vi_gia='vnd_thang' WHERE id='" + listing.version() + "'");
        execute("UPDATE se214.phien_ban_tin SET gia_goc=123456789.123456789123456789 WHERE id='" + listing.version() + "'");
        assertThat(count("SELECT count(*) FROM se214.phien_ban_tin WHERE gia_goc=123456789.123456789123456789"))
                .isEqualTo(1);
    }

    @Test
    void imagesEnforceSizeAndUniquePositionWithinVersion() throws Exception {
        UUID owner = createAccount();
        Listing listing = createListing(owner);
        UUID image = UUID.randomUUID();
        execute("INSERT INTO se214.anh_tin(id,chu_id,phien_ban_tin_id,khoa_tep_goc,khoa_anh_hien_thi,mime_type,kich_thuoc_byte,thu_tu,trang_thai) VALUES ('"
                + image + "','" + owner + "','" + listing.version() + "','original','display','image/jpeg',10000000,1,'da_gan')");
        reject("23514", "UPDATE se214.anh_tin SET kich_thuoc_byte=10000001");
        reject("23514", "UPDATE se214.anh_tin SET kich_thuoc_byte=0");
        reject("23514", "UPDATE se214.anh_tin SET thu_tu=13");
        reject("23505", "INSERT INTO se214.anh_tin SELECT '" + UUID.randomUUID()
                + "',chu_id,phien_ban_tin_id,khoa_tep_goc,khoa_anh_hien_thi,mime_type,kich_thuoc_byte,thu_tu,trang_thai,tao_luc FROM se214.anh_tin WHERE id='" + image + "'");
    }

    @Test
    void quotaAndFavoritesRejectOverLimitDuplicatesAndDanglingReferences() throws Exception {
        UUID owner = createAccount();
        Listing listing = createListing(owner);
        execute("INSERT INTO se214.han_muc_thang VALUES ('" + owner + "','2026-11-01',3)");
        reject("23514", "UPDATE se214.han_muc_thang SET so_tin_moi=4");
        reject("23514", "UPDATE se214.han_muc_thang SET so_tin_moi=-1");
        reject("23514", "UPDATE se214.han_muc_thang SET thang='2026-11-02'");
        String favorite = "INSERT INTO se214.yeu_thich_tin(tai_khoan_id,tin_id) VALUES ('" + owner + "','" + listing.id() + "')";
        execute(favorite);
        reject("23505", favorite);
        reject("23503", "UPDATE se214.yeu_thich_tin SET tin_id='" + UUID.randomUUID() + "'");
        execute("DELETE FROM se214.yeu_thich_tin");
        assertThat(count("SELECT count(*) FROM se214.tin_bat_dong_san")).isEqualTo(1);
        assertThat(count("SELECT so_tin_moi FROM se214.han_muc_thang")).isEqualTo(3);
    }

    private String copyBaseline() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V1__khoi_tao_schema_sprint_1.sql")) {
            if (stream == null) throw new IllegalStateException("Không tìm thấy migration V1");
            Files.copy(stream, temporaryMigrations.resolve("V1__khoi_tao_schema_sprint_1.sql"));
        }
        return "filesystem:" + temporaryMigrations.toAbsolutePath().toString().replace('\\', '/');
    }

    private UUID createAccount() throws Exception {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO se214.tai_khoan(id,supabase_auth_user_id,email,ho_ten) VALUES ('" + id
                + "','" + UUID.randomUUID() + "','" + id + "@example.test','Người thử')");
        return id;
    }

    private Listing createListing(UUID owner) throws Exception {
        Listing listing = new Listing(UUID.randomUUID(), UUID.randomUUID());
        try (var connection = db.connect(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("INSERT INTO se214.tin_bat_dong_san(id,chu_id,phien_ban_hien_tai_id) VALUES ('"
                    + listing.id() + "','" + owner + "','" + listing.version() + "')");
            statement.execute("""
                    INSERT INTO se214.phien_ban_tin(id,tin_id,so_phien_ban,tieu_de,mo_ta,loai_giao_dich,loai_bds,
                    gia_goc,don_vi_gia,dien_tich_m2,dia_chi,khu_vuc,vi_do,kinh_do,ket_qua_so_bo)
                    VALUES ('%s','%s',1,'Tin thử','Mô tả thử','ban','can_ho',100,'vnd_tong',25,'Địa chỉ thử','thu_duc',10,106,'dat')
                    """.formatted(listing.version(), listing.id()));
            connection.commit();
        }
        return listing;
    }

    private void execute(String sql) throws SQLException {
        try (var connection = db.connect(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String sql) throws SQLException {
        try (var connection = db.connect(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void reject(String sqlState, String sql) {
        assertThatThrownBy(() -> execute(sql)).isInstanceOf(SQLException.class)
                .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo(sqlState));
    }

    private record Listing(UUID id, UUID version) {}
}
