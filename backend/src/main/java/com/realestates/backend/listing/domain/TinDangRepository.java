package com.realestates.backend.listing.domain;

import com.realestates.backend.listing.dto.TinGhiKetQua;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TinDangRepository {

    String LOAI_THAO_TAC_DANG_TIN = "dang_tin";
    String LOAI_THAO_TAC_SUA_TIN = "sua_tin";
    String LOAI_THAO_TAC_XOA_TIN = "xoa_tin";
    String TRANG_THAI_CHO_KIEM_DUYET = "cho_kiem_duyet";
    String TRANG_THAI_DA_DUYET = "da_duyet";
    String TRANG_THAI_BI_TU_CHOI = "bi_tu_choi";
    String TRANG_THAI_DA_XOA = "da_xoa";

    record KetQuaYeuCauGhi(String duLieuBam, TinGhiKetQua ketQua) {
    }

    record DuLieuTaoTin(
            UUID nguoiDangId,
            NoiDungTinDauVao noiDung,
            List<UUID> anhIds,
            String ketQuaSoBo,
            LocalDate thangVietNam,
            Instant thoiDiem,
            String maYDinh,
            String duLieuBam,
            String duLieuAuditJson
    ) {
    }

    enum LoaiLoiTaoTin {
        XUNG_DOT_MA_Y_DINH,
        VUOT_HAN_MUC_THANG,
        ANH_KHONG_HOP_LE
    }

    final class LoiNghiepVuTaoTinException extends RuntimeException {

        private final LoaiLoiTaoTin loaiLoi;

        public LoiNghiepVuTaoTinException(LoaiLoiTaoTin loaiLoi, String message) {
            super(message);
            this.loaiLoi = loaiLoi;
        }

        public LoaiLoiTaoTin getLoaiLoi() {
            return loaiLoi;
        }
    }

    record DuLieuSuaTin(
            UUID tinId,
            UUID nguoiSuaId,
            UUID phienBanDaXemId,
            long phienBanCapNhatDaXem,
            NoiDungTinDauVao noiDung,
            List<UUID> anhIds,
            String ketQuaSoBo,
            Instant thoiDiem,
            String maYDinh,
            String duLieuBam
    ) {
    }

    record DuLieuXoaTin(
            UUID tinId,
            UUID nguoiXoaId,
            long phienBanCapNhatDaXem,
            Instant thoiDiem,
            String maYDinh,
            String duLieuBam
    ) {
    }

    enum LoaiLoiQuanLyTin {
        TIN_KHONG_TON_TAI,
        PHIEN_BAN_CU,
        TRANG_THAI_KHONG_HOP_LE,
        XUNG_DOT_MA_Y_DINH,
        ANH_KHONG_HOP_LE
    }

    final class LoiNghiepVuQuanLyTinException extends RuntimeException {

        private final LoaiLoiQuanLyTin loaiLoi;

        public LoiNghiepVuQuanLyTinException(LoaiLoiQuanLyTin loaiLoi, String message) {
            super(message);
            this.loaiLoi = loaiLoi;
        }

        public LoaiLoiQuanLyTin getLoaiLoi() {
            return loaiLoi;
        }
    }

    Optional<KetQuaYeuCauGhi> timYeuCauGhi(UUID taiKhoanId, String loaiThaoTac, String maYDinh);

    TinGhiKetQua taoTinMoiTrongTransaction(DuLieuTaoTin duLieu);

    int docSoTinMoiTrongThang(UUID taiKhoanId, LocalDate dauThangVietNam);

    Optional<UUID> khoaVaDocChuTin(UUID tinId);

    TinGhiKetQua suaTinTrongTransaction(DuLieuSuaTin duLieu);

    TinGhiKetQua xoaTinTrongTransaction(DuLieuXoaTin duLieu);
}
