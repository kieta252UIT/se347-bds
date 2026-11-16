package com.realestates.backend.auth.dto;

/**
 * Kết quả trả về cho POST /api/auth/email/verify (HTTP 200 OK)
 * theo schema XacMinhKetQua trong openapi-sprint-1.yaml và api-specification-sprint-1.md §8.2, §8.5.
 */
public record XacMinhKetQua(
        String trangThai,
        String thongDiep,
        boolean canDangNhapLai,
        String csrfToken
) {
    public static final String TRANG_THAI_DA_XAC_MINH = "da_xac_minh";
    public static final String THONG_DIEP_DA_XAC_MINH =
            "Email đã được xác minh. Phiên hiện tại trên browser đã kết thúc nếu có. Hãy đăng nhập để tiếp tục.";

    public static XacMinhKetQua daXacMinh(String newAnonymousCsrfToken) {
        return new XacMinhKetQua(
                TRANG_THAI_DA_XAC_MINH,
                THONG_DIEP_DA_XAC_MINH,
                true,
                newAnonymousCsrfToken
        );
    }
}
