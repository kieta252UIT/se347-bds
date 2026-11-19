package com.realestates.backend.listing.domain;

import java.util.UUID;

public record AnhTinTruyCap(
        UUID anhId,
        UUID chuAnhId,
        String trangThaiAnh,
        String khoaTepGoc,
        String khoaAnhHienThi,
        String mimeType,
        UUID phienBanTinId,
        String trangThaiTin,
        UUID phienBanHienTaiId,
        UUID phienBanDaDuyetId,
        boolean chuTinHoatDong
) {

    public static final String ANH_TAM = "tam";
    public static final String ANH_DA_GAN = "da_gan";
    public static final String TIN_CHO_KIEM_DUYET = "cho_kiem_duyet";
    public static final String TIN_DA_DUYET = "da_duyet";

    public String khoaDoiTuongCanDoc() {
        return khoaAnhHienThi != null ? khoaAnhHienThi : khoaTepGoc;
    }
}

