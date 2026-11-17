package com.realestates.backend.listing.domain;

import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;

public final class QuyenDocAnh {

    private QuyenDocAnh() {
    }

    public static boolean duocPhepDoc(AnhTinTruyCap anh, PrincipalPhienHienTai nguoiDoc) {
        if (anh == null) {
            return false;
        }
        boolean anhTam = AnhTinTruyCap.ANH_TAM.equals(anh.trangThaiAnh());
        boolean anhDaGan = AnhTinTruyCap.ANH_DA_GAN.equals(anh.trangThaiAnh());
        if (!anhTam && !anhDaGan) {
            return false;
        }
        if (nguoiDoc != null
                && nguoiDoc.getVaiTro() == VaiTroTaiKhoan.NGUOI_DUNG
                && nguoiDoc.getTaiKhoanId().equals(anh.chuAnhId())) {
            return true;
        }
        if (!anhDaGan || anh.phienBanTinId() == null) {
            return false;
        }
        if (AnhTinTruyCap.TIN_DA_DUYET.equals(anh.trangThaiTin())
                && anh.chuTinHoatDong()
                && anh.phienBanTinId().equals(anh.phienBanDaDuyetId())) {
            return true;
        }
        return nguoiDoc != null
                && nguoiDoc.getVaiTro() == VaiTroTaiKhoan.KIEM_DUYET_VIEN
                && AnhTinTruyCap.TIN_CHO_KIEM_DUYET.equals(anh.trangThaiTin())
                && anh.phienBanTinId().equals(anh.phienBanHienTaiId());
    }
}

