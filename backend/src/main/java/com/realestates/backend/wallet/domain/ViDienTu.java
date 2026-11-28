package com.realestates.backend.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ViDienTu(
        UUID id,
        UUID taiKhoanId,
        BigDecimal soDuVnd,
        Instant taoLuc,
        Instant capNhatLuc
) {
    public ViDienTu {
        Objects.requireNonNull(id, "id không được null");
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(soDuVnd, "soDuVnd không được null");
        if (soDuVnd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Số dư ví không được âm: " + soDuVnd);
        }
        if (soDuVnd.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Số dư ví phải là số nguyên VND: " + soDuVnd);
        }
    }

    public static ViDienTu khoiTao(UUID taiKhoanId) {
        Instant now = Instant.now();
        return new ViDienTu(UUID.randomUUID(), taiKhoanId, BigDecimal.ZERO, now, now);
    }

    public static ViDienTu khoiTaoVoiSoDu(UUID taiKhoanId, BigDecimal soDuBanDau) {
        Instant now = Instant.now();
        return new ViDienTu(UUID.randomUUID(), taiKhoanId, soDuBanDau, now, now);
    }

    public ViDienTu truTien(BigDecimal soTien, Instant thoiDiem) {
        Objects.requireNonNull(soTien, "soTien không được null");
        if (soTien.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền trừ phải lớn hơn 0");
        }
        if (soTien.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Số tiền trừ phải là số nguyên VND");
        }
        if (this.soDuVnd.compareTo(soTien) < 0) {
            throw new IllegalStateException("Số dư không đủ để trừ tiền");
        }
        return new ViDienTu(
                this.id,
                this.taiKhoanId,
                this.soDuVnd.subtract(soTien),
                this.taoLuc,
                thoiDiem != null ? thoiDiem : Instant.now()
        );
    }

    public ViDienTu congTien(BigDecimal soTien, Instant thoiDiem) {
        Objects.requireNonNull(soTien, "soTien không được null");
        if (soTien.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền cộng phải lớn hơn 0");
        }
        if (soTien.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Số tiền cộng phải là số nguyên VND");
        }
        return new ViDienTu(
                this.id,
                this.taiKhoanId,
                this.soDuVnd.add(soTien),
                this.taoLuc,
                thoiDiem != null ? thoiDiem : Instant.now()
        );
    }
}
