package com.realestates.backend.listing.domain;

public enum DinhDangAnh {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String duoiTep;

    DinhDangAnh(String mimeType, String duoiTep) {
        this.mimeType = mimeType;
        this.duoiTep = duoiTep;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getDuoiTep() {
        return duoiTep;
    }
}

