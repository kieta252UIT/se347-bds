package com.realestates.backend.recommendation.application;

import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thuật toán tính điểm gợi ý bất động sản đa yếu tố (Multi-criteria Scoring Algorithm):
 * Kết hợp điểm sở thích lịch sử tương tác (50%), độ mới tin đăng (20%) và khoảng cách địa lý (30%).
 */
public class ThuatToanTinhDiemGoiY {

    private static final BigDecimal TRONG_SO_SO_THICH = new BigDecimal("0.50");
    private static final BigDecimal TRONG_SO_DO_MOI = new BigDecimal("0.20");
    private static final BigDecimal TRONG_SO_KHOANG_CACH = new BigDecimal("0.30");

    /**
     * Tính điểm tổng hợp cho tin đăng với người dùng đã có lịch sử tương tác.
     */
    public BigDecimal tinhDiemTongHop(
            ThongTinTinGoiY tin,
            ViTriToaDo toaDoNguoiDung,
            List<LichSuXem> lichSuTuongTac,
            Map<java.util.UUID, ThongTinTinGoiY> mapTatCaTin,
            Instant now
    ) {
        BigDecimal diemSoThich = tinhDiemSoThichLichSu(tin, lichSuTuongTac, mapTatCaTin);
        BigDecimal diemDoMoi = tinhDiemDoMoi(tin.ngayDang(), now);
        BigDecimal diemKhoangCach = tinhDiemKhoangCach(tin.toaDo(), toaDoNguoiDung);

        BigDecimal diemTongHop = diemSoThich.multiply(TRONG_SO_SO_THICH)
                .add(diemDoMoi.multiply(TRONG_SO_DO_MOI))
                .add(diemKhoangCach.multiply(TRONG_SO_KHOANG_CACH));

        return diemTongHop.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Tính điểm cho người mới / khách chưa có lịch sử (chủ yếu dựa vào độ mới và khoảng cách địa lý).
     */
    public BigDecimal tinhDiemNguoiMoiHoacKhach(
            ThongTinTinGoiY tin,
            ViTriToaDo toaDoNguoiDung,
            Instant now
    ) {
        BigDecimal diemDoMoi = tinhDiemDoMoi(tin.ngayDang(), now);
        BigDecimal diemKhoangCach = tinhDiemKhoangCach(tin.toaDo(), toaDoNguoiDung);

        // Trọng số: 60% khoảng cách địa lý ("Gần trước"), 40% độ mới ("Mới sau")
        BigDecimal diem = diemKhoangCach.multiply(new BigDecimal("0.60"))
                .add(diemDoMoi.multiply(new BigDecimal("0.40")));

        return diem.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Tính điểm sở thích S_preference (thang điểm 0 - 100) dựa trên độ trùng khớp loại BĐS và khu vực.
     */
    private BigDecimal tinhDiemSoThichLichSu(
            ThongTinTinGoiY tin,
            List<LichSuXem> lichSuTuongTac,
            Map<java.util.UUID, ThongTinTinGoiY> mapTatCaTin
    ) {
        if (lichSuTuongTac == null || lichSuTuongTac.isEmpty() || mapTatCaTin == null) {
            return new BigDecimal("50.00"); // Điểm trung tính khi chưa có đủ dữ liệu
        }

        List<ThongTinTinGoiY> cacTinDaXem = lichSuTuongTac.stream()
                .map(ls -> mapTatCaTin.get(ls.tinId()))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (cacTinDaXem.isEmpty()) {
            return new BigDecimal("50.00");
        }

        // Tỷ lệ trùng loại BĐS
        long soTinTrungLoai = cacTinDaXem.stream()
                .filter(t -> t.loaiBds() != null && t.loaiBds().equalsIgnoreCase(tin.loaiBds()))
                .count();
        double tiLeTrungLoai = (double) soTinTrungLoai / cacTinDaXem.size();

        // Tỷ lệ trùng khu vực
        long soTinTrungKhuVuc = cacTinDaXem.stream()
                .filter(t -> t.khuVuc() != null && t.khuVuc().equalsIgnoreCase(tin.khuVuc()))
                .count();
        double tiLeTrungKhuVuc = (double) soTinTrungKhuVuc / cacTinDaXem.size();

        // Điểm sở thích = 60% loại BĐS + 40% khu vực (quy về thang 100)
        double score = (tiLeTrungLoai * 60.0) + (tiLeTrungKhuVuc * 40.0);
        return BigDecimal.valueOf(Math.min(100.0, Math.max(0.0, score))).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính điểm độ mới S_recency (thang điểm 0 - 100) dựa trên thời gian đăng.
     */
    private BigDecimal tinhDiemDoMoi(Instant ngayDang, Instant now) {
        if (ngayDang == null) {
            return BigDecimal.ZERO;
        }

        long soNgayTroiQua = Math.max(0, Duration.between(ngayDang, now).toDays());
        // Hàm suy giảm: Sau 0 ngày = 100đ, mỗi ngày giảm 2đ, tối thiểu 10đ
        double score = Math.max(10.0, 100.0 - (soNgayTroiQua * 2.0));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính điểm khoảng cách S_location (thang điểm 0 - 100) theo khoảng cách Haversine.
     */
    private BigDecimal tinhDiemKhoangCach(ViTriToaDo toaDoTin, ViTriToaDo toaDoNguoiDung) {
        if (toaDoTin == null || toaDoNguoiDung == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal khoangCachKm = toaDoTin.tinhKhoangCachKm(toaDoNguoiDung);
        double dist = khoangCachKm.doubleValue();

        // Bán kính ưu tiên: Càng gần điểm càng cao. Dưới 1km = 100đ, trên 20km = 0đ
        double score = Math.max(0.0, 100.0 - (dist * 5.0));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }
}
