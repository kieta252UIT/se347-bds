package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ 400 LIEN_KET_KHONG_HOP_LE khi mã/liên kết xác minh email hoặc đặt lại mật khẩu
 * bị sai, đã hết hạn, đã sử dụng hoặc sai mục đích (api-specification-sprint-1.md §8.5, §8.9).
 */
public class InvalidVerificationLinkException extends RuntimeException {
    public static final String MA_LOI = "LIEN_KET_KHONG_HOP_LE";
    public static final String THONG_DIEP_MAC_DINH =
            "Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. Hãy yêu cầu email mới.";

    public InvalidVerificationLinkException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public InvalidVerificationLinkException(String message) {
        super(message);
    }

    public InvalidVerificationLinkException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
