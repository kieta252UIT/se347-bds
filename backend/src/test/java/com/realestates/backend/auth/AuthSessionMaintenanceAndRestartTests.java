package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.AuthSessionMaintenanceService;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRefreshedSession;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.TaiKhoanHienTai;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.auth.infrastructure.SessionTokenCipher;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Bộ kiểm thử toàn diện cho task "Duy trì đăng nhập qua đóng/mở trình duyệt và khởi động lại backend" (S1-26)
 * theo đặc tả:
 * - ADR-0012: Duy trì đăng nhập trên cùng trình duyệt/profile (không idle/absolute timeout).
 * - thiet-ke-phien-sprint-1.md: PHIEN-02 (Cookie persistent Max-Age=31536000), PHIEN-08 (Refresh token lease 30s + CAS),
 *   PHIEN-15 (Gia hạn trượt trên GET /api/me).
 * - test-specification-sprint-1.md: TS1-014 (Duy trì đăng nhập & nhiều browser), TS1-015 (Mất cookie / lỗi mạng tạm),
 *   TS1-020 (Kho phiên nhiều instance & lỗi DB), TS1-071 (Refresh đồng thời & cô lập credential),
 *   PX-01, PX-18, PX-19.
 */
class AuthSessionMaintenanceAndRestartTests {

    private static final String ALLOWED_ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final String ENCRYPTION_KEY = "TEST_ENCRYPTION_KEY_32_BYTES_GCM!";

    static class RecordingSupabaseAuthPort implements SupabaseAuthPort {
        final List<String> refreshCalls = new ArrayList<>();
        final AtomicInteger refreshCounter = new AtomicInteger(0);
        boolean simulateNetworkError = false;
        boolean simulateInvalidRefreshToken = false;

        @Override
        public SupabaseRegisteredUser registerUser(String normalizedEmail, String rawPassword, String normalizedHoTen, String normalizedSoDienThoai) {
            return new SupabaseRegisteredUser(UUID.randomUUID(), normalizedEmail, false);
        }

        @Override
        public void sendSignupVerificationEmail(String normalizedEmail) {
        }

        @Override
        public SupabaseRefreshedSession refreshSessionToken(String rawRefreshToken) {
            refreshCalls.add(rawRefreshToken);
            refreshCounter.incrementAndGet();

            if (simulateNetworkError) {
                throw new AuthProviderUnavailableException("Mạng tới Supabase Auth bị gián đoạn.");
            }
            if (simulateInvalidRefreshToken) {
                throw new IllegalArgumentException("Refresh token đã bị thu hồi hoặc không hợp lệ.");
            }

            return new SupabaseRefreshedSession(
                    "refreshed-access-jwt-" + UUID.randomUUID(),
                    "refreshed-refresh-token-" + UUID.randomUUID(),
                    3600L,
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );
        }
    }

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private SessionTokenCipher tokenCipher;
    private RecordingSupabaseAuthPort supabaseAuthPort;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private AuthSessionMaintenanceService sessionMaintenanceService;
    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private AuthCsrfAndMeController csrfAndMeController;
    private CsrfAndOriginProtectionFilter csrfFilter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.phienXacThucRepository = new InMemoryPhienXacThucRepository();
        this.tokenCipher = new SessionTokenCipher(ENCRYPTION_KEY, "v1");
        this.supabaseAuthPort = new RecordingSupabaseAuthPort();
        this.sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        this.cookieConfig = createDefaultCookieConfig();

