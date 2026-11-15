package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.MaTranQuyenSprint1;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.CsrfKetQua;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.dto.TaiKhoanHienTai;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.InvalidCsrfException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ ứng dụng kiểm quyền hiện tại (GET /api/me, RBAC, Ownership, kiểm lại quyền trong giao dịch)
 * và bảo vệ yêu cầu thay đổi trạng thái bằng CSRF + Same-Origin (GET /api/csrf, POST/PUT/DELETE/PATCH)
 * theo đặc tả thiet-ke-phien-sprint-1.md, api-specification-sprint-1.md, ma-tran-quyen-va-trang-thai-sprint-1.md
 * và test-specification-sprint-1.md (TS1-008, TS1-012, TS1-013, TS1-019, TS1-020, PX-02..PX-06, PX-18, PX-21).
 */
@Service
public class CurrentPermissionAndCsrfService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final String REQUEST_ATTR_PRINCIPAL =
            CurrentPermissionAndCsrfService.class.getName() + ".PRINCIPAL_PHIEN_HIEN_TAI";

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final TaiKhoanRepository taiKhoanRepository;
    private final PhienXacThucRepository phienXacThucRepository;
    private final AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    private final Set<String> allowedOrigins;

    /**
     * Bảng khóa cấp dòng theo thứ tự cố định: tai_khoan -> phien_xac_thuc -> tài nguyên nghiệp vụ
     * (thiet-ke-phien-sprint-1.md §2.2 Mục 4 & PHIEN-03).
     */
    private final ConcurrentHashMap<UUID, ReentrantLock> accountRowLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> sessionRowLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> resourceRowLocks = new ConcurrentHashMap<>();

    public CurrentPermissionAndCsrfService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            List<String> allowedOrigins
    ) {
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.phienXacThucRepository = Objects.requireNonNull(phienXacThucRepository, "phienXacThucRepository không được null");
        this.sessionLifecyclePort = Objects.requireNonNull(sessionLifecyclePort, "sessionLifecyclePort không được null");
        this.allowedOrigins = (allowedOrigins == null || allowedOrigins.isEmpty())
                ? Set.of("https://localhost", "http://localhost:5173")
                : allowedOrigins.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Autowired
    public CurrentPermissionAndCsrfService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            @Value("${app.security.allowed-origins:https://localhost,http://localhost:5173}") String allowedOriginsProperty
    ) {
        this(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecyclePort,
                parseAllowedOrigins(allowedOriginsProperty)
        );
    }

    /**
     * Phát hành hoặc tái sử dụng CSRF token gắn với ngữ cảnh phiên hiện tại cho GET /api/csrf (operationId: layCsrf).
     * Theo PHIEN-06, PX-05 và thiet-ke-phien-sprint-1.md §3.1:
     * - Nếu __Host-se214_csrf hiện có còn hợp lệ chữ ký và khớp ngữ cảnh hiện tại
     *   (cùng sha256_base64url(raw_session_cookie) hoặc cùng anonymous), tái sử dụng token đó.
     * - Nếu thiếu, hỏng chữ ký hoặc đã đổi ngữ cảnh phiên, sinh token mới và ghi lại cookie __Host-se214_csrf.
     * - Không thay đổi __Host-se214_session.
     */
    public CsrfKetQua phatHanhHoacTaiSuDungCsrfToken(String rawSessionCookieValue, String existingCsrfCookieValue) {
        String normalizedSessionCookie = normalizeCookieValue(rawSessionCookieValue);
        String normalizedExistingCsrf = normalizeCookieValue(existingCsrfCookieValue);

        if (normalizedExistingCsrf != null
                && xacThucChuKyVaNguCanhCsrf(normalizedExistingCsrf, normalizedSessionCookie)) {
            return new CsrfKetQua(normalizedExistingCsrf);
        }

        String issuedToken = normalizedSessionCookie != null
                ? sessionLifecyclePort.issueSessionCsrfToken(normalizedSessionCookie)
                : sessionLifecyclePort.issueAnonymousCsrfToken();
        return new CsrfKetQua(issuedToken);
    }

    /**
     * Kiểm tra phương thức HTTP có phải phương thức thay đổi trạng thái cần bảo vệ CSRF hay không.
     */
    public boolean laPhuongThucThayDoiTrangThai(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return false;
        }
        return STATE_CHANGING_METHODS.contains(httpMethod.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Kiểm tra Origin và Double-Submit CSRF Token trên mọi yêu cầu POST, PUT, DELETE, PATCH
     * trước khi thực thi bất kỳ use case nào (PHIEN-01, PHIEN-06, PX-05, PX-06, TS1-012, TS1-020).
     */
    public void kiemTraOriginVaCsrf(
            String httpMethod,
            String originHeader,
            String rawSessionCookieValue,
            String rawCsrfCookieValue,
            String csrfHeaderValue
    ) {
        if (!laPhuongThucThayDoiTrangThai(httpMethod)) {
            return;
        }

        // 1. Kiểm tra Origin bắt buộc thuộc tập nguồn được cấu hình cho phép (PHIEN-01, PX-06)
        if (originHeader == null || originHeader.isBlank()) {
            throw new InvalidCsrfException("Thiếu header Origin trên yêu cầu thay đổi trạng thái.");
        }
        String normalizedOrigin = originHeader.trim();
        if (!allowedOrigins.contains(normalizedOrigin)) {
            throw new InvalidCsrfException("Nguồn gốc yêu cầu (Origin) không nằm trong danh sách cho phép.");
        }

        // 2. Kiểm tra sự hiện diện của __Host-se214_csrf và X-CSRF-Token (PHIEN-06)
        String csrfCookie = normalizeCookieValue(rawCsrfCookieValue);
        String csrfHeader = normalizeCookieValue(csrfHeaderValue);
        if (csrfCookie == null || csrfHeader == null) {
            throw new InvalidCsrfException("Thiếu cookie __Host-se214_csrf hoặc header X-CSRF-Token.");
        }

        // 3. So sánh hằng thời gian giữa X-CSRF-Token và __Host-se214_csrf
        boolean tokensMatch = MessageDigest.isEqual(
                csrfCookie.getBytes(StandardCharsets.UTF_8),
                csrfHeader.getBytes(StandardCharsets.UTF_8)
        );
        if (!tokensMatch) {
            throw new InvalidCsrfException("Giá trị X-CSRF-Token không khớp với cookie __Host-se214_csrf.");
        }

        // 4. Xác thực chữ ký HMAC-SHA-256 và ngữ cảnh phiên hiện tại (anonymous hoặc sha256_base64url(raw_session_cookie))
        String normalizedSessionCookie = normalizeCookieValue(rawSessionCookieValue);
        if (!xacThucChuKyVaNguCanhCsrf(csrfCookie, normalizedSessionCookie)) {
            throw new InvalidCsrfException("Mã CSRF không hợp lệ hoặc không khớp với ngữ cảnh phiên hiện tại.");
        }
    }

    /**
     * Đọc hồ sơ tài khoản hiện tại cho GET /api/me (operationId: layTaiKhoanHienTai).
     * Thực hiện kiểm tra đầy đủ hiệu lực phiên và trạng thái tài khoản trên kho lưu trữ chính.
     */
    public TaiKhoanHienTai layTaiKhoanHienTai(String rawSessionCookieValue) {
        PrincipalPhienHienTai principal = kiemTraPhienVaQuyenHienTai(rawSessionCookieValue);
        return new TaiKhoanHienTai(
                principal.getTaiKhoanId(),
                principal.getEmail(),
                principal.getHoTen(),
                principal.getSoDienThoai(),
                principal.getVaiTro(),
                principal.getTrangThai(),
                principal.isEmailDaXacMinh()
        );
    }

    /**
     * Kiểm tra phiên xác thực hiện tại trên kho lưu trữ chính (PHIEN-03, PHIEN-04, PHIEN-08, PHIEN-09, PX-02, PX-03, PX-18, PX-21).
     * Chỉ khởi tạo PrincipalPhienHienTai sau khi toàn bộ điều kiện hiệu lực đạt.
     */
    public PrincipalPhienHienTai kiemTraPhienVaQuyenHienTai(String rawSessionCookieValue) {
        String rawCookie = normalizeCookieValue(rawSessionCookieValue);
        if (rawCookie == null) {
            throw new UnauthenticatedSessionException();
        }

        String maCookieBam = sessionLifecyclePort.hashSessionCookie(rawCookie);
        PhienXacThuc phien = docPhienTuKhoChinh(maCookieBam)
                .orElseThrow(UnauthenticatedSessionException::new);

        if (phien.isDaThuHoi()) {
            throw new UnauthenticatedSessionException();
        }

        TaiKhoan taiKhoan = docTaiKhoanTuKhoChinh(phien.getTaiKhoanId())
                .orElseThrow(UnauthenticatedSessionException::new);

        kiemTraTrangThaiTaiKhoanVaPhien(phien, taiKhoan);
        return PrincipalPhienHienTai.tuPhienVaTaiKhoan(phien, taiKhoan);
    }

    /**
     * Thử phân giải Principal cho các yêu cầu đọc công khai (GET công khai).
     * Theo thiet-ke-phien-sprint-1.md §2.2 Mục 1 & PX-02:
     * Yêu cầu GET công khai vẫn hoạt động ở vai trò Khách ngay cả khi browser còn giữ cookie phiên cũ/không hợp lệ;
     * nhưng nếu kho lưu trữ chính đang gián đoạn (503 TAM_GIAN_DOAN) thì không được che giấu lỗi hạ tầng.
     */
    public Optional<PrincipalPhienHienTai> phanGiaiPrincipalTuyChonChoYeuCauCongKhai(String rawSessionCookieValue) {
        String rawCookie = normalizeCookieValue(rawSessionCookieValue);
        if (rawCookie == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(kiemTraPhienVaQuyenHienTai(rawCookie));
        } catch (UnauthenticatedSessionException | AccountLockedException | EmailNotVerifiedException ex) {
            return Optional.empty();
        }
    }

    /**
     * Kiểm tra quyền thực hiện thao tác nghiệp vụ (RBAC + Ownership + chặn tự khai vaiTro/trangThai/chuId từ client).
     */
    public PrincipalPhienHienTai kiemTraQuyenThaoTac(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            UUID chuSoHuuTaiNguyenId,
            Map<String, ?> clientPayload
    ) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");
        kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);

        if (thaoTac.isChoPhepKhach()) {
            return phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookieValue).orElse(null);
        }

        PrincipalPhienHienTai principal = kiemTraPhienVaQuyenHienTai(rawSessionCookieValue);
        kiemTraVaiTroVaChuSoHuu(principal, thaoTac, chuSoHuuTaiNguyenId);
        return principal;
    }

    /**
     * Kiểm tra lại quyền và hiệu lực phiên ngay trong giao dịch ghi có khóa đồng bộ theo thứ tự cố định:
     * tai_khoan -> phien_xac_thuc -> tài nguyên nghiệp vụ (thiet-ke-phien-sprint-1.md §2.2 Mục 4, TS1-019, PX-04).
     * Đảm bảo nếu tài khoản vừa bị khóa, bị thu hồi phiên hoặc bị đổi vai trò trước thời điểm kiểm trong giao dịch,
     * thao tác ghi sẽ bị từ chối nhất quán và không ghi dữ liệu trái phép.
     */
    public <T> T thucThiGhiCoKiemLaiQuyenTrongTransaction(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            Supplier<UUID> docChuSoHuuTaiNguyenTrongKhoa,
            UUID taiNguyenIdCanKhoa,
            Map<String, ?> clientPayload,
            Function<PrincipalPhienHienTai, T> hanhDongGhiTrongTransaction
    ) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");
        Objects.requireNonNull(hanhDongGhiTrongTransaction, "hanhDongGhiTrongTransaction không được null");

        kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);

        // Kiểm tra vòng ngoài trước khi vào giao dịch
        PrincipalPhienHienTai initialPrincipal = kiemTraPhienVaQuyenHienTai(rawSessionCookieValue);

        ReentrantLock accountLock = lockForAccount(initialPrincipal.getTaiKhoanId());
        ReentrantLock sessionLock = lockForSession(initialPrincipal.getPhienId());
        ReentrantLock resourceLock = taiNguyenIdCanKhoa != null ? lockForResource(taiNguyenIdCanKhoa) : null;

        // Tuân thủ thứ tự khóa cố định: tai_khoan -> phien_xac_thuc -> tài nguyên nghiệp vụ
        accountLock.lock();
        try {
            sessionLock.lock();
            try {
                if (resourceLock != null) {
                    resourceLock.lock();
                }
                try {
                    // Đọc lại trạng thái mới nhất từ kho lưu trữ chính ngay bên trong khóa giao dịch
                    PhienXacThuc freshSession = docPhienTuKhoChinhTheoId(
                                    initialPrincipal.getPhienId(),
                                    initialPrincipal.getMaCookieBam()
                            )
                            .orElseThrow(UnauthenticatedSessionException::new);

                    if (freshSession.isDaThuHoi()) {
                        throw new UnauthenticatedSessionException();
                    }

                    TaiKhoan freshAccount = docTaiKhoanTuKhoChinh(freshSession.getTaiKhoanId())
                            .orElseThrow(UnauthenticatedSessionException::new);

                    kiemTraTrangThaiTaiKhoanVaPhien(freshSession, freshAccount);

                    PrincipalPhienHienTai freshPrincipal =
                            PrincipalPhienHienTai.tuPhienVaTaiKhoan(freshSession, freshAccount);

                    UUID chuSoHuuId = docChuSoHuuTaiNguyenTrongKhoa != null
                            ? docChuSoHuuTaiNguyenTrongKhoa.get()
                            : null;
                    kiemTraVaiTroVaChuSoHuu(freshPrincipal, thaoTac, chuSoHuuId);

                    return hanhDongGhiTrongTransaction.apply(freshPrincipal);
                } finally {
                    if (resourceLock != null) {
                        resourceLock.unlock();
                    }
                }
            } finally {
                sessionLock.unlock();
            }
        } finally {
            accountLock.unlock();
        }
    }

    /**
     * Khóa tài khoản nguyên tử theo chuẩn thiết kế phiên (thiet-ke-phien-sprint-1.md §3.6, PHIEN-03, PHIEN-08, TS1-019, PX-03, PX-04):
     * - Khóa dòng tai_khoan trước, sau đó khóa các dòng phien_xac_thuc của tài khoản.
     * - Cập nhật trang_thai = 'bi_khoa', tăng moc_thu_hoi = moc_thu_hoi + 1.
     * - Thu hồi toàn bộ phiên đang hoạt động của tài khoản với ly_do_thu_hoi = 'khoa_tai_khoan'.
     */
    public TaiKhoan khoaTaiKhoanNguyenTu(UUID taiKhoanId, Instant thoiDiem) {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Instant now = thoiDiem != null ? thoiDiem : Instant.now();

        ReentrantLock accountLock = lockForAccount(taiKhoanId);
        accountLock.lock();
        try {
            TaiKhoan current = docTaiKhoanTuKhoChinh(taiKhoanId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + taiKhoanId));

            TaiKhoan lockedAndBumped = current
                    .capNhatTrangThai(TrangThaiTaiKhoan.BI_KHOA, now)
                    .tangMocThuHoi("san_sang", now);

            List<PhienXacThuc> sessions = docDanhSachPhienTheoTaiKhoan(taiKhoanId);
            for (PhienXacThuc session : sessions) {
                ReentrantLock sLock = lockForSession(session.getId());
                sLock.lock();
                try {
                    if (!session.isDaThuHoi()) {
                        PhienXacThuc revoked = session.thuHoi(PhienXacThuc.LY_DO_KHOA_TAI_KHOAN, now, true);
                        luuPhienVaoKhoChinh(revoked);
                    }
                } finally {
                    sLock.unlock();
                }
            }

            return luuTaiKhoanVaoKhoChinh(lockedAndBumped);
        } finally {
            accountLock.unlock();
        }
    }

    /**
     * Cập nhật vai trò tài khoản nguyên tử dưới khóa dòng tai_khoan (hỗ trợ kiểm thử và quản trị đổi vai trò đồng thời PX-04).
     */
    public TaiKhoan capNhatVaiTroTaiKhoanNguyenTu(UUID taiKhoanId, VaiTroTaiKhoan vaiTroMoi, Instant thoiDiem) {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(vaiTroMoi, "vaiTroMoi không được null");
        Instant now = thoiDiem != null ? thoiDiem : Instant.now();

        ReentrantLock accountLock = lockForAccount(taiKhoanId);
        accountLock.lock();
        try {
            TaiKhoan current = docTaiKhoanTuKhoChinh(taiKhoanId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + taiKhoanId));

            TaiKhoan updated = TaiKhoan.khoiPhucTuLuuTru(
                    current.getId(),
                    current.getSupabaseAuthUserId(),
                    current.getEmail(),
                    current.getHoTen(),
                    current.getSoDienThoai(),
                    vaiTroMoi,
                    current.getTrangThai(),
                    current.isEmailDaXacMinh(),
                    current.getMocThuHoi(),
                    current.getTrangThaiDongBoAuth(),
                    current.getCapNhatAuthLuc(),
                    current.getTaoLuc(),
                    now
            );
            return luuTaiKhoanVaoKhoChinh(updated);
        } finally {
            accountLock.unlock();
        }
    }

    /**
     * Chạy một khối lệnh giữ khóa dòng tai_khoan (phục vụ kiểm thử đồng thời khóa giao dịch hoặc điều phối nội bộ).
     */
    public <T> T giuKhoaTaiKhoanTrongGiaoDich(UUID taiKhoanId, Supplier<T> action) {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(action, "action không được null");
        ReentrantLock accountLock = lockForAccount(taiKhoanId);
        accountLock.lock();
        try {
            return action.get();
        } finally {
            accountLock.unlock();
        }
    }

    public static void kiemTraKhongCoTruongTuKhaiBiCam(Map<String, ?> clientPayload) {
        String forbiddenField = MaTranQuyenSprint1.timTruongTuKhaiBiCam(clientPayload);
        if (forbiddenField != null) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Không được phép tự khai báo trường đặc quyền hoặc định danh sở hữu: " + forbiddenField,
                    List.of(new FieldErrorItem(
                            forbiddenField,
                            "TRUONG_KHONG_DUOC_PHEP",
                            "Trường này do hệ thống quyết định từ phiên xác thực và kho lưu trữ chính."
                    ))
            );
        }
    }

    private static void kiemTraVaiTroVaChuSoHuu(
            PrincipalPhienHienTai principal,
            ThaoTacNghiepVu thaoTac,
            UUID chuSoHuuTaiNguyenId
    ) {
        if (!MaTranQuyenSprint1.duocPhepTheoVaiTro(principal.getVaiTro(), thaoTac)) {
            throw new InsufficientPermissionException(
                    "Vai trò " + principal.getVaiTro().getGiaTri() + " không được phép thực hiện thao tác " + thaoTac.name() + "."
            );
        }
        if (thaoTac.isYeuCauChuSoHuuTaiNguyen()) {
            if (chuSoHuuTaiNguyenId == null || !principal.getTaiKhoanId().equals(chuSoHuuTaiNguyenId)) {
                throw new InsufficientPermissionException(
                        "Tài khoản hiện tại không phải chủ sở hữu của tài nguyên."
                );
            }
        }
    }

    private static void kiemTraTrangThaiTaiKhoanVaPhien(PhienXacThuc phien, TaiKhoan taiKhoan) {
        if (!phien.getTaiKhoanId().equals(taiKhoan.getId())) {
            throw new UnauthenticatedSessionException();
        }
        // Nếu tài khoản đang bị khóa, ưu tiên phản hồi 403 TAI_KHOAN_BI_KHOA (api-specification-sprint-1.md §8.7, TS1-019, PX-03)
        if (taiKhoan.getTrangThai() == TrangThaiTaiKhoan.BI_KHOA) {
            throw new AccountLockedException();
        }
        if (!taiKhoan.isEmailDaXacMinh()) {
            throw new EmailNotVerifiedException();
        }
        if (!"san_sang".equals(taiKhoan.getTrangThaiDongBoAuth())) {
            throw new UnauthenticatedSessionException();
        }
        if (phien.getMocThuHoiLucCap() != taiKhoan.getMocThuHoi()) {
            throw new UnauthenticatedSessionException();
        }
        if (!phien.conHieuLuc(taiKhoan)) {
            throw new UnauthenticatedSessionException();
        }
    }

    private boolean xacThucChuKyVaNguCanhCsrf(String csrfToken, String rawSessionCookieOrNull) {
        if (sessionLifecyclePort instanceof DefaultAuthBrowserSessionLifecycleAdapter defaultAdapter) {
            return defaultAdapter.verifyCsrfTokenForContext(csrfToken, rawSessionCookieOrNull);
        }
        if (csrfToken == null || csrfToken.isBlank()) {
            return false;
        }
        String[] parts = csrfToken.trim().split("\\.");
        return parts.length == 3 && "v1".equals(parts[0]) && !parts[1].isBlank() && !parts[2].isBlank();
    }

    private Optional<PhienXacThuc> docPhienTuKhoChinh(String maCookieBam) {
        try {
            return phienXacThucRepository.findByMaCookieBam(maCookieBam);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Kho lưu trữ phiên đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private Optional<PhienXacThuc> docPhienTuKhoChinhTheoId(UUID phienId, String maCookieBamFallback) {
        try {
            Optional<PhienXacThuc> byId = phienXacThucRepository.findById(phienId);
            if (byId.isPresent()) {
                return byId;
            }
            return phienXacThucRepository.findByMaCookieBam(maCookieBamFallback);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Kho lưu trữ phiên đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private Optional<TaiKhoan> docTaiKhoanTuKhoChinh(UUID taiKhoanId) {
        try {
            return taiKhoanRepository.findById(taiKhoanId);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Kho lưu trữ tài khoản đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private List<PhienXacThuc> docDanhSachPhienTheoTaiKhoan(UUID taiKhoanId) {
        try {
            return phienXacThucRepository.findByTaiKhoanId(taiKhoanId);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Kho lưu trữ phiên đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private PhienXacThuc luuPhienVaoKhoChinh(PhienXacThuc phien) {
        try {
            return phienXacThucRepository.save(phien);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Không thể cập nhật phiên xác thực do kho lưu trữ tạm gián đoạn.",
                    ex
            );
        }
    }

    private TaiKhoan luuTaiKhoanVaoKhoChinh(TaiKhoan taiKhoan) {
        try {
            return taiKhoanRepository.save(taiKhoan);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Không thể cập nhật tài khoản do kho lưu trữ tạm gián đoạn.",
                    ex
            );
        }
    }

    private ReentrantLock lockForAccount(UUID taiKhoanId) {
        return accountRowLocks.computeIfAbsent(taiKhoanId, ignored -> new ReentrantLock());
    }

    private ReentrantLock lockForSession(UUID phienId) {
        return sessionRowLocks.computeIfAbsent(phienId, ignored -> new ReentrantLock());
    }

    private ReentrantLock lockForResource(UUID resourceId) {
        return resourceRowLocks.computeIfAbsent(resourceId, ignored -> new ReentrantLock());
    }

    private static String normalizeCookieValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> parseAllowedOrigins(String allowedOriginsProperty) {
        if (allowedOriginsProperty == null || allowedOriginsProperty.isBlank()) {
            return List.of("https://localhost", "http://localhost:5173");
        }
        return Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
