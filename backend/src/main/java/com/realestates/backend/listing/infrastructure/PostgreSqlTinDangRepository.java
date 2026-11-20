package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.QuyTacQuanLyTin;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private record TinKhoaRow(
            UUID chuId,
            String trangThai,
            UUID phienBanHienTaiId,
            UUID phienBanDaDuyetId,
            long phienBanCapNhat
    ) {
    }

    private record AnhChoSuaRow(
            UUID id,
            UUID chuId,
            String trangThai,
            String khoaTepGoc,
            String khoaAnhHienThi,
            String mimeType,
            long kichThuocByte,
            UUID tinIdCuaAnh
    ) {
    }

    private record GoiKhoaRow(UUID id, String trangThai, Timestamp hetHanLuc) {
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

    private static final String SQL_KHOA_CHU_TIN = """
            SELECT chu_id
            FROM se214.tin_bat_dong_san
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String SQL_KHOA_TIN = """
            SELECT chu_id, trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat
            FROM se214.tin_bat_dong_san
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String SQL_SO_PHIEN_BAN_TIEP_THEO = """
            SELECT COALESCE(MAX(so_phien_ban), 0) + 1
            FROM se214.phien_ban_tin
            WHERE tin_id = ?
            """;

    private static final String SQL_INSERT_PHIEN_BAN_SUA = """
            INSERT INTO se214.phien_ban_tin (
                id, tin_id, so_phien_ban, tieu_de, mo_ta, loai_giao_dich, loai_bds,
                gia_goc, don_vi_gia, dien_tich_m2, dia_chi, khu_vuc, vi_do, kinh_do,
                ket_qua_so_bo, ket_qua_kiem_duyet, tao_luc
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_NHAN_BAN_ANH = """
            INSERT INTO se214.anh_tin (
                id, chu_id, phien_ban_tin_id, khoa_tep_goc, khoa_anh_hien_thi, mime_type, kich_thuoc_byte, thu_tu, trang_thai
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'da_gan')
            """;

    private static final String SQL_DOI_TIN_SANG_CHO_DUYET = """
            UPDATE se214.tin_bat_dong_san
            SET trang_thai = 'cho_kiem_duyet',
                phien_ban_hien_tai_id = ?,
                phien_ban_cap_nhat = phien_ban_cap_nhat + 1
            WHERE id = ?
            """;

    private static final String SQL_KHOA_GOI_CON_HIEU_LUC = """
            SELECT id, trang_thai, het_han_luc
            FROM se214.goi_day_tin
            WHERE tin_id = ?
              AND trang_thai IN ('dang_hoat_dong', 'tam_dung_cho_duyet')
            FOR UPDATE
            """;

    private static final String SQL_TAM_DUNG_GOI = """
            UPDATE se214.goi_day_tin
            SET trang_thai = 'tam_dung_cho_duyet',
                thoi_luong_con_lai = het_han_luc - CAST(? AS timestamptz),
                het_han_luc = NULL
            WHERE id = ?
              AND trang_thai = 'dang_hoat_dong'
            """;

    private static final String SQL_SU_KIEN_TAM_DUNG = """
            INSERT INTO se214.su_kien_goi (
                id, goi_id, nguoi_thuc_hien_id, loai, thoi_luong_truoc, thoi_luong_sau, thoi_diem
            ) VALUES (
                ?, ?, ?, 'tam_dung',
                (SELECT thoi_luong_con_lai FROM se214.goi_day_tin WHERE id = ?),
                (SELECT thoi_luong_con_lai FROM se214.goi_day_tin WHERE id = ?),
                ?
            )
            """;

    private static final String SQL_DANH_DAU_TIN_DA_XOA = """
            UPDATE se214.tin_bat_dong_san
            SET trang_thai = 'da_xoa',
                phien_ban_cap_nhat = phien_ban_cap_nhat + 1
            WHERE id = ?
            """;

    private static final String SQL_SU_KIEN_KET_THUC_GOI = """
            INSERT INTO se214.su_kien_goi (
                id, goi_id, nguoi_thuc_hien_id, loai, thoi_luong_truoc, thoi_luong_sau, thoi_diem
            ) VALUES (
                ?, ?, ?, 'ket_thuc_do_xoa',
                (SELECT CASE WHEN trang_thai = 'dang_hoat_dong'
                             THEN GREATEST(het_han_luc - CAST(? AS timestamptz), interval '0 seconds')
                             ELSE thoi_luong_con_lai END
                 FROM se214.goi_day_tin WHERE id = ?),
                interval '0 seconds',
                ?
            )
            """;

    private static final String SQL_KET_THUC_GOI = """
            UPDATE se214.goi_day_tin
            SET trang_thai = 'ket_thuc_do_go_tin',
                thoi_luong_con_lai = interval '0 seconds',
                het_han_luc = NULL
            WHERE id = ?
            """;

    private static final String SQL_KHOA_BAO_CAO_CHO_XU_LY = """
            SELECT id
            FROM se214.bao_cao_vi_pham
            WHERE tin_id = ?
              AND trang_thai = 'cho_xu_ly'
            ORDER BY id
            FOR UPDATE
            """;

    private static final String SQL_DONG_BAO_CAO_CHO_XU_LY = """
            UPDATE se214.bao_cao_vi_pham
            SET trang_thai = 'da_dong',
                ly_do_dong = 'tin_da_xoa',
                dong_luc = ?
            WHERE tin_id = ?
              AND trang_thai = 'cho_xu_ly'
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

    @Override
    public Optional<UUID> khoaVaDocChuTin(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                SQL_KHOA_CHU_TIN,
                (rs, rowNum) -> rs.getObject("chu_id", UUID.class),
                tinId
        ).stream().findFirst();
    }

    @Override
    public TinGhiKetQua suaTinTrongTransaction(DuLieuSuaTin duLieu) {
        Optional<KetQuaYeuCauGhi> daLuu = timYeuCauGhi(duLieu.nguoiSuaId(), LOAI_THAO_TAC_SUA_TIN, duLieu.maYDinh());
        if (daLuu.isPresent()) {
            return phatLaiKetQuaDaGhi(daLuu.get(), duLieu.duLieuBam());
        }

        TinKhoaRow tin = docVaKhoaTin(duLieu.tinId());
        kiemTraDieuKienGhi(
                QuyTacQuanLyTin.dungPhienBanDaXem(
                        tin.phienBanCapNhat(),
                        duLieu.phienBanCapNhatDaXem(),
                        tin.phienBanHienTaiId(),
                        duLieu.phienBanDaXemId()
                ),
                QuyTacQuanLyTin.choPhepSua(tin.trangThai())
        );

        Map<UUID, AnhChoSuaRow> anhTheoId = khoaVaKiemTraAnhChoSua(duLieu.nguoiSuaId(), duLieu.tinId(), duLieu.anhIds());

        UUID phienBanMoiId = UUID.randomUUID();
        Integer soPhienBanMoi = jdbcTemplate.queryForObject(SQL_SO_PHIEN_BAN_TIEP_THEO, Integer.class, duLieu.tinId());
        Timestamp thoiDiemSql = Timestamp.from(duLieu.thoiDiem());
        var nd = duLieu.noiDung();
        jdbcTemplate.update(
                SQL_INSERT_PHIEN_BAN_SUA,
                phienBanMoiId,
                duLieu.tinId(),
                soPhienBanMoi,
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

        ganAnhChoPhienBanSua(duLieu.nguoiSuaId(), phienBanMoiId, duLieu.anhIds(), anhTheoId);

        jdbcTemplate.update(SQL_DOI_TIN_SANG_CHO_DUYET, phienBanMoiId, duLieu.tinId());
        long phienBanCapNhatMoi = tin.phienBanCapNhat() + 1;

        tamDungGoiNeuConHieuLuc(duLieu.tinId(), duLieu.nguoiSuaId(), duLieu.thoiDiem());

        TinGhiKetQua ketQua = new TinGhiKetQua(
                duLieu.tinId(),
                phienBanMoiId,
                TRANG_THAI_CHO_KIEM_DUYET,
                phienBanCapNhatMoi
        );
        ghiYeuCauVaAudit(
                duLieu.nguoiSuaId(),
                LOAI_THAO_TAC_SUA_TIN,
                duLieu.maYDinh(),
                duLieu.duLieuBam(),
                ketQua,
                QuyTacQuanLyTin.duLieuAuditSua(
                        tin.trangThai(),
                        tin.phienBanHienTaiId(),
                        tin.phienBanCapNhat(),
                        phienBanMoiId,
                        soPhienBanMoi,
                        phienBanCapNhatMoi
                ),
                thoiDiemSql
        );
        return ketQua;
    }

    private static TinGhiKetQua phatLaiKetQuaDaGhi(KetQuaYeuCauGhi daLuu, String duLieuBam) {
        if (!daLuu.duLieuBam().equals(duLieuBam)) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.XUNG_DOT_MA_Y_DINH,
                    "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
            );
        }
        return daLuu.ketQua();
    }

    private static void kiemTraDieuKienGhi(boolean dungPhienBan, boolean trangThaiChoPhep) {
        if (!dungPhienBan) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.PHIEN_BAN_CU,
                    "Tin đã thay đổi. Hãy tải lại trước khi thao tác."
            );
        }
        if (!trangThaiChoPhep) {
            throw new LoiNghiepVuQuanLyTinException(
                    LoaiLoiQuanLyTin.TRANG_THAI_KHONG_HOP_LE,
                    "Trạng thái hiện tại của tin không cho phép thao tác này."
            );
        }
    }

    private TinKhoaRow docVaKhoaTin(UUID tinId) {
        List<TinKhoaRow> cacDong = jdbcTemplate.query(
                SQL_KHOA_TIN,
                (rs, rowNum) -> new TinKhoaRow(
                        rs.getObject("chu_id", UUID.class),
                        rs.getString("trang_thai"),
                        rs.getObject("phien_ban_hien_tai_id", UUID.class),
                        rs.getObject("phien_ban_da_duyet_id", UUID.class),
                        rs.getLong("phien_ban_cap_nhat")
                ),
                tinId
        );
        if (cacDong.isEmpty()) {
            throw new LoiNghiepVuQuanLyTinException(LoaiLoiQuanLyTin.TIN_KHONG_TON_TAI, "Không tìm thấy tin.");
        }
        return cacDong.get(0);
    }

    private Map<UUID, AnhChoSuaRow> khoaVaKiemTraAnhChoSua(UUID nguoiSuaId, UUID tinId, List<UUID> anhIds) {
        List<UUID> sortedIds = anhIds.stream().sorted(UUID::compareTo).toList();
        String placeholders = String.join(", ", Collections.nCopies(sortedIds.size(), "?"));
        String sql = "SELECT a.id, a.chu_id, a.trang_thai, a.khoa_tep_goc, a.khoa_anh_hien_thi, a.mime_type, "
                + "a.kich_thuoc_byte, pb.tin_id AS tin_id_cua_anh "
                + "FROM se214.anh_tin a LEFT JOIN se214.phien_ban_tin pb ON pb.id = a.phien_ban_tin_id "
                + "WHERE a.id IN (" + placeholders + ") ORDER BY a.id FOR UPDATE OF a";
        List<AnhChoSuaRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AnhChoSuaRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("chu_id", UUID.class),
                        rs.getString("trang_thai"),
                        rs.getString("khoa_tep_goc"),
                        rs.getString("khoa_anh_hien_thi"),
                        rs.getString("mime_type"),
                        rs.getLong("kich_thuoc_byte"),
                        rs.getObject("tin_id_cua_anh", UUID.class)
                ),
                sortedIds.toArray()
        );
        if (rows.size() != sortedIds.size()) {
            throw anhKhongHopLe();
        }
        Map<UUID, AnhChoSuaRow> ketQua = new HashMap<>();
        for (AnhChoSuaRow row : rows) {
            boolean anhTamHopLe = AnhTinTruyCap.ANH_TAM.equals(row.trangThai())
                    && row.khoaAnhHienThi() != null
                    && !row.khoaAnhHienThi().isBlank();
            boolean anhCuCuaTinNay = AnhTinTruyCap.ANH_DA_GAN.equals(row.trangThai())
                    && tinId.equals(row.tinIdCuaAnh());
            if (!nguoiSuaId.equals(row.chuId()) || (!anhTamHopLe && !anhCuCuaTinNay)) {
                throw anhKhongHopLe();
            }
            ketQua.put(row.id(), row);
        }
        return ketQua;
    }

    private void ganAnhChoPhienBanSua(
            UUID nguoiSuaId,
            UUID phienBanMoiId,
            List<UUID> anhIds,
            Map<UUID, AnhChoSuaRow> anhTheoId
    ) {
        for (int i = 0; i < anhIds.size(); i++) {
            AnhChoSuaRow anh = anhTheoId.get(anhIds.get(i));
            int thuTu = i + 1;
            if (AnhTinTruyCap.ANH_TAM.equals(anh.trangThai())) {
                int updated = jdbcTemplate.update(SQL_GAN_ANH, phienBanMoiId, thuTu, anh.id(), nguoiSuaId);
                if (updated != 1) {
                    throw anhKhongHopLe();
                }
            } else {
                jdbcTemplate.update(
                        SQL_NHAN_BAN_ANH,
                        UUID.randomUUID(),
                        nguoiSuaId,
                        phienBanMoiId,
                        anh.khoaTepGoc(),
                        anh.khoaAnhHienThi(),
                        anh.mimeType(),
                        anh.kichThuocByte(),
                        thuTu
                );
            }
        }
    }

    private static LoiNghiepVuQuanLyTinException anhKhongHopLe() {
        return new LoiNghiepVuQuanLyTinException(
                LoaiLoiQuanLyTin.ANH_KHONG_HOP_LE,
                "Danh sách ảnh chứa mã ảnh không tồn tại, không thuộc chủ tin hoặc không được phép dùng lại."
        );
    }

    private void tamDungGoiNeuConHieuLuc(UUID tinId, UUID nguoiThucHienId, Instant thoiDiem) {
        List<GoiKhoaRow> cacGoi = jdbcTemplate.query(
                SQL_KHOA_GOI_CON_HIEU_LUC,
                (rs, rowNum) -> new GoiKhoaRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("trang_thai"),
                        rs.getTimestamp("het_han_luc")
                ),
                tinId
        );
        Timestamp thoiDiemSql = Timestamp.from(thoiDiem);
        for (GoiKhoaRow goi : cacGoi) {
            boolean dangChayVaConHan = "dang_hoat_dong".equals(goi.trangThai())
                    && goi.hetHanLuc() != null
                    && goi.hetHanLuc().toInstant().isAfter(thoiDiem);
            if (!dangChayVaConHan) {
                continue;
            }
            jdbcTemplate.update(SQL_TAM_DUNG_GOI, thoiDiemSql, goi.id());
            jdbcTemplate.update(
                    SQL_SU_KIEN_TAM_DUNG,
                    UUID.randomUUID(),
                    goi.id(),
                    nguoiThucHienId,
                    goi.id(),
                    goi.id(),
                    thoiDiemSql
            );
        }
    }

    private void ghiYeuCauVaAudit(
            UUID nguoiThucHienId,
            String loaiThaoTac,
            String maYDinh,
            String duLieuBam,
            TinGhiKetQua ketQua,
            String duLieuAuditJson,
            Timestamp thoiDiemSql
    ) {
        jdbcTemplate.update(
                SQL_INSERT_YEU_CAU_GHI,
                UUID.randomUUID(),
                nguoiThucHienId,
                loaiThaoTac,
                maYDinh,
                duLieuBam,
                ketQua.tinId(),
                ketQua.phienBanHienTaiId(),
                ketQua.trangThai(),
                ketQua.phienBanCapNhat(),
                thoiDiemSql
        );
        jdbcTemplate.update(
                SQL_INSERT_AUDIT,
                UUID.randomUUID(),
                nguoiThucHienId,
                ketQua.tinId(),
                ketQua.phienBanHienTaiId(),
                loaiThaoTac,
                duLieuAuditJson,
                thoiDiemSql
        );
    }

    @Override
    public TinGhiKetQua xoaTinTrongTransaction(DuLieuXoaTin duLieu) {
        Optional<KetQuaYeuCauGhi> daLuu = timYeuCauGhi(duLieu.nguoiXoaId(), LOAI_THAO_TAC_XOA_TIN, duLieu.maYDinh());
        if (daLuu.isPresent()) {
            return phatLaiKetQuaDaGhi(daLuu.get(), duLieu.duLieuBam());
        }

        TinKhoaRow tin = docVaKhoaTin(duLieu.tinId());
        kiemTraDieuKienGhi(
                QuyTacQuanLyTin.dungPhienBanCapNhat(tin.phienBanCapNhat(), duLieu.phienBanCapNhatDaXem()),
                QuyTacQuanLyTin.choPhepXoa(tin.trangThai())
        );

        jdbcTemplate.update(SQL_DANH_DAU_TIN_DA_XOA, duLieu.tinId());
        long phienBanCapNhatMoi = tin.phienBanCapNhat() + 1;
        Timestamp thoiDiemSql = Timestamp.from(duLieu.thoiDiem());

        ketThucGoiDangChayHoacTamDung(duLieu.tinId(), duLieu.nguoiXoaId(), thoiDiemSql);
        dongBaoCaoChoXuLy(duLieu.tinId(), thoiDiemSql);

        TinGhiKetQua ketQua = new TinGhiKetQua(
                duLieu.tinId(),
                tin.phienBanHienTaiId(),
                TRANG_THAI_DA_XOA,
                phienBanCapNhatMoi
        );
        ghiYeuCauVaAudit(
                duLieu.nguoiXoaId(),
                LOAI_THAO_TAC_XOA_TIN,
                duLieu.maYDinh(),
                duLieu.duLieuBam(),
                ketQua,
                QuyTacQuanLyTin.duLieuAuditXoa(tin.trangThai(), tin.phienBanCapNhat(), phienBanCapNhatMoi),
                thoiDiemSql
        );
        return ketQua;
    }

    private void ketThucGoiDangChayHoacTamDung(UUID tinId, UUID nguoiThucHienId, Timestamp thoiDiemSql) {
        List<UUID> cacGoiId = jdbcTemplate.query(
                SQL_KHOA_GOI_CON_HIEU_LUC,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tinId
        );
        for (UUID goiId : cacGoiId) {
            jdbcTemplate.update(
                    SQL_SU_KIEN_KET_THUC_GOI,
                    UUID.randomUUID(),
                    goiId,
                    nguoiThucHienId,
                    thoiDiemSql,
                    goiId,
                    thoiDiemSql
            );
            jdbcTemplate.update(SQL_KET_THUC_GOI, goiId);
        }
    }

    private void dongBaoCaoChoXuLy(UUID tinId, Timestamp thoiDiemSql) {
        jdbcTemplate.query(
                SQL_KHOA_BAO_CAO_CHO_XU_LY,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tinId
        );
        jdbcTemplate.update(SQL_DONG_BAO_CAO_CHO_XU_LY, thoiDiemSql, tinId);
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
