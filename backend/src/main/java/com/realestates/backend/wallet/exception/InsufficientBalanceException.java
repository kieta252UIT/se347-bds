package com.realestates.backend.wallet.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public static final String MA_LOI = "SO_DU_KHONG_DU";

    private final BigDecimal soDuHienTai;
    private final BigDecimal giaGoi;

    public InsufficientBalanceException(BigDecimal soDuHienTai, BigDecimal giaGoi) {
        super(String.format("Số dư ví không đủ để thanh toán (hiện có: %s đ, giá gói: %s đ)", soDuHienTai, giaGoi));
        this.soDuHienTai = soDuHienTai;
        this.giaGoi = giaGoi;
    }

    public String getMaLoi() {
        return MA_LOI;
    }

    public BigDecimal getSoDuHienTai() {
        return soDuHienTai;
    }

    public BigDecimal getGiaGoi() {
        return giaGoi;
    }
}
