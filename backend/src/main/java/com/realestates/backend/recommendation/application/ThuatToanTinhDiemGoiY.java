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
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thuật toán tính điểm gợi ý bất động sản đa yếu tố (Multi-criteria Scoring Algorithm):
 * Kết hợp điểm nhóm sở thích từ lịch sử tương tác trong cửa sổ 30 ngày (50%),
 * độ mới tin đăng (20%) và khoảng cách địa lý (30%).
 * Đáp ứng AC-03.2, AC-03.3, Q28 và SDD-23.
 */
public class ThuatToanTinhDiemGoiY {

    public static final Duration CUA_SO_LICH_SU_30_NGAY = Duration.ofDays(30);

    private static final BigDecimal TRONG_SO_SO_THICH = new BigDecimal("0.50");
    private static final BigDecimal TRONG_SO_DO_MOI = new BigDecimal("0.20");
    private static final BigDecimal TRONG_SO_KHOANG_CACH = new BigDecimal("0.30");

    /**
     * Hồ sơ điểm nhóm sở thích (Group preference score profile)
     * tổng hợp từ lịch sử xem trong cửa sổ trượt 30 ngày theo nhóm loại BĐS và nhóm khu vực.
     */
    public record DiemNhomSoThich(
            Map<String, Double> tiLeTheoLoaiBds,
            Map<String, Double> tiLeTheoKhuVuc,
            int tongSoTinHopLeTrong30Ngay
    ) {
        public boolean coLichSu() {
            return tongSoTinHopLeTrong30Ngay > 0;
        }

        /**
         * Tính điểm sở thích cho một tin dựa trên tỷ lệ nhóm tương tác trong 30 ngày.
         * Thang điểm 0 - 100: 60% nhóm loại BĐS + 40% nhóm khu vực.
         */
        public BigDecimal tinhDiemChoTin(ThongTinTinGoiY tin) {
            if (!coLichSu() || tin == null) {
                return new BigDecimal("50.00"); // Điểm trung tính khi không có lịch sử trong 30 ngày
            }

            double tiLeLoai = (tin.loaiBds() != null)
                    ? tiLeTheoLoaiBds.getOrDefault(tin.loaiBds().toLowerCase(), 0.0)
                    : 0.0;
            double tiLeKhuVuc = (tin.khuVuc() != null)
                    ? tiLeTheoKhuVuc.getOrDefault(tin.khuVuc().toLowerCase(), 0.0)
                    : 0.0;

            double score = (tiLeLoai * 60.0) + (tiLeKhuVuc * 40.0);
            return BigDecimal.valueOf(Math.min(100.0, Math.max(0.0, score)))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Lọc danh sách lịch sử xem chỉ giữ lại các tương tác trong cửa sổ 30 ngày trượt [now - 30 days, now].
     */
    public List<LichSuXem> locLichSuTrongCuaSo30Ngay(List<LichSuXem> lichSuTuongTac, Instant now) {
        if (lichSuTuongTac == null || lichSuTuongTac.isEmpty() || now == null) {
            return List.of();
        }
        Instant tuThoiDiem30Ngay = now.minus(CUA_SO_LICH_SU_30_NGAY);
        return lichSuTuongTac.stream()
                .filter(ls -> ls.thoiDiemXem() != null
                        && !ls.thoiDiemXem().isBefore(tuThoiDiem30Ngay)
                        && !ls.thoiDiemXem().isAfter(now))
                .toList();
    }

    /**
     * Tính toán hồ sơ điểm nhóm sở thích từ lịch sử tương tác trong cửa sổ 30 ngày.
     */
    public DiemNhomSoThich tinhDiemNhomSoThich(
            List<LichSuXem> lichSuTuongTac,
            Map<UUID, ThongTinTinGoiY> mapTatCaTin,
            Instant now
    ) {
        List<LichSuXem> lichSu30Ngay = locLichSuTrongCuaSo30Ngay(lichSuTuongTac, now);
        if (lichSu30Ngay.isEmpty() || mapTatCaTin == null) {
            return new DiemNhomSoThich(Map.of(), Map.of(), 0);
        }

        List<ThongTinTinGoiY> cacTinDaXem = lichSu30Ngay.stream()
                .map(ls -> mapTatCaTin.get(ls.tinId()))
                .filter(Objects::nonNull)
                .toList();

        if (cacTinDaXem.isEmpty()) {
            return new DiemNhomSoThich(Map.of(), Map.of(), 0);
        }

        int total = cacTinDaXem.size();
        Map<String, Double> tiLeLoai = cacTinDaXem.stream()
                .filter(t -> t.loaiBds() != null)
                .collect(Collectors.groupingBy(
                        t -> t.loaiBds().toLowerCase(),
                        Collectors.collectingAndThen(Collectors.counting(), count -> (double) count / total)
                ));

        Map<String, Double> tiLeKhuVuc = cacTinDaXem.stream()
                .filter(t -> t.khuVuc() != null)
                .collect(Collectors.groupingBy(
                        t -> t.khuVuc().toLowerCase(),
                        Collectors.collectingAndThen(Collectors.counting(), count -> (double) count / total)
                ));

        return new DiemNhomSoThich(tiLeLoai, tiLeKhuVuc, total);
    }

    /**
     * Tính điểm tổng hợp cho tin đăng với người dùng đã có lịch sử tương tác.
     * Sử dụng các tương tác trong cửa sổ 30 ngày để tính điểm nhóm sở thích.
     */
    public BigDecimal tinhDiemTongHop(
            ThongTinTinGoiY tin,
            ViTriToaDo toaDoNguoiDung,
            List<LichSuXem> lichSuTuongTac,
            Map<UUID, ThongTinTinGoiY> mapTatCaTin,
            Instant now
    ) {
        BigDecimal diemSoThich = tinhDiemSoThichLichSu(tin, lichSuTuongTac, mapTatCaTin, now);
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
     * Tính điểm sở thích S_preference (thang điểm 0 - 100) dựa trên độ trùng khớp nhóm loại BĐS và nhóm khu vực
     * trong cửa sổ 30 ngày.
     */
    public BigDecimal tinhDiemSoThichLichSu(
            ThongTinTinGoiY tin,
            List<LichSuXem> lichSuTuongTac,
            Map<UUID, ThongTinTinGoiY> mapTatCaTin,
            Instant now
    ) {
        DiemNhomSoThich diemNhom = tinhDiemNhomSoThich(lichSuTuongTac, mapTatCaTin, now);
        return diemNhom.tinhDiemChoTin(tin);
    }

    /**
     * Giữ nguyên chữ ký cũ để tương thích ngược.
     */
    public BigDecimal tinhDiemSoThichLichSu(
            ThongTinTinGoiY tin,
            List<LichSuXem> lichSuTuongTac,
            Map<UUID, ThongTinTinGoiY> mapTatCaTin
    ) {
        return tinhDiemSoThichLichSu(tin, lichSuTuongTac, mapTatCaTin, Instant.now());
    }

    /**
     * Tính điểm độ mới S_recency (thang điểm 0 - 100) trong cửa sổ 30 ngày.
     * Tin trong vòng 30 ngày giảm dần theo số ngày trôi qua, sau 30 ngày giữ sàn tối thiểu 10đ.
     */
    public BigDecimal tinhDiemDoMoi(Instant ngayDang, Instant now) {
        if (ngayDang == null) {
            return BigDecimal.ZERO;
        }

        long soNgayTroiQua = Math.max(0, Duration.between(ngayDang, now).toDays());
        // Cửa sổ 30 ngày: Sau 0 ngày = 100đ, giảm dần theo thời gian, tối thiểu 10đ sau 30 ngày
        double score;
        if (soNgayTroiQua >= 30) {
            score = 10.0;
        } else {
            score = Math.max(10.0, 100.0 - (soNgayTroiQua * 2.0));
        }
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tính điểm khoảng cách S_location (thang điểm 0 - 100) theo khoảng cách Haversine.
     */
    public BigDecimal tinhDiemKhoangCach(ViTriToaDo toaDoTin, ViTriToaDo toaDoNguoiDung) {
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
