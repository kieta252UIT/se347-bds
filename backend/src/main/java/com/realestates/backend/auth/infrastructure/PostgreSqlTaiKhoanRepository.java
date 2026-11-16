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

/**
 * Kho lưu trữ Tài khoản bền vững trên PostgreSQL (bảng se214.tai_khoan)
 * theo đặc tả database-design-sprint-1.md §3.1, ADR-0010, ADR-0016.
 */
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
                    trang_thai_dong_bo_auth, cap_nhat_auth_luc, tao_luc, cap_nhat_luc
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    email = EXCLUDED.email,
                    ho_ten = EXCLUDED.ho_ten,
                    so_dien_thoai = EXCLUDED.so_dien_thoai,
                    vai_tro = EXCLUDED.vai_tro,
                    trang_thai = EXCLUDED.trang_thai,
                    email_da_xac_minh = EXCLUDED.email_da_xac_minh,
                    moc_thu_hoi = EXCLUDED.moc_thu_hoi,
                    trang_thai_dong_bo_auth = EXCLUDED.trang_thai_dong_bo_auth,
                    cap_nhat_auth_luc = EXCLUDED.cap_nhat_auth_luc,
                    cap_nhat_luc = EXCLUDED.cap_nhat_luc
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
                    Timestamp.from(taiKhoan.getCapNhatAuthLuc()),
                    Timestamp.from(taiKhoan.getTaoLuc()),
                    Timestamp.from(taiKhoan.getCapNhatLuc())
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
        Instant capNhatAuthLuc = rs.getTimestamp("cap_nhat_auth_luc").toInstant();
        Instant taoLuc = rs.getTimestamp("tao_luc").toInstant();
        Instant capNhatLuc = rs.getTimestamp("cap_nhat_luc").toInstant();

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
                capNhatAuthLuc,
                taoLuc,
                capNhatLuc
        );
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
