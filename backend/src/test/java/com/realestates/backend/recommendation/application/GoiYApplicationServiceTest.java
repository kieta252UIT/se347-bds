package com.realestates.backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.dto.GoiYQueryRequest;
import com.realestates.backend.recommendation.dto.GoiYResponse;
import com.realestates.backend.recommendation.infrastructure.InMemoryDiemGoiYRepository;
import com.realestates.backend.recommendation.infrastructure.InMemoryKiemTraTinChoGoiYAdapter;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoiYApplicationServiceTest {

    private CurrentPermissionAndCsrfService permissionService;
    private InMemoryKiemTraTinChoGoiYAdapter kiemTraTinAdapter;
    private InMemoryDiemGoiYRepository diemGoiYRepository;
    private ThuatToanTinhDiemGoiY thuatToanTinhDiem;
    private Clock fixedClock;
    private Random random;

    private GoiYApplicationService goiYApplicationService;

    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private UUID userId;
    private PrincipalPhienHienTai principal;

    @BeforeEach
    void setUp() {
        permissionService = mock(CurrentPermissionAndCsrfService.class);
        kiemTraTinAdapter = new InMemoryKiemTraTinChoGoiYAdapter();
        kiemTraTinAdapter.clear();
        diemGoiYRepository = new InMemoryDiemGoiYRepository();
        thuatToanTinhDiem = new ThuatToanTinhDiemGoiY();
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        random = new Random(42); // seed cố định để tái lập kiểm thử

        goiYApplicationService = new GoiYApplicationService(
                permissionService,
                kiemTraTinAdapter,
                diemGoiYRepository,
                thuatToanTinhDiem,
                fixedClock,
                random
        );

        userId = UUID.randomUUID();
        principal = new PrincipalPhienHienTai(
                UUID.randomUUID(),
                userId,
                "cookie_hash",
                "user@example.com",
                "Nguyen Van A",
                "0901234567",
                VaiTroTaiKhoan.NGUOI_DUNG,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L
        );
    }

    @Test
    @DisplayName("AC-03.1: Ngữ cảnh C1 - Khách không có GPS dùng vị trí mặc định TP.HCM và xếp hạng gần trước mới sau")
    void testGoiY_NguCanhC1_KhachKhongGps() {
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null)).thenReturn(Optional.empty());

        // Tin 1: Gần trung tâm TP.HCM (Quận 1)
        UUID tin1Id = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tin1Id, UUID.randomUUID(), "Căn hộ Q1", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                new ViTriToaDo(new BigDecimal("10.7800"), new BigDecimal("106.7020")),
                now, null, "da_duyet", "hoat_dong", false
        ));

        // Tin 2: Xa trung tâm TP.HCM (Cần Giờ)
        UUID tin2Id = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tin2Id, UUID.randomUUID(), "Đất Cần Giờ", "Mô tả", "ban", "dat_nen",
                new BigDecimal("1000000000"), "vnd_tong", new BigDecimal("1000000000"),
                BigDecimal.ZERO, new BigDecimal("100"), "Địa chỉ", "Can Gio",
                new ViTriToaDo(new BigDecimal("10.4000"), new BigDecimal("106.9000")),
                now, null, "da_duyet", "hoat_dong", false
        ));

        GoiYResponse res = goiYApplicationService.layGoiY(null, new GoiYQueryRequest(null, null, null, null, null, null, null, 10));

        assertNotNull(res);
        assertEquals(GoiYApplicationService.NGU_CANH_C1_KHACH_KHONG_GPS, res.nguCanh());
        assertTrue(res.dungViTriMacDinh());
        assertEquals(2, res.tongSoTuNhien());
        // Tin 1 gần hơn phải đứng đầu
        assertEquals(tin1Id, res.goiYTuNhien().getFirst().tinId());
    }

    @Test
    @DisplayName("AC-03.1: Ngữ cảnh C2 - Khách có GPS dùng tọa độ thiết bị gửi lên")
    void testGoiY_NguCanhC2_KhachCoGps() {
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null)).thenReturn(Optional.empty());

        // Thiết bị ở Thủ Đức: 10.8500, 106.7700
        BigDecimal viDoGps = new BigDecimal("10.8500");
        BigDecimal kinhDoGps = new BigDecimal("106.7700");

        UUID tinThuDucId = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinThuDucId, UUID.randomUUID(), "Nhà Thủ Đức", "Mô tả", "ban", "nha_pho",
                new BigDecimal("4000000000"), "vnd_tong", new BigDecimal("4000000000"),
                BigDecimal.ZERO, new BigDecimal("80"), "Địa chỉ", "Thu Duc",
                new ViTriToaDo(new BigDecimal("10.8510"), new BigDecimal("106.7710")),
                now, null, "da_duyet", "hoat_dong", false
        ));

        UUID tinQ1Id = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinQ1Id, UUID.randomUUID(), "Căn hộ Q1", "Mô tả", "ban", "can_ho",
                new BigDecimal("5000000000"), "vnd_tong", new BigDecimal("5000000000"),
                BigDecimal.ZERO, new BigDecimal("70"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM,
                now, null, "da_duyet", "hoat_dong", false
        ));

        GoiYResponse res = goiYApplicationService.layGoiY(
                null,
                new GoiYQueryRequest(viDoGps, kinhDoGps, null, null, null, null, null, 10)
        );

        assertEquals(GoiYApplicationService.NGU_CANH_C2_KHACH_CO_GPS, res.nguCanh());
        assertFalse(res.dungViTriMacDinh());
        // Tin Thủ Đức gần GPS hơn phải xếp đầu
        assertEquals(tinThuDucId, res.goiYTuNhien().getFirst().tinId());
    }

    @Test
    @DisplayName("AC-03.1 & AC-03.2: Ngữ cảnh C4 - Người dùng có GPS và có điểm nền tính sẵn")
    void testGoiY_NguCanhC4_NguoiDungCoGps_DungDiemNen() {
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai("valid-cookie")).thenReturn(Optional.of(principal));

        UUID tinAId = UUID.randomUUID();
        UUID tinBId = UUID.randomUUID();

        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinAId, UUID.randomUUID(), "Tin A", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "da_duyet", "hoat_dong", false
        ));
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinBId, UUID.randomUUID(), "Tin B", "Mô tả", "ban", "can_ho",
                new BigDecimal("3000000000"), "vnd_tong", new BigDecimal("3000000000"),
                BigDecimal.ZERO, new BigDecimal("60"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "da_duyet", "hoat_dong", false
        ));

        // Điểm nền tính sẵn: Tin B có điểm 95.5, Tin A có điểm 70.0
        diemGoiYRepository.save(new DiemGoiY(userId, tinAId, new BigDecimal("70.0000"), now));
        diemGoiYRepository.save(new DiemGoiY(userId, tinBId, new BigDecimal("95.5000"), now));

        GoiYResponse res = goiYApplicationService.layGoiY(
                "valid-cookie",
                new GoiYQueryRequest(new BigDecimal("10.7769"), new BigDecimal("106.7009"), null, null, null, null, null, 10)
        );

        assertEquals(GoiYApplicationService.NGU_CANH_C4_NGUOI_DUNG_CO_GPS, res.nguCanh());
        assertFalse(res.dungViTriMacDinh());
        // Tin B điểm cao hơn phải xếp đầu
        assertEquals(tinBId, res.goiYTuNhien().getFirst().tinId());
    }

    @Test
    @DisplayName("BR-01: Điểm nền cũ không làm lộ tin đã bị gỡ vi phạm hoặc chủ bị khóa")
    void testBR01_LoaiBoTinKhongHopLeDuCoDiemNenCao() {
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai("valid-cookie")).thenReturn(Optional.of(principal));

        UUID tinViPhamId = UUID.randomUUID();
        // Tin bị gỡ do vi phạm: trangThaiTin = 'bi_go_vi_pham'
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinViPhamId, UUID.randomUUID(), "Tin vi phạm", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "bi_go_vi_pham", "hoat_dong", false
        ));

        // Dù có điểm nền rất cao 99.9đ
        diemGoiYRepository.save(new DiemGoiY(userId, tinViPhamId, new BigDecimal("99.9000"), now));

        GoiYResponse res = goiYApplicationService.layGoiY("valid-cookie", null);

        // Tin vi phạm tuyệt đối không được xuất hiện
        assertTrue(res.goiYTuNhien().stream().noneMatch(t -> t.tinId().equals(tinViPhamId)));
    }

    @Test
    @DisplayName("BR-06: Tin có gói tài trợ hiệu lực bị loại khỏi tự nhiên và chỉ xuất hiện ở khu vực Được tài trợ")
    void testBR06_PhanTachKhuVucTuNhienVaTaiTro() {
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null)).thenReturn(Optional.empty());

        UUID tinThuongId = UUID.randomUUID();
        UUID tinTaiTroId = UUID.randomUUID();

        // Tin thường không có gói
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinThuongId, UUID.randomUUID(), "Tin thường", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "da_duyet", "hoat_dong", false
        ));

        // Tin có gói tài trợ đang hiệu lực
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinTaiTroId, UUID.randomUUID(), "Tin VIP tài trợ", "Mô tả", "ban", "can_ho",
                new BigDecimal("5000000000"), "vnd_tong", new BigDecimal("5000000000"),
                BigDecimal.ZERO, new BigDecimal("90"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "da_duyet", "hoat_dong", true
        ));

        GoiYResponse res = goiYApplicationService.layGoiY(null, null);

        // Tự nhiên chỉ có tin thường, loại bỏ tin tài trợ
        assertEquals(1, res.tongSoTuNhien());
        assertEquals(tinThuongId, res.goiYTuNhien().getFirst().tinId());
        assertFalse(res.goiYTuNhien().getFirst().duocTaiTro());

        // Được tài trợ có tin tài trợ, gắn nhãn duocTaiTro = true
        assertEquals(1, res.tongSoTaiTro());
        assertEquals(tinTaiTroId, res.duocTaiTro().getFirst().tinId());
        assertTrue(res.duocTaiTro().getFirst().duocTaiTro());
    }
}
