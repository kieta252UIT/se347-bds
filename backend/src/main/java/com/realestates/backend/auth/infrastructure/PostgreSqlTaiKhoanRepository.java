package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlTaiKhoanRepository implements TaiKhoanRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlTaiKhoanRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource không được null"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PostgreSqlTaiKhoanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM se214.tai_khoan WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, normalizedEmail.trim().toLowerCase(Locale.ROOT));
        return count != null && count > 0;
    }

    @Override
    public Optional<TaiKhoan> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.tai_khoan WHERE id = ?";
        return queryOptional(sql, id);
    }

    @Override
    public Optional<TaiKhoan> findByIdForUpdate(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.tai_khoan WHERE id = ? FOR UPDATE";
        return queryOptional(sql, id);
    }

    @Override
    public java.util.List<TaiKhoan> findAllByIdsForUpdateInOrder(java.util.Collection<UUID> ids) {
        java.util.List<UUID> sortedIds = com.realestates.backend.auth.domain.MaTranQuyenSprint2.sapXepThuTuKhoaUuid(ids);
        if (sortedIds.isEmpty()) {
            return java.util.List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(sortedIds.size(), "?"));
        String sql = "SELECT * FROM se214.tai_khoan WHERE id IN (" + placeholders + ") ORDER BY id ASC FOR UPDATE";
        return jdbcTemplate.query(sql, ACCOUNT_ROW_MAPPER, sortedIds.toArray());
    }

    @Override
    public DataSource getDataSource() {
        return jdbcTemplate.getDataSource();
    }

    @Override
    public Optional<TaiKhoan> findByEmail(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.tai_khoan WHERE email = ?";
        return queryOptional(sql, normalizedEmail.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public Optional<TaiKhoan> findBySupabaseAuthUserId(UUID supabaseAuthUserId) {
        if (supabaseAuthUserId == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.tai_khoan WHERE supabase_auth_user_id = ?";
        return queryOptional(sql, supabaseAuthUserId);
    }

    @Override
    public TaiKhoan save(TaiKhoan taiKhoan) {
        Objects.requireNonNull(taiKhoan, "taiKhoan không được null");
        String sql = """
                INSERT INTO se214.tai_khoan (
                    id, supabase_auth_user_id, email, ho_ten, so_dien_thoai,
                    vai_tro, trang_thai, email_da_xac_minh, moc_thu_hoi,
                    trang_thai_dong_bo_auth, ma_thao_tac_auth, cap_nhat_auth_luc
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    ho_ten = EXCLUDED.ho_ten,
                    so_dien_thoai = EXCLUDED.so_dien_thoai,
                    vai_tro = EXCLUDED.vai_tro,
                    trang_thai = EXCLUDED.trang_thai,
                    email_da_xac_minh = EXCLUDED.email_da_xac_minh,
                    moc_thu_hoi = EXCLUDED.moc_thu_hoi,
                    trang_thai_dong_bo_auth = EXCLUDED.trang_thai_dong_bo_auth,
                    ma_thao_tac_auth = EXCLUDED.ma_thao_tac_auth,
                    cap_nhat_auth_luc = EXCLUDED.cap_nhat_auth_luc
                """;

        try {
            jdbcTemplate.update(sql,
                    taiKhoan.getId(),
                    taiKhoan.getSupabaseAuthUserId(),
                    taiKhoan.getEmail().trim().toLowerCase(Locale.ROOT),
                    taiKhoan.getHoTen().trim(),
                    taiKhoan.getSoDienThoai() != null ? taiKhoan.getSoDienThoai().trim() : null,
                    taiKhoan.getVaiTro().getGiaTri(),
                    taiKhoan.getTrangThai().getGiaTri(),
                    taiKhoan.isEmailDaXacMinh(),
                    taiKhoan.getMocThuHoi(),
                    taiKhoan.getTrangThaiDongBoAuth(),
                    taiKhoan.getMaThaoTacAuth(),
                    Timestamp.from(taiKhoan.getCapNhatAuthLuc())
            );
            return taiKhoan;
        } catch (DuplicateKeyException ex) {
            throw new EmailAlreadyExistsException();
        }
    }

    private Optional<TaiKhoan> queryOptional(String sql, Object... args) {
        try {
            TaiKhoan result = jdbcTemplate.queryForObject(sql, ACCOUNT_ROW_MAPPER, args);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static final RowMapper<TaiKhoan> ACCOUNT_ROW_MAPPER = (rs, rowNum) -> mapRowToAccount(rs);

    private static TaiKhoan mapRowToAccount(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID supabaseAuthUserId = rs.getObject("supabase_auth_user_id", UUID.class);
        String email = rs.getString("email");
        String hoTen = rs.getString("ho_ten");
        String soDienThoai = rs.getString("so_dien_thoai");
        String vaiTroStr = rs.getString("vai_tro");
        String trangThaiStr = rs.getString("trang_thai");
        boolean emailDaXacMinh = rs.getBoolean("email_da_xac_minh");
        long mocThuHoi = rs.getLong("moc_thu_hoi");
        String syncState = rs.getString("trang_thai_dong_bo_auth");
        UUID maThaoTacAuth = rs.getObject("ma_thao_tac_auth", UUID.class);
        Instant capNhatAuthLuc = rs.getTimestamp("cap_nhat_auth_luc").toInstant();
        Instant taoLuc = capNhatAuthLuc;
        Instant capNhatLuc = capNhatAuthLuc;

        return TaiKhoan.khoiPhucTuLuuTru(
                id,
                supabaseAuthUserId,
                email,
                hoTen,
                soDienThoai,
                chuyenVaiTro(vaiTroStr),
                chuyenTrangThai(trangThaiStr),
                emailDaXacMinh,
                mocThuHoi,
                syncState,
                maThaoTacAuth,
                capNhatAuthLuc,
                taoLuc,
                capNhatLuc
        );
    }

    @Override
    public boolean updateDongBoAuthCas(
            UUID taiKhoanId,
            UUID expectedMaThaoTacAuth,
            String newTrangThaiDongBoAuth,
            UUID newMaThaoTacAuth,
            Instant now
    ) {
        if (taiKhoanId == null) {
            return false;
        }
        Instant timestamp = now != null ? now : Instant.now();
        String sql;
        int rows;
        if (expectedMaThaoTacAuth == null) {
            sql = """
                    UPDATE se214.tai_khoan
                    SET trang_thai_dong_bo_auth = ?,
                        ma_thao_tac_auth = ?,
                        cap_nhat_auth_luc = ?
                    WHERE id = ? AND ma_thao_tac_auth IS NULL
                    """;
            rows = jdbcTemplate.update(
                    sql,
                    newTrangThaiDongBoAuth,
                    newMaThaoTacAuth,
                    Timestamp.from(timestamp),
                    taiKhoanId
            );
        } else {
            sql = """
                    UPDATE se214.tai_khoan
                    SET trang_thai_dong_bo_auth = ?,
                        ma_thao_tac_auth = ?,
                        cap_nhat_auth_luc = ?
                    WHERE id = ? AND ma_thao_tac_auth = ?
                    """;
            rows = jdbcTemplate.update(
                    sql,
                    newTrangThaiDongBoAuth,
                    newMaThaoTacAuth,
                    Timestamp.from(timestamp),
                    taiKhoanId,
                    expectedMaThaoTacAuth
            );
        }
        return rows > 0;
    }

    @Override
    public java.util.List<TaiKhoan> findPendingAuthSyncAccounts(Instant olderThan) {
        String sql = """
                SELECT * FROM se214.tai_khoan
                WHERE trang_thai_dong_bo_auth IN ('dang_dat_lai', 'can_khoi_phuc')
                  AND cap_nhat_auth_luc <= ?
                ORDER BY cap_nhat_auth_luc ASC
                """;
        return jdbcTemplate.query(sql, ACCOUNT_ROW_MAPPER, Timestamp.from(olderThan != null ? olderThan : Instant.now()));
    }

    private static VaiTroTaiKhoan chuyenVaiTro(String vaiTroStr) {
        if (vaiTroStr == null || vaiTroStr.isBlank()) {
            return VaiTroTaiKhoan.NGUOI_DUNG;
        }
        for (VaiTroTaiKhoan vt : VaiTroTaiKhoan.values()) {
            if (vt.getGiaTri().equalsIgnoreCase(vaiTroStr.trim()) || vt.name().equalsIgnoreCase(vaiTroStr.trim())) {
                return vt;
            }
        }
        return VaiTroTaiKhoan.NGUOI_DUNG;
    }

    private static TrangThaiTaiKhoan chuyenTrangThai(String trangThaiStr) {
        if (trangThaiStr == null || trangThaiStr.isBlank()) {
            return TrangThaiTaiKhoan.HOAT_DONG;
        }
        for (TrangThaiTaiKhoan tt : TrangThaiTaiKhoan.values()) {
            if (tt.getGiaTri().equalsIgnoreCase(trangThaiStr.trim()) || tt.name().equalsIgnoreCase(trangThaiStr.trim())) {
                return tt;
            }
        }
        return TrangThaiTaiKhoan.HOAT_DONG;
    }
}
