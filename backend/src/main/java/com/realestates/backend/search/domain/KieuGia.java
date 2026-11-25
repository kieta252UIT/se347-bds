package com.realestates.backend.search.domain;

import java.util.Optional;

/** Cơ sở so sánh giá: tổng giá chuẩn (VND) hoặc giá chuẩn trên m2 (VND/m2). */
public enum KieuGia {
    TONG("tong"),
    M2("m2");

    private final String giaTri;

    KieuGia(String giaTri) {
        this.giaTri = giaTri;
    }

    public String getGiaTri() {
        return giaTri;
    }

    public static Optional<KieuGia> tuGiaTri(String giaTri) {
        for (KieuGia kieu : values()) {
            if (kieu.giaTri.equals(giaTri)) {
                return Optional.of(kieu);
            }
        }
        return Optional.empty();
    }
}
