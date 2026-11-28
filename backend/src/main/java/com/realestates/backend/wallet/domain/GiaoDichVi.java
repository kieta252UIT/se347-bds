package com.realestates.backend.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GiaoDichVi(
        UUID id,
        UUID viId,
        String loai,
        String trangThai,
        String chieuBienDong,
        BigDecimal soTien,
        BigDecimal soDuTruoc,
        BigDecimal soDuSau,
        String maYDinh,
        String duLieuBam,
        String ketQuaJson,
        UUID giaoDichGocId,
        String maThuTienCong,
        Instant thoiDiemGhiNhan,
        String ghiChu
) {
    public GiaoDichVi(
            UUID id,
            UUID viId,
            String loai,
            String trangThai,
            String chieuBienDong,
            BigDecimal soTien,
            BigDecimal soDuTruoc,
            BigDecimal soDuSau,
            String maYDinh,
            UUID giaoDichGocId,
            String maThuTienCong,
            Instant thoiDiemGhiNhan,
            String ghiChu
    ) {
        this(
                id,
                viId,
                loai,
                trangThai,
                chieuBienDong,
                soTien,
                soDuTruoc,
                soDuSau,
                maYDinh,
                null,
                null,
                giaoDichGocId,
                maThuTienCong,
                thoiDiemGhiNhan,
                ghiChu
        );
    }

    public GiaoDichVi {
        Objects.requireNonNull(id, "id không được null");
        Objects.requireNonNull(viId, "viId không được null");
        Objects.requireNonNull(loai, "loai không được null");
        Objects.requireNonNull(trangThai, "trangThai không được null");
        Objects.requireNonNull(chieuBienDong, "chieuBienDong không được null");
        Objects.requireNonNull(soTien, "soTien không được null");
        Objects.requireNonNull(soDuTruoc, "soDuTruoc không được null");
        Objects.requireNonNull(soDuSau, "soDuSau không được null");
        Objects.requireNonNull(maYDinh, "maYDinh không được null");

        if (soTien.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền giao dịch phải lớn hơn 0");
        }
        if (soTien.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Số tiền giao dịch phải là số nguyên VND");
        }
        if (soDuTruoc.compareTo(BigDecimal.ZERO) < 0 || soDuSau.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Số dư trước và sau không được âm");
        }
    }

    public static GiaoDichVi taoButToanTruTien(
            UUID viId,
            String loai,
            BigDecimal soTien,
            BigDecimal soDuTruoc,
            BigDecimal soDuSau,
            String maYDinh,
            String ghiChu,
            Instant thoiDiem
    ) {
        return taoButToanTruTienCoSnapshot(
                UUID.randomUUID(),
                viId,
                loai,
                soTien,
                soDuTruoc,
                soDuSau,
                maYDinh,
                null,
                null,
                ghiChu,
                thoiDiem
        );
    }

    public static GiaoDichVi taoButToanTruTienCoSnapshot(
            UUID giaoDichId,
            UUID viId,
            String loai,
            BigDecimal soTien,
            BigDecimal soDuTruoc,
            BigDecimal soDuSau,
            String maYDinh,
            String duLieuBam,
            String ketQuaJson,
            String ghiChu,
            Instant thoiDiem
    ) {
        return new GiaoDichVi(
                giaoDichId != null ? giaoDichId : UUID.randomUUID(),
                viId,
                loai,
                "thanh_cong",
                "tru",
                soTien,
                soDuTruoc,
                soDuSau,
                maYDinh,
                duLieuBam,
                ketQuaJson,
                null,
                null,
                thoiDiem != null ? thoiDiem : Instant.now(),
                ghiChu
        );
    }

    public static GiaoDichVi taoButToanCongTien(
            UUID viId,
            String loai,
            BigDecimal soTien,
            BigDecimal soDuTruoc,
            BigDecimal soDuSau,
            String maYDinh,
            String ghiChu,
            Instant thoiDiem
    ) {
        return new GiaoDichVi(
                UUID.randomUUID(),
                viId,
                loai,
                "thanh_cong",
                "cong",
                soTien,
                soDuTruoc,
                soDuSau,
                maYDinh,
                null,
                null,
                null,
                null,
                thoiDiem != null ? thoiDiem : Instant.now(),
                ghiChu
        );
    }
}
