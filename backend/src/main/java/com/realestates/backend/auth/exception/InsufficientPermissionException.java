package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ khi tài khoản hiện tại không đủ vai trò RBAC hoặc không phải chủ sở hữu tài nguyên
 * để thực hiện thao tác nghiệp vụ (403 KHONG_DU_QUYEN theo ma-tran-quyen-va-trang-thai-sprint-1.md).
 */
public class InsufficientPermissionException extends RuntimeException {

    public static final String MA_LOI = "KHONG_DU_QUYEN";
    public static final String THONG_DIEP_MAC_DINH =
            "Tài khoản hiện tại không đủ quyền thực hiện thao tác này.";

    public InsufficientPermissionException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public InsufficientPermissionException(String message) {
        super(message != null && !message.isBlank() ? message : THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
