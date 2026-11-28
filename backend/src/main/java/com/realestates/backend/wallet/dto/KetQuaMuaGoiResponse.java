package com.realestates.backend.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KetQuaMuaGoiResponse(
        boolean thanhCong,
        String thongDiep,
        UUID maGiaoDich,
        UUID lanMuaId,
        BigDecimal soDuConLaiVnd,
        ThongTinGoiKetQua goi
) {
    public KetQuaMuaGoiResponse(
            boolean thanhCong,
            String thongDiep,
            UUID maGiaoDich,
            BigDecimal soDuConLaiVnd,
            ThongTinGoiKetQua goi
    ) {
        this(thanhCong, thongDiep, maGiaoDich, null, soDuConLaiVnd, goi);
    }

    public record ThongTinGoiKetQua(
            UUID id,
            String trangThai,
            Instant hetHanLuc,
            Long thoiLuongConLaiGiay
    ) {
        public ThongTinGoiKetQua(UUID id, String trangThai, Instant hetHanLuc) {
            this(id, trangThai, hetHanLuc, null);
        }
    }

    public static KetQuaMuaGoiResponse thanhCong(
            String thongDiep,
            UUID maGiaoDich,
            BigDecimal soDuConLaiVnd,
            UUID goiId,
            String trangThai,
            Instant hetHanLuc
    ) {
        return thanhCong(thongDiep, maGiaoDich, null, soDuConLaiVnd, goiId, trangThai, hetHanLuc, null);
    }

    public static KetQuaMuaGoiResponse thanhCong(
            String thongDiep,
            UUID maGiaoDich,
            UUID lanMuaId,
            BigDecimal soDuConLaiVnd,
            UUID goiId,
            String trangThai,
            Instant hetHanLuc,
            Long thoiLuongConLaiGiay
    ) {
        return new KetQuaMuaGoiResponse(
                true,
                thongDiep,
                maGiaoDich,
                lanMuaId,
                soDuConLaiVnd,
                new ThongTinGoiKetQua(goiId, trangThai, hetHanLuc, thoiLuongConLaiGiay)
        );
    }
}
