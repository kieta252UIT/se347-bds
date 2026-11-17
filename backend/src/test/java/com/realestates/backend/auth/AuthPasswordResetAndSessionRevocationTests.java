package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.AuthPasswordResetReconciliationService;
import com.realestates.backend.auth.application.AuthPasswordResetService;
import com.realestates.backend.auth.application.AuthSessionMaintenanceService;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.application.SupabaseVerifiedIdentity;
import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.auth.controller.AuthPasswordResetController;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.InvalidVerificationLinkException;
import com.realestates.backend.auth.exception.PasswordPolicyViolationException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryAuthEmailRateLimiter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.auth.infrastructure.SessionTokenCryptoService;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Bộ kiểm thử toàn diện cho task "Đặt lại mật khẩu và thu hồi mọi phiên cũ, gồm trường hợp lỗi giữa Auth–DB"
 * theo đặc tả:
 * - SRS §3.1.15 FR-15 / UC-29 / AC-15.3, AC-15.4, AC-09.2
 * - ADR-0008, ADR-0009, ADR-0010, ADR-0012
 * - thiet-ke-phien-sprint-1.md §5, §5.1, §5.2, §5.3, §5.4, §7
 * - quyet-dinh-hoan-thien.md PHIEN-09, PHIEN-10, PHIEN-16
 * - openapi-sprint-1.yaml lines 1039-1413 (/api/auth/password/reset-requests & /api/auth/password/reset)
 * - test-specification-sprint-1.md: TS1-010, TS1-011, TS1-017, TS1-018.
 */
class AuthPasswordResetAndSessionRevocationTests {

    private static final String ALLOWED_ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final String ENCRYPTION_KEY = "TEST_ENCRYPTION_KEY_32_BYTES_GCM!";

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

    static class RecordingPasswordResetSupabaseAuthPort implements SupabaseAuthPort {
        final List<String> recoveryEmailsSent = new ArrayList<>();
        final Map<String, SupabaseVerifiedIdentity> validRecoveryTokenHashes = new HashMap<>();
        final Set<String> consumedTokenHashes = new HashSet<>();
        final Map<UUID, String> userPasswordResetMarkers = new HashMap<>();
        final Map<UUID, String> userPasswords = new HashMap<>();

        boolean throwOnVerify = false;
        boolean throwOnUpdatePassword = false;
        boolean providerUnavailableOnUpdate = false;
        boolean providerUnavailableOnMarker = false;
        boolean rejectPasswordPolicyOnUpdate = false;

        @Override
        public SupabaseRegisteredUser registerUser(String normalizedEmail, String rawPassword, String normalizedHoTen, String normalizedSoDienThoai) {
            return new SupabaseRegisteredUser(UUID.randomUUID(), normalizedEmail, false);
        }

        @Override
        public void sendSignupVerificationEmail(String normalizedEmail) {
        }

        @Override
        public void sendPasswordRecoveryEmail(String normalizedEmail) {
            recoveryEmailsSent.add(normalizedEmail);
        }

        @Override
        public SupabaseVerifiedIdentity verifyPasswordRecoveryTokenHash(String tokenHash) {
            if (throwOnVerify) {
                throw new AuthProviderUnavailableException("Provider verify recovery unavailable");
            }
            if (consumedTokenHashes.contains(tokenHash)) {
                throw new InvalidVerificationLinkException("Liên kết đã được sử dụng.");
            }
            SupabaseVerifiedIdentity identity = validRecoveryTokenHashes.get(tokenHash);
            if (identity == null) {
                throw new InvalidVerificationLinkException("Bằng chứng recovery không hợp lệ hoặc đã hết hạn.");
            }
            consumedTokenHashes.add(tokenHash);
            return identity;
        }

        @Override
        public void updatePasswordWithResetMarker(UUID authUserId, String newPassword, UUID operationId) {
            if (providerUnavailableOnUpdate) {
                throw new AuthProviderUnavailableException("Provider timeout khi cập nhật mật khẩu");
            }
            if (rejectPasswordPolicyOnUpdate) {
                throw new PasswordPolicyViolationException("Mật khẩu không đạt chính sách Supabase Auth.");
            }
            if (throwOnUpdatePassword) {
                throw new RuntimeException("Lỗi hệ thống bất ngờ tại provider");
            }
            userPasswords.put(authUserId, newPassword);
            userPasswordResetMarkers.put(authUserId, operationId != null ? operationId.toString() : null);
        }

