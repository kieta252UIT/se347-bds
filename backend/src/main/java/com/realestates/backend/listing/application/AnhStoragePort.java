package com.realestates.backend.listing.application;

public interface AnhStoragePort {

    void luu(String khoaDoiTuong, byte[] noiDung, String mimeType);

    byte[] doc(String khoaDoiTuong);
}

