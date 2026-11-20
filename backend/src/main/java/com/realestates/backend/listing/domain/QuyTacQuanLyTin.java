package com.realestates.backend.listing.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class QuyTacQuanLyTin {

    private QuyTacQuanLyTin() {
    }

    public static boolean choPhepSua(String trangThaiTin) {
        return TinDangRepository.TRANG_THAI_DA_DUYET.equals(trangThaiTin)
                || TinDangRepository.TRANG_THAI_BI_TU_CHOI.equals(trangThaiTin);
    }

    public static boolean choPhepXoa(String trangThaiTin) {
        return TinDangRepository.TRANG_THAI_CHO_KIEM_DUYET.equals(trangThaiTin)
                || TinDangRepository.TRANG_THAI_DA_DUYET.equals(trangThaiTin)
                || TinDangRepository.TRANG_THAI_BI_TU_CHOI.equals(trangThaiTin);
    }

    public static boolean dungPhienBanDaXem(
            long phienBanCapNhatHienTai,
            long phienBanCapNhatDaXem,
            UUID phienBanHienTaiId,
            UUID phienBanDaXemId
    ) {
        return phienBanCapNhatHienTai == phienBanCapNhatDaXem
                && phienBanHienTaiId != null
                && phienBanHienTaiId.equals(phienBanDaXemId);
    }

    public static boolean dungPhienBanCapNhat(long phienBanCapNhatHienTai, long phienBanCapNhatDaXem) {
        return phienBanCapNhatHienTai == phienBanCapNhatDaXem;
    }

    public static String duLieuAuditSua(
            String trangThaiTruoc,
            UUID phienBanTruocId,
            long phienBanCapNhatTruoc,
            UUID phienBanSauId,
            int soPhienBanSau,
            long phienBanCapNhatSau
    ) {
        Map<String, Object> truoc = new LinkedHashMap<>();
        truoc.put("trangThai", trangThaiTruoc);
        truoc.put("phienBanHienTaiId", phienBanTruocId);
        truoc.put("phienBanCapNhat", phienBanCapNhatTruoc);

        Map<String, Object> sau = new LinkedHashMap<>();
        sau.put("trangThai", TinDangRepository.TRANG_THAI_CHO_KIEM_DUYET);
        sau.put("phienBanHienTaiId", phienBanSauId);
        sau.put("soPhienBan", soPhienBanSau);
        sau.put("phienBanCapNhat", phienBanCapNhatSau);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("truoc", truoc);
        audit.put("sau", sau);
        return ChuanHoaJson.toJsonString(audit);
    }

    public static String duLieuAuditXoa(
            String trangThaiTruoc,
            long phienBanCapNhatTruoc,
            long phienBanCapNhatSau
    ) {
        Map<String, Object> truoc = new LinkedHashMap<>();
        truoc.put("trangThai", trangThaiTruoc);
        truoc.put("phienBanCapNhat", phienBanCapNhatTruoc);

        Map<String, Object> sau = new LinkedHashMap<>();
        sau.put("trangThai", TinDangRepository.TRANG_THAI_DA_XOA);
        sau.put("phienBanCapNhat", phienBanCapNhatSau);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("truoc", truoc);
        audit.put("sau", sau);
        return ChuanHoaJson.toJsonString(audit);
    }
}

