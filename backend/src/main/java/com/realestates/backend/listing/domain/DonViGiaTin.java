package com.realestates.backend.listing.domain;

import java.util.Optional;

public enum DonViGiaTin {
    VND_TONG("vnd_tong", LoaiGiaoDich.BAN),
    VND_M2("vnd_m2", LoaiGiaoDich.BAN),
    VND_NGAY("vnd_ngay", LoaiGiaoDich.CHO_THUE),
    VND_THANG("vnd_thang", LoaiGiaoDich.CHO_THUE),
    VND_NAM("vnd_nam", LoaiGiaoDich.CHO_THUE),
    VND_M2_NGAY("vnd_m2_ngay", LoaiGiaoDich.CHO_THUE),
    VND_M2_THANG("vnd_m2_thang", LoaiGiaoDich.CHO_THUE);

    private final String giaTri;
    private final LoaiGiaoDich loaiGiaoDich;

    DonViGiaTin(String giaTri, LoaiGiaoDich loaiGiaoDich) {
        this.giaTri = giaTri;
        this.loaiGiaoDich = loaiGiaoDich;
    }

    public String getGiaTri() {
        return giaTri;
    }

    public boolean phuHopVoi(LoaiGiaoDich loai) {
        return this.loaiGiaoDich == loai;
    }

    public static Optional<DonViGiaTin> tuGiaTri(String giaTri) {
        for (DonViGiaTin donVi : values()) {
            if (donVi.giaTri.equals(giaTri)) {
                return Optional.of(donVi);
            }
        }
        return Optional.empty();
    }
}

