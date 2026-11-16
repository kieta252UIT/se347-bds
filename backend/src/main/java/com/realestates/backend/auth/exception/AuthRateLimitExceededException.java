package com.realestates.backend.auth.exception;

public class AuthRateLimitExceededException extends RuntimeException {
    public static final String MA_LOI = "QUA_NHIEU_YEU_CAU";
    public static final String THONG_DIEP_MAC_DINH =
            "Bạn đã gửi quá nhiều yêu cầu. Vui lòng đợi ít phút rồi thử lại.";

    private final Integer retryAfterSeconds;

    public AuthRateLimitExceededException() {
        this(THONG_DIEP_MAC_DINH, null);
    }

    public AuthRateLimitExceededException(String message) {
        this(message, null);
    }

    public AuthRateLimitExceededException(int retryAfterSeconds) {
        this(THONG_DIEP_MAC_DINH, Math.max(1, retryAfterSeconds));
    }

    public AuthRateLimitExceededException(String message, Integer retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getMaLoi() {
        return MA_LOI;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
