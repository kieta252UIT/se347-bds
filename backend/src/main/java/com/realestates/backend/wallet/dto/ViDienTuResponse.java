package com.realestates.backend.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ViDienTuResponse(
        UUID viId,
        UUID taiKhoanId,
        BigDecimal soDuVnd,
        Instant capNhatLuc,
        List<GiaoDichViItemDto> giaoDichGanDay
) {
    public record GiaoDichViItemDto(
            UUID id,
            String loai,
            String trangThai,
            String chieuBienDong,
            BigDecimal soTien,
            BigDecimal soDuSau,
            Instant thoiDiemGhiNhan,
            String ghiChu
    ) {
    }
}
