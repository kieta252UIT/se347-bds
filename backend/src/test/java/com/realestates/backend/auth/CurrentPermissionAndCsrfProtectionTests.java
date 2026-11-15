package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.MaTranQuyenSprint1;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.CsrfKetQua;
import com.realestates.backend.auth.dto.TaiKhoanHienTai;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.InvalidCsrfException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kiểm thử toàn diện cho task "Kiểm quyền hiện tại và bảo vệ yêu cầu bằng CSRF" (S1-25)
 * theo đặc tả:
 * - thiet-ke-phien-sprint-1.md (PHIEN-01, PHIEN-02, PHIEN-03, PHIEN-04, PHIEN-06, PHIEN-08, PHIEN-09, PHIEN-15)
 * - api-specification-sprint-1.md (§3.1, §8.6 GET /api/csrf, §8.7 GET /api/me, §8.9 LoiApi)
 * - ma-tran-quyen-va-trang-thai-sprint-1.md (Bảng 1 & Bảng 2: RBAC 3 vai trò + Ownership chuId + chặn tự khai)
 * - test-specification-sprint-1.md (TS1-008, TS1-012, TS1-013, TS1-019, TS1-020, PX-02, PX-03, PX-04, PX-05, PX-06, PX-18, PX-21)
 */
class CurrentPermissionAndCsrfProtectionTests {

