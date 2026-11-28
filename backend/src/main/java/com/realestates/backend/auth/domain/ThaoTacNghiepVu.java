package com.realestates.backend.auth.domain;

/**
 * Các thao tác nghiệp vụ trong Sprint 1 và Sprint 2 theo đặc tả
 * ma-tran-quyen-va-trang-thai-sprint-1.md và docs/ma-tran-quyen-replay-va-thu-tu-khoa-sprint-2.md.
 */
public enum ThaoTacNghiepVu {
    // Nhóm công khai / khách (Khách, Người dùng, KDV, Admin đều xem được)
    TIM_LOC_SAP_XEP_TIN_CONG_KHAI(true, false),
    XEM_CHI_TIET_TIN_DA_DUYET(true, false),
    XEM_GOI_Y_BAT_DONG_SAN(true, false),
    DANG_KY_XAC_MINH_EMAIL_DANG_NHAP_DAT_LAI_MAT_KHAU(true, false),

    // Nhóm yêu cầu phiên đã xác thực hợp lệ (Người dùng, KDV, Admin)
    LAY_HO_SO_HIEN_TAI(false, false),
    DANG_XUAT(false, false),

    // Nhóm chỉ dành cho vai trò Người dùng (nguoi_dung)
    THEM_GO_XEM_DANH_SACH_YEU_THICH(false, false),
    GHI_NHAN_LUOT_XEM_TIN(false, false),
    TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN(false, false),
    CHINH_SUA_TIN_CUA_MINH(false, true),
    XOA_MEM_TIN_CUA_MINH(false, true),
    XEM_TIN_CUA_TOI_CHI_DOC(false, true),
    TAI_ANH_BANG_CHUNG_BAO_CAO(false, false),
    GUI_BAO_CAO_VI_PHAM(false, false),
    XEM_THONG_BAO_CUA_TOI(false, false),
    XEM_VI_VA_SO_GIAO_DICH(false, false),
    XEM_BAO_GIA_GOI_DAY_TIN(false, true),
    KICH_HOAT_GOI_DAY_TIN(false, true),

    // Nhóm chỉ dành cho vai trò Kiểm duyệt viên (kiem_duyet_vien)
    XEM_HANG_CHO_VA_DUYET_TIN(false, false),
    XEM_VA_XU_LY_BAO_CAO_VI_PHAM(false, false),
    TAO_YEU_CAU_KHOA_TAI_KHOAN(false, false),

    // Nhóm chỉ dành cho vai trò Quản trị viên (quan_tri_vien)
    TAO_CAP_NHAT_KHOA_MO_KHOA_TAI_KHOAN(false, false),
    CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI(false, false),
    XEM_VA_QUYET_DINH_YEU_CAU_KHOA(false, false),
    MO_KHOA_TAI_KHOAN_CO_CAN_CU(false, false);

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
