package com.realestates.backend.listing.domain;

import java.util.Optional;

public enum LoaiBds {
    CAN_HO("can_ho"),
    NHA_RIENG("nha_rieng"),
    DAT("dat"),
    PHONG_TRO("phong_tro"),
    KHAC("khac");

    private final String giaTri;

    LoaiBds(String giaTri) {
        this.giaTri = giaTri;
    }

    public String getGiaTri() {
        return giaTri;
    }

    public static Optional<LoaiBds> tuGiaTri(String giaTri) {
        for (LoaiBds loai : values()) {
            if (loai.giaTri.equals(giaTri)) {
                return Optional.of(loai);
            }
        }
        return Optional.empty();
    }
}

