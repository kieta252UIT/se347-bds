package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.dto.DatLaiKetQua;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InvalidVerificationLinkException;
import com.realestates.backend.auth.exception.PasswordPolicyViolationException;
import com.realestates.backend.auth.exception.PasswordResetInProgressException;
import com.realestates.backend.auth.exception.PasswordResetSyncIncompleteException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ ứng dụng xử lý yêu cầu và hoàn tất đặt lại mật khẩu, thu hồi mọi phiên cũ,
 * bao gồm trường hợp lỗi và bất đồng bộ giữa Auth và Database (UC-29 / FR-15 / AC-15.3 / AC-15.4 / TS1-017 / TS1-018):
 *
 * 1. POST /api/auth/password/reset-requests:
 *    - Nhận email, kiểm tra rate-limit, gửi recovery email qua Supabase Auth.
 *    - Phản hồi trung tính 202 Accepted (không để lộ sự tồn tại của email, không tạo phiên, không đổi epoch).
 *
 * 2. POST /api/auth/password/reset:
 *    - Kiểm tra CSRF, rate-limit và cấu hình mật khẩu (>= 8 ký tự Unicode, <= 72 bytes UTF-8).
 *    - Xác minh tokenHash (type = "recovery") với Supabase Auth.
 *    - Mở transaction DB ngắn: khóa tai_khoan, kiểm tra gate ('san_sang'),
 *      tăng mốc thu hồi (moc_thu_hoi + 1) và thu hồi toàn bộ phiên PostgreSQL cũ (commit trước khi gọi provider).
 *    - Gọi Supabase Admin API đổi mật khẩu cùng app_metadata.se214_password_reset_operation = operationId.
 *    - Sau khi remote thành công: CAS cập nhật app hoàn tất 'san_sang'.
 *    - Nếu provider lỗi/timeout: chuyển 'can_khoi_phuc' và trả 503 KET_QUA_DAT_LAI_CHUA_XAC_DINH.
 *    - Không tự động đăng nhập sau khi đặt lại mật khẩu; tài khoản khóa vẫn tiếp tục bị khóa.
 */
