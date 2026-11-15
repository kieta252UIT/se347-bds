package com.realestates.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * DTO phản hồi cho POST /api/auth/logout (operationId: dangXuatPhienHienTai) theo schema DangXuatKetQua
 * trong openapi-sprint-1.yaml và api-specification-sprint-1.md §8.8.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DangXuatKetQua(
        String trangThai,
        String csrfToken
) {
    public static final String TRANG_THAI_DA_DANG_XUAT = "da_dang_xuat";

    public DangXuatKetQua {
        if (trangThai == null || trangThai.isBlank()) {
            throw new IllegalArgumentException("trangThai không được rỗng");
        }
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrfToken không được rỗng");
        }
        trangThai = Objects.requireNonNull(trangThai).trim();
        csrfToken = Objects.requireNonNull(csrfToken).trim();
    }

    public static DangXuatKetQua thanhCong(String anonymousCsrfToken) {
        return new DangXuatKetQua(TRANG_THAI_DA_DANG_XUAT, anonymousCsrfToken);
    }
}
