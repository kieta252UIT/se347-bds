package com.realestates.backend.detail.dto;

import com.realestates.backend.search.dto.AnhHienThi;
import com.realestates.backend.search.dto.GiaQuyDoiHienThi;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Schema PhienBanNoiDung; số thập phân trả dạng chuỗi. */
public record PhienBanNoiDung(
        UUID id,
        int soPhienBan,
        String tieuDe,
        String moTa,
        String loaiGiaoDich,
        String loaiBds,
        String giaGoc,
        String donViGia,
        String dienTichM2,
        String diaChi,
        String khuVuc,
        String viDo,
        String kinhDo,
        List<AnhHienThi> anh,
        Instant taoLuc,
        GiaQuyDoiHienThi giaQuyDoiHienThi
) {
}
