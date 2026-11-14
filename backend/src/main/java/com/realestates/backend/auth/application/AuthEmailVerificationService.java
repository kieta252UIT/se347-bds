package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.dto.XacMinhKetQua;
import com.realestates.backend.auth.exception.AuthProfileNeedsInspectionException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InvalidVerificationLinkException;
import com.realestates.backend.auth.exception.VerificationSyncIncompleteException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ ứng dụng xử lý nghiệp vụ xác minh email và gửi lại liên kết xác minh email
 * theo UC-26 / FR-15 / AC-15.2 / AC-15.3 / TS1-008..TS1-011 / AUTH-03, AUTH-05..07, AUTH-09, AUTH-12, AUTH-14, AUTH-15, AUTH-17.
 */
@Service
public class AuthEmailVerificationService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_TOKEN_HASH_LENGTH = 1024;
    public static final int MIN_HO_TEN_CODE_POINTS = 1;
    public static final int MAX_HO_TEN_CODE_POINTS = 100;
    public static final int MIN_SO_DIEN_THOAI_CODE_POINTS = 1;
    public static final int MAX_SO_DIEN_THOAI_CODE_POINTS = 32;

    private static final Set<String> ALLOWED_RESEND_FIELDS = Set.of("email");
    private static final Set<String> ALLOWED_VERIFY_FIELDS = Set.of("tokenHash", "otp", "maXacMinh", "token", "email");
    private static final int MIN_LINK_TOKEN_HASH_LENGTH = 10;

    private static final Pattern SIX_DIGIT_OTP_PATTERN = Pattern.compile("^[0-9]{6}$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    );

    private final TaiKhoanRepository taiKhoanRepository;
    private final SupabaseAuthPort supabaseAuthPort;
    private final AuthEmailRateLimitPort rateLimitPort;
    private final AuthBrowserSessionLifecyclePort browserSessionLifecyclePort;

    public AuthEmailVerificationService(
            TaiKhoanRepository taiKhoanRepository,
            SupabaseAuthPort supabaseAuthPort,
            AuthEmailRateLimitPort rateLimitPort,
            AuthBrowserSessionLifecyclePort browserSessionLifecyclePort
    ) {
        this.taiKhoanRepository = taiKhoanRepository;
        this.supabaseAuthPort = supabaseAuthPort;
        this.rateLimitPort = rateLimitPort;
        this.browserSessionLifecyclePort = browserSessionLifecyclePort;
    }

    /**
     * Xử lý yêu cầu gửi lại email xác minh (POST /api/auth/email/verification-requests, operationId: yeuCauGuiLaiXacMinh).
     * - Chỉ nhận đúng trường `email` (chặn trường dư như `type`, `vaiTro`, `emailDaXacMinh`, `redirectTo`...).
     * - Áp dụng rate limit trước khi tra cứu tài khoản (AUTH-14, api-specification §8.8).
     * - Email chưa đăng ký (AUTH-06) hoặc đã xác minh (AUTH-05) vẫn trả về 202 chung, không gửi email, không tạo tài khoản mới.
     * - Tài khoản tồn tại và chưa xác minh (hoặc retry đăng ký dở trên provider) sẽ gọi provider resend(type=signup).
     */
    public GuiEmailKetQua yeuCauGuiLaiXacMinh(Map<String, Object> rawPayload, String clientIp) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    List.of(new FieldErrorItem("email", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Email là bắt buộc."))
            );
        }

        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        for (String fieldName : rawPayload.keySet()) {
            if (!ALLOWED_RESEND_FIELDS.contains(fieldName)) {
                fieldErrors.add(new FieldErrorItem(
                        fieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Trường dữ liệu '" + fieldName + "' không được phép trong yêu cầu gửi lại xác minh."
                ));
            }
        }

        String rawEmail = extractRequiredStringField(rawPayload, "email", fieldErrors);
        String normalizedEmail = null;
        if (rawEmail != null) {
            normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
            if (normalizedEmail.isEmpty()) {
                fieldErrors.add(new FieldErrorItem(
                        "email",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Email không được để trống."
                ));
            } else if (normalizedEmail.length() > MAX_EMAIL_LENGTH
                    || containsControlOrWhitespace(normalizedEmail)
                    || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                fieldErrors.add(new FieldErrorItem(
                        "email",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Email không đúng định dạng hoặc vượt quá 254 ký tự."
                ));
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    fieldErrors
            );
        }

        // Áp dụng chống lạm dụng (1/60s/email, 5/giờ/email, 20/giờ/IP) trước khi kiểm tra tài khoản tồn tại (AUTH-14)
        rateLimitPort.checkAndRecordVerificationEmailRequest(clientIp, normalizedEmail);

        Optional<TaiKhoan> existingOpt = taiKhoanRepository.findByEmail(normalizedEmail);
        if (existingOpt.isEmpty()) {
            Optional<SupabaseVerifiedIdentity> providerIdentityOpt = supabaseAuthPort.findProviderUserByEmail(normalizedEmail);
            if (providerIdentityOpt.isPresent() && !providerIdentityOpt.get().emailConfirmed()) {
                supabaseAuthPort.sendSignupVerificationEmail(normalizedEmail);
            }
            // AUTH-06: Email chưa đăng ký (hoặc đã xác minh trên provider) -> vẫn trả thông báo 202 chung
            return GuiEmailKetQua.daTiepNhanGuiLaiXacMinh();
        }

        TaiKhoan taiKhoan = existingOpt.get();
        if (taiKhoan.isEmailDaXacMinh()) {
            // AUTH-05: Tài khoản đã xác minh -> không gửi thêm email xác minh, không đổi quyền/phiên, vẫn trả 202 chung
            return GuiEmailKetQua.daTiepNhanGuiLaiXacMinh();
        }

        // Tài khoản chưa xác minh email -> kích hoạt gửi lại qua Supabase Auth với mục đích cố định type = signup
        supabaseAuthPort.sendSignupVerificationEmail(normalizedEmail);
        return GuiEmailKetQua.daTiepNhanGuiLaiXacMinh();
    }

    /**
     * Hoàn tất xác minh email qua liên kết một lần (POST /api/auth/email/verify, operationId: hoanTatXacMinhEmail).
     * - Chỉ nhận đúng trường `tokenHash` (1..1024 ký tự, không rỗng/toàn khoảng trắng); tuyệt đối không nhận `type` từ client.
     * - Áp dụng rate limit 20 lần/5 phút/IP (AUTH-14).
     * - Xác minh với Supabase Auth cố định `type = signup` (AUTH-15).
     * - Link sai/hết hạn/đã dùng trả 400 LIEN_KET_KHONG_HOP_LE và KHÔNG thu hồi phiên browser hiện tại (AC-15.2, AUTH-07).
     * - Xác minh thành công: đồng bộ `email_da_xac_minh = true` (hoặc tạo hồ sơ `nguoi_dung` nếu đăng ký dở đủ họ tên hợp lệ),
     *   thu hồi đúng phiên hiện có trên browser (nếu có) và trả `csrfToken` anonymous mới kèm `canDangNhapLai = true` (AUTH-03, AUTH-07, AUTH-09).
     */
    public XacMinhKetQua hoanTatXacMinhEmail(
            Map<String, Object> rawPayload,
            String clientIp,
            String currentBrowserSessionCookie
    ) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    List.of(new FieldErrorItem("tokenHash", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Mã xác minh là bắt buộc."))
            );
        }

        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        for (String fieldName : rawPayload.keySet()) {
            if (!ALLOWED_VERIFY_FIELDS.contains(fieldName)) {
                fieldErrors.add(new FieldErrorItem(
                        fieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Trường dữ liệu '" + fieldName + "' không được phép trong yêu cầu xác minh email."
                ));
            }
        }

        String normalizedEmail = null;
        if (rawPayload.containsKey("email") && rawPayload.get("email") != null) {
            String rawEmail = extractRequiredStringField(rawPayload, "email", fieldErrors);
            if (rawEmail != null) {
                normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
                if (normalizedEmail.isEmpty()
                        || normalizedEmail.length() > MAX_EMAIL_LENGTH
                        || containsControlOrWhitespace(normalizedEmail)
                        || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                    fieldErrors.add(new FieldErrorItem(
                            "email",
                            MA_LOI_DU_LIEU_KHONG_HOP_LE,
                            "Email không đúng định dạng hoặc vượt quá 254 ký tự."
                    ));
                }
            }
        }

        String codeFieldName = resolveVerifyCodeFieldName(rawPayload);
        boolean isExplicitOtpField = !"tokenHash".equals(codeFieldName);
        String rawCode = extractRequiredStringField(rawPayload, codeFieldName, fieldErrors);
        String trimmedCode = null;
        boolean isSixDigitNumericCode = false;

        if (rawCode != null) {
            trimmedCode = rawCode.trim();
            isSixDigitNumericCode = SIX_DIGIT_OTP_PATTERN.matcher(trimmedCode).matches();
            if (trimmedCode.isEmpty()
                    || trimmedCode.length() > MAX_TOKEN_HASH_LENGTH
                    || containsControlOrWhitespace(trimmedCode)) {
                fieldErrors.add(new FieldErrorItem(
                        codeFieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Mã xác minh không được để trống, không chứa khoảng trắng và tối đa 1024 ký tự."
                ));
            } else if (isExplicitOtpField || normalizedEmail != null || trimmedCode.length() < MIN_LINK_TOKEN_HASH_LENGTH) {
                // Mã xác minh OTP gồm đúng 6 kí tự và chỉ là chữ số (0-9)
                if (!isSixDigitNumericCode) {
                    fieldErrors.add(new FieldErrorItem(
                            codeFieldName,
                            MA_LOI_DU_LIEU_KHONG_HOP_LE,
                            "Mã xác minh email phải gồm đúng 6 kí tự và chỉ là chữ số (0-9)."
                    ));
                }
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu xác thực chưa hợp lệ.",
                    fieldErrors
            );
        }

        // Áp dụng chống lạm dụng 20 lần/5 phút/IP cho thao tác verify (AUTH-14)
        rateLimitPort.checkAndRecordEmailVerifyAttempt(clientIp);

        // Xác minh mã xác minh 6 chữ số (OTP) hoặc liên kết một lần (tokenHash) với Supabase Auth cố định type = signup (AUTH-15)
        // Nếu mã sai/hết hạn/đã dùng -> ném InvalidVerificationLinkException (400 LIEN_KET_KHONG_HOP_LE)
        // trước khi chạm vào phiên browser hiện tại (mã lỗi không làm mất phiên đang đăng nhập).
        SupabaseVerifiedIdentity verifiedIdentity = isSixDigitNumericCode
                ? supabaseAuthPort.verifySignupEmailOtp(normalizedEmail, trimmedCode)
                : supabaseAuthPort.verifySignupEmailTokenHash(trimmedCode);
        if (verifiedIdentity == null || !verifiedIdentity.emailConfirmed()) {
            throw new InvalidVerificationLinkException();
        }

        // Đồng bộ trạng thái xác minh vào kho tài khoản ứng dụng và thu hồi phiên browser hiện tại (nếu có)
        try {
            synchronizeVerifiedAccount(verifiedIdentity);
            browserSessionLifecyclePort.revokeCurrentBrowserSessionIfPresent(currentBrowserSessionCookie);
        } catch (AuthProfileNeedsInspectionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Provider đã xác minh nhưng bước đồng bộ DB / thu hồi phiên gặp sự cố -> 503 DONG_BO_XAC_MINH_CHUA_HOAN_TAT
            throw new VerificationSyncIncompleteException(VerificationSyncIncompleteException.THONG_DIEP_MAC_DINH, ex);
        }

        String newAnonymousCsrfToken = browserSessionLifecyclePort.issueAnonymousCsrfToken();
        return XacMinhKetQua.daXacMinh(newAnonymousCsrfToken);
    }

    private void synchronizeVerifiedAccount(SupabaseVerifiedIdentity verifiedIdentity) {
        UUID supabaseUserId = verifiedIdentity.supabaseAuthUserId();
        String normalizedEmail = verifiedIdentity.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        Optional<TaiKhoan> bySupabaseId = taiKhoanRepository.findBySupabaseAuthUserId(supabaseUserId);
        if (bySupabaseId.isPresent()) {
            TaiKhoan existing = bySupabaseId.get();
            TaiKhoan updated = existing.danhDauEmailDaXacMinh(now);
            taiKhoanRepository.save(updated);
            return;
        }

        Optional<TaiKhoan> byEmail = taiKhoanRepository.findByEmail(normalizedEmail);
        if (byEmail.isPresent()) {
            TaiKhoan existingByEmail = byEmail.get();
            if (!existingByEmail.getSupabaseAuthUserId().equals(supabaseUserId)) {
                throw new AuthProfileNeedsInspectionException();
            }
            TaiKhoan updated = existingByEmail.danhDauEmailDaXacMinh(now);
            taiKhoanRepository.save(updated);
            return;
        }

        // Phục hồi trường hợp đăng ký dở (provider đã tạo user nhưng bước lưu hồ sơ app trước đó bị gián đoạn - AUTH-11 / §8.4).
        // Nếu thiếu họ tên hợp lệ (danh tính ngoài luồng chưa có hồ sơ app) -> 409 HO_SO_XAC_THUC_CAN_KIEM_TRA (AUTH-17).
        String hoTenMeta = verifiedIdentity.hoTenMetadata();
        if (!isValidHoTenMetadata(hoTenMeta)) {
            throw new AuthProfileNeedsInspectionException();
        }
        String normalizedHoTen = hoTenMeta.trim();
        String normalizedPhone = normalizeOptionalPhoneMetadata(verifiedIdentity.soDienThoaiMetadata());

        TaiKhoan recoveredAccount = TaiKhoan.taoNguoiDungDaXacMinh(
                UUID.randomUUID(),
                supabaseUserId,
                normalizedEmail,
                normalizedHoTen,
                normalizedPhone,
                now
        );
        if (recoveredAccount.getVaiTro() != VaiTroTaiKhoan.NGUOI_DUNG) {
            throw new IllegalStateException("Phục hồi hồ sơ qua xác minh công khai chỉ được phép gán vai trò Người dùng.");
        }
        taiKhoanRepository.save(recoveredAccount);
    }

    private static boolean isValidHoTenMetadata(String rawHoTen) {
        if (rawHoTen == null) {
            return false;
        }
        String trimmed = rawHoTen.trim();
        if (trimmed.isBlank() || containsControlCharacter(trimmed)) {
            return false;
        }
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        return codePoints >= MIN_HO_TEN_CODE_POINTS && codePoints <= MAX_HO_TEN_CODE_POINTS;
    }

    private static String normalizeOptionalPhoneMetadata(String rawPhone) {
        if (rawPhone == null) {
            return null;
        }
        String trimmed = rawPhone.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        if (codePoints < MIN_SO_DIEN_THOAI_CODE_POINTS
                || codePoints > MAX_SO_DIEN_THOAI_CODE_POINTS
                || containsControlOrWhitespace(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String resolveVerifyCodeFieldName(Map<String, Object> payload) {
        if (payload.containsKey("maXacMinh")) {
            return "maXacMinh";
        }
        if (payload.containsKey("otp")) {
            return "otp";
        }
        if (payload.containsKey("token")) {
            return "token";
        }
        return "tokenHash";
    }

    private static String extractRequiredStringField(
            Map<String, Object> payload,
            String fieldName,
            List<FieldErrorItem> fieldErrors
    ) {
        if (!payload.containsKey(fieldName) || payload.get(fieldName) == null) {
            fieldErrors.add(new FieldErrorItem(
                    fieldName,
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Trường '" + fieldName + "' là bắt buộc."
            ));
            return null;
        }
        Object rawValue = payload.get(fieldName);
        if (!(rawValue instanceof String strValue)) {
            fieldErrors.add(new FieldErrorItem(
                    fieldName,
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Trường '" + fieldName + "' phải là chuỗi ký tự."
            ));
            return null;
        }
        return strValue;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static boolean containsControlOrWhitespace(String value) {
        return value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || Character.isWhitespace(cp));
    }
}
