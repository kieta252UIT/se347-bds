package com.realestates.backend.auth.dto;

/**
 * Phản hồi thành công cho POST /api/auth/login (operationId: dangNhap) theo schema DangNhapKetQua
 * trong openapi-sprint-1.yaml và api-specification-sprint-1.md §8.6.
 */
public record DangNhapKetQua(
        TaiKhoanHienTai taiKhoan,
        String csrfToken
) {
}
