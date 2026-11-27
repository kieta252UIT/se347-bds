package com.realestates.backend.recommendation.dto;

import java.math.BigDecimal;

public record GoiYQueryRequest(
        BigDecimal viDo,
        BigDecimal kinhDo,
        String loaiBds,
        String loaiGiaoDich,
        BigDecimal giaMin,
        BigDecimal giaMax,
        String khuVuc,
        Integer limit
) {
    public int getLimitOrFallback() {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 50);
    }
}
