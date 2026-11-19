package com.realestates.backend.listing.domain;

import java.math.BigDecimal;

public record NoiDungTinDauVao(
        String tieuDe,
        String moTa,
        LoaiGiaoDich loaiGiaoDich,
        LoaiBds loaiBds,
        BigDecimal giaGoc,
        DonViGiaTin donViGia,
        BigDecimal dienTichM2,
        String diaChi,
        String khuVuc,
        String viDo,
        String kinhDo
) {
}

