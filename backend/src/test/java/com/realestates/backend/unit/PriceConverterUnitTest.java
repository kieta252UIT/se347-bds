package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.domain.model.PriceConverter;
import com.realestates.backend.domain.model.PriceConverter.DonViGia;
import com.realestates.backend.domain.model.PriceConverter.KetQuaQuyDoiGia;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cấp độ 1: Kiểm thử đơn vị (Unit Testing) theo IEEE 829 Test Plan Sprint 1 - Mục 7.1.
 * Đối tượng: Thuật toán quy đổi 7 đơn vị giá và bảo toàn độ chính xác BigDecimal (AC-02.3, TS1-63, TS1-64).
 * Đặc điểm: POJO thuần túy, không phụ thuộc Spring Context, thời gian chạy < 100ms.
 */
class PriceConverterUnitTest {

    @Test
    @DisplayName("TIN-P-S1: Bán 100m2 - 6.000.000.000 TONG_VND -> Tổng 6 tỷ, Đơn giá 60 triệu/m2")
    void shouldConvertTinPS1Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("6000000000"),
                DonViGia.TONG_VND,
                new BigDecimal("100")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("6000000000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("60000000");
    }

    @Test
    @DisplayName("TIN-P-S2: Bán 80m2 - 50.000.000 VND_M2 -> Tổng 4 tỷ, Đơn giá 50 triệu/m2")
    void shouldConvertTinPS2Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("50000000"),
                DonViGia.VND_M2,
                new BigDecimal("80")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("4000000000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("50000000");
    }

    @Test
    @DisplayName("TIN-P-S3: Bán 100m2 - 4.999.999.999 TONG_VND -> Tổng 4.999.999.999, Đơn giá 49.999.999,99/m2")
    void shouldConvertTinPS3Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("4999999999"),
                DonViGia.TONG_VND,
                new BigDecimal("100")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("4999999999");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("49999999.99");
    }

    @Test
    @DisplayName("TIN-P-R1: Thuê 50m2 - 10.000.000 VND_THANG -> Tổng 10 triệu/tháng, Đơn giá 200.000/m2/tháng")
    void shouldConvertTinPR1Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("10000000"),
                DonViGia.VND_THANG,
                new BigDecimal("50")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("10000000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("TIN-P-R2: Thuê 45m2 - 300.000 VND_NGAY -> Tổng 9 triệu/tháng (x30), Đơn giá 200.000/m2/tháng")
    void shouldConvertTinPR2Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("300000"),
                DonViGia.VND_NGAY,
                new BigDecimal("45")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("9000000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("TIN-P-R3: Thuê 60m2 - 144.000.000 VND_NAM -> Tổng 12 triệu/tháng (/12), Đơn giá 200.000/m2/tháng")
    void shouldConvertTinPR3Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("144000000"),
                DonViGia.VND_NAM,
                new BigDecimal("60")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("12000000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("TIN-P-R4: Thuê 80m2 - 4.000 VND_M2_NGAY -> Tổng 9.6 triệu/tháng, Đơn giá 120.000/m2/tháng")
    void shouldConvertTinPR4Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("4000"),
                DonViGia.VND_M2_NGAY,
                new BigDecimal("80")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("9600000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("120000");
    }

    @Test
    @DisplayName("TIN-P-R5: Thuê 100m2 - 100.000 VND_M2_THANG -> Tổng 10 triệu/tháng, Đơn giá 100.000/m2/tháng")
    void shouldConvertTinPR5Correctly() {
        KetQuaQuyDoiGia result = PriceConverter.quyDoi(
                new BigDecimal("100000"),
                DonViGia.VND_M2_THANG,
                new BigDecimal("100")
        );

        assertThat(result.giaTongChuanVnd()).isEqualByComparingTo("10000000");
        assertThat(result.giaM2ChuanVnd()).isEqualByComparingTo("100000");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 50",
            "-1000, 50",
            "10000000, 0",
            "10000000, -5"
    })
    @DisplayName("BR-03: Chặn giá trị biên không hợp lệ (Giá <= 0 hoặc Diện tích <= 0)")
    void shouldRejectInvalidPriceOrArea(double price, double area) {
        assertThatThrownBy(() -> PriceConverter.quyDoi(
                BigDecimal.valueOf(price),
                DonViGia.TONG_VND,
                BigDecimal.valueOf(area)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
