package com.realestates.backend.auth.exception;

public class AuthProviderUnavailableException extends RuntimeException {
    public static final String MA_LOI = "TAM_GIAN_DOAN";
    public static final String THONG_DIEP_MAC_DINH =
            "Dịch vụ xác thực đang tạm gián đoạn. Vui lòng thử lại sau.";

    public AuthProviderUnavailableException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public AuthProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthProviderUnavailableException(String message) {
        super(message);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
