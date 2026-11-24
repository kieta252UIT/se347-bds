package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.TieuChiGoiY;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlKiemTraTinChoGoiYAdapter implements KiemTraTinChoGoiYPort {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ThongTinTinGoiY> ROW_MAPPER = (rs, rowNum) -> {
        BigDecimal viDo = rs.getBigDecimal("vi_do");
        BigDecimal kinhDo = rs.getBigDecimal("kinh_do");
        ViTriToaDo toaDo = (viDo != null && kinhDo != null) ? new ViTriToaDo(viDo, kinhDo) : null;
        UUID anhId = (UUID) rs.getObject("anh_dai_dien_id");

        return new ThongTinTinGoiY(
                (UUID) rs.getObject("tin_id"),
                (UUID) rs.getObject("chu_id"),
                rs.getString("tieu_de"),
                rs.getString("mo_ta"),
                rs.getString("loai_giao_dich"),
                rs.getString("loai_bds"),
                rs.getBigDecimal("gia_goc"),
                rs.getString("don_vi_gia"),
                rs.getBigDecimal("gia_tong_chuan"),
                rs.getBigDecimal("gia_m2_chuan"),
                rs.getBigDecimal("dien_tich_m2"),
                rs.getString("dia_chi"),
                rs.getString("khu_vuc"),
                toaDo,
                rs.getTimestamp("ngay_dang").toInstant(),
                anhId,
                rs.getString("trang_thai_tin"),
                rs.getString("trang_thai_chu"),
                rs.getBoolean("co_goi_tai_tro")
        );
    };

    public PostgreSqlKiemTraTinChoGoiYAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public List<ThongTinTinGoiY> layDanhSachTinCongKhaiHopLe(TieuChiGoiY tieuChi, Instant now) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id AS tin_id,
                       t.chu_id,
                       pb.tieu_de,
                       pb.mo_ta,
                       pb.loai_giao_dich,
                       pb.loai_bds,
                       pb.gia_goc,
                       pb.don_vi_gia,
                       pb.gia_tong_chuan,
                       pb.gia_m2_chuan,
                       pb.dien_tich AS dien_tich_m2,
                       pb.dia_chi,
                       pb.khu_vuc,
                       pb.vi_do,
                       pb.kinh_do,
                       t.ngay_dang,
                       (SELECT a.id FROM se214.anh_tin a WHERE a.phien_ban_tin_id = pb.id ORDER BY a.thu_tu ASC LIMIT 1) AS anh_dai_dien_id,
                       t.trang_thai AS trang_thai_tin,
                       tk.trang_thai AS trang_thai_chu,
                       EXISTS (
                           SELECT 1 FROM se214.goi_day_tin g
                           WHERE g.tin_id = t.id
                             AND g.trang_thai = 'dang_hoat_dong'
                             AND g.het_han_luc > ?
                       ) AS co_goi_tai_tro
                FROM se214.tin_bat_dong_san t
                JOIN se214.phien_ban_tin pb ON pb.id = t.phien_ban_da_duyet_id
                JOIN se214.tai_khoan tk ON tk.id = t.chu_id
                WHERE t.trang_thai = 'da_duyet' AND tk.trang_thai = 'hoat_dong'
                """);

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.from(now));

        buildFilterSql(sql, params, tieuChi);

        sql.append(" ORDER BY t.ngay_dang DESC LIMIT 200");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public List<ThongTinTinGoiY> layDanhSachTinTaiTroHopLe(TieuChiGoiY tieuChi, Instant now) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id AS tin_id,
                       t.chu_id,
                       pb.tieu_de,
                       pb.mo_ta,
                       pb.loai_giao_dich,
                       pb.loai_bds,
                       pb.gia_goc,
                       pb.don_vi_gia,
                       pb.gia_tong_chuan,
                       pb.gia_m2_chuan,
                       pb.dien_tich AS dien_tich_m2,
                       pb.dia_chi,
                       pb.khu_vuc,
                       pb.vi_do,
                       pb.kinh_do,
                       t.ngay_dang,
                       (SELECT a.id FROM se214.anh_tin a WHERE a.phien_ban_tin_id = pb.id ORDER BY a.thu_tu ASC LIMIT 1) AS anh_dai_dien_id,
                       t.trang_thai AS trang_thai_tin,
                       tk.trang_thai AS trang_thai_chu,
                       true AS co_goi_tai_tro
                FROM se214.tin_bat_dong_san t
                JOIN se214.phien_ban_tin pb ON pb.id = t.phien_ban_da_duyet_id
                JOIN se214.tai_khoan tk ON tk.id = t.chu_id
                JOIN se214.goi_day_tin g ON g.tin_id = t.id
                WHERE t.trang_thai = 'da_duyet' AND tk.trang_thai = 'hoat_dong'
                  AND g.trang_thai = 'dang_hoat_dong' AND g.het_han_luc > ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.from(now));

        buildFilterSql(sql, params, tieuChi);

        sql.append(" LIMIT 50");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    private void buildFilterSql(StringBuilder sql, List<Object> params, TieuChiGoiY tieuChi) {
        if (tieuChi == null) {
            return;
        }

        if (tieuChi.coLocLoaiBds()) {
            sql.append(" AND pb.loai_bds = ?");
            params.add(tieuChi.loaiBds());
        }

        if (tieuChi.coLocLoaiGiaoDich()) {
            sql.append(" AND pb.loai_giao_dich = ?");
            params.add(tieuChi.loaiGiaoDich());
        }

        if (tieuChi.coLocKhuVuc()) {
            sql.append(" AND pb.khu_vuc = ?");
            params.add(tieuChi.khuVuc());
        }

        if (tieuChi.giaMinVnd() != null && tieuChi.giaMinVnd().compareTo(BigDecimal.ZERO) > 0) {
            sql.append(" AND pb.gia_tong_chuan >= ?");
            params.add(tieuChi.giaMinVnd());
        }

        if (tieuChi.giaMaxVnd() != null && tieuChi.giaMaxVnd().compareTo(BigDecimal.ZERO) > 0) {
            sql.append(" AND pb.gia_tong_chuan <= ?");
            params.add(tieuChi.giaMaxVnd());
        }
    }
}
