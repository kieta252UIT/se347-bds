package com.realestates.backend.wallet.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.wallet.domain.GiaoDichVi;
import com.realestates.backend.wallet.domain.GoiDayTin;
import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.dto.KetQuaMuaGoiResponse;
import com.realestates.backend.wallet.dto.ThongTinMuaGoiResponse;
import com.realestates.backend.wallet.dto.YeuCauMuaGoiRequest;
import com.realestates.backend.wallet.exception.InsufficientBalanceException;
import com.realestates.backend.wallet.exception.ListingNotEligibleForPackageException;
import com.realestates.backend.wallet.exception.PackageAccumulationRequiredException;
import com.realestates.backend.wallet.exception.PurchaseIntentConflictException;
import com.realestates.backend.wallet.infrastructure.InMemoryGiaoDichViRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryGoiDayTinRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryKiemTraTinAdapter;
import com.realestates.backend.wallet.infrastructure.InMemoryLanMuaGoiRepository;
import com.realestates.backend.wallet.infrastructure.InMemoryViDienTuRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class QuanLyGoiDayTinApplicationServiceTest {

    private InMemoryViDienTuRepository viDienTuRepository;
    private InMemoryGiaoDichViRepository giaoDichViRepository;
    private InMemoryGoiDayTinRepository goiDayTinRepository;
    private InMemoryLanMuaGoiRepository lanMuaGoiRepository;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private InMemoryKiemTraTinAdapter kiemTraTinAdapter;
    private CurrentPermissionAndCsrfService permissionService;

    private QuanLyGoiDayTinApplicationService goiDayTinService;
    private ViDienTuApplicationService viDienTuService;

    private final Instant nowFixed = Instant.parse("2026-11-23T10:00:00Z");
    private Clock fixedClock;

    private UUID chuId;
    private UUID nguoiKhacId;
    private UUID tinId;
    private String cookieChinhChu;
    private String cookieNguoiKhac;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(nowFixed, ZoneOffset.UTC);

        viDienTuRepository = new InMemoryViDienTuRepository();
        giaoDichViRepository = new InMemoryGiaoDichViRepository();
        goiDayTinRepository = new InMemoryGoiDayTinRepository();
        lanMuaGoiRepository = new InMemoryLanMuaGoiRepository();
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM",
                phienXacThucRepository
        );
        kiemTraTinAdapter = new InMemoryKiemTraTinAdapter(Optional.empty());

        // Dùng CurrentPermissionAndCsrfService THẬT (không mock) để kiểm chứng truyền đúng chuId qua lớp quyền thật
        permissionService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of("https://localhost", "http://localhost:5173")
        );

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
                fixedClock,
                true
        );

        chuId = UUID.randomUUID();
        nguoiKhacId = UUID.randomUUID();
        tinId = UUID.randomUUID();

        cookieChinhChu = seedActiveUserSession(chuId, "owner@example.com", "Chủ Tin");
        cookieNguoiKhac = seedActiveUserSession(nguoiKhacId, "other@example.com", "Người Dùng Khác");

        // Khởi tạo tin chính chủ, đã duyệt
        kiemTraTinAdapter.mockTin(tinId, chuId, "da_duyet");
    }

    private String seedActiveUserSession(UUID accountId, String email, String hoTen) {
        TaiKhoan taiKhoan = TaiKhoan.taoNguoiDungDaXacMinh(
                accountId,
                UUID.randomUUID(),
                email,
                hoTen,
                "0901234567",
                nowFixed
        );
        taiKhoanRepository.save(taiKhoan);

        String rawCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                accountId,
                sessionLifecycleAdapter.hashSessionCookie(rawCookie),
                0L,
                nowFixed,
                UUID.randomUUID(),
                "access".getBytes(StandardCharsets.UTF_8),
                "refresh".getBytes(StandardCharsets.UTF_8),
                nowFixed.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return rawCookie;
    }

    @Test
    @DisplayName("Review PR #21 Điểm 1: Lớp quyền thật cho phép chính chủ xem báo giá & mua gói, nhưng từ chối người dùng khác")
    void testKiemQuyenChinhChuVaNguoiDungKhacQuaLopQuyenThat() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư chủ tin");
        viDienTuService.khoiTaoSoDuThuNhatQuan(nguoiKhacId, new BigDecimal("500000"), "Số dư người khác");

        // 1. Chính chủ xem thông tin báo giá thành công
        ThongTinMuaGoiResponse quote = goiDayTinService.layThongTinGoiVaSoDu(cookieChinhChu, tinId);
        assertEquals(tinId, quote.tinId());
        assertEquals(new BigDecimal("500000"), quote.soDuHienTaiVnd());
        assertTrue(quote.duDieuKienMua());
        assertFalse(quote.canXacNhanCongDon());

        // 2. Người dùng khác xem báo giá tin của chủ tin -> bị chặn
        ListingNotEligibleForPackageException exQuoteOther = assertThrows(
                ListingNotEligibleForPackageException.class,
                () -> goiDayTinService.layThongTinGoiVaSoDu(cookieNguoiKhac, tinId)
        );
        assertEquals("KHONG_PHAI_CHU_TIN", exQuoteOther.getMaLoi());

        // 3. Người dùng khác cố mua gói trên tin của chủ tin -> bị chặn, không trừ tiền của ai
        YeuCauMuaGoiRequest reqOther = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_OTHER_01", false);
        ListingNotEligibleForPackageException exBuyOther = assertThrows(
                ListingNotEligibleForPackageException.class,
                () -> goiDayTinService.muaGoi(cookieNguoiKhac, tinId, reqOther)
        );
        assertEquals("KHONG_PHAI_CHU_TIN", exBuyOther.getMaLoi());
        assertEquals(new BigDecimal("500000"), viDienTuRepository.findByTaiKhoanId(nguoiKhacId).orElseThrow().soDuVnd());
        assertTrue(goiDayTinRepository.findGoiChuaKetThuc(tinId).isEmpty());
    }

    @Test
    @DisplayName("AC-11.5: Mua gói 7 ngày thành công khi tin chưa có gói, số dư ví trừ đúng 49.000đ")
    void testMuaGoiThanhCong_KhiTinChuaCoGoi() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_001", false);
        KetQuaMuaGoiResponse res = goiDayTinService.muaGoi(cookieChinhChu, tinId, request);

        assertTrue(res.thanhCong());
        assertEquals(new BigDecimal("451000"), res.soDuConLaiVnd());
        assertNotNull(res.goi());
        assertEquals("dang_hoat_dong", res.goi().trangThai());
        assertEquals(nowFixed.plus(Duration.ofDays(7)), res.goi().hetHanLuc());

        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("451000"), vi.soDuVnd());

        var danhSachLedger = giaoDichViRepository.findByViId(vi.id());
        assertEquals(2, danhSachLedger.size());
        assertTrue(viDienTuService.kiemToanNhatQuanSoDu(vi.id()));
    }

    @Test
    @DisplayName("AC-11.5: Mua gói thất bại khi số dư ví không đủ (20.000đ < 49.000đ), không trừ tiền")
    void testMuaGoiThatBai_KhiSoDuKhongDu() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("20000"), "Số dư thử ít");

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_002", false);

        InsufficientBalanceException ex = assertThrows(
                InsufficientBalanceException.class,
                () -> goiDayTinService.muaGoi(cookieChinhChu, tinId, request)
        );

        assertEquals("SO_DU_KHONG_DU", ex.getMaLoi());
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("20000"), vi.soDuVnd());
    }

    @Test
    @DisplayName("AC-11.5: Cảnh báo cộng dồn khi tin đang có gói hiệu lực và chưa gửi cờ xác nhận")
    void testMuaGoiCanhBaoCongDon_KhiChuaDongYCongDon() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        Instant hetHanHienTai = nowFixed.plus(Duration.ofDays(5));
        GoiDayTin goiCu = new GoiDayTin(UUID.randomUUID(), tinId, "dang_hoat_dong", Duration.ZERO, hetHanHienTai);
        goiDayTinRepository.save(goiCu);

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_30_NGAY", "INTENT_003", false);

        PackageAccumulationRequiredException ex = assertThrows(
                PackageAccumulationRequiredException.class,
                () -> goiDayTinService.muaGoi(cookieChinhChu, tinId, request)
        );

        assertEquals("CAN_XAC_NHAN_CONG_DON", ex.getMaLoi());
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("500000"), vi.soDuVnd());
    }

    @Test
    @DisplayName("AC-11.5 & AC-11.6: Cộng dồn thời lượng chính xác khi có xác nhận dongYCongDon = true")
    void testMuaGoiCongDonThanhCong_KhiDongYCongDon() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        Instant hetHanHienTai = nowFixed.plus(Duration.ofDays(5));
        GoiDayTin goiCu = new GoiDayTin(UUID.randomUUID(), tinId, "dang_hoat_dong", Duration.ZERO, hetHanHienTai);
        goiDayTinRepository.save(goiCu);

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_30_NGAY", "INTENT_004", true);
        KetQuaMuaGoiResponse res = goiDayTinService.muaGoi(cookieChinhChu, tinId, request);

        assertTrue(res.thanhCong());
        assertEquals(new BigDecimal("321000"), res.soDuConLaiVnd());
        assertEquals(hetHanHienTai.plus(Duration.ofDays(30)), res.goi().hetHanLuc());

        var goiOpt = goiDayTinRepository.findGoiChuaKetThuc(tinId);
        assertTrue(goiOpt.isPresent());
        assertEquals(goiCu.id(), goiOpt.get().id());
    }

    @Test
    @DisplayName("AC-02.2 & AC-11: Tự động đóng gói cũ khi đã quá hạn thực tế và tạo gói mới")
    void testMuaGoiTuDongDongGoiHetHanThucTe() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        Instant hetHanQuaKhu = nowFixed.minus(Duration.ofHours(2));
        GoiDayTin goiQuaHan = new GoiDayTin(UUID.randomUUID(), tinId, "dang_hoat_dong", Duration.ZERO, hetHanQuaKhu);
        goiDayTinRepository.save(goiQuaHan);

        YeuCauMuaGoiRequest request = new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_005", false);
        KetQuaMuaGoiResponse res = goiDayTinService.muaGoi(cookieChinhChu, tinId, request);

        assertTrue(res.thanhCong());
        GoiDayTin goiCuCapNhat = goiDayTinRepository.findById(goiQuaHan.id()).orElseThrow();
        assertEquals("het_han", goiCuCapNhat.trangThai());

        assertNotNull(res.goi());
        assertEquals("dang_hoat_dong", res.goi().trangThai());
        assertEquals(nowFixed.plus(Duration.ofDays(7)), res.goi().hetHanLuc());
    }

    @Test
    @DisplayName("AC-11.5 & Review PR #21 Điểm 3: Cùng mã và nội dung trả kết quả ban đầu dù số dư ví đã đổi; cùng mã khác nội dung trả 409 XUNG_DOT_MA_Y_DINH")
    void testIdempotency_TraKetQuaGocKhiSoDuDaDoi_VaChan409KhiKhacNoiDung() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        String maYDinh = "INTENT_IDEMPOTENT_TEST";
        YeuCauMuaGoiRequest req1 = new YeuCauMuaGoiRequest("GOI_7_NGAY", maYDinh, false);
        KetQuaMuaGoiResponse res1 = goiDayTinService.muaGoi(cookieChinhChu, tinId, req1);
        assertTrue(res1.thanhCong());
        assertEquals(new BigDecimal("451000"), res1.soDuConLaiVnd());
        Instant hetHanBanDau = res1.goi().hetHanLuc();

        // Giả sử sau lần mua 1, ví người dùng có biến động khác (cộng thêm 100.000đ làm số dư hiện tại thành 551.000đ)
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("100000"), "Biến động sau lần mua 1");
        assertEquals(new BigDecimal("551000"), viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow().soDuVnd());

        // 1. Gửi lại cùng mã ý định và cùng nội dung -> phải trả đúng kết quả ban đầu (soDuConLaiVnd = 451000 tại thời điểm giao dịch gốc)
        YeuCauMuaGoiRequest req2 = new YeuCauMuaGoiRequest("7_ngay", maYDinh, false);
        KetQuaMuaGoiResponse res2 = goiDayTinService.muaGoi(cookieChinhChu, tinId, req2);
        assertTrue(res2.thanhCong());
        assertEquals(new BigDecimal("451000"), res2.soDuConLaiVnd());
        assertEquals(res1.maGiaoDich(), res2.maGiaoDich());
        assertEquals(hetHanBanDau, res2.goi().hetHanLuc());

        // Số dư thực của ví vẫn giữ nguyên 551.000đ
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        assertEquals(new BigDecimal("551000"), vi.soDuVnd());

        // 2. Cùng mã ý định nhưng khác nội dung (đổi sang GOI_30_NGAY hoặc đổi dongYCongDon = true) -> ném 409 XUNG_DOT_MA_Y_DINH
        YeuCauMuaGoiRequest reqKhacGoi = new YeuCauMuaGoiRequest("GOI_30_NGAY", maYDinh, false);
        PurchaseIntentConflictException exConflict = assertThrows(
                PurchaseIntentConflictException.class,
                () -> goiDayTinService.muaGoi(cookieChinhChu, tinId, reqKhacGoi)
        );
        assertEquals("XUNG_DOT_MA_Y_DINH", exConflict.getMaLoi());

        // Ví, sổ giao dịch và gói hoàn toàn không bị thay đổi
        assertEquals(new BigDecimal("551000"), viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow().soDuVnd());
        assertEquals(hetHanBanDau, goiDayTinRepository.findGoiChuaKetThuc(tinId).orElseThrow().hetHanLuc());
        var listTru = giaoDichViRepository.findByViId(vi.id()).stream()
                .filter(g -> "tru".equals(g.chieuBienDong()))
                .toList();
        assertEquals(1, listTru.size());
    }

    @Test
    @DisplayName("Review PR #26 Điểm 2: Replay đọc chính xác ket_qua_json định dạng jsonb của PostgreSQL (có khoảng trắng sau dấu hai chấm)")
    void testReplay_DocChinhXacKetQuaJsonDinhDangPostgresJsonbCoKhoangTrang() {
        viDienTuService.khoiTaoSoDuThuNhatQuan(chuId, new BigDecimal("500000"), "Số dư thử");

        String maYDinh = "INTENT_JSONB_WHITESPACE";
        YeuCauMuaGoiRequest req = new YeuCauMuaGoiRequest("GOI_7_NGAY", maYDinh, false);
        KetQuaMuaGoiResponse resGoc = goiDayTinService.muaGoi(cookieChinhChu, tinId, req);

        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(chuId).orElseThrow();
        GiaoDichVi gdGoc = giaoDichViRepository.findByViIdAndLoaiAndMaYDinh(vi.id(), "mua_goi", maYDinh).orElseThrow();

        // Mô phỏng PostgreSQL jsonb trả về chuỗi JSON có khoảng trắng sau dấu ':' và thứ tự key bất kỳ
        String postgresJsonbFormatted = "{ \"thoiLuongConLaiGiay\": " + resGoc.goi().thoiLuongConLaiGiay()
                + ", \"goiId\": \"" + resGoc.goi().id()
                + "\", \"lanMuaId\": \"" + resGoc.lanMuaId()
                + "\", \"trangThaiGoi\": \"dang_hoat_dong\""
                + ", \"hetHanLuc\": \"" + resGoc.goi().hetHanLuc()
                + "\", \"soDuSauVnd\": \"451000\" }";

        giaoDichViRepository.clear();
        lanMuaGoiRepository.clear();
        goiDayTinRepository.clear();

        GiaoDichVi gdTuPostgresJsonb = new GiaoDichVi(
                gdGoc.id(),
                gdGoc.viId(),
                gdGoc.loai(),
                gdGoc.trangThai(),
                gdGoc.chieuBienDong(),
                gdGoc.soTien(),
                gdGoc.soDuTruoc(),
                gdGoc.soDuSau(),
                gdGoc.maYDinh(),
                gdGoc.duLieuBam(),
                postgresJsonbFormatted,
                gdGoc.giaoDichGocId(),
                gdGoc.maThuTienCong(),
                gdGoc.thoiDiemGhiNhan(),
                gdGoc.ghiChu()
        );
        giaoDichViRepository.save(gdTuPostgresJsonb);

        // Ngay cả khi goiDayTinRepository đã đổi trạng thái về sau, Replay phải khôi phục đúng 100% từ ket_qua_json jsonb
        KetQuaMuaGoiResponse resReplay = goiDayTinService.muaGoi(cookieChinhChu, tinId, req);
        assertEquals(resGoc.maGiaoDich(), resReplay.maGiaoDich());
        assertEquals(resGoc.lanMuaId(), resReplay.lanMuaId());
        assertEquals(new BigDecimal("451000"), resReplay.soDuConLaiVnd());
        assertEquals(resGoc.goi().id(), resReplay.goi().id());
        assertEquals("dang_hoat_dong", resReplay.goi().trangThai());
        assertEquals(resGoc.goi().hetHanLuc(), resReplay.goi().hetHanLuc());
        assertEquals(resGoc.goi().thoiLuongConLaiGiay(), resReplay.goi().thoiLuongConLaiGiay());
    }

    @Test
    @DisplayName("Review PR #26 Điểm 3: Thứ tự khóa đúng tai_khoan -> phien_xac_thuc -> vi_dien_tu -> tin_bat_dong_san -> goi_day_tin và InMemory chặn trùng unique")
    void testThuTuKhoaVaInMemoryUniqueConstraints() {
        List<String> lockSteps = new ArrayList<>();

        InMemoryTaiKhoanRepository trackingTaiKhoanRepo = new InMemoryTaiKhoanRepository() {
            @Override
            public Optional<TaiKhoan> findByIdForUpdate(UUID id) {
                lockSteps.add("1:tai_khoan");
                return super.findByIdForUpdate(id);
            }
        };
        InMemoryPhienXacThucRepository trackingPhienRepo = new InMemoryPhienXacThucRepository() {
            @Override
            public Optional<PhienXacThuc> findByIdForUpdate(UUID id) {
                lockSteps.add("2:phien_xac_thuc");
                return super.findByIdForUpdate(id);
            }
        };
        InMemoryViDienTuRepository trackingViRepo = new InMemoryViDienTuRepository() {
            @Override
            public synchronized Optional<ViDienTu> findByTaiKhoanIdForUpdate(UUID taiKhoanId) {
                lockSteps.add("4:vi_dien_tu");
                return super.findByTaiKhoanIdForUpdate(taiKhoanId);
            }
        };
        InMemoryKiemTraTinAdapter trackingTinAdapter = new InMemoryKiemTraTinAdapter(Optional.empty()) {
            @Override
            public Optional<ThongTinTinKiemTra> khoaVaDocThongTinTin(UUID id) {
                lockSteps.add("6:tin_bat_dong_san");
                return super.khoaVaDocThongTinTin(id);
            }
        };
        InMemoryGoiDayTinRepository trackingGoiRepo = new InMemoryGoiDayTinRepository() {
            @Override
            public synchronized Optional<GoiDayTin> findGoiChuaKetThucForUpdate(UUID id) {
                lockSteps.add("8:goi_day_tin");
                return super.findGoiChuaKetThucForUpdate(id);
            }
        };

        DefaultAuthBrowserSessionLifecycleAdapter trackingSessionAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM",
                trackingPhienRepo
        );
        CurrentPermissionAndCsrfService trackingPermService = new CurrentPermissionAndCsrfService(
                trackingTaiKhoanRepo,
                trackingPhienRepo,
                trackingSessionAdapter,
                List.of("https://localhost")
        );
        QuanLyGoiDayTinApplicationService trackingGoiService = new QuanLyGoiDayTinApplicationService(
                trackingPermService,
                trackingTaiKhoanRepo,
                trackingTinAdapter,
                trackingViRepo,
                giaoDichViRepository,
                trackingGoiRepo,
                lanMuaGoiRepository,
                fixedClock
        );

        TaiKhoan tk = TaiKhoan.taoNguoiDungDaXacMinh(chuId, UUID.randomUUID(), "lock@example.com", "Lock Owner", "0901234567", nowFixed);
        trackingTaiKhoanRepo.save(tk);
        String rawCookie = trackingSessionAdapter.generateRawSessionCookie();
        trackingPhienRepo.save(PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                chuId,
                trackingSessionAdapter.hashSessionCookie(rawCookie),
                0L,
                nowFixed,
                UUID.randomUUID(),
                "a".getBytes(StandardCharsets.UTF_8),
                "r".getBytes(StandardCharsets.UTF_8),
                nowFixed.plusSeconds(3600),
                "v1"
        ));
        trackingTinAdapter.mockTin(tinId, chuId, "da_duyet");
        trackingViRepo.save(new ViDienTu(UUID.randomUUID(), chuId, new BigDecimal("500000"), nowFixed, nowFixed));

        lockSteps.clear();
        trackingGoiService.muaGoi(rawCookie, tinId, new YeuCauMuaGoiRequest("GOI_7_NGAY", "INTENT_LOCK_ORDER", false));

        assertEquals(
                List.of("1:tai_khoan", "2:phien_xac_thuc", "4:vi_dien_tu", "6:tin_bat_dong_san", "8:goi_day_tin"),
                lockSteps,
                "Thứ tự khóa phải tuân thủ tuyệt đối tai_khoan -> phien_xac_thuc -> vi_dien_tu -> tin_bat_dong_san -> goi_day_tin"
        );

        // Kiểm tra InMemoryGoiDayTinRepository chặn lưu gói chưa kết thúc thứ 2 trên cùng tin
        assertThrows(
                DuplicateKeyException.class,
                () -> trackingGoiRepo.save(GoiDayTin.taoMoiDangHoatDong(tinId, Duration.ofDays(7), nowFixed))
        );

        // Kiểm tra InMemoryViDienTuRepository chặn lưu ví thứ 2 cho cùng tài khoản
        assertThrows(
                DuplicateKeyException.class,
                () -> trackingViRepo.save(ViDienTu.khoiTao(chuId))
        );

        // Kiểm tra InMemoryGiaoDichViRepository chặn lưu bút toán trùng (vi_id, loai, ma_y_dinh)
        ViDienTu viHienTai = trackingViRepo.findByTaiKhoanId(chuId).orElseThrow();
        assertThrows(
                DuplicateKeyException.class,
                () -> giaoDichViRepository.save(GiaoDichVi.taoButToanTruTien(
                        viHienTai.id(),
                        "mua_goi",
                        new BigDecimal("49000"),
                        new BigDecimal("500000"),
                        new BigDecimal("451000"),
                        "INTENT_LOCK_ORDER",
                        "Trùng mã ý định",
                        nowFixed
                ))
        );
    }
}
