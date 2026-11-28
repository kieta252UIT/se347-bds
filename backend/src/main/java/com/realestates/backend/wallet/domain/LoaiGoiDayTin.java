package com.realestates.backend.wallet.domain;

import java.math.BigDecimal;
import java.time.Duration;

public enum LoaiGoiDayTin {
    GOI_7_NGAY("7_ngay", "Gói đẩy tin 7 ngày", new BigDecimal("49000"), Duration.ofDays(7), 7),
    GOI_30_NGAY("30_ngay", "Gói đẩy tin 30 ngày", new BigDecimal("179000"), Duration.ofDays(30), 30);

    private final String maChuan;
    private final String tenGoi;
    private final BigDecimal giaVnd;
    private final Duration thoiLuong;
    private final int soNgay;

    LoaiGoiDayTin(String maChuan, String tenGoi, BigDecimal giaVnd, Duration thoiLuong, int soNgay) {
        this.maChuan = maChuan;
        this.tenGoi = tenGoi;
        this.giaVnd = giaVnd;
        this.thoiLuong = thoiLuong;
        this.soNgay = soNgay;
    }

    public String getMaChuan() {
        return maChuan;
    }

    public String getTenGoi() {
        return tenGoi;
    }

    public BigDecimal getGiaVnd() {
        return giaVnd;
    }

    public Duration getThoiLuong() {
        return thoiLuong;
    }

    public int getSoNgay() {
        return soNgay;
    }

    public static LoaiGoiDayTin tuMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw new IllegalArgumentException("Mã gói không được để trống");
        }
        String trimmed = ma.trim();
        for (LoaiGoiDayTin goi : values()) {
            if (goi.name().equalsIgnoreCase(trimmed) || goi.maChuan.equalsIgnoreCase(trimmed)) {
                return goi;
            }
        }
        throw new IllegalArgumentException("Mã gói không hợp lệ: " + ma);
    }
}
