package com.realestates.backend.auth.exception;

public class AccountLockedException extends RuntimeException {

    public static final String MA_LOI = "TAI_KHOAN_BI_KHOA";
    public static final String THONG_DIEP_MAC_DINH = "Tài khoản đang bị khóa.";

    public AccountLockedException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public AccountLockedException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
