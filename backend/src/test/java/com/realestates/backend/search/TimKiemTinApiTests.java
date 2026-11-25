package com.realestates.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.controller.TimKiemTinController;
import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.KieuGia;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** API tìm kiếm qua MockMvc độc lập (không DB) với kho giả: khách được phép, gán tham số, lỗi 400 theo trường, schema. */
class TimKiemTinApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final Instant GOC = Instant.parse("2026-11-18T03:00:00Z");

    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private KhoGia khoGia;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        cookieConfig = taoCookieConfig();
        CurrentPermissionAndCsrfService permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ORIGIN)
        );
        khoGia = new KhoGia();
        TimKiemTinService service = new TimKiemTinService(permissionAndCsrfService, khoGia);
        mockMvc = MockMvcBuilders.standaloneSetup(new TimKiemTinController(service, cookieConfig))
                .setControllerAdvice(
                        new AuthSessionSecurityExceptionHandler(),
                        new AuthExceptionHandler(),
                        new ListingExceptionHandler(),
                        new GlobalApiExceptionHandler()
                )
                .addFilters(new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig))
                .build();
    }

    @Test
    @DisplayName("Khách không cookie nhận 200, no-store, Vary Cookie; response đúng schema KetQuaTimKiem/TinTomTat")
    void khachDuocPhepVaDungSchema() throws Exception {
        String anhId = new UUID(2L, 1L).toString();
        mockMvc.perform(yeuCauGet("/api/listings", null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.thoiDiemTruyVan").exists())
                .andExpect(jsonPath("$.tuNhien.trang").value(0))
                .andExpect(jsonPath("$.tuNhien.kichThuoc").value(20))
                .andExpect(jsonPath("$.tuNhien.tongSo").value(42))
                .andExpect(jsonPath("$.tuNhien.noiDung.length()").value(1))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].id").value(new UUID(0L, 1L).toString()))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].phienBanId").value(new UUID(1L, 1L).toString()))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].giaGoc").value("100000000"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].dienTichM2").value("3"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].viDo").value("10.7772"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].kinhDo").value("106.7015"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].anhDaiDien.id").value(anhId))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].anhDaiDien.thuTu").value(1))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].anhDaiDien.duongDanDoc").value("/api/images/" + anhId + "/content"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].anhDaiDien.khoaAnhHienThi").doesNotExist())
                .andExpect(jsonPath("$.tuNhien.noiDung[0].giaQuyDoiHienThi.giaTri").value("100000000.00"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].giaQuyDoiHienThi.donVi").value("vnd_tong"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].giaQuyDoiHienThi.chuThich")
                        .value("Giá quy đổi tham khảo; không dùng để lọc."))
                .andExpect(jsonPath("$.taiTro.length()").value(1))
                .andExpect(jsonPath("$.taiTro[0].id").value(new UUID(0L, 9L).toString()));
        assertThat(khoGia.boLocCuoi.cheDoGia()).isEqualTo(KieuGia.TONG);
        assertThat(khoGia.boLocCuoi.sapXep()).isEqualTo(SapXepTin.MAC_DINH);
        assertThat(khoGia.boLocCuoi.trang()).isZero();
        assertThat(khoGia.boLocCuoi.kichThuoc()).isEqualTo(TimKiemTinService.KICH_THUOC_MAC_DINH);
        assertThat(khoGia.soTaiTroCuoi).isEqualTo(TimKiemTinService.SO_TAI_TRO_TOI_DA);
    }

    @Test
    @DisplayName("cheDoGia=m2 hiển thị giá/m2 làm tròn HALF_UP 2 chữ số")
    void cheDoGiaM2() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings?cheDoGia=m2", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuNhien.noiDung[0].giaQuyDoiHienThi.giaTri").value("33333333.33"))
                .andExpect(jsonPath("$.tuNhien.noiDung[0].giaQuyDoiHienThi.donVi").value("vnd_m2"));
    }

    @Test
    @DisplayName("Cookie phiên rác hoặc người dùng đăng nhập đều vẫn là 200")
    void cookieRacVaNguoiDung() throws Exception {
        mockMvc.perform(yeuCauGet("/api/listings", "cookie-rac-khong-ton-tai"))
                .andExpect(status().isOk());
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        mockMvc.perform(yeuCauGet("/api/listings", nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tham số hợp đồng được gán đúng: tuKhoa/khuVuc chuẩn hóa không dấu, giá, diện tích, vùng bản đồ, trang")
    void ganThamSo() throws Exception {
        mockMvc.perform(get("/api/listings")
                        .param("tuKhoa", "  Căn Hộ Quận 1 căn ")
                        .param("loaiGiaoDich", "cho_thue")
                        .param("loaiBds", "phong_tro")
                        .param("khuVuc", "  TP.  Thủ---Đức!! ")
                        .param("cheDoGia", "m2")
                        .param("giaMin", "0")
                        .param("giaMax", "200000.5")
                        .param("dienTichMin", "10")
                        .param("dienTichMax", "80")
                        .param("nam", "10.5")
                        .param("bac", "11")
                        .param("tay", "106")
                        .param("dong", "107.25")
                        .param("sapXep", "moi_nhat")
                        .param("trang", "3")
                        .param("kichThuoc", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuNhien.trang").value(3))
                .andExpect(jsonPath("$.tuNhien.kichThuoc").value(50));
        BoLocTimKiem b = khoGia.boLocCuoi;
        assertThat(b.tuKhoa()).containsExactly("can", "ho", "quan", "1");
        assertThat(b.loaiGiaoDich()).isEqualTo(LoaiGiaoDich.CHO_THUE);
        assertThat(b.loaiBds()).isEqualTo(LoaiBds.PHONG_TRO);
        assertThat(b.khuVuc()).isEqualTo("tp thu duc");
        assertThat(b.cheDoGia()).isEqualTo(KieuGia.M2);
        assertThat(b.giaMin()).isEqualByComparingTo("0");
        assertThat(b.giaMax()).isEqualByComparingTo("200000.5");
        assertThat(b.dienTichMin()).isEqualByComparingTo("10");
        assertThat(b.dienTichMax()).isEqualByComparingTo("80");
        assertThat(b.nam()).isEqualByComparingTo("10.5");
        assertThat(b.dong()).isEqualByComparingTo("107.25");
        assertThat(b.sapXep()).isEqualTo(SapXepTin.MOI_NHAT);
        assertThat(b.trang()).isEqualTo(3);
        assertThat(b.kichThuoc()).isEqualTo(50);
    }

    @Test
    @DisplayName("Tham số lạ (giaDen, cursor, limit) hoặc lặp lại nhận 400, không bị bỏ qua; kho không được gọi")
    void thamSoLaHoacLap() throws Exception {
        kiemTra400("/api/listings?loaiGiaoDich=ban&giaDen=0", "giaDen");
        kiemTra400("/api/listings?cursor=abc", "cursor");
        kiemTra400("/api/listings?limit=10", "limit");
        kiemTra400("/api/listings?loaiBds=can_ho&loaiBds=dat", "loaiBds");
        kiemTra400("/api/listings?trang=1&trang=2", "trang");
        assertThat(khoGia.soLanGoi).isZero();
    }

    @Test
    @DisplayName("400 cho enum lạ, sapXep ngoài mac_dinh/moi_nhat, số sai, trang/kichThuoc ngoài miền, độ dài vượt giới hạn")
    void giaTriSai() throws Exception {
        kiemTra400("/api/listings?loaiGiaoDich=mua", "loaiGiaoDich");
        kiemTra400("/api/listings?loaiBds=can_ho,dat", "loaiBds");
        kiemTra400("/api/listings?cheDoGia=thang", "cheDoGia");
        for (String sapXep : List.of("gia_tang", "dien_tich_giam", "cu_nhat")) {
            kiemTra400("/api/listings?sapXep=" + sapXep, "sapXep");
        }
        kiemTra400("/api/listings?dienTichMin=-1", "dienTichMin");
        kiemTra400("/api/listings?dienTichMin=1e3", "dienTichMin");
        kiemTra400("/api/listings?dienTichMin=01", "dienTichMin");
        kiemTra400("/api/listings?trang=100001", "trang");
        kiemTra400("/api/listings?trang=-1", "trang");
        kiemTra400("/api/listings?kichThuoc=0", "kichThuoc");
        kiemTra400("/api/listings?kichThuoc=51", "kichThuoc");
        kiemTra400("/api/listings?tuKhoa=" + "a".repeat(101), "tuKhoa");
        kiemTra400("/api/listings?tuKhoa=a b c d e f g h i j k", "tuKhoa");
        kiemTra400("/api/listings?tuKhoa=?!...", "tuKhoa");
        kiemTra400("/api/listings?khuVuc=" + "a".repeat(121), "khuVuc");
        assertThat(khoGia.soLanGoi).isZero();
    }

    @Test
    @DisplayName("Cận giá cần loaiGiaoDich; khoảng đảo bị 400; tuKhoa/khuVuc chỉ khoảng trắng là không truyền")
    void quyTacKhoang() throws Exception {
        kiemTra400("/api/listings?giaMax=100", "loaiGiaoDich");
        kiemTra400("/api/listings?loaiGiaoDich=ban&giaMin=5&giaMax=4", "giaMin");
        kiemTra400("/api/listings?dienTichMin=5&dienTichMax=4", "dienTichMin");
        mockMvc.perform(get("/api/listings")
                        .param("loaiGiaoDich", "ban").param("giaMin", "4").param("giaMax", "4")
                        .param("tuKhoa", "   ").param("khuVuc", " "))
                .andExpect(status().isOk());
        assertThat(khoGia.boLocCuoi.coTuKhoa()).isFalse();
        assertThat(khoGia.boLocCuoi.khuVuc()).isNull();

        mockMvc.perform(get("/api/listings").param("khuVuc", " .,-!! ")).andExpect(status().isOk());
        assertThat(khoGia.boLocCuoi.khuVuc()).isNull();
    }

    @Test
    @DisplayName("Vùng bản đồ: thiếu cận, ngoài miền, nam > bac, tay > dong (cắt kinh tuyến 180) đều 400")
    void vungBanDo() throws Exception {
        kiemTra400("/api/listings?nam=10&bac=11&tay=106", "dong");
        kiemTra400("/api/listings?nam=-91&bac=11&tay=106&dong=107", "nam");
        kiemTra400("/api/listings?nam=10&bac=11&tay=106&dong=181", "dong");
        kiemTra400("/api/listings?nam=12&bac=11&tay=106&dong=107", "nam");
        kiemTra400("/api/listings?nam=10&bac=11&tay=170&dong=-170", "tay");
        mockMvc.perform(yeuCauGet("/api/listings?nam=-90&bac=90&tay=-180&dong=180", null))
                .andExpect(status().isOk());
        assertThat(khoGia.boLocCuoi.coVung()).isTrue();
    }

    private void kiemTra400(String duongDan, String truong) throws Exception {
        mockMvc.perform(yeuCauGet(duongDan, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[*].truong", hasItem(truong)));
    }

    private MockHttpServletRequestBuilder yeuCauGet(String duongDan, String rawSessionCookie) {
        MockHttpServletRequestBuilder builder = get(duongDan).accept(MediaType.APPLICATION_JSON);
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    private record Fixture(TaiKhoan taiKhoan, String rawSessionCookie) {
    }

    private Fixture taoTaiKhoan(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tim-kiem-" + UUID.randomUUID() + "@bds-test.local",
                "Tài khoản thử",
                "0901234567",
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                now,
                now,
                now
        );
        taiKhoanRepository.save(taiKhoan);
        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                taiKhoan.getId(),
                sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie),
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new Fixture(taiKhoan, rawSessionCookie);
    }

    private static AuthSessionCookieConfig taoCookieConfig() {
        AuthSessionCookieConfig config = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(config, "cookieSecure", true);
        ReflectionTestUtils.setField(config, "sessionCookieName", AuthSessionCookieConfig.SESSION_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionMaxAgeSeconds", AuthSessionCookieConfig.SESSION_MAX_AGE_SECONDS);
        ReflectionTestUtils.setField(config, "csrfCookieName", AuthSessionCookieConfig.CSRF_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionPersistent", true);
        return config;
    }

    /** Kho giả: một tin tự nhiên, một tin tài trợ, tổng số 42; ghi lại bộ lọc lần gọi cuối. */
    private static final class KhoGia implements TimKiemRepository {

        int soLanGoi;
        BoLocTimKiem boLocCuoi;
        int soTaiTroCuoi;

        @Override
        public KetQua timKiem(BoLocTimKiem boLoc, int soTaiTroToiDa) {
            soLanGoi++;
            boLocCuoi = boLoc;
            soTaiTroCuoi = soTaiTroToiDa;
            return new KetQua(List.of(dong(1L)), 42, List.of(dong(9L)), GOC);
        }

        private static DongTin dong(long so) {
            return new DongTin(
                    new UUID(0L, so),
                    new UUID(1L, so),
                    "Tin " + so,
                    "ban",
                    "can_ho",
                    new BigDecimal("100000000"),
                    "vnd_tong",
                    new BigDecimal("3"),
                    "TP. Hồ Chí Minh",
                    new BigDecimal("10.7772"),
                    new BigDecimal("106.7015"),
                    GOC.minusSeconds(so),
                    new UUID(2L, so),
                    1
            );
        }
    }
}