@Service
public class AuthPasswordResetService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MIN_PASSWORD_CODE_POINTS = 8;
    public static final int MAX_PASSWORD_UTF8_BYTES = 72;
    public static final int MAX_TOKEN_HASH_LENGTH = 1024;

    private static final Set<String> ALLOWED_RESET_REQUEST_FIELDS = Set.of("email");
    private static final Set<String> ALLOWED_RESET_FIELDS = Set.of("tokenHash", "matKhauMoi", "token", "otp", "email");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    );

    private final TaiKhoanRepository taiKhoanRepository;
    private final PhienXacThucRepository phienXacThucRepository;
    private final SupabaseAuthPort supabaseAuthPort;
    private final AuthEmailRateLimitPort rateLimitPort;
    private Clock clock;

    @Autowired
    public AuthPasswordResetService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            SupabaseAuthPort supabaseAuthPort,
            AuthEmailRateLimitPort rateLimitPort
    ) {
        this(taiKhoanRepository, phienXacThucRepository, supabaseAuthPort, rateLimitPort, Clock.systemUTC());
    }

    public AuthPasswordResetService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            SupabaseAuthPort supabaseAuthPort,
            AuthEmailRateLimitPort rateLimitPort,
            Clock clock
    ) {
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.phienXacThucRepository = Objects.requireNonNull(phienXacThucRepository, "phienXacThucRepository không được null");
        this.supabaseAuthPort = Objects.requireNonNull(supabaseAuthPort, "supabaseAuthPort không được null");
        this.rateLimitPort = Objects.requireNonNull(rateLimitPort, "rateLimitPort không được null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Yêu cầu gửi email khôi phục mật khẩu (POST /api/auth/password/reset-requests, operationId: yeuCauDatLaiMatKhau).
     * - Chỉ nhận đúng trường `email` (chặn trường dư như `type`, `role`, `trangThai`...).
     * - Áp dụng rate limit 1 lần/60s/email, 5 lần/h/email, 20 lần/h/IP trước khi gọi provider.
     * - Không để lộ email có tồn tại hay không; luôn trả 202 Accepted trung tính.
     */
    public GuiEmailKetQua yeuCauDatLaiMatKhau(Map<String, Object> rawPayload, String clientIp) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    List.of(new FieldErrorItem("email", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Email là bắt buộc."))
            );
        }

        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        for (String fieldName : rawPayload.keySet()) {
            if (!ALLOWED_RESET_REQUEST_FIELDS.contains(fieldName)) {
                fieldErrors.add(new FieldErrorItem(
                        fieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Trường dữ liệu '" + fieldName + "' không được phép trong yêu cầu đặt lại mật khẩu."
                ));
            }
        }

        String rawEmail = extractRequiredStringField(rawPayload, "email", fieldErrors);
        String normalizedEmail = null;
        if (rawEmail != null) {
            normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
            if (normalizedEmail.isEmpty()) {
                fieldErrors.add(new FieldErrorItem("email", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Email không được để trống."));
            } else if (normalizedEmail.length() > MAX_EMAIL_LENGTH) {
                fieldErrors.add(new FieldErrorItem(
                        "email",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Độ dài email vượt quá giới hạn tối đa " + MAX_EMAIL_LENGTH + " ký tự."
                ));
            } else if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                fieldErrors.add(new FieldErrorItem("email", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Định dạng email không hợp lệ."));
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    fieldErrors
            );
        }

        // Kiểm tra và ghi nhận rate limit (1 lần/60s/email, 5 lần/giờ/email, 20 lần/giờ/IP)
        rateLimitPort.checkAndRecordVerificationEmailRequest(clientIp, normalizedEmail);

        // Kích hoạt gửi recovery qua Supabase Auth
        try {
            supabaseAuthPort.sendPasswordRecoveryEmail(normalizedEmail);
        } catch (AuthRateLimitExceededException ex) {
            throw ex;
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }

        return GuiEmailKetQua.daTiepNhanDatLaiMatKhau();
    }

    /**
     * Hoàn tất đặt lại mật khẩu bằng recovery token hash (POST /api/auth/password/reset, operationId: hoanTatDatLaiMatKhau).
     * - Kiểm tra cấu trúc dữ liệu, trường dư và chính sách mật khẩu mới.
     * - Kiểm tra rate limit hoàn tất (20 lần/5 phút/IP).
     * - Xác minh tokenHash với Supabase Auth (cố định type = "recovery").
     * - Lưu operationId, đặt 'dang_dat_lai', tăng moc_thu_hoi và thu hồi toàn bộ phiên PostgreSQL trước khi gọi provider.
     * - Cập nhật mật khẩu tại Supabase Admin API kèm app_metadata.se214_password_reset_operation = operationId.
     * - CAS hoàn tất app sau khi provider thành công.
     * - Trả về 200 OK (canDangNhapLai = true).
     */
    public DatLaiKetQua hoanTatDatLaiMatKhau(Map<String, Object> rawPayload, String clientIp) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    List.of(
                            new FieldErrorItem("tokenHash", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Token hash là bắt buộc."),
                            new FieldErrorItem("matKhauMoi", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Mật khẩu mới là bắt buộc.")
                    )
            );
        }

        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        for (String fieldName : rawPayload.keySet()) {
            if (!ALLOWED_RESET_FIELDS.contains(fieldName)) {
                fieldErrors.add(new FieldErrorItem(
                        fieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Trường dữ liệu '" + fieldName + "' không được phép trong yêu cầu hoàn tất đặt lại mật khẩu."
                ));
            }
        }

        String rawTokenHash = extractOptionalStringField(rawPayload, "tokenHash");
        if (rawTokenHash == null || rawTokenHash.isBlank()) {
            rawTokenHash = extractOptionalStringField(rawPayload, "token");
        }
        if (rawTokenHash == null || rawTokenHash.isBlank()) {
            rawTokenHash = extractOptionalStringField(rawPayload, "otp");
        }
        if (rawTokenHash == null || rawTokenHash.isBlank()) {
            fieldErrors.add(new FieldErrorItem("tokenHash", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Token hash là bắt buộc."));
        } else if (rawTokenHash.trim().length() > MAX_TOKEN_HASH_LENGTH) {
            fieldErrors.add(new FieldErrorItem("tokenHash", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Token hash vượt quá độ dài tối đa."));
        }

        String rawMatKhauMoi = extractOptionalStringField(rawPayload, "matKhauMoi");
        if (rawMatKhauMoi == null) {
            fieldErrors.add(new FieldErrorItem("matKhauMoi", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Mật khẩu mới là bắt buộc."));
        }

        if (!fieldErrors.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    fieldErrors
            );
        }

        // Kiểm tra chính sách mật khẩu mới (openapi & x-cau-hinh-auth-thiet-ke: min 8 code points, max 72 bytes UTF-8)
        kiemTraChinhSachMatKhauMoi(rawMatKhauMoi);

        // Áp dụng rate limit hoàn tất xác minh (20 lần/5 phút/IP)
        rateLimitPort.checkAndRecordEmailVerifyAttempt(clientIp);

        String rawEmail = extractOptionalStringField(rawPayload, "email");

        // 1. Xác minh proof với Supabase Auth (cố định type = "recovery")
        SupabaseVerifiedIdentity verifiedIdentity;
        try {
            if (rawEmail != null && !rawEmail.isBlank() && rawTokenHash.trim().matches("^[0-9]{6,8}$")) {
                verifiedIdentity = supabaseAuthPort.verifyPasswordRecoveryOtp(
                        rawEmail.trim().toLowerCase(Locale.ROOT),
                        rawTokenHash.trim()
                );
            } else {
                verifiedIdentity = supabaseAuthPort.verifyPasswordRecoveryTokenHash(rawTokenHash.trim());
            }
        } catch (InvalidVerificationLinkException ex) {
            throw ex;
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidVerificationLinkException("Liên kết recovery không hợp lệ, hết hạn hoặc đã dùng.", ex);
        }

        if (verifiedIdentity == null || verifiedIdentity.supabaseAuthUserId() == null) {
            throw new InvalidVerificationLinkException("Liên kết recovery không hợp lệ, hết hạn hoặc đã dùng.");
        }

        // 2. Tra cứu tài khoản nội bộ và kiểm tra gate
        UUID providerUserId = verifiedIdentity.supabaseAuthUserId();
        String identityEmail = verifiedIdentity.email() != null ? verifiedIdentity.email().trim().toLowerCase(Locale.ROOT) : null;
        TaiKhoan account = taiKhoanRepository.findBySupabaseAuthUserId(providerUserId)
                .or(() -> (identityEmail != null && !identityEmail.isBlank())
                        ? taiKhoanRepository.findByEmail(identityEmail)
                        : Optional.empty())
                .orElseGet(() -> {
                    // Phục hồi hồ sơ tài khoản nếu chưa có trong DB (vd InMemory sau khởi động lại)
                    String hoTen = (verifiedIdentity.hoTenMetadata() != null && !verifiedIdentity.hoTenMetadata().isBlank())
                            ? verifiedIdentity.hoTenMetadata().trim()
                            : "Người dùng";
                    TaiKhoan recovered = TaiKhoan.taoNguoiDungDaXacMinh(
                            UUID.randomUUID(),
                            providerUserId,
                            identityEmail != null ? identityEmail : (rawEmail != null ? rawEmail.trim().toLowerCase(Locale.ROOT) : ""),
                            hoTen,
                            verifiedIdentity.soDienThoaiMetadata(),
                            clock.instant()
                    );
                    return taiKhoanRepository.save(recovered);
                });

        if (!account.isEmailDaXacMinh()) {
            account = account.danhDauEmailDaXacMinh(clock.instant());
            account = taiKhoanRepository.save(account);
        }

        if (!"san_sang".equals(account.getTrangThaiDongBoAuth())) {
            Instant staleThreshold = clock.instant().minusSeconds(30);
            if (account.getCapNhatAuthLuc() != null && account.getCapNhatAuthLuc().isBefore(staleThreshold)) {
                // Thao tác dở trước đó đã quá 30s -> CAS mở lại gate về san_sang theo quy trình vận hành §5.4
                taiKhoanRepository.updateDongBoAuthCas(account.getId(), account.getMaThaoTacAuth(), "san_sang", null, clock.instant());
                account = taiKhoanRepository.findById(account.getId()).orElse(account);
            }
        }

        if (!"san_sang".equals(account.getTrangThaiDongBoAuth())) {
            // Đang có thao tác reset hoặc khôi phục khác chưa hoàn tất -> 409 DAT_LAI_DANG_XU_LY
            throw new PasswordResetInProgressException();
        }

        // 3. Mở transaction DB: sinh operationId, đặt 'dang_dat_lai', tăng epoch, thu hồi mọi phiên cũ
        UUID operationId = UUID.randomUUID();
        Instant now = clock.instant();

        TaiKhoan updatingAccount = account.batDauDatLaiMatKhau(operationId, now);
        try {
            taiKhoanRepository.save(updatingAccount);
        } catch (Exception ex) {
            throw new AuthProviderUnavailableException("Không thể ghi nhận trạng thái đặt lại mật khẩu trong CSDL.", ex);
        }

        try {
            phienXacThucRepository.revokeAllByTaiKhoanId(updatingAccount.getId(), PhienXacThuc.LY_DO_DAT_LAI_MAT_KHAU, now);
        } catch (Exception ex) {
            throw new AuthProviderUnavailableException("Không thể thu hồi phiên cũ trong CSDL.", ex);
        }

        // Commit trước khi gọi provider. Người dùng chấp nhận thu hồi sớm dù bước sau lỗi (§5.2).

        // 4. Ngoài transaction DB: gọi Admin updateUserById cập nhật mật khẩu và dấu operation
        try {
            supabaseAuthPort.updatePasswordWithResetMarker(account.getSupabaseAuthUserId(), rawMatKhauMoi, operationId);
        } catch (PasswordPolicyViolationException ex) {
            // Provider từ chối chắc chắn trước khi cập nhật (vd chính sách mật khẩu phía remote):
            // Hoàn tất thất bại bằng CAS, mở gate thành 'san_sang', giữ epoch đã tăng
            try {
                taiKhoanRepository.updateDongBoAuthCas(account.getId(), operationId, "san_sang", null, clock.instant());
            } catch (Exception ignored) {
            }
            throw ex;
        } catch (Exception ex) {
            // Timeout, network error, hoặc 5xx: provider có thể đã commit nhưng app chưa xác nhận được.
            // Chuyển sang 'can_khoi_phuc' và giữ operationId để worker đối chiếu
            try {
                taiKhoanRepository.updateDongBoAuthCas(account.getId(), operationId, "can_khoi_phuc", operationId, clock.instant());
            } catch (Exception ignored) {
            }
            throw new PasswordResetSyncIncompleteException(
                    "Chưa xác định được toàn bộ kết quả đặt lại. Không tự gửi lại liên tục; thử lại sau khi hệ thống kiểm tra thao tác dở. Nếu liên kết đã dùng, yêu cầu email mới.",
                    ex
            );
        }

        // 5. Provider thành công: CAS cập nhật trạng thái app về 'san_sang' và xóa operationId
        boolean casSuccess = taiKhoanRepository.updateDongBoAuthCas(
                account.getId(),
                operationId,
                "san_sang",
                null,
                clock.instant()
        );

        if (!casSuccess) {
            throw new PasswordResetSyncIncompleteException();
        }

        return DatLaiKetQua.thanhCong();
    }

    private void kiemTraChinhSachMatKhauMoi(String matKhauMoi) {
        if (matKhauMoi == null || matKhauMoi.isBlank()) {
            throw new PasswordPolicyViolationException();
        }
        int codePoints = matKhauMoi.codePointCount(0, matKhauMoi.length());
        if (codePoints < MIN_PASSWORD_CODE_POINTS) {
            throw new PasswordPolicyViolationException();
        }
        byte[] utf8Bytes = matKhauMoi.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > MAX_PASSWORD_UTF8_BYTES) {
            throw new PasswordPolicyViolationException();
        }
    }

    private String extractRequiredStringField(Map<String, Object> payload, String fieldName, List<FieldErrorItem> fieldErrors) {
        if (!payload.containsKey(fieldName)) {
            fieldErrors.add(new FieldErrorItem(fieldName, MA_LOI_DU_LIEU_KHONG_HOP_LE, "Trường '" + fieldName + "' là bắt buộc."));
            return null;
        }
        Object value = payload.get(fieldName);
        if (value == null) {
            fieldErrors.add(new FieldErrorItem(fieldName, MA_LOI_DU_LIEU_KHONG_HOP_LE, "Trường '" + fieldName + "' không được null."));
            return null;
        }
        if (!(value instanceof String stringValue)) {
            fieldErrors.add(new FieldErrorItem(fieldName, MA_LOI_DU_LIEU_KHONG_HOP_LE, "Trường '" + fieldName + "' phải là chuỗi ký tự."));
            return null;
        }
        return stringValue;
    }

    private String extractOptionalStringField(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return null;
    }
}
