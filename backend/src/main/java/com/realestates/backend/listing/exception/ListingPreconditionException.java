package com.realestates.backend.listing.exception;

public class ListingPreconditionException extends RuntimeException {

    public static final String MA_LOI_PHIEN_BAN_CU = "PHIEN_BAN_CU";
    public static final String MA_LOI_THIEU_IF_MATCH = "THIEU_IF_MATCH";

    private final String maLoi;

    public ListingPreconditionException(String maLoi, String message) {
        super(message);
        this.maLoi = maLoi;
    }

    public String getMaLoi() {
        return maLoi;
    }

    public boolean laThieuIfMatch() {
        return MA_LOI_THIEU_IF_MATCH.equals(maLoi);
    }
}

