package com.realestates.backend.auth.exception;

/**
 * Ngoại lệ 409 HO_SO_XAC_THUC_CAN_KIEM_TRA khi danh tính provider ngoài luồng chưa có hồ sơ app
 * và thiếu họ tên hợp lệ (AUTH-17 / api-specification-sprint-1.md §8.4, §8.9).
 */
public class AuthProfileNeedsInspectionException extends RuntimeException {
    public static final String MA_LOI = "HO_SO_XAC_THUC_CAN_KIEM_TRA";
    public static final String THONG_DIEP_MAC_DINH =
            "Hồ sơ xác thực cần được nhóm vận hành kiểm tra trước khi cấp quyền.";

    public AuthProfileNeedsInspectionException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public AuthProfileNeedsInspectionException(String message) {
        super(message);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
