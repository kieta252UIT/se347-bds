package com.realestates.backend.wallet.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.wallet.domain.GoiDayTin;
import com.realestates.backend.wallet.domain.LoaiGoiDayTin;
import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.dto.KetQuaMuaGoiResponse;
import com.realestates.backend.wallet.dto.YeuCauMuaGoiRequest;
import com.realestates.backend.wallet.exception.InsufficientBalanceException;
import com.realestates.backend.wallet.exception.PackageAccumulationRequiredException;
import com.realestates.backend.wallet.infrastructure.InMemoryGiaoDichViRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryGoiDayTinRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryKiemTraTinAdapter;
import com.realestates.backend.wallet.infrastructure.InMemoryLanMuaGoiRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryViDienTuRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class QuanLyGoiDayTinApplicationServiceTest {

    private InMemoryViDienTuRepository viDienTuRepository;
    private InMemoryGiaoDichViRepository giaoDichViRepository;
    private InMemoryGoiDayTinRepository goiDayTinRepository;
    private InMemoryLanMuaGoiRepository lanMuaGoiRepository;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryKiemTraTinAdapter kiemTraTinAdapter;
    private CurrentPermissionAndCsrfService permissionService;

    private QuanLyGoiDayTinApplicationService goiDayTinService;
    private ViDienTuApplicationService viDienTuService;

    private final Instant nowFixed = Instant.parse("2026-11-23T10:00:00Z");
    private Clock fixedClock;

    private UUID chuId;
    private UUID tinId;
    private String cookieFake;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(nowFixed, ZoneOffset.UTC);

        viDienTuRepository = new InMemoryViDienTuRepository();
        giaoDichViRepository = new InMemoryGiaoDichViRepository();
        goiDayTinRepository = new InMemoryGoiDayTinRepository();
        lanMuaGoiRepository = new InMemoryLanMuaGoiRepository();
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        kiemTraTinAdapter = new InMemoryKiemTraTinAdapter(Optional.empty());

        permissionService = Mockito.mock(CurrentPermissionAndCsrfService.class);

        goiDayTinService = new QuanLyGoiDayTinApplicationService(
                permissionService,
                taiKhoanRepository,
                kiemTraTinAdapter,
                viDienTuRepository,
                giaoDichViRepository,
                goiDayTinRepository,
                lanMuaGoiRepository,
                fixedClock
        );

        viDienTuService = new ViDienTuApplicationService(
                permissionService,
                viDienTuRepository,
                giaoDichViRepository,
                fixedClock
        );

        chuId = UUID.randomUUID();
        tinId = UUID.randomUUID();
        cookieFake = "fake-session-cookie-123";

        // Khởi tạo tài khoản hoạt động và đã xác minh email
        TaiKhoan taiKhoan = TaiKhoan.taoNguoiDungDaXacMinh(
                chuId,
                UUID.randomUUID(),
                "user@example.com",
                "Nguyen Van A",
                "0901234567",
                nowFixed
        );
        taiKhoanRepository.save(taiKhoan);

        // Giả lập quyền thành công
        PrincipalPhienHienTai principal = new PrincipalPhienHienTai(
                UUID.randomUUID(),
                chuId,
                "cookie_hash",
                "user@example.com",
                "Nguyen Van A",
                "0901234567",
                VaiTroTaiKhoan.NGUOI_DUNG,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L
        );
        when(permissionService.kiemTraQuyenThaoTac(eq(cookieFake), any(), any(), any())).thenReturn(principal);
        when(permissionService.kiemTraPhienVaQuyenHienTai(eq(cookieFake))).thenReturn(principal);

        // Khởi tạo tin chính chủ, đã duyệt
        kiemTraTinAdapter.mockTin(tinId, chuId, "da_duyet");
    }

    @Test
    @DisplayName("AC-11.5: Mua gói 7 ngày thành công khi tin chưa có gói, số dư ví trừ đúng 49.000đ")
    void testMuaGoiThanhCong_KhiTinChuaCoGoi() {
        // Cấp số dư thử nghiệm minh bạch có ghi chép sổ giao dịch: 500.000 VNĐ
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_001", false);
        KetQuaMuaGoiResponse res = goiDayTinService.muaGoi(cookieFake, tinId, request);

        assertTrue(res.thanhCong());
        assertEquals(new BigDecimal("451000"), res.soDuConLaiVnd());
        assertNotNull(res.goi());
        assertEquals("dang_hoat_dong", res.goi().trangThai());
        assertEquals(nowFixed.plus(Duration.ofDays(7)), res.goi().hetHanLuc());

        // Kiểm tra sổ giao dịch (Ledger)
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("451000"), vi.soDuVnd());

        var danhSachLedger = giaoDichViRepository.findByViId(vi.id());
        assertEquals(2, danhSachLedger.size()); // 1 bút toán khởi tạo số dư + 1 bút toán trừ tiền mua gói

        // Bất biến số dư ví luôn khớp 100% sổ giao dịch
        assertTrue(viDienTuService.kiemToanNhatQuanSoDu(vi.id()));
    }

    @Test
    @DisplayName("AC-11.5: Mua gói thất bại khi số dư ví không đủ (20.000đ < 49.000đ), không trừ tiền")
    void testMuaGoiThatBai_KhiSoDuKhongDu() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("20000"), "Số dư thử ít");

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_002", false);

        InsufficientBalanceException ex = assertThrows(
                InsufficientBalanceException.class,
                () -> goiDayTinService.muaGoi(cookieFake, tinId, request)
        );

        assertEquals("SO_DU_KHONG_DU", ex.getMaLoi());
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("20000"), vi.soDuVnd()); // Số dư nguyên vẹn
    }

    @Test
    @DisplayName("AC-11.5: Cảnh báo cộng dồn khi tin đang có gói hiệu lực và chưa gửi cờ xác nhận")
    void testMuaGoiCanhBaoCongDon_KhiChuaDongYCongDon() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        // Giả lập tin đã có gói 7 ngày còn 5 ngày hiệu lực
        Instant hetHanHienTai = nowFixed.plus(Duration.ofDays(5));
        GoiDayTin goiCu = new GoiDayTin(UUID.randomUUID(), tinId, "dang_hoat_dong", Duration.ZERO, hetHanHienTai);
        goiDayTinRepository.save(goiCu);

        // Mua tiếp gói 30 ngày nhưng dongYCongDon = false
        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_30_NGAY", "INTENT_003", false);

        PackageAccumulationRequiredException ex = assertThrows(
                PackageAccumulationRequiredException.class,
                () -> goiDayTinService.muaGoi(cookieFake, tinId, request)
        );

        assertEquals("GOI_DANG_CON_HIEU_LUC_CAN_XAC_NHAN", ex.getMaLoi());
        // Số dư không bị trừ
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("500000"), vi.soDuVnd());
    }

    @Test
    @DisplayName("AC-11.5 & AC-11.6: Cộng dồn thời lượng chính xác khi có xác nhận dongYCongDon = true")
    void testMuaGoiCongDonThanhCong_KhiDongYCongDon() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        // Gói cũ còn 5 ngày
        Instant hetHanHienTai = nowFixed.plus(Duration.ofDays(5));
        GoiDayTin goiCu = new GoiDayTin(UUID.randomUUID(), tinId, "dang_hoat_dong", Duration.ZERO, hetHanHienTai);
        goiDayTinRepository.save(goiCu);

        // Mua tiếp gói 30 ngày (179.000đ) với dongYCongDon = true
        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_30_NGAY", "INTENT_004", true);
        KetQuaMuaGoiResponse res = goiDayTinService.muaGoi(cookieFake, tinId, request);

        assertTrue(res.thanhCong());
        // 500.000 - 179.000 = 321.000 VNĐ
        assertEquals(new BigDecimal("321000"), res.soDuConLaiVnd());
        // Thời hạn mới = 5 ngày còn lại + 30 ngày mới = 35 ngày
        assertEquals(hetHanHienTai.plus(Duration.ofDays(30)), res.goi().hetHanLuc());

        // Duy nhất 1 gói chưa kết thúc theo tin
        var goiOpt = goiDayTinRepository.findGoiChuaKetThuc(tinId);
        assertTrue(goiOpt.isPresent());
        assertEquals(goiCu.id(), goiOpt.get().id()); // Cập nhật trên cùng 1 gói
    }

    @Test
    @DisplayName("AC-02.2 & AC-11: Tự động đóng gói cũ khi đã quá hạn thực tế và tạo gói mới")
    void testMuaGoiTuDongDongGoiHetHanThucTe() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        // Gói cũ có het_han_luc ở quá khứ (đã quá hạn 2 tiếng)
        Instant hetHanQuaKhu = nowFixed.minus(Duration.ofHours(2));
        GoiDayTin goiQuaHan = new GoiDayTin(UUID.randomUUID(), tinId, "dang_hoat_dong", Duration.ZERO, hetHanQuaKhu);
        goiDayTinRepository.save(goiQuaHan);

        // Mua gói mới không cần xác nhận cộng dồn vì gói cũ đã hết hạn thực tế
        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_005", false);
        KetQuaMuaGoiResponse res = goiDayTinService.muaGoi(cookieFake, tinId, request);

        assertTrue(res.thanhCong());
        // Gói cũ phải bị chuyển sang 'het_han'
        GoiDayTin goiCuCapNhat = goiDayTinRepository.findById(goiQuaHan.id()).orElseThrow();
        assertEquals("het_han", goiCuCapNhat.trangThai());

        // Gói mới được tạo riêng biệt
        assertNotNull(res.goi());
        assertEquals("dang_hoat_dong", res.goi().trangThai());
        assertEquals(nowFixed.plus(Duration.ofDays(7)), res.goi().hetHanLuc());
    }

    @Test
    @DisplayName("AC-11.5: Idempotency — Gửi lại cùng mã ý định chỉ trừ tiền 1 lần, replay kết quả cũ")
    void testIdempotency_CungMaYDinhChiTruTienMotLan() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        String maYDinh = "INTENT_IDEMPOTENT_TEST";
        YeuCauMuaGoiRequest req1 = new YeuCauMuaGoiRequest("GOI_7_NGAY", maYDinh, false);
        KetQuaMuaGoiResponse res1 = goiDayTinService.muaGoi(cookieFake, tinId, req1);
        assertTrue(res1.thanhCong());
        assertEquals(new BigDecimal("451000"), res1.soDuConLaiVnd());

        // Lần 2 gửi lại cùng mã ý định
        YeuCauMuaGoiRequest req2 = new YeuCauMuaGoiRequest("GOI_7_NGAY", maYDinh, false);
        KetQuaMuaGoiResponse res2 = goiDayTinService.muaGoi(cookieFake, tinId, req2);
        assertTrue(res2.thanhCong());
        // Số dư vẫn là 451.000đ, không bị trừ thành 402.000đ
        assertEquals(new BigDecimal("451000"), res2.soDuConLaiVnd());
        assertEquals(res1.maGiaoDich(), res2.maGiaoDich());

        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("451000"), vi.soDuVnd());

        // Sổ giao dịch chỉ có đúng 1 bút toán trừ tiền
        var listTru = giaoDichViRepository.findByViId(vi.id()).stream()
                .filter(g -> "tru".equals(g.chieuBienDong()))
                .toList();
        assertEquals(1, listTru.size());
    }
}
