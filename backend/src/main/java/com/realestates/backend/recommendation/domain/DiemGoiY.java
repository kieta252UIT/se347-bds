package com.realestates.backend.recommendation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Thực thể Điểm gợi ý nền (Precomputed Score) cho cặp (taiKhoanId, tinId).
 */
public record DiemGoiY(
        UUID taiKhoanId,
        UUID tinId,
        BigDecimal diem,
        Instant capNhatLuc
) {
    public DiemGoiY {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(tinId, "tinId không được null");
        Objects.requireNonNull(diem, "diem không được null");
        Objects.requireNonNull(capNhatLuc, "capNhatLuc không được null");
    }
}
