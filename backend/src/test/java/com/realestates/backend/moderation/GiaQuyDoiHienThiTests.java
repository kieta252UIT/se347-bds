package com.realestates.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.moderation.dto.GiaQuyDoiHienThi;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Giá hiển thị màn kiểm duyệt: tính đầy đủ rồi làm tròn một lần HALF_UP; expected tính tay độc lập. */
class GiaQuyDoiHienThiTests {

    @ParameterizedTest
    @CsvSource({
            // 1000.0005 × 100 × 30 = 3000001.5 (PriceConverter cũ làm tròn trung gian ra 3000002.00)
            "thue, 1000.0005, vnd_m2_ngay, 100, 3000001.50, vnd_thang",
            // 0.005 × 30 = 0.15 (làm tròn trước khi nhân sẽ ra 0.30)
            "thue, 0.005, vnd_ngay, 10, 0.15, vnd_thang",
            // 1.005 × 3 = 3.015 → 3.02 (làm tròn đơn giá trước sẽ ra 3.03)
            "ban, 1.005, vnd_m2, 3, 3.02, vnd_tong",
            "thue, 1.0049, vnd_m2_thang, 1, 1.00, vnd_thang",
            "thue, 100, vnd_nam, 50, 8.33, vnd_thang",
            "ban, 2500000000, vnd_tong, 68.5, 2500000000.00, vnd_tong",
            "thue, 7000000, vnd_thang, 30, 7000000.00, vnd_thang"
    })
    void lamTronMotLan(String loai, String gia, String donVi, String dienTich, String mongDoi, String donViHienThi) {
        GiaQuyDoiHienThi kq = GiaQuyDoiHienThi.tinhCheDoTong(loai, new BigDecimal(gia), donVi, new BigDecimal(dienTich));

        assertThat(kq.giaTri()).isEqualTo(mongDoi);
        assertThat(kq.donVi()).isEqualTo(donViHienThi);
        assertThat(kq.chuThich()).isEqualTo(GiaQuyDoiHienThi.CHU_THICH);
    }

    @ParameterizedTest
    @CsvSource({"vnd_la", "''"})
    void donViKhongHopLeTraNull(String donVi) {
        assertThat(GiaQuyDoiHienThi.tinhCheDoTong("ban", BigDecimal.ONE, donVi, BigDecimal.ONE)).isNull();
    }
}
