package com.realestates.backend.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ThongTinMuaGoiResponse(
        UUID tinId,
        String trangThaiTin,
        boolean duDieuKienMua,
        boolean canXacNhanCongDon,
        BigDecimal soDuHienTaiVnd,
        GoiHienTaiDto goiHienTai,
        List<GoiHoTroDto> cacGoiHoTro
) {
    public ThongTinMuaGoiResponse(
            UUID tinId,
            BigDecimal soDuHienTaiVnd,
            GoiHienTaiDto goiHienTai,
            List<GoiHoTroDto> cacGoiHoTro
    ) {
        this(
                tinId,
                "da_duyet",
                true,
                goiHienTai != null && goiHienTai.coGoi(),
                soDuHienTaiVnd,
                goiHienTai,
                cacGoiHoTro
        );
    }

    public record GoiHienTaiDto(
            boolean coGoi,
            String trangThai,
            Instant hetHanLuc,
            Long thoiLuongConLaiGiay
    ) {
    }

    public record GoiHoTroDto(
            String maGoi,
            String tenGoi,
            BigDecimal giaVnd,
            int thoiLuongNgay
    ) {
    }
}
