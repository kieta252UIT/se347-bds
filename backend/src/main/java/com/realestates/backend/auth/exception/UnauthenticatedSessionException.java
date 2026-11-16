package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ khi phiên xác thực không tồn tại, đã bị thu hồi hoặc không còn hiệu lực
 * theo mốc thu hồi tài khoản (401 CHUA_DANG_NHAP theo api-specification-sprint-1.md §8.9).
 */
public class UnauthenticatedSessionException extends RuntimeException {

    public static final String MA_LOI = "CHUA_DANG_NHAP";
    public static final String THONG_DIEP_MAC_DINH =
            "Chưa đăng nhập hoặc phiên đăng nhập đã hết hiệu lực.";

    public UnauthenticatedSessionException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public UnauthenticatedSessionException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
