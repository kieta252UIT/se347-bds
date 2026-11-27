package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.domain.ViDienTuRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlViDienTuRepository implements ViDienTuRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlViDienTuRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource không được null"));
    }

    @Autowired
    public PostgreSqlViDienTuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    private static final RowMapper<ViDienTu> ROW_MAPPER = (rs, rowNum) -> new ViDienTu(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("tai_khoan_id")),
            rs.getBigDecimal("so_du_vnd"),
            rs.getTimestamp("tao_luc").toInstant(),
            rs.getTimestamp("cap_nhat_luc").toInstant()
    );

    @Override
    public Optional<ViDienTu> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, tai_khoan_id, so_du_vnd, tao_luc, cap_nhat_luc FROM se214.vi_dien_tu WHERE tai_khoan_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, taiKhoanId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ViDienTu> findByTaiKhoanIdForUpdate(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, tai_khoan_id, so_du_vnd, tao_luc, cap_nhat_luc FROM se214.vi_dien_tu WHERE tai_khoan_id = ? FOR UPDATE";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, taiKhoanId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ViDienTu> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, tai_khoan_id, so_du_vnd, tao_luc, cap_nhat_luc FROM se214.vi_dien_tu WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ViDienTu> findByIdForUpdate(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, tai_khoan_id, so_du_vnd, tao_luc, cap_nhat_luc FROM se214.vi_dien_tu WHERE id = ? FOR UPDATE";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public ViDienTu save(ViDienTu vi) {
        Objects.requireNonNull(vi, "vi không được null");
        String sql = """
            INSERT INTO se214.vi_dien_tu (id, tai_khoan_id, so_du_vnd, tao_luc, cap_nhat_luc)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                so_du_vnd = EXCLUDED.so_du_vnd,
                cap_nhat_luc = EXCLUDED.cap_nhat_luc
        """;
        jdbcTemplate.update(
                sql,
                vi.id(),
                vi.taiKhoanId(),
                vi.soDuVnd(),
                Timestamp.from(vi.taoLuc()),
                Timestamp.from(vi.capNhatLuc())
        );
        return vi;
    }
}
