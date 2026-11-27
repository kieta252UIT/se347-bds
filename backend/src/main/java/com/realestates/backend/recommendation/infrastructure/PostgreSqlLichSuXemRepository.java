package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.LichSuXemRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlLichSuXemRepository implements LichSuXemRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LichSuXem> ROW_MAPPER = (rs, rowNum) -> new LichSuXem(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("tai_khoan_id"),
            (UUID) rs.getObject("tin_id"),
            rs.getTimestamp("thoi_diem_xem").toInstant()
    );

    @Autowired
    public PostgreSqlLichSuXemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public void save(LichSuXem lichSu) {
        if (lichSu == null) {
            return;
        }
        String sql = """
                INSERT INTO se214.lich_su_xem (id, tai_khoan_id, tin_id, thoi_diem_xem)
                VALUES (?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                lichSu.id(),
                lichSu.taiKhoanId(),
                lichSu.tinId(),
                Timestamp.from(lichSu.thoiDiemXem())
        );
    }

    @Override
    public List<LichSuXem> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return List.of();
        }
        String sql = """
                SELECT id, tai_khoan_id, tin_id, thoi_diem_xem
                FROM se214.lich_su_xem
                WHERE tai_khoan_id = ?
                ORDER BY thoi_diem_xem DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, taiKhoanId);
    }

    @Override
    public List<UUID> findTaiKhoanCoTuongTacTrongKhoang(Instant tuThoiDiem, Instant denThoiDiem) {
        if (tuThoiDiem == null || denThoiDiem == null) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT tai_khoan_id
                FROM se214.lich_su_xem
                WHERE thoi_diem_xem >= ? AND thoi_diem_xem <= ?
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> (UUID) rs.getObject("tai_khoan_id"),
                Timestamp.from(tuThoiDiem),
                Timestamp.from(denThoiDiem)
        );
    }
}
