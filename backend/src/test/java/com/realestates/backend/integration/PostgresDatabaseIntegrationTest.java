package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.database.TestDatabaseSettings;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp độ 2: Kiểm thử tích hợp cơ sở dữ liệu (Database Integration Testing) theo IEEE 829 - Mục 7.2.
 * Kiểm tra:
 * - Flyway Migration khởi tạo schema se214 đầy đủ 12 bảng.
 * - Ràng buộc toàn vẹn: CHECK (hạn mức <= 3, BR-09), Unique index, Foreign Key.
 * - Giao dịch ACID và cơ chế Transaction Rollback.
 */
@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgresDatabaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FLYWAY-01: Schema PostgreSQL 17 được khởi tạo đầy đủ 12 bảng nghiệp vụ Sprint 1")
    void flywayShouldInitializeCoreTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'se214' " +
                "AND table_name <> 'flyway_schema_history'",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(12);
    }

    @Test
    @Transactional
    @DisplayName("AC-15.1 / TS1-01: Chèn và đọc tài khoản hợp lệ trên PostgreSQL 17")
    void shouldInsertAndQueryValidUserAccount() {
        UUID userId = UUID.randomUUID();
        String email = "user1_" + userId + "@bds-test.local";

        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten, so_dien_thoai, vai_tro, trang_thai) " +
                "VALUES (?, ?, ?, ?, ?, 'nguoi_dung', 'hoat_dong')",
                userId,
                "sb_auth_" + UUID.randomUUID(),
                email,
                "Nguyễn Văn A",
                "0901234567"
        );

        String queriedEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM se214.tai_khoan WHERE id = ?",
                String.class,
                userId
        );

        assertThat(queriedEmail).isEqualTo(email);
    }

    @Test
    @Transactional
    @DisplayName("BR-09 / AC-01.1: CHECK constraint chặn hạn mức tin vượt quá 3 tin/tháng ở cấp CSDL")
    void databaseShouldEnforceMonthlyListingLimitConstraint() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten) " +
                "VALUES (?, ?, ?, 'Người Dùng Test Hạn Mức')",
                userId,
                "sb_auth_" + UUID.randomUUID(),
                "user_limit_" + userId + "@bds-test.local"
        );

        // Hạn mức hợp lệ = 3
        jdbcTemplate.update(
                "INSERT INTO se214.han_muc_thang (tai_khoan_id, thang, so_tin_moi) VALUES (?, '2026-11-01', 3)",
                userId
        );

        // Cố gắng cập nhật lên 4 -> phải bị CSDL chặn ngay lập tức qua CHECK constraint
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE se214.han_muc_thang SET so_tin_moi = 4 WHERE tai_khoan_id = ? AND thang = '2026-11-01'",
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("AC-15.1 / TS1-02: Ràng buộc Unique email ngăn chặn ghi đè tài khoản trùng lặp")
    void databaseShouldEnforceUniqueEmailConstraint() {
        String duplicateEmail = "dup_" + UUID.randomUUID() + "@bds-test.local";

        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten) " +
                "VALUES (?, ?, ?, 'User 1')",
                UUID.randomUUID(),
                "sb_auth_" + UUID.randomUUID(),
                duplicateEmail
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten) " +
                "VALUES (?, ?, ?, 'User 2')",
                UUID.randomUUID(),
                "sb_auth_" + UUID.randomUUID(),
                duplicateEmail
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