        @Override
        public Optional<String> getPasswordResetMarker(UUID authUserId) {
            if (providerUnavailableOnMarker) {
                throw new AuthProviderUnavailableException("Provider timeout khi lấy marker");
            }
            return Optional.ofNullable(userPasswordResetMarkers.get(authUserId));
        }

        @Override
        public void revokeProviderSessionLocal(String rawAccessToken) {
        }
    }

    private MutableClock clock;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private SessionTokenCryptoService tokenCryptoService;
    private RecordingPasswordResetSupabaseAuthPort supabaseAuthPort;
    private InMemoryAuthEmailRateLimiter rateLimiter;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private AuthSessionMaintenanceService sessionMaintenanceService;
    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private AuthPasswordResetService passwordResetService;
    private AuthPasswordResetReconciliationService reconciliationService;
    private AuthPasswordResetController passwordResetController;
    private AuthCsrfAndMeController csrfAndMeController;
    private CsrfAndOriginProtectionFilter csrfFilter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.clock = new MutableClock(Instant.parse("2026-11-16T10:00:00Z"));
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.phienXacThucRepository = new InMemoryPhienXacThucRepository();
        this.tokenCryptoService = new SessionTokenCryptoService("v1:" + ENCRYPTION_KEY, "v1");
        this.supabaseAuthPort = new RecordingPasswordResetSupabaseAuthPort();
        this.rateLimiter = new InMemoryAuthEmailRateLimiter(clock);
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

        this.passwordResetService = new AuthPasswordResetService(
                taiKhoanRepository,
                phienXacThucRepository,
                supabaseAuthPort,
                rateLimiter
        );

        this.reconciliationService = new AuthPasswordResetReconciliationService(
                taiKhoanRepository,
                supabaseAuthPort
        );

