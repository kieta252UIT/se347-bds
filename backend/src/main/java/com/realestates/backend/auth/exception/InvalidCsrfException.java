package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ khi yêu cầu thay đổi trạng thái (POST, PUT, DELETE, PATCH) thiếu/sai Origin
 * hoặc thiếu/sai/lệch ngữ cảnh CSRF token (403 CSRF_KHONG_HOP_LE theo PHIEN-01, PHIEN-06, PX-06).
 */
public class InvalidCsrfException extends RuntimeException {

    public static final String MA_LOI = "CSRF_KHONG_HOP_LE";
    public static final String THONG_DIEP_MAC_DINH =
            "Yêu cầu bị từ chối do nguồn gốc (Origin) hoặc mã bảo vệ CSRF không hợp lệ.";

    public InvalidCsrfException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public InvalidCsrfException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
