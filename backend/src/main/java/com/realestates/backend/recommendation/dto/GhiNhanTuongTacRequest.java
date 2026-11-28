package com.realestates.backend.recommendation.dto;

import java.util.Objects;
import java.util.UUID;

public record GhiNhanTuongTacRequest(
        UUID tinId,
        String loaiTuongTac
) {
    public GhiNhanTuongTacRequest {
        Objects.requireNonNull(tinId, "tinId không được null");
        if (loaiTuongTac == null || loaiTuongTac.isBlank()) {
            loaiTuongTac = "xem";
        }
    }
}
