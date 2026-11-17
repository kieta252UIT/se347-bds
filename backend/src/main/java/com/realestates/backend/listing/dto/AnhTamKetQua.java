package com.realestates.backend.listing.dto;

import java.util.UUID;

public record AnhTamKetQua(
        UUID id,
        String trangThai,
        String mimeType,
        long kichThuocByte,
        String duongDanDoc
) {
}

