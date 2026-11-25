package com.realestates.backend.moderation.infrastructure;

import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.domain.KiemDuyetSoBoTin;
import com.realestates.backend.listing.domain.KiemTraNoiDungTin;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.AnhHienThi;
import com.realestates.backend.moderation.dto.GiaQuyDoiHienThi;
import com.realestates.backend.moderation.dto.PhienBanNoiDung;
import com.realestates.backend.moderation.dto.TinQuanLy;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlKiemDuyetRepository implements KiemDuyetRepository {

    private static final String TRANG_THAI_CHO = "cho_kiem_duyet";
    private static final String TRANG_THAI_DA_DUYET = "da_duyet";
    private static final String TRANG_THAI_BI_TU_CHOI = "bi_tu_choi";

    private record TinKhoaRow(
            String trangThai,
            UUID phienBanHienTaiId,
            UUID phienBanDaDuyetId,
            long phienBanCapNhat
    ) {
    }

    /** Một dòng tin + bản hiện tại, chưa có ảnh. */
    private record TinBanRow(
            UUID tinId,
            String trangThai,
            Timestamp ngayDang,
            long phienBanCapNhat,
            UUID phienBanHienTaiId,
            UUID phienBanDaDuyetId,
            String lyDoNgungHienThi,
            UUID phienBanId,
            int soPhienBan,
            String tieuDe,
            String moTa,
            String loaiGiaoDich,
            String loaiBds,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal dienTichM2,
            String diaChi,
            String khuVuc,
            BigDecimal viDo,
            BigDecimal kinhDo,
            String ketQuaSoBo,
            String ketQuaKiemDuyet,
            String lyDoTuChoi,
            Timestamp taoLuc
    ) {
    }

    private record AnhRow(UUID id, UUID phienBanId, int thuTu, String khoaAnhHienThi) {
    }

    private record GoiTamDungRow(UUID id) {
    }

    private static final String COT_TIN_BAN = """
            SELECT t.id AS tin_id, t.trang_thai, t.ngay_dang, t.phien_ban_cap_nhat, t.phien_ban_hien_tai_id,
                   t.phien_ban_da_duyet_id, t.ly_do_ngung_hien_thi,
                   p.id AS phien_ban_id, p.so_phien_ban, p.tieu_de, p.mo_ta, p.loai_giao_dich, p.loai_bds,
                   p.gia_goc, p.don_vi_gia, p.dien_tich_m2, p.dia_chi, p.khu_vuc, p.vi_do, p.kinh_do,
                   p.ket_qua_so_bo, p.ket_qua_kiem_duyet, p.ly_do_tu_choi, p.tao_luc
            FROM se214.tin_bat_dong_san t
            JOIN se214.phien_ban_tin p ON p.id = t.phien_ban_hien_tai_id AND p.tin_id = t.id
            """;

    private static final String DIEU_KIEN_HANG_CHO = """
            WHERE t.trang_thai = 'cho_kiem_duyet'
              AND p.ket_qua_kiem_duyet = 'cho_kiem_duyet'
            """;

    private static final String SQL_HANG_CHO = COT_TIN_BAN + DIEU_KIEN_HANG_CHO + """
            ORDER BY t.ngay_dang ASC, t.id ASC
            LIMIT ? OFFSET ?
            """;

    private static final String SQL_DEM_HANG_CHO = """
            SELECT count(*)
            FROM se214.tin_bat_dong_san t
            JOIN se214.phien_ban_tin p ON p.id = t.phien_ban_hien_tai_id AND p.tin_id = t.id
            """ + DIEU_KIEN_HANG_CHO;

    private static final String SQL_DOC_TIN_BAN = COT_TIN_BAN + " WHERE t.id = ?";

    private static final String SQL_DOC_ANH_CAC_PHIEN_BAN = """
            SELECT id, phien_ban_tin_id, thu_tu, khoa_anh_hien_thi
            FROM se214.anh_tin
            WHERE phien_ban_tin_id = ANY(?)
              AND trang_thai = 'da_gan'
            ORDER BY phien_ban_tin_id, thu_tu ASC
            """;

    private static final String SQL_KHOA_TIN = """
            SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat
            FROM se214.tin_bat_dong_san
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String SQL_TIM_YEU_CAU_QUYET_DINH = """
            SELECT du_lieu_bam, tin_ket_qua_id, phien_ban_ket_qua_id, trang_thai_ket_qua, phien_ban_cap_nhat_ket_qua
            FROM se214.yeu_cau_ghi
            WHERE tai_khoan_id = ?
              AND loai_thao_tac = 'quyet_dinh'
              AND ma_y_dinh = ?
            """;

    private static final String SQL_GHI_QUYET_DINH_PHIEN_BAN = """
            UPDATE se214.phien_ban_tin
            SET ket_qua_kiem_duyet = ?,
                nguoi_duyet_id = ?,
                thoi_diem_quyet_dinh = ?,
                ly_do_tu_choi = CAST(? AS text)
            WHERE id = ?
              AND tin_id = ?
              AND ket_qua_kiem_duyet = 'cho_kiem_duyet'
            """;

    private static final String SQL_PHE_DUYET_TIN = """
            UPDATE se214.tin_bat_dong_san
            SET trang_thai = 'da_duyet',
                phien_ban_da_duyet_id = ?,
                ly_do_ngung_hien_thi = NULL,
                phien_ban_cap_nhat = phien_ban_cap_nhat + 1
            WHERE id = ?
            """;

    /** Từ chối (tin mới hoặc bản sửa): bi_tu_choi, giữ phien_ban_da_duyet_id để truy vết. */
    private static final String SQL_TU_CHOI_TIN = """
            UPDATE se214.tin_bat_dong_san
            SET trang_thai = 'bi_tu_choi',
                phien_ban_cap_nhat = phien_ban_cap_nhat + 1
            WHERE id = ?
            """;

    private static final String SQL_KHOA_GOI_TAM_DUNG = """
            SELECT id
            FROM se214.goi_day_tin
            WHERE tin_id = ?
              AND trang_thai = 'tam_dung_cho_duyet'
            FOR UPDATE
            """;

    private static final String SQL_CHAY_TIEP_GOI = """
            UPDATE se214.goi_day_tin
            SET trang_thai = 'dang_hoat_dong',
                het_han_luc = CAST(? AS timestamptz) + thoi_luong_con_lai
            WHERE id = ?
              AND trang_thai = 'tam_dung_cho_duyet'
            """;

    private static final String SQL_SU_KIEN_CHAY_TIEP = """
            INSERT INTO se214.su_kien_goi (
                id, goi_id, nguoi_thuc_hien_id, loai, thoi_luong_truoc, thoi_luong_sau, thoi_diem
            ) VALUES (
                ?, ?, ?, 'chay_tiep',
                (SELECT thoi_luong_con_lai FROM se214.goi_day_tin WHERE id = ?),
                (SELECT thoi_luong_con_lai FROM se214.goi_day_tin WHERE id = ?),
                ?
            )
            """;

    private static final String SQL_INSERT_AUDIT = """
            INSERT INTO se214.nhat_ky_kiem_toan (
                id, nguoi_thuc_hien_id, tin_id, phien_ban_tin_id, hanh_dong, du_lieu_truoc_sau, ly_do, thoi_diem
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS text), ?)
            """;

    private static final String SQL_INSERT_YEU_CAU_GHI = """
            INSERT INTO se214.yeu_cau_ghi (
                id, tai_khoan_id, loai_thao_tac, ma_y_dinh, du_lieu_bam,
                tin_ket_qua_id, phien_ban_ket_qua_id, trang_thai_ket_qua, phien_ban_cap_nhat_ket_qua, tao_luc
            ) VALUES (?, ?, 'quyet_dinh', ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlKiemDuyetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public List<TinQuanLy> docHangCho(int boQua, int gioiHan) {
        List<TinBanRow> cacDong = jdbcTemplate.query(SQL_HANG_CHO, (rs, rowNum) -> tinBanRow(rs), gioiHan, boQua);
        Map<UUID, List<AnhRow>> anhTheoBan = docAnh(cacDong.stream().map(TinBanRow::phienBanId).toList());
        List<TinQuanLy> ketQua = new ArrayList<>(cacDong.size());
        for (TinBanRow dong : cacDong) {
            ketQua.add(tinQuanLy(dong, anhTheoBan.getOrDefault(dong.phienBanId(), List.of())));
        }
        return ketQua;
    }

    @Override
    public long demHangCho() {
        Long soLuong = jdbcTemplate.queryForObject(SQL_DEM_HANG_CHO, Long.class);
        return soLuong == null ? 0L : soLuong;
    }

    @Override
    public Optional<TinQuanLy> docTinCanDuyet(UUID tinId) {
        Optional<TinBanRow> dong = docTinBan(tinId);
        if (dong.isEmpty()) {
            return Optional.empty();
        }
        List<AnhRow> anh = docAnh(List.of(dong.get().phienBanId())).getOrDefault(dong.get().phienBanId(), List.of());
        return Optional.of(tinQuanLy(dong.get(), anh));
    }

    private Optional<TinBanRow> docTinBan(UUID tinId) {
        return jdbcTemplate.query(SQL_DOC_TIN_BAN, (rs, rowNum) -> tinBanRow(rs), tinId).stream().findFirst();
    }

    private Map<UUID, List<AnhRow>> docAnh(List<UUID> phienBanIds) {
        Map<UUID, List<AnhRow>> ketQua = new HashMap<>();
        if (phienBanIds.isEmpty()) {
            return ketQua;
        }
        List<AnhRow> cacAnh = jdbcTemplate.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(SQL_DOC_ANH_CAC_PHIEN_BAN);
                    Array mang = con.createArrayOf("uuid", phienBanIds.toArray());
                    ps.setArray(1, mang);
                    return ps;
                },
                (rs, rowNum) -> new AnhRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("phien_ban_tin_id", UUID.class),
                        rs.getInt("thu_tu"),
                        rs.getString("khoa_anh_hien_thi")
                )
        );
        for (AnhRow anh : cacAnh) {
            ketQua.computeIfAbsent(anh.phienBanId(), k -> new ArrayList<>()).add(anh);
        }
        return ketQua;
    }

    private static TinQuanLy tinQuanLy(TinBanRow d, List<AnhRow> anh) {
        PhienBanNoiDung noiDung = new PhienBanNoiDung(
                d.phienBanId(),
                d.soPhienBan(),
                d.tieuDe(),
                d.moTa(),
                d.loaiGiaoDich(),
                d.loaiBds(),
                chuoiSo(d.giaGoc()),
                d.donViGia(),
                chuoiSo(d.dienTichM2()),
                d.diaChi(),
                d.khuVuc(),
                chuoiSo(d.viDo()),
                chuoiSo(d.kinhDo()),
                anh.stream().map(a -> AnhHienThi.tu(a.id(), a.thuTu())).toList(),
                d.taoLuc().toInstant(),
                GiaQuyDoiHienThi.tinhCheDoTong(d.loaiGiaoDich(), d.giaGoc(), d.donViGia(), d.dienTichM2())
        );
        return new TinQuanLy(
                d.tinId(),
                d.trangThai(),
                d.ngayDang().toInstant(),
                d.phienBanCapNhat(),
                d.phienBanHienTaiId(),
                d.phienBanDaDuyetId(),
                noiDung,
                d.ketQuaSoBo(),
                d.lyDoTuChoi(),
                d.lyDoNgungHienThi()
        );
    }

    private static String chuoiSo(BigDecimal so) {
        return so == null ? null : so.toPlainString();
    }

    @Override
    public Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
        if (taiKhoanId == null || maYDinh == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                SQL_TIM_YEU_CAU_QUYET_DINH,
                (rs, rowNum) -> new KetQuaYeuCauQuyetDinh(
                        rs.getString("du_lieu_bam"),
                        new TinGhiKetQua(
                                rs.getObject("tin_ket_qua_id", UUID.class),
                                rs.getObject("phien_ban_ket_qua_id", UUID.class),
                                rs.getString("trang_thai_ket_qua"),
                                rs.getLong("phien_ban_cap_nhat_ket_qua")
                        )
                ),
                taiKhoanId,
                maYDinh
        ).stream().findFirst();
    }

    @Override
    public TinGhiKetQua quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu) {
        boolean pheDuyet = duLieu.pheDuyet();

        // 1. Khóa dòng tin trước mọi thứ khác (404 nếu không có).
        TinKhoaRow tin = jdbcTemplate.query(SQL_KHOA_TIN, (rs, rowNum) -> new TinKhoaRow(
                        rs.getString("trang_thai"),
                        rs.getObject("phien_ban_hien_tai_id", UUID.class),
                        rs.getObject("phien_ban_da_duyet_id", UUID.class),
                        rs.getLong("phien_ban_cap_nhat")
                ), duLieu.tinId())
                .stream().findFirst()
                .orElseThrow(() -> new LoiNghiepVuKiemDuyetException(
                        LoaiLoiKiemDuyet.TIN_KHONG_TON_TAI, "Không tìm thấy tin."));

        // 2. Replay Idempotency-Key sau khi đã giữ khóa, nên yêu cầu đồng thời cùng mã luôn thấy bản đã commit.
        Optional<KetQuaYeuCauQuyetDinh> daLuu = timYeuCauQuyetDinh(duLieu.nguoiDuyetId(), duLieu.maYDinh());
        if (daLuu.isPresent()) {
            if (!daLuu.get().duLieuBam().equals(duLieu.duLieuBam())) {
                throw new LoiNghiepVuKiemDuyetException(
                        LoaiLoiKiemDuyet.XUNG_DOT_MA_Y_DINH,
                        "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
                );
            }
            return daLuu.get().ketQua();
        }

        // 3. Tiền điều kiện phiên bản: If-Match và phienBanDaXemId (412 PHIEN_BAN_CU).
        if (tin.phienBanCapNhat() != duLieu.phienBanCapNhatDaXem()
                || !duLieu.phienBanDaXemId().equals(tin.phienBanHienTaiId())) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.PHIEN_BAN_CU, "Tin đã thay đổi. Hãy tải lại trước khi thao tác."
            );
        }

        // 4. Trạng thái: chỉ tin và bản hiện tại còn cho_kiem_duyet (409 TRANG_THAI_KHONG_HOP_LE).
        if (!TRANG_THAI_CHO.equals(tin.trangThai())) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.TRANG_THAI_KHONG_HOP_LE, "Tin không còn ở trạng thái chờ kiểm duyệt."
            );
        }
        TinBanRow ban = docTinBan(duLieu.tinId()).orElseThrow(() -> new LoiNghiepVuKiemDuyetException(
                LoaiLoiKiemDuyet.TIN_KHONG_TON_TAI, "Không tìm thấy tin."));
        if (!TRANG_THAI_CHO.equals(ban.ketQuaKiemDuyet())) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.TRANG_THAI_KHONG_HOP_LE, "Phiên bản này đã có quyết định kiểm duyệt."
            );
        }

        // 5. Phê duyệt: kiểm lại BR-03 trên đúng nội dung và ảnh da_gan của bản này.
        if (pheDuyet && !datBr03(ban)) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.KHONG_DAT_BR03,
                    "Phiên bản chưa đủ điều kiện hiển thị (BR-03); không thể phê duyệt."
            );
        }

        // 6. Ghi phiên bản.
        Timestamp thoiDiemSql = Timestamp.from(duLieu.thoiDiem());
        int daGhi = jdbcTemplate.update(
                SQL_GHI_QUYET_DINH_PHIEN_BAN,
                pheDuyet ? TRANG_THAI_DA_DUYET : TRANG_THAI_BI_TU_CHOI,
                duLieu.nguoiDuyetId(),
                thoiDiemSql,
                pheDuyet ? null : duLieu.lyDo(),
                duLieu.phienBanDaXemId(),
                duLieu.tinId()
        );
        if (daGhi != 1) {
            throw new LoiNghiepVuKiemDuyetException(
                    LoaiLoiKiemDuyet.TRANG_THAI_KHONG_HOP_LE, "Phiên bản này đã có quyết định kiểm duyệt."
            );
        }

        // 7. Ghi tin. Q04–Q05: từ chối (kể cả bản sửa) => bi_tu_choi, giữ phien_ban_da_duyet_id để truy vết.
        String trangThaiMoi;
        UUID phienBanDaDuyetMoi;
        if (pheDuyet) {
            trangThaiMoi = TRANG_THAI_DA_DUYET;
            phienBanDaDuyetMoi = duLieu.phienBanDaXemId();
            jdbcTemplate.update(SQL_PHE_DUYET_TIN, duLieu.phienBanDaXemId(), duLieu.tinId());
        } else {
            trangThaiMoi = TRANG_THAI_BI_TU_CHOI;
            phienBanDaDuyetMoi = tin.phienBanDaDuyetId();
            jdbcTemplate.update(SQL_TU_CHOI_TIN, duLieu.tinId());
        }
        long phienBanCapNhatMoi = tin.phienBanCapNhat() + 1;

        // 8. Gói đẩy tin: chỉ chạy tiếp khi phê duyệt; từ chối giữ nguyên tam_dung_cho_duyet.
        if (pheDuyet) {
            chayTiepGoiTamDung(duLieu.tinId(), duLieu.nguoiDuyetId(), thoiDiemSql);
        }

        // 9. Nhật ký kiểm toán và yêu cầu ghi (snapshot kết quả cho replay).
        jdbcTemplate.update(
                SQL_INSERT_AUDIT,
                UUID.randomUUID(),
                duLieu.nguoiDuyetId(),
                duLieu.tinId(),
                duLieu.phienBanDaXemId(),
                pheDuyet ? "phe_duyet" : "tu_choi",
                duLieuAudit(tin, trangThaiMoi, phienBanDaDuyetMoi, phienBanCapNhatMoi),
                pheDuyet ? null : duLieu.lyDo(),
                thoiDiemSql
        );
        jdbcTemplate.update(
                SQL_INSERT_YEU_CAU_GHI,
                UUID.randomUUID(),
                duLieu.nguoiDuyetId(),
                duLieu.maYDinh(),
                duLieu.duLieuBam(),
                duLieu.tinId(),
                duLieu.phienBanDaXemId(),
                trangThaiMoi,
                phienBanCapNhatMoi,
                thoiDiemSql
        );
        return new TinGhiKetQua(duLieu.tinId(), duLieu.phienBanDaXemId(), trangThaiMoi, phienBanCapNhatMoi);
    }

    /** BR-03: nội dung hợp lệ theo cùng quy tắc đăng/sửa và có 1–12 ảnh da_gan đã có bản hiển thị. */
    private boolean datBr03(TinBanRow ban) {
        Map<String, Object> noiDung = new LinkedHashMap<>();
        noiDung.put(KiemTraNoiDungTin.TRUONG_TIEU_DE, ban.tieuDe());
        noiDung.put(KiemTraNoiDungTin.TRUONG_MO_TA, ban.moTa());
        noiDung.put(KiemTraNoiDungTin.TRUONG_LOAI_GIAO_DICH, ban.loaiGiaoDich());
        noiDung.put(KiemTraNoiDungTin.TRUONG_LOAI_BDS, ban.loaiBds());
        noiDung.put(KiemTraNoiDungTin.TRUONG_GIA_GOC, chuoiSo(ban.giaGoc()));
        noiDung.put(KiemTraNoiDungTin.TRUONG_DON_VI_GIA, ban.donViGia());
        noiDung.put(KiemTraNoiDungTin.TRUONG_DIEN_TICH, chuoiSo(ban.dienTichM2()));
        noiDung.put(KiemTraNoiDungTin.TRUONG_DIA_CHI, ban.diaChi());
        noiDung.put(KiemTraNoiDungTin.TRUONG_KHU_VUC, ban.khuVuc());
        noiDung.put(KiemTraNoiDungTin.TRUONG_VI_DO, chuoiSo(ban.viDo()));
        noiDung.put(KiemTraNoiDungTin.TRUONG_KINH_DO, chuoiSo(ban.kinhDo()));
        KiemTraNoiDungTin.KetQua ketQuaNoiDung = KiemTraNoiDungTin.kiemTra(noiDung);
        if (!ketQuaNoiDung.hopLe()) {
            return false;
        }
        List<AnhRow> anh = docAnh(List.of(ban.phienBanId())).getOrDefault(ban.phienBanId(), List.of());
        boolean anhDuBanHienThi = anh.stream().allMatch(a -> a.khoaAnhHienThi() != null && !a.khoaAnhHienThi().isBlank());
        if (!anhDuBanHienThi) {
            return false;
        }
        List<UUID> anhIds = anh.stream().map(AnhRow::id).toList();
        return KiemDuyetSoBoTin.danhGia(ketQuaNoiDung.noiDung(), anhIds).dat();
    }

    private void chayTiepGoiTamDung(UUID tinId, UUID nguoiThucHienId, Timestamp thoiDiemSql) {
        List<GoiTamDungRow> cacGoi = jdbcTemplate.query(
                SQL_KHOA_GOI_TAM_DUNG,
                (rs, rowNum) -> new GoiTamDungRow(rs.getObject("id", UUID.class)),
                tinId
        );
        for (GoiTamDungRow goi : cacGoi) {
            jdbcTemplate.update(SQL_CHAY_TIEP_GOI, thoiDiemSql, goi.id());
            jdbcTemplate.update(
                    SQL_SU_KIEN_CHAY_TIEP,
                    UUID.randomUUID(),
                    goi.id(),
                    nguoiThucHienId,
                    goi.id(),
                    goi.id(),
                    thoiDiemSql
            );
        }
    }

    private static String duLieuAudit(
            TinKhoaRow truocRow,
            String trangThaiSau,
            UUID phienBanDaDuyetSau,
            long phienBanCapNhatSau
    ) {
        Map<String, Object> truoc = new LinkedHashMap<>();
        truoc.put("trangThai", truocRow.trangThai());
        truoc.put("phienBanDaDuyetId", truocRow.phienBanDaDuyetId());
        truoc.put("phienBanCapNhat", truocRow.phienBanCapNhat());

        Map<String, Object> sau = new LinkedHashMap<>();
        sau.put("trangThai", trangThaiSau);
        sau.put("phienBanDaDuyetId", phienBanDaDuyetSau);
        sau.put("phienBanCapNhat", phienBanCapNhatSau);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("truoc", truoc);
        audit.put("sau", sau);
        return ChuanHoaJson.toJsonString(audit);
    }

    private static TinBanRow tinBanRow(ResultSet rs) throws SQLException {
        return new TinBanRow(
                rs.getObject("tin_id", UUID.class),
                rs.getString("trang_thai"),
                rs.getTimestamp("ngay_dang"),
                rs.getLong("phien_ban_cap_nhat"),
                rs.getObject("phien_ban_hien_tai_id", UUID.class),
                rs.getObject("phien_ban_da_duyet_id", UUID.class),
                rs.getString("ly_do_ngung_hien_thi"),
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
                rs.getString("ket_qua_so_bo"),
                rs.getString("ket_qua_kiem_duyet"),
                rs.getString("ly_do_tu_choi"),
                rs.getTimestamp("tao_luc")
        );
    }
}
