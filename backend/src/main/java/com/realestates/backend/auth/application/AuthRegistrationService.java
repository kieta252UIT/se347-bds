package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ ứng dụng xử lý nghiệp vụ đăng ký tài khoản công khai (UC-25 / FR-15 / AC-15.1).
 * Đảm bảo chỉ cho phép tạo vai trò Người dùng (nguoi_dung) và tuân thủ chuẩn hóa đầu vào AUTH-12, AUTH-13.
 */
@Service
public class AuthRegistrationService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final String MA_LOI_MAT_KHAU_KHONG_DAT = "MAT_KHAU_KHONG_DAT";
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MIN_HO_TEN_CODE_POINTS = 1;
    public static final int MAX_HO_TEN_CODE_POINTS = 100;
    public static final int MIN_SO_DIEN_THOAI_CODE_POINTS = 1;
    public static final int MAX_SO_DIEN_THOAI_CODE_POINTS = 32;
    public static final int MIN_MAT_KHAU_CODE_POINTS = 8;
    public static final int MAX_MAT_KHAU_UTF8_BYTES = 72;

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "email",
            "matKhau",
            "hoTen",
            "soDienThoai"
    );

    private static final Set<String> PRIVILEGED_ROLE_OR_STATE_FIELDS = Set.of(
            "vaiTro",
            "vai_tro",
            "role",
            "roles",
            "isAdmin",
            "is_admin",
            "trangThai",
            "trang_thai",
            "status",
            "emailDaXacMinh",
            "email_da_xac_minh",
            "emailVerified",
            "email_confirm",
            "taiKhoanId",
            "id",
            "supabaseAuthUserId",
            "supabase_auth_user_id",
            "user_metadata",
            "app_metadata",
            "mocThuHoi",
            "moc_thu_hoi",
            "type",
            "redirectTo"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    );

    private final TaiKhoanRepository taiKhoanRepository;
    private final SupabaseAuthPort supabaseAuthPort;

    public AuthRegistrationService(
            TaiKhoanRepository taiKhoanRepository,
            SupabaseAuthPort supabaseAuthPort
    ) {
        this.taiKhoanRepository = taiKhoanRepository;
        this.supabaseAuthPort = supabaseAuthPort;
    }

    /**
     * Thực hiện đăng ký người dùng mới từ payload JSON thô đã qua kiểm tra kích thước.
     */
    public GuiEmailKetQua dangKyNguoiDung(Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường bắt buộc.",
                    List.of(
                            new FieldErrorItem("email", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Email là bắt buộc."),
                            new FieldErrorItem("matKhau", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Mật khẩu là bắt buộc."),
                            new FieldErrorItem("hoTen", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Họ tên là bắt buộc.")
                    )
            );
        }

        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        // 1. Chặn tuyệt đối các trường tự khai vai trò / trạng thái / quyền hạn hoặc trường lạ (AC-15.1, TS1-006)
        for (String fieldName : rawPayload.keySet()) {
            if (!ALLOWED_FIELDS.contains(fieldName)) {
                String message = PRIVILEGED_ROLE_OR_STATE_FIELDS.contains(fieldName)
                        ? "Đăng ký công khai chỉ được tạo tài khoản vai trò Người dùng; không chấp nhận trường tự khai vai trò hoặc trạng thái."
                        : "Trường dữ liệu '" + fieldName + "' không được phép trong yêu cầu đăng ký.";
                fieldErrors.add(new FieldErrorItem(fieldName, MA_LOI_DU_LIEU_KHONG_HOP_LE, message));
            }
        }

        // 2. Đọc và kiểm tra kiểu dữ liệu chuỗi cho các trường hợp lệ
        String rawEmail = extractStringField(rawPayload, "email", true, fieldErrors);
        String rawMatKhau = extractStringField(rawPayload, "matKhau", true, fieldErrors);
        String rawHoTen = extractStringField(rawPayload, "hoTen", true, fieldErrors);
        String rawSoDienThoai = extractStringField(rawPayload, "soDienThoai", false, fieldErrors);

        // 3. Chuẩn hóa và kiểm tra email theo AUTH-12
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

        // 4. Chuẩn hóa và kiểm tra hoTen theo AUTH-02 & AUTH-12
        String normalizedHoTen = null;
        if (rawHoTen != null) {
            normalizedHoTen = rawHoTen.trim();
            int codePoints = normalizedHoTen.codePointCount(0, normalizedHoTen.length());
            if (normalizedHoTen.isBlank()
                    || codePoints < MIN_HO_TEN_CODE_POINTS
                    || codePoints > MAX_HO_TEN_CODE_POINTS
                    || containsControlCharacter(normalizedHoTen)) {
                fieldErrors.add(new FieldErrorItem(
                        "hoTen",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Họ tên là bắt buộc, từ 1 đến 100 ký tự và không chứa ký tự điều khiển."
                ));
            }
        }

        // 5. Chuẩn hóa và kiểm tra soDienThoai (tùy chọn) theo AUTH-02 & AUTH-12
        String normalizedSoDienThoai = null;
        if (rawSoDienThoai != null) {
            String trimmedPhone = rawSoDienThoai.trim();
            if (!trimmedPhone.isEmpty()) {
                int phoneCodePoints = trimmedPhone.codePointCount(0, trimmedPhone.length());
                if (phoneCodePoints < MIN_SO_DIEN_THOAI_CODE_POINTS
                        || phoneCodePoints > MAX_SO_DIEN_THOAI_CODE_POINTS
                        || containsControlOrWhitespace(trimmedPhone)) {
                    fieldErrors.add(new FieldErrorItem(
                            "soDienThoai",
                            MA_LOI_DU_LIEU_KHONG_HOP_LE,
                            "Số điện thoại không được chứa khoảng trắng và tối đa 32 ký tự."
                    ));
                } else {
                    normalizedSoDienThoai = trimmedPhone;
                }
            }
        }

        // 6. Kiểm tra matKhau theo AUTH-13 (không trim, >= 8 code points, không toàn khoảng trắng, <= 72 byte UTF-8)
        boolean passwordPolicyViolated = false;
        if (rawMatKhau != null) {
            int passwordCodePoints = rawMatKhau.codePointCount(0, rawMatKhau.length());
            int passwordUtf8Bytes = rawMatKhau.getBytes(StandardCharsets.UTF_8).length;
            if (passwordCodePoints < MIN_MAT_KHAU_CODE_POINTS
                    || rawMatKhau.isBlank()
                    || passwordUtf8Bytes > MAX_MAT_KHAU_UTF8_BYTES) {
                passwordPolicyViolated = true;
                fieldErrors.add(new FieldErrorItem(
                        "matKhau",
                        MA_LOI_MAT_KHAU_KHONG_DAT,
                        "Mật khẩu cần ít nhất 8 ký tự, không toàn khoảng trắng và tối đa 72 byte UTF-8."
                ));
            }
        }

        if (!fieldErrors.isEmpty()) {
            boolean onlyPasswordPolicyError = passwordPolicyViolated && fieldErrors.size() == 1;
            String topLevelCode = onlyPasswordPolicyError
                    ? MA_LOI_MAT_KHAU_KHONG_DAT
                    : MA_LOI_DU_LIEU_KHONG_HOP_LE;
            String topLevelMessage = onlyPasswordPolicyError
                    ? "Mật khẩu cần ít nhất 8 ký tự, không toàn khoảng trắng và tối đa 72 byte UTF-8."
                    : "Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại các trường thông tin.";
            throw new AuthValidationException(topLevelCode, topLevelMessage, fieldErrors);
        }

        // 7. Kiểm tra email định danh không trùng trước khi gọi provider (AUTH-01, TS1-007)
        if (taiKhoanRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException();
        }

        // 8. Khởi tạo tài khoản trên Supabase Auth với vai trò cố định NGUOI_DUNG ("nguoi_dung")
        SupabaseRegisteredUser registeredUser = supabaseAuthPort.registerUser(
                normalizedEmail,
                rawMatKhau,
                normalizedHoTen,
                normalizedSoDienThoai
        );

        // 9. Tạo bản ghi miền TaiKhoan với vai trò cố định VaiTroTaiKhoan.NGUOI_DUNG ("nguoi_dung")
        TaiKhoan taiKhoanMoi = TaiKhoan.taoNguoiDungMoi(
                UUID.randomUUID(),
                registeredUser.supabaseAuthUserId(),
                normalizedEmail,
                normalizedHoTen,
                normalizedSoDienThoai,
                Instant.now()
        );
        if (!taiKhoanMoi.getVaiTro().duocPhepDangKyCongKhai()
                || taiKhoanMoi.getVaiTro() != VaiTroTaiKhoan.NGUOI_DUNG) {
            throw new IllegalStateException("Đăng ký công khai chỉ được phép tạo vai trò Người dùng.");
        }

        try {
            taiKhoanRepository.save(taiKhoanMoi);
        } catch (RuntimeException ex) {
            supabaseAuthPort.deleteUserOnCompensation(registeredUser.supabaseAuthUserId());
            throw ex;
        }

        // 10. Kích hoạt gửi email xác minh đăng ký nếu bước tạo user chưa tự động gửi (AUTH-10)
        if (!registeredUser.verificationEmailAlreadyTriggered()) {
            supabaseAuthPort.sendSignupVerificationEmail(normalizedEmail);
        }

        return GuiEmailKetQua.daTiepNhanDangKy();
    }

    private static String extractStringField(
            Map<String, Object> payload,
            String fieldName,
            boolean required,
            List<FieldErrorItem> fieldErrors
    ) {
        if (!payload.containsKey(fieldName) || payload.get(fieldName) == null) {
            if (required) {
                fieldErrors.add(new FieldErrorItem(
                        fieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Trường '" + fieldName + "' là bắt buộc."
                ));
            }
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
