package com.realestates.backend.wallet.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
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
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuanLyGoiDayTinApplicationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final TaiKhoanRepository taiKhoanRepository;
    private final KiemTraTinChoGoiPort kiemTraTinPort;
    private final ViDienTuRepository viDienTuRepository;
    private final GiaoDichViRepository giaoDichViRepository;
    private final GoiDayTinRepository goiDayTinRepository;
    private final LanMuaGoiRepository lanMuaGoiRepository;
    private final Clock clock;

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

    public KetQuaMuaGoiResponse muaGoi(String rawSessionCookie, UUID tinId, YeuCauMuaGoiRequest request) {
        return muaGoi(rawSessionCookie, tinId, null, request);
    }

    /**
     * Thực hiện mua hoặc cộng dồn gói đẩy tin theo Q16 và nguyên tắc một ý định một lần thu.
     *
     * Tuân thủ thứ tự khóa toàn cục S2-02 Bảng 4:
     * [1] tai_khoan -> [2] phien_xac_thuc -> [4] vi_dien_tu -> [6] tin_bat_dong_san -> [8] goi_day_tin
     * thông qua CurrentPermissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction.
     *
     * Mọi xử lý dự phòng DuplicateKeyException đặt ở NGOÀI phạm vi transaction để trên PostgreSQL
     * không bao giờ thực hiện SELECT bên trong transaction đã bị đánh dấu aborted (SQLState 25P02),
     * và trên In-Memory không bao giờ commit dở dang bước cộng gói / trừ ví.
     */
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

        // Kiểm tra vòng ngoài phiên và vai trò RBAC trước khi khóa tài nguyên
        PrincipalPhienHienTai callerOuter = permissionAndCsrfService.kiemTraPhienVaQuyenHienTai(rawSessionCookie);
        if (callerOuter.getVaiTro() != VaiTroTaiKhoan.NGUOI_DUNG) {
            throw new InsufficientPermissionException("Chỉ vai trò Người dùng mới được phép mua gói đẩy tin.");
        }

        AtomicReference<ViDienTu> lockedViRef = new AtomicReference<>();
        AtomicReference<ThongTinTinKiemTra> lockedTinRef = new AtomicReference<>();

        try {
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie,
                    ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                    () -> {
                        // Tại thời điểm này CurrentPermissionAndCsrfService đã khóa:
                        // [Bậc 1] tai_khoan FOR UPDATE và [Bậc 2] phien_xac_thuc FOR UPDATE
                        // Tiếp tục khóa theo đúng thứ tự: [Bậc 4] vi_dien_tu -> [Bậc 6] tin_bat_dong_san
                        UUID callerId = callerOuter.getTaiKhoanId();
                        ViDienTu vi = viDienTuRepository.findByTaiKhoanIdForUpdate(callerId)
                                .orElseGet(() -> viDienTuRepository.save(ViDienTu.khoiTao(callerId)));
                        lockedViRef.set(vi);

                        ThongTinTinKiemTra tin = kiemTraTinPort.khoaVaDocThongTinTin(tinId)
                                .orElseThrow(ListingNotFoundException::new);
                        lockedTinRef.set(tin);

                        if (!callerId.equals(tin.chuId())) {
                            throw ListingNotEligibleForPackageException.khongPhaiChuTin();
                        }
                        return tin.chuId();
                    },
                    tinId,
                    null,
                    freshPrincipal -> thucThiMuaGoiTrongKhoa(
                            freshPrincipal,
                            tinId,
                            maYDinhChuan,
                            loaiGoi,
                            dongYCongDon,
                            duLieuBam,
                            Objects.requireNonNull(lockedViRef.get(), "Ví đã khóa không được null"),
                            Objects.requireNonNull(lockedTinRef.get(), "Tin đã khóa không được null")
                    )
            );
        } catch (DuplicateKeyException ex) {
            // Giao dịch bên trong đã rollback hoàn toàn; mở truy vấn đọc sạch ngoài transaction để phát lại nếu trùng mã ý định
            ViDienTu vi = lockedViRef.get();
            if (vi == null) {
                vi = viDienTuRepository.findByTaiKhoanId(callerOuter.getTaiKhoanId()).orElse(null);
            }
            if (vi != null) {
                Optional<GiaoDichVi> replayOpt = giaoDichViRepository.findByViIdAndLoaiAndMaYDinh(
                        vi.id(),
                        "mua_goi",
                        maYDinhChuan
                );
                if (replayOpt.isPresent()) {
                    return phatLaiHoacBaoXungDot(replayOpt.get(), duLieuBam, tinId);
                }
            }
            throw ex;
        }
    }

    private KetQuaMuaGoiResponse thucThiMuaGoiTrongKhoa(
            PrincipalPhienHienTai freshPrincipal,
            UUID tinId,
            String maYDinhChuan,
            LoaiGoiDayTin loaiGoi,
            boolean dongYCongDon,
            String duLieuBam,
            ViDienTu vi,
            ThongTinTinKiemTra thongTinTin
    ) {
        UUID taiKhoanId = freshPrincipal.getTaiKhoanId();

        // Bước 4 — Kiểm tra Idempotency / Replay dưới khóa ví TRƯỚC khi chạm vào gói hay trừ tiền (AC-11.5)
        Optional<GiaoDichVi> giaoDichCuOpt = giaoDichViRepository.findByViIdAndLoaiAndMaYDinh(
                vi.id(),
                "mua_goi",
                maYDinhChuan
        );
        if (giaoDichCuOpt.isPresent()) {
            return phatLaiHoacBaoXungDot(giaoDichCuOpt.get(), duLieuBam, tinId);
        }

        // [Bậc 8] Khóa gói đẩy tin chưa kết thúc của tin đăng
        Optional<GoiDayTin> goiOpt = goiDayTinRepository.findGoiChuaKetThucForUpdate(tinId);
        Instant now = clock.instant();

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

        // Tính trước số dư sau trừ tiền và tạo bút toán sổ giao dịch có snapshot ket_qua_json
        BigDecimal soDuTruoc = vi.soDuVnd();
        ViDienTu viSauTru = vi.truTien(loaiGoi.getGiaVnd(), now);

        UUID giaoDichId = UUID.randomUUID();
        UUID lanMuaId = UUID.randomUUID();
        long thoiLuongConLaiGiay = goi.hetHanLuc() != null && goi.hetHanLuc().isAfter(now)
                ? goi.hetHanLuc().getEpochSecond() - now.getEpochSecond()
                : goi.thoiLuongConLai().getSeconds();

        KetQuaMuaGoiResponse ketQuaGoc = KetQuaMuaGoiResponse.thanhCong(
                "Mua và kích hoạt gói đẩy tin thành công",
                giaoDichId,
                lanMuaId,
                viSauTru.soDuVnd(),
                goi.id(),
                goi.trangThai(),
                goi.hetHanLuc(),
                thoiLuongConLaiGiay
        );

        String ketQuaJson = taoKetQuaJsonSnapshot(ketQuaGoc, loaiGoi);

        GiaoDichVi giaoDich = GiaoDichVi.taoButToanTruTienCoSnapshot(
                giaoDichId,
                viSauTru.id(),
                "mua_goi",
                loaiGoi.getGiaVnd(),
                soDuTruoc,
                viSauTru.soDuVnd(),
                maYDinhChuan,
                duLieuBam,
                ketQuaJson,
                "Mua " + loaiGoi.getTenGoi() + " cho tin " + tinId,
                now
        );

        // Ghi bút toán giao_dich_vi trước để kiểm tra ràng buộc duy nhất (vi_id, loai, ma_y_dinh)
        // trước khi ghi nhận biến động số dư ví và gói đẩy tin
        giaoDich = giaoDichViRepository.save(giaoDich);
        goi = goiDayTinRepository.save(goi);
        viDienTuRepository.save(viSauTru);

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
        auditMap.put("soDuSauVnd", viSauTru.soDuVnd().toPlainString());
        auditMap.put("hetHanLuc", goi.hetHanLuc() != null ? goi.hetHanLuc().toString() : null);
        goiDayTinRepository.ghiNhatKyKiemToanGoi(
                taiKhoanId,
                tinId,
                hanhDongAudit,
                ChuanHoaJson.toJsonString(auditMap),
                now
        );

        return ketQuaGoc;
    }

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

        Optional<LanMuaGoi> lanMuaOpt = lanMuaGoiRepository.findByGiaoDichViId(giaoDichCu.id());
        UUID lanMuaId = lanMuaOpt.map(LanMuaGoi::id).orElse(null);
        UUID goiId = lanMuaOpt.map(LanMuaGoi::goiId).orElse(null);

        String trangThaiGoi = null;
        String hetHanLucStr = null;
        String goiIdStr = null;
        String lanMuaIdStr = null;
        Long thoiLuongGiay = null;

        String rawSnapshot = giaoDichCu.ketQuaJson();
        if (rawSnapshot != null && !rawSnapshot.isBlank()) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(rawSnapshot);
                if (root != null && root.isObject()) {
                    trangThaiGoi = readJsonText(root, "trangThaiGoi");
                    hetHanLucStr = readJsonText(root, "hetHanLuc");
                    goiIdStr = readJsonText(root, "goiId");
                    lanMuaIdStr = readJsonText(root, "lanMuaId");
                    thoiLuongGiay = readJsonLong(root, "thoiLuongConLaiGiay");
                }
            } catch (Exception ignored) {
                // Nếu ket_qua_json không đọc được thì dùng dữ liệu từ lan_mua_goi và goi_day_tin bên dưới
            }
        }

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
                giaoDichCu.soDuSau(),
                goiId,
                trangThaiGoi != null ? trangThaiGoi : "dang_hoat_dong",
                hetHanLuc,
                thoiLuongGiay
        );
    }

    private static String readJsonText(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return (text != null && !text.isBlank()) ? text : null;
    }

    private static Long readJsonLong(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
