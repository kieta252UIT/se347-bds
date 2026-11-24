package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.DiemGoiYRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlDiemGoiYRepository implements DiemGoiYRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<DiemGoiY> ROW_MAPPER = (rs, rowNum) -> new DiemGoiY(
            (UUID) rs.getObject("tai_khoan_id"),
            (UUID) rs.getObject("tin_id"),
            rs.getBigDecimal("diem"),
            rs.getTimestamp("cap_nhat_luc").toInstant()
    );

    public PostgreSqlDiemGoiYRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public List<DiemGoiY> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return List.of();
        }
        String sql = """
                SELECT tai_khoan_id, tin_id, diem, cap_nhat_luc
                FROM se214.diem_goi_y
                WHERE tai_khoan_id = ?
                ORDER BY diem DESC
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, taiKhoanId);
    }

    @Override
    public Optional<DiemGoiY> findByTaiKhoanIdAndTinId(UUID taiKhoanId, UUID tinId) {
        if (taiKhoanId == null || tinId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT tai_khoan_id, tin_id, diem, cap_nhat_luc
                FROM se214.diem_goi_y
                WHERE tai_khoan_id = ? AND tin_id = ?
                """;
        List<DiemGoiY> list = jdbcTemplate.query(sql, ROW_MAPPER, taiKhoanId, tinId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public void save(DiemGoiY diemGoiY) {
        if (diemGoiY == null) {
            return;
        }
        String sql = """
                INSERT INTO se214.diem_goi_y (tai_khoan_id, tin_id, diem, cap_nhat_luc)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tai_khoan_id, tin_id)
                DO UPDATE SET diem = EXCLUDED.diem, cap_nhat_luc = EXCLUDED.cap_nhat_luc
                """;
        jdbcTemplate.update(
                sql,
                diemGoiY.taiKhoanId(),
                diemGoiY.tinId(),
                diemGoiY.diem(),
                Timestamp.from(diemGoiY.capNhatLuc())
        );
    }

    @Override
    public void saveAll(List<DiemGoiY> danhSachDiem) {
        if (danhSachDiem == null || danhSachDiem.isEmpty()) {
            return;
        }
        for (DiemGoiY diem : danhSachDiem) {
            save(diem);
        }
    }

    @Override
    public void deleteByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return;
        }
        String sql = "DELETE FROM se214.diem_goi_y WHERE tai_khoan_id = ?";
        jdbcTemplate.update(sql, taiKhoanId);
    }
}
