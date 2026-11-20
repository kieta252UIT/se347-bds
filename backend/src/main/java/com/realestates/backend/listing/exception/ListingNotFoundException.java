package com.realestates.backend.listing.exception;

public class ListingNotFoundException extends RuntimeException {

    public static final String MA_LOI = "KHONG_TIM_THAY";
    public static final String THONG_DIEP_MAC_DINH = "Không tìm thấy tin.";

    public ListingNotFoundException() {
        super(THONG_DIEP_MAC_DINH);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}

