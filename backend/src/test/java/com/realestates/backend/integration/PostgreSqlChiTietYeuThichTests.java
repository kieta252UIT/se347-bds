package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.detail.application.ChiTietTinService;
import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.detail.dto.TinCongKhai;
import com.realestates.backend.detail.infrastructure.PostgreSqlChiTietTinRepository;
import com.realestates.backend.favorite.application.YeuThichService;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.favorite.infrastructure.PostgreSqlYeuThichRepository;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.search.TimKiemPgTestSupport;
import com.realestates.backend.search.TimKiemPgTestSupport.NoiDung;
import com.realestates.backend.search.TimKiemPgTestSupport.TaiKhoanSession;
import com.realestates.backend.search.TimKiemPgTestSupport.TinTao;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.dto.AnhHienThi;
import com.realestates.backend.search.dto.TinTomTat;
import com.realestates.backend.search.dto.TrangTinCongKhai;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

/** Chi tiết tin (kèm liên hệ) và yêu thích trên PostgreSQL thật; dựng dữ liệu bằng luồng thật qua TimKiemPgTestSupport. */
@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlChiTietYeuThichTests {

    private static final Instant GOC = Instant.parse("2026-11-01T00:00:00Z");

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
    private TinDangService tinDangService;
    @Autowired
    private QuanLyTinService quanLyTinService;
    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    @Autowired
    private ChiTietTinRepository chiTietTinRepository;
    @Autowired
    private YeuThichRepository yeuThichRepository;
    @Autowired
    private ChiTietTinService chiTietTinService;
    @Autowired
    private YeuThichService yeuThichService;
    @Autowired
    private TimKiemTinService timKiemTinService;

    private TimKiemPgTestSupport ht;
    /** Khu vực duy nhất cho mỗi test để cô lập khỏi dữ liệu khác. */
    private final String kv = "CTTEST-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assertThat(chiTietTinRepository).isInstanceOf(PostgreSqlChiTietTinRepository.class);
        assertThat(yeuThichRepository).isInstanceOf(PostgreSqlYeuThichRepository.class);
        ht = new TimKiemPgTestSupport(
                jdbcTemplate, transactionManager, taiKhoanRepository, phienXacThucRepository, anhTinRepository,
                tinDangService, quanLyTinService, sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    // ------------------------------------------------------------------ tiện ích

    private TinTao tinDaDuyet() {
        return ht.taoTinDaDuyet(NoiDung.banMau(kv), GOC);
    }

    private TinCongKhai chiTiet(String cookie, TinTao tin) {
        return chiTietTinService.xem(cookie, tin.tinId().toString());
    }

    private static void phaiLaKhongTimThay(ThrowingCallable viec) {
        assertThatThrownBy(viec).isInstanceOf(ListingNotFoundException.class);
    }

    private void luu(TaiKhoanSession nguoi, TinTao tin) {
        yeuThichService.luu(nguoi.rawCookie(), tin.tinId().toString());
    }

    private void bo(TaiKhoanSession nguoi, TinTao tin) {
        yeuThichService.bo(nguoi.rawCookie(), tin.tinId().toString());
    }

    private TrangTinCongKhai danhSach(TaiKhoanSession nguoi, String trang, String kichThuoc) {
        return yeuThichService.danhSach(nguoi.rawCookie(), trang, kichThuoc);
    }

    private static List<UUID> ids(TrangTinCongKhai trang) {
        return trang.noiDung().stream().map(TinTomTat::id).toList();
    }

    private static void kiemTraLoiTruong(ThrowingCallable viec, String truong) {
        assertThatThrownBy(viec)
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> assertThat(((AuthValidationException) ex).getTruongLoi())
                        .extracting(FieldErrorItem::truong)
                        .contains(truong));
    }

    /** Dựng các tin không công khai: chờ duyệt, bị từ chối, đã xóa, bị gỡ, chủ bị khóa. */
    private record TinAn(TinTao choDuyet, TinTao biTuChoi, TinTao daXoa, TinTao biGo, TinTao chuBiKhoa) {
        List<TinTao> tatCa() {
            return List.of(choDuyet, biTuChoi, daXoa, biGo, chuBiKhoa);
        }
    }

    private TinAn dungTinAn() {
        TinTao choDuyet = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        TinTao biTuChoi = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        ht.tuChoiPhienBanHienTai(biTuChoi.tinId());
        TinTao daXoa = tinDaDuyet();
        ht.xoaTin(daXoa);
        TinTao biGo = tinDaDuyet();
        ht.goViPham(biGo.tinId());
        TinTao chuBiKhoa = tinDaDuyet();
        ht.khoaChu(chuBiKhoa);
        return new TinAn(choDuyet, biTuChoi, daXoa, biGo, chuBiKhoa);
    }

    // ------------------------------------------------------------------ chi tiết

    @Test
    @DisplayName("Chi tiết tin công khai: TinCongKhai đủ trường, giá quy đổi tong, ảnh AnhHienThi, liên hệ chủ tin")
    void chiTietDayDu() {
        NoiDung noiDung = NoiDung.banMau(kv).voiBan("can_ho", "30000000", "vnd_m2", "50");
        TinTao tin = ht.taoTinDaDuyet(noiDung, GOC);
        ht.datHoTenVaSoDienThoai(tin.chu().taiKhoan().getId(), "Nguyễn Văn Chủ", "0912345678");

        TinCongKhai kq = chiTiet(null, tin);

        assertThat(kq.id()).isEqualTo(tin.tinId());
        assertThat(kq.trangThai()).isEqualTo("da_duyet");
        assertThat(kq.ngayDang()).isEqualTo(GOC);
        assertThat(kq.noiDung().id()).isEqualTo(tin.phienBanId());
        assertThat(kq.noiDung().tieuDe()).isEqualTo("Căn hộ 2 phòng ngủ view sông");
        assertThat(kq.noiDung().moTa()).isEqualTo("Kết cấu 1 trệt 3 lầu, đường rộng 8m.");
        assertThat(kq.noiDung().loaiGiaoDich()).isEqualTo("ban");
        assertThat(kq.noiDung().loaiBds()).isEqualTo("can_ho");
        assertThat(kq.noiDung().giaGoc()).isEqualTo("30000000");
        assertThat(kq.noiDung().donViGia()).isEqualTo("vnd_m2");
        assertThat(kq.noiDung().dienTichM2()).isEqualTo("50");
        assertThat(kq.noiDung().diaChi()).isEqualTo("18 Lý Tự Trọng, Quận 1");
        assertThat(kq.noiDung().khuVuc()).isEqualTo(kv);
        assertThat(kq.noiDung().viDo()).isEqualTo("10.7772");
        assertThat(kq.noiDung().kinhDo()).isEqualTo("106.7015");
        assertThat(kq.noiDung().giaQuyDoiHienThi().giaTri()).isEqualTo("1500000000.00");
        assertThat(kq.noiDung().giaQuyDoiHienThi().donVi()).isEqualTo("vnd_tong");
        assertThat(kq.noiDung().anh()).containsExactly(AnhHienThi.tu(tin.anhId(), 1));
        assertThat(kq.lienHe().hoTen()).isEqualTo("Nguyễn Văn Chủ");
        assertThat(kq.lienHe().soDienThoai()).isEqualTo("0912345678");
        assertThat(kq.toString()).doesNotContain(ht.emailTaiKhoan(tin.chu().taiKhoan().getId()));
    }

    @Test
    @DisplayName("Ảnh trả về theo thứ tự thu_tu tăng dần của phiên bản đã duyệt")
    void chiTietAnhDungThuTu() {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tin = ht.taoTinChoDuyet(chu, NoiDung.banMau(kv), 3);
        ht.duyetPhienBanHienTai(tin.tinId());

        List<Map<String, Object>> mong = jdbcTemplate.queryForList(
                "SELECT id, thu_tu FROM se214.anh_tin WHERE phien_ban_tin_id = ? AND trang_thai = 'da_gan' ORDER BY thu_tu",
                tin.phienBanId()
        );
        List<AnhHienThi> anh = chiTiet(null, tin).noiDung().anh();

        assertThat(mong).hasSize(3);
        assertThat(anh).containsExactlyElementsOf(mong.stream()
                .map(d -> AnhHienThi.tu((UUID) d.get("id"), ((Number) d.get("thu_tu")).intValue()))
                .toList());
        assertThat(anh).extracting(AnhHienThi::thuTu).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Liên hệ: họ tên/số điện thoại rỗng trong DB trả null")
    void lienHeTruongNull() {
        TinTao tin = tinDaDuyet();
        ht.datHoTenVaSoDienThoai(tin.chu().taiKhoan().getId(), null, null);

        TinCongKhai kq = chiTiet(null, tin);

        assertThat(kq.lienHe().hoTen()).isNull();
        assertThat(kq.lienHe().soDienThoai()).isNull();
    }

    @Test
    @DisplayName("BR-01: chờ duyệt, bị từ chối, đã xóa, bị gỡ, chủ bị khóa, id ngẫu nhiên hoặc sai định dạng đều 404")
    void chiTietKhongCongKhai() {
        TinAn an = dungTinAn();
        for (TinTao tin : an.tatCa()) {
            phaiLaKhongTimThay(() -> chiTiet(null, tin));
        }
        phaiLaKhongTimThay(() -> chiTietTinService.xem(null, UUID.randomUUID().toString()));
        phaiLaKhongTimThay(() -> chiTietTinService.xem(null, "khong-phai-uuid"));
        phaiLaKhongTimThay(() -> chiTietTinService.xem(null, "1-1-1-1-1"));
    }

    @Test
    @DisplayName("BR-01: tin sửa chờ duyệt (trạng thái cho_duyet) là 404; duyệt bản sửa thì hiện nội dung mới")
    void chiTietBanSuaChoDuyet() {
        TinTao tin = tinDaDuyet();
        ht.suaTin(tin, NoiDung.banMau(kv).voiTieuDe("Tiêu đề mới chờ duyệt"));

        phaiLaKhongTimThay(() -> chiTiet(null, tin));

        ht.duyetPhienBanHienTai(tin.tinId());
        assertThat(chiTiet(null, tin).noiDung().tieuDe()).isEqualTo("Tiêu đề mới chờ duyệt");
    }

    // ------------------------------------------------------------------ yêu thích: lưu / bỏ

    @Test
    @DisplayName("Lưu hai lần chỉ có một dòng và giữ nguyên luu_luc ban đầu")
    void luuHaiLan() throws InterruptedException {
        TinTao tin = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        luu(nguoi, tin);
        Instant lanDau = ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId());
        Thread.sleep(50);
        luu(nguoi, tin);

        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(1);
        assertThat(ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(lanDau);
    }

    @Test
    @DisplayName("Lưu tin không công khai (kể cả chủ bị khóa), id ngẫu nhiên hoặc sai định dạng: 404 và không có dòng nào")
    void luuTinKhongCongKhai() {
        TinAn an = dungTinAn();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        for (TinTao tin : an.tatCa()) {
            phaiLaKhongTimThay(() -> luu(nguoi, tin));
            assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isZero();
        }
        phaiLaKhongTimThay(() -> yeuThichService.luu(nguoi.rawCookie(), UUID.randomUUID().toString()));
        phaiLaKhongTimThay(() -> yeuThichService.luu(nguoi.rawCookie(), "khong-phai-uuid"));
    }

    @Test
    @DisplayName("Đã lưu rồi tin bị ẩn: PUT lại trả 404, dòng cũ và luu_luc giữ nguyên")
    void luuLaiTinDaAn() {
        TinTao tin = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        luu(nguoi, tin);
        Instant luuLuc = ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId());
        ht.goViPham(tin.tinId());

        phaiLaKhongTimThay(() -> luu(nguoi, tin));
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(1);
        assertThat(ht.luuLucYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isEqualTo(luuLuc);
    }

    @Test
    @DisplayName("Bỏ yêu thích idempotent: bỏ hai lần, chưa từng lưu, tin đã xóa, id ngẫu nhiên hoặc sai định dạng đều êm")
    void boYeuThich() {
        TinTao tin = tinDaDuyet();
        TinTao chuaLuu = tinDaDuyet();
        TinTao daXoa = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        luu(nguoi, tin);
        luu(nguoi, daXoa);
        ht.xoaTin(daXoa);

        bo(nguoi, tin);
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isZero();
        assertThatCode(() -> bo(nguoi, tin)).doesNotThrowAnyException();
        assertThatCode(() -> bo(nguoi, chuaLuu)).doesNotThrowAnyException();
        assertThatCode(() -> bo(nguoi, daXoa)).doesNotThrowAnyException();
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), daXoa.tinId())).isZero();
        assertThatCode(() -> yeuThichService.bo(nguoi.rawCookie(), UUID.randomUUID().toString())).doesNotThrowAnyException();
        assertThatCode(() -> yeuThichService.bo(nguoi.rawCookie(), "khong-phai-uuid")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Khách nhận 401, kiểm duyệt viên và quản trị viên nhận 403 ở cả ba thao tác yêu thích")
    void phanQuyenYeuThich() {
        TinTao tin = tinDaDuyet();
        String id = tin.tinId().toString();
        String kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN).rawCookie();
        String qtv = ht.taoTaiKhoan(VaiTroTaiKhoan.QUAN_TRI_VIEN).rawCookie();

        assertThatThrownBy(() -> yeuThichService.luu(null, id)).isInstanceOf(UnauthenticatedSessionException.class);
        assertThatThrownBy(() -> yeuThichService.bo(null, id)).isInstanceOf(UnauthenticatedSessionException.class);
        assertThatThrownBy(() -> yeuThichService.danhSach(null, null, null)).isInstanceOf(UnauthenticatedSessionException.class);
        for (String cookie : List.of(kdv, qtv)) {
            assertThatThrownBy(() -> yeuThichService.luu(cookie, id)).isInstanceOf(InsufficientPermissionException.class);
            assertThatThrownBy(() -> yeuThichService.bo(cookie, id)).isInstanceOf(InsufficientPermissionException.class);
            assertThatThrownBy(() -> yeuThichService.danhSach(cookie, null, null)).isInstanceOf(InsufficientPermissionException.class);
        }
    }

    @Test
    @DisplayName("Tài khoản bị khóa sau khi đăng nhập: PUT bị chặn khi kiểm lại quyền, không ghi dòng nào")
    void luuKhiTaiKhoanBiKhoa() {
        TinTao tin = tinDaDuyet();
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        ht.khoaTaiKhoan(nguoi.taiKhoan().getId());

        assertThatThrownBy(() -> luu(nguoi, tin)).isNotInstanceOf(ListingNotFoundException.class);
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), tin.tinId())).isZero();
    }

    // ------------------------------------------------------------------ yêu thích: danh sách

    @Test
    @DisplayName("Danh sách rỗng khi chưa lưu gì: trang 0, kichThuoc 20, tongSo 0")
    void danhSachRong() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        TrangTinCongKhai kq = danhSach(nguoi, null, null);

        assertThat(kq.noiDung()).isEmpty();
        assertThat(kq.trang()).isZero();
        assertThat(kq.kichThuoc()).isEqualTo(20);
        assertThat(kq.tongSo()).isZero();
    }

    @Test
    @DisplayName("Phân trang OFFSET với nhiều luu_luc bằng nhau: luu_luc DESC rồi tin_id ASC, không trùng, không sót")
    void danhSachPhanTrangLuuLucBangNhau() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        List<TinTao> tins = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            tins.add(tinDaDuyet());
        }
        // 3 nhóm, mỗi nhóm 3 tin cùng luu_luc.
        for (int i = 0; i < tins.size(); i++) {
            ht.chenYeuThich(nguoi.taiKhoan().getId(), tins.get(i).tinId(), GOC.plusSeconds(60L * (i / 3)));
        }
        // PostgreSQL so sánh uuid theo chuỗi hex, không theo UUID.compareTo của Java.
        List<UUID> mong = tins.stream()
                .sorted(Comparator
                        .comparing((TinTao t) -> ht.luuLucYeuThich(nguoi.taiKhoan().getId(), t.tinId()))
                        .reversed()
                        .thenComparing(t -> t.tinId().toString()))
                .map(TinTao::tinId)
                .toList();

        for (int kichThuoc : new int[] {1, 2, 4, 9, 50}) {
            List<UUID> nhanDuoc = new ArrayList<>();
            for (int trang = 0; trang * kichThuoc < 9; trang++) {
                TrangTinCongKhai kq = danhSach(nguoi, String.valueOf(trang), String.valueOf(kichThuoc));
                assertThat(kq.tongSo()).isEqualTo(9);
                assertThat(kq.noiDung().size()).isLessThanOrEqualTo(kichThuoc);
                nhanDuoc.addAll(ids(kq));
            }
            assertThat(nhanDuoc).as("kichThuoc=%d", kichThuoc).containsExactlyElementsOf(mong);
            assertThat(new HashSet<>(nhanDuoc)).hasSameSizeAs(nhanDuoc);
        }
        TrangTinCongKhai quaCuoi = danhSach(nguoi, "5", "2");
        assertThat(quaCuoi.noiDung()).isEmpty();
        assertThat(quaCuoi.tongSo()).isEqualTo(9);
    }

    @Test
    @DisplayName("Cô lập giữa hai người dùng: mỗi người chỉ thấy yêu thích của mình")
    void danhSachCoLap() {
        TaiKhoanSession a = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession b = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tinA = tinDaDuyet();
        TinTao tinB = tinDaDuyet();
        TinTao chung = tinDaDuyet();
        luu(a, tinA);
        luu(a, chung);
        luu(b, tinB);
        luu(b, chung);

        assertThat(ids(danhSach(a, null, null))).containsExactlyInAnyOrder(tinA.tinId(), chung.tinId());
        assertThat(ids(danhSach(b, null, null))).containsExactlyInAnyOrder(tinB.tinId(), chung.tinId());
        bo(a, chung);
        assertThat(ids(danhSach(b, null, null))).contains(chung.tinId());
        assertThat(ids(danhSach(a, null, null))).containsExactly(tinA.tinId());
    }

    @Test
    @DisplayName("Mục yêu thích là TinTomTat giống hệt kết quả tìm kiếm (cheDoGia tong)")
    void danhSachTinGiongTimKiem() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tin = ht.taoTinDaDuyet(NoiDung.banMau(kv).voiGia("2500000000").voiDienTich("68.5"), GOC);
        luu(nguoi, tin);

        TinTomTat muc = danhSach(nguoi, null, null).noiDung().get(0);
        Map<String, String[]> thamSo = new LinkedHashMap<>();
        thamSo.put("khuVuc", new String[] {kv});
        TinTomTat timThay = timKiemTinService.timKiem(null, thamSo).tuNhien().noiDung().stream()
                .filter(t -> t.id().equals(tin.tinId())).findFirst().orElseThrow();

        assertThat(muc).isEqualTo(timThay);
        assertThat(muc.anhDaiDien()).isEqualTo(AnhHienThi.tu(tin.anhId(), 1));
        assertThat(muc.giaQuyDoiHienThi().donVi()).isEqualTo("vnd_tong");
    }

    @Test
    @DisplayName("BR-01: tin bị ẩn sau khi lưu (xóa, gỡ, chủ khóa, chưa duyệt, bị từ chối) bị loại khỏi noiDung và tongSo")
    void danhSachLoaiTinKhongCongKhai() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao conHien = tinDaDuyet();
        TinTao daXoa = tinDaDuyet();
        TinTao biGo = tinDaDuyet();
        TinTao chuBiKhoa = tinDaDuyet();
        luu(nguoi, conHien);
        luu(nguoi, daXoa);
        luu(nguoi, biGo);
        luu(nguoi, chuBiKhoa);
        ht.xoaTin(daXoa);
        ht.goViPham(biGo.tinId());
        ht.khoaChu(chuBiKhoa);
        // Không thể lưu qua API: chèn thẳng để mô phỏng dữ liệu lệch (chưa duyệt / bị từ chối).
        TinTao choDuyet = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        TinTao biTuChoi = ht.taoTinChoDuyet(NoiDung.banMau(kv));
        ht.tuChoiPhienBanHienTai(biTuChoi.tinId());
        ht.chenYeuThich(nguoi.taiKhoan().getId(), choDuyet.tinId(), GOC);
        ht.chenYeuThich(nguoi.taiKhoan().getId(), biTuChoi.tinId(), GOC);

        TrangTinCongKhai kq = danhSach(nguoi, null, "50");

        assertThat(ids(kq)).containsExactly(conHien.tinId());
        assertThat(kq.tongSo()).isEqualTo(1);
        // Người dùng vẫn bỏ lưu được mục đã ẩn.
        bo(nguoi, biGo);
        assertThat(ht.demYeuThich(nguoi.taiKhoan().getId(), biGo.tinId())).isZero();
    }

    @Test
    @DisplayName("Tin sửa chờ duyệt tạm rời danh sách yêu thích; duyệt xong quay lại với nội dung mới, giữ luu_luc")
    void danhSachBanSuaChoDuyet() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TinTao tin = tinDaDuyet();
        luu(nguoi, tin);
        ht.suaTin(tin, NoiDung.banMau(kv).voiTieuDe("Tiêu đề mới chờ duyệt"));

        assertThat(danhSach(nguoi, null, null).tongSo()).isZero();

        ht.duyetPhienBanHienTai(tin.tinId());
        assertThat(danhSach(nguoi, null, null).noiDung()).extracting(TinTomTat::tieuDe)
                .containsExactly("Tiêu đề mới chờ duyệt");
    }

    @Test
    @DisplayName("trang hoặc kichThuoc không hợp lệ trả 400 theo trường")
    void danhSachThamSoKhongHopLe() {
        TaiKhoanSession nguoi = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        for (String trang : new String[] {"-1", "100001", "abc", "1.5", "01", "99999999999"}) {
            kiemTraLoiTruong(() -> danhSach(nguoi, trang, null), "trang");
        }
        for (String kichThuoc : new String[] {"0", "51", "-1", "abc", "1.5", "99999999999"}) {
            kiemTraLoiTruong(() -> danhSach(nguoi, null, kichThuoc), "kichThuoc");
        }
    }
}
