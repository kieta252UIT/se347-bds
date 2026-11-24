package com.realestates.backend.wallet.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.listing.exception.ListingNotFoundException;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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

    public ThongTinMuaGoiResponse layThongTinGoiVaSoDu(String rawSessionCookie, UUID tinId) {
        Objects.requireNonNull(tinId, "tinId không được null");
        PrincipalPhienHienTai principal = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                null,
                null
        );

        BigDecimal soDu = viDienTuRepository.findByTaiKhoanId(principal.getTaiKhoanId())
                .map(ViDienTu::soDuVnd)
                .orElse(BigDecimal.ZERO);

        Instant now = clock.instant();
        Optional<GoiDayTin> goiOpt = goiDayTinRepository.findGoiChuaKetThuc(tinId);

        ThongTinMuaGoiResponse.GoiHienTaiDto goiHienTaiDto;
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
        } else {
            goiHienTaiDto = new ThongTinMuaGoiResponse.GoiHienTaiDto(false, null, null, null);
        }

        List<ThongTinMuaGoiResponse.GoiHoTroDto> cacGoiHoTro = Arrays.stream(LoaiGoiDayTin.values())
                .map(g -> new ThongTinMuaGoiResponse.GoiHoTroDto(
                        g.name(),
                        g.getTenGoi(),
                        g.getGiaVnd(),
                        g.getSoNgay()
                ))
                .toList();

        return new ThongTinMuaGoiResponse(tinId, soDu, goiHienTaiDto, cacGoiHoTro);
    }

    @Transactional(rollbackFor = Exception.class)
    public KetQuaMuaGoiResponse muaGoi(String rawSessionCookie, UUID tinId, YeuCauMuaGoiRequest request) {
        Objects.requireNonNull(tinId, "tinId không được null");
        if (request == null || request.maGoi() == null || request.maGoi().isBlank()) {
            throw new IllegalArgumentException("Thông tin gói mua không được để trống");
        }
        if (request.maYDinh() == null || request.maYDinh().trim().isEmpty()) {
            throw new InvalidPurchaseIntentException("Mã ý định mua không được để trống");
        }

        LoaiGoiDayTin loaiGoi = LoaiGoiDayTin.tuMa(request.maGoi());
        String maYDinhChuan = request.maYDinh().trim();

        // 1. Xác thực phiên và quyền người dùng
        PrincipalPhienHienTai principal = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                null,
                null
        );

        UUID taiKhoanId = principal.getTaiKhoanId();
        Instant now = clock.instant();

        // 2. Tra cứu ví & Kiểm tra Idempotency trước khi áp dụng lock
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(taiKhoanId)
                .orElseGet(() -> viDienTuRepository.save(ViDienTu.khoiTao(taiKhoanId)));

        Optional<GiaoDichVi> giaoDichCuOpt = giaoDichViRepository.findByViIdAndLoaiAndMaYDinh(
                vi.id(),
                "mua_goi",
                maYDinhChuan
        );
        if (giaoDichCuOpt.isPresent()) {
            GoiDayTin goiHienTai = goiDayTinRepository.findGoiChuaKetThuc(tinId).orElse(null);
            return KetQuaMuaGoiResponse.thanhCong(
                    "Yêu cầu mua gói đã được xử lý thành công trước đó (Replay)",
                    giaoDichCuOpt.get().id(),
                    vi.soDuVnd(),
                    goiHienTai != null ? goiHienTai.id() : null,
                    goiHienTai != null ? goiHienTai.trangThai() : "dang_hoat_dong",
                    goiHienTai != null ? goiHienTai.hetHanLuc() : null
            );
        }

        // 3. Khóa theo Pessimistic Lock Order cố định: TaiKhoan -> Tin -> Vi -> Goi
        // 3.1. Khóa & Kiểm tra Tài khoản
        TaiKhoan taiKhoan = taiKhoanRepository.findById(taiKhoanId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản người dùng"));
        if (taiKhoan.getTrangThai() != TrangThaiTaiKhoan.HOAT_DONG) {
            throw new AccountLockedException("Tài khoản đang bị khóa, không thể mua gói");
        }
        if (!taiKhoan.isEmailDaXacMinh()) {
            throw new EmailNotVerifiedException("Vui lòng xác minh email trước khi mua gói");
        }

        // 3.2. Khóa & Kiểm tra Tin bất động sản
        var thongTinTinOpt = kiemTraTinPort.khoaVaDocThongTinTin(tinId);
        if (thongTinTinOpt.isEmpty()) {
            throw new ListingNotFoundException();
        }
        var thongTinTin = thongTinTinOpt.get();
        if (!taiKhoanId.equals(thongTinTin.chuId())) {
            throw ListingNotEligibleForPackageException.khongPhaiChuTin();
        }
        if (!"da_duyet".equals(thongTinTin.trangThai())) {
            throw ListingNotEligibleForPackageException.tinChuaDuyet();
        }

        // 3.3. Khóa & Kiểm tra Ví điện tử
        vi = viDienTuRepository.findByTaiKhoanIdForUpdate(taiKhoanId)
                .orElseGet(() -> viDienTuRepository.save(ViDienTu.khoiTao(taiKhoanId)));
        if (vi.soDuVnd().compareTo(loaiGoi.getGiaVnd()) < 0) {
            throw new InsufficientBalanceException(vi.soDuVnd(), loaiGoi.getGiaVnd());
        }

        // 3.4. Khóa & Xử lý Gói chưa kết thúc
        Optional<GoiDayTin> goiOpt = goiDayTinRepository.findGoiChuaKetThucForUpdate(tinId);
        GoiDayTin goi;

        if (goiOpt.isPresent()) {
            goi = goiOpt.get();
            if (goi.laHetHanThucTe(now)) {
                // Đóng gói cũ đã quá hạn trên thực tế
                goi = goi.chuyenHetHan();
                goiDayTinRepository.save(goi);

                // Khởi tạo gói mới
                goi = GoiDayTin.taoMoiDangHoatDong(tinId, loaiGoi.getThoiLuong(), now);
                goi = goiDayTinRepository.save(goi);
            } else {
                // Gói đang thực sự còn hiệu lực -> kiểm tra xác nhận cộng dồn
                if (!request.isDongYCongDon()) {
                    throw new PackageAccumulationRequiredException(goi.hetHanLuc(), goi.thoiLuongConLai());
                }
                // Đồng ý cộng dồn -> Kéo dài hạn hoặc cộng dồn thời lượng
                goi = goi.congDonThoiLuong(loaiGoi.getThoiLuong(), now);
                goi = goiDayTinRepository.save(goi);
            }
        } else {
            // Chưa có gói -> Tạo gói mới
            goi = GoiDayTin.taoMoiDangHoatDong(tinId, loaiGoi.getThoiLuong(), now);
            goi = goiDayTinRepository.save(goi);
        }

        // 4. Trừ số dư ví và Ghi sổ giao dịch (Ledger) nguyên tử
        BigDecimal soDuTruoc = vi.soDuVnd();
        vi = vi.truTien(loaiGoi.getGiaVnd(), now);
        viDienTuRepository.save(vi);

        GiaoDichVi giaoDich = GiaoDichVi.taoButToanTruTien(
                vi.id(),
                "mua_goi",
                loaiGoi.getGiaVnd(),
                soDuTruoc,
                vi.soDuVnd(),
                maYDinhChuan,
                "Mua " + loaiGoi.getTenGoi() + " cho tin " + tinId,
                now
        );
        giaoDich = giaoDichViRepository.save(giaoDich);

        // 5. Ghi nhận Lần mua gói (phục vụ đối soát/bồi hoàn S3 mà không giảm thời hạn)
        LanMuaGoi lanMua = LanMuaGoi.taoMoi(
                goi.id(),
                giaoDich.id(),
                loaiGoi.getGiaVnd(),
                loaiGoi.getThoiLuong(),
                now
        );
        lanMuaGoiRepository.save(lanMua);

        return KetQuaMuaGoiResponse.thanhCong(
                "Mua và kích hoạt gói đẩy tin thành công",
                giaoDich.id(),
                vi.soDuVnd(),
                goi.id(),
                goi.trangThai(),
                goi.hetHanLuc()
        );
    }
}
