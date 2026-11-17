package com.realestates.backend.listing.exception;

public class ListingConflictException extends RuntimeException {

    public static final String MA_LOI_HAN_MUC_THANG = "HAN_MUC_THANG";
    public static final String MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG = "MA_Y_DINH_KHAC_NOI_DUNG";
    public static final String MA_LOI_TRANG_THAI_KHONG_HOP_LE = "TRANG_THAI_KHONG_HOP_LE";

    private final String maLoi;

    public ListingConflictException(String maLoi, String message) {
        super(message);
        this.maLoi = maLoi;
    }

    public String getMaLoi() {
        return maLoi;
    }
}
