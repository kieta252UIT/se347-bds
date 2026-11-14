package com.realestates.backend.auth.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Trạng thái tài khoản theo đặc tả cơ sở dữ liệu (tai_khoan.trang_thai).
 */
public enum TrangThaiTaiKhoan {
    HOAT_DONG("hoat_dong"),
    BI_KHOA("bi_khoa");

    private final String giaTri;

    TrangThaiTaiKhoan(String giaTri) {
        this.giaTri = giaTri;
    }

    @JsonValue
    public String getGiaTri() {
        return giaTri;
    }
}
