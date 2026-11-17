package com.realestates.backend.auth.dto;


public record DatLaiKetQua(
        String trangThai,
        String thongDiep,
        boolean canDangNhapLai
) {
    public static final String TRANG_THAI_DA_DAT_LAI = "da_dat_lai_mat_khau";
    public static final String THONG_DIEP_THANH_CONG =
            "Đã đặt lại mật khẩu và thu hồi các phiên cũ. Hãy đăng nhập lại.";

    public static DatLaiKetQua thanhCong() {
        return new DatLaiKetQua(TRANG_THAI_DA_DAT_LAI, THONG_DIEP_THANH_CONG, true);
    }
}
