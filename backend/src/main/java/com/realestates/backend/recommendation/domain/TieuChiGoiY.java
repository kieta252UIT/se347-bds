package com.realestates.backend.recommendation.domain;

import java.math.BigDecimal;

/**
 * Value Object chứa các tiêu chí lọc nhu cầu mà người dùng hoặc khách truyền lên.
 */
public record TieuChiGoiY(
        String loaiBds,
        String loaiGiaoDich,
        BigDecimal giaMinVnd,
        BigDecimal giaMaxVnd,
        String khuVuc
) {
    public boolean coLocLoaiBds() {
        return loaiBds != null && !loaiBds.isBlank();
    }

    public boolean coLocLoaiGiaoDich() {
        return loaiGiaoDich != null && !loaiGiaoDich.isBlank();
    }

    public boolean coLocGia() {
        return (giaMinVnd != null && giaMinVnd.compareTo(BigDecimal.ZERO) > 0)
                || (giaMaxVnd != null && giaMaxVnd.compareTo(BigDecimal.ZERO) > 0);
    }

    public boolean coLocKhuVuc() {
        return khuVuc != null && !khuVuc.isBlank();
    }
}
