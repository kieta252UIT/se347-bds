package com.realestates.backend.wallet.exception;

public class PurchaseIntentConflictException extends RuntimeException {

    public static final String MA_LOI = "XUNG_DOT_MA_Y_DINH";

    public PurchaseIntentConflictException(String message) {
        super(message);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
