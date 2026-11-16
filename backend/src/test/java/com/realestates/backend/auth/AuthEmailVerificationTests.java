package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthEmailVerificationService;
import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.application.SupabaseVerifiedIdentity;
import com.realestates.backend.auth.controller.AuthEmailVerificationController;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.dto.LoiApiResponse;
import com.realestates.backend.auth.dto.XacMinhKetQua;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthProfileNeedsInspectionException;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InvalidVerificationLinkException;
import com.realestates.backend.auth.exception.VerificationSyncIncompleteException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryAuthEmailRateLimiter;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Kiểm thử đơn vị và tích hợp cho task "Hiện thực xác minh và gửi lại liên kết xác minh email"
 * theo đặc tả UC-26, FR-15, AC-15.2, AC-15.3, TS1-008..TS1-011, AUTH-03, AUTH-05..07, AUTH-09, AUTH-14, AUTH-15, AUTH-17, PX-20.
 */
class AuthEmailVerificationTests {

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

    static class RecordingVerificationSupabaseAuthPort implements SupabaseAuthPort {
        final List<String> resentEmails = new ArrayList<>();
        final Map<String, SupabaseVerifiedIdentity> validTokenHashes = new HashMap<>();
        final Set<String> consumedTokenHashes = new HashSet<>();
        final Set<String> expiredTokenHashes = new HashSet<>();

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
            resentEmails.add(normalizedEmail);
        }

