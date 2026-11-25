package com.realestates.backend.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Schema TinTomTat; số thập phân trả dạng chuỗi. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TinTomTat(
        UUID id,
        UUID phienBanId,
        String tieuDe,
        String loaiGiaoDich,
        String loaiBds,
        String giaGoc,
        String donViGia,
        String dienTichM2,
        String khuVuc,
        String viDo,
        String kinhDo,
        Instant ngayDang,
        AnhHienThi anhDaiDien,
        GiaQuyDoiHienThi giaQuyDoiHienThi
) {
}
