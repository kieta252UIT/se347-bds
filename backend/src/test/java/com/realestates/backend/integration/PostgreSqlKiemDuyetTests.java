package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.BanDoPort;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport.TaiKhoanSession;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport.TinCho;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.TinQuanLy;
import com.realestates.backend.moderation.dto.TrangTinQuanLy;
import com.realestates.backend.moderation.infrastructure.PostgreSqlKiemDuyetRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Kiểm duyệt trên PostgreSQL thật theo hợp đồng openapi-sprint-1.yaml (layDanhSachChoDuyet, layBanCanDuyet,
 * quyetDinhKiemDuyet). Dữ liệu tạo qua luồng đăng/sửa/xóa thật của module listing.
 */
@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlKiemDuyetTests {

    private static final Instant THOI_DIEM = KiemDuyetPgTestSupport.THOI_DIEM;
    private static final Instant THOI_DIEM_DUYET = KiemDuyetPgTestSupport.THOI_DIEM_DUYET;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TaiKhoanRepository taiKhoanRepository;

    @Autowired
    private PhienXacThucRepository phienXacThucRepository;

    @Autowired
    private AnhTinRepository anhTinRepository;

    @Autowired
    private TinDangRepository tinDangRepository;

    @Autowired
    private KiemDuyetRepository kiemDuyetRepository;

    @Autowired
    private TinDangService tinDangService;

    @Autowired
    private CurrentPermissionAndCsrfService permissionAndCsrfService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    @Autowired
    private BanDoPort banDoPort;

    private KiemDuyetService kiemDuyetService;
    private KiemDuyetPgTestSupport ht;

    @BeforeEach
    void setUp() {
        assertThat(kiemDuyetRepository).isInstanceOf(PostgreSqlKiemDuyetRepository.class);
        kiemDuyetService = new KiemDuyetService(
                permissionAndCsrfService,
                kiemDuyetRepository,
                Clock.fixed(THOI_DIEM_DUYET, ZoneOffset.UTC)
        );
        QuanLyTinService quanLyTinService = new QuanLyTinService(
                permissionAndCsrfService,
                tinDangRepository,
                banDoPort,
                Clock.fixed(THOI_DIEM, ZoneOffset.UTC)
        );
        ht = new KiemDuyetPgTestSupport(
                jdbcTemplate,
                transactionManager,
                taiKhoanRepository,
                phienXacThucRepository,
                anhTinRepository,
                tinDangService,
                quanLyTinService,
                kiemDuyetService,
                sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    // ---------------------------------------------------------------- danh sách chờ

    @Test
    @DisplayName("Danh sách chờ chỉ chứa tin cho_kiem_duyet với bản hiện tại; loại tin đã duyệt, bị từ chối, đã xóa, bị gỡ")
    void danhSachChoChiChuaTinChoDuyet() {
        TaiKhoanSession chu1 = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession chu2 = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        // Hạn mức 3 tin mới/tháng/tài khoản: tách sang chu3 để fixture không chạm hạn mức.
        TaiKhoanSession chu3 = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        TinCho tinCho = ht.taoTinMoiChoDuyet(chu1);
        TinCho tinDaDuyet = ht.taoTinDaDuyet(chu1, kdv);
        TinCho tinXoa = ht.taoTinMoiChoDuyet(chu1);
        ht.xoaTin(chu1, tinXoa.tinId());
        TinCho tinBiGo = ht.taoTinMoiChoDuyet(chu2);
        jdbcTemplate.update(
                "UPDATE se214.tin_bat_dong_san SET trang_thai = 'bi_go_vi_pham', ly_do_ngung_hien_thi = 'Vi phạm' WHERE id = ?",
                tinBiGo.tinId()
        );
        TinCho tinSua = ht.taoTinDaDuyet(chu2, kdv);
        TinGhiKetQua suaKetQua = ht.suaTin(chu2, tinSua.tinId(), "Tiêu đề bản sửa chờ duyệt");
        TinCho tinBiTuChoi = ht.taoTinMoiChoDuyet(chu2);
        ht.tuChoi(kdv, tinBiTuChoi.tinId(), tinBiTuChoi.phienBanId(), "tc-1", ht.etag(tinBiTuChoi.tinId()), "Thiếu thông tin");
        TinCho tinTuChoiRoiSua = ht.taoTinMoiChoDuyet(chu3);
        ht.tuChoi(kdv, tinTuChoiRoiSua.tinId(), tinTuChoiRoiSua.phienBanId(), "tc-2", ht.etag(tinTuChoiRoiSua.tinId()), "Thiếu thông tin");
        TinGhiKetQua suaLaiKetQua = ht.suaTin(chu3, tinTuChoiRoiSua.tinId(), "Tiêu đề đã chỉnh theo lý do");

        List<TinQuanLy> tatCa = docTatCaDanhSachCho(kdv, 50);
        Set<UUID> tinTrongDanhSach = new HashSet<>();
        tatCa.forEach(t -> tinTrongDanhSach.add(t.id()));

        assertThat(tinTrongDanhSach).contains(tinCho.tinId(), tinSua.tinId(), tinTuChoiRoiSua.tinId());
        assertThat(tinTrongDanhSach).doesNotContain(tinDaDuyet.tinId(), tinXoa.tinId(), tinBiGo.tinId(), tinBiTuChoi.tinId());
        assertThat(tatCa).allSatisfy(t -> assertThat(t.trangThai()).isEqualTo("cho_kiem_duyet"));

        TinQuanLy banSua = timTin(tatCa, tinSua.tinId());
        assertThat(banSua.phienBanHienTaiId()).isEqualTo(suaKetQua.phienBanHienTaiId());
        assertThat(banSua.phienBanDaDuyetId()).isEqualTo(tinSua.phienBanId());
        assertThat(banSua.noiDung().id()).isEqualTo(suaKetQua.phienBanHienTaiId());
        assertThat(banSua.noiDung().soPhienBan()).isEqualTo(2);
        assertThat(banSua.noiDung().tieuDe()).isEqualTo("Tiêu đề bản sửa chờ duyệt");

        TinQuanLy banMoi = timTin(tatCa, tinCho.tinId());
        assertThat(banMoi.phienBanDaDuyetId()).isNull();
        assertThat(banMoi.phienBanCapNhat()).isZero();
        assertThat(banMoi.ngayDang()).isNotNull();
        assertThat(banMoi.ketQuaSoBo()).isNotBlank();
        assertThat(banMoi.lyDoTuChoi()).isNull();
        assertThat(banMoi.lyDoNgungHienThi()).isNull();
        assertThat(banMoi.noiDung().soPhienBan()).isEqualTo(1);
        assertThat(banMoi.noiDung().loaiGiaoDich()).isEqualTo("ban");
        assertThat(banMoi.noiDung().khuVuc()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(banMoi.noiDung().giaGoc()).isEqualTo("12000000000");
        assertThat(banMoi.noiDung().giaQuyDoiHienThi().giaTri()).isEqualTo("12000000000.00");
        assertThat(banMoi.noiDung().giaQuyDoiHienThi().donVi()).isEqualTo("vnd_tong");
        assertThat(banMoi.noiDung().anh()).hasSize(1);
        assertThat(banMoi.noiDung().anh().get(0).id()).isEqualTo(tinCho.anhId());
        assertThat(banMoi.noiDung().anh().get(0).duongDanDoc()).isEqualTo("/api/images/" + tinCho.anhId() + "/content");

        // Tin bị từ chối rồi sửa lại: vào lại danh sách với bản mới, chưa từng được duyệt.
        TinQuanLy banSuaLai = timTin(tatCa, tinTuChoiRoiSua.tinId());
        assertThat(banSuaLai.phienBanHienTaiId()).isEqualTo(suaLaiKetQua.phienBanHienTaiId());
        assertThat(banSuaLai.phienBanDaDuyetId()).isNull();
    }

    @Test
    @DisplayName("Phân trang trang/kichThuoc ổn định khi trùng ngay_dang: không trùng, không sót, thứ tự (ngay_dang, id) tăng; tongSo đúng")
    void phanTrangOnDinhKhiCungNgayDang() {
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        List<TinCho> cacTin = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
            cacTin.add(ht.taoTinMoiChoDuyet(chu));
            cacTin.add(ht.taoTinMoiChoDuyet(chu));
        }
        Timestamp cungThoiDiem = Timestamp.from(Instant.parse("2001-01-01T00:00:00.123456Z"));
        for (TinCho tin : cacTin) {
            jdbcTemplate.update("UPDATE se214.tin_bat_dong_san SET ngay_dang = ? WHERE id = ?", cungThoiDiem, tin.tinId());
        }

        long tongSo = kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), "0", "2").tongSo();
        List<TinQuanLy> tatCa = docTatCaDanhSachCho(kdv, 2);
        assertThat(tatCa).hasSize((int) tongSo);

        Set<UUID> daThay = new HashSet<>();
        for (TinQuanLy tin : tatCa) {
            assertThat(daThay.add(tin.id())).as("Không được trả trùng " + tin.id()).isTrue();
        }
        List<TinQuanLy> cuaToi = tatCa.stream()
                .filter(t -> cacTin.stream().anyMatch(c -> c.tinId().equals(t.id())))
                .toList();
        assertThat(cuaToi).hasSize(cacTin.size());
        assertThat(cuaToi).extracting(TinQuanLy::ngayDang).containsOnly(cungThoiDiem.toInstant());
        assertThat(cuaToi.stream().map(t -> t.id().toString()).toList()).isSorted();
        for (int i = 1; i < tatCa.size(); i++) {
            TinQuanLy truoc = tatCa.get(i - 1);
            TinQuanLy sau = tatCa.get(i);
            int soSanh = truoc.ngayDang().compareTo(sau.ngayDang());
            assertThat(soSanh < 0 || (soSanh == 0 && truoc.id().toString().compareTo(sau.id().toString()) < 0))
                    .as("Thứ tự tại vị trí " + i)
                    .isTrue();
        }

        TrangTinQuanLy trangDau = kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), "0", "2");
        assertThat(trangDau.trang()).isZero();
        assertThat(trangDau.kichThuoc()).isEqualTo(2);
        assertThat(trangDau.noiDung()).hasSizeLessThanOrEqualTo(2);
        TrangTinQuanLy macDinh = kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), null, null);
        assertThat(macDinh.trang()).isZero();
        assertThat(macDinh.kichThuoc()).isEqualTo(20);
    }

    @Test
    @DisplayName("trang/kichThuoc sai định dạng hoặc ngoài khoảng bị 400 với lỗi theo trường, không clamp")
    void trangVaKichThuocKhongHopLe() {
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        for (String kichThuoc : List.of("0", "51", "-1", "abc", "1.5", "+5", "99999999999", "")) {
            assertThatThrownBy(() -> kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), null, kichThuoc))
                    .as("kichThuoc=" + kichThuoc)
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("kichThuoc"));
        }
        for (String trang : List.of("-1", "100001", "x", "1e3")) {
            assertThatThrownBy(() -> kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), trang, null))
                    .as("trang=" + trang)
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("trang"));
        }
        assertThat(kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), "100000", "50").noiDung()).isEmpty();
        assertThat(kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), "0", "1").noiDung().size()).isLessThanOrEqualTo(1);
    }

    // ---------------------------------------------------------------- bản cần duyệt

    @Test
    @DisplayName("Bản cần duyệt trả đúng bản hiện tại (bản sửa, không nhầm bản đã duyệt) kèm ảnh da_gan; 404 khi không có tin, 409 khi không còn chờ")
    void banCanDuyet() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinDaDuyet(chu, kdv);
        TinGhiKetQua sua = ht.suaTin(chu, tin.tinId(), "Tiêu đề bản sửa");
        TinCho tinMoi = ht.taoTinMoiChoDuyet(chu);

        TinQuanLy banSua = kiemDuyetService.layBanCanDuyet(kdv.rawCookie(), tin.tinId());
        assertThat(banSua.id()).isEqualTo(tin.tinId());
        assertThat(banSua.trangThai()).isEqualTo("cho_kiem_duyet");
        assertThat(banSua.phienBanCapNhat()).isEqualTo(ht.phienBanCapNhat(tin.tinId()));
        assertThat(banSua.phienBanHienTaiId()).isEqualTo(sua.phienBanHienTaiId());
        assertThat(banSua.phienBanDaDuyetId()).isEqualTo(tin.phienBanId());
        assertThat(banSua.noiDung().id()).isEqualTo(sua.phienBanHienTaiId());
        assertThat(banSua.noiDung().soPhienBan()).isEqualTo(2);
        assertThat(banSua.noiDung().tieuDe()).isEqualTo("Tiêu đề bản sửa");
        assertThat(banSua.noiDung().taoLuc()).isNotNull();
        assertThat(banSua.noiDung().anh()).hasSize(1);
        assertThat(banSua.noiDung().anh().get(0).thuTu()).isEqualTo(1);
        assertThat(banSua.noiDung().anh().get(0).duongDanDoc()).startsWith("/api/images/").endsWith("/content");
        assertThat(banSua.ketQuaSoBo()).isNotBlank();
        assertThat(banSua.lyDoTuChoi()).isNull();

        TinQuanLy banMoi = kiemDuyetService.layBanCanDuyet(kdv.rawCookie(), tinMoi.tinId());
        assertThat(banMoi.phienBanDaDuyetId()).isNull();
        assertThat(banMoi.noiDung().id()).isEqualTo(tinMoi.phienBanId());

        assertThatThrownBy(() -> kiemDuyetService.layBanCanDuyet(kdv.rawCookie(), UUID.randomUUID()))
                .isInstanceOf(ListingNotFoundException.class);

        TinCho tinDaDuyet = ht.taoTinDaDuyet(chu, kdv);
        // Hạn mức 3 tin mới/tháng/tài khoản: tin thứ 4, 5 dùng chủ khác.
        TaiKhoanSession chu2 = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinCho tinXoa = ht.taoTinMoiChoDuyet(chu2);
        ht.xoaTin(chu2, tinXoa.tinId());
        TinCho tinTuChoi = ht.taoTinMoiChoDuyet(chu2);
        ht.tuChoi(kdv, tinTuChoi.tinId(), tinTuChoi.phienBanId(), "tc-ban-can-duyet", "\"0\"", "Thiếu ảnh");
        for (UUID tinId : List.of(tinDaDuyet.tinId(), tinXoa.tinId(), tinTuChoi.tinId())) {
            assertThatThrownBy(() -> kiemDuyetService.layBanCanDuyet(kdv.rawCookie(), tinId))
                    .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
        }
    }

    @Test
    @DisplayName("Người dùng thường bị 403 và khách bị 401 ở danh sách chờ, bản cần duyệt và quyết định")
    void phanQuyen() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        assertThatThrownBy(() -> kiemDuyetService.layDanhSachChoDuyet(chu.rawCookie(), null, null))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> kiemDuyetService.layBanCanDuyet(chu.rawCookie(), tin.tinId()))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> ht.pheDuyet(chu, tin.tinId(), tin.phienBanId(), "k-1", "\"0\""))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> ht.tuChoi(chu, tin.tinId(), tin.phienBanId(), "k-2", "\"0\"", "x"))
                .isInstanceOf(InsufficientPermissionException.class);
        assertThatThrownBy(() -> kiemDuyetService.layDanhSachChoDuyet(null, null, null))
                .isInstanceOf(UnauthenticatedSessionException.class);
        assertThatThrownBy(() -> kiemDuyetService.quyetDinh(null, tin.tinId(), "k-3", "\"0\"", KiemDuyetPgTestSupport.thanPheDuyet(tin.phienBanId())))
                .isInstanceOf(UnauthenticatedSessionException.class);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')", tin.tinId()))
                .isZero();
    }

    // ---------------------------------------------------------------- phê duyệt

    @Test
    @DisplayName("Phê duyệt tin mới: phiên bản, tin, nhật ký kiểm toán, yêu cầu ghi đều đúng; replay trả đúng TinGhiKetQua cũ, không ghi thêm")
    void pheDuyetTinMoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        TinGhiKetQua ketQua = ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-1", "\"0\"");

        assertThat(ketQua).isEqualTo(new TinGhiKetQua(tin.tinId(), tin.phienBanId(), "da_duyet", 1L));

        Map<String, Object> pb = jdbcTemplate.queryForMap(
                "SELECT ket_qua_kiem_duyet, nguoi_duyet_id, thoi_diem_quyet_dinh, ly_do_tu_choi FROM se214.phien_ban_tin WHERE id = ?",
                tin.phienBanId());
        assertThat(pb.get("ket_qua_kiem_duyet")).isEqualTo("da_duyet");
        assertThat(pb.get("nguoi_duyet_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(((Timestamp) pb.get("thoi_diem_quyet_dinh")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(pb.get("ly_do_tu_choi")).isNull();

        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, ly_do_ngung_hien_thi, phien_ban_cap_nhat "
                        + "FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("da_duyet");
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("ly_do_ngung_hien_thi")).isNull();
        assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(1L);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT nguoi_thuc_hien_id, phien_ban_tin_id, ly_do, thoi_diem, "
                        + "du_lieu_truoc_sau->'truoc'->>'trangThai' AS truoc_trang_thai, "
                        + "du_lieu_truoc_sau->'truoc'->>'phienBanCapNhat' AS truoc_cap_nhat, "
                        + "jsonb_typeof(du_lieu_truoc_sau->'truoc'->'phienBanDaDuyetId') AS truoc_da_duyet_kieu, "
                        + "du_lieu_truoc_sau->'sau'->>'trangThai' AS sau_trang_thai, "
                        + "du_lieu_truoc_sau->'sau'->>'phienBanDaDuyetId' AS sau_da_duyet, "
                        + "du_lieu_truoc_sau->'sau'->>'phienBanCapNhat' AS sau_cap_nhat "
                        + "FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'",
                tin.tinId());
        assertThat(audit.get("nguoi_thuc_hien_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(audit.get("phien_ban_tin_id")).isEqualTo(tin.phienBanId());
        assertThat(audit.get("ly_do")).isNull();
        assertThat(((Timestamp) audit.get("thoi_diem")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(audit.get("truoc_trang_thai")).isEqualTo("cho_kiem_duyet");
        assertThat(audit.get("truoc_cap_nhat")).isEqualTo("0");
        assertThat(audit.get("truoc_da_duyet_kieu")).isEqualTo("null");
        assertThat(audit.get("sau_trang_thai")).isEqualTo("da_duyet");
        assertThat(audit.get("sau_da_duyet")).isEqualTo(tin.phienBanId().toString());
        assertThat(audit.get("sau_cap_nhat")).isEqualTo("1");

        Map<String, Object> yc = jdbcTemplate.queryForMap(
                "SELECT tin_ket_qua_id, phien_ban_ket_qua_id, trang_thai_ket_qua, phien_ban_cap_nhat_ket_qua, du_lieu_bam "
                        + "FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'quyet_dinh' AND ma_y_dinh = 'duyet-1'",
                kdv.taiKhoan().getId());
        assertThat(yc.get("tin_ket_qua_id")).isEqualTo(tin.tinId());
        assertThat(yc.get("phien_ban_ket_qua_id")).isEqualTo(tin.phienBanId());
        assertThat(yc.get("trang_thai_ket_qua")).isEqualTo("da_duyet");
        assertThat(((Number) yc.get("phien_ban_cap_nhat_ket_qua")).longValue()).isEqualTo(1L);
        assertThat((String) yc.get("du_lieu_bam")).isNotBlank();

        TinGhiKetQua replay = ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-1", "\"0\"");
        assertThat(replay).isEqualTo(ketQua);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tin.tinId()))
                .isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId()))
                .isEqualTo(1);
        assertThat(ht.phienBanCapNhat(tin.tinId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Phê duyệt bản sửa: gói đang tạm dừng chạy tiếp với het_han_luc = thời điểm duyệt + thời lượng còn lại, ghi sự kiện chay_tiep")
    void pheDuyetBanSuaChayTiepGoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinDaDuyet(chu, kdv);
        Duration conLai = Duration.ofHours(123);
        UUID goiId = ht.taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(conLai), "7 days");
        TinGhiKetQua sua = ht.suaTin(chu, tin.tinId(), "Tiêu đề bản sửa");
        assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'tam_dung_cho_duyet' AND het_han_luc IS NULL", goiId))
                .isEqualTo(1);
        long capNhatTruoc = ht.phienBanCapNhat(tin.tinId());

        TinGhiKetQua ketQua = ht.pheDuyet(kdv, tin.tinId(), sua.phienBanHienTaiId(), "duyet-sua", ht.etag(tin.tinId()));

        assertThat(ketQua.trangThai()).isEqualTo("da_duyet");
        assertThat(ketQua.phienBanHienTaiId()).isEqualTo(sua.phienBanHienTaiId());
        assertThat(ketQua.phienBanCapNhat()).isEqualTo(capNhatTruoc + 1);
        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("da_duyet");
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(sua.phienBanHienTaiId());
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(sua.phienBanHienTaiId());

        Map<String, Object> goi = jdbcTemplate.queryForMap(
                "SELECT trang_thai, het_han_luc, EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint AS giay FROM se214.goi_day_tin WHERE id = ?", goiId);
        assertThat(goi.get("trang_thai")).isEqualTo("dang_hoat_dong");
        assertThat(((Timestamp) goi.get("het_han_luc")).toInstant()).isEqualTo(THOI_DIEM_DUYET.plus(conLai));
        assertThat(((Number) goi.get("giay")).longValue()).isEqualTo(conLai.toSeconds());

        Map<String, Object> chayTiep = jdbcTemplate.queryForMap(
                "SELECT nguoi_thuc_hien_id, thoi_diem, EXTRACT(EPOCH FROM thoi_luong_truoc)::bigint AS truoc, "
                        + "EXTRACT(EPOCH FROM thoi_luong_sau)::bigint AS sau FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'chay_tiep'", goiId);
        assertThat(chayTiep.get("nguoi_thuc_hien_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(((Timestamp) chayTiep.get("thoi_diem")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(((Number) chayTiep.get("truoc")).longValue()).isEqualTo(conLai.toSeconds());
        assertThat(((Number) chayTiep.get("sau")).longValue()).isEqualTo(conLai.toSeconds());
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isEqualTo(2);
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'tam_dung'", goiId)).isEqualTo(1);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT du_lieu_truoc_sau->'truoc'->>'trangThai' AS truoc_trang_thai, "
                        + "du_lieu_truoc_sau->'truoc'->>'phienBanDaDuyetId' AS truoc_da_duyet, "
                        + "du_lieu_truoc_sau->'sau'->>'phienBanDaDuyetId' AS sau_da_duyet "
                        + "FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet' AND phien_ban_tin_id = ?",
                tin.tinId(), sua.phienBanHienTaiId());
        assertThat(audit.get("truoc_trang_thai")).isEqualTo("cho_kiem_duyet");
        assertThat(audit.get("truoc_da_duyet")).isEqualTo(tin.phienBanId().toString());
        assertThat(audit.get("sau_da_duyet")).isEqualTo(sua.phienBanHienTaiId().toString());
    }

    @Test
    @DisplayName("TS1-043: phê duyệt kiểm lại BR-03 trong transaction; bản thiếu ảnh da_gan hoặc loại BĐS sai bị 409, giữ chờ duyệt và gói")
    void pheDuyetKiemLaiBr03() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        Map<String, Consumer<TinCho>> cacBienThe = new LinkedHashMap<>();
        cacBienThe.put("không còn ảnh da_gan", tin ->
                jdbcTemplate.update("DELETE FROM se214.anh_tin WHERE phien_ban_tin_id = ?", tin.phienBanId()));
        cacBienThe.put("loại BĐS ngoài danh mục", tin ->
                jdbcTemplate.update("UPDATE se214.phien_ban_tin SET loai_bds = 'biet_thu' WHERE id = ?", tin.phienBanId()));
        cacBienThe.put("đối chứng: dữ liệu hợp lệ vẫn đạt", tin -> {
        });

        for (Map.Entry<String, Consumer<TinCho>> bienThe : cacBienThe.entrySet()) {
            TinCho tin = ht.taoTinMoiChoDuyet(chu);
            UUID goiId = ht.taoGoi(tin.tinId(), "tam_dung_cho_duyet", null, "2 days");
            bienThe.getValue().accept(tin);
            boolean phaiDat = bienThe.getKey().endsWith("vẫn đạt");

            if (phaiDat) {
                assertThat(ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "br03-" + UUID.randomUUID(), "\"0\"").trangThai())
                        .as(bienThe.getKey())
                        .isEqualTo("da_duyet");
                continue;
            }
            assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "br03-" + UUID.randomUUID(), "\"0\""))
                    .as(bienThe.getKey())
                    .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));

            assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'cho_kiem_duyet' "
                    + "AND phien_ban_da_duyet_id IS NULL AND phien_ban_cap_nhat = 0", tin.tinId())).as(bienThe.getKey()).isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet' AND nguoi_duyet_id IS NULL",
                    tin.phienBanId())).as(bienThe.getKey()).isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'tam_dung_cho_duyet' AND het_han_luc IS NULL", goiId))
                    .as(bienThe.getKey()).isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).as(bienThe.getKey()).isZero();
            assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tin.tinId()))
                    .as(bienThe.getKey()).isZero();
            assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId()))
                    .as(bienThe.getKey()).isZero();

            // Từ chối vẫn được phép với bản không đạt BR-03.
            assertThat(ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "br03-tc-" + UUID.randomUUID(), "\"0\"", "Thiếu dữ liệu").trangThai())
                    .isEqualTo("bi_tu_choi");
        }
    }

    // ---------------------------------------------------------------- từ chối

    @Test
    @DisplayName("Từ chối bắt buộc có lý do: thiếu, null, rỗng, toàn khoảng trắng, không phải chuỗi, 1001 ký tự đều bị 400 trường lyDo và không ghi gì")
    void tuChoiPhaiCoLyDo() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        Map<String, Object> thieuLyDo = new LinkedHashMap<>(KiemDuyetPgTestSupport.thanTuChoi(tin.phienBanId(), "x"));
        thieuLyDo.remove("lyDo");
        Map<String, Object> lyDoNull = new HashMap<>(KiemDuyetPgTestSupport.thanTuChoi(tin.phienBanId(), "x"));
        lyDoNull.put("lyDo", null);
        Map<String, Object> lyDoSo = new HashMap<>(KiemDuyetPgTestSupport.thanTuChoi(tin.phienBanId(), "x"));
        lyDoSo.put("lyDo", 123);
        List<Map<String, Object>> cacBodyLoi = List.of(
                thieuLyDo,
                lyDoNull,
                lyDoSo,
                KiemDuyetPgTestSupport.thanTuChoi(tin.phienBanId(), ""),
                KiemDuyetPgTestSupport.thanTuChoi(tin.phienBanId(), "   \t\n "),
                KiemDuyetPgTestSupport.thanTuChoi(tin.phienBanId(), "a".repeat(1001))
        );
        int stt = 0;
        for (Map<String, Object> body : cacBodyLoi) {
            String maYDinh = "tc-loi-" + stt++;
            assertThatThrownBy(() -> kiemDuyetService.quyetDinh(kdv.rawCookie(), tin.tinId(), maYDinh, "\"0\"", body))
                    .as("body=" + body)
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("lyDo"));
        }

        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet'", tin.phienBanId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tin.tinId())).isZero();
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId())).isZero();

        // Đúng 1000 ký tự sau khi trim là hợp lệ.
        String lyDo1000 = "b".repeat(1000);
        ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-1000", "\"0\"", "  " + lyDo1000 + "  ");
        assertThat(jdbcTemplate.queryForObject("SELECT ly_do_tu_choi FROM se214.phien_ban_tin WHERE id = ?", String.class, tin.phienBanId()))
                .isEqualTo(lyDo1000);
    }

    @Test
    @DisplayName("QuyetDinhRequest additionalProperties:false: trường lạ hoặc trường tự khai (trangThai, nguoiDuyetId...) bị 400, không ghi gì")
    void quyetDinhChanTruongLa() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        Map<String, Object> cacTruongLa = new LinkedHashMap<>();
        cacTruongLa.put("trangThai", "da_duyet");
        cacTruongLa.put("nguoiDuyetId", kdv.taiKhoan().getId().toString());
        cacTruongLa.put("phienBanCapNhat", 0);
        cacTruongLa.put("tinId", tin.tinId().toString());
        int stt = 0;
        for (Map.Entry<String, Object> truong : cacTruongLa.entrySet()) {
            Map<String, Object> body = new LinkedHashMap<>(KiemDuyetPgTestSupport.thanPheDuyet(tin.phienBanId()));
            body.put(truong.getKey(), truong.getValue());
            String maYDinh = "la-" + stt++;
            assertThatThrownBy(() -> kiemDuyetService.quyetDinh(kdv.rawCookie(), tin.tinId(), maYDinh, "\"0\"", body))
                    .as(truong.getKey())
                    .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                            assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly(truong.getKey()));
        }
        Map<String, Object> thieuQuyetDinh = new LinkedHashMap<>(KiemDuyetPgTestSupport.thanPheDuyet(tin.phienBanId()));
        thieuQuyetDinh.remove("quyetDinh");
        assertThatThrownBy(() -> kiemDuyetService.quyetDinh(kdv.rawCookie(), tin.tinId(), "thieu-qd", "\"0\"", thieuQuyetDinh))
                .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                        assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("quyetDinh"));
        assertThatThrownBy(() -> kiemDuyetService.quyetDinh(kdv.rawCookie(), tin.tinId(), "thieu-body", "\"0\"", null))
                .isInstanceOf(AuthValidationException.class);

        assertThat(ht.phienBanCapNhat(tin.tinId())).isZero();
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'quyet_dinh'", kdv.taiKhoan().getId())).isZero();
    }

    @Test
    @DisplayName("Từ chối tin mới: tin thành bi_tu_choi, lý do được trim và lưu vào phiên bản và nhật ký; replay không ghi thêm")
    void tuChoiTinMoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        TinGhiKetQua ketQua = ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-1", "\"0\"", "  Ảnh không đúng thực tế  ");

        assertThat(ketQua).isEqualTo(new TinGhiKetQua(tin.tinId(), tin.phienBanId(), "bi_tu_choi", 1L));

        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("bi_tu_choi");
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isNull();
        assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(1L);
        Map<String, Object> pb = jdbcTemplate.queryForMap(
                "SELECT ket_qua_kiem_duyet, nguoi_duyet_id, thoi_diem_quyet_dinh, ly_do_tu_choi FROM se214.phien_ban_tin WHERE id = ?", tin.phienBanId());
        assertThat(pb.get("ket_qua_kiem_duyet")).isEqualTo("bi_tu_choi");
        assertThat(pb.get("nguoi_duyet_id")).isEqualTo(kdv.taiKhoan().getId());
        assertThat(((Timestamp) pb.get("thoi_diem_quyet_dinh")).toInstant()).isEqualTo(THOI_DIEM_DUYET);
        assertThat(pb.get("ly_do_tu_choi")).isEqualTo("Ảnh không đúng thực tế");
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT ly_do, du_lieu_truoc_sau->'sau'->>'trangThai' AS sau_trang_thai FROM se214.nhat_ky_kiem_toan "
                        + "WHERE tin_id = ? AND hanh_dong = 'tu_choi' AND phien_ban_tin_id = ?", tin.tinId(), tin.phienBanId());
        assertThat(audit.get("ly_do")).isEqualTo("Ảnh không đúng thực tế");
        assertThat(audit.get("sau_trang_thai")).isEqualTo("bi_tu_choi");

        TinGhiKetQua replay = ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-1", "\"0\"", "Ảnh không đúng thực tế");
        assertThat(replay).isEqualTo(ketQua);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tin.tinId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Q04–Q05/TS1-034: từ chối bản sửa -> tin bi_tu_choi (ngừng công khai), giữ phien_ban_da_duyet_id cũ, gói giữ nguyên tam_dung_cho_duyet")
    void tuChoiBanSuaChuyenBiTuChoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinDaDuyet(chu, kdv);
        Duration conLai = Duration.ofDays(3);
        UUID goiId = ht.taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(conLai), "3 days");
        TinGhiKetQua sua = ht.suaTin(chu, tin.tinId(), "Tiêu đề bản sửa bị từ chối");
        long capNhatTruoc = ht.phienBanCapNhat(tin.tinId());

        TinGhiKetQua ketQua = ht.tuChoi(kdv, tin.tinId(), sua.phienBanHienTaiId(), "tc-sua", ht.etag(tin.tinId()), "Giá không hợp lý");

        assertThat(ketQua).isEqualTo(new TinGhiKetQua(tin.tinId(), sua.phienBanHienTaiId(), "bi_tu_choi", capNhatTruoc + 1));
        Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId());
        assertThat(tinRow.get("trang_thai")).isEqualTo("bi_tu_choi");
        assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(tin.phienBanId());
        assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(sua.phienBanHienTaiId());
        assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(capNhatTruoc + 1);
        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'bi_tu_choi' AND ly_do_tu_choi = 'Giá không hợp lý'",
                sua.phienBanHienTaiId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'da_duyet'", tin.phienBanId())).isEqualTo(1);

        Map<String, Object> goi = jdbcTemplate.queryForMap(
                "SELECT trang_thai, het_han_luc, EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint AS giay FROM se214.goi_day_tin WHERE id = ?", goiId);
        assertThat(goi.get("trang_thai")).isEqualTo("tam_dung_cho_duyet");
        assertThat(goi.get("het_han_luc")).isNull();
        assertThat(((Number) goi.get("giay")).longValue()).isEqualTo(conLai.toSeconds());
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'chay_tiep'", goiId)).isZero();

        // Tin không còn chờ duyệt: không còn trong danh sách chờ.
        Set<UUID> trongDanhSach = new HashSet<>();
        docTatCaDanhSachCho(kdv, 50).forEach(t -> trongDanhSach.add(t.id()));
        assertThat(trongDanhSach).doesNotContain(tin.tinId());
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT du_lieu_truoc_sau->'sau'->>'trangThai' AS sau_trang_thai, du_lieu_truoc_sau->'sau'->>'phienBanDaDuyetId' AS sau_da_duyet "
                        + "FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tin.tinId());
        assertThat(audit.get("sau_trang_thai")).isEqualTo("bi_tu_choi");
        assertThat(audit.get("sau_da_duyet")).isEqualTo(tin.phienBanId().toString());

        // TS1-034: chủ sửa lại rồi KDV duyệt -> gói chạy lại đủ thời lượng từ thời điểm duyệt.
        TinGhiKetQua suaLai = ht.suaTin(chu, tin.tinId(), "Tiêu đề đã chỉnh giá");
        ht.pheDuyet(kdv, tin.tinId(), suaLai.phienBanHienTaiId(), "duyet-sau-tc", ht.etag(tin.tinId()));
        Map<String, Object> goiSauDuyet = jdbcTemplate.queryForMap(
                "SELECT trang_thai, het_han_luc FROM se214.goi_day_tin WHERE id = ?", goiId);
        assertThat(goiSauDuyet.get("trang_thai")).isEqualTo("dang_hoat_dong");
        assertThat(((Timestamp) goiSauDuyet.get("het_han_luc")).toInstant()).isEqualTo(THOI_DIEM_DUYET.plus(conLai));
    }

    @Test
    @DisplayName("Từ chối tin mới không đụng tới gói đẩy tin và không có sự kiện chay_tiep")
    void tuChoiTinMoiKhongDungGoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        UUID goiId = ht.taoGoi(tin.tinId(), "tam_dung_cho_duyet", null, "2 days");

        ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-1", "\"0\"", "Không hợp lệ");

        assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'tam_dung_cho_duyet' AND het_han_luc IS NULL", goiId)).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isZero();
    }

    // ---------------------------------------------------------------- điều kiện và xung đột

    @Test
    @DisplayName("Quyết định lần hai: ETag cũ -> 412 PHIEN_BAN_CU; ETag mới -> 409 TRANG_THAI_KHONG_HOP_LE; không ghi đè")
    void quyetDinhLanHai() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-1", "\"0\"");

        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-2", "\"1\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
        assertThatThrownBy(() -> ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-2", "\"1\"", "Muộn rồi"))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
        assertThatThrownBy(() -> ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-3", "\"0\"", "Muộn rồi"))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));

        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')", tin.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'da_duyet' AND phien_ban_cap_nhat = 1", tin.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId())).isEqualTo(1);
    }

    @Test
    @DisplayName("TS1-046: tin đã xóa (luồng xóa thật) hoặc bị gỡ -> 409 TRANG_THAI_KHONG_HOP_LE, không hồi sinh, phiên bản không đổi")
    void tinDaXoaVaBiGo() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tinXoa = ht.taoTinMoiChoDuyet(chu);
        ht.xoaTin(chu, tinXoa.tinId());
        TinCho tinGo = ht.taoTinMoiChoDuyet(chu);
        jdbcTemplate.update("UPDATE se214.tin_bat_dong_san SET trang_thai = 'bi_go_vi_pham', ly_do_ngung_hien_thi = 'Vi phạm' WHERE id = ?", tinGo.tinId());

        assertThatThrownBy(() -> ht.pheDuyet(kdv, tinXoa.tinId(), tinXoa.phienBanId(), "duyet-xoa", ht.etag(tinXoa.tinId())))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
        assertThatThrownBy(() -> ht.tuChoi(kdv, tinXoa.tinId(), tinXoa.phienBanId(), "tc-xoa", ht.etag(tinXoa.tinId()), "x"))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tinGo.tinId(), tinGo.phienBanId(), "duyet-go", ht.etag(tinGo.tinId())))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
        // ETag trước khi xóa đã cũ -> 412.
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tinXoa.tinId(), tinXoa.phienBanId(), "duyet-xoa-cu", "\"0\""))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));

        assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'da_xoa'", tinXoa.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id IN (?, ?) AND ket_qua_kiem_duyet = 'cho_kiem_duyet' AND nguoi_duyet_id IS NULL",
                tinXoa.phienBanId(), tinGo.phienBanId())).isEqualTo(2);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id IN (?, ?) AND hanh_dong IN ('phe_duyet', 'tu_choi')",
                tinXoa.tinId(), tinGo.tinId())).isZero();
    }

    @Test
    @DisplayName("Chủ tin gửi bản mới khi KDV còn giữ bản cũ: ETag cũ hoặc phienBanDaXemId cũ đều -> 412 PHIEN_BAN_CU; bản mới duyệt bình thường")
    void banMoiLamBanDaXemLoiThoi() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);
        String etagKdvDangGiu = ht.etag(tin.tinId());
        ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "tc-1", etagKdvDangGiu, "Thiếu giấy tờ");
        TinGhiKetQua suaLai = ht.suaTin(chu, tin.tinId(), "Tiêu đề đã bổ sung giấy tờ");

        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-cu-1", etagKdvDangGiu))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));
        // Tải lại ETag nhưng vẫn gửi phienBanDaXemId của bản cũ.
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-cu-2", ht.etag(tin.tinId())))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));
        // phienBanDaXemId của tin khác cũng là bản đã xem sai.
        TinCho tinKhac = ht.taoTinMoiChoDuyet(chu);
        assertThatThrownBy(() -> ht.tuChoi(kdv, tin.tinId(), tinKhac.phienBanId(), "tc-cu-3", ht.etag(tin.tinId()), "x"))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU"));

        TinGhiKetQua ketQua = ht.pheDuyet(kdv, tin.tinId(), suaLai.phienBanHienTaiId(), "duyet-moi", ht.etag(tin.tinId()));
        assertThat(ketQua.trangThai()).isEqualTo("da_duyet");
        assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND phien_ban_da_duyet_id = ?", tin.tinId(), suaLai.phienBanHienTaiId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet'", tinKhac.phienBanId())).isEqualTo(1);
    }

    @Test
    @DisplayName("If-Match: thiếu -> 428 THIEU_IF_MATCH, lệch -> 412 PHIEN_BAN_CU, không phải ETag mạnh -> 400; không ghi gì")
    void ifMatch() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "k-1", null))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> {
                    assertThat(ex.getMaLoi()).isEqualTo("THIEU_IF_MATCH");
                    assertThat(ex.laThieuIfMatch()).isTrue();
                });
        assertThatThrownBy(() -> ht.tuChoi(kdv, tin.tinId(), tin.phienBanId(), "k-2", "  ", "x"))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> assertThat(ex.laThieuIfMatch()).isTrue());
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "k-3", "\"7\""))
                .isInstanceOfSatisfying(ListingPreconditionException.class, ex -> {
                    assertThat(ex.getMaLoi()).isEqualTo("PHIEN_BAN_CU");
                    assertThat(ex.laThieuIfMatch()).isFalse();
                });
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "k-4", "W/\"0\""))
                .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                        assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("If-Match"));
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), null, "\"0\""))
                .isInstanceOfSatisfying(AuthValidationException.class, ex ->
                        assertThat(ex.getTruongLoi()).extracting(f -> f.truong()).containsExactly("Idempotency-Key"));
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "k".repeat(256), "\"0\""))
                .isInstanceOf(AuthValidationException.class);

        assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet'", tin.phienBanId())).isEqualTo(1);
        assertThat(ht.phienBanCapNhat(tin.tinId())).isZero();
    }

    @Test
    @DisplayName("Tin không tồn tại -> 404 KHONG_TIM_THAY")
    void tinKhongTonTai() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        assertThatThrownBy(() -> ht.pheDuyet(kdv, UUID.randomUUID(), tin.phienBanId(), "k-1", "\"0\""))
                .isInstanceOf(ListingNotFoundException.class);
        assertThatThrownBy(() -> ht.tuChoi(kdv, UUID.randomUUID(), tin.phienBanId(), "k-2", "\"0\"", "x"))
                .isInstanceOf(ListingNotFoundException.class);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')",
                tin.tinId())).isZero();
    }

    @Test
    @DisplayName("Idempotency-Key: cùng khóa cùng nội dung trả lại kết quả cũ; khác quyết định, khác tin hoặc khác If-Match -> 409 MA_Y_DINH_KHAC_NOI_DUNG")
    void idempotency() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tinA = ht.taoTinMoiChoDuyet(chu);
        TinCho tinB = ht.taoTinMoiChoDuyet(chu);

        TinGhiKetQua duyetA = ht.pheDuyet(kdv, tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"0\"");
        assertThat(ht.pheDuyet(kdv, tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"0\"")).isEqualTo(duyetA);
        assertThatThrownBy(() -> ht.tuChoi(kdv, tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"0\"", "x"))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tinB.tinId(), tinB.phienBanId(), "khoa-chung", "\"0\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));
        assertThatThrownBy(() -> ht.pheDuyet(kdv, tinA.tinId(), tinA.phienBanId(), "khoa-chung", "\"1\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));

        TinGhiKetQua tuChoiB = ht.tuChoi(kdv, tinB.tinId(), tinB.phienBanId(), "khoa-tc", "\"0\"", "Lý do một");
        assertThat(ht.tuChoi(kdv, tinB.tinId(), tinB.phienBanId(), "khoa-tc", "\"0\"", "  Lý do một ")).isEqualTo(tuChoiB);
        assertThatThrownBy(() -> ht.tuChoi(kdv, tinB.tinId(), tinB.phienBanId(), "khoa-tc", "\"0\"", "Lý do hai"))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("MA_Y_DINH_KHAC_NOI_DUNG"));

        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tinA.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tinB.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'quyet_dinh'", kdv.taiKhoan().getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Cùng Idempotency-Key của hai kiểm duyệt viên khác nhau là hai ý định độc lập")
    void maYDinhTheoTaiKhoan() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv1 = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TaiKhoanSession kdv2 = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tin = ht.taoTinMoiChoDuyet(chu);

        ht.pheDuyet(kdv1, tin.tinId(), tin.phienBanId(), "trung-khoa", "\"0\"");
        assertThatThrownBy(() -> ht.pheDuyet(kdv2, tin.tinId(), tin.phienBanId(), "trung-khoa", "\"1\""))
                .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
    }

    // ---------------------------------------------------------------- tiện ích

    private static TinQuanLy timTin(List<TinQuanLy> danhSach, UUID tinId) {
        return danhSach.stream().filter(t -> t.id().equals(tinId)).findFirst().orElseThrow();
    }

    private List<TinQuanLy> docTatCaDanhSachCho(TaiKhoanSession kdv, int kichThuoc) {
        List<TinQuanLy> tatCa = new ArrayList<>();
        for (int trang = 0; trang < 100000; trang++) {
            TrangTinQuanLy ketQua = kiemDuyetService.layDanhSachChoDuyet(kdv.rawCookie(), String.valueOf(trang), String.valueOf(kichThuoc));
            assertThat(ketQua.noiDung().size()).isLessThanOrEqualTo(kichThuoc);
            if (ketQua.noiDung().isEmpty()) {
                break;
            }
            tatCa.addAll(ketQua.noiDung());
        }
        return tatCa;
    }
}
