package com.realestates.backend.auth.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public static final String MA_LOI = "EMAIL_DA_TON_TAI";
    public static final String THONG_DIEP_MAC_DINH =
            "Email đã được đăng ký. Vui lòng đăng nhập, quên mật khẩu hoặc yêu cầu gửi lại email xác minh.";

    public EmailAlreadyExistsException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
