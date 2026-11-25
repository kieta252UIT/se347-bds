package com.realestates.backend.detail.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Đọc chi tiết và liên hệ của tin công khai (BR-01), luôn là nội dung phiên bản ĐÃ DUYỆT. */
public interface ChiTietTinRepository {

    record AnhChiTiet(UUID id, int thuTu) {
    }

    record DongChiTiet(
            UUID tinId,
            Instant ngayDang,
            UUID phienBanId,
            int soPhienBan,
            String tieuDe,
            String moTa,
            String loaiGiaoDich,
            String loaiBds,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal dienTichM2,
            String diaChi,
            String khuVuc,
            BigDecimal viDo,
            BigDecimal kinhDo,
            Instant taoLuc,
            String hoTen,
            String soDienThoai,
            List<AnhChiTiet> anh
    ) {
    }

    /** Empty nếu tin không tồn tại hoặc không công khai. */
    Optional<DongChiTiet> docChiTiet(UUID tinId);
}
