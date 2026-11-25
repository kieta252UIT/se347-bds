package com.realestates.backend.favorite.infrastructure;

import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import com.realestates.backend.search.domain.TinCongKhai;
import com.realestates.backend.search.infrastructure.PostgreSqlTimKiemRepository;
import java.sql.ResultSet;
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
 * Yêu thích trên PostgreSQL. Lưu = INSERT ... SELECT (chỉ khi tin công khai) ON CONFLICT DO NOTHING nên idempotent và
 * giữ luu_luc ban đầu. Danh sách INNER JOIN điều kiện BR-01 nên tin đã ẩn bị loại khỏi cả trang lẫn tongSo;
 * thứ tự (luu_luc DESC, tin_id ASC).
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlYeuThichRepository implements YeuThichRepository {

    private static final String SQL_LUU = """
            INSERT INTO se214.yeu_thich_tin (tai_khoan_id, tin_id)
            SELECT CAST(:taiKhoanId AS uuid), t.id
            FROM %s
            WHERE t.id = CAST(:tinId AS uuid) AND %s
            ON CONFLICT (tai_khoan_id, tin_id) DO NOTHING
            """.formatted(TinCongKhai.TU_BANG, TinCongKhai.DIEU_KIEN);

    private static final String SQL_CONG_KHAI = """
            SELECT EXISTS (SELECT 1 FROM %s WHERE t.id = CAST(:tinId AS uuid) AND %s)
            """.formatted(TinCongKhai.TU_BANG, TinCongKhai.DIEU_KIEN);

    private static final String SQL_DANH_SACH = """
            WITH hop_le AS MATERIALIZED (
                SELECT y.luu_luc, t.id AS tin_id, p.id AS phien_ban_id, p.tieu_de, p.loai_giao_dich, p.loai_bds,
                       p.gia_goc, p.don_vi_gia, p.dien_tich_m2, p.khu_vuc, p.vi_do, p.kinh_do, t.ngay_dang
                FROM se214.yeu_thich_tin y
                JOIN %s ON t.id = y.tin_id
                WHERE y.tai_khoan_id = CAST(:taiKhoanId AS uuid) AND %s
            ),
            trang AS (
                SELECT * FROM hop_le ORDER BY luu_luc DESC, tin_id ASC LIMIT :gioiHan OFFSET :boQua
            )
            SELECT (SELECT count(*) FROM hop_le) AS tong_so, k.*, a.id AS anh_id, a.thu_tu AS anh_thu_tu
            FROM (SELECT 1) m
            LEFT JOIN trang k ON true
            LEFT JOIN LATERAL (
                SELECT ai.id, ai.thu_tu FROM se214.anh_tin ai
                WHERE ai.phien_ban_tin_id = k.phien_ban_id AND ai.trang_thai = 'da_gan'
                ORDER BY ai.thu_tu ASC LIMIT 1
            ) a ON true
            ORDER BY k.luu_luc DESC, k.tin_id ASC
            """.formatted(TinCongKhai.TU_BANG, TinCongKhai.DIEU_KIEN);

    private final NamedParameterJdbcTemplate jdbc;

    public PostgreSqlYeuThichRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null"));
    }

    @Override
    public boolean luu(UUID taiKhoanId, UUID tinId) {
        MapSqlParameterSource tham = new MapSqlParameterSource()
                .addValue("taiKhoanId", taiKhoanId.toString())
                .addValue("tinId", tinId.toString());
        if (jdbc.update(SQL_LUU, tham) > 0) {
            return true;
        }
        // 0 dòng: đã lưu từ trước (vẫn công khai) hoặc tin không công khai.
        return Boolean.TRUE.equals(jdbc.queryForObject(SQL_CONG_KHAI, tham, Boolean.class));
    }

    @Override
    public void bo(UUID taiKhoanId, UUID tinId) {
        jdbc.update(
                "DELETE FROM se214.yeu_thich_tin WHERE tai_khoan_id = CAST(:taiKhoanId AS uuid) AND tin_id = CAST(:tinId AS uuid)",
                new MapSqlParameterSource()
                        .addValue("taiKhoanId", taiKhoanId.toString())
                        .addValue("tinId", tinId.toString())
        );
    }

    @Override
    public TrangYeuThich docDanhSach(UUID taiKhoanId, long boQua, int gioiHan) {
        MapSqlParameterSource tham = new MapSqlParameterSource()
                .addValue("taiKhoanId", taiKhoanId.toString())
                .addValue("boQua", boQua)
                .addValue("gioiHan", gioiHan);
        List<DongTin> noiDung = new ArrayList<>();
        long[] tongSo = {0};
        jdbc.query(SQL_DANH_SACH, tham, (ResultSet rs) -> {
            tongSo[0] = rs.getLong("tong_so");
            if (rs.getObject("tin_id") != null) {
                noiDung.add(PostgreSqlTimKiemRepository.anhXa(rs));
            }
        });
        return new TrangYeuThich(List.copyOf(noiDung), tongSo[0]);
    }
}
