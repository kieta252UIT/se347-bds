package com.realestates.backend.auth.domain;

/**
 * Các thao tác nghiệp vụ trong Sprint 1 theo Bảng 1 và Bảng 2 của
 * ma-tran-quyen-va-trang-thai-sprint-1.md.
 */
public enum ThaoTacNghiepVu {
    // Nhóm công khai / khách (Khách, Người dùng, KDV, Admin đều xem được)
    TIM_LOC_SAP_XEP_TIN_CONG_KHAI(true, false),
    XEM_CHI_TIET_TIN_DA_DUYET(true, false),
    DANG_KY_XAC_MINH_EMAIL_DANG_NHAP_DAT_LAI_MAT_KHAU(true, false),

    // Nhóm yêu cầu phiên đã xác thực hợp lệ (Người dùng, KDV, Admin)
    LAY_HO_SO_HIEN_TAI(false, false),
    DANG_XUAT(false, false),

    // Nhóm chỉ dành cho vai trò Người dùng (nguoi_dung)
    THEM_GO_XEM_DANH_SACH_YEU_THICH(false, false),
    TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN(false, false),
    CHINH_SUA_TIN_CUA_MINH(false, true),
    XOA_MEM_TIN_CUA_MINH(false, true),
    GUI_BAO_CAO_VI_PHAM(false, false),
    KICH_HOAT_GOI_DAY_TIN(false, true),

    // Nhóm chỉ dành cho vai trò Kiểm duyệt viên (kiem_duyet_vien)
    XEM_HANG_CHO_VA_DUYET_TIN(false, false),
    XEM_VA_XU_LY_BAO_CAO_VI_PHAM(false, false),

    // Nhóm chỉ dành cho vai trò Quản trị viên (quan_tri_vien)
    TAO_CAP_NHAT_KHOA_MO_KHOA_TAI_KHOAN(false, false);

    private final boolean choPhepKhach;
    private final boolean yeuCauChuSoHuuTaiNguyen;

    ThaoTacNghiepVu(boolean choPhepKhach, boolean yeuCauChuSoHuuTaiNguyen) {
        this.choPhepKhach = choPhepKhach;
        this.yeuCauChuSoHuuTaiNguyen = yeuCauChuSoHuuTaiNguyen;
    }

    public boolean isChoPhepKhach() {
        return choPhepKhach;
    }

    public boolean isYeuCauChuSoHuuTaiNguyen() {
        return yeuCauChuSoHuuTaiNguyen;
    }
}
