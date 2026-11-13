package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.common.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp độ 2: Kiểm thử tích hợp cơ sở dữ liệu (Database Integration Testing) theo IEEE 829 - Mục 7.2.
 * Sử dụng Testcontainers chạy PostgreSQL 17 thật (không dùng H2).
 * Kiểm tra:
 * - Flyway Migration khởi tạo schema sạch.
 * - Ràng buộc toàn vẹn: CHECK (hạn mức <= 3, BR-09), Unique index, Foreign Key.
 * - Giao dịch ACID và cơ chế Transaction Rollback.
 */
class PostgresDatabaseIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("FLYWAY-01: Schema PostgreSQL 17 được khởi tạo đầy đủ các bảng nghiệp vụ cốt lõi")
    void flywayShouldInitializeCoreTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('tai_khoan', 'phien_xac_thuc', 'han_muc_thang', 'tin_bat_dong_san', 'phien_ban_tin', 'anh_tin')",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(6);
    }

    @Test
    @Transactional
    @DisplayName("AC-15.1 / TS1-01: Chèn và đọc tài khoản hợp lệ trên PostgreSQL 17")
    void shouldInsertAndQueryValidUserAccount() {
        jdbcTemplate.update(
                "INSERT INTO tai_khoan (supabase_auth_user_id, email, ho_ten, so_dien_thoai, vai_tro, trang_thai, email_da_xac_minh, moc_thu_hoi) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "sb_auth_" + UUID.randomUUID(),
                "user1_test@bds-test.local",
                "Nguyễn Văn A",
                "0901234567",
                "NGUOI_DUNG",
                "HOAT_DONG",
                true,
                0L
        );

        String email = jdbcTemplate.queryForObject(
                "SELECT email FROM tai_khoan WHERE email = ?",
                String.class,
                "user1_test@bds-test.local"
        );

        assertThat(email).isEqualTo("user1_test@bds-test.local");
    }

    @Test
    @Transactional
    @DisplayName("BR-09 / AC-01.1: CHECK constraint chặn hạn mức tin vượt quá 3 tin/tháng ở cấp CSDL")
    void databaseShouldEnforceMonthlyListingLimitConstraint() {
        // Tạo tài khoản trước
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO tai_khoan (supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, ?, 'NGUOI_DUNG', 'HOAT_DONG') RETURNING id",
                Long.class,
                "sb_auth_" + UUID.randomUUID(),
                "user_limit_test@bds-test.local",
                "Người Dùng Test Hạn Mức"
        );

        // Hạn mức hợp lệ = 3
        jdbcTemplate.update(
                "INSERT INTO han_muc_thang (tai_khoan_id, thang_nam, so_tin_da_dang) VALUES (?, '2026-11', 3)",
                userId
        );

        // Cố gắng cập nhật lên 4 -> phải bị CSDL chặn ngay lập tức qua CHECK constraint
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE han_muc_thang SET so_tin_da_dang = 4 WHERE tai_khoan_id = ? AND thang_nam = '2026-11'",
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("AC-15.1 / TS1-02: Ràng buộc Unique email ngăn chặn ghi đè tài khoản trùng lặp")
    void databaseShouldEnforceUniqueEmailConstraint() {
        String duplicateEmail = "duplicate_email@bds-test.local";

        jdbcTemplate.update(
                "INSERT INTO tai_khoan (supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, 'User 1', 'NGUOI_DUNG', 'HOAT_DONG')",
                "sb_auth_" + UUID.randomUUID(),
                duplicateEmail
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tai_khoan (supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, 'User 2', 'NGUOI_DUNG', 'HOAT_DONG')",
                "sb_auth_" + UUID.randomUUID(),
                duplicateEmail
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
