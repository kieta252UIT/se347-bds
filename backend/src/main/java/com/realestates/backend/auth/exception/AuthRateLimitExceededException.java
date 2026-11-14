package com.realestates.backend.auth.exception;

public class AuthRateLimitExceededException extends RuntimeException {
    public static final String MA_LOI = "QUA_NHIEU_YEU_CAU";
    public static final String THONG_DIEP_MAC_DINH =
            "Bạn đã gửi quá nhiều yêu cầu. Vui lòng đợi ít phút rồi thử lại.";

    public AuthRateLimitExceededException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public AuthRateLimitExceededException(String message) {
        super(message);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
