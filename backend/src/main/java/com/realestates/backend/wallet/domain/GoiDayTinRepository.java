package com.realestates.backend.wallet.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GoiDayTinRepository {
    Optional<GoiDayTin> findGoiChuaKetThuc(UUID tinId);
    Optional<GoiDayTin> findGoiChuaKetThucForUpdate(UUID tinId);
    Optional<GoiDayTin> findById(UUID id);
    GoiDayTin save(GoiDayTin goi);

    default void ghiSuKienGoi(
            UUID goiId,
            UUID nguoiThucHienId,
            String loai,
            Duration thoiLuongTruoc,
            Duration thoiLuongSau,
            Instant thoiDiem
    ) {
        // Mặc định no-op nếu repository chưa cài đặt
    }

    default void ghiNhatKyKiemToanGoi(
            UUID nguoiThucHienId,
            UUID tinId,
            String hanhDong,
            String duLieuTruocSauJson,
            Instant thoiDiem
    ) {
        // Mặc định no-op nếu repository chưa cài đặt
    }
}
