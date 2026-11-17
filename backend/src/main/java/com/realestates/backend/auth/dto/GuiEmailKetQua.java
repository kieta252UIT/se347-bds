package com.realestates.backend.auth.dto;


public record GuiEmailKetQua(
        String trangThai,
        String thongDiep
) {
    public static final String TRANG_THAI_DA_TIEP_NHAN = "da_tiep_nhan";
    public static final String THONG_DIEP_DANG_KY_THANH_CONG =
            "Đã tạo tài khoản và tiếp nhận gửi email xác minh. Hãy kiểm tra hộp thư; xác minh xong rồi đăng nhập.";
    public static final String THONG_DIEP_GUI_LAI_XAC_MINH_CHUNG =
            "Nếu tài khoản cần xác minh, hướng dẫn sẽ được gửi.";
    public static final String THONG_DIEP_DAT_LAI_MAT_KHAU_CHUNG =
            "Nếu email đã đăng ký, hướng dẫn khôi phục sẽ được gửi.";

    public static GuiEmailKetQua daTiepNhanDangKy() {
        return new GuiEmailKetQua(TRANG_THAI_DA_TIEP_NHAN, THONG_DIEP_DANG_KY_THANG_CONG());
    }

    public static GuiEmailKetQua daTiepNhanGuiLaiXacMinh() {
        return new GuiEmailKetQua(TRANG_THAI_DA_TIEP_NHAN, THONG_DIEP_GUI_LAI_XAC_MINH_CHUNG);
    }

    public static GuiEmailKetQua daTiepNhanDatLaiMatKhau() {
        return new GuiEmailKetQua(TRANG_THAI_DA_TIEP_NHAN, THONG_DIEP_DAT_LAI_MAT_KHAU_CHUNG);
    }

    private static String THONG_DIEP_DANG_KY_THANG_CONG() {
        return THONG_DIEP_DANG_KY_THANH_CONG;
    }
}
