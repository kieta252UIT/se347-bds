package com.realestates.backend.wallet.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GoiDayTin(
        UUID id,
        UUID tinId,
        String trangThai,
        Duration thoiLuongConLai,
        Instant hetHanLuc
) {
    public GoiDayTin {
        Objects.requireNonNull(id, "id không được null");
        Objects.requireNonNull(tinId, "tinId không được null");
        Objects.requireNonNull(trangThai, "trangThai không được null");
        Objects.requireNonNull(thoiLuongConLai, "thoiLuongConLai không được null");
    }

    public static GoiDayTin taoMoiDangHoatDong(UUID tinId, Duration thoiLuong, Instant now) {
        Objects.requireNonNull(now, "now không được null");
        Objects.requireNonNull(thoiLuong, "thoiLuong không được null");
        return new GoiDayTin(
                UUID.randomUUID(),
                tinId,
                "dang_hoat_dong",
                Duration.ZERO,
                now.plus(thoiLuong)
        );
    }

    public GoiDayTin congDonThoiLuong(Duration thoiLuongMoi, Instant now) {
        Objects.requireNonNull(thoiLuongMoi, "thoiLuongMoi không được null");
        if ("dang_hoat_dong".equals(this.trangThai)) {
            Instant baseTime = (this.hetHanLuc != null && this.hetHanLuc.isAfter(now))
                    ? this.hetHanLuc
                    : now;
            return new GoiDayTin(
                    this.id,
                    this.tinId,
                    "dang_hoat_dong",
                    Duration.ZERO,
                    baseTime.plus(thoiLuongMoi)
            );
        } else if ("tam_dung_cho_duyet".equals(this.trangThai)) {
            return new GoiDayTin(
                    this.id,
                    this.tinId,
                    "tam_dung_cho_duyet",
                    this.thoiLuongConLai.plus(thoiLuongMoi),
                    this.hetHanLuc
            );
        } else {
            // Đã hết hạn hoặc kết thúc do gỡ -> tạo gói mới
            return taoMoiDangHoatDong(this.tinId, thoiLuongMoi, now);
        }
    }

    public GoiDayTin chuyenHetHan() {
        return new GoiDayTin(
                this.id,
                this.tinId,
                "het_han",
                Duration.ZERO,
                this.hetHanLuc
        );
    }

    public boolean laHetHanThucTe(Instant now) {
        if (!"dang_hoat_dong".equals(this.trangThai)) {
            return false;
        }
        return hetHanLuc != null && !hetHanLuc.isAfter(now);
    }

    public boolean laDangHieuLuc(Instant now) {
        if ("tam_dung_cho_duyet".equals(this.trangThai)) {
            return true;
        }
        if ("dang_hoat_dong".equals(this.trangThai)) {
            return hetHanLuc != null && hetHanLuc.isAfter(now);
        }
        return false;
    }
}
