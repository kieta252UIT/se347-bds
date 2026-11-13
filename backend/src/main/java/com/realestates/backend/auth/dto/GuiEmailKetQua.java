package com.realestates.backend.auth.dto;

/**
 * Kết quả trả về cho POST /api/auth/register (HTTP 202 Accepted)
 * theo schema GuiEmailKetQua trong openapi-sprint-1.yaml và api-specification-sprint-1.md §8.4.
 */
public record GuiEmailKetQua(
        String trangThai,
        String thongDiep
) {
    public static final String TRANG_THAI_DA_TIEP_NHAN = "da_tiep_nhan";
    public static final String THONG_DIEP_DANG_KY_THANH_CONG =
            "Đã tạo tài khoản và tiếp nhận gửi email xác minh. Hãy kiểm tra hộp thư; xác minh xong rồi đăng nhập.";

    public static GuiEmailKetQua daTiepNhanDangKy() {
        return new GuiEmailKetQua(TRANG_THAI_DA_TIEP_NHAN, THONG_DIEP_DANG_KY_THANG_CONG());
    }

    private static String THONG_DIEP_DANG_KY_THANG_CONG() {
        return THONG_DIEP_DANG_KY_THANH_CONG;
    }
}
