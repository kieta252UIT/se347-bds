package com.realestates.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthRegistrationService;
import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.controller.AuthRegistrationController;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.dto.LoiApiResponse;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.auth.infrastructure.SupabaseAuthProperties;
import com.realestates.backend.auth.infrastructure.SupabaseAuthRestClientAdapter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.web.client.RestClient;

/**
 * Kiểm thử đơn vị và tích hợp cho task "Tích hợp đăng ký Supabase Auth; chỉ cho tạo vai trò Người dùng"
 * theo đặc tả TS1-006, TS1-007, AC-15.1, AUTH-01, AUTH-02, AUTH-10, AUTH-12, AUTH-13.
 */
class SupabaseAuthRegistrationTests {

    static class RecordingSupabaseAuthPort implements SupabaseAuthPort {
        final List<String> registeredEmails = new ArrayList<>();
        final List<String> verificationEmailsSent = new ArrayList<>();
        final List<UUID> compensatedDeletedUserIds = new ArrayList<>();
        boolean simulateProviderDuplicate = false;

        void reset() {
            registeredEmails.clear();
            verificationEmailsSent.clear();
            compensatedDeletedUserIds.clear();
            simulateProviderDuplicate = false;
        }

        @Override
        public SupabaseRegisteredUser registerUser(
                String normalizedEmail,
                String rawPassword,
                String normalizedHoTen,
                String normalizedSoDienThoai
        ) {
            if (simulateProviderDuplicate) {
                throw new EmailAlreadyExistsException();
            }
            registeredEmails.add(normalizedEmail);
            return new SupabaseRegisteredUser(UUID.randomUUID(), normalizedEmail, false);
        }

        @Override
        public void sendSignupVerificationEmail(String normalizedEmail) {
            verificationEmailsSent.add(normalizedEmail);
        }

        @Override
        public void deleteUserOnCompensation(UUID supabaseAuthUserId) {
            compensatedDeletedUserIds.add(supabaseAuthUserId);
        }
    }

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private RecordingSupabaseAuthPort supabaseAuthPort;
    private AuthRegistrationService authRegistrationService;
    private AuthRegistrationController authRegistrationController;
    private AuthExceptionHandler authExceptionHandler;

    @BeforeEach
    void setUp() {
        this.taiKhoanRepository = new InMemoryTaiKhoanRepository();
        this.supabaseAuthPort = new RecordingSupabaseAuthPort();
        this.authRegistrationService = new AuthRegistrationService(taiKhoanRepository, supabaseAuthPort);
        this.authRegistrationController = new AuthRegistrationController(authRegistrationService);
        this.authExceptionHandler = new AuthExceptionHandler();
    }

    @Test
    @DisplayName("TS1-006 / UC-25: Đăng ký hợp lệ tạo tài khoản vai trò nguoi_dung, email_da_xac_minh=false, trả về 202 và không cấp session cookie")
    void registerValidUserCreatesNguoiDungRoleOnly() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", "  User.Moi+Test@Example.COM ");
        payload.put("matKhau", "MatKhauAnToan123!");
        payload.put("hoTen", "  Nguyễn Văn A  ");
        payload.put("soDienThoai", " 0901234567 ");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        ResponseEntity<GuiEmailKetQua> response = authRegistrationController.dangKyNguoiDung(request, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().trangThai()).isEqualTo("da_tiep_nhan");
        assertThat(response.getBody().thongDiep()).isNotBlank();

        Optional<TaiKhoan> savedOpt = taiKhoanRepository.findByEmail("user.moi+test@example.com");
        assertThat(savedOpt).isPresent();

        TaiKhoan saved = savedOpt.get();
        assertThat(saved.getEmail()).isEqualTo("user.moi+test@example.com");
        assertThat(saved.getHoTen()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getSoDienThoai()).isEqualTo("0901234567");
        assertThat(saved.getVaiTro()).isEqualTo(VaiTroTaiKhoan.NGUOI_DUNG);
        assertThat(saved.getVaiTro().getGiaTri()).isEqualTo("nguoi_dung");
        assertThat(saved.getTrangThai()).isEqualTo(TrangThaiTaiKhoan.HOAT_DONG);
        assertThat(saved.isEmailDaXacMinh()).isFalse();

