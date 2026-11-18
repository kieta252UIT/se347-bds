package com.realestates.backend.listing.exception;

public class MapServiceUnavailableException extends RuntimeException {

    public static final String MA_LOI_BAN_DO_CHUA_SAN_SANG = "BAN_DO_CHUA_SAN_SANG";
    public static final String MA_LOI_TAM_GIAN_DOAN = "TAM_GIAN_DOAN";

    private final String maLoi;

    public MapServiceUnavailableException(String maLoi, String message) {
        super(message);
        this.maLoi = maLoi;
    }

    public MapServiceUnavailableException(String maLoi, String message, Throwable cause) {
        super(message, cause);
        this.maLoi = maLoi;
    }

    public String getMaLoi() {
        return maLoi;
    }
}
