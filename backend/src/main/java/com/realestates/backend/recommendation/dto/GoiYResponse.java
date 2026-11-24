package com.realestates.backend.recommendation.dto;

import java.util.List;

public record GoiYResponse(
        List<TinGoiYItemDto> goiYTuNhien,
        List<TinGoiYItemDto> duocTaiTro,
        String nguCanh,
        boolean dungViTriMacDinh,
        int tongSoTuNhien,
        int tongSoTaiTro
) {
}
