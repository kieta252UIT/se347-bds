package com.realestates.backend.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TinGoiYItemDto(
        UUID tinId,
        String tieuDe,
        String moTa,
        String loaiGiaoDich,
        String loaiBds,
        BigDecimal giaGoc,
        String donViGia,
        BigDecimal dienTichM2,
        String diaChi,
        String khuVuc,
        BigDecimal viDo,
        BigDecimal kinhDo,
        BigDecimal khoangCachKm,
        BigDecimal diemGoiY,
        Instant ngayDang,
        UUID anhDaiDienId,
        boolean duocTaiTro
) {
}
