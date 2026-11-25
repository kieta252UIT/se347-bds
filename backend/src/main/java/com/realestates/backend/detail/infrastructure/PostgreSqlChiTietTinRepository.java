package com.realestates.backend.detail.infrastructure;

import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.search.domain.TinCongKhai;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Chi tiết + liên hệ trên PostgreSQL trong MỘT câu lệnh (LEFT JOIN ảnh) nên nội dung, ảnh và liên hệ luôn cùng một
 * phiên bản và cùng điều kiện BR-01 (TinCongKhai).
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlChiTietTinRepository implements ChiTietTinRepository {

    private static final String SQL = """
            SELECT t.id AS tin_id, t.ngay_dang, p.id AS phien_ban_id, p.so_phien_ban, p.tieu_de, p.mo_ta,
                   p.loai_giao_dich, p.loai_bds, p.gia_goc, p.don_vi_gia, p.dien_tich_m2, p.dia_chi, p.khu_vuc,
                   p.vi_do, p.kinh_do, p.tao_luc, chu.ho_ten, chu.so_dien_thoai,
                   a.id AS anh_id, a.thu_tu AS anh_thu_tu
            FROM %s
            LEFT JOIN se214.anh_tin a ON a.phien_ban_tin_id = p.id AND a.trang_thai = 'da_gan'
            WHERE t.id = CAST(:tinId AS uuid)
              AND %s
            ORDER BY a.thu_tu ASC
            """.formatted(TinCongKhai.TU_BANG, TinCongKhai.DIEU_KIEN);

    private final NamedParameterJdbcTemplate jdbc;

    public PostgreSqlChiTietTinRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null"));
    }

    @Override
    public Optional<DongChiTiet> docChiTiet(UUID tinId) {
        return jdbc.query(SQL, new MapSqlParameterSource("tinId", tinId.toString()), PostgreSqlChiTietTinRepository::gom);
    }

    private static Optional<DongChiTiet> gom(ResultSet rs) throws SQLException {
        DongChiTiet dong = null;
        List<AnhChiTiet> anh = new ArrayList<>();
        while (rs.next()) {
            if (dong == null) {
                dong = new DongChiTiet(
                        rs.getObject("tin_id", UUID.class),
                        rs.getTimestamp("ngay_dang").toInstant(),
                        rs.getObject("phien_ban_id", UUID.class),
                        rs.getInt("so_phien_ban"),
                        rs.getString("tieu_de"),
                        rs.getString("mo_ta"),
                        rs.getString("loai_giao_dich"),
                        rs.getString("loai_bds"),
                        rs.getBigDecimal("gia_goc"),
                        rs.getString("don_vi_gia"),
                        rs.getBigDecimal("dien_tich_m2"),
                        rs.getString("dia_chi"),
                        rs.getString("khu_vuc"),
                        rs.getBigDecimal("vi_do"),
                        rs.getBigDecimal("kinh_do"),
                        rs.getTimestamp("tao_luc").toInstant(),
                        rs.getString("ho_ten"),
                        rs.getString("so_dien_thoai"),
                        anh
                );
            }
            UUID anhId = rs.getObject("anh_id", UUID.class);
            if (anhId != null) {
                anh.add(new AnhChiTiet(anhId, rs.getInt("anh_thu_tu")));
            }
        }
        return Optional.ofNullable(dong);
    }
}
