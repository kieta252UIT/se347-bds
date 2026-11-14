package com.realestates.backend.auth.exception;


public class AuthSyncInProgressException extends RuntimeException {

    public static final String MA_LOI = "DONG_BO_XAC_THUC_DANG_XU_LY";
    public static final String THONG_DIEP_MAC_DINH =
            "Tài khoản đang được đồng bộ bảo mật, vui lòng thử lại sau.";

    public AuthSyncInProgressException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public AuthSyncInProgressException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
