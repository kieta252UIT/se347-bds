package com.realestates.backend.moderation.infrastructure;

import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.TinQuanLy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Bản tối thiểu cho chế độ in-memory (chỉ để ngữ cảnh Spring khởi động được): không có dữ liệu kiểm duyệt.
 * Nghiệp vụ kiểm duyệt đầy đủ chạy trên PostgreSQL (app.repository.type=postgres).
 */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryKiemDuyetRepository implements KiemDuyetRepository {

    @Override
    public List<TinQuanLy> docHangCho(int boQua, int gioiHan) {
        return List.of();
    }

    @Override
    public long demHangCho() {
        return 0L;
    }

    @Override
    public Optional<TinQuanLy> docTinCanDuyet(UUID tinId) {
        return Optional.empty();
    }

    @Override
    public Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
        return Optional.empty();
    }

    @Override
    public TinGhiKetQua quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu) {
        throw new LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet.TIN_KHONG_TON_TAI, "Không tìm thấy tin.");
    }
}