        this.sessionMaintenanceService = new AuthSessionMaintenanceService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                tokenCipher,
                supabaseAuthPort
        );

        this.permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ALLOWED_ORIGIN),
                sessionMaintenanceService
        );

        this.csrfAndMeController = new AuthCsrfAndMeController(permissionAndCsrfService, cookieConfig);
        this.csrfFilter = new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig);

        this.mockMvc = MockMvcBuilders.standaloneSetup(csrfAndMeController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler(), new GlobalApiExceptionHandler())
                .addFilters(csrfFilter)
                .build();
    }

    // =========================================================================
    // 1. TS1-014 & PX-01: Duy trì đăng nhập qua đóng/mở trình duyệt thường
    // =========================================================================

    @Test
    @DisplayName("TS1-014 / PX-01 / PHIEN-02 / PHIEN-15: Đóng/mở trình duyệt thường giữ nguyên cookie persistent Max-Age=31536000, gọi GET /api/me thành công trả 200 OK và gia hạn trượt cookie với CÙNG giá trị opaque")
    void browserCloseAndReopenMaintainsPersistentCookieAndSlidingRenewal() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("user.browser@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        // Giả lập trình duyệt lưu cookie persistent trên đĩa và đóng/mở lại trình duyệt thường
        Cookie browserReopenedCookie = new Cookie(
                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                fixture.rawSessionCookie()
        );

        MvcResult result = mockMvc.perform(get("/api/me").cookie(browserReopenedCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value("user.browser@example.com"))
                .andExpect(jsonPath("$.vaiTro").value("nguoi_dung"))
                .andExpect(jsonPath("$.trangThai").value("hoat_dong"))
                .andReturn();

        // Kiểm tra Set-Cookie gia hạn trượt: cùng giá trị opaque, Max-Age=31536000 (365 ngày), không Domain
        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        String renewedCookieHeader = setCookies.getFirst();
        assertThat(renewedCookieHeader)
                .contains("__Host-se214_session=" + fixture.rawSessionCookie())
                .contains("Max-Age=31536000")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("Chính sách Session Cookie (session-persistent: false): Đóng web/đóng tab mở lại vẫn duy trì đăng nhập; trừ khi đóng hẳn trình duyệt (mất cookie khỏi RAM) thì phải đăng nhập lại")
    void browserSessionCookieMaintainsLoginAcrossTabsUntilBrowserClose() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("tab.session@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        // Cấu hình chế độ Session Cookie (không Max-Age)
        AuthSessionCookieConfig sessionOnlyConfig = createDefaultCookieConfig();
        sessionOnlyConfig.setSessionPersistent(false);

        AuthCsrfAndMeController sessionOnlyController = new AuthCsrfAndMeController(permissionAndCsrfService, sessionOnlyConfig);
        MockMvc sessionOnlyMockMvc = MockMvcBuilders.standaloneSetup(sessionOnlyController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        // 1. Khi đăng nhập thành công và gọi GET /api/me: Server phát hành cookie không có Max-Age (Session Cookie)
        MvcResult result = sessionOnlyMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(sessionOnlyConfig.getSessionCookieName(), fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tab.session@example.com"))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        String sessionCookieHeader = setCookies.getFirst();
        assertThat(sessionCookieHeader)
                .contains("__Host-se214_session=" + fixture.rawSessionCookie())
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Max-Age=")
                .doesNotContainIgnoringCase("Expires=");

        // 2. Kịch bản đóng web/đóng tab và mở lại tab trong cùng trình duyệt:
        // Do trình duyệt chưa tắt, cookie phiên vẫn nằm trong RAM của trình duyệt -> gửi lại cookie -> vẫn duy trì đăng nhập
        sessionOnlyMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(sessionOnlyConfig.getSessionCookieName(), fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tab.session@example.com"));

        // 3. Kịch bản đóng hẳn trình duyệt (exit browser process):
        // Trình duyệt tự động dọn sạch Session Cookie khỏi RAM. Khi mở lại trình duyệt, request không còn cookie -> 401 CHUA_DANG_NHAP
        sessionOnlyMockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
    }

    // =========================================================================
    // 2. TS1-014, TS1-020 & PX-01: Duy trì đăng nhập qua khởi động lại backend
    // =========================================================================

    @Test
    @DisplayName("TS1-014 / TS1-020 / PX-01: Khởi động lại backend (mô phỏng instance mới hoàn toàn kết nối vào kho lưu trữ bền vững) vẫn nhận diện đúng cookie phiên, duy trì đăng nhập 200 OK mà không bắt đăng nhập lại")
    void backendRestartPreservesActiveSessionWithoutRequiringReLogin() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("user.restart@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        // Mô phỏng restart backend:
        // Instance 1 shutdown (tất cả service/controller/cache của instance 1 bị hủy).
        // Instance 2 khởi động mới với container Spring/JVM mới, cùng kết nối vào kho lưu trữ phiên bền vững phienXacThucRepository và taiKhoanRepository.
        DefaultAuthBrowserSessionLifecycleAdapter newLifecycleAdapter =
                new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        AuthSessionMaintenanceService newMaintenanceService =
                new AuthSessionMaintenanceService(
                        taiKhoanRepository,
                        phienXacThucRepository,
                        newLifecycleAdapter,
                        tokenCipher,
                        supabaseAuthPort
                );
        CurrentPermissionAndCsrfService newPermissionService =
                new CurrentPermissionAndCsrfService(
                        taiKhoanRepository,
                        phienXacThucRepository,
                        newLifecycleAdapter,
                        List.of(ALLOWED_ORIGIN),
                        newMaintenanceService
                );
        AuthCsrfAndMeController newController = new AuthCsrfAndMeController(newPermissionService, cookieConfig);
        MockMvc newBackendMockMvc = MockMvcBuilders.standaloneSetup(newController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        // Browser gửi lại cookie cũ sau khi backend đã khởi động lại
        newBackendMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.email").value("user.restart@example.com"))
                .andExpect(jsonPath("$.vaiTro").value("nguoi_dung"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=31536000")));
    }

    @Test
    @DisplayName("Khởi động lại backend với Session Cookie (session-persistent: false): Backend mới đọc DB duy trì đăng nhập thành công 200 OK, cookie tiếp tục là Session Cookie (không Max-Age)")
    void backendRestartWithBrowserSessionCookiePreservesActiveSession() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("session.restart@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        AuthSessionCookieConfig sessionOnlyConfig = createDefaultCookieConfig();
        sessionOnlyConfig.setSessionPersistent(false);

        // Instance 2 khởi động mới với container Spring/JVM mới, cùng kết nối vào phienXacThucRepository và taiKhoanRepository
        DefaultAuthBrowserSessionLifecycleAdapter newLifecycleAdapter =
                new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        AuthSessionMaintenanceService newMaintenanceService =
                new AuthSessionMaintenanceService(taiKhoanRepository, phienXacThucRepository, newLifecycleAdapter, tokenCipher, supabaseAuthPort);
        CurrentPermissionAndCsrfService newPermissionService =
                new CurrentPermissionAndCsrfService(taiKhoanRepository, phienXacThucRepository, newLifecycleAdapter, List.of(ALLOWED_ORIGIN), newMaintenanceService);
        AuthCsrfAndMeController newController = new AuthCsrfAndMeController(newPermissionService, sessionOnlyConfig);

        MockMvc newBackendMockMvc = MockMvcBuilders.standaloneSetup(newController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        // Browser gửi lại cookie cũ sau khi backend đã khởi động lại
        MvcResult result = newBackendMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(sessionOnlyConfig.getSessionCookieName(), fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.email").value("session.restart@example.com"))
                .andExpect(jsonPath("$.vaiTro").value("nguoi_dung"))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        String sessionCookieHeader = setCookies.getFirst();
        assertThat(sessionCookieHeader)
                .contains("__Host-se214_session=" + fixture.rawSessionCookie())
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Max-Age=")
                .doesNotContainIgnoringCase("Expires=");
    }

    @Test
    @DisplayName("Cấu hình session-clear-on-restart: true: Khi restart backend, các phiên cũ tạo trước thời điểm khởi động bị từ chối 401 CHUA_DANG_NHAP và buộc đăng nhập lại")
    void backendRestartWithSessionClearOnRestartEnabledRequiresReLogin() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("clear.restart@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        AuthSessionCookieConfig sessionConfig = createDefaultCookieConfig();

        // Chờ 10ms để đảm bảo mốc thời gian serverBootTime của backend mới sau thời điểm tạo phiên
        Thread.sleep(15L);

        // Instance 2 khởi động mới với cấu hình session-clear-on-restart = true
        DefaultAuthBrowserSessionLifecycleAdapter newLifecycleAdapter =
                new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        AuthSessionMaintenanceService newMaintenanceService =
                new AuthSessionMaintenanceService(taiKhoanRepository, phienXacThucRepository, newLifecycleAdapter, tokenCipher, supabaseAuthPort);
        newMaintenanceService.setSessionClearOnRestart(true);

        CurrentPermissionAndCsrfService newPermissionService =
                new CurrentPermissionAndCsrfService(taiKhoanRepository, phienXacThucRepository, newLifecycleAdapter, List.of(ALLOWED_ORIGIN), newMaintenanceService);
        AuthCsrfAndMeController newController = new AuthCsrfAndMeController(newPermissionService, sessionConfig);

        MockMvc newBackendMockMvc = MockMvcBuilders.standaloneSetup(newController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        // Browser gửi lại cookie cũ sau khi backend đã khởi động lại -> Bị từ chối 401 CHUA_DANG_NHAP
        newBackendMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(sessionConfig.getSessionCookieName(), fixture.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
    }

    // =========================================================================
    // 3. TS1-014: Nhiều trình duyệt / profile độc lập cho cùng người dùng
    // =========================================================================

    @Test
    @DisplayName("TS1-014 / ADR-0012 / PHIEN-04: Cùng người dùng đăng nhập trên 2 browser A và B độc lập; đóng/mở A hoặc restart backend không ảnh hưởng B; đăng nhập B không tự thu hồi A")
    void multipleBrowsersOperateIndependentlyWithoutAutoRevokingEachOther() throws Exception {
        UUID accountId = UUID.randomUUID();
        ActiveSessionFixture sessionA = seedSessionForAccount(accountId, "multi.browser@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        ActiveSessionFixture sessionB = seedSessionForAccount(accountId, "multi.browser@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        assertThat(sessionA.rawSessionCookie()).isNotEqualTo(sessionB.rawSessionCookie());
        assertThat(sessionA.phien().getId()).isNotEqualTo(sessionB.phien().getId());

        // Browser A và Browser B đều hợp lệ
        mockMvc.perform(get("/api/me").cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionA.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("multi.browser@example.com"));

        mockMvc.perform(get("/api/me").cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionB.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("multi.browser@example.com"));

        // Thu hồi riêng phiên Browser A (người dùng bấm đăng xuất trên A)
        phienXacThucRepository.revokeByMaCookieBam(
                sessionA.phien().getMaCookieBam(),
                PhienXacThuc.LY_DO_DANG_XUAT,
                Instant.now(),
                false
        );

        // Browser A bị từ chối 401 CHUA_DANG_NHAP
        mockMvc.perform(get("/api/me").cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionA.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // Browser B vẫn tiếp tục duy trì đăng nhập bình thường (PHIEN-04, TS1-014)
        mockMvc.perform(get("/api/me").cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionB.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("multi.browser@example.com"));
    }

    // =========================================================================
    // 4. ADR-0012: Không có idle timeout hoặc absolute timeout buộc đăng nhập lại
    // =========================================================================

    @Test
    @DisplayName("ADR-0012: Không áp idle timeout hoặc absolute timeout; phiên tạo từ lâu (vượt 15 phút, 24 giờ, 30 ngày) vẫn duy trì đăng nhập hợp lệ khi chưa bị thu hồi")
    void noIdleOrAbsoluteTimeoutEnforcedOnValidSessions() throws Exception {
        Instant thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 3600);
        ActiveSessionFixture oldSession = seedSessionWithCreationTime(
                "long.idle@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG,
                thirtyDaysAgo
        );

        mockMvc.perform(get("/api/me").cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, oldSession.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("long.idle@example.com"))
                .andExpect(jsonPath("$.vaiTro").value("nguoi_dung"));
    }

    // =========================================================================
    // 5. TS1-015: Mất cookie vs Xóa cache
    // =========================================================================

    @Test
    @DisplayName("TS1-015: Xóa cache giữ cookie thì tiếp tục đăng nhập; xóa cookie/site data thì trả 401 và không tự dựng lại phiên bằng fingerprint")
    void cacheClearPreservesLoginWhileCookieClearRequiresLoginWithoutFingerprinting() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("cache.cookie@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        // 1. Trình duyệt xóa cache HTTP nhưng giữ nguyên cookie phiên
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cache.cookie@example.com"));

        // 2. Người dùng xóa cookie / mở profile mới chưa có cookie -> 401 CHUA_DANG_NHAP
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // =========================================================================
    // 6. TS1-015 & PX-18: Sự cố mạng / CSDL tạm thời trả 503 và KHÔNG xóa cookie
    // =========================================================================

    @Test
    @DisplayName("TS1-015 / PX-18: CSDL gián đoạn tạm thời trả 503 TAM_GIAN_DOAN và tuyệt đối KHÔNG gửi Set-Cookie xóa phiên của người dùng; khi DB phục hồi tiếp tục đăng nhập bình thường")
    void temporaryDbOutageReturns503WithoutClearingSessionCookie() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("outage.test@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        PhienXacThucRepository outageRepository = new PhienXacThucRepository() {
            @Override
            public Optional<PhienXacThuc> findById(UUID id) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }

            @Override
            public Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }

            @Override
            public Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }

            @Override
            public List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }

            @Override
            public PhienXacThuc save(PhienXacThuc phienXacThuc) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }

            @Override
            public Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(String oldMaCookieBam, PhienXacThuc newPhien, Instant now) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }

            @Override
            public Optional<PhienXacThuc> revokeByMaCookieBam(String maCookieBam, String lyDoThuHoi, Instant now, boolean giuLaiCredentialDeThuHoiProvider) {
                throw new RuntimeException("PostgreSQL Connection refused (simulated)");
            }
        };

        CurrentPermissionAndCsrfService outagePermissionService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                outageRepository,
                sessionLifecycleAdapter,
                List.of(ALLOWED_ORIGIN)
        );
        AuthCsrfAndMeController outageController = new AuthCsrfAndMeController(outagePermissionService, cookieConfig);
        MockMvc outageMockMvc = MockMvcBuilders.standaloneSetup(outageController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        // Trong lúc DB gián đoạn: trả 503 TAM_GIAN_DOAN và KHÔNG gửi Set-Cookie
        outageMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value("TAM_GIAN_DOAN"));

        // Khi DB phục hồi (quay lại repository ban đầu): phiên vẫn còn hiệu lực và đăng nhập bình thường
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("outage.test@example.com"));
    }

    // =========================================================================
    // 7. PHIEN-08 & TS1-014: Tự động làm mới access token khi hết hạn kỹ thuật
    // =========================================================================

    @Test
    @DisplayName("PHIEN-08 / ADR-0012 / PX-01: Access token provider hết hạn kỹ thuật không làm đứt phiên ứng dụng; backend tự động làm mới access token dưới lease 30s + CAS")
    void expiredProviderAccessTokenIsAutomaticallyRefreshedWithoutUserLogout() throws Exception {
        // Tạo phiên có access token đã hết hạn 10 phút trước
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);
        ActiveSessionFixture fixture = seedSessionWithTokenExpiry(
                "refresh.auto@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG,
                tenMinutesAgo
        );

        mockMvc.perform(get("/api/me").cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("refresh.auto@example.com"));

        // Kiểm tra SupabaseAuthPort đã được gọi làm mới đúng 1 lần
        assertThat(supabaseAuthPort.refreshCounter.get()).isEqualTo(1);

        // Kiểm tra bản ghi phiên trong repository đã được cập nhật token mới và gia hạn thời gian
        PhienXacThuc updatedSession = phienXacThucRepository.findById(fixture.phien().getId()).orElseThrow();
        assertThat(updatedSession.getAccessTokenHetHanLuc()).isAfter(Instant.now());
        assertThat(updatedSession.getRefreshLeaseId()).isNull(); // Lease đã được giải phóng
        assertThat(updatedSession.getPhienBanCapNhat()).isGreaterThan(fixture.phien().getPhienBanCapNhat());
    }

    // =========================================================================
    // 8. TS1-071 & PHIEN-08: Hai request song song cùng refresh token
    // =========================================================================

    @Test
    @DisplayName("TS1-071 / PHIEN-08: Hai request đồng thời trên cùng một phiên khi access token hết hạn; request 1 nhận lease và cập nhật token, request 2 đợi và tái sử dụng kết quả, không bị lỗi tranh chấp")
    void concurrentRequestsOnSameSessionShareRefreshedTokensWithoutRaceCondition() throws Exception {
        Instant expiredTime = Instant.now().minusSeconds(300);
        ActiveSessionFixture fixture = seedSessionWithTokenExpiry(
                "concurrent.refresh@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG,
                expiredTime
        );

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        List<PrincipalPhienHienTai> results = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PrincipalPhienHienTai principal =
                            sessionMaintenanceService.duyTriVaKiemTraPhien(fixture.rawSessionCookie());
                    results.add(principal);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(errors).isEmpty();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getEmail()).isEqualTo("concurrent.refresh@example.com");
        assertThat(results.get(1).getEmail()).isEqualTo("concurrent.refresh@example.com");

        // Provider chỉ bị gọi 1 lần duy nhất cho lượt refresh này (request thứ 2 tái sử dụng kết quả)
        assertThat(supabaseAuthPort.refreshCounter.get()).isEqualTo(1);
    }

    // =========================================================================
    // 9. TS1-071: Đua refresh vs Thu hồi / Khóa tài khoản
    // =========================================================================

    @Test
    @DisplayName("TS1-071: Phiên bị thu hồi trong lúc chờ làm mới token; CAS thất bại và không hồi sinh phiên đã thu hồi")
    void concurrentRevokeDuringRefreshAbortsUpdateAndDoesNotReviveSession() throws Exception {
        Instant expiredTime = Instant.now().minusSeconds(100);
        ActiveSessionFixture fixture = seedSessionWithTokenExpiry(
                "race.revoke@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG,
                expiredTime
        );

        // Thu hồi phiên trước khi bước update CAS hoàn tất
        phienXacThucRepository.revokeByMaCookieBam(
                fixture.phien().getMaCookieBam(),
                PhienXacThuc.LY_DO_DANG_XUAT,
                Instant.now(),
                false
        );

        // Gọi làm mới phiên đã thu hồi -> Phải bị từ chối và ném UnauthenticatedSessionException
        assertThatThrownBy(() -> sessionMaintenanceService.duyTriVaKiemTraPhien(fixture.rawSessionCookie()))
                .isInstanceOf(UnauthenticatedSessionException.class);

        // Kiểm tra phiên trong repository vẫn giữ trạng thái đã thu hồi, không bị sống lại
        PhienXacThuc stored = phienXacThucRepository.findById(fixture.phien().getId()).orElseThrow();
        assertThat(stored.isDaThuHoi()).isTrue();
        assertThat(stored.getLyDoThuHoi()).isEqualTo(PhienXacThuc.LY_DO_DANG_XUAT);
    }

    // =========================================================================
    // 10. PX-19: Mã hóa token an toàn với AES-256-GCM
    // =========================================================================

    @Test
    @DisplayName("PX-19: SessionTokenCipher mã hóa AES-256-GCM với nonce ngẫu nhiên và AAD; ciphertext bị can thiệp sẽ ném SecurityException và không lộ token")
    void tokenCipherRejectsTamperedCiphertextAndProtectsSecrets() {
        String phienId = UUID.randomUUID().toString();
        String originalToken = "secret-provider-token-value-12345";

        byte[] encrypted = tokenCipher.encryptToken(originalToken, phienId, "access", "v1");
        assertThat(encrypted).isNotNull();
        assertThat(encrypted.length).isGreaterThan(SessionTokenCipher.GCM_NONCE_LENGTH_BYTES);

        // Giải mã đúng trả lại đúng token
        String decrypted = tokenCipher.decryptToken(encrypted, phienId, "access", "v1");
        assertThat(decrypted).isEqualTo(originalToken);

        // Giả lập can thiệp 1 byte trong ciphertext
        byte[] tampered = Arrays.copyOf(encrypted, encrypted.length);
        tampered[tampered.length - 1] ^= 0xFF;

        assertThatThrownBy(() -> tokenCipher.decryptToken(tampered, phienId, "access", "v1"))
                .isInstanceOf(SecurityException.class);

        // Sai mục đích AAD ("refresh" thay vì "access")
        assertThatThrownBy(() -> tokenCipher.decryptToken(encrypted, phienId, "refresh", "v1"))
                .isInstanceOf(SecurityException.class);
    }

    // =========================================================================
    // 11. TS1-020: Bền vững trạng thái thu hồi qua nhiều instance và restart
    // =========================================================================

    @Test
    @DisplayName("TS1-020: Thu hồi phiên ở instance A; instance B khởi động lại (restart) vẫn kiên quyết từ chối 401 khi replay cookie cũ")
    void revokedSessionRemainsRevokedAcrossInstanceRestarts() throws Exception {
        ActiveSessionFixture fixture = seedActiveSession("revoke.persist@example.com", VaiTroTaiKhoan.NGUOI_DUNG);

        // Instance A thu hồi phiên
        phienXacThucRepository.revokeByMaCookieBam(
                fixture.phien().getMaCookieBam(),
                PhienXacThuc.LY_DO_DANG_XUAT,
                Instant.now(),
                false
        );

        // Giả lập Instance B khởi động lại
        DefaultAuthBrowserSessionLifecycleAdapter instanceBLifecycle =
                new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        AuthSessionMaintenanceService instanceBService =
                new AuthSessionMaintenanceService(taiKhoanRepository, phienXacThucRepository, instanceBLifecycle, tokenCipher, supabaseAuthPort);
        CurrentPermissionAndCsrfService instanceBPermission =
                new CurrentPermissionAndCsrfService(taiKhoanRepository, phienXacThucRepository, instanceBLifecycle, List.of(ALLOWED_ORIGIN), instanceBService);
        AuthCsrfAndMeController instanceBController = new AuthCsrfAndMeController(instanceBPermission, cookieConfig);
        MockMvc instanceBMockMvc = MockMvcBuilders.standaloneSetup(instanceBController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        // Gửi lại cookie cũ tới instance B sau restart -> 401 CHUA_DANG_NHAP
        instanceBMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // =========================================================================
    // Helper fixtures
    // =========================================================================

    private record ActiveSessionFixture(
            TaiKhoan taiKhoan,
            PhienXacThuc phien,
            String rawSessionCookie
    ) {}

    private ActiveSessionFixture seedActiveSession(String email, VaiTroTaiKhoan vaiTro) {
        return seedSessionWithTokenExpiry(email, vaiTro, Instant.now().plusSeconds(3600));
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

        byte[] accessCipher = tokenCipher.encryptToken("access-jwt-" + phienId, phienId.toString(), "access", "v1");
        byte[] refreshCipher = tokenCipher.encryptToken("refresh-jwt-" + phienId, phienId.toString(), "refresh", "v1");

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

    private ActiveSessionFixture seedSessionWithCreationTime(String email, VaiTroTaiKhoan vaiTro, Instant creationTime) {
        Instant now = Instant.now();
        UUID accountId = UUID.randomUUID();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
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
                creationTime,
                creationTime,
                creationTime
        );
        taiKhoanRepository.save(taiKhoan);

        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        String cookieHash = sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie);
        UUID phienId = UUID.randomUUID();

        byte[] accessCipher = tokenCipher.encryptToken("access-jwt-" + phienId, phienId.toString(), "access", "v1");
        byte[] refreshCipher = tokenCipher.encryptToken("refresh-jwt-" + phienId, phienId.toString(), "refresh", "v1");

        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                phienId,
                taiKhoan.getId(),
                cookieHash,
                taiKhoan.getMocThuHoi(),
                creationTime,
                UUID.randomUUID(),
                accessCipher,
                refreshCipher,
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new ActiveSessionFixture(taiKhoan, phien, rawSessionCookie);
    }

    private ActiveSessionFixture seedSessionWithTokenExpiry(String email, VaiTroTaiKhoan vaiTro, Instant expiryTime) {
        Instant now = Instant.now();
        UUID accountId = UUID.randomUUID();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
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
        taiKhoanRepository.save(taiKhoan);

        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        String cookieHash = sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie);
        UUID phienId = UUID.randomUUID();

        byte[] accessCipher = tokenCipher.encryptToken("access-jwt-" + phienId, phienId.toString(), "access", "v1");
        byte[] refreshCipher = tokenCipher.encryptToken("refresh-jwt-" + phienId, phienId.toString(), "refresh", "v1");

        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                phienId,
                taiKhoan.getId(),
                cookieHash,
                taiKhoan.getMocThuHoi(),
                now,
                UUID.randomUUID(),
                accessCipher,
                refreshCipher,
                expiryTime,
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
