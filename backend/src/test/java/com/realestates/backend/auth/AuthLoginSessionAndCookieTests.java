package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthLoginService;
import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseAuthenticatedSession;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.controller.AuthLoginController;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.DangNhapKetQua;
import com.realestates.backend.auth.dto.LoiApiResponse;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import com.realestates.backend.auth.exception.AuthSyncInProgressException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InvalidCredentialsException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryAuthEmailRateLimiter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.auth.infrastructure.SessionTokenCryptoService;
import com.realestates.backend.auth.infrastructure.SupabaseAuthProperties;
import com.realestates.backend.auth.infrastructure.SupabaseAuthRestClientAdapter;
import com.realestates.backend.config.AuthSessionCookieConfig;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/**
 * Kiểm thử đơn vị và tích hợp cho task "Tạo phiên PostgreSQL và cookie sau đăng nhập hợp lệ"
 * theo đặc tả UC-27, FR-15, AC-15.4, TS1-012, TS1-013, PHIEN-02..PHIEN-06, PX-01, PX-10, PX-19.
 */
class AuthLoginSessionAndCookieTests {

    static class MutableClock extends Clock {
        private Instant currentInstant;

        MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        void advance(Duration duration) {
            this.currentInstant = this.currentInstant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    static class RecordingLoginSupabaseAuthPort implements SupabaseAuthPort {
        final Map<String, String> passwordsByEmail = new HashMap<>();
        final Map<String, SupabaseAuthenticatedSession> sessionsByEmail = new HashMap<>();
        final List<String> locallyRevokedAccessTokens = new ArrayList<>();
        final List<String> resentVerificationEmails = new ArrayList<>();
        Runnable onAuthenticateHook;

        void registerValidCredential(
                String normalizedEmail,
                String rawPassword,
                UUID supabaseUserId,
                boolean emailConfirmed,
                String hoTenMeta,
                String soDienThoaiMeta
        ) {
            passwordsByEmail.put(normalizedEmail, rawPassword);
            sessionsByEmail.put(
                    normalizedEmail,
                    new SupabaseAuthenticatedSession(
                            supabaseUserId,
                            normalizedEmail,
                            emailConfirmed,
                            UUID.randomUUID(),
                            "sb_access_" + UUID.randomUUID(),
                            "sb_refresh_" + UUID.randomUUID(),
                            Instant.parse("2026-11-14T11:00:00Z"),
                            hoTenMeta,
                            soDienThoaiMeta
                    )
            );
        }

        @Override
        public SupabaseRegisteredUser registerUser(
                String normalizedEmail,
                String rawPassword,
                String normalizedHoTen,
                String normalizedSoDienThoai
        ) {
            return new SupabaseRegisteredUser(UUID.randomUUID(), normalizedEmail, false);
        }

        @Override
        public void sendSignupVerificationEmail(String normalizedEmail) {
            resentVerificationEmails.add(normalizedEmail);
        }

        @Override
        public SupabaseAuthenticatedSession authenticateWithPassword(String normalizedEmail, String rawPassword) {
            if (onAuthenticateHook != null) {
                onAuthenticateHook.run();
            }
            String expectedPassword = passwordsByEmail.get(normalizedEmail);
            if (expectedPassword == null || !expectedPassword.equals(rawPassword)) {
                throw new InvalidCredentialsException();
            }
            SupabaseAuthenticatedSession template = sessionsByEmail.get(normalizedEmail);
            if (template == null) {
                throw new InvalidCredentialsException();
            }
            // Mỗi lần đăng nhập hợp lệ cấp một cặp session/token mới từ provider
            return new SupabaseAuthenticatedSession(
                    template.supabaseAuthUserId(),
                    template.email(),
                    template.emailConfirmed(),
                    UUID.randomUUID(),
                    "sb_access_" + UUID.randomUUID(),
                    "sb_refresh_" + UUID.randomUUID(),
                    template.accessTokenExpiresAt(),
                    template.hoTenMetadata(),
                    template.soDienThoaiMetadata()
            );
        }

        @Override
        public void revokeProviderSessionLocal(String accessToken) {
            if (accessToken != null) {
                locallyRevokedAccessTokens.add(accessToken);
            }
        }
    }

    private MutableClock clock;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private RecordingLoginSupabaseAuthPort supabaseAuthPort;
    private InMemoryAuthEmailRateLimiter rateLimiter;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private SessionTokenCryptoService sessionTokenCryptoService;
    private AuthSessionCookieConfig cookieConfig;
    private AuthLoginService loginService;
    private AuthLoginController loginController;
    private AuthExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        this.clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.phienXacThucRepository = new InMemoryPhienXacThucRepository();
        this.supabaseAuthPort = new RecordingLoginSupabaseAuthPort();
        this.rateLimiter = new InMemoryAuthEmailRateLimiter(clock);
        this.sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                "test-csrf-secret-key-32-bytes-ok",
                phienXacThucRepository
        );
        this.sessionTokenCryptoService = new SessionTokenCryptoService(
                "v1:test-key-v1-32-bytes-secret-material,v2:test-key-v2-32-bytes-secret-material",
                "v1"
        );
        this.cookieConfig = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(cookieConfig, "cookieSecure", true);
        ReflectionTestUtils.setField(cookieConfig, "sessionCookieName", "__Host-se214_session");
        ReflectionTestUtils.setField(cookieConfig, "sessionMaxAgeSeconds", 31536000L);
        ReflectionTestUtils.setField(cookieConfig, "csrfCookieName", "__Host-se214_csrf");

