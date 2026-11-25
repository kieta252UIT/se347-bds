package com.realestates.backend.moderation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Schema GiaQuyDoiHienThi; đường đọc quản lý/KDV mặc định chế độ tong. */
public record GiaQuyDoiHienThi(String giaTri, String donVi, String chuThich) {

    public static final String CHU_THICH = "Giá quy đổi tham khảo; không dùng để lọc.";
    private static final BigDecimal BA_MUOI = BigDecimal.valueOf(30);
    private static final BigDecimal MUOI_HAI = BigDecimal.valueOf(12);

    /**
     * Tính chính xác rồi làm tròn MỘT lần 2 chữ số HALF_UP (cùng bảng quy đổi với tìm kiếm/chi tiết; không dùng
     * PriceConverter vì nó làm tròn trung gian). Trả null nếu dữ liệu không quy đổi được.
     */
    public static GiaQuyDoiHienThi tinhCheDoTong(
            String loaiGiaoDich,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal dienTichM2
    ) {
        if (giaGoc == null || dienTichM2 == null || donViGia == null) {
            return null;
        }
        BigDecimal giaTri = switch (donViGia) {
            case "vnd_tong", "vnd_thang" -> giaGoc;
            case "vnd_m2", "vnd_m2_thang" -> giaGoc.multiply(dienTichM2);
            case "vnd_ngay" -> giaGoc.multiply(BA_MUOI);
            case "vnd_m2_ngay" -> giaGoc.multiply(BA_MUOI).multiply(dienTichM2);
            case "vnd_nam" -> giaGoc.divide(MUOI_HAI, 2, RoundingMode.HALF_UP);
            default -> null;
        };
        if (giaTri == null) {
            return null;
        }
        String donViHienThi = "ban".equals(loaiGiaoDich) ? "vnd_tong" : "vnd_thang";
        return new GiaQuyDoiHienThi(giaTri.setScale(2, RoundingMode.HALF_UP).toPlainString(), donViHienThi, CHU_THICH);
    }
}
