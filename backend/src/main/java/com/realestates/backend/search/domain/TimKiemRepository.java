package com.realestates.backend.search.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimKiemRepository {

    /** Nội dung phiên bản đã duyệt của một tin; anhDaiDien là ảnh da_gan thu_tu nhỏ nhất (có thể null). */
    record DongTin(
            UUID tinId,
            UUID phienBanId,
            String tieuDe,
            String loaiGiaoDich,
            String loaiBds,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal dienTichM2,
            String khuVuc,
            BigDecimal viDo,
            BigDecimal kinhDo,
            Instant ngayDang,
            UUID anhDaiDienId,
            Integer anhDaiDienThuTu
    ) {
    }

    /** Cả hai khu và tổng số tự nhiên lấy từ cùng một câu SQL, cùng mốc thoiDiemTruyVan. */
    record KetQua(List<DongTin> tuNhien, long tongSoTuNhien, List<DongTin> taiTro, Instant thoiDiemTruyVan) {
    }

    KetQua timKiem(BoLocTimKiem boLoc, int soTaiTroToiDa);
}
