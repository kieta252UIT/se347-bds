package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.LanMuaGoi;
import com.realestates.backend.wallet.domain.LanMuaGoiRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
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

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlLanMuaGoiRepository implements LanMuaGoiRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlLanMuaGoiRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource không được null"));
    }

    public PostgreSqlLanMuaGoiRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    private static final RowMapper<LanMuaGoi> ROW_MAPPER = (rs, rowNum) -> {
        long thoiLuongGiay = rs.getLong("thoi_luong_giay");
        return new LanMuaGoi(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("goi_id")),
                UUID.fromString(rs.getString("giao_dich_vi_id")),
                rs.getBigDecimal("gia_da_mua"),
                Duration.ofSeconds(thoiLuongGiay),
                rs.getTimestamp("thoi_diem_mua").toInstant()
        );
    };

    @Override
    public Optional<LanMuaGoi> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, goi_id, giao_dich_vi_id, gia_da_mua,
                   EXTRACT(EPOCH FROM thoi_luong_da_cap)::bigint AS thoi_luong_giay,
                   thoi_diem_mua
            FROM se214.lan_mua_goi
            WHERE id = ?
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<LanMuaGoi> findByGiaoDichViId(UUID giaoDichViId) {
        if (giaoDichViId == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, goi_id, giao_dich_vi_id, gia_da_mua,
                   EXTRACT(EPOCH FROM thoi_luong_da_cap)::bigint AS thoi_luong_giay,
                   thoi_diem_mua
            FROM se214.lan_mua_goi
            WHERE giao_dich_vi_id = ?
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, giaoDichViId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<LanMuaGoi> findByGoiId(UUID goiId) {
        if (goiId == null) {
            return List.of();
        }
        String sql = """
            SELECT id, goi_id, giao_dich_vi_id, gia_da_mua,
                   EXTRACT(EPOCH FROM thoi_luong_da_cap)::bigint AS thoi_luong_giay,
                   thoi_diem_mua
            FROM se214.lan_mua_goi
            WHERE goi_id = ?
            ORDER BY thoi_diem_mua DESC
        """;
        return jdbcTemplate.query(sql, ROW_MAPPER, goiId);
    }

    @Override
    public LanMuaGoi save(LanMuaGoi lanMua) {
        Objects.requireNonNull(lanMua, "lanMua không được null");
        String sql = """
            INSERT INTO se214.lan_mua_goi (id, goi_id, giao_dich_vi_id, gia_da_mua, thoi_luong_da_cap, thoi_diem_mua)
            VALUES (?, ?, ?, ?, (? || ' seconds')::interval, ?)
        """;
        jdbcTemplate.update(
                sql,
                lanMua.id(),
                lanMua.goiId(),
                lanMua.giaoDichViId(),
                lanMua.giaDaMua(),
                String.valueOf(lanMua.thoiLuongDaCap().getSeconds()),
                Timestamp.from(lanMua.thoiDiemMua())
        );
        return lanMua;
    }
}
