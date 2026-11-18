package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import java.sql.Timestamp;
import java.time.Instant;
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
public class PostgreSqlAnhTinRepository implements AnhTinRepository {

    private static final String SQL_LUU_ANH_TAM = """
            INSERT INTO se214.anh_tin (id, chu_id, khoa_tep_goc, khoa_anh_hien_thi, mime_type, kich_thuoc_byte, trang_thai)
            VALUES (?, ?, ?, ?, ?, ?, 'tam')
            """;

    private static final String SQL_TIM_ANH_DE_DOC = """
            SELECT a.id,
                   a.chu_id,
                   a.trang_thai AS trang_thai_anh,
                   a.khoa_tep_goc,
                   a.khoa_anh_hien_thi,
                   a.mime_type,
                   a.phien_ban_tin_id,
                   t.trang_thai AS trang_thai_tin,
                   t.phien_ban_hien_tai_id,
                   t.phien_ban_da_duyet_id,
                   (c.trang_thai = 'hoat_dong') AS chu_tin_hoat_dong
            FROM se214.anh_tin a
            LEFT JOIN se214.phien_ban_tin pb ON pb.id = a.phien_ban_tin_id
            LEFT JOIN se214.tin_bat_dong_san t ON t.id = pb.tin_id
            LEFT JOIN se214.tai_khoan c ON c.id = t.chu_id
            WHERE a.id = ?
            """;

    private static final String SQL_CO_THAM_CHIEU_KHOA = """
            SELECT EXISTS (
                SELECT 1 FROM se214.anh_tin
                WHERE khoa_tep_goc = ? OR khoa_anh_hien_thi = ?
            )
            """;

    private static final String SQL_CO_THAM_CHIEU_KHOA_NGOAI_ANH = """
            SELECT EXISTS (
                SELECT 1 FROM se214.anh_tin
                WHERE id <> ? AND (khoa_tep_goc = ? OR khoa_anh_hien_thi = ?)
            )
            """;

    private static final String SQL_DANH_DAU_ANH_TAM_QUA_HAN = """
            UPDATE se214.anh_tin
            SET trang_thai = 'cho_don'
            WHERE trang_thai = 'tam'
              AND phien_ban_tin_id IS NULL
              AND thu_tu IS NULL
              AND tao_luc <= ?
            """;

    private static final String SQL_LAY_ANH_CHO_DON = """
            SELECT id, khoa_tep_goc, khoa_anh_hien_thi
            FROM se214.anh_tin
            WHERE trang_thai = 'cho_don'
              AND phien_ban_tin_id IS NULL
            ORDER BY tao_luc ASC, id ASC
            LIMIT ?
            """;

    private static final String SQL_XOA_METADATA_ANH_CHO_DON = """
            DELETE FROM se214.anh_tin
            WHERE id = ?
              AND trang_thai = 'cho_don'
              AND phien_ban_tin_id IS NULL
            """;

    private static final RowMapper<AnhTinTruyCap> ANH_ROW_MAPPER = (rs, rowNum) -> new AnhTinTruyCap(
            rs.getObject("id", UUID.class),
            rs.getObject("chu_id", UUID.class),
            rs.getString("trang_thai_anh"),
            rs.getString("khoa_tep_goc"),
            rs.getString("khoa_anh_hien_thi"),
            rs.getString("mime_type"),
            rs.getObject("phien_ban_tin_id", UUID.class),
            rs.getString("trang_thai_tin"),
            rs.getObject("phien_ban_hien_tai_id", UUID.class),
            rs.getObject("phien_ban_da_duyet_id", UUID.class),
            rs.getBoolean("chu_tin_hoat_dong")
    );

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlAnhTinRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public void luuAnhTam(
            UUID id,
            UUID chuId,
            String khoaTepGoc,
            String khoaAnhHienThi,
            DinhDangAnh dinhDang,
            long kichThuocByte
    ) {
        jdbcTemplate.update(SQL_LUU_ANH_TAM, id, chuId, khoaTepGoc, khoaAnhHienThi, dinhDang.getMimeType(), kichThuocByte);
    }

    @Override
    public Optional<AnhTinTruyCap> timAnhDeDoc(UUID anhId) {
        return jdbcTemplate.query(SQL_TIM_ANH_DE_DOC, ANH_ROW_MAPPER, anhId).stream().findFirst();
    }

    @Override
    public boolean coThamChieuKhoa(String khoaDoiTuong) {
        if (khoaDoiTuong == null || khoaDoiTuong.isBlank()) {
            return false;
        }
        Boolean co = jdbcTemplate.queryForObject(SQL_CO_THAM_CHIEU_KHOA, Boolean.class, khoaDoiTuong, khoaDoiTuong);
        return Boolean.TRUE.equals(co);
    }

    @Override
    public boolean coThamChieuKhoaNgoaiAnh(UUID anhId, String khoaDoiTuong) {
        if (anhId == null || khoaDoiTuong == null || khoaDoiTuong.isBlank()) {
            return false;
        }
        Boolean co = jdbcTemplate.queryForObject(
                SQL_CO_THAM_CHIEU_KHOA_NGOAI_ANH,
                Boolean.class,
                anhId,
                khoaDoiTuong,
                khoaDoiTuong
        );
        return Boolean.TRUE.equals(co);
    }

    @Override
    public int danhDauAnhTamQuaHanChoDon(Instant mocHetHan) {
        if (mocHetHan == null) {
            return 0;
        }
        return jdbcTemplate.update(SQL_DANH_DAU_ANH_TAM_QUA_HAN, Timestamp.from(mocHetHan));
    }

    @Override
    public List<AnhChoDon> layDanhSachAnhChoDon(int gioiHan) {
        if (gioiHan <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                SQL_LAY_ANH_CHO_DON,
                (rs, rowNum) -> new AnhChoDon(
                        rs.getObject("id", UUID.class),
                        rs.getString("khoa_tep_goc"),
                        rs.getString("khoa_anh_hien_thi")
                ),
                gioiHan
        );
    }

    @Override
    public boolean xoaMetadataAnhChoDon(UUID anhId) {
        if (anhId == null) {
            return false;
        }
        return jdbcTemplate.update(SQL_XOA_METADATA_ANH_CHO_DON, anhId) > 0;
    }
}
