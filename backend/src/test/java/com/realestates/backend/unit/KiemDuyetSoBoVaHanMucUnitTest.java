package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.domain.DonViGiaTin;
import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.KiemDuyetSoBoTin;
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.listing.domain.NoiDungTinDauVao;
import com.realestates.backend.listing.exception.MapServiceUnavailableException;
import com.realestates.backend.listing.infrastructure.MacDinhBanDoAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KiemDuyetSoBoVaHanMucUnitTest {

    private static NoiDungTinDauVao noiDungHopLe() {
        return new NoiDungTinDauVao(
                "Nhà phố 1 trệt 2 lầu quận 3",
                "Sổ hồng riêng, hẻm xe hơi.",
                LoaiGiaoDich.BAN,
                LoaiBds.NHA_RIENG,
                new BigDecimal("8500000000"),
                DonViGiaTin.VND_TONG,
                new BigDecimal("72.5"),
                "45 Võ Văn Tần",
                "Quận 3, TP.HCM",
                "10.7745",
                "106.6901"
        );
    }

    @Test
    @DisplayName("HanMucThangHelper tính đúng ranh giới tháng theo múi giờ Asia/Ho_Chi_Minh (TS1-025)")
    void ranhGioiThangTheoMuiGioVietNam() {
        // 2026-11-30 23:59:59 +07:00 == 2026-11-30T16:59:59Z -> tháng 11/2026
        Instant cuoiThang11Vn = Instant.parse("2026-11-30T16:59:59Z");
        // 2026-12-01 00:00:00 +07:00 == 2026-11-30T17:00:00Z -> tháng 12/2026
        Instant dauThang12Vn = Instant.parse("2026-11-30T17:00:00Z");

        assertThat(HanMucThangHelper.dauThangVietNam(cuoiThang11Vn)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(HanMucThangHelper.dauThangVietNam(dauThang12Vn)).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    @DisplayName("ChuanHoaJson sắp xếp key map, giữ thứ tự mảng, phân biệt kiểu dữ liệu và chuỗi số")
    void chuanHoaJsonBamChuan() {
        UUID anh1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID anh2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        Map<String, Object> thuTu1 = new LinkedHashMap<>();
        thuTu1.put("b", "2");
        thuTu1.put("a", "1");
        thuTu1.put("anhIds", List.of(anh1.toString(), anh2.toString()));

        Map<String, Object> daoKey = new LinkedHashMap<>();
        daoKey.put("anhIds", List.of(anh1.toString(), anh2.toString()));
        daoKey.put("a", "1");
        daoKey.put("b", "2");

        Map<String, Object> daoThuTuAnh = new LinkedHashMap<>();
        daoThuTuAnh.put("a", "1");
        daoThuTuAnh.put("b", "2");
        daoThuTuAnh.put("anhIds", List.of(anh2.toString(), anh1.toString()));

        Map<String, Object> khacKieu = new LinkedHashMap<>();
        khacKieu.put("a", 1);
        khacKieu.put("b", "2");
        khacKieu.put("anhIds", List.of(anh1.toString(), anh2.toString()));

        Map<String, Object> khacSo0 = new LinkedHashMap<>();
        khacSo0.put("a", "1.00");
        khacSo0.put("b", "2");
        khacSo0.put("anhIds", List.of(anh1.toString(), anh2.toString()));

        String hash1 = ChuanHoaJson.bamYeuCau("POST", "/api/listings", thuTu1);
        assertThat(ChuanHoaJson.bamYeuCau("POST", "/api/listings", daoKey)).isEqualTo(hash1);
        assertThat(ChuanHoaJson.bamYeuCau("POST", "/api/listings", daoThuTuAnh)).isNotEqualTo(hash1);
        assertThat(ChuanHoaJson.bamYeuCau("POST", "/api/listings", khacKieu)).isNotEqualTo(hash1);
        assertThat(ChuanHoaJson.bamYeuCau("POST", "/api/listings", khacSo0)).isNotEqualTo(hash1);
        assertThat(ChuanHoaJson.bamYeuCau("PUT", "/api/listings", thuTu1)).isNotEqualTo(hash1);

        String json = ChuanHoaJson.toJsonString(Map.of(
                "tieuDe", "Tin \"đẹp\"\nMới",
                "soLuong", 2,
                "anhIds", List.of(anh1.toString())
        ));
        assertThat(json).contains("\\\"đẹp\\\"\\nMới");
        assertThat(json).contains("\"soLuong\":2");
    }

    @Test
    @DisplayName("KiemDuyetSoBoTin đánh giá đúng hợp lệ và các nhánh không đạt")
    void kiemDuyetSoBoDanhGiaDung() {
        List<UUID> motAnh = List.of(UUID.randomUUID());

        assertThat(KiemDuyetSoBoTin.danhGia(noiDungHopLe(), motAnh))
                .isEqualTo(new KiemDuyetSoBoTin.KetQua(true, KiemDuyetSoBoTin.KET_QUA_DAT));

        assertThat(KiemDuyetSoBoTin.danhGia(null, motAnh).dat()).isFalse();
        assertThat(KiemDuyetSoBoTin.danhGia(noiDungHopLe(), List.of()).dat()).isFalse();

        NoiDungTinDauVao saiDonVi = new NoiDungTinDauVao(
                "Tiêu đề",
                "Mô tả",
                LoaiGiaoDich.CHO_THUE,
                LoaiBds.CAN_HO,
                new BigDecimal("10000000"),
                DonViGiaTin.VND_TONG,
                new BigDecimal("50"),
                "Địa chỉ",
                "Khu vực",
                "10.7",
                "106.6"
        );
        assertThat(KiemDuyetSoBoTin.danhGia(saiDonVi, motAnh).dat()).isFalse();

        NoiDungTinDauVao toaDoNgoaiMien = new NoiDungTinDauVao(
                "Tiêu đề",
                "Mô tả",
                LoaiGiaoDich.BAN,
                LoaiBds.CAN_HO,
                new BigDecimal("1000000000"),
                DonViGiaTin.VND_TONG,
                new BigDecimal("50"),
                "Địa chỉ",
                "Khu vực",
                "91.0",
                "106.6"
        );
        assertThat(KiemDuyetSoBoTin.danhGia(toaDoNgoaiMien, motAnh).dat()).isFalse();
    }

    @Test
    @DisplayName("MacDinhBanDoAdapter hỗ trợ sandbox, fail, unconfigured và unavailable")
    void macDinhBanDoAdapterCacCheDo() {
        MacDinhBanDoAdapter sandbox = new MacDinhBanDoAdapter("sandbox");
        assertThat(sandbox.xacMinhToaDo(new BigDecimal("10.77"), new BigDecimal("106.70"), "12 Lê Lợi", "Q1")).isTrue();
        assertThat(sandbox.xacMinhToaDo(new BigDecimal("95"), new BigDecimal("106.70"), "12 Lê Lợi", "Q1")).isFalse();
        assertThat(sandbox.xacMinhToaDo(new BigDecimal("10.77"), new BigDecimal("106.70"), "  ", "Q1")).isFalse();

        MacDinhBanDoAdapter fail = new MacDinhBanDoAdapter("fail");
        assertThat(fail.xacMinhToaDo(new BigDecimal("10.77"), new BigDecimal("106.70"), "12 Lê Lợi", "Q1")).isFalse();

        MacDinhBanDoAdapter unconfigured = new MacDinhBanDoAdapter("unconfigured");
        assertThatThrownBy(() -> unconfigured.xacMinhToaDo(BigDecimal.TEN, BigDecimal.TEN, "A", "B"))
                .isInstanceOf(MapServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((MapServiceUnavailableException) ex).getMaLoi())
                        .isEqualTo(MapServiceUnavailableException.MA_LOI_BAN_DO_CHUA_SAN_SANG));

        MacDinhBanDoAdapter unavailable = new MacDinhBanDoAdapter("unavailable");
        assertThatThrownBy(() -> unavailable.xacMinhToaDo(BigDecimal.TEN, BigDecimal.TEN, "A", "B"))
                .isInstanceOf(MapServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((MapServiceUnavailableException) ex).getMaLoi())
                        .isEqualTo(MapServiceUnavailableException.MA_LOI_TAM_GIAN_DOAN));
    }
}
