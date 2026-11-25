package com.realestates.backend.moderation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** Schema TinQuanLy của OpenAPI Sprint 1 (đường KDV). */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TinQuanLy(
        UUID id,
        String trangThai,
        Instant ngayDang,
        long phienBanCapNhat,
        UUID phienBanHienTaiId,
        UUID phienBanDaDuyetId,
        PhienBanNoiDung noiDung,
        String ketQuaSoBo,
        String lyDoTuChoi,
        String lyDoNgungHienThi
) {
}
