package com.realestates.backend.search.infrastructure;

import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.ChuanHoaTimKiem;
import com.realestates.backend.search.domain.QuyDoiGiaTimKiem;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import com.realestates.backend.search.domain.TinCongKhai;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tìm kiếm công khai trong MỘT câu SQL (TK-09): một mốc statement_timestamp() dùng chung cho tập hợp lệ,
 * tổng số tự nhiên, trang tự nhiên và mẫu tài trợ, nên một tin không thể nằm ở cả hai khu.
 * Mọi giá trị người dùng đi qua tham số có tên; các đoạn SQL nối chuỗi đều là hằng chọn từ enum.
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlTimKiemRepository implements TimKiemRepository {

    private static final String VECTOR_TIM_KIEM =
            "setweight(to_tsvector('simple', " + ChuanHoaTimKiem.sqlKhuVuc("p.tieu_de") + "), 'A')"
                    + " || setweight(to_tsvector('simple', "
                    + ChuanHoaTimKiem.sqlKhuVuc("p.khu_vuc || ' ' || p.dia_chi") + "), 'B')"
                    + " || setweight(to_tsvector('simple', " + ChuanHoaTimKiem.sqlKhuVuc("p.mo_ta") + "), 'C')";

    private static final String COT_TIN = """
            tin_id, phien_ban_id, tieu_de, loai_giao_dich, loai_bds, gia_goc, don_vi_gia, dien_tich_m2,
            khu_vuc, vi_do, kinh_do, ngay_dang""";

    private final NamedParameterJdbcTemplate jdbc;

    public PostgreSqlTimKiemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null"));
    }

    @Override
    public KetQua timKiem(BoLocTimKiem boLoc, int soTaiTroToiDa) {
        MapSqlParameterSource tham = new MapSqlParameterSource();
        String sql = xayDungSql(boLoc, tham);
        tham.addValue("kichThuoc", boLoc.kichThuoc());
        tham.addValue("boQua", (long) boLoc.trang() * boLoc.kichThuoc());
        tham.addValue("soTaiTro", soTaiTroToiDa);

        List<DongTin> tuNhien = new ArrayList<>();
        List<DongTin> taiTro = new ArrayList<>();
        long[] tongSo = {0};
        Instant[] thoiDiem = {null};
        jdbc.query(sql, tham, (ResultSet rs) -> {
            tongSo[0] = rs.getLong("tong_so");
            thoiDiem[0] = rs.getTimestamp("thoi_diem").toInstant();
            String khu = rs.getString("khu");
            if ("tu_nhien".equals(khu)) {
                tuNhien.add(anhXa(rs));
            } else if ("tai_tro".equals(khu)) {
                taiTro.add(anhXa(rs));
            }
        });
        return new KetQua(List.copyOf(tuNhien), tongSo[0], List.copyOf(taiTro), thoiDiem[0]);
    }

    static String xayDungSql(BoLocTimKiem boLoc, MapSqlParameterSource tham) {
        StringBuilder loc = new StringBuilder();
        String hang = "CAST(0 AS real)";
        String tuKhoaJoin = "";
        if (boLoc.coTuKhoa()) {
            tuKhoaJoin = "\n    CROSS JOIN LATERAL (SELECT " + VECTOR_TIM_KIEM + " AS vb) v"
                    + "\n    CROSS JOIN LATERAL (SELECT plainto_tsquery('simple', :tuKhoa) AS q) tq";
            loc.append("\n      AND v.vb @@ tq.q");
            hang = "ts_rank(v.vb, tq.q)";
            tham.addValue("tuKhoa", String.join(" ", boLoc.tuKhoa()));
        }
        if (boLoc.loaiGiaoDich() != null) {
            loc.append("\n      AND p.loai_giao_dich = :loaiGiaoDich");
            tham.addValue("loaiGiaoDich", boLoc.loaiGiaoDich().getGiaTri());
        }
        if (boLoc.loaiBds() != null) {
            loc.append("\n      AND p.loai_bds = :loaiBds");
            tham.addValue("loaiBds", boLoc.loaiBds().getGiaTri());
        }
        if (boLoc.khuVuc() != null) {
            loc.append("\n      AND strpos(").append(ChuanHoaTimKiem.sqlKhuVuc("p.khu_vuc || ' ' || p.dia_chi"))
                    .append(", :khuVuc) > 0");
            tham.addValue("khuVuc", boLoc.khuVuc());
        }
        // TK-06: nhân chéo trên phân số chính xác N/D, không làm tròn trước khi so sánh.
        String tu = QuyDoiGiaTimKiem.sqlTu("p");
        String mau = QuyDoiGiaTimKiem.sqlMau("p", boLoc.cheDoGia());
        if (boLoc.giaMin() != null) {
            loc.append("\n      AND ").append(tu).append(" >= CAST(:giaMin AS numeric) * ").append(mau);
            tham.addValue("giaMin", boLoc.giaMin());
        }
        if (boLoc.giaMax() != null) {
            loc.append("\n      AND ").append(tu).append(" <= CAST(:giaMax AS numeric) * ").append(mau);
            tham.addValue("giaMax", boLoc.giaMax());
        }
        if (boLoc.dienTichMin() != null) {
            loc.append("\n      AND p.dien_tich_m2 >= CAST(:dienTichMin AS numeric)");
            tham.addValue("dienTichMin", boLoc.dienTichMin());
        }
        if (boLoc.dienTichMax() != null) {
            loc.append("\n      AND p.dien_tich_m2 <= CAST(:dienTichMax AS numeric)");
            tham.addValue("dienTichMax", boLoc.dienTichMax());
        }
        if (boLoc.coVung()) {
            loc.append("\n      AND p.vi_do BETWEEN CAST(:nam AS numeric) AND CAST(:bac AS numeric)")
                    .append("\n      AND p.kinh_do BETWEEN CAST(:tay AS numeric) AND CAST(:dong AS numeric)");
            tham.addValue("nam", boLoc.nam());
            tham.addValue("bac", boLoc.bac());
            tham.addValue("tay", boLoc.tay());
            tham.addValue("dong", boLoc.dong());
        }
        String thuTu = boLoc.sapXep() == SapXepTin.MAC_DINH && boLoc.coTuKhoa()
                ? "hang DESC, ngay_dang DESC, tin_id ASC"
                : "ngay_dang DESC, tin_id ASC";

        return """
                WITH moc AS MATERIALIZED (SELECT statement_timestamp() AS ts),
                hop_le AS MATERIALIZED (
                    SELECT t.id AS tin_id, p.id AS phien_ban_id, p.tieu_de, p.loai_giao_dich, p.loai_bds, p.gia_goc,
                           p.don_vi_gia, p.dien_tich_m2, p.khu_vuc, p.vi_do, p.kinh_do, t.ngay_dang,
                           %s AS hang,
                           EXISTS (SELECT 1 FROM se214.goi_day_tin g
                                   WHERE g.tin_id = t.id AND g.trang_thai = 'dang_hoat_dong'
                                     AND g.het_han_luc > moc.ts) AS co_tai_tro
                    FROM %s
                    CROSS JOIN moc%s
                    WHERE %s%s
                ),
                tu_nhien AS (SELECT * FROM hop_le WHERE NOT co_tai_tro),
                trang AS (
                    SELECT 'tu_nhien' AS khu, row_number() OVER (ORDER BY %s) AS thu_tu, %s
                    FROM tu_nhien
                    ORDER BY %s
                    LIMIT :kichThuoc OFFSET :boQua
                ),
                tai_tro AS (
                    SELECT 'tai_tro' AS khu, CAST(0 AS bigint) AS thu_tu, %s
                    FROM hop_le
                    WHERE co_tai_tro
                    ORDER BY random(), tin_id
                    LIMIT :soTaiTro
                ),
                ket_qua AS (SELECT * FROM trang UNION ALL SELECT * FROM tai_tro),
                meta AS (SELECT (SELECT count(*) FROM tu_nhien) AS tong_so, ts AS thoi_diem FROM moc)
                SELECT m.tong_so, m.thoi_diem, k.*, a.id AS anh_id, a.thu_tu AS anh_thu_tu
                FROM meta m
                LEFT JOIN ket_qua k ON true
                LEFT JOIN LATERAL (
                    SELECT ai.id, ai.thu_tu FROM se214.anh_tin ai
                    WHERE ai.phien_ban_tin_id = k.phien_ban_id AND ai.trang_thai = 'da_gan'
                    ORDER BY ai.thu_tu ASC LIMIT 1
                ) a ON true
                ORDER BY k.khu, k.thu_tu
                """.formatted(hang, TinCongKhai.TU_BANG, tuKhoaJoin, TinCongKhai.DIEU_KIEN, loc, thuTu, COT_TIN, thuTu, COT_TIN);
    }

    /** Ánh xạ cột tóm tắt tin (dùng chung với yêu thích). */
    public static DongTin anhXa(ResultSet rs) throws SQLException {
        return new DongTin(
                rs.getObject("tin_id", UUID.class),
                rs.getObject("phien_ban_id", UUID.class),
                rs.getString("tieu_de"),
                rs.getString("loai_giao_dich"),
                rs.getString("loai_bds"),
                rs.getBigDecimal("gia_goc"),
                rs.getString("don_vi_gia"),
                rs.getBigDecimal("dien_tich_m2"),
                rs.getString("khu_vuc"),
                rs.getBigDecimal("vi_do"),
                rs.getBigDecimal("kinh_do"),
                rs.getTimestamp("ngay_dang").toInstant(),
                rs.getObject("anh_id", UUID.class),
                rs.getObject("anh_thu_tu", Integer.class)
        );
    }
}
