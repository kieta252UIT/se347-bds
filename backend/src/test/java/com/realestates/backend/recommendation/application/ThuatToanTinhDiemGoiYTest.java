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
}
