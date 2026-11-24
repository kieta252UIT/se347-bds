package com.realestates.backend.recommendation.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object biểu diễn tọa độ địa lý (Vĩ độ - Kinh độ)
 * và thuật toán tính khoảng cách địa lý theo công thức Haversine (bán kính Trái Đất R = 6371 km).
 */
public record ViTriToaDo(BigDecimal viDo, BigDecimal kinhDo) {

    public static final ViTriToaDo TOA_DO_MAC_DINH_TPHCM = new ViTriToaDo(
            new BigDecimal("10.7769"),
            new BigDecimal("106.7009")
    );

    private static final double BAN_KINH_TRAI_DAT_KM = 6371.0;

    public ViTriToaDo {
        Objects.requireNonNull(viDo, "viDo không được null");
        Objects.requireNonNull(kinhDo, "kinhDo không được null");
    }

    /**
     * Tính khoảng cách đường chim bay (km) giữa 2 tọa độ theo công thức Haversine.
     */
    public BigDecimal tinhKhoangCachKm(ViTriToaDo toaDoKhac) {
        if (toaDoKhac == null) {
            return BigDecimal.ZERO;
        }

        double lat1Rad = Math.toRadians(this.viDo.doubleValue());
        double lon1Rad = Math.toRadians(this.kinhDo.doubleValue());
        double lat2Rad = Math.toRadians(toaDoKhac.viDo.doubleValue());
        double lon2Rad = Math.toRadians(toaDoKhac.kinhDo.doubleValue());

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = BAN_KINH_TRAI_DAT_KM * c;

        return BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
    }
}
