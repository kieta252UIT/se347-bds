package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlTinDangRepository implements TinDangRepository {

    private record AnhKhoaRow(UUID id, UUID chuId, String trangThai, String khoaAnhHienThi) {
    }

    private static final String SQL_TIM_YEU_CAU_GHI = """
            SELECT du_lieu_bam,
                   tin_ket_qua_id,
                   phien_ban_ket_qua_id,
                   trang_thai_ket_qua,
                   phien_ban_cap_nhat_ket_qua
            FROM se214.yeu_cau_ghi
            WHERE tai_khoan_id = ?
              AND loai_thao_tac = ?
              AND ma_y_dinh = ?
            """;

    private static final String SQL_ENSURE_HAN_MUC_THANG = """
            INSERT INTO se214.han_muc_thang (tai_khoan_id, thang, so_tin_moi)
            VALUES (?, ?, 0)
            ON CONFLICT (tai_khoan_id, thang) DO NOTHING
            """;

    private static final String SQL_KHOA_VA_DOC_HAN_MUC_THANG = """
            SELECT so_tin_moi
            FROM se214.han_muc_thang
            WHERE tai_khoan_id = ?
              AND thang = ?
            FOR UPDATE
            """;

    private static final String SQL_TANG_HAN_MUC_THANG = """
            UPDATE se214.han_muc_thang
            SET so_tin_moi = so_tin_moi + 1
            WHERE tai_khoan_id = ?
              AND thang = ?
              AND so_tin_moi < ?
            """;

    private static final String SQL_DOC_HAN_MUC_THANG = """
            SELECT so_tin_moi
            FROM se214.han_muc_thang
            WHERE tai_khoan_id = ?
              AND thang = ?
            """;

    private static final String SQL_INSERT_TIN = """
            INSERT INTO se214.tin_bat_dong_san (
                id, chu_id, trang_thai, ngay_dang, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat
            ) VALUES (?, ?, ?, ?, ?, NULL, 0)
            """;

    private static final String SQL_INSERT_PHIEN_BAN = """
            INSERT INTO se214.phien_ban_tin (
                id, tin_id, so_phien_ban, tieu_de, mo_ta, loai_giao_dich, loai_bds,
                gia_goc, don_vi_gia, dien_tich_m2, dia_chi, khu_vuc, vi_do, kinh_do,
                ket_qua_so_bo, ket_qua_kiem_duyet, tao_luc
            ) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_GAN_ANH = """
            UPDATE se214.anh_tin
            SET phien_ban_tin_id = ?,
                thu_tu = ?,
                trang_thai = 'da_gan'
            WHERE id = ?
              AND chu_id = ?
              AND trang_thai = 'tam'
              AND phien_ban_tin_id IS NULL
              AND thu_tu IS NULL
              AND khoa_anh_hien_thi IS NOT NULL
            """;

    private static final String SQL_INSERT_YEU_CAU_GHI = """
            INSERT INTO se214.yeu_cau_ghi (
                id, tai_khoan_id, loai_thao_tac, ma_y_dinh, du_lieu_bam,
                tin_ket_qua_id, phien_ban_ket_qua_id, trang_thai_ket_qua, phien_ban_cap_nhat_ket_qua, tao_luc
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_INSERT_AUDIT = """
            INSERT INTO se214.nhat_ky_kiem_toan (
                id, nguoi_thuc_hien_id, tin_id, phien_ban_tin_id, hanh_dong, du_lieu_truoc_sau, ly_do, thoi_diem
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), NULL, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlTinDangRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public Optional<KetQuaYeuCauGhi> timYeuCauGhi(UUID taiKhoanId, String loaiThaoTac, String maYDinh) {
        if (taiKhoanId == null || loaiThaoTac == null || maYDinh == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                SQL_TIM_YEU_CAU_GHI,
                (rs, rowNum) -> new KetQuaYeuCauGhi(
                        rs.getString("du_lieu_bam"),
                        new TinGhiKetQua(
                                rs.getObject("tin_ket_qua_id", UUID.class),
                                rs.getObject("phien_ban_ket_qua_id", UUID.class),
                                rs.getString("trang_thai_ket_qua"),
                                rs.getLong("phien_ban_cap_nhat_ket_qua")
                        )
                ),
                taiKhoanId,
                loaiThaoTac,
                maYDinh
        ).stream().findFirst();
    }

    @Override
    public TinGhiKetQua taoTinMoiTrongTransaction(DuLieuTaoTin duLieu) {
        Optional<KetQuaYeuCauGhi> daLuu = timYeuCauGhi(
                duLieu.nguoiDangId(),
                LOAI_THAO_TAC_DANG_TIN,
                duLieu.maYDinh()
        );
        if (daLuu.isPresent()) {
            if (!daLuu.get().duLieuBam().equals(duLieu.duLieuBam())) {
                throw new LoiNghiepVuTaoTinException(
                        LoaiLoiTaoTin.XUNG_DOT_MA_Y_DINH,
                        "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
                );
            }
            return daLuu.get().ketQua();
        }

        Date sqlThang = Date.valueOf(duLieu.thangVietNam());
        jdbcTemplate.update(SQL_ENSURE_HAN_MUC_THANG, duLieu.nguoiDangId(), sqlThang);
        Integer soTinHienTai = jdbcTemplate.queryForObject(
                SQL_KHOA_VA_DOC_HAN_MUC_THANG,
                Integer.class,
                duLieu.nguoiDangId(),
                sqlThang
        );
        if (soTinHienTai == null || soTinHienTai >= HanMucThangHelper.GIOI_HAN_TIN_MOI_MOI_THANG) {
            throw new LoiNghiepVuTaoTinException(
                    LoaiLoiTaoTin.VUOT_HAN_MUC_THANG,
                    "Tài khoản đã đạt hạn mức tối đa 3 tin mới trong tháng."
            );
        }

        khoaVaKiemTraAnhTam(duLieu.nguoiDangId(), duLieu.anhIds());

        UUID tinId = UUID.randomUUID();
        UUID phienBanId = UUID.randomUUID();
        Timestamp thoiDiemSql = Timestamp.from(duLieu.thoiDiem());

        jdbcTemplate.update(
                SQL_INSERT_TIN,
                tinId,
                duLieu.nguoiDangId(),
                TRANG_THAI_CHO_KIEM_DUYET,
                thoiDiemSql,
                phienBanId
        );

        var nd = duLieu.noiDung();
        jdbcTemplate.update(
                SQL_INSERT_PHIEN_BAN,
                phienBanId,
                tinId,
                nd.tieuDe(),
                nd.moTa(),
                nd.loaiGiaoDich().getGiaTri(),
                nd.loaiBds().getGiaTri(),
                nd.giaGoc(),
                nd.donViGia().getGiaTri(),
                nd.dienTichM2(),
                nd.diaChi(),
                nd.khuVuc(),
                new BigDecimal(nd.viDo()),
                new BigDecimal(nd.kinhDo()),
                duLieu.ketQuaSoBo(),
                TRANG_THAI_CHO_KIEM_DUYET,
                thoiDiemSql
        );

        for (int i = 0; i < duLieu.anhIds().size(); i++) {
            UUID anhId = duLieu.anhIds().get(i);
            int updated = jdbcTemplate.update(
                    SQL_GAN_ANH,
                    phienBanId,
                    i + 1,
                    anhId,
                    duLieu.nguoiDangId()
            );
            if (updated != 1) {
                throw new LoiNghiepVuTaoTinException(
                        LoaiLoiTaoTin.ANH_KHONG_HOP_LE,
                        "Không thể gắn ảnh không hợp lệ hoặc không còn ở trạng thái tạm."
                );
            }
        }

        int hanMucUpdated = jdbcTemplate.update(
                SQL_TANG_HAN_MUC_THANG,
                duLieu.nguoiDangId(),
                sqlThang,
                HanMucThangHelper.GIOI_HAN_TIN_MOI_MOI_THANG
        );
        if (hanMucUpdated != 1) {
            throw new LoiNghiepVuTaoTinException(
                    LoaiLoiTaoTin.VUOT_HAN_MUC_THANG,
                    "Tài khoản đã đạt hạn mức tối đa 3 tin mới trong tháng."
            );
        }

        jdbcTemplate.update(
                SQL_INSERT_YEU_CAU_GHI,
                UUID.randomUUID(),
                duLieu.nguoiDangId(),
                LOAI_THAO_TAC_DANG_TIN,
                duLieu.maYDinh(),
                duLieu.duLieuBam(),
                tinId,
                phienBanId,
                TRANG_THAI_CHO_KIEM_DUYET,
                0L,
                thoiDiemSql
        );

        jdbcTemplate.update(
                SQL_INSERT_AUDIT,
                UUID.randomUUID(),
                duLieu.nguoiDangId(),
                tinId,
                phienBanId,
                LOAI_THAO_TAC_DANG_TIN,
                duLieu.duLieuAuditJson(),
                thoiDiemSql
        );

        return new TinGhiKetQua(tinId, phienBanId, TRANG_THAI_CHO_KIEM_DUYET, 0L);
    }

    @Override
    public int docSoTinMoiTrongThang(UUID taiKhoanId, LocalDate dauThangVietNam) {
        if (taiKhoanId == null || dauThangVietNam == null) {
            return 0;
        }
        try {
            Integer val = jdbcTemplate.queryForObject(
                    SQL_DOC_HAN_MUC_THANG,
                    Integer.class,
                    taiKhoanId,
                    Date.valueOf(dauThangVietNam)
            );
            return val != null ? val : 0;
        } catch (EmptyResultDataAccessException ex) {
            return 0;
        }
    }

    private void khoaVaKiemTraAnhTam(UUID nguoiDangId, List<UUID> anhIds) {
        if (anhIds == null || anhIds.isEmpty()) {
            throw new LoiNghiepVuTaoTinException(
                    LoaiLoiTaoTin.ANH_KHONG_HOP_LE,
                    "Danh sách ảnh phải có từ 1 đến 12 ảnh hợp lệ."
            );
        }
        List<UUID> sortedIds = anhIds.stream().sorted(UUID::compareTo).toList();
        String placeholders = String.join(", ", Collections.nCopies(sortedIds.size(), "?"));
        String sql = "SELECT id, chu_id, trang_thai, khoa_anh_hien_thi FROM se214.anh_tin WHERE id IN ("
                + placeholders
                + ") ORDER BY id FOR UPDATE";
        List<AnhKhoaRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AnhKhoaRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("chu_id", UUID.class),
                        rs.getString("trang_thai"),
                        rs.getString("khoa_anh_hien_thi")
                ),
                sortedIds.toArray()
        );
        if (rows.size() != sortedIds.size()) {
            throw new LoiNghiepVuTaoTinException(
                    LoaiLoiTaoTin.ANH_KHONG_HOP_LE,
                    "Danh sách ảnh chứa mã ảnh không tồn tại hoặc không thuộc quyền sử dụng hợp lệ."
            );
        }
        for (AnhKhoaRow row : rows) {
            if (!nguoiDangId.equals(row.chuId())
                    || !AnhTinTruyCap.ANH_TAM.equals(row.trangThai())
                    || row.khoaAnhHienThi() == null
                    || row.khoaAnhHienThi().isBlank()) {
                throw new LoiNghiepVuTaoTinException(
                        LoaiLoiTaoTin.ANH_KHONG_HOP_LE,
                        "Danh sách ảnh chứa mã ảnh không hợp lệ, không ở trạng thái tạm hoặc không thuộc chủ tài khoản."
                );
            }
        }
    }
}
