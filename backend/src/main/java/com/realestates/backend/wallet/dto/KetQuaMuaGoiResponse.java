package com.realestates.backend.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KetQuaMuaGoiResponse(
        boolean thanhCong,
        String thongDiep,
        UUID maGiaoDich,
        BigDecimal soDuConLaiVnd,
        ThongTinGoiKetQua goi
) {
    public record ThongTinGoiKetQua(
            UUID id,
            String trangThai,
            Instant hetHanLuc
    ) {
    }

    public static KetQuaMuaGoiResponse thanhCong(
            String thongDiep,
            UUID maGiaoDich,
            BigDecimal soDuConLaiVnd,
            UUID goiId,
            String trangThai,
            Instant hetHanLuc
    ) {
        return new KetQuaMuaGoiResponse(
                true,
                thongDiep,
                maGiaoDich,
                soDuConLaiVnd,
                new ThongTinGoiKetQua(goiId, trangThai, hetHanLuc)
        );
    }
}
