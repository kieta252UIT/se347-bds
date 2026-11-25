package com.realestates.backend.moderation.domain;

import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.moderation.dto.TinQuanLy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KiemDuyetRepository {

    String LOAI_THAO_TAC_QUYET_DINH = "quyet_dinh";

    record KetQuaYeuCauQuyetDinh(String duLieuBam, TinGhiKetQua ketQua) {
    }

    record DuLieuQuyetDinh(
            UUID tinId,
            UUID phienBanDaXemId,
            UUID nguoiDuyetId,
            boolean pheDuyet,
            /** Chỉ dùng khi từ chối; đã trim. */
            String lyDo,
            long phienBanCapNhatDaXem,
            Instant thoiDiem,
            String maYDinh,
            String duLieuBam
    ) {
    }

    enum LoaiLoiKiemDuyet {
        TIN_KHONG_TON_TAI,
        /** ETag hoặc phienBanDaXemId không còn đúng (412). */
        PHIEN_BAN_CU,
        /** Tin/phiên bản không còn chờ duyệt: đã xóa, bị gỡ, đã có quyết định (409). */
        TRANG_THAI_KHONG_HOP_LE,
        /** Phiên bản không đạt BR-03 khi kiểm lại trong transaction phê duyệt (409). */
        KHONG_DAT_BR03,
        XUNG_DOT_MA_Y_DINH
    }

    final class LoiNghiepVuKiemDuyetException extends RuntimeException {

        private final LoaiLoiKiemDuyet loaiLoi;

        public LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet loaiLoi, String message) {
            super(message);
            this.loaiLoi = loaiLoi;
        }

        public LoaiLoiKiemDuyet getLoaiLoi() {
            return loaiLoi;
        }
    }

    /** Tin cho_kiem_duyet, ngày đăng tăng rồi id tăng. */
    List<TinQuanLy> docHangCho(int boQua, int gioiHan);

    long demHangCho();

    /** Tin kèm bản hiện tại, không lọc trạng thái (service quyết định 409). */
    Optional<TinQuanLy> docTinCanDuyet(UUID tinId);

    Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh);

    /**
     * Phải được gọi trong giao dịch ghi đã mở (khóa dòng tin, kiểm tra điều kiện, ghi phiên bản, tin, gói, nhật ký,
     * yêu cầu ghi) để mọi thay đổi cùng commit hoặc cùng rollback.
     */
    TinGhiKetQua quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu);
}