        this.passwordResetController = new AuthPasswordResetController(passwordResetService);
        this.csrfAndMeController = new AuthCsrfAndMeController(permissionAndCsrfService, cookieConfig);
        this.csrfFilter = new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig);

        this.mockMvc = MockMvcBuilders.standaloneSetup(passwordResetController, csrfAndMeController)
                .setControllerAdvice(
                        new AuthExceptionHandler(),
                        new AuthSessionSecurityExceptionHandler(),
                        new GlobalApiExceptionHandler()
                )
                .addFilters(csrfFilter)
                .build();
    }

    // =========================================================================
    // NHÓM 1: TS1-011 & UC-29 / AC-15.3: Yêu cầu khôi phục mật khẩu (reset-requests)
    // =========================================================================

    @Test
    @DisplayName("TS1-011 / UC-29: POST /api/auth/password/reset-requests tiếp nhận email đã đăng ký, trả 202 trung lập")
    void yeuCauDatLaiMatKhau_thanhCong_traVe202TrungLap() throws Exception {
        TaiKhoan account = seedAccount("user@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("user@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.trangThai").value("da_tiep_nhan"))
                .andExpect(jsonPath("$.thongDiep").value("Nếu email đã đăng ký, hướng dẫn khôi phục sẽ được gửi."));

        assertThat(supabaseAuthPort.recoveryEmailsSent).containsExactly("user@example.com");
    }

    @Test
    @DisplayName("TS1-011 / UC-29: POST /api/auth/password/reset-requests với email chưa tồn tại vẫn trả 202 trung lập không tiết lộ thông tin")
    void yeuCauDatLaiMatKhau_emailChuaTonTai_traVe202TrungLapKhongTietLo() throws Exception {
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("nonexistent@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.trangThai").value("da_tiep_nhan"))
                .andExpect(jsonPath("$.thongDiep").value("Nếu email đã đăng ký, hướng dẫn khôi phục sẽ được gửi."));

        // Gọi provider recovery email cho email chưa tồn tại (Supabase Auth cũng xử lý trung lập)
        assertThat(supabaseAuthPort.recoveryEmailsSent).containsExactly("nonexistent@example.com");
    }

    @Test
    @DisplayName("TS1-017 / UC-29: Tài khoản bị khóa (bi_khoa) vẫn gửi được yêu cầu reset-requests và trả 202")
    void yeuCauDatLaiMatKhau_taiKhoanDangBiKhoa_vanTiepNhan202() throws Exception {
        TaiKhoan account = seedAccount("locked@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA);
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("locked@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.trangThai").value("da_tiep_nhan"));

        assertThat(supabaseAuthPort.recoveryEmailsSent).containsExactly("locked@example.com");
    }

    @Test
    @DisplayName("TS1-011: Rate limiting cho reset-requests (1 lần / 60 giây / email)")
    void yeuCauDatLaiMatKhau_rateLimitCooldown_chan429() throws Exception {
        seedAccount("ratelimit@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        // Lần 1: Thành công 202
        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("ratelimit@example.com")))
                .andExpect(status().isAccepted());

        // Lần 2 (ngay sau đó): Bị chặn 429 QUA_NHIEU_YEU_CAU có Retry-After
        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("ratelimit@example.com")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.maLoi").value("QUA_NHIEU_YEU_CAU"));

        // Tua đồng hồ qua 61 giây
        clock.advance(Duration.ofSeconds(61));

        // Lần 3: Lại thành công 202
        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("ratelimit@example.com")))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("TS1-012: Thiếu hoặc sai CSRF / Origin trên reset-requests bị chặn 403 CSRF_KHONG_HOP_LE")
    void yeuCauDatLaiMatKhau_csrfHoacOriginSai_traVe403() throws Exception {
        // Thiếu Origin
        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("user@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // CSRF header không khớp CSRF cookie
        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, "wrong-header-token")
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, "valid-cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("user@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("OpenAPI: Kích thước payload vượt quá 16 KiB bị từ chối 413 NOI_DUNG_QUA_LON")
    void yeuCauDatLaiMatKhau_payloadQuaLon_traVe413() throws Exception {
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();
        String hugeEmail = "a".repeat(17000) + "@example.com";

        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + hugeEmail + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.maLoi").value("NOI_DUNG_QUA_LON"));
    }

    @Test
    @DisplayName("OpenAPI: Thừa trường thẩm quyền (role, status) trong body reset-requests trả về 400 DU_LIEU_KHONG_HOP_LE")
    void yeuCauDatLaiMatKhau_thuaTruongThamQuyen_traVe400() throws Exception {
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"role\":\"quan_tri_vien\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));
    }

    // =========================================================================
    // NHÓM 2: TS1-010, TS1-017 & UC-29 / AC-15.3, AC-15.4: Hoàn tất đặt lại mật khẩu (reset)
    // =========================================================================

    @Test
    @DisplayName("TS1-010: TokenHash không hợp lệ, hết hạn hoặc đã dùng trả về 400 LIEN_KET_KHONG_HOP_LE")
    void hoanTatDatLaiMatKhau_tokenHashKhongHopLeHoacHetHan_traVe400() throws Exception {
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset("invalid-token", "NewPassword123!")))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.maLoi").value("LIEN_KET_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("TS1-017: Mật khẩu dưới 8 ký tự hoặc quá 72 UTF-8 bytes bị từ chối 400 MAT_KHAU_KHONG_DAT")
    void hoanTatDatLaiMatKhau_chinhSachMatKhauViPham_traVe400() throws Exception {
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        // 1. Dưới 8 ký tự
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset("valid-token", "1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("MAT_KHAU_KHONG_DAT"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("matKhauMoi"));

        // 2. Quá 72 UTF-8 bytes
        String tooLongPassword = "A".repeat(73);
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset("valid-token", tooLongPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("MAT_KHAU_KHONG_DAT"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("matKhauMoi"));

        // 3. Mật khẩu rỗng
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset("valid-token", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("MAT_KHAU_KHONG_DAT"));
    }

    @Test
    @DisplayName("TS1-017 / UC-29 / AC-15.4: Đặt lại mật khẩu thành công thu hồi TẤT CẢ các phiên cũ trên mọi trình duyệt (Browser A & B)")
    void hoanTatDatLaiMatKhau_thanhCong_thuHoiToanBoPhienCuCuaTaiKhoan() throws Exception {
        // Chuẩn bị tài khoản và 2 phiên đăng nhập trên 2 browser khác nhau (A và B)
        TaiKhoan account = seedAccount("multi.browser@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        ActiveSessionFixture sessionA = seedActiveSession(account, "token-session-A");
        ActiveSessionFixture sessionB = seedActiveSession(account, "token-session-B");

        assertThat(account.getMocThuHoi()).isEqualTo(1L);

        // Đăng ký tokenHash hợp lệ cho account tại provider
        String tokenHash = "valid-recovery-token-123";
        supabaseAuthPort.validRecoveryTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(account.getSupabaseAuthUserId(), account.getEmail(), true, "Test User", "0901234567")
        );

        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        // Thực hiện đặt lại mật khẩu thành công
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset(tokenHash, "NewSecurePassword2026!")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.trangThai").value("da_dat_lai_mat_khau"))
                .andExpect(jsonPath("$.thongDiep").value("Đã đặt lại mật khẩu và thu hồi các phiên cũ. Hãy đăng nhập lại."))
                .andExpect(jsonPath("$.canDangNhapLai").value(true));

        // Kiểm tra cơ sở dữ liệu:
        TaiKhoan updatedAccount = taiKhoanRepository.findById(account.getId()).orElseThrow();
        // 1. Mốc thu hồi (epoch) đã tăng từ 1 lên 2
        assertThat(updatedAccount.getMocThuHoi()).isEqualTo(2L);
        // 2. Trạng thái đồng bộ Auth đã hoàn tất sang 'san_sang' và ma_thao_tac_auth = null
        assertThat(updatedAccount.getTrangThaiDongBoAuth()).isEqualTo("san_sang");
        assertThat(updatedAccount.getMaThaoTacAuth()).isNull();

        // 3. Cả phiên A và phiên B đều đã bị thu hồi trong DB với lý do dat_lai_mat_khau
        PhienXacThuc dbSessionA = phienXacThucRepository.findById(sessionA.phien().getId()).orElseThrow();
        PhienXacThuc dbSessionB = phienXacThucRepository.findById(sessionB.phien().getId()).orElseThrow();
        assertThat(dbSessionA.isDaThuHoi()).isTrue();
        assertThat(dbSessionA.getLyDoThuHoi()).isEqualTo("dat_lai_mat_khau");
        assertThat(dbSessionB.isDaThuHoi()).isTrue();
        assertThat(dbSessionB.getLyDoThuHoi()).isEqualTo("dat_lai_mat_khau");

        // 4. Provider đã nhận mật khẩu mới và marker operation
        assertThat(supabaseAuthPort.userPasswords.get(account.getSupabaseAuthUserId())).isEqualTo("NewSecurePassword2026!");
        assertThat(supabaseAuthPort.userPasswordResetMarkers.get(account.getSupabaseAuthUserId())).isNotEmpty();
    }

    @Test
    @DisplayName("TS1-017: Replay các phiên cũ (Browser A & B) sau khi reset mật khẩu trả về 401 CHUA_DANG_NHAP")
    void hoanTatDatLaiMatKhau_replayPhienCuSauKhiReset_biChan401() throws Exception {
        TaiKhoan account = seedAccount("replay.test@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        ActiveSessionFixture sessionA = seedActiveSession(account, "token-session-A");
        ActiveSessionFixture sessionB = seedActiveSession(account, "token-session-B");

        String tokenHash = "recovery-token-for-replay-test";
        supabaseAuthPort.validRecoveryTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(account.getSupabaseAuthUserId(), account.getEmail(), true, "Test User", "0901234567")
        );

        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        // Trước khi reset: Session A gọi GET /api/me thành công 200
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionA.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("replay.test@example.com"));

        // Thực hiện reset mật khẩu
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset(tokenHash, "NewPass987654321!")))
                .andExpect(status().isOk());

        // Sau khi reset: Replay Session A bị chặn 401 CHUA_DANG_NHAP
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionA.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // Replay Session B cũng bị chặn 401 CHUA_DANG_NHAP
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, sessionB.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
    }

    @Test
    @DisplayName("TS1-017 / AC-09.2: Tài khoản bị khóa (bi_khoa) sau khi reset vẫn giữ trạng thái khóa; mở khóa không hồi sinh phiên cũ")
    void hoanTatDatLaiMatKhau_taiKhoanBiKhoa_vanGiuKhoaVaKhongHoiSinhPhienCu() throws Exception {
        TaiKhoan account = seedAccount("locked.user@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA);
        ActiveSessionFixture oldSession = seedActiveSession(account, "token-locked-user");

        String tokenHash = "recovery-token-locked-user";
        supabaseAuthPort.validRecoveryTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(account.getSupabaseAuthUserId(), account.getEmail(), true, "Test User", "0901234567")
        );

        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        // Reset mật khẩu cho tài khoản bị khóa
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset(tokenHash, "NewPassForLockedUser1!")))
                .andExpect(status().isOk());

        // Tài khoản VẪN BỊ KHÓA trong DB
        TaiKhoan reloaded = taiKhoanRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getTrangThai()).isEqualTo(TrangThaiTaiKhoan.BI_KHOA);

        // Mở khóa tài khoản (giả lập thao tác quản trị cập nhật trạng thái hoạt động)
        TaiKhoan unlocked = TaiKhoan.khoiPhucTuLuuTru(
                reloaded.getId(),
                reloaded.getSupabaseAuthUserId(),
                reloaded.getEmail(),
                reloaded.getHoTen(),
                reloaded.getSoDienThoai(),
                reloaded.getVaiTro(),
                TrangThaiTaiKhoan.HOAT_DONG,
                reloaded.isEmailDaXacMinh(),
                reloaded.getMocThuHoi(),
                reloaded.getTrangThaiDongBoAuth(),
                reloaded.getMaThaoTacAuth(),
                reloaded.getCapNhatAuthLuc(),
                reloaded.getTaoLuc(),
                Instant.now()
        );
        taiKhoanRepository.save(unlocked);

        // Phiên cũ vẫn bị từ chối 401 vì epoch đã tăng và bản ghi phiên đã bị thu hồi
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, oldSession.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
    }

    @Test
    @DisplayName("thiet-ke-phien-sprint-1.md §5.2 bước 5: Provider từ chối do chính sách mật khẩu; CAS mở gate về 'san_sang', giữ epoch tăng và trả 400")
    void hoanTatDatLaiMatKhau_providerTuChoiMatKhau_casMoGateVaGiuEpochTang_traVe400() throws Exception {
        TaiKhoan account = seedAccount("policy.reject@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        ActiveSessionFixture oldSession = seedActiveSession(account, "token-policy-reject");
        long initialEpoch = account.getMocThuHoi();

        String tokenHash = "recovery-token-policy-reject";
        supabaseAuthPort.validRecoveryTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(account.getSupabaseAuthUserId(), account.getEmail(), true, "Test User", "0901234567")
        );
        supabaseAuthPort.rejectPasswordPolicyOnUpdate = true; // Provider từ chối

        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset(tokenHash, "PasswordRejectedByProvider!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("MAT_KHAU_KHONG_DAT"));

        // Kiểm tra tài khoản:
        TaiKhoan reloaded = taiKhoanRepository.findById(account.getId()).orElseThrow();
        // 1. Gate được mở về 'san_sang'
        assertThat(reloaded.getTrangThaiDongBoAuth()).isEqualTo("san_sang");
        assertThat(reloaded.getMaThaoTacAuth()).isNull();
        // 2. Epoch đã tăng để bảo vệ và không phục hồi phiên cũ
        assertThat(reloaded.getMocThuHoi()).isGreaterThan(initialEpoch);

        // Phiên cũ không được hồi sinh
        PhienXacThuc dbSession = phienXacThucRepository.findById(oldSession.phien().getId()).orElseThrow();
        assertThat(dbSession.isDaThuHoi()).isTrue();
    }

    // =========================================================================
    // NHÓM 3: TS1-018: Lỗi giữa Auth–DB, pending sync, phục hồi worker & CLI
    // =========================================================================

    @Test
    @DisplayName("TS1-018 / §5.2 bước 6: Provider lỗi mạng / 503 khi đổi mật khẩu; app chuyển 'can_khoi_phuc', thu hồi phiên sớm và trả 503")
    void hoanTatDatLaiMatKhau_loiKetNoiProvider_chuyenCanKhoiPhucVaThuHoiPhienSom_traVe503() throws Exception {
        TaiKhoan account = seedAccount("network.fault@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        ActiveSessionFixture oldSession = seedActiveSession(account, "token-fault-session");

        String tokenHash = "recovery-token-network-fault";
        supabaseAuthPort.validRecoveryTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(account.getSupabaseAuthUserId(), account.getEmail(), true, "Test User", "0901234567")
        );
        supabaseAuthPort.providerUnavailableOnUpdate = true; // Giả lập lỗi 503 / timeout provider

        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset(tokenHash, "ValidNewPass12345!")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.maLoi").value("KET_QUA_DAT_LAI_CHUA_XAC_DINH"));

        // Kiểm tra tài khoản và phiên:
        TaiKhoan reloaded = taiKhoanRepository.findById(account.getId()).orElseThrow();
        // 1. Trạng thái dở 'can_khoi_phuc' và lưu ma_thao_tac_auth
        assertThat(reloaded.getTrangThaiDongBoAuth()).isEqualTo("can_khoi_phuc");
        assertThat(reloaded.getMaThaoTacAuth()).isNotNull();

        // 2. Bất biến thu hồi sớm (§5.2): Phiên cũ ĐÃ BỊ THU HỒI trong DB trước khi gọi provider
        PhienXacThuc dbSession = phienXacThucRepository.findById(oldSession.phien().getId()).orElseThrow();
        assertThat(dbSession.isDaThuHoi()).isTrue();

        // 3. Replay phiên cũ trong cửa sổ lỗi vẫn bị chặn 401
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, oldSession.rawSessionCookie())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
    }

    @Test
    @DisplayName("TS1-018 / §5.2 bước 2: Tài khoản đang ở 'dang_dat_lai' hoặc 'can_khoi_phuc' bị chặn yêu cầu reset mới với 409 DAT_LAI_DANG_XU_LY")
    void hoanTatDatLaiMatKhau_thaoTacDangXuLy_chanThaoTacMoiTraVe409() throws Exception {
        TaiKhoan account = seedAccount("pending.reset@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        UUID existingOp = UUID.randomUUID();
        // Đặt tài khoản vào trạng thái dở
        taiKhoanRepository.updateDongBoAuthCas(account.getId(), null, "can_khoi_phuc", existingOp, Instant.now());

        String tokenHash = "recovery-token-pending";
        supabaseAuthPort.validRecoveryTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(account.getSupabaseAuthUserId(), account.getEmail(), true, "Test User", "0901234567")
        );

        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        // Thử thực hiện reset mới trên tài khoản đang treo
        mockMvc.perform(post("/api/auth/password/reset")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonReset(tokenHash, "AnotherPassword2026!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("DAT_LAI_DANG_XU_LY"));
    }

    @Test
    @DisplayName("TS1-018 / §5.3: Worker khôi phục đối chiếu provider marker khớp -> CAS hoàn tất app sang 'san_sang'")
    void workerKhoiPhuc_markerKhop_casHoanTatThanhCong() {
        TaiKhoan account = seedAccount("reconcile.match@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        UUID operationId = UUID.randomUUID();

        // Tài khoản bị dở 'dang_dat_lai' từ 35 giây trước (vượt ngưỡng 30s)
        taiKhoanRepository.updateDongBoAuthCas(account.getId(), null, "dang_dat_lai", operationId, Instant.now().minusSeconds(35));

        // Provider đã ghi nhận thành công marker operation này
        supabaseAuthPort.userPasswordResetMarkers.put(account.getSupabaseAuthUserId(), operationId.toString());

        // Chạy worker khôi phục
        int reconciledCount = reconciliationService.reconcilePendingAccounts();
        assertThat(reconciledCount).isEqualTo(1);

        // Kiểm tra tài khoản đã hoàn tất về 'san_sang'
        TaiKhoan reloaded = taiKhoanRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getTrangThaiDongBoAuth()).isEqualTo("san_sang");
        assertThat(reloaded.getMaThaoTacAuth()).isNull();
    }

    @Test
    @DisplayName("TS1-018 / §5.3: Worker khôi phục khi provider marker không khớp hoặc thiếu -> giữ 'can_khoi_phuc', không tự mở gate")
    void workerKhoiPhuc_markerKhongKhop_giuCanKhoiPhucKhongMoGate() {
        TaiKhoan account = seedAccount("reconcile.mismatch@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        UUID operationId = UUID.randomUUID();

        taiKhoanRepository.updateDongBoAuthCas(account.getId(), null, "dang_dat_lai", operationId, Instant.now().minusSeconds(35));

        // Provider chưa có marker hoặc marker khác
        supabaseAuthPort.userPasswordResetMarkers.put(account.getSupabaseAuthUserId(), "different-marker-or-null");

        int reconciledCount = reconciliationService.reconcilePendingAccounts();
        assertThat(reconciledCount).isEqualTo(0);

        TaiKhoan reloaded = taiKhoanRepository.findById(account.getId()).orElseThrow();
        // Không tự mở gate: chuyển sang 'can_khoi_phuc' và giữ operationId
        assertThat(reloaded.getTrangThaiDongBoAuth()).isEqualTo("can_khoi_phuc");
        assertThat(reloaded.getMaThaoTacAuth()).isEqualTo(operationId);
    }

    @Test
    @DisplayName("TS1-018 / §5.4: Lệnh quản trị CLI dongThaoTacThatBaiBoiQuanTri đóng thao tác dở, mở gate về 'san_sang' và giữ epoch đã tăng")
    void quanTriDongThaoTacThatBai_moGateVeSanSangNhungGiuEpochTang() throws Exception {
        TaiKhoan account = seedAccount("admin.intervention@example.com", VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG);
        UUID stuckOperationId = UUID.randomUUID();
        long initialEpoch = account.getMocThuHoi();

        // Giả lập tài khoản bị kẹt ở 'can_khoi_phuc'
        taiKhoanRepository.updateDongBoAuthCas(account.getId(), null, "can_khoi_phuc", stuckOperationId, Instant.now());

        // Quản trị viên kiểm tra trạng thái qua CLI (§5.4)
        var statusOpt = reconciliationService.kiemTraTrangThaiThaoTac(account.getId());
        assertThat(statusOpt).isPresent();
        assertThat(statusOpt.get().trangThaiDongBoAuth()).isEqualTo("can_khoi_phuc");
        assertThat(statusOpt.get().maThaoTacAuth()).isEqualTo(stuckOperationId);

        // Quản trị viên đóng thao tác thất bại với operation ID dự kiến
        boolean closed = reconciliationService.dongThaoTacThatBaiBoiQuanTri(
                account.getId(),
                stuckOperationId,
                "Auth handler finished with error, confirmed via provider audit log"
        );
        assertThat(closed).isTrue();

        // Kiểm tra sau khi đóng: gate đã mở về 'san_sang'
        TaiKhoan reloaded = taiKhoanRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getTrangThaiDongBoAuth()).isEqualTo("san_sang");
        assertThat(reloaded.getMaThaoTacAuth()).isNull();

        // Người dùng có thể yêu cầu gửi lại email recovery link mới
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();
        mockMvc.perform(post("/api/auth/password/reset-requests")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonEmail("admin.intervention@example.com")))
                .andExpect(status().isAccepted());
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static String jsonEmail(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private static String jsonReset(String tokenHash, String matKhauMoi) {
        return "{\"tokenHash\":\"" + tokenHash + "\",\"matKhauMoi\":\"" + matKhauMoi + "\"}";
    }

    private TaiKhoan seedAccount(String email, VaiTroTaiKhoan vaiTro, TrangThaiTaiKhoan trangThai) {
        Instant now = Instant.now();
        TaiKhoan account = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                email,
                "Nguyễn Văn Test",
                "0901234567",
                vaiTro,
                trangThai,
                true,
                1L,
                "san_sang",
                null,
                now,
                now,
                now
        );
        return taiKhoanRepository.save(account);
    }

    private record ActiveSessionFixture(PhienXacThuc phien, String rawSessionCookie) {}

    private ActiveSessionFixture seedActiveSession(TaiKhoan account, String rawToken) {
        Instant now = Instant.now();
        UUID phienId = UUID.randomUUID();
        String rawSessionCookie = "test_cookie_" + UUID.randomUUID();
        String maCookieBam = sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie);

        byte[] encAccess = tokenCryptoService.encryptAccessToken(phienId, rawToken);
        byte[] encRefresh = tokenCryptoService.encryptRefreshToken(phienId, "refresh-" + rawToken);

        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                phienId,
                account.getId(),
                maCookieBam,
                account.getMocThuHoi(),
                now,
                UUID.randomUUID(),
                encAccess,
                encRefresh,
                now.plusSeconds(3600),
                tokenCryptoService.getActiveKeyVersion()
        );
        phienXacThucRepository.save(phien);
        return new ActiveSessionFixture(phien, rawSessionCookie);
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