        assertThat(supabaseAuthPort.registeredEmails).containsExactly("user.moi+test@example.com");
        assertThat(supabaseAuthPort.verificationEmailsSent).containsExactly("user.moi+test@example.com");
    }

    @Test
    @DisplayName("TS1-006 / AC-15.1: Chặn tuyệt đối payload tự khai vaiTro KDV/Admin hoặc trạng thái/emailDaXacMinh với 400 DU_LIEU_KHONG_HOP_LE")
    void rejectPrivilegedOrSelfDeclaredRoleFields() {
        List<Map<String, Object>> maliciousPayloads = List.of(
                Map.of(
                        "email", "attacker1@example.com",
                        "matKhau", "MatKhauAnToan123!",
                        "hoTen", "Attacker KDV",
                        "vaiTro", "KDV"
                ),
                Map.of(
                        "email", "attacker2@example.com",
                        "matKhau", "MatKhauAnToan123!",
                        "hoTen", "Attacker Admin",
                        "vaiTro", "quan_tri_vien"
                ),
                Map.of(
                        "email", "attacker3@example.com",
                        "matKhau", "MatKhauAnToan123!",
                        "hoTen", "Attacker Role",
                        "role", "ADMIN",
                        "emailDaXacMinh", true
                )
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        for (Map<String, Object> payload : maliciousPayloads) {
            assertThatThrownBy(() -> authRegistrationController.dangKyNguoiDung(request, payload))
                    .isInstanceOf(AuthValidationException.class)
                    .satisfies(ex -> {
                        AuthValidationException valEx = (AuthValidationException) ex;
                        ResponseEntity<LoiApiResponse> errResp =
                                authExceptionHandler.handleValidationException(valEx, request);
                        assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(errResp.getHeaders().getCacheControl()).isEqualTo("no-store");
                        assertThat(errResp.getBody()).isNotNull();
                        assertThat(errResp.getBody().maLoi()).isEqualTo("DU_LIEU_KHONG_HOP_LE");
                        assertThat(errResp.getBody().truongLoi()).isNotEmpty();
                    });
        }

        assertThat(supabaseAuthPort.registeredEmails).isEmpty();
        assertThat(taiKhoanRepository.existsByEmail("attacker1@example.com")).isFalse();
        assertThat(taiKhoanRepository.existsByEmail("attacker2@example.com")).isFalse();
        assertThat(taiKhoanRepository.existsByEmail("attacker3@example.com")).isFalse();
    }

    @Test
    @DisplayName("TS1-007 / AUTH-01: Email trùng (kể cả khác chữ hoa/thường) bị từ chối 409 EMAIL_DA_TON_TAI và không ghi đè tài khoản cũ")
    void rejectDuplicateEmailCaseInsensitiveWithoutOverwritingExistingAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        Map<String, Object> firstPayload = Map.of(
                "email", "DupUser@Example.com",
                "matKhau", "MatKhauBanDau123!",
                "hoTen", "Người Dùng Gốc"
        );
        ResponseEntity<GuiEmailKetQua> firstResp = authRegistrationController.dangKyNguoiDung(request, firstPayload);
        assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        TaiKhoan original = taiKhoanRepository.findByEmail("dupuser@example.com").orElseThrow();

        Map<String, Object> duplicatePayload = Map.of(
                "email", "  dupuser@EXAMPLE.com ",
                "matKhau", "MatKhauMoiKhac999!",
                "hoTen", "Kẻ Ghi Đè"
        );

        assertThatThrownBy(() -> authRegistrationController.dangKyNguoiDung(request, duplicatePayload))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .satisfies(ex -> {
                    EmailAlreadyExistsException dupEx = (EmailAlreadyExistsException) ex;
                    ResponseEntity<LoiApiResponse> errResp =
                            authExceptionHandler.handleEmailAlreadyExists(dupEx, request);
                    assertThat(errResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(errResp.getBody()).isNotNull();
                    assertThat(errResp.getBody().maLoi()).isEqualTo("EMAIL_DA_TON_TAI");
                });

        TaiKhoan afterAttempt = taiKhoanRepository.findByEmail("dupuser@example.com").orElseThrow();
        assertThat(afterAttempt.getId()).isEqualTo(original.getId());
        assertThat(afterAttempt.getHoTen()).isEqualTo("Người Dùng Gốc");
        assertThat(afterAttempt.getVaiTro()).isEqualTo(VaiTroTaiKhoan.NGUOI_DUNG);
        assertThat(supabaseAuthPort.registeredEmails).hasSize(1);
    }

    @Test
    @DisplayName("AUTH-13: Kiểm tra chính sách mật khẩu (< 8 ký tự, toàn khoảng trắng, > 72 byte UTF-8) trả về 400 MAT_KHAU_KHONG_DAT")
    void rejectInvalidPasswordsAccordingToAuth13() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");

        Map<String, Object> shortPasswordPayload = Map.of(
                "email", "pwd1@example.com",
                "matKhau", "1234567",
                "hoTen", "Nguyễn Văn B"
        );

        assertThatThrownBy(() -> authRegistrationController.dangKyNguoiDung(request, shortPasswordPayload))
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> {
                    AuthValidationException valEx = (AuthValidationException) ex;
                    assertThat(valEx.getMaLoi()).isEqualTo("MAT_KHAU_KHONG_DAT");
                    assertThat(valEx.getTruongLoi()).hasSize(1);
                    assertThat(valEx.getTruongLoi().get(0).truong()).isEqualTo("matKhau");
                    assertThat(valEx.getTruongLoi().get(0).maLoi()).isEqualTo("MAT_KHAU_KHONG_DAT");
                });

        // 25 ký tự tiếng Việt 'ắ' (3 byte UTF-8 mỗi ký tự = 75 byte > 72 byte UTF-8)
        String over72Utf8BytesPassword = "ắ".repeat(25);
        Map<String, Object> over72BytesPayload = Map.of(
                "email", "pwd2@example.com",
                "matKhau", over72Utf8BytesPassword,
                "hoTen", "Nguyễn Văn B"
        );

        assertThatThrownBy(() -> authRegistrationController.dangKyNguoiDung(request, over72BytesPayload))
                .isInstanceOf(AuthValidationException.class)
                .satisfies(ex -> {
                    AuthValidationException valEx = (AuthValidationException) ex;
                    assertThat(valEx.getMaLoi()).isEqualTo("MAT_KHAU_KHONG_DAT");
                    assertThat(valEx.getTruongLoi()).hasSize(1);
                    assertThat(valEx.getTruongLoi().get(0).truong()).isEqualTo("matKhau");
                    assertThat(valEx.getTruongLoi().get(0).maLoi()).isEqualTo("MAT_KHAU_KHONG_DAT");
                });
    }

    @Test
    @DisplayName("AUTH-10 / TS1-007: SupabaseAuthRestClientAdapter gọi Admin createUser (email_confirm=false, vai_tro=nguoi_dung) và resend(type=signup)")
    void supabaseAuthRestClientAdapterCallsAdminCreateAndResendWithNguoiDungRole() {
        AtomicReference<String> adminRequestBody = new AtomicReference<>();
        AtomicReference<String> resendRequestBody = new AtomicReference<>();
        UUID expectedSupabaseId = UUID.randomUUID();

        ClientHttpRequestFactory inMemoryRequestFactory = (uri, httpMethod) -> {
            String path = uri.getPath();
            if ("/auth/v1/admin/users".equals(path)) {
                byte[] responseBytes = ("{\"id\":\"" + expectedSupabaseId + "\",\"email\":\"adapter@example.com\"}")
                        .getBytes(StandardCharsets.UTF_8);
                MockClientHttpResponse response = new MockClientHttpResponse(responseBytes, HttpStatus.OK);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        adminRequestBody.set(getBodyAsString(StandardCharsets.UTF_8));
                        return response;
                    }
                };
            }
            if ("/auth/v1/resend".equals(path)) {
                byte[] responseBytes = "{}".getBytes(StandardCharsets.UTF_8);
                MockClientHttpResponse response = new MockClientHttpResponse(responseBytes, HttpStatus.OK);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    protected org.springframework.http.client.ClientHttpResponse executeInternal() {
                        resendRequestBody.set(getBodyAsString(StandardCharsets.UTF_8));
                        return response;
                    }
                };
            }
            throw new AssertionError("Unexpected path: " + path);
        };

        SupabaseAuthProperties props = new SupabaseAuthProperties();
        props.setUrl("https://fcsbaknvtengrgsvuehv.supabase.co");
        props.setAnonKey("test-anon-key");
        props.setServiceRoleKey("test-service-role-key");

        RestClient testRestClient = RestClient.builder()
                .requestFactory(inMemoryRequestFactory)
                .build();

        SupabaseAuthRestClientAdapter adapter = new SupabaseAuthRestClientAdapter(props, testRestClient);
        SupabaseRegisteredUser created = adapter.registerUser(
                "adapter@example.com",
                "MatKhauAnToan123!",
                "Trần Thị C",
                "0912345678"
        );
        adapter.sendSignupVerificationEmail("adapter@example.com");

        assertThat(created.supabaseAuthUserId()).isEqualTo(expectedSupabaseId);
        assertThat(created.verificationEmailAlreadyTriggered()).isFalse();
        assertThat(adminRequestBody.get())
                .contains("\"email\":\"adapter@example.com\"")
                .contains("\"email_confirm\":false")
                .contains("\"vai_tro\":\"nguoi_dung\"")
                .doesNotContain("kiem_duyet_vien")
                .doesNotContain("quan_tri_vien");
        assertThat(resendRequestBody.get())
                .contains("\"type\":\"signup\"")
                .contains("\"email\":\"adapter@example.com\"");
    }
}
