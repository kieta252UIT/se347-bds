package com.realestates.backend.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Domain Service: Chuyển đổi chính xác 7 đơn vị giá chuẩn hóa theo IEEE 829 Test Plan Sprint 1 (AC-02.3, TS1-63, TS1-64).
 * POJO thuần túy không phụ thuộc Spring context để đảm bảo tốc độ chạy Unit Test dưới 5ms.
 */
public final class PriceConverter {

    private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);
    private static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);

    public enum DonViGia {
        TONG_VND,       // Bán: Tổng giá trị (VND)
        VND_M2,         // Bán: Giá theo m2 (VND/m2)
        VND_THANG,      // Thuê: Giá thuê theo tháng (VND/tháng)
        VND_NGAY,       // Thuê: Giá thuê theo ngày (VND/ngày -> quy về tháng x30)
        VND_NAM,        // Thuê: Giá thuê theo năm (VND/năm -> quy về tháng /12)
        VND_M2_NGAY,    // Thuê: Giá theo m2/ngày (VND/m2/ngày -> x diện tích x30)
        VND_M2_THANG    // Thuê: Giá theo m2/tháng (VND/m2/tháng -> x diện tích)
    }

    public record KetQuaQuyDoiGia(
            BigDecimal giaTongChuanVnd,
            BigDecimal giaM2ChuanVnd
    ) {}

    private PriceConverter() {
        // Utility class
    }

    /**
     * Quy đổi giá gốc và đơn vị sang 2 trường chuẩn hóa: Giá tổng chuẩn (VND) và Giá/m2 chuẩn (VND/m2).
     */
    public static KetQuaQuyDoiGia quyDoi(BigDecimal giaGoc, DonViGia donVi, BigDecimal dienTich) {
        if (giaGoc == null || giaGoc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá gốc phải lớn hơn 0");
        }
        if (dienTich == null || dienTich.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Diện tích phải lớn hơn 0");
        }
        if (donVi == null) {
            throw new IllegalArgumentException("Đơn vị giá không được để trống");
        }

        BigDecimal giaTong;
        BigDecimal giaM2;

        switch (donVi) {
            case TONG_VND -> {
                giaTong = giaGoc;
                giaM2 = giaTong.divide(dienTich, 2, RoundingMode.HALF_UP);
            }
            case VND_M2 -> {
                giaM2 = giaGoc;
                giaTong = giaM2.multiply(dienTich).setScale(2, RoundingMode.HALF_UP);
            }
            case VND_THANG -> {
                giaTong = giaGoc;
                giaM2 = giaTong.divide(dienTich, 2, RoundingMode.HALF_UP);
            }
            case VND_NGAY -> {
                giaTong = giaGoc.multiply(DAYS_IN_MONTH).setScale(2, RoundingMode.HALF_UP);
                giaM2 = giaTong.divide(dienTich, 2, RoundingMode.HALF_UP);
            }
            case VND_NAM -> {
                giaTong = giaGoc.divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);
                giaM2 = giaTong.divide(dienTich, 2, RoundingMode.HALF_UP);
            }
            case VND_M2_NGAY -> {
                BigDecimal giaM2Thang = giaGoc.multiply(DAYS_IN_MONTH).setScale(2, RoundingMode.HALF_UP);
                giaM2 = giaM2Thang;
                giaTong = giaM2.multiply(dienTich).setScale(2, RoundingMode.HALF_UP);
            }
            case VND_M2_THANG -> {
                giaM2 = giaGoc;
                giaTong = giaM2.multiply(dienTich).setScale(2, RoundingMode.HALF_UP);
            }
            default -> throw new UnsupportedOperationException("Chưa hỗ trợ đơn vị: " + donVi);
        }

        return new KetQuaQuyDoiGia(giaTong, giaM2);
    }
}