        @Override
        public SupabaseVerifiedIdentity verifySignupEmailTokenHash(String tokenHash) {
            if (expiredTokenHashes.contains(tokenHash) || consumedTokenHashes.contains(tokenHash)) {
                throw new InvalidVerificationLinkException();
            }
            SupabaseVerifiedIdentity identity = validTokenHashes.get(tokenHash);
            if (identity == null) {
                throw new InvalidVerificationLinkException();
            }
            // Liên kết xác minh dùng một lần (one-time proof)
            consumedTokenHashes.add(tokenHash);
            return identity;
        }
    }

    private MutableClock clock;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private RecordingVerificationSupabaseAuthPort supabaseAuthPort;
    private InMemoryAuthEmailRateLimiter rateLimiter;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private AuthEmailVerificationService verificationService;
    private AuthEmailVerificationController verificationController;
    private AuthExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        this.clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.supabaseAuthPort = new RecordingVerificationSupabaseAuthPort();
        this.rateLimiter = new InMemoryAuthEmailRateLimiter(clock);
        this.sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter("test-csrf-secret-key-32-bytes-ok");
        this.cookieConfig = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(cookieConfig, "cookieSecure", true);
        ReflectionTestUtils.setField(cookieConfig, "sessionCookieName", "__Host-se214_session");
        ReflectionTestUtils.setField(cookieConfig, "sessionMaxAgeSeconds", 31536000L);
        ReflectionTestUtils.setField(cookieConfig, "csrfCookieName", "__Host-se214_csrf");

        this.verificationService = new AuthEmailVerificationService(
                taiKhoanRepository,
                supabaseAuthPort,
                rateLimiter,
                sessionLifecycleAdapter
        );
        this.verificationController = new AuthEmailVerificationController(verificationService, cookieConfig);
        this.exceptionHandler = new AuthExceptionHandler();
    }

    @Test
    @DisplayName("UC-26 / AUTH-05 / AUTH-06 / TS1-008: Gửi lại xác minh cho tài khoản chưa xác minh gọi provider; email chưa đăng ký hoặc đã xác minh vẫn trả 202 chung và không gửi email")
    void resendVerificationHandlesUnverifiedVerifiedAndUnregisteredConsistently() {
        UUID unverifiedAuthId = UUID.randomUUID();
        TaiKhoan unverifiedAccount = TaiKhoan.taoNguoiDungMoi(
                UUID.randomUUID(),
                unverifiedAuthId,
                "unverified@example.com",
                "Nguyễn Chưa Xác Minh",
                "0901112222",
                clock.instant()
        );
        taiKhoanRepository.save(unverifiedAccount);

        UUID verifiedAuthId = UUID.randomUUID();
        TaiKhoan verifiedAccount = TaiKhoan.taoNguoiDungDaXacMinh(
                UUID.randomUUID(),
                verifiedAuthId,
                "verified@example.com",
                "Trần Đã Xác Minh",
                "0903334444",
                clock.instant()
        );
        taiKhoanRepository.save(verifiedAccount);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/email/verification-requests");
        request.setRemoteAddr("192.0.2.10");

        // 1. Tài khoản chưa xác minh -> trả 202 chung và thực sự gọi provider gửi email
        ResponseEntity<GuiEmailKetQua> resUnverified = verificationController.yeuCauGuiLaiXacMinh(
                request,
                Map.of("email", "  Unverified@Example.com ")
        );
        assertThat(resUnverified.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resUnverified.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(resUnverified.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(resUnverified.getBody()).isNotNull();
        assertThat(resUnverified.getBody().trangThai()).isEqualTo("da_tiep_nhan");
        assertThat(resUnverified.getBody().thongDiep())
                .isEqualTo("Nếu tài khoản cần xác minh, hướng dẫn sẽ được gửi.");
        assertThat(supabaseAuthPort.resentEmails).containsExactly("unverified@example.com");

        // 2. Tài khoản đã xác minh (AUTH-05) -> vẫn trả cùng câu 202 chung, không gọi thêm provider
        ResponseEntity<GuiEmailKetQua> resAlreadyVerified = verificationController.yeuCauGuiLaiXacMinh(
                request,
                Map.of("email", "verified@example.com")
        );
        assertThat(resAlreadyVerified.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resAlreadyVerified.getBody()).isEqualTo(resUnverified.getBody());
        assertThat(supabaseAuthPort.resentEmails).hasSize(1);

        // 3. Email chưa từng đăng ký (AUTH-06) -> vẫn trả cùng câu 202 chung, không gọi provider, không tạo tài khoản mới
        ResponseEntity<GuiEmailKetQua> resUnknown = verificationController.yeuCauGuiLaiXacMinh(
                request,
                Map.of("email", "khongtontai@example.com")
        );
        assertThat(resUnknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resUnknown.getBody()).isEqualTo(resUnverified.getBody());
        assertThat(supabaseAuthPort.resentEmails).hasSize(1);
        assertThat(taiKhoanRepository.existsByEmail("khongtontai@example.com")).isFalse();
    }

    @Test
    @DisplayName("TS1-009 / AUTH-03 / AUTH-07 / AUTH-09 / PX-20: Xác minh hợp lệ cập nhật emailDaXacMinh=true, thu hồi đúng phiên browser hiện tại (Nam/Lan), xóa cookie phiên, xoay CSRF mới và không tự login")
    void verifyValidTokenUpdatesAccountRevokesCurrentBrowserSessionAndClearsCookie() {
        UUID lanSupabaseId = UUID.randomUUID();
        TaiKhoan lanAccount = TaiKhoan.taoNguoiDungMoi(
                UUID.randomUUID(),
                lanSupabaseId,
                "lan@example.com",
                "Lê Thị Lan",
                "0912345678",
                clock.instant()
        );
        taiKhoanRepository.save(lanAccount);

        String validTokenHash = "pkce_signup_token_hash_hop_le_lan_001";
        supabaseAuthPort.validTokenHashes.put(
                validTokenHash,
                new SupabaseVerifiedIdentity(
                        lanSupabaseId,
                        "lan@example.com",
                        true,
                        "Lê Thị Lan",
                        "0912345678"
                )
        );

        // Giả sử trình duyệt hiện tại đang có phiên của Nam (cookie "session_cookie_browser_nam"),
        // còn thiết bị khác có phiên "session_cookie_other_device"
        String namBrowserCookie = "session_cookie_browser_nam";
        String otherDeviceCookie = "session_cookie_other_device";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/email/verify");
        request.setRemoteAddr("198.51.100.25");
        request.setCookies(new Cookie("__Host-se214_session", namBrowserCookie));

        ResponseEntity<XacMinhKetQua> response = verificationController.hoanTatXacMinhEmail(
                request,
                Map.of("tokenHash", validTokenHash)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().trangThai()).isEqualTo("da_xac_minh");
        assertThat(response.getBody().canDangNhapLai()).isTrue();
        assertThat(response.getBody().csrfToken()).isNotBlank();

        // Kiểm tra 2 dòng Set-Cookie độc lập: xóa __Host-se214_session (Max-Age=0) và đặt __Host-se214_csrf mới
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies.get(0))
                .contains("__Host-se214_session=")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");
        assertThat(setCookies.get(1))
                .contains("__Host-se214_csrf=" + response.getBody().csrfToken())
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");

        // Hồ sơ của Lan đã chuyển sang emailDaXacMinh = true, giữ nguyên vai trò nguoi_dung
        Optional<TaiKhoan> updatedLanOpt = taiKhoanRepository.findByEmail("lan@example.com");
        assertThat(updatedLanOpt).isPresent();
        assertThat(updatedLanOpt.get().isEmailDaXacMinh()).isTrue();
        assertThat(updatedLanOpt.get().getVaiTro()).isEqualTo(VaiTroTaiKhoan.NGUOI_DUNG);
        assertThat(updatedLanOpt.get().getTrangThai()).isEqualTo(TrangThaiTaiKhoan.HOAT_DONG);

        // Phiên của Nam trên browser này đã bị thu hồi, nhưng phiên trên thiết bị khác không bị ảnh hưởng (AUTH-07, PX-20)
        assertThat(sessionLifecycleAdapter.isSessionCookieRevoked(namBrowserCookie)).isTrue();
        assertThat(sessionLifecycleAdapter.isSessionCookieRevoked(otherDeviceCookie)).isFalse();
    }

    @Test
    @DisplayName("TS1-010 / AC-15.3 / AUTH-07: Mã xác minh sai, hết hạn hoặc dùng lại lần 2 bị từ chối 400 LIEN_KET_KHONG_HOP_LE và KHÔNG đăng xuất phiên hiện có")
    void rejectInvalidExpiredOrReusedTokenHashWithoutRevokingCurrentSession() {
        UUID userAuthId = UUID.randomUUID();
        TaiKhoan account = TaiKhoan.taoNguoiDungMoi(
                UUID.randomUUID(),
                userAuthId,
                "user.otp@example.com",
                "Phạm Văn D",
                null,
                clock.instant()
        );
        taiKhoanRepository.save(account);

        String oneTimeToken = "token_hash_mot_lan_002";
        String expiredToken = "token_hash_het_han_003";
        supabaseAuthPort.validTokenHashes.put(
                oneTimeToken,
                new SupabaseVerifiedIdentity(userAuthId, "user.otp@example.com", true, "Phạm Văn D", null)
        );
        supabaseAuthPort.expiredTokenHashes.add(expiredToken);

        String activeBrowserCookie = "session_cookie_dang_hoat_dong";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/email/verify");
        request.setRemoteAddr("198.51.100.50");
        request.setCookies(new Cookie("__Host-se214_session", activeBrowserCookie));

        // 1. Mã sai -> 400 LIEN_KET_KHONG_HOP_LE, không thu hồi phiên đang có, tài khoản vẫn chưa xác minh
        assertThatThrownBy(() -> verificationController.hoanTatXacMinhEmail(
                request,
                Map.of("tokenHash", "ma_sai_khong_ton_tai")
        ))
                .isInstanceOf(InvalidVerificationLinkException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleInvalidVerificationLink(
                            (InvalidVerificationLinkException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("LIEN_KET_KHONG_HOP_LE");
                    assertThat(errResp.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
                });

        assertThat(sessionLifecycleAdapter.isSessionCookieRevoked(activeBrowserCookie)).isFalse();
        assertThat(taiKhoanRepository.findByEmail("user.otp@example.com").orElseThrow().isEmailDaXacMinh()).isFalse();

        // 2. Mã hết hạn -> 400 LIEN_KET_KHONG_HOP_LE, không thu hồi phiên đang có
        assertThatThrownBy(() -> verificationController.hoanTatXacMinhEmail(
                request,
                Map.of("tokenHash", expiredToken)
        ))
                .isInstanceOf(InvalidVerificationLinkException.class);
        assertThat(sessionLifecycleAdapter.isSessionCookieRevoked(activeBrowserCookie)).isFalse();

        // 3. Dùng mã hợp lệ lần 1 -> 200 OK
        ResponseEntity<XacMinhKetQua> firstUse = verificationController.hoanTatXacMinhEmail(
                request,
                Map.of("tokenHash", oneTimeToken)
        );
        assertThat(firstUse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(taiKhoanRepository.findByEmail("user.otp@example.com").orElseThrow().isEmailDaXacMinh()).isTrue();

        // 4. Dùng lại chính mã đó lần 2 (replay tokenHash) -> bị từ chối 400 LIEN_KET_KHONG_HOP_LE
        String newBrowserCookieAfterRelogin = "session_cookie_moi_sau_dang_nhap";
        request.setCookies(new Cookie("__Host-se214_session", newBrowserCookieAfterRelogin));

        assertThatThrownBy(() -> verificationController.hoanTatXacMinhEmail(
                request,
                Map.of("tokenHash", oneTimeToken)
        ))
                .isInstanceOf(InvalidVerificationLinkException.class);
        assertThat(sessionLifecycleAdapter.isSessionCookieRevoked(newBrowserCookieAfterRelogin)).isFalse();
    }

    @Test
    @DisplayName("TS1-011 / AUTH-14: Chống lạm dụng gửi lại xác minh (1 lần/60s/email, 5 lần/giờ/email, áp cả email không tồn tại) trả 429 QUA_NHIEU_YEU_CAU kèm Retry-After")
    void rateLimitResendVerificationEnforcesCooldownAndHourlyLimits() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/email/verification-requests");
        request.setRemoteAddr("203.0.113.77");

        // Lần 1 ở t = 0s thành công (kể cả với email chưa tồn tại để chống dò email)
        ResponseEntity<GuiEmailKetQua> first = verificationController.yeuCauGuiLaiXacMinh(
                request,
                Map.of("email", "ratelimit.test@example.com")
        );
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Lần 2 ở t = 15s (chưa đủ 60s cooldown) -> bị chặn 429 QUA_NHIEU_YEU_CAU kèm Retry-After = 45
        clock.advance(Duration.ofSeconds(15));
        assertThatThrownBy(() -> verificationController.yeuCauGuiLaiXacMinh(
                request,
                Map.of("email", "ratelimit.test@example.com")
        ))
                .isInstanceOf(AuthRateLimitExceededException.class)
                .satisfies(ex -> {
                    AuthRateLimitExceededException rateEx = (AuthRateLimitExceededException) ex;
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleRateLimitExceeded(rateEx, request);
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(errResp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("45");
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("QUA_NHIEU_YEU_CAU");
                });

        // Sau khi qua 60s (t = 61s), gửi tiếp các lần 2, 3, 4, 5 cách nhau mỗi lần 61 giây -> đều thành công (đủ 5 lần/giờ)
        for (int attempt = 2; attempt <= 5; attempt++) {
            clock.advance(Duration.ofSeconds(61));
            ResponseEntity<GuiEmailKetQua> okResp = verificationController.yeuCauGuiLaiXacMinh(
                    request,
                    Map.of("email", "ratelimit.test@example.com")
            );
            assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        // Lần thứ 6 trong cùng 1 giờ (dù đã qua 61s từ lần thứ 5) -> bị chặn bởi ngưỡng 5 lần/giờ/email
        clock.advance(Duration.ofSeconds(61));
        assertThatThrownBy(() -> verificationController.yeuCauGuiLaiXacMinh(
                request,
                Map.of("email", "ratelimit.test@example.com")
        ))
                .isInstanceOf(AuthRateLimitExceededException.class);
    }

    @Test
    @DisplayName("AUTH-15 / AC-15.2 / AUTH-17: Chặn client truyền trường dư (type, vaiTro, emailDaXacMinh) với 400; chặn danh tính ngoài luồng thiếu họ tên với 409 HO_SO_XAC_THUC_CAN_KIEM_TRA")
    void rejectExtraFieldsAndOutOfBandIdentityMissingHoTen() {
        MockHttpServletRequest verifyRequest = new MockHttpServletRequest("POST", "/api/auth/email/verify");
        verifyRequest.setRemoteAddr("192.0.2.99");

        // Client cố tình truyền type = "recovery" hoặc "email" hoặc emailDaXacMinh = true -> 400 DU_LIEU_KHONG_HOP_LE
        assertThatThrownBy(() -> verificationController.hoanTatXacMinhEmail(
                verifyRequest,
                Map.of("tokenHash", "some_token_hash", "type", "recovery")
        ))
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> {
                    AuthValidationException valEx = (AuthValidationException) ex;
                    assertThat(valEx.getMaLoi()).isEqualTo("DU_LIEU_KHONG_HOP_LE");
                    assertThat(valEx.getTruongLoi()).anyMatch(f -> "type".equals(f.truong()));
                });

        // Danh tính trên provider chưa có bản ghi app và thiếu ho_ten hợp lệ -> 409 HO_SO_XAC_THUC_CAN_KIEM_TRA (AUTH-17)
        String orphanToken = "token_orphan_missing_hoten";
        supabaseAuthPort.validTokenHashes.put(
                orphanToken,
                new SupabaseVerifiedIdentity(UUID.randomUUID(), "orphan@example.com", true, "   ", null)
        );

        assertThatThrownBy(() -> verificationController.hoanTatXacMinhEmail(
                verifyRequest,
                Map.of("tokenHash", orphanToken)
        ))
                .isInstanceOf(AuthProfileNeedsInspectionException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> resp = exceptionHandler.handleProfileNeedsInspection(
                            (AuthProfileNeedsInspectionException) ex,
                            verifyRequest
                    );
                    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(resp.getBody()).isNotNull();
                    assertThat(resp.getBody().maLoi()).isEqualTo("HO_SO_XAC_THUC_CAN_KIEM_TRA");
                });

        assertThat(taiKhoanRepository.existsByEmail("orphan@example.com")).isFalse();
    }

    @Test
    @DisplayName("AUTH-15 / api-specification §8.5: SupabaseAuthRestClientAdapter gọi /auth/v1/verify cố định type=signup và thu hồi ngay access_token tạm với scope=local")
    void supabaseAuthRestClientAdapterCallsVerifyWithFixedSignupTypeAndRevokesTempSessionLocally() {
        AtomicReference<String> verifyRequestBody = new AtomicReference<>();
        AtomicReference<String> logoutQueryAndAuth = new AtomicReference<>();
        UUID verifiedUserId = UUID.randomUUID();

        ClientHttpRequestFactory requestFactory = (uri, httpMethod) -> {
            String path = uri.getPath();
            if ("/auth/v1/verify".equals(path)) {
                String json = "{"
                        + "\"access_token\":\"temp_provider_access_token_xyz\","
                        + "\"refresh_token\":\"temp_provider_refresh_token_xyz\","
                        + "\"user\":{"
                        + "\"id\":\"" + verifiedUserId + "\","
                        + "\"email\":\"verified.adapter@example.com\","
                        + "\"email_confirmed_at\":\"2026-11-14T10:05:00Z\","
                        + "\"user_metadata\":{\"ho_ten\":\"Hoàng Văn E\",\"so_dien_thoai\":\"0988776655\"}"
                        + "}"
                        + "}";
                MockClientHttpResponse response = new MockClientHttpResponse(
                        json.getBytes(StandardCharsets.UTF_8),
                        HttpStatus.OK
                );
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        verifyRequestBody.set(getBodyAsString(StandardCharsets.UTF_8));
                        return response;
                    }
                };
            }
            if ("/auth/v1/logout".equals(path)) {
                MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.NO_CONTENT);
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        logoutQueryAndAuth.set(
                                uri.getQuery() + "|" + getHeaders().getFirst(HttpHeaders.AUTHORIZATION)
                        );
                        return response;
                    }
                };
            }
            throw new AssertionError("Unexpected URI path: " + path);
        };

        SupabaseAuthProperties props = new SupabaseAuthProperties();
        props.setUrl("https://fcsbaknvtengrgsvuehv.supabase.co");
        props.setAnonKey("test-publishable-anon-key");

        RestClient testRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        SupabaseAuthRestClientAdapter adapter = new SupabaseAuthRestClientAdapter(props, testRestClient);
        SupabaseVerifiedIdentity identity = adapter.verifySignupEmailTokenHash("hash_token_123456");

        assertThat(identity.supabaseAuthUserId()).isEqualTo(verifiedUserId);
        assertThat(identity.email()).isEqualTo("verified.adapter@example.com");
        assertThat(identity.emailConfirmed()).isTrue();
        assertThat(identity.hoTenMetadata()).isEqualTo("Hoàng Văn E");
        assertThat(identity.soDienThoaiMetadata()).isEqualTo("0988776655");

        assertThat(verifyRequestBody.get())
                .contains("\"type\":\"signup\"")
                .contains("\"token_hash\":\"hash_token_123456\"")
                .doesNotContain("\"type\":\"email\"")
                .doesNotContain("\"type\":\"recovery\"");

        assertThat(logoutQueryAndAuth.get())
                .isEqualTo("scope=local|Bearer temp_provider_access_token_xyz");
    }

    @Test
    @DisplayName("api-specification §8.9: Khi đồng bộ DB thất bại sau khi provider đã xác minh, trả về 503 DONG_BO_XAC_MINH_CHUA_HOAN_TAT")
    void return503WhenDatabaseSyncFailsAfterProviderVerification() {
        UUID userAuthId = UUID.randomUUID();
        String tokenHash = "token_sync_fail_503";
        supabaseAuthPort.validTokenHashes.put(
                tokenHash,
                new SupabaseVerifiedIdentity(userAuthId, "syncfail@example.com", true, "Nguyễn Lỗi Đồng Bộ", null)
        );

        AuthEmailVerificationService failingSyncService = new AuthEmailVerificationService(
                taiKhoanRepository,
                supabaseAuthPort,
                rateLimiter,
                new com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort() {
                    @Override
                    public void revokeCurrentBrowserSessionIfPresent(String rawSessionCookieValue) {
                        throw new RuntimeException("Simulated DB/Session store outage");
                    }

                    @Override
                    public String issueAnonymousCsrfToken() {
                        return "csrf_unused";
                    }
                }
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/email/verify");
        assertThatThrownBy(() -> failingSyncService.hoanTatXacMinhEmail(
                Map.of("tokenHash", tokenHash),
                "192.0.2.120",
                "some_cookie"
        ))
                .isInstanceOf(VerificationSyncIncompleteException.class)
                .satisfies(ex -> {
                    ResponseEntity<LoiApiResponse> errResp = exceptionHandler.handleVerificationSyncIncomplete(
                            (VerificationSyncIncompleteException) ex,
                            request
                    );
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("DONG_BO_XAC_MINH_CHUA_HOAN_TAT");
                });
    }

    @Test
    @DisplayName("Mã xác minh email 6 chữ số (OTP 0-9): Chấp nhận đúng 6 chữ số và từ chối mã ngắn không đủ 6 chữ số hoặc chứa chữ cái")
    void verifySixDigitNumericOtpAndRejectNonSixDigitShortCodes() {
        UUID userAuthId = UUID.randomUUID();
        TaiKhoan account = TaiKhoan.taoNguoiDungMoi(
                UUID.randomUUID(),
                userAuthId,
                "otp6digit@example.com",
                "Võ Minh K",
                "0905556666",
                clock.instant()
        );
        taiKhoanRepository.save(account);

        String validSixDigitCode = "482910";
        supabaseAuthPort.validTokenHashes.put(
                validSixDigitCode,
                new SupabaseVerifiedIdentity(userAuthId, "otp6digit@example.com", true, "Võ Minh K", "0905556666")
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/email/verify");
        request.setRemoteAddr("198.51.100.88");

        // 1. Mã không đủ 6 kí tự hoặc chứa chữ cái ("12345", "12345a", "abcdef") -> bị từ chối 400 DU_LIEU_KHONG_HOP_LE
        for (String invalidShortCode : List.of("12345", "1234567", "12345a", "abcdef")) {
            assertThatThrownBy(() -> verificationController.hoanTatXacMinhEmail(
                    request,
                    Map.of("tokenHash", invalidShortCode)
            ))
                    .isInstanceOf(AuthValidationException.class)
                    .satisfies(ex -> {
                        AuthValidationException valEx = (AuthValidationException) ex;
                        assertThat(valEx.getMaLoi()).isEqualTo("DU_LIEU_KHONG_HOP_LE");
                    });
        }

        // 2. Mã xác minh đúng 6 kí tự và chỉ là chữ số ("482910") -> 200 OK và cập nhật emailDaXacMinh = true
        ResponseEntity<XacMinhKetQua> okResp = verificationController.hoanTatXacMinhEmail(
                request,
                Map.of("email", "otp6digit@example.com", "maXacMinh", validSixDigitCode)
        );
        assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(taiKhoanRepository.findByEmail("otp6digit@example.com").orElseThrow().isEmailDaXacMinh()).isTrue();
    }
}
