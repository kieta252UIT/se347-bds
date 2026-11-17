package com.realestates.backend.listing.domain;

import java.util.Optional;

public enum LoaiGiaoDich {
    BAN("ban"),
    CHO_THUE("cho_thue");

    private final String giaTri;

    LoaiGiaoDich(String giaTri) {
        this.giaTri = giaTri;
    }

    public String getGiaTri() {
        return giaTri;
    }

    public static Optional<LoaiGiaoDich> tuGiaTri(String giaTri) {
        for (LoaiGiaoDich loai : values()) {
            if (loai.giaTri.equals(giaTri)) {
                return Optional.of(loai);
            }
        }
        return Optional.empty();
    }
}

