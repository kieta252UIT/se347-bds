package com.realestates.backend.auth.exception;

public class PasswordResetInProgressException extends RuntimeException {

    public static final String MA_LOI = "DAT_LAI_DANG_XU_LY";
    public static final String THONG_DIEP_MAC_DINH =
            "Thao tác đặt lại trước đang được xử lý. Hãy thử lại khi hệ thống hoàn tất phục hồi.";

    public PasswordResetInProgressException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public PasswordResetInProgressException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
