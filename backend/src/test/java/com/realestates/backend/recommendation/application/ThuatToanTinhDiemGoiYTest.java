package com.realestates.backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.*;

import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThuatToanTinhDiemGoiYTest {

    private ThuatToanTinhDiemGoiY thuatToan;
    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");

    @BeforeEach
    void setUp() {
        thuatToan = new ThuatToanTinhDiemGoiY();
    }

    @Test
    @DisplayName("Haversine: Tính khoảng cách chính xác giữa 2 tọa độ TP.HCM")
    void testTinhKhoangCachHaversine() {
        // Tọa độ Quận 1 (Bưu điện TP.HCM): 10.7769, 106.7009
        ViTriToaDo quan1 = ViTriToaDo.TOA_DO_MAC_DINH_TPHCM;
        // Tọa độ Landmark 81 (Bình Thạnh): 10.7950, 106.7218
        ViTriToaDo landmark81 = new ViTriToaDo(new BigDecimal("10.7950"), new BigDecimal("106.7218"));

        BigDecimal khoangCachKm = quan1.tinhKhoangCachKm(landmark81);
        assertNotNull(khoangCachKm);
        // Khoảng cách thực tế khoảng 3.0km - 3.5km
        assertTrue(khoangCachKm.compareTo(new BigDecimal("2.5")) > 0);
        assertTrue(khoangCachKm.compareTo(new BigDecimal("4.0")) < 0);
    }

    @Test
    @DisplayName("S_preference: Điểm sở thích cao hơn khi tin trùng loại BĐS và khu vực với lịch sử xem")
    void testDiemSoThichLichSu() {
        UUID userId = UUID.randomUUID();
        UUID tin1Id = UUID.randomUUID();
        UUID tin2Id = UUID.randomUUID();

        // Tin đã xem trong lịch sử: Căn hộ tại Quận 1
        ThongTinTinGoiY tinDaXem = new ThongTinTinGoiY(
                tin1Id, UUID.randomUUID(), "Căn hộ Q1", "Mô tả", "ban", "can_ho",
                new BigDecimal("3000000000"), "vnd_tong", new BigDecimal("3000000000"),
                new BigDecimal("50000000"), new BigDecimal("60"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now.minus(2, ChronoUnit.DAYS),
                null, "da_duyet", "hoat_dong", false
        );

        List<LichSuXem> lichSu = List.of(new LichSuXem(UUID.randomUUID(), userId, tin1Id, now.minus(1, ChronoUnit.HOURS)));
        Map<UUID, ThongTinTinGoiY> mapTin = Map.of(tin1Id, tinDaXem);

        // Tin A: Cùng là căn hộ tại Quận 1 (khớp 100%)
        ThongTinTinGoiY tinA = new ThongTinTinGoiY(
                UUID.randomUUID(), UUID.randomUUID(), "Căn hộ cao cấp Q1", "Mô tả", "ban", "can_ho",
                new BigDecimal("4000000000"), "vnd_tong", new BigDecimal("4000000000"),
                new BigDecimal("60000000"), new BigDecimal("70"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now,
                null, "da_duyet", "hoat_dong", false
        );

        // Tin B: Đất nền tại Củ Chi (khác hoàn toàn sở thích)
        ThongTinTinGoiY tinB = new ThongTinTinGoiY(
                tin2Id, UUID.randomUUID(), "Đất vườn Củ Chi", "Mô tả", "ban", "dat_nen",
                new BigDecimal("1500000000"), "vnd_tong", new BigDecimal("1500000000"),
                new BigDecimal("10000000"), new BigDecimal("150"), "Địa chỉ", "Cu Chi",
                new ViTriToaDo(new BigDecimal("11.0000"), new BigDecimal("106.5000")), now,
                null, "da_duyet", "hoat_dong", false
        );

        BigDecimal scoreA = thuatToan.tinhDiemTongHop(tinA, ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, lichSu, mapTin, now);
        BigDecimal scoreB = thuatToan.tinhDiemTongHop(tinB, ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, lichSu, mapTin, now);

        assertTrue(scoreA.compareTo(scoreB) > 0, "Tin A hợp sở thích và gần hơn phải có điểm cao hơn Tin B");
    }

    @Test
    @DisplayName("S_location & S_recency: Khách chưa có lịch sử ưu tiên gần trước, mới sau")
    void testDiemNguoiMoiHoacKhach() {
        ViTriToaDo viTriKhach = ViTriToaDo.TOA_DO_MAC_DINH_TPHCM;

        // Tin gần (cách 0.5km), đăng hôm qua
        ThongTinTinGoiY tinGan = new ThongTinTinGoiY(
                UUID.randomUUID(), UUID.randomUUID(), "Tin gần", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                new ViTriToaDo(new BigDecimal("10.7800"), new BigDecimal("106.7020")),
                now.minus(1, ChronoUnit.DAYS), null, "da_duyet", "hoat_dong", false
        );

        // Tin xa (cách 30km), đăng hôm nay
        ThongTinTinGoiY tinXa = new ThongTinTinGoiY(
                UUID.randomUUID(), UUID.randomUUID(), "Tin xa", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Binh Chanh",
                new ViTriToaDo(new BigDecimal("10.5000"), new BigDecimal("106.5000")),
                now, null, "da_duyet", "hoat_dong", false
        );

        BigDecimal scoreGan = thuatToan.tinhDiemNguoiMoiHoacKhach(tinGan, viTriKhach, now);
        BigDecimal scoreXa = thuatToan.tinhDiemNguoiMoiHoacKhach(tinXa, viTriKhach, now);

        assertTrue(scoreGan.compareTo(scoreXa) > 0, "Tin gần phải có điểm cao hơn tin xa");
    }

    @Test
    @DisplayName("Cửa sổ 30 ngày: Loại bỏ tương tác cũ hơn 30 ngày khi tính điểm sở thích")
    void testCuaSo30Ngay_LoaiBoTuongTacCuHon30Ngay() {
        UUID userId = UUID.randomUUID();
        UUID tinTrong30NgayId = UUID.randomUUID();
        UUID tinCuHon30NgayId = UUID.randomUUID();

        // Tin tương tác trong 30 ngày: Căn hộ tại Quận 1 (xem cách đây 10 ngày)
        ThongTinTinGoiY tinTrong30Ngay = new ThongTinTinGoiY(
                tinTrong30NgayId, UUID.randomUUID(), "Căn hộ Q1", "Mô tả", "ban", "can_ho",
                new BigDecimal("3000000000"), "vnd_tong", new BigDecimal("3000000000"),
                BigDecimal.ZERO, new BigDecimal("60"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now.minus(10, ChronoUnit.DAYS),
                null, "da_duyet", "hoat_dong", false
        );

        // Tin tương tác cũ hơn 30 ngày: Biệt thự tại Củ Chi (xem cách đây 45 ngày)
        ThongTinTinGoiY tinCuHon30Ngay = new ThongTinTinGoiY(
                tinCuHon30NgayId, UUID.randomUUID(), "Biệt thự Củ Chi", "Mô tả", "ban", "biet_thu",
                new BigDecimal("5000000000"), "vnd_tong", new BigDecimal("5000000000"),
                BigDecimal.ZERO, new BigDecimal("200"), "Địa chỉ", "Cu Chi",
                new ViTriToaDo(new BigDecimal("11.0000"), new BigDecimal("106.5000")), now.minus(45, ChronoUnit.DAYS),
                null, "da_duyet", "hoat_dong", false
        );

        List<LichSuXem> lichSu = List.of(
                new LichSuXem(UUID.randomUUID(), userId, tinTrong30NgayId, now.minus(10, ChronoUnit.DAYS)),
                new LichSuXem(UUID.randomUUID(), userId, tinCuHon30NgayId, now.minus(45, ChronoUnit.DAYS))
        );
        Map<UUID, ThongTinTinGoiY> mapTin = Map.of(
                tinTrong30NgayId, tinTrong30Ngay,
                tinCuHon30NgayId, tinCuHon30Ngay
        );

        // Tin cần gợi ý 1: Căn hộ tại Quận 1 -> Khớp 100% với tin xem trong 30 ngày
        ThongTinTinGoiY tinCanHoQ1 = new ThongTinTinGoiY(
                UUID.randomUUID(), UUID.randomUUID(), "Căn hộ Q1 mới", "Mô tả", "ban", "can_ho",
                new BigDecimal("3500000000"), "vnd_tong", new BigDecimal("3500000000"),
                BigDecimal.ZERO, new BigDecimal("65"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "da_duyet", "hoat_dong", false
        );

        // Tin cần gợi ý 2: Biệt thự tại Củ Chi -> Chỉ khớp với tin đã xem cách đây 45 ngày (đã quá hạn 30 ngày)
        ThongTinTinGoiY tinBietThuCuChi = new ThongTinTinGoiY(
                UUID.randomUUID(), UUID.randomUUID(), "Biệt thự Củ Chi mới", "Mô tả", "ban", "biet_thu",
                new BigDecimal("6000000000"), "vnd_tong", new BigDecimal("6000000000"),
                BigDecimal.ZERO, new BigDecimal("250"), "Địa chỉ", "Cu Chi",
                new ViTriToaDo(new BigDecimal("11.0000"), new BigDecimal("106.5000")), now, null, "da_duyet", "hoat_dong", false
        );

        BigDecimal scoreCanHoQ1 = thuatToan.tinhDiemTongHop(tinCanHoQ1, ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, lichSu, mapTin, now);
        BigDecimal scoreBietThuCuChi = thuatToan.tinhDiemTongHop(tinBietThuCuChi, ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, lichSu, mapTin, now);

        // Tin Căn hộ Q1 phải có điểm sở thích 100đ, còn Biệt thự Củ Chi có điểm sở thích 0đ vì tương tác cũ 45 ngày đã bị loại
        assertTrue(scoreCanHoQ1.compareTo(scoreBietThuCuChi) > 0,
                "Tin căn hộ Q1 (trong 30 ngày) phải có điểm vượt trội so với biệt thự Củ Chi (tương tác > 30 ngày)");

        // Kiểm tra trực tiếp hàm locLichSuTrongCuaSo30Ngay
        List<LichSuXem> lichSuLoc = thuatToan.locLichSuTrongCuaSo30Ngay(lichSu, now);
        assertEquals(1, lichSuLoc.size());
        assertEquals(tinTrong30NgayId, lichSuLoc.getFirst().tinId());
    }

    @Test
    @DisplayName("Điểm nhóm: Tính hồ sơ tỷ lệ nhóm loại BĐS và khu vực chính xác từ lịch sử 30 ngày")
    void testDiemNhomSoThich() {
        UUID userId = UUID.randomUUID();
        UUID tin1Id = UUID.randomUUID();
        UUID tin2Id = UUID.randomUUID();

        // 2 tin xem trong 30 ngày: 1 Căn hộ Q1, 1 Căn hộ Bình Thạnh
        ThongTinTinGoiY tin1 = new ThongTinTinGoiY(
                tin1Id, UUID.randomUUID(), "Tin 1", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now.minus(5, ChronoUnit.DAYS),
                null, "da_duyet", "hoat_dong", false
        );
        ThongTinTinGoiY tin2 = new ThongTinTinGoiY(
                tin2Id, UUID.randomUUID(), "Tin 2", "Mô tả", "ban", "can_ho",
                new BigDecimal("3000000000"), "vnd_tong", new BigDecimal("3000000000"),
                BigDecimal.ZERO, new BigDecimal("60"), "Địa chỉ", "Binh Thanh",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now.minus(2, ChronoUnit.DAYS),
                null, "da_duyet", "hoat_dong", false
        );

        List<LichSuXem> lichSu = List.of(
                new LichSuXem(UUID.randomUUID(), userId, tin1Id, now.minus(5, ChronoUnit.DAYS)),
                new LichSuXem(UUID.randomUUID(), userId, tin2Id, now.minus(2, ChronoUnit.DAYS))
        );
        Map<UUID, ThongTinTinGoiY> mapTin = Map.of(tin1Id, tin1, tin2Id, tin2);

        ThuatToanTinhDiemGoiY.DiemNhomSoThich diemNhom = thuatToan.tinhDiemNhomSoThich(lichSu, mapTin, now);
        assertTrue(diemNhom.coLichSu());
        assertEquals(2, diemNhom.tongSoTinHopLeTrong30Ngay());
        // 100% can_ho
        assertEquals(1.0, diemNhom.tiLeTheoLoaiBds().get("can_ho"), 0.001);
        // 50% Quan 1, 50% Binh Thanh
        assertEquals(0.5, diemNhom.tiLeTheoKhuVuc().get("quan 1"), 0.001);
        assertEquals(0.5, diemNhom.tiLeTheoKhuVuc().get("binh thanh"), 0.001);

        // Tin Căn hộ Quận 1: 100% loai * 60 + 50% khu vuc * 40 = 60 + 20 = 80đ
        BigDecimal diemTinQ1 = diemNhom.tinhDiemChoTin(tin1);
        assertEquals(new BigDecimal("80.00"), diemTinQ1);
    }

    @Test
    @DisplayName("Độ mới: Trong cửa sổ 30 ngày giảm dần, sau 30 ngày giữ sàn tối thiểu 10đ")
    void testDiemDoMoi_TrongCuaSo30NgayVaSau30Ngay() {
        // Tin đăng ngay thời điểm now: 100đ
        BigDecimal diemMoi0Ngay = thuatToan.tinhDiemDoMoi(now, now);
        assertEquals(new BigDecimal("100.00"), diemMoi0Ngay);

        // Tin đăng 10 ngày trước: 100 - (10 * 2) = 80đ
        BigDecimal diemMoi10Ngay = thuatToan.tinhDiemDoMoi(now.minus(10, ChronoUnit.DAYS), now);
        assertEquals(new BigDecimal("80.00"), diemMoi10Ngay);

        // Tin đăng 30 ngày trước: giữ sàn tối thiểu 10đ
        BigDecimal diemMoi30Ngay = thuatToan.tinhDiemDoMoi(now.minus(30, ChronoUnit.DAYS), now);
        assertEquals(new BigDecimal("10.00"), diemMoi30Ngay);

        // Tin đăng 60 ngày trước: giữ sàn tối thiểu 10đ
        BigDecimal diemMoi60Ngay = thuatToan.tinhDiemDoMoi(now.minus(60, ChronoUnit.DAYS), now);
        assertEquals(new BigDecimal("10.00"), diemMoi60Ngay);
    }
}
