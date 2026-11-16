package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ khi thông tin đăng nhập (email hoặc mật khẩu) không hợp lệ (401 DANG_NHAP_KHONG_HOP_LE).
 */
public class InvalidCredentialsException extends RuntimeException {

    public static final String MA_LOI = "DANG_NHAP_KHONG_HOP_LE";
    public static final String THONG_DIEP_MAC_DINH = "Email hoặc mật khẩu không đúng.";

    public InvalidCredentialsException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public InvalidCredentialsException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
