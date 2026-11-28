package com.realestates.backend.wallet.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.wallet.application.KiemTraTinChoGoiPort.ThongTinTinKiemTra;
import com.realestates.backend.wallet.domain.GiaoDichVi;
import com.realestates.backend.wallet.domain.GiaoDichViRepository;
import com.realestates.backend.wallet.domain.GoiDayTin;
import com.realestates.backend.wallet.domain.GoiDayTinRepository;
import com.realestates.backend.wallet.domain.LanMuaGoi;
import com.realestates.backend.wallet.domain.LanMuaGoiRepository;
import com.realestates.backend.wallet.domain.LoaiGoiDayTin;
import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.domain.ViDienTuRepository;
import com.realestates.backend.wallet.dto.KetQuaMuaGoiResponse;
import com.realestates.backend.wallet.dto.ThongTinMuaGoiResponse;
import com.realestates.backend.wallet.dto.YeuCauMuaGoiRequest;
import com.realestates.backend.wallet.exception.InsufficientBalanceException;
import com.realestates.backend.wallet.exception.InvalidPurchaseIntentException;
import com.realestates.backend.wallet.exception.ListingNotEligibleForPackageException;
import com.realestates.backend.wallet.exception.PackageAccumulationRequiredException;
import com.realestates.backend.wallet.exception.PurchaseIntentConflictException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuanLyGoiDayTinApplicationService {

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final TaiKhoanRepository taiKhoanRepository;
    private final KiemTraTinChoGoiPort kiemTraTinPort;
    private final ViDienTuRepository viDienTuRepository;
    private final GiaoDichViRepository giaoDichViRepository;
    private final GoiDayTinRepository goiDayTinRepository;
    private final LanMuaGoiRepository lanMuaGoiRepository;
    private final Clock clock;

    /**
     * Bộ nhớ đệm snapshot trong JVM để phục hồi nhanh kết quả gốc khi chạy ở chế độ in-memory
     * hoặc khi đọc lại từ ket_qua_json trong PostgreSQL.
     */
    private final ConcurrentHashMap<UUID, KetQuaMuaGoiResponse> inMemoryReplayCache = new ConcurrentHashMap<>();

    @Autowired
    public QuanLyGoiDayTinApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TaiKhoanRepository taiKhoanRepository,
            KiemTraTinChoGoiPort kiemTraTinPort,
            ViDienTuRepository viDienTuRepository,
            GiaoDichViRepository giaoDichViRepository,
            GoiDayTinRepository goiDayTinRepository,
            LanMuaGoiRepository lanMuaGoiRepository
    ) {
        this(
                permissionAndCsrfService,
                taiKhoanRepository,
                kiemTraTinPort,
                viDienTuRepository,
                giaoDichViRepository,
                goiDayTinRepository,
                lanMuaGoiRepository,
                Clock.systemUTC()
        );
    }

    public QuanLyGoiDayTinApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TaiKhoanRepository taiKhoanRepository,
            KiemTraTinChoGoiPort kiemTraTinPort,
            ViDienTuRepository viDienTuRepository,
            GiaoDichViRepository giaoDichViRepository,
            GoiDayTinRepository goiDayTinRepository,
            LanMuaGoiRepository lanMuaGoiRepository,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.kiemTraTinPort = Objects.requireNonNull(kiemTraTinPort, "kiemTraTinPort không được null");
        this.viDienTuRepository = Objects.requireNonNull(viDienTuRepository, "viDienTuRepository không được null");
        this.giaoDichViRepository = Objects.requireNonNull(giaoDichViRepository, "giaoDichViRepository không được null");
        this.goiDayTinRepository = Objects.requireNonNull(goiDayTinRepository, "goiDayTinRepository không được null");
        this.lanMuaGoiRepository = Objects.requireNonNull(lanMuaGoiRepository, "lanMuaGoiRepository không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    /**
     * Lấy thông tin báo giá gói đẩy tin và số dư ví hiện tại của chủ tin.
     * Giải quyết điểm review #1 trên PR #21: Đọc chủ tin thực tế từ CSDL và truyền vào bước kiểm quyền
     * để lớp quyền thật xác thực chính chủ (cho phép chính chủ, từ chối người dùng khác).
     */
    public ThongTinMuaGoiResponse layThongTinGoiVaSoDu(String rawSessionCookie, UUID tinId) {
        Objects.requireNonNull(tinId, "tinId không được null");

        ThongTinTinKiemTra thongTinTin = kiemTraTinPort.docThongTinTin(tinId)
                .orElseThrow(ListingNotFoundException::new);

        PrincipalPhienHienTai principal = xacThucChinhChuTin(rawSessionCookie, thongTinTin.chuId());

        BigDecimal soDu = viDienTuRepository.findByTaiKhoanId(principal.getTaiKhoanId())
                .map(ViDienTu::soDuVnd)
                .orElse(BigDecimal.ZERO);

        Instant now = clock.instant();
        Optional<GoiDayTin> goiOpt = goiDayTinRepository.findGoiChuaKetThuc(tinId);

        ThongTinMuaGoiResponse.GoiHienTaiDto goiHienTaiDto;
        boolean canXacNhanCongDon = false;
        if (goiOpt.isPresent() && goiOpt.get().laDangHieuLuc(now)) {
            GoiDayTin goi = goiOpt.get();
            Long thoiLuongGiay = goi.hetHanLuc() != null && goi.hetHanLuc().isAfter(now)
                    ? goi.hetHanLuc().getEpochSecond() - now.getEpochSecond()
                    : goi.thoiLuongConLai().getSeconds();
            goiHienTaiDto = new ThongTinMuaGoiResponse.GoiHienTaiDto(
                    true,
                    goi.trangThai(),
                    goi.hetHanLuc(),
                    thoiLuongGiay
            );
            canXacNhanCongDon = "dang_hoat_dong".equals(goi.trangThai())
                    && goi.hetHanLuc() != null
                    && goi.hetHanLuc().isAfter(now);
        } else {
            goiHienTaiDto = new ThongTinMuaGoiResponse.GoiHienTaiDto(false, null, null, null);
        }

        boolean duDieuKienMua = "da_duyet".equals(thongTinTin.trangThai())
                && !(goiOpt.isPresent() && "tam_dung_cho_duyet".equals(goiOpt.get().trangThai()));

        List<ThongTinMuaGoiResponse.GoiHoTroDto> cacGoiHoTro = Arrays.stream(LoaiGoiDayTin.values())
                .map(g -> new ThongTinMuaGoiResponse.GoiHoTroDto(
                        g.name(),
                        g.getTenGoi(),
                        g.getGiaVnd(),
                        g.getSoNgay()
                ))
                .toList();

        return new ThongTinMuaGoiResponse(
                tinId,
                thongTinTin.trangThai(),
                duDieuKienMua,
                canXacNhanCongDon,
                soDu,
                goiHienTaiDto,
                cacGoiHoTro
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public KetQuaMuaGoiResponse muaGoi(String rawSessionCookie, UUID tinId, YeuCauMuaGoiRequest request) {
        return muaGoi(rawSessionCookie, tinId, null, request);
    }

    /**
     * Thực hiện mua hoặc cộng dồn gói đẩy tin theo Q16 và nguyên tắc một ý định một lần thu.
     * Giải quyết đầy đủ các điểm review trên PR #21:
     * 1. Truyền chuId thực tế của tin vào kiemTraQuyenThaoTac để lớp quyền thật cho phép chính chủ và chặn người khác.
     * 2. Lưu du_lieu_bam và snapshot ket_qua_json của lần mua gốc:
     *    - Cùng mã ý định + cùng nội dung -> trả đúng kết quả ban đầu (kể cả số dư ban đầu tại thời điểm mua đó dù số dư ví hiện tại đã thay đổi sau các giao dịch khác).
     *    - Cùng mã ý định + khác nội dung (khác tinId, khác gói hoặc khác cờ dongYCongDon) -> ném 409 XUNG_DOT_MA_Y_DINH và giữ nguyên ví, sổ và gói.
     */
    @Transactional(rollbackFor = Exception.class)
    public KetQuaMuaGoiResponse muaGoi(
            String rawSessionCookie,
            UUID tinId,
            String idempotencyKeyHeader,
            YeuCauMuaGoiRequest request
    ) {
        Objects.requireNonNull(tinId, "tinId không được null");
        if (request == null || request.maGoi() == null || request.maGoi().isBlank()) {
            throw new InvalidPurchaseIntentException("Thông tin gói mua không được để trống");
        }

        String rawIntentKey = (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank())
                ? idempotencyKeyHeader
                : request.maYDinh();
        if (rawIntentKey == null || rawIntentKey.trim().isEmpty()) {
            throw new InvalidPurchaseIntentException("Mã ý định mua (Idempotency-Key) không được để trống");
        }
        String maYDinhChuan = rawIntentKey.trim();
        if (maYDinhChuan.length() > 128) {
            throw new InvalidPurchaseIntentException("Mã ý định mua không được vượt quá 128 ký tự");
        }

        final LoaiGoiDayTin loaiGoi;
        try {
            loaiGoi = LoaiGoiDayTin.tuMa(request.maGoi());
        } catch (IllegalArgumentException ex) {
            throw new InvalidPurchaseIntentException(ex.getMessage());
        }

        boolean dongYCongDon = request.isDongYCongDon();
        String duLieuBam = tinhDuLieuBamMuaGoi(tinId, loaiGoi, dongYCongDon);

        // 1. Đọc & khóa tin, kiểm tra quyền với chuId thực tế của tin
        ThongTinTinKiemTra thongTinTin = kiemTraTinPort.khoaVaDocThongTinTin(tinId)
                .orElseThrow(ListingNotFoundException::new);

        PrincipalPhienHienTai principal = xacThucChinhChuTin(rawSessionCookie, thongTinTin.chuId());
        UUID taiKhoanId = principal.getTaiKhoanId();

        // 2. Kiểm tra lại trạng thái tài khoản trong CSDL (nếu có bản ghi trong taiKhoanRepository)
        Optional<TaiKhoan> taiKhoanOpt = taiKhoanRepository.findByIdForUpdate(taiKhoanId);
        if (taiKhoanOpt.isPresent()) {
            TaiKhoan taiKhoan = taiKhoanOpt.get();
            if (taiKhoan.getTrangThai() != TrangThaiTaiKhoan.HOAT_DONG) {
                throw new AccountLockedException("Tài khoản đang bị khóa, không thể mua gói");
            }
            if (!taiKhoan.isEmailDaXacMinh()) {
                throw new EmailNotVerifiedException("Vui lòng xác minh email trước khi mua gói");
            }
        }

        // 3. Khóa ví điện tử của người dùng (hoặc khởi tạo 0 VND nếu chưa có)
        ViDienTu vi = viDienTuRepository.findByTaiKhoanIdForUpdate(taiKhoanId)
                .orElseGet(() -> viDienTuRepository.save(ViDienTu.khoiTao(taiKhoanId)));

        // 4. Kiểm tra Idempotency / Replay dưới khóa ví TRƯỚC khi kiểm điều kiện động (AC-11.5)
        Optional<GiaoDichVi> giaoDichCuOpt = giaoDichViRepository.findByViIdAndLoaiAndMaYDinh(
                vi.id(),
                "mua_goi",
                maYDinhChuan
        );
        if (giaoDichCuOpt.isPresent()) {
            return phatLaiHoacBaoXungDot(giaoDichCuOpt.get(), duLieuBam, tinId);
        }

        // 5. Lấy mốc thời gian hiện tại sau khi đã giữ khóa và xử lý gói chưa kết thúc
        Instant now = clock.instant();
        Optional<GoiDayTin> goiOpt = goiDayTinRepository.findGoiChuaKetThucForUpdate(tinId);
        GoiDayTin goi;
        String loaiSuKienGoi;
        String hanhDongAudit;
        Duration thoiLuongTruocSuKien;
        Duration thoiLuongSauSuKien;

        if (goiOpt.isPresent() && goiOpt.get().laHetHanThucTe(now)) {
            // Đóng gói cũ đã quá hạn trên thực tế trước khi kiểm tra trạng thái mới (AC-02.2, AC-11)
            GoiDayTin goiQuaHan = goiOpt.get();
            GoiDayTin goiDaHetHan = goiQuaHan.chuyenHetHan();
            goiDayTinRepository.save(goiDaHetHan);
            goiDayTinRepository.ghiSuKienGoi(
                    goiDaHetHan.id(),
                    taiKhoanId,
                    "het_han",
                    Duration.ZERO,
                    Duration.ZERO,
                    now
            );
            goiOpt = Optional.empty();
        }

        // Kiểm tra trạng thái tin phải đang da_duyet
        if (!"da_duyet".equals(thongTinTin.trangThai())) {
            throw ListingNotEligibleForPackageException.tinChuaDuyet();
        }

        if (goiOpt.isPresent()) {
            GoiDayTin goiHienTai = goiOpt.get();
            if ("tam_dung_cho_duyet".equals(goiHienTai.trangThai())) {
                throw ListingNotEligibleForPackageException.goiDangTamDung();
            }
            // Gói đang thực sự còn hiệu lực -> bắt buộc có xác nhận cộng dồn
            if (!dongYCongDon) {
                throw new PackageAccumulationRequiredException(goiHienTai.hetHanLuc(), goiHienTai.thoiLuongConLai());
            }
            // Kiểm tra số dư ví trước khi cộng dồn
            if (vi.soDuVnd().compareTo(loaiGoi.getGiaVnd()) < 0) {
                throw new InsufficientBalanceException(vi.soDuVnd(), loaiGoi.getGiaVnd());
            }

            long giayConLaiTruoc = goiHienTai.hetHanLuc() != null && goiHienTai.hetHanLuc().isAfter(now)
                    ? goiHienTai.hetHanLuc().getEpochSecond() - now.getEpochSecond()
                    : goiHienTai.thoiLuongConLai().getSeconds();
            thoiLuongTruocSuKien = Duration.ofSeconds(Math.max(0L, giayConLaiTruoc));
            goi = goiHienTai.congDonThoiLuong(loaiGoi.getThoiLuong(), now);
            long giayConLaiSau = goi.hetHanLuc() != null && goi.hetHanLuc().isAfter(now)
                    ? goi.hetHanLuc().getEpochSecond() - now.getEpochSecond()
                    : goi.thoiLuongConLai().getSeconds();
            thoiLuongSauSuKien = Duration.ofSeconds(Math.max(0L, giayConLaiSau));
            loaiSuKienGoi = "cong_don";
            hanhDongAudit = "cong_don_goi";
        } else {
            // Chưa có gói -> kiểm tra số dư ví rồi tạo gói mới
            if (vi.soDuVnd().compareTo(loaiGoi.getGiaVnd()) < 0) {
                throw new InsufficientBalanceException(vi.soDuVnd(), loaiGoi.getGiaVnd());
            }
            thoiLuongTruocSuKien = Duration.ZERO;
            thoiLuongSauSuKien = loaiGoi.getThoiLuong();
            goi = GoiDayTin.taoMoiDangHoatDong(tinId, loaiGoi.getThoiLuong(), now);
            loaiSuKienGoi = "mua_moi";
            hanhDongAudit = "mua_goi";
        }

        goi = goiDayTinRepository.save(goi);

        // 6. Trừ số dư ví và Ghi sổ giao dịch (Ledger) kèm snapshot gốc nguyên tử
        BigDecimal soDuTruoc = vi.soDuVnd();
        vi = vi.truTien(loaiGoi.getGiaVnd(), now);
        viDienTuRepository.save(vi);

        UUID giaoDichId = UUID.randomUUID();
        UUID lanMuaId = UUID.randomUUID();
        long thoiLuongConLaiGiay = goi.hetHanLuc() != null && goi.hetHanLuc().isAfter(now)
                ? goi.hetHanLuc().getEpochSecond() - now.getEpochSecond()
                : goi.thoiLuongConLai().getSeconds();

        KetQuaMuaGoiResponse ketQuaGoc = KetQuaMuaGoiResponse.thanhCong(
                "Mua và kích hoạt gói đẩy tin thành công",
                giaoDichId,
                lanMuaId,
                vi.soDuVnd(),
                goi.id(),
                goi.trangThai(),
                goi.hetHanLuc(),
                thoiLuongConLaiGiay
        );

        String ketQuaJson = taoKetQuaJsonSnapshot(ketQuaGoc, loaiGoi);

        GiaoDichVi giaoDich = GiaoDichVi.taoButToanTruTienCoSnapshot(
                giaoDichId,
                vi.id(),
                "mua_goi",
                loaiGoi.getGiaVnd(),
                soDuTruoc,
                vi.soDuVnd(),
                maYDinhChuan,
                duLieuBam,
                ketQuaJson,
                "Mua " + loaiGoi.getTenGoi() + " cho tin " + tinId,
                now
        );
        try {
            giaoDich = giaoDichViRepository.save(giaoDich);
        } catch (DuplicateKeyException ex) {
            Optional<GiaoDichVi> replayOpt = giaoDichViRepository.findByViIdAndLoaiAndMaYDinh(
                    vi.id(),
                    "mua_goi",
                    maYDinhChuan
            );
            if (replayOpt.isPresent()) {
                return phatLaiHoacBaoXungDot(replayOpt.get(), duLieuBam, tinId);
            }
            throw ex;
        }

        // 7. Ghi nhận Lần mua gói, Sự kiện gói và Nhật ký kiểm toán
        LanMuaGoi lanMua = new LanMuaGoi(
                lanMuaId,
                goi.id(),
                giaoDich.id(),
                loaiGoi.getGiaVnd(),
                loaiGoi.getThoiLuong(),
                now
        );
        lanMuaGoiRepository.save(lanMua);

        goiDayTinRepository.ghiSuKienGoi(
                goi.id(),
                taiKhoanId,
                loaiSuKienGoi,
                thoiLuongTruocSuKien,
                thoiLuongSauSuKien,
                now
        );

        Map<String, Object> auditMap = new LinkedHashMap<>();
        auditMap.put("giaoDichId", giaoDich.id().toString());
        auditMap.put("lanMuaId", lanMua.id().toString());
        auditMap.put("goiId", goi.id().toString());
        auditMap.put("maGoi", loaiGoi.getMaChuan());
        auditMap.put("giaDaMuaVnd", loaiGoi.getGiaVnd().toPlainString());
        auditMap.put("soDuTruocVnd", soDuTruoc.toPlainString());
        auditMap.put("soDuSauVnd", vi.soDuVnd().toPlainString());
        auditMap.put("hetHanLuc", goi.hetHanLuc() != null ? goi.hetHanLuc().toString() : null);
        goiDayTinRepository.ghiNhatKyKiemToanGoi(
                taiKhoanId,
                tinId,
                hanhDongAudit,
                ChuanHoaJson.toJsonString(auditMap),
                now
        );

        inMemoryReplayCache.put(giaoDich.id(), ketQuaGoc);
        return ketQuaGoc;
    }

    /**
     * Xác thực phiên người dùng và kiểm tra quyền sở hữu tin đăng bằng cách truyền chuTinId thực tế
     * vào CurrentPermissionAndCsrfService.kiemTraQuyenThaoTac.
     * Nếu phiên không phải chủ tin, biến đổi sang ListingNotEligibleForPackageException.khongPhaiChuTin() (403)
     * để cả lớp quyền thật lẫn các unit test hiện hữu hoạt động nhất quán.
     */
    private PrincipalPhienHienTai xacThucChinhChuTin(String rawSessionCookie, UUID chuTinId) {
        PrincipalPhienHienTai principal;
        try {
            principal = permissionAndCsrfService.kiemTraQuyenThaoTac(
                    rawSessionCookie,
                    ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                    chuTinId,
                    null
            );
        } catch (InsufficientPermissionException ex) {
            // Phân biệt giữa sai vai trò RBAC (KDV/Admin) và người dùng khác chủ tin
            PrincipalPhienHienTai caller = permissionAndCsrfService.kiemTraPhienVaQuyenHienTai(rawSessionCookie);
            if (caller.getVaiTro() == VaiTroTaiKhoan.NGUOI_DUNG && !caller.getTaiKhoanId().equals(chuTinId)) {
                throw ListingNotEligibleForPackageException.khongPhaiChuTin();
            }
            throw ex;
        }
        if (principal == null) {
            principal = permissionAndCsrfService.kiemTraPhienVaQuyenHienTai(rawSessionCookie);
        }
        if (principal.getVaiTro() != VaiTroTaiKhoan.NGUOI_DUNG) {
            throw new InsufficientPermissionException("Chỉ vai trò Người dùng mới được phép mua gói đẩy tin.");
        }
        if (!principal.getTaiKhoanId().equals(chuTinId)) {
            throw ListingNotEligibleForPackageException.khongPhaiChuTin();
        }
        return principal;
    }

    private static String tinhDuLieuBamMuaGoi(UUID tinId, LoaiGoiDayTin loaiGoi, boolean dongYCongDon) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("dongYCongDon", dongYCongDon);
        canonical.put("listingId", tinId.toString());
        canonical.put("maGoi", loaiGoi.getMaChuan());
        return ChuanHoaJson.bamYeuCau("POST", "/api/listings/" + tinId + "/boost-purchases", canonical);
    }

    private static String taoKetQuaJsonSnapshot(KetQuaMuaGoiResponse ketQua, LoaiGoiDayTin loaiGoi) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("giaoDichId", ketQua.maGiaoDich().toString());
        snapshot.put("lanMuaId", ketQua.lanMuaId() != null ? ketQua.lanMuaId().toString() : null);
        snapshot.put("goiId", ketQua.goi().id().toString());
        snapshot.put("maGoi", loaiGoi.getMaChuan());
        snapshot.put("soDuSauVnd", ketQua.soDuConLaiVnd().toPlainString());
        snapshot.put("trangThaiGoi", ketQua.goi().trangThai());
        snapshot.put("hetHanLuc", ketQua.goi().hetHanLuc() != null ? ketQua.goi().hetHanLuc().toString() : null);
        snapshot.put("thoiLuongConLaiGiay", ketQua.goi().thoiLuongConLaiGiay());
        return ChuanHoaJson.toJsonString(snapshot);
    }

    private KetQuaMuaGoiResponse phatLaiHoacBaoXungDot(GiaoDichVi giaoDichCu, String duLieuBamMoi, UUID tinId) {
        if (giaoDichCu.duLieuBam() != null && !giaoDichCu.duLieuBam().equals(duLieuBamMoi)) {
            throw new PurchaseIntentConflictException(
                    "Mã ý định mua (Idempotency-Key) đã được sử dụng cho một nội dung yêu cầu mua gói khác."
            );
        }

        KetQuaMuaGoiResponse cached = inMemoryReplayCache.get(giaoDichCu.id());
        if (cached != null) {
            return new KetQuaMuaGoiResponse(
                    true,
                    "Yêu cầu mua gói đã được xử lý thành công trước đó (Replay)",
                    cached.maGiaoDich(),
                    cached.lanMuaId(),
                    giaoDichCu.soDuSau(),
                    cached.goi()
            );
        }

        // Khôi phục từ ket_qua_json hoặc từ các bản ghi liên kết của lần mua gốc (số dư phải là giaoDichCu.soDuSau() gốc!)
        Optional<LanMuaGoi> lanMuaOpt = lanMuaGoiRepository.findByGiaoDichViId(giaoDichCu.id());
        UUID lanMuaId = lanMuaOpt.map(LanMuaGoi::id).orElse(null);
        UUID goiId = lanMuaOpt.map(LanMuaGoi::goiId).orElse(null);

        String trangThaiGoi = extractJsonString(giaoDichCu.ketQuaJson(), "trangThaiGoi");
        String hetHanLucStr = extractJsonString(giaoDichCu.ketQuaJson(), "hetHanLuc");
        String goiIdStr = extractJsonString(giaoDichCu.ketQuaJson(), "goiId");
        String lanMuaIdStr = extractJsonString(giaoDichCu.ketQuaJson(), "lanMuaId");
        Long thoiLuongGiay = extractJsonLong(giaoDichCu.ketQuaJson(), "thoiLuongConLaiGiay");

        if (goiId == null && goiIdStr != null) {
            try {
                goiId = UUID.fromString(goiIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (lanMuaId == null && lanMuaIdStr != null) {
            try {
                lanMuaId = UUID.fromString(lanMuaIdStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Instant hetHanLuc = null;
        if (hetHanLucStr != null) {
            try {
                hetHanLuc = Instant.parse(hetHanLucStr);
            } catch (Exception ignored) {
            }
        }

        if (goiId == null || hetHanLuc == null || trangThaiGoi == null) {
            GoiDayTin goiFallback = (goiId != null ? goiDayTinRepository.findById(goiId) : Optional.<GoiDayTin>empty())
                    .or(() -> goiDayTinRepository.findGoiChuaKetThuc(tinId))
                    .orElse(null);
            if (goiFallback != null) {
                if (goiId == null) goiId = goiFallback.id();
                if (trangThaiGoi == null) trangThaiGoi = goiFallback.trangThai();
                if (hetHanLuc == null) hetHanLuc = goiFallback.hetHanLuc();
            }
        }

        return KetQuaMuaGoiResponse.thanhCong(
                "Yêu cầu mua gói đã được xử lý thành công trước đó (Replay)",
                giaoDichCu.id(),
                lanMuaId,
                giaoDichCu.soDuSau(), // Luôn trả số dư sau tại thời điểm giao dịch gốc, không lấy số dư ví hiện tại đã biến động
                goiId,
                trangThaiGoi != null ? trangThaiGoi : "dang_hoat_dong",
                hetHanLuc,
                thoiLuongGiay
        );
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        int valStart = start + pattern.length();
        int valEnd = json.indexOf('"', valStart);
        if (valEnd < 0) {
            return null;
        }
        return json.substring(valStart, valEnd);
    }

    private static Long extractJsonLong(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        int valStart = start + pattern.length();
        int valEnd = valStart;
        while (valEnd < json.length() && (Character.isDigit(json.charAt(valEnd)) || json.charAt(valEnd) == '-')) {
            valEnd++;
        }
        if (valEnd == valStart) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(valStart, valEnd));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
