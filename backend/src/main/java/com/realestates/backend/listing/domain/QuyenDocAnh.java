package com.realestates.backend.listing.domain;

import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import java.util.UUID;

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

    /**
     * AC-08.2 / Q37 (S2-02 Bảng 2): Kiểm tra quyền đọc ảnh tin của phiên bản đã duyệt gần nhất
     * ({@code phien_ban_da_duyet_id}) trong ngữ cảnh Kiểm duyệt viên đang thẩm định một báo cáo vi phạm (UC-14).
     *
     * <p>Ràng buộc server-side: {@code reportId -> tinIdCuaBaoCao == tinIdCuaAnh -> anh.phienBanTinId == tin.phienBanDaDuyetId}.
     * Cho phép KDV đọc ảnh của {@code phien_ban_da_duyet_id} ngay cả khi tin đang ở {@code cho_kiem_duyet}
     * (bản sửa chưa duyệt) hoặc {@code bi_tu_choi} (bản sửa bị từ chối). Khi bản sửa mới được duyệt làm
     * {@code phien_ban_da_duyet_id} đổi sang bản mới, ảnh của bản cũ lập tức bị từ chối; khi tin đã {@code da_xoa}
     * (AC-08.3) cũng từ chối đọc nội dung.
     */
    public static boolean duocPhepDocTrongNguCanhBaoCao(
            AnhTinTruyCap anh,
            UUID tinIdCuaBaoCao,
            UUID tinIdCuaAnh,
            PrincipalPhienHienTai nguoiDoc
    ) {
        if (anh == null || tinIdCuaBaoCao == null || tinIdCuaAnh == null || nguoiDoc == null) {
            return false;
        }
        if (nguoiDoc.getTrangThai() != TrangThaiTaiKhoan.HOAT_DONG
                || !nguoiDoc.isEmailDaXacMinh()
                || nguoiDoc.getVaiTro() != VaiTroTaiKhoan.KIEM_DUYET_VIEN) {
            return false;
        }
        if (!AnhTinTruyCap.ANH_DA_GAN.equals(anh.trangThaiAnh())
                || anh.phienBanTinId() == null
                || anh.phienBanDaDuyetId() == null) {
            return false;
        }
        if ("da_xoa".equals(anh.trangThaiTin())) {
            return false;
        }
        if (!tinIdCuaBaoCao.equals(tinIdCuaAnh)) {
            return false;
        }
        return anh.phienBanTinId().equals(anh.phienBanDaDuyetId());
    }
}

