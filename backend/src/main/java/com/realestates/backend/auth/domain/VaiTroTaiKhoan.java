package com.realestates.backend.auth.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vai trò tài khoản theo đặc tả cơ sở dữ liệu (tai_khoan.vai_tro) và SRS FR-15 / UC-25.
 * Đăng ký công khai chỉ được phép tạo vai trò NGUOI_DUNG ("nguoi_dung").
 */
public enum VaiTroTaiKhoan {
    NGUOI_DUNG("nguoi_dung"),
    KIEM_DUYET_VIEN("kiem_duyet_vien"),
    QUAN_TRI_VIEN("quan_tri_vien");

    private final String giaTri;

    VaiTroTaiKhoan(String giaTri) {
        this.giaTri = giaTri;
    }

    @JsonValue
    public String getGiaTri() {
        return giaTri;
    }

    public boolean duocPhepDangKyCongKhai() {
        return this == NGUOI_DUNG;
    }

    public static VaiTroTaiKhoan vaiTroDangKyCongKhai() {
        return NGUOI_DUNG;
    }
}
