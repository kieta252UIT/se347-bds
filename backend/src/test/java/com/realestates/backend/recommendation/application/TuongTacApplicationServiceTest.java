package com.realestates.backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.LichSuXemRepository;
import com.realestates.backend.recommendation.dto.GhiNhanTuongTacRequest;
import com.realestates.backend.recommendation.infrastructure.InMemoryLichSuXemRepository;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TuongTacApplicationServiceTest {

    private CurrentPermissionAndCsrfService permissionService;
    private LichSuXemRepository lichSuXemRepository;
    private KiemTraTinChoGoiYPort kiemTraTinPort;
    private Clock fixedClock;

    private TuongTacApplicationService tuongTacService;

    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private UUID userId;
    private PrincipalPhienHienTai principal;

    @BeforeEach
    void setUp() {
        permissionService = mock(CurrentPermissionAndCsrfService.class);
        lichSuXemRepository = spy(new InMemoryLichSuXemRepository());
        kiemTraTinPort = mock(KiemTraTinChoGoiYPort.class);
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        tuongTacService = new TuongTacApplicationService(
                permissionService,
                lichSuXemRepository,
                kiemTraTinPort,
                fixedClock
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
    @DisplayName("Quyền Khách: Xem tin công khai hợp lệ -> không lưu vào CSDL lich_su_xem")
    void testTuongTac_KhachVangLai_TinCongKhai_KhongGhiDB() {
        UUID tinId = UUID.randomUUID();
        when(kiemTraTinPort.kiemTraTinHopLeCongKhai(tinId, now)).thenReturn(true);
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null)).thenReturn(Optional.empty());

        GhiNhanTuongTacRequest request = new GhiNhanTuongTacRequest(tinId, "xem");
        boolean ketQua = tuongTacService.ghiNhanTuongTac(null, request);

        assertFalse(ketQua, "Khách xem tin công khai phải trả về false");
        verify(lichSuXemRepository, never()).save(any(LichSuXem.class));
    }

    @Test
    @DisplayName("Quyền Người dùng: Xem tin công khai hợp lệ -> lưu lịch sử tương tác vào CSDL")
    void testTuongTac_NguoiDungDangNhap_TinCongKhai_GhiNhanVaoDB() {
        UUID tinId = UUID.randomUUID();
        when(kiemTraTinPort.kiemTraTinHopLeCongKhai(tinId, now)).thenReturn(true);
        when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai("valid-cookie"))
                .thenReturn(Optional.of(principal));

        GhiNhanTuongTacRequest request = new GhiNhanTuongTacRequest(tinId, "xem");
        boolean ketQua = tuongTacService.ghiNhanTuongTac("valid-cookie", request);

        assertTrue(ketQua, "Người dùng đăng nhập phải trả về true");
        verify(lichSuXemRepository, times(1)).save(any(LichSuXem.class));

        var lichSu = lichSuXemRepository.findByTaiKhoanId(userId);
        assertEquals(1, lichSu.size());
        assertEquals(tinId, lichSu.getFirst().tinId());
        assertEquals(userId, lichSu.getFirst().taiKhoanId());
        assertEquals(now, lichSu.getFirst().thoiDiemXem());
    }

    @Test
    @DisplayName("BR-01: Tin không công khai hợp lệ (bị gỡ hoặc chủ bị khóa) -> ném ListingNotFoundException")
    void testTuongTac_TinKhongCongKhaiHoacKhongTonTai_NemListingNotFoundException() {
        UUID tinBiGoId = UUID.randomUUID();
        // Tin không hợp lệ BR-01
        when(kiemTraTinPort.kiemTraTinHopLeCongKhai(tinBiGoId, now)).thenReturn(false);

        GhiNhanTuongTacRequest request = new GhiNhanTuongTacRequest(tinBiGoId, "xem");

        assertThrows(ListingNotFoundException.class, () ->
                tuongTacService.ghiNhanTuongTac("valid-cookie", request)
        );

        verify(lichSuXemRepository, never()).save(any(LichSuXem.class));
    }

    @Test
    @DisplayName("Xử lý lỗi: Request null ném NullPointerException")
    void testTuongTac_RequestNull_NemNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                tuongTacService.ghiNhanTuongTac("valid-cookie", null)
        );
    }
}
