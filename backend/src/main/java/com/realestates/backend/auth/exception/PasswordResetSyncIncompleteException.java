package com.realestates.backend.auth.exception;

public class PasswordResetSyncIncompleteException extends RuntimeException {

    public static final String MA_LOI = "KET_QUA_DAT_LAI_CHUA_XAC_DINH";
    public static final String THONG_DIEP_MAC_DINH =
            "Chưa xác định được toàn bộ kết quả đặt lại. Không tự gửi lại liên tục; thử lại sau khi hệ thống kiểm tra thao tác dở. Nếu liên kết đã dùng, yêu cầu email mới.";

    public PasswordResetSyncIncompleteException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public PasswordResetSyncIncompleteException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public PasswordResetSyncIncompleteException(String message, Throwable cause) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH, cause);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
