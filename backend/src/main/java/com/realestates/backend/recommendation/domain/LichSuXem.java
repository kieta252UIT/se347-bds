package com.realestates.backend.recommendation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Thực thể ghi nhận lịch sử tương tác (xem tin chi tiết) của người dùng.
 */
public record LichSuXem(
        UUID id,
        UUID taiKhoanId,
        UUID tinId,
        Instant thoiDiemXem
) {
    public LichSuXem {
        Objects.requireNonNull(id, "id không được null");
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(tinId, "tinId không được null");
        Objects.requireNonNull(thoiDiemXem, "thoiDiemXem không được null");
    }

    public static LichSuXem taoMoi(UUID taiKhoanId, UUID tinId, Instant now) {
        return new LichSuXem(UUID.randomUUID(), taiKhoanId, tinId, now);
    }
}
