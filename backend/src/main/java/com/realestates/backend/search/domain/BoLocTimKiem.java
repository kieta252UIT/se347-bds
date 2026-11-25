package com.realestates.backend.search.domain;

import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Tham số tìm kiếm đã kiểm tra. Trường null/rỗng = không lọc. tuKhoa và khuVuc đã chuẩn hóa không dấu;
 * vùng bản đồ hoặc đủ cả bốn cận hoặc null cả bốn.
 */
public record BoLocTimKiem(
        List<String> tuKhoa,
        LoaiGiaoDich loaiGiaoDich,
        LoaiBds loaiBds,
        String khuVuc,
        KieuGia cheDoGia,
        BigDecimal giaMin,
        BigDecimal giaMax,
        BigDecimal dienTichMin,
        BigDecimal dienTichMax,
        BigDecimal nam,
        BigDecimal bac,
        BigDecimal tay,
        BigDecimal dong,
        SapXepTin sapXep,
        int trang,
        int kichThuoc
) {

    public BoLocTimKiem {
        tuKhoa = tuKhoa == null ? List.of() : List.copyOf(tuKhoa);
        Objects.requireNonNull(cheDoGia, "cheDoGia không được null");
        Objects.requireNonNull(sapXep, "sapXep không được null");
    }

    public boolean coTuKhoa() {
        return !tuKhoa.isEmpty();
    }

    public boolean coVung() {
        return nam != null;
    }
}
