package com.realestates.backend.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.realestates.backend.detail.application.ChiTietTinService;
import com.realestates.backend.detail.controller.ChiTietTinController;
import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.favorite.application.YeuThichService;
import com.realestates.backend.favorite.controller.YeuThichController;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

/** API chi tiết (kèm liên hệ) và yêu thích qua MockMvc độc lập (không DB) với kho giả: quyền, CSRF, mã trạng thái, header. */
class ChiTietYeuThichApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final Instant GOC = Instant.parse("2026-11-18T03:00:00Z");
    private static final UUID TIN_CONG_KHAI = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private KhoYeuThichGia khoYeuThich;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        cookieConfig = taoCookieConfig();
        CurrentPermissionAndCsrfService permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository, phienXacThucRepository, sessionLifecycleAdapter, List.of(ORIGIN));
        khoYeuThich = new KhoYeuThichGia();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChiTietTinController(new ChiTietTinService(permissionAndCsrfService, new KhoChiTietGia()), cookieConfig),
                        new YeuThichController(new YeuThichService(permissionAndCsrfService, khoYeuThich), cookieConfig))
                .setControllerAdvice(
                        new AuthSessionSecurityExceptionHandler(),
                        new AuthExceptionHandler(),
                        new ListingExceptionHandler(),
                        new GlobalApiExceptionHandler()
                )
                .addFilters(new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig))
                .build();
    }

    // ------------------------------------------------------------------ chi tiết

    @Test
    @DisplayName("Khách xem chi tiết: 200 TinCongKhai, no-store, Vary Cookie, lienHe, ảnh qua duongDanDoc, không lộ chuId/email")
    void chiTietKhach() throws Exception {
        UUID anh1 = new UUID(2L, 1L);
        mockMvc.perform(yeuCauGet("/api/listings/" + TIN_CONG_KHAI, null))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.id").value(TIN_CONG_KHAI.toString()))
                .andExpect(jsonPath("$.trangThai").value("da_duyet"))
                .andExpect(jsonPath("$.noiDung.tieuDe").value("Căn hộ view sông"))
                .andExpect(jsonPath("$.noiDung.moTa").value("Mô tả chi tiết"))
                .andExpect(jsonPath("$.noiDung.soPhienBan").value(2))
                .andExpect(jsonPath("$.noiDung.giaGoc").value("2500000000"))
                .andExpect(jsonPath("$.noiDung.dienTichM2").value("68.5"))
                .andExpect(jsonPath("$.noiDung.giaQuyDoiHienThi.giaTri").value("2500000000.00"))
                .andExpect(jsonPath("$.noiDung.giaQuyDoiHienThi.donVi").value("vnd_tong"))
                .andExpect(jsonPath("$.noiDung.anh.length()").value(2))
                .andExpect(jsonPath("$.noiDung.anh[0].id").value(anh1.toString()))
                .andExpect(jsonPath("$.noiDung.anh[0].thuTu").value(1))
                .andExpect(jsonPath("$.noiDung.anh[0].duongDanDoc").value("/api/images/" + anh1 + "/content"))
                .andExpect(jsonPath("$.noiDung.anh[0].khoaAnhHienThi").doesNotExist())
                .andExpect(jsonPath("$.lienHe.hoTen").value("Nguyễn Văn Chủ"))
                .andExpect(jsonPath("$.lienHe.soDienThoai").value("0912345678"))
                .andExpect(jsonPath("$.chuId").doesNotExist())
                .andExpect(content().string(not(containsString("email"))));
    }

    @Test
    @DisplayName("Tin không công khai, không tồn tại hoặc id sai định dạng cùng nhận 404 KHONG_TIM_THAY với thông điệp giống nhau")
    void chiTiet404() throws Exception {
        for (String id : List.of(UUID.randomUUID().toString(), "khong-phai-uuid", "1-1-1-1-1")) {
            mockMvc.perform(yeuCauGet("/api/listings/" + id, null))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"))
                    .andExpect(jsonPath("$.thongDiep").value("Không tìm thấy tin."))
                    .andExpect(jsonPath("$.truongLoi.length()").value(0));
        }
    }

    // ------------------------------------------------------------------ yêu thích

    @Test
    @DisplayName("Khách nhận 401, kiểm duyệt viên và quản trị viên nhận 403 ở PUT, DELETE và GET danh sách")
    void yeuThichPhanQuyen() throws Exception {
        String duongDan = "/api/me/favorites/" + TIN_CONG_KHAI;
        mockMvc.perform(yeuCauGhi(put(duongDan), null)).andExpect(status().isUnauthorized());
        mockMvc.perform(yeuCauGhi(delete(duongDan), null)).andExpect(status().isUnauthorized());
        mockMvc.perform(yeuCauGet("/api/me/favorites", null)).andExpect(status().isUnauthorized());

        for (VaiTroTaiKhoan vaiTro : List.of(VaiTroTaiKhoan.KIEM_DUYET_VIEN, VaiTroTaiKhoan.QUAN_TRI_VIEN)) {
            Fixture f = taoTaiKhoan(vaiTro);
            mockMvc.perform(yeuCauGhi(put(duongDan), f.rawSessionCookie())).andExpect(status().isForbidden());
            mockMvc.perform(yeuCauGhi(delete(duongDan), f.rawSessionCookie())).andExpect(status().isForbidden());
            mockMvc.perform(yeuCauGhi(delete("/api/me/favorites/rac"), f.rawSessionCookie())).andExpect(status().isForbidden());
            mockMvc.perform(yeuCauGet("/api/me/favorites", f.rawSessionCookie())).andExpect(status().isForbidden());
        }
        assertThat(khoYeuThich.daLuu).isEmpty();
        assertThat(khoYeuThich.soLanGoiLuu).isZero();
        assertThat(khoYeuThich.soLanGoiBo).isZero();
    }

    @Test
    @DisplayName("PUT và DELETE thiếu token CSRF bị 403 và không chạm kho")
    void yeuThichCanCsrf() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        String duongDan = "/api/me/favorites/" + TIN_CONG_KHAI;
        for (MockHttpServletRequestBuilder builder : List.of(put(duongDan), delete(duongDan))) {
            builder.header(HttpHeaders.ORIGIN, ORIGIN)
                    .cookie(new Cookie(cookieConfig.getSessionCookieName(), nguoiDung.rawSessionCookie()));
            mockMvc.perform(builder)
                    .andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        assertThat(khoYeuThich.soLanGoiLuu).isZero();
        assertThat(khoYeuThich.soLanGoiBo).isZero();
    }

    @Test
    @DisplayName("PUT và DELETE hợp lệ trả 204 (không body), idempotent, taiKhoanId lấy từ phiên")
    void yeuThichLuuVaBo() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        String duongDan = "/api/me/favorites/" + TIN_CONG_KHAI;

        mockMvc.perform(yeuCauGhi(put(duongDan), nguoiDung.rawSessionCookie()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(content().string(""));
        assertThat(khoYeuThich.daLuu).containsExactly(nguoiDung.taiKhoan().getId() + "/" + TIN_CONG_KHAI);

        mockMvc.perform(yeuCauGhi(put(duongDan), nguoiDung.rawSessionCookie())).andExpect(status().isNoContent());
        mockMvc.perform(yeuCauGhi(delete(duongDan), nguoiDung.rawSessionCookie())).andExpect(status().isNoContent());
        mockMvc.perform(yeuCauGhi(delete(duongDan), nguoiDung.rawSessionCookie())).andExpect(status().isNoContent());
        assertThat(khoYeuThich.daLuu).isEmpty();
    }

    @Test
    @DisplayName("PUT tin không công khai/không tồn tại/sai định dạng: 404 KHONG_TIM_THAY; DELETE luôn 204")
    void yeuThichKhongCongKhai() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        for (String id : List.of(UUID.randomUUID().toString(), "khong-phai-uuid")) {
            mockMvc.perform(yeuCauGhi(put("/api/me/favorites/" + id), nguoiDung.rawSessionCookie()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
            mockMvc.perform(yeuCauGhi(delete("/api/me/favorites/" + id), nguoiDung.rawSessionCookie()))
                    .andExpect(status().isNoContent());
        }
        assertThat(khoYeuThich.daLuu).isEmpty();
    }

    @Test
    @DisplayName("GET /api/me/favorites: TrangTinCongKhai<TinTomTat>, mặc định trang 0 cỡ 20, OFFSET = trang*kichThuoc")
    void danhSachYeuThich() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        UUID anh = new UUID(2L, 1L);

        mockMvc.perform(yeuCauGet("/api/me/favorites", nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.trang").value(0))
                .andExpect(jsonPath("$.kichThuoc").value(20))
                .andExpect(jsonPath("$.tongSo").value(7))
                .andExpect(jsonPath("$.noiDung.length()").value(1))
                .andExpect(jsonPath("$.noiDung[0].id").value(TIN_CONG_KHAI.toString()))
                .andExpect(jsonPath("$.noiDung[0].tieuDe").value("Căn hộ view sông"))
                .andExpect(jsonPath("$.noiDung[0].giaGoc").value("2500000000"))
                .andExpect(jsonPath("$.noiDung[0].anhDaiDien.duongDanDoc").value("/api/images/" + anh + "/content"))
                .andExpect(jsonPath("$.noiDung[0].giaQuyDoiHienThi.giaTri").value("2500000000.00"))
                .andExpect(jsonPath("$.noiDung[0].giaQuyDoiHienThi.donVi").value("vnd_tong"));
        assertThat(khoYeuThich.taiKhoanDocCuoi).isEqualTo(nguoiDung.taiKhoan().getId());
        assertThat(khoYeuThich.boQuaCuoi).isZero();
        assertThat(khoYeuThich.gioiHanCuoi).isEqualTo(20);

        mockMvc.perform(yeuCauGet("/api/me/favorites?trang=3&kichThuoc=5", nguoiDung.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trang").value(3))
                .andExpect(jsonPath("$.kichThuoc").value(5));
        assertThat(khoYeuThich.boQuaCuoi).isEqualTo(15);
        assertThat(khoYeuThich.gioiHanCuoi).isEqualTo(5);
    }

    @Test
    @DisplayName("GET /api/me/favorites: trang/kichThuoc sai trả 400 DU_LIEU_KHONG_HOP_LE theo trường, không chạm kho")
    void danhSachThamSoSai() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        for (String q : List.of("trang=-1", "trang=100001", "trang=abc")) {
            mockMvc.perform(yeuCauGet("/api/me/favorites?" + q, nguoiDung.rawSessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("trang"));
        }
        for (String q : List.of("kichThuoc=0", "kichThuoc=51", "kichThuoc=01")) {
            mockMvc.perform(yeuCauGet("/api/me/favorites?" + q, nguoiDung.rawSessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("kichThuoc"));
        }
        assertThat(khoYeuThich.taiKhoanDocCuoi).isNull();
    }

    // ------------------------------------------------------------------ tiện ích

    private MockHttpServletRequestBuilder yeuCauGet(String duongDan, String rawSessionCookie) {
        MockHttpServletRequestBuilder builder = get(duongDan).accept(MediaType.APPLICATION_JSON);
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    /** Yêu cầu ghi hợp lệ về CSRF/Origin (token theo phiên, hoặc token ẩn danh cho khách). */
    private MockHttpServletRequestBuilder yeuCauGhi(MockHttpServletRequestBuilder builder, String rawSessionCookie) {
        builder.header(HttpHeaders.ORIGIN, ORIGIN);
        String csrf = rawSessionCookie != null
                ? sessionLifecycleAdapter.issueSessionCsrfToken(rawSessionCookie)
                : sessionLifecycleAdapter.issueAnonymousCsrfToken();
        builder.header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf);
        builder.cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf));
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
                "chi-tiet-" + UUID.randomUUID() + "@bds-test.local",
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

    private static DongTin dongTin(UUID id) {
        return new DongTin(id, new UUID(1L, 1L), "Căn hộ view sông", "ban", "can_ho",
                new BigDecimal("2500000000"), "vnd_tong", new BigDecimal("68.5"), "Quận 1",
                new BigDecimal("10.7772"), new BigDecimal("106.7015"), GOC, new UUID(2L, 1L), 1);
    }

    /** Kho chi tiết giả: chỉ TIN_CONG_KHAI công khai. */
    private static final class KhoChiTietGia implements ChiTietTinRepository {

        @Override
        public Optional<DongChiTiet> docChiTiet(UUID tinId) {
            if (!tinId.equals(TIN_CONG_KHAI)) {
                return Optional.empty();
            }
            return Optional.of(new DongChiTiet(
                    tinId, GOC, new UUID(1L, 1L), 2, "Căn hộ view sông", "Mô tả chi tiết", "ban", "can_ho",
                    new BigDecimal("2500000000"), "vnd_tong", new BigDecimal("68.5"),
                    "18 Lý Tự Trọng", "Quận 1", new BigDecimal("10.7772"), new BigDecimal("106.7015"), GOC,
                    "Nguyễn Văn Chủ", "0912345678",
                    List.of(new AnhChiTiet(new UUID(2L, 1L), 1), new AnhChiTiet(new UUID(2L, 2L), 2))
            ));
        }
    }

    /** Kho yêu thích giả: chỉ TIN_CONG_KHAI lưu được; danh sách trả 1 mục, tongSo 7. */
    private static final class KhoYeuThichGia implements YeuThichRepository {

        final Set<String> daLuu = new HashSet<>();
        int soLanGoiLuu;
        int soLanGoiBo;
        UUID taiKhoanDocCuoi;
        long boQuaCuoi;
        int gioiHanCuoi;

        @Override
        public boolean luu(UUID taiKhoanId, UUID tinId) {
            soLanGoiLuu++;
            if (!tinId.equals(TIN_CONG_KHAI)) {
                return false;
            }
            daLuu.add(taiKhoanId + "/" + tinId);
            return true;
        }

        @Override
        public void bo(UUID taiKhoanId, UUID tinId) {
            soLanGoiBo++;
            daLuu.remove(taiKhoanId + "/" + tinId);
        }

        @Override
        public TrangYeuThich docDanhSach(UUID taiKhoanId, long boQua, int gioiHan) {
            taiKhoanDocCuoi = taiKhoanId;
            boQuaCuoi = boQua;
            gioiHanCuoi = gioiHan;
            return new TrangYeuThich(List.of(dongTin(TIN_CONG_KHAI)), 7);
        }
    }
}
