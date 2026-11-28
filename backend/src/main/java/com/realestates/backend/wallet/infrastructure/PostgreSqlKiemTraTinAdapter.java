package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.application.KiemTraTinChoGoiPort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlKiemTraTinAdapter implements KiemTraTinChoGoiPort {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlKiemTraTinAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public Optional<ThongTinTinKiemTra> docThongTinTin(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, chu_id, trang_thai FROM se214.tin_bat_dong_san WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    sql,
                    (rs, rowNum) -> new ThongTinTinKiemTra(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("chu_id")),
                            rs.getString("trang_thai")
                    ),
                    tinId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ThongTinTinKiemTra> khoaVaDocThongTinTin(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, chu_id, trang_thai FROM se214.tin_bat_dong_san WHERE id = ? FOR UPDATE";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    sql,
                    (rs, rowNum) -> new ThongTinTinKiemTra(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("chu_id")),
                            rs.getString("trang_thai")
                    ),
                    tinId
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
