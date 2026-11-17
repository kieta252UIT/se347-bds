package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.AuthLogoutService;
import com.realestates.backend.auth.application.AuthSessionMaintenanceService;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.auth.controller.AuthLogoutController;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.auth.infrastructure.SessionTokenCryptoService;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthLogoutAndSessionRevocationTests {

    private static final String ALLOWED_ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final String ENCRYPTION_KEY = "TEST_ENCRYPTION_KEY_32_BYTES_GCM!";

    static class RecordingSupabaseAuthPort implements SupabaseAuthPort {
        final List<String> revokedAccessTokens = new ArrayList<>();

        @Override
        public SupabaseRegisteredUser registerUser(String normalizedEmail, String rawPassword, String normalizedHoTen, String normalizedSoDienThoai) {
            return new SupabaseRegisteredUser(UUID.randomUUID(), normalizedEmail, false);
        }

        @Override
        public void sendSignupVerificationEmail(String normalizedEmail) {
        }

        @Override
        public void revokeProviderSessionLocal(String rawAccessToken) {
            revokedAccessTokens.add(rawAccessToken);
        }
    }

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private SessionTokenCryptoService tokenCryptoService;
    private RecordingSupabaseAuthPort supabaseAuthPort;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private AuthSessionMaintenanceService sessionMaintenanceService;
    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private AuthLogoutService logoutService;
    private AuthCsrfAndMeController csrfAndMeController;
    private AuthLogoutController logoutController;
    private CsrfAndOriginProtectionFilter csrfFilter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.phienXacThucRepository = new InMemoryPhienXacThucRepository();
        this.tokenCryptoService = new SessionTokenCryptoService("v1:" + ENCRYPTION_KEY, "v1");
        this.supabaseAuthPort = new RecordingSupabaseAuthPort();
        this.sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        this.cookieConfig = createDefaultCookieConfig();

        this.sessionMaintenanceService = new AuthSessionMaintenanceService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                tokenCryptoService,
                supabaseAuthPort
        );

        this.permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ALLOWED_ORIGIN),
                sessionMaintenanceService
        );

        this.logoutService = new AuthLogoutService(
                phienXacThucRepository,
                sessionLifecycleAdapter,
                tokenCryptoService,
                supabaseAuthPort
        );

        this.csrfAndMeController = new AuthCsrfAndMeController(permissionAndCsrfService, cookieConfig);
        this.logoutController = new AuthLogoutController(logoutService, cookieConfig);
        this.csrfFilter = new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig);

        this.mockMvc = MockMvcBuilders.standaloneSetup(csrfAndMeController, logoutController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler(), new GlobalApiExceptionHandler())
                .addFilters(csrfFilter)
                .build();
    }
    @Test
    @DisplayName("TS1-016 / UC-28 / AC-15.4: POST /api/auth/logout thu hồi đúng phiên app, xóa cookie session (Max-Age=0), cấp CSRF ngữ cảnh khách mới và gọi Supabase Auth local logout")
    void logoutCurrentSessionRevokesDatabaseRecordAndClearsSessionCookie() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("user.logout@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrfToken = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrfToken)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrfToken)
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trangThai").value("da_dang_xuat"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);

        String sessionClearCookie = setCookies.stream()
                .filter(c -> c.startsWith(AuthSessionCookieConfig.SESSION_COOKIE_NAME + "="))
                .findFirst().orElseThrow();
        assertThat(sessionClearCookie)
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");

        String newCsrfCookie = setCookies.stream()
                .filter(c -> c.startsWith(AuthSessionCookieConfig.CSRF_COOKIE_NAME + "="))
                .findFirst().orElseThrow();
        assertThat(newCsrfCookie)
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");

        // Kiểm tra trong CSDL: bản ghi phiên đã được đánh dấu thu hồi với lý do "dang_xuat"
        Optional<PhienXacThuc> updatedPhien = phienXacThucRepository.findById(fixture.phien().getId());
        assertThat(updatedPhien).isPresent();
        assertThat(updatedPhien.get().isDaThuHoi()).isTrue();
        assertThat(updatedPhien.get().getLyDoThuHoi()).isEqualTo(PhienXacThuc.LY_DO_DANG_XUAT);
        assertThat(updatedPhien.get().getThuHoiLuc()).isNotNull();
        assertThat(supabaseAuthPort.revokedAccessTokens).hasSize(1);
    }
    @Test
    @DisplayName("TS1-016 / AC-15.4 / PX-02: Replay cookie của phiên vừa logout bị từ chối 401 CHUA_DANG_NHAP và tuyệt đối KHÔNG trả Set-Cookie gia hạn")
    void replayRevokedSessionCookieImmediatelyRejectedWith401() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("user.replay@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrfToken = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        // 1. Thực hiện logout phiên này
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrfToken)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrfToken)
                        ))
                .andExpect(status().isOk());
        MvcResult replayResult = mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"))
                .andReturn();
        assertThat(replayResult.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }
    @Test
    @DisplayName("TS1-016 / TS1-020: Restart backend rồi replay cookie đã logout vẫn bị từ chối 401 CHUA_DANG_NHAP (thu hồi bền vững trong DB)")
    void restartBackendAndReplayRevokedSessionContinuesToBeRejectedWith401() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("user.restart.replay@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrfToken = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrfToken)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrfToken)
                        ))
                .andExpect(status().isOk());

        DefaultAuthBrowserSessionLifecycleAdapter newLifecycle =
                new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        AuthSessionMaintenanceService newMaintenance =
                new AuthSessionMaintenanceService(taiKhoanRepository, phienXacThucRepository, newLifecycle, tokenCryptoService, supabaseAuthPort);
        CurrentPermissionAndCsrfService newPermission =
                new CurrentPermissionAndCsrfService(taiKhoanRepository, phienXacThucRepository, newLifecycle, List.of(ALLOWED_ORIGIN), newMaintenance);
        AuthCsrfAndMeController newMeController = new AuthCsrfAndMeController(newPermission, cookieConfig);

        MockMvc newMockMvc = MockMvcBuilders.standaloneSetup(newMeController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();
        newMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
    }
    @Test
    @DisplayName("TS1-014 / TS1-016 / PHIEN-04: Đăng xuất ở browser A chỉ thu hồi phiên A; phiên B trên browser khác của cùng người dùng vẫn duy trì 200 OK")
    void logoutOnBrowserADoesNotRevokeActiveSessionOnBrowserB() throws Exception {
        UUID accountId = UUID.randomUUID();
        ActiveSessionFixture sessionA = seedSessionForAccount(accountId, "multi.user@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        ActiveSessionFixture sessionB = seedSessionForAccount(accountId, "multi.user@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        String csrfA = sessionLifecycleAdapter.issueSessionCsrfToken(sessionA.rawSessionCookie());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrfA)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionA.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrfA)
                        ))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionA.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // 3. Phiên B trên browser khác vẫn duy trì bình thường -> Trả 200 OK
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionB.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("multi.user@example.com"));
    }

    @Test
    @DisplayName("Idempotent / openapi: Logout khi không gửi cookie phiên (chưa đăng nhập hoặc cookie đã mất) vẫn trả 200 OK và cấp CSRF khách mới")
    void logoutWithoutSessionCookieReturns200OkWithNewAnonymousCsrf() throws Exception {
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("da_dang_xuat"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    @DisplayName("Idempotent / openapi: Gọi logout nhiều lần liên tiếp (re-logout cùng phiên đã thu hồi) vẫn trả 200 OK an toàn")
    void repeatedLogoutCallsAreIdempotentAndSafe() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("idempotent@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());
        MvcResult firstLogout = mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isOk())
                .andReturn();

        String newAnonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, newAnonymousCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, newAnonymousCsrf)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("da_dang_xuat"));
    }

    @Test
    @DisplayName("openapi / §3.2: Đăng xuất khi tài khoản bị khóa vẫn nhận 200 OK và thu hồi phiên thành công")
    void logoutOnLockedAccountSucceedsAndRevokesSession() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("locked.logout@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoan lockedAccount = fixture.taiKhoan().capNhatTrangThai(TrangThaiTaiKhoan.BI_KHOA, Instant.now());
        taiKhoanRepository.save(lockedAccount);

        String sessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("da_dang_xuat"));

        assertThat(phienXacThucRepository.findById(fixture.phien().getId()).get().isDaThuHoi()).isTrue();
    }
    @Test
    @DisplayName("openapi / PHIEN-06: POST /api/auth/logout thiếu Origin hoặc Origin không hợp lệ bị 403 Forbidden")
    void logoutWithMissingOrDisallowedOriginRejectedWith403() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("csrf.origin@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        // Thiếu Origin
        mockMvc.perform(post("/api/auth/logout")
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, "https://attacker.site")
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        assertThat(phienXacThucRepository.findById(fixture.phien().getId()).get().isDaThuHoi()).isFalse();
    }

    @Test
    @DisplayName("openapi / PHIEN-06: POST /api/auth/logout thiếu hoặc sai header X-CSRF-Token bị 403 Forbidden")
    void logoutWithMissingOrInvalidCsrfTokenRejectedWith403() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("csrf.token@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, "token_sai_hoan_toan")
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));
        assertThat(phienXacThucRepository.findById(fixture.phien().getId()).get().isDaThuHoi()).isFalse();
    }
    @Test
    @DisplayName("openapi / §3.2: Khi kho lưu trữ phiên lỗi trước commit thu hồi, trả 503 TAM_GIAN_DOAN và KHÔNG xóa cookie")
    void databaseFailureBeforeCommitReturns503AndDoesNotClearCookie() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("db.error@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        // Giả lập repo ném lỗi DB
        PhienXacThucRepository failingRepo = new InMemoryPhienXacThucRepository() {
            @Override
            public synchronized Optional<PhienXacThuc> revokeByMaCookieBam(String maCookieBam, String lyDoThuHoi, Instant now, boolean giuLaiCredentialDeThuHoiProvider) {
                throw new RuntimeException("PostgreSQL connection timeout");
            }
        };

        AuthLogoutService failingLogoutService = new AuthLogoutService(failingRepo, sessionLifecycleAdapter, tokenCryptoService, supabaseAuthPort);
        AuthLogoutController failingController = new AuthLogoutController(failingLogoutService, cookieConfig);

        MockMvc failingMockMvc = MockMvcBuilders.standaloneSetup(failingController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler(), new GlobalApiExceptionHandler())
                .addFilters(csrfFilter)
                .build();

        MvcResult result = failingMockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value("TAM_GIAN_DOAN"))
                .andReturn();

        // 503 không phát Set-Cookie xóa cookie
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("openapi line 939 / §3.2: Khi Supabase Auth provider lỗi, logout vẫn trả 200 da_dang_xuat, xóa cookie và giữ can_thu_hoi_provider=true")
    void providerLogoutFailureStillReturns200AndRetainsProviderRevocationFlag() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("provider.fail@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String sessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());

        // Giả lập Supabase Auth lỗi khi gọi local logout
        SupabaseAuthPort failingSupabasePort = new RecordingSupabaseAuthPort() {
            @Override
            public void revokeProviderSessionLocal(String rawAccessToken) {
                throw new RuntimeException("Supabase 500 network timeout");
            }
        };

        AuthLogoutService resilientLogoutService = new AuthLogoutService(
                phienXacThucRepository,
                sessionLifecycleAdapter,
                tokenCryptoService,
                failingSupabasePort
        );
        AuthLogoutController resilientController = new AuthLogoutController(resilientLogoutService, cookieConfig);

        MockMvc resilientMockMvc = MockMvcBuilders.standaloneSetup(resilientController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler(), new GlobalApiExceptionHandler())
                .addFilters(csrfFilter)
                .build();

        MvcResult result = resilientMockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, sessionCsrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionCsrf)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("da_dang_xuat"))
                .andReturn();

        // Cookie phiên vẫn được xóa
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(c -> c.startsWith(AuthSessionCookieConfig.SESSION_COOKIE_NAME + "=") && c.contains("Max-Age=0"));

        // Trong DB: phiên đã thu hồi và can_thu_hoi_provider = true để retry nội bộ
        PhienXacThuc phienInDb = phienXacThucRepository.findById(fixture.phien().getId()).orElseThrow();
        assertThat(phienInDb.isDaThuHoi()).isTrue();
        assertThat(phienInDb.isCanThuHoiProvider()).isTrue();
    }

    // =========================================================================
    // Helpers & Fixtures
    // =========================================================================

    record ActiveSessionFixture(TaiKhoan taiKhoan, PhienXacThuc phien, String rawSessionCookie) {}

    private ActiveSessionFixture seedActiveSession(String email, VaiTroTaiKhoan vaiTro) {
        return seedSessionForAccount(UUID.randomUUID(), email, vaiTro);
    }

    private ActiveSessionFixture seedSessionForAccount(UUID accountId, String email, VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = taiKhoanRepository.findById(accountId).orElseGet(() -> {
            TaiKhoan created = TaiKhoan.khoiPhucTuLuuTru(
                    accountId,
                    UUID.randomUUID(),
                    email,
                    "Người dùng " + email,
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
            return taiKhoanRepository.save(created);
        });

        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        String cookieHash = sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie);
        UUID phienId = UUID.randomUUID();

        byte[] accessCipher = tokenCryptoService.encryptAccessToken(phienId, "access-jwt-" + phienId);
        byte[] refreshCipher = tokenCryptoService.encryptRefreshToken(phienId, "refresh-jwt-" + phienId);

        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                phienId,
                taiKhoan.getId(),
                cookieHash,
                taiKhoan.getMocThuHoi(),
                now,
                UUID.randomUUID(),
                accessCipher,
                refreshCipher,
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new ActiveSessionFixture(taiKhoan, phien, rawSessionCookie);
    }

    private static AuthSessionCookieConfig createDefaultCookieConfig() {
        AuthSessionCookieConfig config = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(config, "cookieSecure", true);
        ReflectionTestUtils.setField(config, "sessionCookieName", AuthSessionCookieConfig.SESSION_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionMaxAgeSeconds", AuthSessionCookieConfig.SESSION_MAX_AGE_SECONDS);
        ReflectionTestUtils.setField(config, "csrfCookieName", AuthSessionCookieConfig.CSRF_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionPersistent", true);
        return config;
    }
}
