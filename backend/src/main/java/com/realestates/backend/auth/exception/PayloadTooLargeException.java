package com.realestates.backend.auth.exception;

public class PayloadTooLargeException extends RuntimeException {
    public static final String MA_LOI = "NOI_DUNG_QUA_LON";
    public static final String THONG_DIEP_MAC_DINH =
            "Kích thước dữ liệu gửi lên vượt quá giới hạn cho phép (tối đa 16 KiB).";

    public PayloadTooLargeException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
