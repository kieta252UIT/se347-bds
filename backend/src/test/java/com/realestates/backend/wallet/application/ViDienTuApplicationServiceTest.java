package com.realestates.backend.wallet.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.dto.ViDienTuResponse;
import com.realestates.backend.wallet.infrastructure.InMemoryGiaoDichViRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryViDienTuRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViDienTuApplicationServiceTest {

    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private InMemoryViDienTuRepository viDienTuRepository;
    private InMemoryGiaoDichViRepository giaoDichViRepository;
    private Clock fixedClock;
    private ViDienTuApplicationService viDienTuApplicationService;

    private UUID userId;
    private PrincipalPhienHienTai principal;

    @BeforeEach
    void setUp() {
        permissionAndCsrfService = mock(CurrentPermissionAndCsrfService.class);
        viDienTuRepository = new InMemoryViDienTuRepository();
        giaoDichViRepository = new InMemoryGiaoDichViRepository();
        fixedClock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);

        viDienTuApplicationService = new ViDienTuApplicationService(
                permissionAndCsrfService,
                viDienTuRepository,
                giaoDichViRepository,
                fixedClock
        );

        userId = UUID.randomUUID();
        principal = new PrincipalPhienHienTai(
                UUID.randomUUID(),
                userId,
                "cookie_hash",
                "test@example.com",
                "Nguyễn Văn A",
                "0901234567",
                VaiTroTaiKhoan.NGUOI_DUNG,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L
        );
    }

    @Test
    void testLayViCuaToi_KhoiTaoTuDongNeuChuaCo() {
        when(permissionAndCsrfService.kiemTraPhienVaQuyenHienTai("valid-cookie")).thenReturn(principal);

        ViDienTuResponse response = viDienTuApplicationService.layViCuaToi("valid-cookie");

        assertNotNull(response);
        assertEquals(userId, response.taiKhoanId());
        assertEquals(BigDecimal.ZERO, response.soDuVnd());
        assertTrue(response.giaoDichGanDay().isEmpty());
    }

    @Test
    void testKhoiTaoSoDuThuNhatQuan_VaKiemToanKhopSoDu() {
        BigDecimal soTienKhoiTao = new BigDecimal("200000");
        ViDienTu vi = viDienTuApplicationService.khoiTaoSoDuThuNhatQuan(userId, soTienKhoiTao, "Số dư ban đầu để kiểm thử");

        assertEquals(soTienKhoiTao, vi.soDuVnd());

        // Kiểm toán số dư khớp sổ giao dịch
        boolean nhatQuan = viDienTuApplicationService.kiemToanNhatQuanSoDu(vi.id());
        assertTrue(nhatQuan, "Số dư ví phải bằng tổng các bút toán trong sổ cái");

        // Gọi lại lấy ví qua cookie
        when(permissionAndCsrfService.kiemTraPhienVaQuyenHienTai("valid-cookie")).thenReturn(principal);
        ViDienTuResponse response = viDienTuApplicationService.layViCuaToi("valid-cookie");

        assertEquals(soTienKhoiTao, response.soDuVnd());
        assertEquals(1, response.giaoDichGanDay().size());
        assertEquals("cong", response.giaoDichGanDay().getFirst().chieuBienDong());
        assertEquals(soTienKhoiTao, response.giaoDichGanDay().getFirst().soTien());
    }

    @Test
    void testKhoiTaoSoDuThuQuaCookie() {
        when(permissionAndCsrfService.kiemTraPhienVaQuyenHienTai("valid-cookie")).thenReturn(principal);
        ViDienTuResponse response = viDienTuApplicationService.khoiTaoSoDuThu("valid-cookie", new BigDecimal("500000"), "Nạp số dư thử nghiệm");

        assertEquals(new BigDecimal("500000"), response.soDuVnd());
        assertEquals(1, response.giaoDichGanDay().size());
        assertEquals("khoi_tao_thu_nghiem", response.giaoDichGanDay().getFirst().loai());
        assertEquals("cong", response.giaoDichGanDay().getFirst().chieuBienDong());
        assertEquals(new BigDecimal("500000"), response.giaoDichGanDay().getFirst().soTien());

        boolean nhatQuan = viDienTuApplicationService.kiemToanNhatQuanSoDu(response.viId());
        assertTrue(nhatQuan, "Số dư ví sau khi khởi tạo qua cookie phải nhất quán");
    }
}
