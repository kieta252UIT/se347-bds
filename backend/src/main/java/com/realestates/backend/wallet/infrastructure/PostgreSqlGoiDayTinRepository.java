package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.GoiDayTin;
import com.realestates.backend.wallet.domain.GoiDayTinRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
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
public class PostgreSqlGoiDayTinRepository implements GoiDayTinRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlGoiDayTinRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource không được null"));
    }

    @Autowired
    public PostgreSqlGoiDayTinRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    private static final RowMapper<GoiDayTin> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp hetHanTs = rs.getTimestamp("het_han_luc");
        long thoiLuongGiay = rs.getLong("thoi_luong_giay");
        return new GoiDayTin(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("tin_id")),
                rs.getString("trang_thai"),
                Duration.ofSeconds(thoiLuongGiay),
                hetHanTs != null ? hetHanTs.toInstant() : null
        );
    };

    @Override
    public Optional<GoiDayTin> findGoiChuaKetThuc(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, tin_id, trang_thai,
                   COALESCE(EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint, 0) AS thoi_luong_giay,
                   het_han_luc
            FROM se214.goi_day_tin
            WHERE tin_id = ? AND trang_thai IN ('dang_hoat_dong', 'tam_dung_cho_duyet')
            LIMIT 1
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, tinId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<GoiDayTin> findGoiChuaKetThucForUpdate(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, tin_id, trang_thai,
                   COALESCE(EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint, 0) AS thoi_luong_giay,
                   het_han_luc
            FROM se214.goi_day_tin
            WHERE tin_id = ? AND trang_thai IN ('dang_hoat_dong', 'tam_dung_cho_duyet')
            LIMIT 1
            FOR UPDATE
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, tinId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<GoiDayTin> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, tin_id, trang_thai,
                   COALESCE(EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint, 0) AS thoi_luong_giay,
                   het_han_luc
            FROM se214.goi_day_tin
            WHERE id = ?
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public GoiDayTin save(GoiDayTin goi) {
        Objects.requireNonNull(goi, "goi không được null");
        String sql = """
            INSERT INTO se214.goi_day_tin (id, tin_id, trang_thai, thoi_luong_con_lai, het_han_luc)
            VALUES (?, ?, ?, (? || ' seconds')::interval, ?)
            ON CONFLICT (id) DO UPDATE SET
                trang_thai = EXCLUDED.trang_thai,
                thoi_luong_con_lai = EXCLUDED.thoi_luong_con_lai,
                het_han_luc = EXCLUDED.het_han_luc
        """;
        Timestamp hetHanTs = goi.hetHanLuc() != null ? Timestamp.from(goi.hetHanLuc()) : null;
        jdbcTemplate.update(
                sql,
                goi.id(),
                goi.tinId(),
                goi.trangThai(),
                String.valueOf(goi.thoiLuongConLai().getSeconds()),
                hetHanTs
        );
        return goi;
    }
}
