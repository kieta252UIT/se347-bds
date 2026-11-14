package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ 503 DONG_BO_XAC_MINH_CHUA_HOAN_TAT khi Supabase Auth có thể đã xác minh liên kết
 * nhưng ứng dụng chưa hoàn tất đồng bộ trạng thái tài khoản hoặc thu hồi phiên (api-specification-sprint-1.md §8.5, §8.9).
 */
public class VerificationSyncIncompleteException extends RuntimeException {
    public static final String MA_LOI = "DONG_BO_XAC_MINH_CHUA_HOAN_TAT";
    public static final String THONG_DIEP_MAC_DINH =
            "Chưa hoàn tất đồng bộ xác minh. Hãy thử đăng nhập khi dịch vụ sẵn sàng; nếu cần, yêu cầu liên kết mới.";

    public VerificationSyncIncompleteException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public VerificationSyncIncompleteException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