        this.loginService = new AuthLoginService(
                taiKhoanRepository,
                phienXacThucRepository,
                supabaseAuthPort,
                rateLimiter,
                sessionLifecycleAdapter,
                sessionTokenCryptoService,
                clock
        );
        this.loginController = new AuthLoginController(loginService, cookieConfig);
        this.exceptionHandler = new AuthExceptionHandler();
    }

    @Test
    @DisplayName("TS1-012 / PHIEN-02 / PHIEN-04 / PHIEN-05 / PHIEN-06 / PX-01 / PX-19: Đăng nhập hợp lệ cho cả 3 vai trò đọc vaiTro từ DB, tạo phiên PostgreSQL mã hóa AES-256-GCM và trả 2 dòng Set-Cookie độc lập")
    void validLoginCreatesEncryptedPostgresSessionAndIssuesOpaqueCookiesForAllRoles() {
        for (VaiTroTaiKhoan role : VaiTroTaiKhoan.values()) {
            UUID accountId = UUID.randomUUID();
            UUID supabaseUserId = UUID.randomUUID();
            String email = role.getGiaTri() + "@example.com";
            String password = "MatKhauHopLe!123";

            TaiKhoan seededAccount = TaiKhoan.khoiPhucTuLuuTru(
                    accountId,
                    supabaseUserId,
                    email,
                    "Nhân sự " + role.getGiaTri(),
                    "0901234567",
                    role,
                    TrangThaiTaiKhoan.HOAT_DONG,
                    true,
                    3L,
                    "san_sang",
                    clock.instant(),
                    clock.instant(),
                    clock.instant()
            );
            taiKhoanRepository.save(seededAccount);
            supabaseAuthPort.registerValidCredential(
                    email,
                    password,
                    supabaseUserId,
                    true,
                    "Nhân sự " + role.getGiaTri(),
                    "0901234567"
            );

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("198.51.100.10");

            ResponseEntity<DangNhapKetQua> response = loginController.dangNhap(
                    request,
                    Map.of("email", "  " + email.toUpperCase() + " ", "matKhau", password)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(response.getHeaders().getFirst(HttpHeaders.VARY)).isEqualTo(HttpHeaders.COOKIE);

            DangNhapKetQua body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.taiKhoan().id()).isEqualTo(accountId);
            assertThat(body.taiKhoan().email()).isEqualTo(email);
            assertThat(body.taiKhoan().vaiTro()).isEqualTo(role);
            assertThat(body.taiKhoan().trangThai()).isEqualTo(TrangThaiTaiKhoan.HOAT_DONG);
            assertThat(body.taiKhoan().emailDaXacMinh()).isTrue();
            assertThat(body.csrfToken()).startsWith("v1.");

            // Kiểm tra 2 dòng Set-Cookie độc lập: __Host-se214_session và __Host-se214_csrf
            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(setCookies).hasSize(2);

            String sessionCookieHeader = setCookies.get(0);
            String csrfCookieHeader = setCookies.get(1);

            assertThat(sessionCookieHeader)
                    .startsWith("__Host-se214_session=")
                    .contains("Path=/")
                    .contains("Max-Age=31536000")
                    .contains("Secure")
                    .contains("HttpOnly")
                    .contains("SameSite=Lax")
                    .doesNotContainIgnoringCase("Domain=")
                    .doesNotContain("sb_access_")
                    .doesNotContain("sb_refresh_");

            assertThat(csrfCookieHeader)
                    .startsWith("__Host-se214_csrf=" + body.csrfToken())
                    .contains("Path=/")
                    .contains("Secure")
                    .contains("HttpOnly")
                    .contains("SameSite=Lax")
                    .doesNotContainIgnoringCase("Domain=");

            String rawCookieValue = extractCookieValueFromSetCookieHeader(sessionCookieHeader, "__Host-se214_session");
            assertThat(rawCookieValue).isNotBlank();
            // Giá trị cookie phiên là chuỗi opaque Base64URL 32 byte (không phải JWT 3 phần)
            assertThat(rawCookieValue.split("\\.")).hasSize(1);
            assertThat(Base64.getUrlDecoder().decode(rawCookieValue)).hasSize(32);

            // Kiểm tra CSRF token gắn chặt với ngữ cảnh cookie phiên vừa cấp
            assertThat(sessionLifecycleAdapter.verifyCsrfTokenForContext(body.csrfToken(), rawCookieValue)).isTrue();
            assertThat(sessionLifecycleAdapter.verifyCsrfTokenForContext(body.csrfToken(), null)).isFalse();

            // Kiểm tra bản ghi PhienXacThuc trong kho lưu trữ PostgreSQL
            String expectedCookieHash = sessionLifecycleAdapter.hashSessionCookie(rawCookieValue);
            assertThat(expectedCookieHash).isNotEqualTo(rawCookieValue);

            Optional<PhienXacThuc> savedSessionOpt = phienXacThucRepository.findByMaCookieBam(expectedCookieHash);
            assertThat(savedSessionOpt).isPresent();
            PhienXacThuc savedSession = savedSessionOpt.get();
            assertThat(savedSession.getTaiKhoanId()).isEqualTo(accountId);
            assertThat(savedSession.getMocThuHoiLucCap()).isEqualTo(3L);
            assertThat(savedSession.getThuHoiLuc()).isNull();
            assertThat(savedSession.getLyDoThuHoi()).isNull();
            assertThat(savedSession.isCanThuHoiProvider()).isFalse();
            assertThat(savedSession.getPhienBanKhoaMaHoa()).isEqualTo("v1");
            assertThat(savedSession.conHieuLuc(seededAccount)).isTrue();

            // Kiểm tra token mã hóa AES-256-GCM giải mã đúng với AAD chuẩn và thất bại nếu đổi sessionId hoặc đổi mục đích
            String decryptedAccess = sessionTokenCryptoService.decryptAccessToken(
                    savedSession.getId(),
                    savedSession.getPhienBanKhoaMaHoa(),
                    savedSession.getAccessTokenMaHoa()
            );
            String decryptedRefresh = sessionTokenCryptoService.decryptRefreshToken(
                    savedSession.getId(),
                    savedSession.getPhienBanKhoaMaHoa(),
                    savedSession.getRefreshTokenMaHoa()
            );
            assertThat(decryptedAccess).startsWith("sb_access_");
            assertThat(decryptedRefresh).startsWith("sb_refresh_");

            // Thử giải mã access_token bằng mục đích refresh hoặc bằng sessionId khác -> phải bị chặn bởi AAD GCM
            assertThatThrownBy(() -> sessionTokenCryptoService.decryptRefreshToken(
                    savedSession.getId(),
                    savedSession.getPhienBanKhoaMaHoa(),
                    savedSession.getAccessTokenMaHoa()
            )).isInstanceOf(IllegalStateException.class);

            assertThatThrownBy(() -> sessionTokenCryptoService.decryptAccessToken(
                    UUID.randomUUID(),
                    savedSession.getPhienBanKhoaMaHoa(),
                    savedSession.getAccessTokenMaHoa()
            )).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("PHIEN-04 / api-specification §8.6: Đăng nhập lại trên trình duyệt đã có cookie phiên chỉ thu hồi đúng phiên cũ trên trình duyệt đó (ly_do='thay_phien_dang_nhap') và giữ nguyên phiên trên thiết bị khác")
    void reloginOnSameBrowserRevokesOnlyOldBrowserSessionAndPreservesOtherDeviceSession() {
        UUID accountId = UUID.randomUUID();
        UUID supabaseUserId = UUID.randomUUID();
        String email = "multidevice@example.com";
        String password = "Password#2026";

        TaiKhoan account = TaiKhoan.taoNguoiDungDaXacMinh(
                accountId,
                supabaseUserId,
                email,
                "Nguyễn Đa Thiết Bị",
                "0911223344",
                clock.instant()
        );
        taiKhoanRepository.save(account);
        supabaseAuthPort.registerValidCredential(email, password, supabaseUserId, true, "Nguyễn Đa Thiết Bị", "0911223344");

        // 1. Đăng nhập trên Browser A (lần 1)
        MockHttpServletRequest reqBrowserA1 = new MockHttpServletRequest("POST", "/api/auth/login");
        reqBrowserA1.setRemoteAddr("198.51.100.21");
        ResponseEntity<DangNhapKetQua> resA1 = loginController.dangNhap(
                reqBrowserA1,
                Map.of("email", email, "matKhau", password)
        );
        String cookieBrowserA1 = extractCookieValueFromSetCookieHeader(
                resA1.getHeaders().get(HttpHeaders.SET_COOKIE).get(0),
                "__Host-se214_session"
        );
        String hashA1 = sessionLifecycleAdapter.hashSessionCookie(cookieBrowserA1);

        // 2. Đăng nhập trên Browser B (thiết bị khác, không gửi cookie của Browser A)
        MockHttpServletRequest reqBrowserB = new MockHttpServletRequest("POST", "/api/auth/login");
        reqBrowserB.setRemoteAddr("198.51.100.22");
        ResponseEntity<DangNhapKetQua> resB = loginController.dangNhap(
                reqBrowserB,
                Map.of("email", email, "matKhau", password)
        );
        String cookieBrowserB = extractCookieValueFromSetCookieHeader(
                resB.getHeaders().get(HttpHeaders.SET_COOKIE).get(0),
                "__Host-se214_session"
        );
        String hashB = sessionLifecycleAdapter.hashSessionCookie(cookieBrowserB);

        // 3. Đăng nhập lại trên Browser A (mang theo cookieBrowserA1)
        clock.advance(Duration.ofMinutes(2));
        MockHttpServletRequest reqBrowserA2 = new MockHttpServletRequest("POST", "/api/auth/login");
        reqBrowserA2.setRemoteAddr("198.51.100.21");
        reqBrowserA2.setCookies(new Cookie("__Host-se214_session", cookieBrowserA1));

        ResponseEntity<DangNhapKetQua> resA2 = loginController.dangNhap(
                reqBrowserA2,
                Map.of("email", email, "matKhau", password)
        );
        String cookieBrowserA2 = extractCookieValueFromSetCookieHeader(
                resA2.getHeaders().get(HttpHeaders.SET_COOKIE).get(0),
                "__Host-se214_session"
        );
        String hashA2 = sessionLifecycleAdapter.hashSessionCookie(cookieBrowserA2);

        assertThat(cookieBrowserA2).isNotEqualTo(cookieBrowserA1);

        // Phiên cũ A1 đã bị thu hồi với lý do "thay_phien_dang_nhap" và đã dọn sạch credential sau khi gọi revoke local
        PhienXacThuc sessionA1 = phienXacThucRepository.findByMaCookieBam(hashA1).orElseThrow();
        assertThat(sessionA1.isDaThuHoi()).isTrue();
        assertThat(sessionA1.getLyDoThuHoi()).isEqualTo(PhienXacThuc.LY_DO_THAY_PHIEN_DANG_NHAP);
        assertThat(sessionA1.conHieuLuc(account)).isFalse();
        assertThat(sessionA1.getAccessTokenMaHoa()).isNull();
        assertThat(sessionA1.getRefreshTokenMaHoa()).isNull();
        assertThat(supabaseAuthPort.locallyRevokedAccessTokens).hasSize(1);

        // Phiên mới A2 và phiên trên thiết bị khác B vẫn còn hiệu lực
        PhienXacThuc sessionA2 = phienXacThucRepository.findByMaCookieBam(hashA2).orElseThrow();
        PhienXacThuc sessionB = phienXacThucRepository.findByMaCookieBam(hashB).orElseThrow();
        assertThat(sessionA2.conHieuLuc(account)).isTrue();
        assertThat(sessionB.conHieuLuc(account)).isTrue();
    }

    @Test
    @DisplayName("TS1-013 / AC-15.4: Chặn đăng nhập khi sai mật khẩu (401 DANG_NHAP_KHONG_HOP_LE), chưa xác minh email (403 EMAIL_CHUA_XAC_MINH, không tự gửi lại email), hoặc tài khoản bị khóa (403 TAI_KHOAN_BI_KHOA)")
    void rejectWrongPasswordUnverifiedEmailAndLockedAccountWithoutCreatingSessionOrCookies() {
        // 1. Tài khoản chưa xác minh email
        UUID unverifiedAuthId = UUID.randomUUID();
        TaiKhoan unverifiedAccount = TaiKhoan.taoNguoiDungMoi(
                UUID.randomUUID(),
                unverifiedAuthId,
                "unverified.login@example.com",
                "Trần Chưa Xác Minh",
                null,
                clock.instant()
        );
        taiKhoanRepository.save(unverifiedAccount);
        supabaseAuthPort.registerValidCredential(
                "unverified.login@example.com",
                "CorrectPass123",
                unverifiedAuthId,
                false,
                "Trần Chưa Xác Minh",
                null
        );

        // 2. Tài khoản đã xác minh nhưng đang bị khóa (bi_khoa)
        UUID lockedAuthId = UUID.randomUUID();
        TaiKhoan lockedAccount = TaiKhoan.taoNguoiDungDaXacMinh(
                UUID.randomUUID(),
                lockedAuthId,
                "locked.login@example.com",
                "Lê Bị Khóa",
                "0909998888",
                clock.instant()
        ).capNhatTrangThai(TrangThaiTaiKhoan.BI_KHOA, clock.instant());
        taiKhoanRepository.save(lockedAccount);
        supabaseAuthPort.registerValidCredential(
                "locked.login@example.com",
                "CorrectPass123",
                lockedAuthId,
                true,
                "Lê Bị Khóa",
                "0909998888"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("198.51.100.40");

        // Case A: Sai mật khẩu -> 401 DANG_NHAP_KHONG_HOP_LE
        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", "locked.login@example.com", "matKhau", "WrongPass999")
        ))
                .isInstanceOf(InvalidCredentialsException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleInvalidCredentials(
                            (InvalidCredentialsException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(errResp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("DANG_NHAP_KHONG_HOP_LE");
                    assertThat(errResp.getBody().thongDiep()).isEqualTo("Email hoặc mật khẩu không đúng.");
                });

        // Case B: Email chưa xác minh -> 403 EMAIL_CHUA_XAC_MINH, tuyệt đối KHÔNG tự động gửi lại email xác minh
        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", "unverified.login@example.com", "matKhau", "CorrectPass123")
        ))
                .isInstanceOf(EmailNotVerifiedException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleEmailNotVerified(
                            (EmailNotVerifiedException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(errResp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("EMAIL_CHUA_XAC_MINH");
                    assertThat(errResp.getBody().thongDiep()).isEqualTo("Email chưa được xác minh.");
                });
        assertThat(supabaseAuthPort.resentVerificationEmails).isEmpty();
        assertThat(phienXacThucRepository.findByTaiKhoanId(unverifiedAccount.getId())).isEmpty();

        // Case C: Tài khoản bị khóa -> 403 TAI_KHOAN_BI_KHOA và thu hồi ngay phiên vừa sinh tại provider
        int revokedCountBeforeLocked = supabaseAuthPort.locallyRevokedAccessTokens.size();
        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", "locked.login@example.com", "matKhau", "CorrectPass123")
        ))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleAccountLocked(
                            (AccountLockedException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(errResp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("TAI_KHOAN_BI_KHOA");
                    assertThat(errResp.getBody().thongDiep()).isEqualTo("Tài khoản đang bị khóa.");
                });
        assertThat(supabaseAuthPort.locallyRevokedAccessTokens).hasSize(revokedCountBeforeLocked + 1);
        assertThat(phienXacThucRepository.findByTaiKhoanId(lockedAccount.getId())).isEmpty();
    }

    @Test
    @DisplayName("PX-10 / thiet-ke-phien-sprint-1.md §3.1: Chặn cấp phiên khi moc_thu_hoi tăng trong lúc gọi Supabase Auth (503 TAM_GIAN_DOAN) hoặc khi trang_thai_dong_bo_auth != 'san_sang' (409 DONG_BO_XAC_THUC_DANG_XU_LY)")
    void rejectLoginWhenRevocationEpochChangesDuringProviderCallOrSyncInProgress() {
        UUID accountId = UUID.randomUUID();
        UUID supabaseUserId = UUID.randomUUID();
        String email = "epoch.race@example.com";
        String password = "RacePassword123";

        TaiKhoan initialAccount = TaiKhoan.taoNguoiDungDaXacMinh(
                accountId,
                supabaseUserId,
                email,
                "Phạm Đua Tốc",
                null,
                clock.instant()
        );
        taiKhoanRepository.save(initialAccount);
        supabaseAuthPort.registerValidCredential(email, password, supabaseUserId, true, "Phạm Đua Tốc", null);

        // Mô phỏng moc_thu_hoi tăng từ 0 -> 1 ngay trong lúc đang gọi authenticateWithPassword
        supabaseAuthPort.onAuthenticateHook = () -> {
            TaiKhoan current = taiKhoanRepository.findByEmail(email).orElseThrow();
            taiKhoanRepository.save(current.tangMocThuHoi("san_sang", clock.instant()));
        };

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("198.51.100.55");

        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", email, "matKhau", password)
        ))
                .isInstanceOf(AuthProviderUnavailableException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleProviderUnavailable(
                            (AuthProviderUnavailableException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(errResp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("TAM_GIAN_DOAN");
                });

        // Không được tạo bản ghi phiên và phiên provider vừa sinh đã được thu hồi local
        assertThat(phienXacThucRepository.findByTaiKhoanId(accountId)).isEmpty();
        assertThat(supabaseAuthPort.locallyRevokedAccessTokens).hasSize(1);

        // Tiếp theo: trạng thái đồng bộ đang là "dang_dat_lai" -> 409 DONG_BO_XAC_THUC_DANG_XU_LY
        supabaseAuthPort.onAuthenticateHook = null;
        TaiKhoan syncingAccount = taiKhoanRepository.findByEmail(email).orElseThrow()
                .capNhatTrangThaiDongBoAuth("dang_dat_lai", clock.instant());
        taiKhoanRepository.save(syncingAccount);

        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", email, "matKhau", password)
        ))
                .isInstanceOf(AuthSyncInProgressException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleAuthSyncInProgress(
                            (AuthSyncInProgressException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("DONG_BO_XAC_THUC_DANG_XU_LY");
                });
        assertThat(phienXacThucRepository.findByTaiKhoanId(accountId)).isEmpty();
        assertThat(supabaseAuthPort.locallyRevokedAccessTokens).hasSize(2);
    }

    @Test
    @DisplayName("api-specification §8.8 & §8.3: Kiểm soát tần suất đăng nhập 10 lần/5 phút/(IP + email) trả 429 QUA_NHIEU_YEU_CAU; chặn trường lạ với 400 DU_LIEU_KHONG_HOP_LE")
    void enforceLoginRateLimitAndRejectExtraFields() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.88");

        // 1. Chặn trường dư (vaiTro, rememberMe) với 400 DU_LIEU_KHONG_HOP_LE
        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", "user@example.com", "matKhau", "pass", "vaiTro", "quan_tri_vien")
        ))
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> {
                    AuthValidationException valEx = (AuthValidationException) ex;
                    assertThat(valEx.getMaLoi()).isEqualTo("DU_LIEU_KHONG_HOP_LE");
                    assertThat(valEx.getTruongLoi()).anyMatch(f -> "vaiTro".equals(f.truong()));
                });

        // 2. Sau 10 lần thử đăng nhập trong 5 phút trên cùng (IP + email), lần thứ 11 bị chặn 429 QUA_NHIEU_YEU_CAU
        String targetEmail = "bruteforce@example.com";
        for (int i = 1; i <= 10; i++) {
            assertThatThrownBy(() -> loginController.dangNhap(
                    request,
                    Map.of("email", targetEmail, "matKhau", "wrong-" + UUID.randomUUID())
            )).isInstanceOf(InvalidCredentialsException.class);
        }

        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> loginController.dangNhap(
                request,
                Map.of("email", targetEmail, "matKhau", "wrong-11")
        ))
                .isInstanceOf(AuthRateLimitExceededException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleRateLimitExceeded(
                            (AuthRateLimitExceededException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(errResp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("270");
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("QUA_NHIEU_YEU_CAU");
                });
    }

    @Test
    @DisplayName("SupabaseAuthRestClientAdapter: Gọi POST /auth/v1/token?grant_type=password, giải mã session_id từ JWT claim và ánh xạ đúng lỗi email_not_confirmed / invalid_credentials")
    void supabaseAuthRestClientAdapterAuthenticatesPasswordAndExtractsSessionIdFromJwt() {
        UUID supabaseUserId = UUID.randomUUID();
        UUID expectedSessionId = UUID.randomUUID();
        String jwtPayloadJson = "{\"sub\":\"" + supabaseUserId + "\",\"session_id\":\"" + expectedSessionId + "\"}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jwtPayloadJson.getBytes(StandardCharsets.UTF_8));
        String mockJwtValue = String.join(".", encodedHeader, encodedPayload, "sig");

        AtomicReference<String> capturedQuery = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        ClientHttpRequestFactory requestFactory = (uri, httpMethod) -> {
            if ("/auth/v1/token".equals(uri.getPath())) {
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        String reqBody = getBodyAsString(StandardCharsets.UTF_8);
                        capturedQuery.set(uri.getQuery());
                        capturedBody.set(reqBody);

                        if (reqBody.contains("unconfirmed@example.com")) {
                            MockClientHttpResponse err = new MockClientHttpResponse(
                                    "{\"error_code\":\"email_not_confirmed\",\"msg\":\"Email not confirmed\"}"
                                            .getBytes(StandardCharsets.UTF_8),
                                    HttpStatus.BAD_REQUEST
                            );
                            err.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            return err;
                        }
                        if (reqBody.contains("wrongpass")) {
                            MockClientHttpResponse err = new MockClientHttpResponse(
                                    "{\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"}"
                                            .getBytes(StandardCharsets.UTF_8),
                                    HttpStatus.BAD_REQUEST
                            );
                            err.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            return err;
                        }

                        String okJson = "{"
                                + "\"access_token\":\"" + mockJwtValue + "\","
                                + "\"refresh_token\":\"refresh_stub_123\","
                                + "\"expires_at\":1794654000,"
                                + "\"user\":{"
                                + "\"id\":\"" + supabaseUserId + "\","
                                + "\"email\":\"jwt.login@example.com\","
                                + "\"email_confirmed_at\":\"2026-11-14T09:00:00Z\","
                                + "\"user_metadata\":{\"ho_ten\":\"Đỗ Văn JWT\"}"
                                + "}"
                                + "}";
                        MockClientHttpResponse ok = new MockClientHttpResponse(
                                okJson.getBytes(StandardCharsets.UTF_8),
                                HttpStatus.OK
                        );
                        ok.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return ok;
                    }
                };
            }
            throw new AssertionError("Unexpected URI path: " + uri.getPath());
        };

        SupabaseAuthProperties props = new SupabaseAuthProperties();
        props.setUrl("https://fcsbaknvtengrgsvuehv.supabase.co");
        props.setAnonKey("test-anon-key");

        SupabaseAuthRestClientAdapter adapter = new SupabaseAuthRestClientAdapter(
                props,
                RestClient.builder().requestFactory(requestFactory).build()
        );

        SupabaseAuthenticatedSession session = adapter.authenticateWithPassword("jwt.login@example.com", "goodpass");
        assertThat(capturedQuery.get()).isEqualTo("grant_type=password");
        assertThat(capturedBody.get()).contains("\"email\":\"jwt.login@example.com\"");
        assertThat(session.supabaseAuthUserId()).isEqualTo(supabaseUserId);
        assertThat(session.supabaseSessionId()).isEqualTo(expectedSessionId);
        assertThat(session.accessToken()).isEqualTo(mockJwtValue);
        assertThat(session.refreshToken()).isEqualTo("refresh_stub_123");
        assertThat(session.accessTokenExpiresAt()).isEqualTo(Instant.ofEpochSecond(1794654000L));

        assertThatThrownBy(() -> adapter.authenticateWithPassword("unconfirmed@example.com", "goodpass"))
                .isInstanceOf(EmailNotVerifiedException.class);

        assertThatThrownBy(() -> adapter.authenticateWithPassword("jwt.login@example.com", "wrongpass"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private static String extractCookieValueFromSetCookieHeader(String setCookieHeader, String cookieName) {
        String prefix = cookieName + "=";
        int start = setCookieHeader.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int valueStart = start + prefix.length();
        int semi = setCookieHeader.indexOf(';', valueStart);
        return semi > 0 ? setCookieHeader.substring(valueStart, semi) : setCookieHeader.substring(valueStart);
    }
}
