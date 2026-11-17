package com.realestates.backend.auth.exception;

import com.realestates.backend.auth.dto.FieldErrorItem;
import java.util.List;

public class PasswordPolicyViolationException extends RuntimeException {

    public static final String MA_LOI = "MAT_KHAU_KHONG_DAT";
    public static final String THONG_DIEP_MAC_DINH =
            "Mật khẩu chưa đạt cấu hình bảo mật đã được rà. Nếu mã đã được dùng, yêu cầu liên kết mới.";

    private final List<FieldErrorItem> truongLoi;

    public PasswordPolicyViolationException() {
        this(THONG_DIEP_MAC_DINH, List.of(new FieldErrorItem("matKhauMoi", MA_LOI, "Không đạt chính sách mật khẩu hiện hành.")));
    }

    public PasswordPolicyViolationException(String message) {
        this(message, List.of(new FieldErrorItem("matKhauMoi", MA_LOI, "Không đạt chính sách mật khẩu hiện hành.")));
    }

    public PasswordPolicyViolationException(String message, List<FieldErrorItem> truongLoi) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
        this.truongLoi = truongLoi != null ? truongLoi : List.of();
    }

    public String getMaLoi() {
        return MA_LOI;
    }

    public List<FieldErrorItem> getTruongLoi() {
        return truongLoi;
    }
}
