package com.realestates.backend.listing.domain;

import com.realestates.backend.listing.dto.TinGhiKetQua;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TinDangRepository {

    String LOAI_THAO_TAC_DANG_TIN = "dang_tin";
    String TRANG_THAI_CHO_KIEM_DUYET = "cho_kiem_duyet";

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

    Optional<KetQuaYeuCauGhi> timYeuCauGhi(UUID taiKhoanId, String loaiThaoTac, String maYDinh);

    TinGhiKetQua taoTinMoiTrongTransaction(DuLieuTaoTin duLieu);

    int docSoTinMoiTrongThang(UUID taiKhoanId, LocalDate dauThangVietNam);
}
