package com.realestates.backend.wallet.exception;

public class InvalidPurchaseIntentException extends RuntimeException {

    public static final String MA_LOI = "Y_DINH_KHONG_HOP_LE";

    public InvalidPurchaseIntentException(String message) {
        super(message);
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
