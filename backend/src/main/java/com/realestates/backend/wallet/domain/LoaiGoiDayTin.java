package com.realestates.backend.wallet.domain;

import java.math.BigDecimal;
import java.time.Duration;

public enum LoaiGoiDayTin {
    GOI_7_NGAY("Gói đẩy tin 7 ngày", new BigDecimal("49000"), Duration.ofDays(7), 7),
    GOI_30_NGAY("Gói đẩy tin 30 ngày", new BigDecimal("179000"), Duration.ofDays(30), 30);

    private final String tenGoi;
    private final BigDecimal giaVnd;
    private final Duration thoiLuong;
    private final int soNgay;

    LoaiGoiDayTin(String tenGoi, BigDecimal giaVnd, Duration thoiLuong, int soNgay) {
        this.tenGoi = tenGoi;
        this.giaVnd = giaVnd;
        this.thoiLuong = thoiLuong;
        this.soNgay = soNgay;
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
        if (ma == null) {
            throw new IllegalArgumentException("Mã gói không được để trống");
        }
        for (LoaiGoiDayTin goi : values()) {
            if (goi.name().equalsIgnoreCase(ma.trim())) {
                return goi;
            }
        }
        throw new IllegalArgumentException("Mã gói không hợp lệ: " + ma);
    }
}