    private static final String ALLOWED_ORIGIN = "https://localhost";
    private static final String ALLOWED_DEV_ORIGIN = "http://localhost:5173";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private AuthCsrfAndMeController csrfAndMeController;
    private CsrfAndOriginProtectionFilter csrfFilter;
    private ProtectedBusinessProbeController probeController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.phienXacThucRepository = new InMemoryPhienXacThucRepository();
        this.sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                CSRF_SECRET,
                phienXacThucRepository
        );
        this.cookieConfig = createDefaultCookieConfig();
        this.permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ALLOWED_ORIGIN, ALLOWED_DEV_ORIGIN)
        );
        this.csrfAndMeController = new AuthCsrfAndMeController(permissionAndCsrfService, cookieConfig);
        this.csrfFilter = new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig);
        this.probeController = new ProtectedBusinessProbeController(permissionAndCsrfService, cookieConfig);

        this.mockMvc = MockMvcBuilders.standaloneSetup(csrfAndMeController, probeController)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler(), new GlobalApiExceptionHandler())
                .addFilters(csrfFilter)
                .build();
    }

    // =========================================================================
    // 1. GET /api/csrf & Bảo vệ CSRF + Same-Origin (PHIEN-01, PHIEN-06, PX-05, PX-06, TS1-012, TS1-020)
    // =========================================================================

    @Test
    @DisplayName("PHIEN-06 / PX-05: GET /api/csrf phát hành CSRF token theo ngữ cảnh anonymous hoặc session, tái sử dụng khi còn hợp lệ và đổi mới khi ngữ cảnh phiên thay đổi, không chạm __Host-se214_session")
    void getCsrfIssuesAndReusesContextBoundTokenWithoutTouchingSessionCookie() throws Exception {
        // 1. Khách chưa có cookie phiên gọi GET /api/csrf -> nhận token anonymous và Set-Cookie __Host-se214_csrf
        MvcResult anonResult1 = mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();

        List<String> setCookies1 = anonResult1.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies1).hasSize(1);
        assertThat(setCookies1.getFirst())
                .contains("__Host-se214_csrf=")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("__Host-se214_session")
                .doesNotContainIgnoringCase("Domain=");

        String anonToken1 = extractCsrfTokenFromJson(anonResult1.getResponse().getContentAsString());
        assertThat(sessionLifecycleAdapter.verifyCsrfTokenForContext(anonToken1, null)).isTrue();

        // 2. Gọi lại GET /api/csrf với cùng ngữ cảnh anonymous và cookie __Host-se214_csrf hiện có -> tái sử dụng đúng token đó
        MvcResult anonResult2 = mockMvc.perform(get("/api/csrf")
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonToken1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").value(anonToken1))
                .andReturn();
        assertThat(anonResult2.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .allSatisfy(h -> assertThat(h).doesNotContain("__Host-se214_session"));

        // 3. Khi trình duyệt đã có __Host-se214_session (ngữ cảnh phiên mới), gọi GET /api/csrf kèm anonToken1 cũ -> phải phát hành token mới gắn với phiên
        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        MvcResult sessionResult1 = mockMvc.perform(get("/api/csrf")
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, rawSessionCookie),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonToken1)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();

        String sessionToken1 = extractCsrfTokenFromJson(sessionResult1.getResponse().getContentAsString());
        assertThat(sessionToken1).isNotEqualTo(anonToken1);
        assertThat(sessionLifecycleAdapter.verifyCsrfTokenForContext(sessionToken1, rawSessionCookie)).isTrue();
        assertThat(sessionLifecycleAdapter.verifyCsrfTokenForContext(sessionToken1, null)).isFalse();

        // 4. Gọi lại GET /api/csrf trên cùng phiên -> tái sử dụng sessionToken1
        mockMvc.perform(get("/api/csrf")
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, rawSessionCookie),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, sessionToken1)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").value(sessionToken1));
    }

    @Test
    @DisplayName("TS1-012 / TS1-020 / PX-05 / PX-06: Mọi yêu cầu POST/PUT/DELETE/PATCH thiếu/sai Origin hoặc thiếu/sai/lệch ngữ cảnh CSRF bị chặn 403 CSRF_KHONG_HOP_LE trước khi thực thi use case")
    void stateChangingRequestsEnforceOriginAndCsrfBeforeExecutingUseCase() throws Exception {
        ActiveSessionFixture fixture = seedActiveVerifiedAccountAndSession(
                "user.csrf@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        String validSessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(fixture.rawSessionCookie());
        String anonymousCsrf = sessionLifecycleAdapter.issueAnonymousCsrfToken();

        String otherSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        String otherSessionCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(otherSessionCookie);

        // Trường hợp 1: Thiếu Origin
        mockMvc.perform(post("/api/probe/listings")
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, validSessionCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, validSessionCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Tin thử\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value(InvalidCsrfException.MA_LOI));

        // Trường hợp 2: Origin lạ ngoài danh sách cho phép (PX-06)
        mockMvc.perform(post("/api/probe/listings")
                        .header(HttpHeaders.ORIGIN, "https://attacker.evil.example")
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, validSessionCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, validSessionCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Tin thử\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // Trường hợp 3: Thiếu X-CSRF-Token header
        mockMvc.perform(put("/api/probe/listings/" + UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, validSessionCsrf)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Cập nhật\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // Trường hợp 4: Thiếu cookie __Host-se214_csrf
        mockMvc.perform(delete("/api/probe/listings/" + UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()))
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, validSessionCsrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // Trường hợp 5: Header X-CSRF-Token khác giá trị cookie __Host-se214_csrf
        mockMvc.perform(patch("/api/probe/listings/" + UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, validSessionCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, otherSessionCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Sửa\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // Trường hợp 6: Dùng CSRF token của ngữ cảnh anonymous khi đang gửi cookie phiên đăng nhập (PX-05: lệch ngữ cảnh)
        mockMvc.perform(post("/api/probe/listings")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, anonymousCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, anonymousCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Tin thử\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // Trường hợp 7: Dùng CSRF token của phiên A cho phiên B (PX-05: lệch phiên)
        mockMvc.perform(post("/api/probe/listings")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, otherSessionCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, otherSessionCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Tin thử\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        // Đảm bảo không có bất kỳ lời gọi nào ở trên chạm tới logic nghiệp vụ của Controller
        assertThat(probeController.writeExecutionCount.get()).isZero();

        // Trường hợp 8: Khi gửi đúng Origin + đúng cặp CSRF của phiên hiện tại -> đi qua bộ lọc thành công (200 OK)
        mockMvc.perform(post("/api/probe/listings")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, validSessionCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, validSessionCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Tin hợp lệ\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value("created"));

        assertThat(probeController.writeExecutionCount.get()).isEqualTo(1);
    }

    // =========================================================================
    // 2. GET /api/me & Kiểm tra phiên hiện tại (PHIEN-02, PHIEN-03, PHIEN-04, PHIEN-15, PX-02, PX-03, PX-18)
    // =========================================================================

    @Test
    @DisplayName("PHIEN-02 / PHIEN-03 / PHIEN-15: GET /api/me hợp lệ trả 200 TaiKhoanHienTai (không lộ trường nhạy cảm) và gia hạn lại __Host-se214_session với CÙNG giá trị opaque")
    void getMeValidSessionReturnsTaiKhoanHienTaiAndRenewsSameOpaqueCookie() throws Exception {
        ActiveSessionFixture fixture = seedActiveVerifiedAccountAndSession(
                "nguoidung.me@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );

        MvcResult result = mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, fixture.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(fixture.taiKhoan().getId().toString()))
                .andExpect(jsonPath("$.email").value("nguoidung.me@example.com"))
                .andExpect(jsonPath("$.hoTen").value(fixture.taiKhoan().getHoTen()))
                .andExpect(jsonPath("$.vaiTro").value("nguoi_dung"))
                .andExpect(jsonPath("$.trangThai").value("hoat_dong"))
                .andExpect(jsonPath("$.emailDaXacMinh").value(true))
                .andExpect(jsonPath("$.supabaseAuthUserId").doesNotExist())
                .andExpect(jsonPath("$.mocThuHoi").doesNotExist())
                .andExpect(jsonPath("$.accessTokenMaHoa").doesNotExist())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        assertThat(setCookies.getFirst())
                .contains("__Host-se214_session=" + fixture.rawSessionCookie())
                .contains("Max-Age=31536000")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("PX-02 / PX-03 / PX-18: GET /api/me từ chối không có cookie, cookie giả, phiên đã thu hồi, lệch moc_thu_hoi, đang đồng bộ auth (401 CHUA_DANG_NHAP), tài khoản bị khóa (403 TAI_KHOAN_BI_KHOA), chưa xác minh email (403 EMAIL_CHUA_XAC_MINH) và tuyệt đối KHÔNG gửi Set-Cookie")
    void getMeRejectsInvalidRevokedLockedUnverifiedOrSyncingAccountsWithoutSetCookie() throws Exception {
        // 1. Không có cookie phiên -> 401 CHUA_DANG_NHAP
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // 2. Cookie phiên ngẫu nhiên không tồn tại trong DB -> 401 CHUA_DANG_NHAP
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                sessionLifecycleAdapter.generateRawSessionCookie()
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // 3. Phiên đã có thu_hoi_luc != null -> 401 CHUA_DANG_NHAP
        ActiveSessionFixture revokedFixture = seedActiveVerifiedAccountAndSession(
                "revoked@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        phienXacThucRepository.revokeByMaCookieBam(
                revokedFixture.phien().getMaCookieBam(),
                PhienXacThuc.LY_DO_DANG_XUAT,
                Instant.now(),
                false
        );
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                revokedFixture.rawSessionCookie()
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // 4. moc_thu_hoi_luc_cap != tai_khoan.moc_thu_hoi -> 401 CHUA_DANG_NHAP
        ActiveSessionFixture epochMismatchFixture = seedActiveVerifiedAccountAndSession(
                "epoch@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        taiKhoanRepository.save(epochMismatchFixture.taiKhoan().tangMocThuHoi("san_sang", Instant.now()));
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                epochMismatchFixture.rawSessionCookie()
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // 5. trang_thai_dong_bo_auth != 'san_sang' (PX-18: đang đặt lại mật khẩu / cần khôi phục) -> 401 CHUA_DANG_NHAP
        ActiveSessionFixture syncingFixture = seedActiveVerifiedAccountAndSession(
                "syncing@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        taiKhoanRepository.save(syncingFixture.taiKhoan().capNhatTrangThaiDongBoAuth("dang_dat_lai", Instant.now()));
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                syncingFixture.rawSessionCookie()
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        // 6. Tài khoản có trang_thai = 'bi_khoa' (khi bản ghi phiên chưa thu hồi) -> 403 TAI_KHOAN_BI_KHOA
        ActiveSessionFixture lockedFixture = seedActiveVerifiedAccountAndSession(
                "locked@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        taiKhoanRepository.save(lockedFixture.taiKhoan().capNhatTrangThai(TrangThaiTaiKhoan.BI_KHOA, Instant.now()));
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                lockedFixture.rawSessionCookie()
                        )))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value(AccountLockedException.MA_LOI));

        // 7. Tài khoản có email_da_xac_minh = false -> 403 EMAIL_CHUA_XAC_MINH
        ActiveSessionFixture unverifiedFixture = seedAccountAndSession(
                "unverified@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG,
                TrangThaiTaiKhoan.HOAT_DONG,
                false,
                0L,
                "san_sang"
        );
        mockMvc.perform(get("/api/me")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                unverifiedFixture.rawSessionCookie()
                        )))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value(EmailNotVerifiedException.MA_LOI));

        // 8. Yêu cầu GET công khai vẫn hoạt động ở vai trò Khách ngay cả khi browser còn giữ cookie phiên đã thu hồi/bị khóa (PX-02)
        mockMvc.perform(get("/api/probe/public-listings")
                        .cookie(new Cookie(
                                AuthSessionCookieConfig.SESSION_COOKIE_NAME,
                                revokedFixture.rawSessionCookie()
                        )))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.viewerRole").value("khach"));
    }

    // =========================================================================
    // 3. Ma trận quyền RBAC, Ownership chuId và chặn tự khai (TS1-008, TS1-013, Bảng 1 & Bảng 2)
    // =========================================================================

    @Test
    @DisplayName("TS1-008 / TS1-013 / Bảng 1 & Bảng 2: Thực thi đúng ma trận quyền 3 vai trò (NGUOI_DUNG, KIEM_DUYET_VIEN, QUAN_TRI_VIEN không kế thừa chéo), kiểm quyền sở hữu chuId từ DB và chặn tự khai vaiTro/trangThai/chuId")
    void enforceRbacMatrixResourceOwnershipAndRejectSelfDeclaredFields() throws Exception {
        UUID ownerOneId = UUID.randomUUID();
        UUID ownerTwoId = UUID.randomUUID();
        UUID listingOwnedByOne = UUID.randomUUID();
        probeController.listingOwners.put(listingOwnedByOne, ownerOneId);

        ActiveSessionFixture userOne = seedAccountAndSessionWithId(
                ownerOneId,
                "user1@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        ActiveSessionFixture userTwo = seedAccountAndSessionWithId(
                ownerTwoId,
                "user2@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        ActiveSessionFixture kdv = seedActiveVerifiedAccountAndSession(
                "kdv@example.com",
                VaiTroTaiKhoan.KIEM_DUYET_VIEN
        );
        ActiveSessionFixture admin = seedActiveVerifiedAccountAndSession(
                "admin@example.com",
                VaiTroTaiKhoan.QUAN_TRI_VIEN
        );

        String userOneCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(userOne.rawSessionCookie());
        String userTwoCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(userTwo.rawSessionCookie());
        String kdvCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(kdv.rawSessionCookie());
        String adminCsrf = sessionLifecycleAdapter.issueSessionCsrfToken(admin.rawSessionCookie());

        // 1. Kiểm duyệt tin (XEM_HANG_CHO_VA_DUYET_TIN):
        // - Người dùng bị chặn 403 KHONG_DU_QUYEN
        // - Quản trị viên bị chặn 403 KHONG_DU_QUYEN (Admin không tự động kế thừa quyền KDV!)
        // - Kiểm duyệt viên được phép 200 OK
        mockMvc.perform(post("/api/probe/moderation/approve")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, userOne.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, userOneCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, userOneCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tinId\":\"" + listingOwnedByOne + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value(InsufficientPermissionException.MA_LOI));

        mockMvc.perform(post("/api/probe/moderation/approve")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, admin.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, adminCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, adminCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tinId\":\"" + listingOwnedByOne + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        mockMvc.perform(post("/api/probe/moderation/approve")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, kdv.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, kdvCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, kdvCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tinId\":\"" + listingOwnedByOne + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));

        // 2. Quản trị tài khoản (TAO_CAP_NHAT_KHOA_MO_KHOA_TAI_KHOAN):
        // - Người dùng và KDV bị chặn 403 KHONG_DU_QUYEN
        // - Admin được phép 200 OK
        mockMvc.perform(post("/api/probe/admin/accounts")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, kdv.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, kdvCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, kdvCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"lock\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        mockMvc.perform(post("/api/probe/admin/accounts")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, admin.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, adminCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, adminCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"lock\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("admin_ok"));

        // 3. Chỉnh sửa / xóa mềm tin của mình (CHINH_SUA_TIN_CUA_MINH):
        // - userTwo (không phải chủ tin) bị chặn 403 KHONG_DU_QUYEN (TS1-013)
        // - Admin và KDV bị chặn 403 KHONG_DU_QUYEN (Bảng 1: chỉ chủ tin vai trò Người dùng được sửa/xóa tin cá nhân)
        // - userOne (chủ tin) được phép 200 OK
        mockMvc.perform(put("/api/probe/listings/" + listingOwnedByOne)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, userTwo.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, userTwoCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, userTwoCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"User 2 sửa trộm tin User 1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        mockMvc.perform(put("/api/probe/listings/" + listingOwnedByOne)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, admin.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, adminCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, adminCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Admin sửa tin cá nhân\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        mockMvc.perform(put("/api/probe/listings/" + listingOwnedByOne)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, userOne.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, userOneCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, userOneCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Chủ tin sửa hợp lệ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"));

        // 4. Chặn tuyệt đối payload tự khai vaiTro, chuId, trangThai từ client với 400 DU_LIEU_KHONG_HOP_LE
        mockMvc.perform(put("/api/probe/listings/" + listingOwnedByOne)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, userOne.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, userOneCsrf)
                        )
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, userOneCsrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tieuDe\":\"Tự khai chuId\",\"chuId\":\"" + ownerTwoId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("chuId"));

        // Kiểm tra hàm miền MaTranQuyenSprint1 cho đủ tất cả các thao tác trong Bảng 1
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(null, ThaoTacNghiepVu.TIM_LOC_SAP_XEP_TIN_CONG_KHAI)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(null, ThaoTacNghiepVu.XEM_CHI_TIET_TIN_DA_DUYET)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(null, ThaoTacNghiepVu.THEM_GO_XEM_DANH_SACH_YEU_THICH)).isFalse();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.NGUOI_DUNG, ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.KIEM_DUYET_VIEN, ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN)).isFalse();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.KIEM_DUYET_VIEN, ThaoTacNghiepVu.XEM_VA_XU_LY_BAO_CAO_VI_PHAM)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.QUAN_TRI_VIEN, ThaoTacNghiepVu.XEM_VA_XU_LY_BAO_CAO_VI_PHAM)).isFalse();
    }

    // =========================================================================
    // 4. Kiểm lại quyền trong Transaction khi đồng thời khóa tài khoản hoặc đổi vai trò (TS1-019, PX-04)
    // =========================================================================

    @Test
    @DisplayName("TS1-019 / PX-04: Giao dịch ghi đang chờ khóa khi Admin khóa tài khoản hoặc đổi vai trò đồng thời sẽ đọc trạng thái mới nhất sau khi lấy khóa và bị từ chối nhất quán")
    void writeTransactionReverifiesPermissionsUnderLockWhenConcurrentLockOrRoleChangeCommitsFirst() throws Exception {
        // Kịch bản A (TS1-019): Admin giữ khóa tài khoản và thực hiện khóa tài khoản;
        // luồng ghi của người dùng đã qua kiểm tra vòng ngoài nhưng phải chờ khóa giao dịch -> khi vào giao dịch phải bị chặn (không được ghi)
        ActiveSessionFixture userFixture = seedActiveVerifiedAccountAndSession(
                "concurrent.lock@example.com",
                VaiTroTaiKhoan.NGUOI_DUNG
        );
        UUID taiKhoanId = userFixture.taiKhoan().getId();
        UUID listingId = UUID.randomUUID();
        probeController.listingOwners.put(listingId, taiKhoanId);

        CountDownLatch adminHoldingLockLatch = new CountDownLatch(1);
        CountDownLatch releaseAdminLockLatch = new CountDownLatch(1);
        AtomicBoolean writeActionExecuted = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> adminLockFuture = executor.submit(() -> {
                permissionAndCsrfService.giuKhoaTaiKhoanTrongGiaoDich(taiKhoanId, () -> {
                    adminHoldingLockLatch.countDown();
                    try {
                        if (!releaseAdminLockLatch.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timeout waiting for releaseAdminLockLatch");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    // Trước khi nhả khóa, Admin hoàn tất khóa tài khoản + tăng moc_thu_hoi + thu hồi phiên
                    permissionAndCsrfService.khoaTaiKhoanNguyenTu(taiKhoanId, Instant.now());
                    return null;
                });
            });

            assertThat(adminHoldingLockLatch.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> concurrentWriteFuture = executor.submit(() -> {
                try {
                    permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                            userFixture.rawSessionCookie(),
                            ThaoTacNghiepVu.CHINH_SUA_TIN_CUA_MINH,
                            () -> probeController.listingOwners.get(listingId),
                            listingId,
                            Map.of("tieuDe", "Ghi đồng thời khi đang bị khóa"),
                            principal -> {
                                writeActionExecuted.set(true);
                                return "SHOULD_NOT_HAPPEN";
                            }
                    );
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            // Cho phép luồng ghi vượt qua bước kiểm vòng ngoài và xếp hàng chờ khóa accountLock
            Thread.sleep(80);
            releaseAdminLockLatch.countDown();

            adminLockFuture.get(5, TimeUnit.SECONDS);
            Throwable writeFailure = concurrentWriteFuture.get(5, TimeUnit.SECONDS);

            assertThat(writeFailure)
                    .isNotNull()
                    .isInstanceOfAny(UnauthenticatedSessionException.class, AccountLockedException.class);
            assertThat(writeActionExecuted.get()).isFalse();

            // Kịch bản B (PX-04): Đổi vai trò đồng thời từ KIEM_DUYET_VIEN sang NGUOI_DUNG ngay trước điểm kiểm trong giao dịch duyệt tin
            ActiveSessionFixture kdvFixture = seedActiveVerifiedAccountAndSession(
                    "concurrent.role@example.com",
                    VaiTroTaiKhoan.KIEM_DUYET_VIEN
            );
            UUID kdvId = kdvFixture.taiKhoan().getId();
            CountDownLatch roleChangeHoldingLatch = new CountDownLatch(1);
            CountDownLatch releaseRoleChangeLatch = new CountDownLatch(1);
            AtomicBoolean approveExecuted = new AtomicBoolean(false);

            Future<?> roleChangeFuture = executor.submit(() -> {
                permissionAndCsrfService.giuKhoaTaiKhoanTrongGiaoDich(kdvId, () -> {
                    roleChangeHoldingLatch.countDown();
                    try {
                        releaseRoleChangeLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    permissionAndCsrfService.capNhatVaiTroTaiKhoanNguyenTu(
                            kdvId,
                            VaiTroTaiKhoan.NGUOI_DUNG,
                            Instant.now()
                    );
                    return null;
                });
            });

            assertThat(roleChangeHoldingLatch.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> kdvApproveFuture = executor.submit(() -> {
                try {
                    permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                            kdvFixture.rawSessionCookie(),
                            ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                            null,
                            listingId,
                            Map.of("ghiChu", "Duyệt tin"),
                            principal -> {
                                approveExecuted.set(true);
                                return "APPROVED";
                            }
                    );
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            Thread.sleep(80);
            releaseRoleChangeLatch.countDown();

            roleChangeFuture.get(5, TimeUnit.SECONDS);
            Throwable kdvFailure = kdvApproveFuture.get(5, TimeUnit.SECONDS);

            assertThat(kdvFailure)
                    .isNotNull()
                    .isInstanceOf(InsufficientPermissionException.class);
            assertThat(approveExecuted.get()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    // =========================================================================
    // 5. Sự cố kho lưu trữ chính trả 503 TAM_GIAN_DOAN (PX-21 & thiet-ke-phien-sprint-1.md §2.2 Mục 3)
    // =========================================================================

    @Test
    @DisplayName("PX-21: Khi kho lưu trữ chính gặp sự cố khi đọc phiên/tài khoản, trả 503 TAM_GIAN_DOAN, không coi là đăng xuất và không gửi Set-Cookie xóa phiên")
    void primaryStoreOutageReturns503TamGianDoanWithoutClearingSessionCookie() throws Exception {
        PhienXacThucRepository failingSessionRepo = new PhienXacThucRepository() {
            @Override
            public Optional<PhienXacThuc> findById(UUID id) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }

            @Override
            public Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }

            @Override
            public Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }

            @Override
            public List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }

            @Override
            public PhienXacThuc save(PhienXacThuc phienXacThuc) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }

            @Override
            public Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(
                    String oldMaCookieBam,
                    PhienXacThuc newPhien,
                    Instant now
            ) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }

            @Override
            public Optional<PhienXacThuc> revokeByMaCookieBam(
                    String maCookieBam,
                    String lyDoThuHoi,
                    Instant now,
                    boolean giuLaiCredentialDeThuHoiProvider
            ) {
                throw new RuntimeException("Simulated PostgreSQL connection timeout");
            }
        };

        CurrentPermissionAndCsrfService serviceWithOutage = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                failingSessionRepo,
                sessionLifecycleAdapter,
                List.of(ALLOWED_ORIGIN)
        );
        AuthCsrfAndMeController controllerWithOutage =
                new AuthCsrfAndMeController(serviceWithOutage, cookieConfig);
        MockMvc outageMockMvc = MockMvcBuilders.standaloneSetup(controllerWithOutage)
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler())
                .build();

        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();

        outageMockMvc.perform(get("/api/me")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, rawSessionCookie)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.maLoi").value(AuthProviderUnavailableException.MA_LOI));
    }

    // =========================================================================
    // Helper classes & methods
    // =========================================================================

    private record ActiveSessionFixture(
            TaiKhoan taiKhoan,
            PhienXacThuc phien,
            String rawSessionCookie
    ) {
    }

    private ActiveSessionFixture seedActiveVerifiedAccountAndSession(
            String email,
            VaiTroTaiKhoan vaiTro
    ) {
        return seedAccountAndSession(
                email,
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang"
        );
    }

    private ActiveSessionFixture seedAccountAndSessionWithId(
            UUID accountId,
            String email,
            VaiTroTaiKhoan vaiTro
    ) {
        return seedAccountAndSessionInternal(
                accountId,
                email,
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang"
        );
    }

    private ActiveSessionFixture seedAccountAndSession(
            String email,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh,
            long mocThuHoi,
            String trangThaiDongBoAuth
    ) {
        return seedAccountAndSessionInternal(
                UUID.randomUUID(),
                email,
                vaiTro,
                trangThai,
                emailDaXacMinh,
                mocThuHoi,
                trangThaiDongBoAuth
        );
    }

    private ActiveSessionFixture seedAccountAndSessionInternal(
            UUID accountId,
            String email,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh,
            long mocThuHoi,
            String trangThaiDongBoAuth
    ) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                accountId,
                UUID.randomUUID(),
                email,
                "Người dùng " + email,
                "0901234567",
                vaiTro,
                trangThai,
                emailDaXacMinh,
                mocThuHoi,
                trangThaiDongBoAuth,
                now,
                now,
                now
        );
        taiKhoanRepository.save(taiKhoan);

        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        String cookieHash = sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie);
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                taiKhoan.getId(),
                cookieHash,
                mocThuHoi,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
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
        return config;
    }

    private static String extractCsrfTokenFromJson(String json) {
        String marker = "\"csrfToken\":\"";
        int start = json.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        assertThat(valueEnd).isGreaterThan(valueStart);
        return json.substring(valueStart, valueEnd);
    }

    /**
     * Controller giả lập các endpoint nghiệp vụ dùng trong test để xác minh CsrfAndOriginProtectionFilter,
     * RBAC, Ownership chuId, kiểm lại quyền trong giao dịch và quy tắc không phát Set-Cookie trên endpoint thường.
     */
    @RestController
    @RequestMapping("/api/probe")
    static class ProtectedBusinessProbeController {

        final AtomicInteger writeExecutionCount = new AtomicInteger(0);
        final Map<UUID, UUID> listingOwners = new java.util.concurrent.ConcurrentHashMap<>();

        private final CurrentPermissionAndCsrfService permissionAndCsrfService;
        private final AuthSessionCookieConfig cookieConfig;

        ProtectedBusinessProbeController(
                CurrentPermissionAndCsrfService permissionAndCsrfService,
                AuthSessionCookieConfig cookieConfig
        ) {
            this.permissionAndCsrfService = permissionAndCsrfService;
            this.cookieConfig = cookieConfig;
        }

        @GetMapping("/public-listings")
        ResponseEntity<Map<String, String>> getPublicListings(HttpServletRequest request) {
            Object attr = request.getAttribute(CurrentPermissionAndCsrfService.REQUEST_ATTR_PRINCIPAL);
            String role = (attr instanceof PrincipalPhienHienTai p)
                    ? p.getVaiTro().getGiaTri()
                    : "khach";
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("viewerRole", role));
        }

        @PostMapping("/listings")
        ResponseEntity<Map<String, String>> createListing(
                HttpServletRequest request,
                @RequestBody(required = false) Map<String, Object> payload
        ) {
            String rawSession = AuthCsrfAndMeController.docGiaTriCookie(
                    request,
                    cookieConfig.getSessionCookieName()
            );
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSession,
                    ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                    null,
                    null,
                    payload,
                    principal -> {
                        writeExecutionCount.incrementAndGet();
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                                .body(Map.of("status", "created", "ownerId", principal.getTaiKhoanId().toString()));
                    }
            );
        }

        @PutMapping("/listings/{id}")
        ResponseEntity<Map<String, String>> updateOwnListing(
                @PathVariable("id") UUID listingId,
                HttpServletRequest request,
                @RequestBody(required = false) Map<String, Object> payload
        ) {
            String rawSession = AuthCsrfAndMeController.docGiaTriCookie(
                    request,
                    cookieConfig.getSessionCookieName()
            );
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSession,
                    ThaoTacNghiepVu.CHINH_SUA_TIN_CUA_MINH,
                    () -> listingOwners.get(listingId),
                    listingId,
                    payload,
                    principal -> {
                        writeExecutionCount.incrementAndGet();
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                                .body(Map.of("status", "updated"));
                    }
            );
        }

        @PatchMapping("/listings/{id}")
        ResponseEntity<Map<String, String>> patchOwnListing(
                @PathVariable("id") UUID listingId,
                HttpServletRequest request,
                @RequestBody(required = false) Map<String, Object> payload
        ) {
            return updateOwnListing(listingId, request, payload);
        }

        @DeleteMapping("/listings/{id}")
        ResponseEntity<Map<String, String>> deleteOwnListing(
                @PathVariable("id") UUID listingId,
                HttpServletRequest request
        ) {
            String rawSession = AuthCsrfAndMeController.docGiaTriCookie(
                    request,
                    cookieConfig.getSessionCookieName()
            );
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSession,
                    ThaoTacNghiepVu.XOA_MEM_TIN_CUA_MINH,
                    () -> listingOwners.get(listingId),
                    listingId,
                    null,
                    principal -> {
                        writeExecutionCount.incrementAndGet();
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                                .body(Map.of("status", "deleted"));
                    }
            );
        }

        @PostMapping("/moderation/approve")
        ResponseEntity<Map<String, String>> approveListing(
                HttpServletRequest request,
                @RequestBody(required = false) Map<String, Object> payload
        ) {
            String rawSession = AuthCsrfAndMeController.docGiaTriCookie(
                    request,
                    cookieConfig.getSessionCookieName()
            );
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSession,
                    ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                    null,
                    null,
                    payload,
                    principal -> {
                        writeExecutionCount.incrementAndGet();
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                                .body(Map.of("status", "approved"));
                    }
            );
        }

        @PostMapping("/admin/accounts")
        ResponseEntity<Map<String, String>> manageAccounts(
                HttpServletRequest request,
                @RequestBody(required = false) Map<String, Object> payload
        ) {
            String rawSession = AuthCsrfAndMeController.docGiaTriCookie(
                    request,
                    cookieConfig.getSessionCookieName()
            );
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSession,
                    ThaoTacNghiepVu.TAO_CAP_NHAT_KHOA_MO_KHOA_TAI_KHOAN,
                    null,
                    null,
                    payload,
                    principal -> {
                        writeExecutionCount.incrementAndGet();
                        return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore())
                                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                                .body(Map.of("status", "admin_ok"));
                    }
            );
        }
    }
}
