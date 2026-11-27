package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.GiaoDichVi;
import com.realestates.backend.wallet.domain.GiaoDichViRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
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
public class PostgreSqlGiaoDichViRepository implements GiaoDichViRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlGiaoDichViRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource không được null"));
    }

    @Autowired
    public PostgreSqlGiaoDichViRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    private static final RowMapper<GiaoDichVi> ROW_MAPPER = (rs, rowNum) -> {
        String gocIdStr = rs.getString("giao_dich_goc_id");
        UUID gocId = gocIdStr != null ? UUID.fromString(gocIdStr) : null;
        return new GiaoDichVi(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("vi_id")),
                rs.getString("loai"),
                rs.getString("trang_thai"),
                rs.getString("chieu_bien_dong"),
                rs.getBigDecimal("so_tien"),
                rs.getBigDecimal("so_du_truoc"),
                rs.getBigDecimal("so_du_sau"),
                rs.getString("ma_y_dinh"),
                gocId,
                rs.getString("ma_thu_tien_cong"),
                rs.getTimestamp("thoi_diem_ghi_nhan").toInstant(),
                rs.getString("ghi_chu")
        );
    };

    @Override
    public Optional<GiaoDichVi> findByViIdAndLoaiAndMaYDinh(UUID viId, String loai, String maYDinh) {
        if (viId == null || loai == null || maYDinh == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT id, vi_id, loai, trang_thai, chieu_bien_dong, so_tien, so_du_truoc, so_du_sau,
                   ma_y_dinh, giao_dich_goc_id, ma_thu_tien_cong, thoi_diem_ghi_nhan, ghi_chu
            FROM se214.giao_dich_vi
            WHERE vi_id = ? AND loai = ? AND ma_y_dinh = ? AND trang_thai = 'thanh_cong'
            LIMIT 1
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, viId, loai, maYDinh));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<GiaoDichVi> findByViId(UUID viId) {
        if (viId == null) {
            return List.of();
        }
        String sql = """
            SELECT id, vi_id, loai, trang_thai, chieu_bien_dong, so_tien, so_du_truoc, so_du_sau,
                   ma_y_dinh, giao_dich_goc_id, ma_thu_tien_cong, thoi_diem_ghi_nhan, ghi_chu
            FROM se214.giao_dich_vi
            WHERE vi_id = ?
            ORDER BY thoi_diem_ghi_nhan DESC
        """;
        return jdbcTemplate.query(sql, ROW_MAPPER, viId);
    }

    @Override
    public List<GiaoDichVi> findByViIdAndTrangThai(UUID viId, String trangThai) {
        if (viId == null || trangThai == null) {
            return List.of();
        }
        String sql = """
            SELECT id, vi_id, loai, trang_thai, chieu_bien_dong, so_tien, so_du_truoc, so_du_sau,
                   ma_y_dinh, giao_dich_goc_id, ma_thu_tien_cong, thoi_diem_ghi_nhan, ghi_chu
            FROM se214.giao_dich_vi
            WHERE vi_id = ? AND trang_thai = ?
            ORDER BY thoi_diem_ghi_nhan DESC
        """;
        return jdbcTemplate.query(sql, ROW_MAPPER, viId, trangThai);
    }

    @Override
    public GiaoDichVi save(GiaoDichVi giaoDich) {
        Objects.requireNonNull(giaoDich, "giaoDich không được null");
        String sql = """
            INSERT INTO se214.giao_dich_vi (
                id, vi_id, loai, trang_thai, chieu_bien_dong,
                so_tien, so_du_truoc, so_du_sau, ma_y_dinh,
                giao_dich_goc_id, ma_thu_tien_cong, thoi_diem_ghi_nhan, ghi_chu
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(
                sql,
                giaoDich.id(),
                giaoDich.viId(),
                giaoDich.loai(),
                giaoDich.trangThai(),
                giaoDich.chieuBienDong(),
                giaoDich.soTien(),
                giaoDich.soDuTruoc(),
                giaoDich.soDuSau(),
                giaoDich.maYDinh(),
                giaoDich.giaoDichGocId(),
                giaoDich.maThuTienCong(),
                Timestamp.from(giaoDich.thoiDiemGhiNhan()),
                giaoDich.ghiChu()
        );
        return giaoDich;
    }
}
