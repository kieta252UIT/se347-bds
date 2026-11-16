package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Kho lưu trữ Phiên xác thực bền vững trên PostgreSQL (bảng se214.phien_xac_thuc)
 * theo đặc tả database-design-sprint-1.md §3.2, ADR-0010, ADR-0012, PHIEN-02, PHIEN-08.
 * Đảm bảo dữ liệu phiên và trạng thái thu hồi được duy trì an toàn qua mọi lần khởi động lại backend.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlPhienXacThucRepository implements PhienXacThucRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlPhienXacThucRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource không được null"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PostgreSqlPhienXacThucRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public Optional<PhienXacThuc> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.phien_xac_thuc WHERE id = ?";
        return queryOptional(sql, id);
    }

    @Override
    public Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam) {
        if (maCookieBam == null || maCookieBam.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.phien_xac_thuc WHERE ma_cookie_bam = ?";
        return queryOptional(sql, maCookieBam.trim());
    }

    @Override
    public Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId) {
        if (supabaseSessionId == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM se214.phien_xac_thuc WHERE supabase_session_id = ?";
        return queryOptional(sql, supabaseSessionId);
    }

    @Override
    public List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return List.of();
        }
        String sql = "SELECT * FROM se214.phien_xac_thuc WHERE tai_khoan_id = ? ORDER BY tao_luc DESC";
        return jdbcTemplate.query(sql, SESSION_ROW_MAPPER, taiKhoanId);
    }

    @Override
    public PhienXacThuc save(PhienXacThuc phien) {
        Objects.requireNonNull(phien, "phien không được null");
        String sql = """
                INSERT INTO se214.phien_xac_thuc (
                    id, tai_khoan_id, ma_cookie_bam, moc_thu_hoi_luc_cap, tao_luc,
                    thu_hoi_luc, ly_do_thu_hoi, supabase_session_id, access_token_ma_hoa,
                    refresh_token_ma_hoa, access_token_het_han_luc, phien_ban_khoa_ma_hoa,
                    phien_ban_cap_nhat, refresh_lease_id, refresh_lease_den_luc, can_thu_hoi_provider
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    tai_khoan_id = EXCLUDED.tai_khoan_id,
                    ma_cookie_bam = EXCLUDED.ma_cookie_bam,
                    moc_thu_hoi_luc_cap = EXCLUDED.moc_thu_hoi_luc_cap,
                    thu_hoi_luc = EXCLUDED.thu_hoi_luc,
                    ly_do_thu_hoi = EXCLUDED.ly_do_thu_hoi,
                    access_token_ma_hoa = EXCLUDED.access_token_ma_hoa,
                    refresh_token_ma_hoa = EXCLUDED.refresh_token_ma_hoa,
                    access_token_het_han_luc = EXCLUDED.access_token_het_han_luc,
                    phien_ban_khoa_ma_hoa = EXCLUDED.phien_ban_khoa_ma_hoa,
                    phien_ban_cap_nhat = EXCLUDED.phien_ban_cap_nhat,
                    refresh_lease_id = EXCLUDED.refresh_lease_id,
                    refresh_lease_den_luc = EXCLUDED.refresh_lease_den_luc,
                    can_thu_hoi_provider = EXCLUDED.can_thu_hoi_provider
                """;

        jdbcTemplate.update(sql,
                phien.getId(),
                phien.getTaiKhoanId(),
                phien.getMaCookieBam(),
                phien.getMocThuHoiLucCap(),
                Timestamp.from(phien.getTaoLuc()),
                toTimestamp(phien.getThuHoiLuc()),
                phien.getLyDoThuHoi(),
                phien.getSupabaseSessionId(),
                phien.getAccessTokenMaHoa(),
                phien.getRefreshTokenMaHoa(),
                toTimestamp(phien.getAccessTokenHetHanLuc()),
                phien.getPhienBanKhoaMaHoa(),
                phien.getPhienBanCapNhat(),
                phien.getRefreshLeaseId(),
                toTimestamp(phien.getRefreshLeaseDenLuc()),
                phien.isCanThuHoiProvider()
        );
        return phien;
    }

    @Override
    public Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(
            String oldMaCookieBam,
            PhienXacThuc newPhien,
            Instant now
    ) {
        Objects.requireNonNull(newPhien, "newPhien không được null");
        Instant timestamp = now != null ? now : Instant.now();
        Optional<PhienXacThuc> revokedOld = Optional.empty();

        if (oldMaCookieBam != null && !oldMaCookieBam.isBlank()) {
            revokedOld = revokeByMaCookieBam(
                    oldMaCookieBam.trim(),
                    PhienXacThuc.LY_DO_THAY_PHIEN_DANG_NHAP,
                    timestamp,
                    true
            );
        }

        save(newPhien);
        return revokedOld;
    }

    @Override
    public Optional<PhienXacThuc> revokeByMaCookieBam(
            String maCookieBam,
            String lyDoThuHoi,
            Instant now,
            boolean giuLaiCredentialDeThuHoiProvider
    ) {
        if (maCookieBam == null || maCookieBam.isBlank()) {
            return Optional.empty();
        }
        Optional<PhienXacThuc> currentOpt = findByMaCookieBam(maCookieBam);
        if (currentOpt.isEmpty()) {
            return Optional.empty();
        }
        PhienXacThuc current = currentOpt.get();
        if (current.isDaThuHoi()) {
            return Optional.of(current);
        }

        Instant timestamp = now != null ? now : Instant.now();
        PhienXacThuc revoked = current.thuHoi(lyDoThuHoi, timestamp, giuLaiCredentialDeThuHoiProvider);
        save(revoked);
        return Optional.of(revoked);
    }

    @Override
    public Optional<PhienXacThuc> tryAcquireRefreshLease(
            UUID phienId,
            UUID leaseId,
            Instant leaseUntil,
            Instant now
    ) {
        if (phienId == null || leaseId == null || leaseUntil == null) {
            return Optional.empty();
        }
        Instant currentTime = now != null ? now : Instant.now();
        String sql = """
                UPDATE se214.phien_xac_thuc
                SET refresh_lease_id = ?,
                    refresh_lease_den_luc = ?,
                    phien_ban_cap_nhat = phien_ban_cap_nhat + 1
                WHERE id = ?
                  AND thu_hoi_luc IS NULL
                  AND (refresh_lease_den_luc IS NULL OR refresh_lease_den_luc <= ?)
                RETURNING *
                """;
        try {
            PhienXacThuc updated = jdbcTemplate.queryForObject(
                    sql,
                    SESSION_ROW_MAPPER,
                    leaseId,
                    Timestamp.from(leaseUntil),
                    phienId,
                    Timestamp.from(currentTime)
            );
            return Optional.ofNullable(updated);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean updateRefreshedTokensWithCas(
            UUID phienId,
            UUID expectedLeaseId,
            long expectedCasVersion,
            byte[] newAccessTokenMaHoa,
            byte[] newRefreshTokenMaHoa,
            Instant newAccessTokenHetHanLuc,
            String phienBanKhoaMaHoa,
            Instant now
    ) {
        if (phienId == null || expectedLeaseId == null) {
            return false;
        }
        String sql = """
                UPDATE se214.phien_xac_thuc
                SET access_token_ma_hoa = ?,
                    refresh_token_ma_hoa = ?,
                    access_token_het_han_luc = ?,
                    phien_ban_khoa_ma_hoa = ?,
                    refresh_lease_id = NULL,
                    refresh_lease_den_luc = NULL,
                    phien_ban_cap_nhat = phien_ban_cap_nhat + 1
                WHERE id = ?
                  AND thu_hoi_luc IS NULL
                  AND refresh_lease_id = ?
                  AND phien_ban_cap_nhat = ?
                """;

        int rows = jdbcTemplate.update(
                sql,
                newAccessTokenMaHoa,
                newRefreshTokenMaHoa,
                Timestamp.from(newAccessTokenHetHanLuc),
                phienBanKhoaMaHoa,
                phienId,
                expectedLeaseId,
                expectedCasVersion
        );
        return rows > 0;
    }

    @Override
    public void releaseRefreshLease(UUID phienId, UUID leaseId) {
        if (phienId == null || leaseId == null) {
            return;
        }
        String sql = """
                UPDATE se214.phien_xac_thuc
                SET refresh_lease_id = NULL,
                    refresh_lease_den_luc = NULL,
                    phien_ban_cap_nhat = phien_ban_cap_nhat + 1
                WHERE id = ?
                  AND refresh_lease_id = ?
                """;
        jdbcTemplate.update(sql, phienId, leaseId);
    }

    private Optional<PhienXacThuc> queryOptional(String sql, Object... args) {
        try {
            PhienXacThuc result = jdbcTemplate.queryForObject(sql, SESSION_ROW_MAPPER, args);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static final RowMapper<PhienXacThuc> SESSION_ROW_MAPPER = (rs, rowNum) -> mapRowToSession(rs);

    private static PhienXacThuc mapRowToSession(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID taiKhoanId = rs.getObject("tai_khoan_id", UUID.class);
        String maCookieBam = rs.getString("ma_cookie_bam");
        long mocThuHoi = rs.getLong("moc_thu_hoi_luc_cap");
        Instant taoLuc = rs.getTimestamp("tao_luc").toInstant();
        Timestamp thuHoiTs = rs.getTimestamp("thu_hoi_luc");
        Instant thuHoiLuc = thuHoiTs != null ? thuHoiTs.toInstant() : null;
        String lyDoThuHoi = rs.getString("ly_do_thu_hoi");
        UUID supabaseSessionId = rs.getObject("supabase_session_id", UUID.class);
        byte[] accessToken = rs.getBytes("access_token_ma_hoa");
        byte[] refreshToken = rs.getBytes("refresh_token_ma_hoa");
        Timestamp expTs = rs.getTimestamp("access_token_het_han_luc");
        Instant accessExpLuc = expTs != null ? expTs.toInstant() : null;
        String keyVersion = rs.getString("phien_ban_khoa_ma_hoa");
        long version = rs.getLong("phien_ban_cap_nhat");
        UUID leaseId = rs.getObject("refresh_lease_id", UUID.class);
        Timestamp leaseTs = rs.getTimestamp("refresh_lease_den_luc");
        Instant leaseDenLuc = leaseTs != null ? leaseTs.toInstant() : null;
        boolean canThuHoi = rs.getBoolean("can_thu_hoi_provider");

        return new PhienXacThuc(
                id,
                taiKhoanId,
                maCookieBam,
                mocThuHoi,
                taoLuc,
                thuHoiLuc,
                lyDoThuHoi,
                supabaseSessionId,
                accessToken,
                refreshToken,
                accessExpLuc,
                keyVersion,
                version,
                leaseId,
                leaseDenLuc,
                canThuHoi
        );
    }
}
