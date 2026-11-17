package com.realestates.backend.listing.dto;

import java.util.UUID;

public record TinGhiKetQua(
        UUID tinId,
        UUID phienBanHienTaiId,
        String trangThai,
        long phienBanCapNhat
) {
}
