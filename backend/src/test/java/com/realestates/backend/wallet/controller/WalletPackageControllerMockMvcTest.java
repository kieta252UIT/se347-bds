package com.realestates.backend.wallet.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.config.WebConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.wallet.application.QuanLyGoiDayTinApplicationService;
import com.realestates.backend.wallet.application.ViDienTuApplicationService;
import com.realestates.backend.wallet.exception.WalletPackageExceptionHandler;
import com.realestates.backend.wallet.infrastructure.InMemoryGiaoDichViRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryGoiDayTinRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryKiemTraTinAdapter;
import com.realestates.backend.wallet.infrastructure.InMemoryLanMuaGoiRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryViDienTuRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class WalletPackageControllerMockMvcTest {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final Instant FIXED_NOW = Instant.parse("2026-11-28T08:00:00Z");

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryViDienTuRepository viDienTuRepository;
    private InMemoryGiaoDichViRepository giaoDichViRepository;
    private InMemoryGoiDayTinRepository goiDayTinRepository;
    private InMemoryLanMuaGoiRepository lanMuaGoiRepository;
    private InMemoryKiemTraTinAdapter kiemTraTinAdapter;
    private ViDienTuApplicationService viDienTuService;

    private UUID ownerId;
    private UUID listingId;
    private String rawSessionCookie;
    private String csrfToken;

    @Configuration
    @EnableWebMvc
    @Import({
            WebConfig.class,
            WalletPackageExceptionHandler.class,
            AuthSessionSecurityExceptionHandler.class,
            AuthExceptionHandler.class,
            ListingExceptionHandler.class,
            GlobalApiExceptionHandler.class
    })
    static class TestWebMvcConfig {

        static ViDienTuController viDienTuController;
        static QuanLyGoiDayTinController quanLyGoiDayTinController;

        @Bean
        ViDienTuController viDienTuController() {
            return viDienTuController;
        }

        @Bean
        QuanLyGoiDayTinController quanLyGoiDayTinController() {
            return quanLyGoiDayTinController;
        }
    }

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);

        cookieConfig = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(cookieConfig, "cookieSecure", true);
        ReflectionTestUtils.setField(cookieConfig, "sessionCookieName", "__Host-se214_session");
        ReflectionTestUtils.setField(cookieConfig, "csrfCookieName", "__Host-se214_csrf");
        ReflectionTestUtils.setField(cookieConfig, "sessionMaxAgeSeconds", 31536000L);

        CurrentPermissionAndCsrfService permissionService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ORIGIN)
        );

        viDienTuRepository = new InMemoryViDienTuRepository();
        giaoDichViRepository = new InMemoryGiaoDichViRepository();
        goiDayTinRepository = new InMemoryGoiDayTinRepository();
        lanMuaGoiRepository = new InMemoryLanMuaGoiRepository();
        kiemTraTinAdapter = new InMemoryKiemTraTinAdapter(Optional.empty());

        viDienTuService = new ViDienTuApplicationService(
                permissionService,
                viDienTuRepository,
                giaoDichViRepository,
                clock,
                false
        );
        QuanLyGoiDayTinApplicationService goiDayTinService = new QuanLyGoiDayTinApplicationService(
                permissionService,
                taiKhoanRepository,
                kiemTraTinAdapter,
                viDienTuRepository,
                giaoDichViRepository,
                goiDayTinRepository,
                lanMuaGoiRepository,
                clock
        );

        TestWebMvcConfig.viDienTuController = new ViDienTuController(viDienTuService, cookieConfig);
        TestWebMvcConfig.quanLyGoiDayTinController = new QuanLyGoiDayTinController(goiDayTinService, cookieConfig);

        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebMvcConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CsrfAndOriginProtectionFilter(permissionService, cookieConfig))
                .build();

        ownerId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        taiKhoanRepository.save(TaiKhoan.taoNguoiDungDaXacMinh(
                ownerId,
                UUID.randomUUID(),
                "owner.mvc@example.com",
                "Chủ Tin MVC",
                "0901234567",
                FIXED_NOW
        ));
        rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        phienXacThucRepository.save(PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                ownerId,
                sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie),
                0L,
                FIXED_NOW,
                UUID.randomUUID(),
                "a".getBytes(StandardCharsets.UTF_8),
                "r".getBytes(StandardCharsets.UTF_8),
                FIXED_NOW.plusSeconds(3600),
                "v1"
        ));
        csrfToken = permissionService.phatHanhHoacTaiSuDungCsrfToken(rawSessionCookie, null).csrfToken();
        kiemTraTinAdapter.mockTin(listingId, ownerId, "da_duyet");
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("Review PR #26 Điểm 1: WebConfig + ViDienTuController định tuyến đúng /api/me/wallet và /api/wallet/me (không bị nhân đôi /api/api)")
    void testWalletRoutesWithWebConfigPrefix() throws Exception {
        viDienTuService.khoiTaoSoDuThuNhatQuan(ownerId, new BigDecimal("250000"), "Khởi tạo ví kiểm thử MVC");

        mockMvc.perform(get("/api/me/wallet")
                        .cookie(new Cookie("__Host-se214_session", rawSessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taiKhoanId").value(ownerId.toString()))
                .andExpect(jsonPath("$.soDuVnd").value(250000))
                .andExpect(jsonPath("$.giaoDichGanDay.length()").value(1));

        mockMvc.perform(get("/api/wallet/me")
                        .cookie(new Cookie("__Host-se214_session", rawSessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soDuVnd").value(250000));

        // POST /api/me/wallet/seed-test-balance định tuyến đúng vào controller và bị chặn 403 trong cấu hình mặc định
        mockMvc.perform(post("/api/me/wallet/seed-test-balance")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrfToken)
                        .cookie(
                                new Cookie("__Host-se214_session", rawSessionCookie),
                                new Cookie("__Host-se214_csrf", csrfToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soTien\":500000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_CHO_PHEP_KHOI_TAO_SO_DU_THU"));
    }

    @Test
    @DisplayName("Review PR #26 Điểm 1..3: Báo giá, mua gói và phát lại Replay qua MockMvc + WebConfig")
    void testBoostQuoteAndPurchaseReplayViaMockMvc() throws Exception {
        viDienTuService.khoiTaoSoDuThuNhatQuan(ownerId, new BigDecimal("500000"), "Khởi tạo ví 500k");

        mockMvc.perform(get("/api/listings/" + listingId + "/boost-quote")
                        .cookie(new Cookie("__Host-se214_session", rawSessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tinId").value(listingId.toString()))
                .andExpect(jsonPath("$.soDuHienTaiVnd").value(500000))
                .andExpect(jsonPath("$.duDieuKienMua").value(true));

        String payload7Ngay = "{\"maGoi\":\"GOI_7_NGAY\",\"maYDinh\":\"MVC_INTENT_01\",\"dongYCongDon\":false}";
        mockMvc.perform(post("/api/listings/" + listingId + "/boost-purchases")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "MVC_INTENT_01")
                        .cookie(
                                new Cookie("__Host-se214_session", rawSessionCookie),
                                new Cookie("__Host-se214_csrf", csrfToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload7Ngay))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thanhCong").value(true))
                .andExpect(jsonPath("$.soDuConLaiVnd").value(451000));

        // Gửi lại cùng mã ý định nhưng đổi gói sang GOI_30_NGAY -> 409 XUNG_DOT_MA_Y_DINH
        String payloadConflict = "{\"maGoi\":\"GOI_30_NGAY\",\"maYDinh\":\"MVC_INTENT_01\",\"dongYCongDon\":false}";
        mockMvc.perform(post("/api/listings/" + listingId + "/boost-purchases")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "MVC_INTENT_01")
                        .cookie(
                                new Cookie("__Host-se214_session", rawSessionCookie),
                                new Cookie("__Host-se214_csrf", csrfToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadConflict))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("XUNG_DOT_MA_Y_DINH"));
    }
}
