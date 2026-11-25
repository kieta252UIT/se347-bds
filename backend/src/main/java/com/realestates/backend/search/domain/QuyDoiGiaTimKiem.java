package com.realestates.backend.search.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Giá quy đổi dạng phân số chính xác N/D (TK-06). Chế độ tong: bán = VND tổng, thuê = VND/tháng;
 * chế độ m2: chia thêm diện tích. Lọc dùng nhân chéo N ≥ L·D, N ≤ U·D (SQL); chỉ hiển thị mới chia và làm tròn
 * 2 chữ số HALF_UP. Bảng ở {@link #tu(String, KieuGia, BigDecimal, BigDecimal)} và {@link #sqlTu}/{@link #sqlMau}
 * phải luôn khớp nhau.
 */
public record QuyDoiGiaTimKiem(BigDecimal tu, BigDecimal mau) {

    private static final BigDecimal BA_MUOI = BigDecimal.valueOf(30);
    private static final BigDecimal MUOI_HAI = BigDecimal.valueOf(12);

    public static QuyDoiGiaTimKiem tu(String donViGia, KieuGia cheDo, BigDecimal giaGoc, BigDecimal dienTich) {
        BigDecimal tuTong;
        BigDecimal mauTong;
        switch (donViGia) {
            case "vnd_tong", "vnd_thang" -> {
                tuTong = giaGoc;
                mauTong = BigDecimal.ONE;
            }
            case "vnd_m2", "vnd_m2_thang" -> {
                tuTong = giaGoc.multiply(dienTich);
                mauTong = BigDecimal.ONE;
            }
            case "vnd_ngay" -> {
                tuTong = giaGoc.multiply(BA_MUOI);
                mauTong = BigDecimal.ONE;
            }
            case "vnd_nam" -> {
                tuTong = giaGoc;
                mauTong = MUOI_HAI;
            }
            case "vnd_m2_ngay" -> {
                tuTong = giaGoc.multiply(BA_MUOI).multiply(dienTich);
                mauTong = BigDecimal.ONE;
            }
            default -> throw new IllegalArgumentException("Đơn vị giá không hợp lệ: " + donViGia);
        }
        return cheDo == KieuGia.M2
                ? new QuyDoiGiaTimKiem(tuTong, mauTong.multiply(dienTich))
                : new QuyDoiGiaTimKiem(tuTong, mauTong);
    }

    /** Giá trị hiển thị: N/D làm tròn 2 chữ số HALF_UP. */
    public BigDecimal hienThi() {
        return tu.divide(mau, 2, RoundingMode.HALF_UP);
    }

    /** Đơn vị hiển thị theo loại giao dịch và chế độ giá. */
    public static String donViHienThi(String loaiGiaoDich, KieuGia cheDo) {
        boolean ban = "ban".equals(loaiGiaoDich);
        if (cheDo == KieuGia.M2) {
            return ban ? "vnd_m2" : "vnd_m2_thang";
        }
        return ban ? "vnd_tong" : "vnd_thang";
    }

    /** Tử số N trong SQL; p là alias bảng phiên bản do mã cung cấp. */
    public static String sqlTu(String p) {
        String g = p + ".gia_goc";
        String a = p + ".dien_tich_m2";
        return "(CASE " + p + ".don_vi_gia"
                + " WHEN 'vnd_tong' THEN " + g
                + " WHEN 'vnd_thang' THEN " + g
                + " WHEN 'vnd_m2' THEN " + g + " * " + a
                + " WHEN 'vnd_m2_thang' THEN " + g + " * " + a
                + " WHEN 'vnd_ngay' THEN " + g + " * 30"
                + " WHEN 'vnd_nam' THEN " + g
                + " WHEN 'vnd_m2_ngay' THEN " + g + " * 30 * " + a
                + " END)";
    }

    /** Mẫu số D trong SQL theo chế độ giá. */
    public static String sqlMau(String p, KieuGia cheDo) {
        String mauTong = "(CASE WHEN " + p + ".don_vi_gia = 'vnd_nam' THEN 12 ELSE 1 END)";
        return cheDo == KieuGia.M2 ? "(" + mauTong + " * " + p + ".dien_tich_m2)" : mauTong;
    }
}
