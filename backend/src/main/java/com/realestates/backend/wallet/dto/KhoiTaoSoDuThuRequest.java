package com.realestates.backend.wallet.dto;

import java.math.BigDecimal;

public record KhoiTaoSoDuThuRequest(
        BigDecimal soTien,
        String ghiChu
) {}
