package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ khi đăng nhập vào tài khoản chưa xác minh email (403 EMAIL_CHUA_XAC_MINH).
 * Không tự động gửi lại email xác minh khi đăng nhập bị chặn bởi lỗi này.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public static final String MA_LOI = "EMAIL_CHUA_XAC_MINH";
    public static final String THONG_DIEP_MAC_DINH = "Email chưa được xác minh.";

    public EmailNotVerifiedException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public EmailNotVerifiedException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
