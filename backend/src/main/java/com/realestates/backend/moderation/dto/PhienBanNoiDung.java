package com.realestates.backend.moderation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Schema PhienBanNoiDung của OpenAPI Sprint 1; số thập phân trả dạng chuỗi. */
@JsonInclude(JsonInclude.Include.ALWAYS)
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
