package com.realestates.backend.search.domain;

import java.util.Optional;

/** TK-05: chỉ mac_dinh (độ khớp/ngày đăng/id) và moi_nhat (ngày đăng/id); không sort giá/diện tích ở Sprint 1. */
public enum SapXepTin {
    MAC_DINH("mac_dinh"),
    MOI_NHAT("moi_nhat");

    private final String giaTri;

    SapXepTin(String giaTri) {
        this.giaTri = giaTri;
    }

    public String getGiaTri() {
        return giaTri;
    }

    public static Optional<SapXepTin> tuGiaTri(String giaTri) {
        for (SapXepTin sapXep : values()) {
            if (sapXep.giaTri.equals(giaTri)) {
                return Optional.of(sapXep);
            }
        }
        return Optional.empty();
    }
}
