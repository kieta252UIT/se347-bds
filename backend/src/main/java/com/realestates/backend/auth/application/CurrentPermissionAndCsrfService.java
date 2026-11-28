package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.MaTranQuyenSprint1;
import com.realestates.backend.auth.domain.MaTranQuyenSprint2;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AuthSessionMaintenanceService sessionMaintenanceService;
    private final TransactionOperations transactionOperations;

    /**
     * Bảng khóa cấp dòng theo thứ tự cố định: tai_khoan -> phien_xac_thuc -> tài nguyên nghiệp vụ
     * (thiet-ke-phien-sprint-1.md §2.2 Mục 4 & PHIEN-03), dùng chung trong JVM với luồng đăng xuất.
     */
    private static final ConcurrentHashMap<UUID, ReentrantLock> accountRowLocks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ReentrantLock> sessionRowLocks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ReentrantLock> resourceRowLocks = new ConcurrentHashMap<>();

    public CurrentPermissionAndCsrfService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            List<String> allowedOrigins
    ) {
        this(taiKhoanRepository, phienXacThucRepository, sessionLifecyclePort, allowedOrigins, null);
    }

    public CurrentPermissionAndCsrfService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            List<String> allowedOrigins,
            AuthSessionMaintenanceService sessionMaintenanceService
    ) {
        this(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecyclePort,
                allowedOrigins,
                sessionMaintenanceService,
                resolveDefaultTransactionOperations(taiKhoanRepository, phienXacThucRepository)
        );
    }

    public CurrentPermissionAndCsrfService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            List<String> allowedOrigins,
            AuthSessionMaintenanceService sessionMaintenanceService,
            TransactionOperations transactionOperations
    ) {
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.phienXacThucRepository = Objects.requireNonNull(phienXacThucRepository, "phienXacThucRepository không được null");
        this.sessionLifecyclePort = Objects.requireNonNull(sessionLifecyclePort, "sessionLifecyclePort không được null");
        this.sessionMaintenanceService = sessionMaintenanceService;
        this.transactionOperations = transactionOperations;
        this.allowedOrigins = (allowedOrigins == null || allowedOrigins.isEmpty())
                ? Set.of("https://localhost", "http://localhost:5173")
                : allowedOrigins.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    private static TransactionOperations resolveDefaultTransactionOperations(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository
    ) {
        DataSource dataSource = phienXacThucRepository.getDataSource() != null
                ? phienXacThucRepository.getDataSource()
                : taiKhoanRepository.getDataSource();
        return dataSource != null
                ? new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                : null;
    }

    @Autowired
    public CurrentPermissionAndCsrfService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            @Value("${app.security.allowed-origins:https://localhost,http://localhost:5173}") String allowedOriginsProperty,
            @Autowired(required = false) AuthSessionMaintenanceService sessionMaintenanceService
    ) {
        this(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecyclePort,
                parseAllowedOrigins(allowedOriginsProperty),
                sessionMaintenanceService
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

        if (sessionMaintenanceService != null) {
            return sessionMaintenanceService.duyTriVaKiemTraPhien(rawCookie);
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
        if (sessionMaintenanceService != null) {
            phien = sessionMaintenanceService.refreshAccessTokenNeuCan(phien, taiKhoan);
        }
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
     * Với thao tác yêu cầu đăng nhập, xác thực phiên và kiểm tra vai trò RBAC trước khi kiểm tra payload
     * để khách hoặc người sai vai trò nhận đúng 401/403 thay vì lộ quy tắc kiểm tra trường.
     */
    public PrincipalPhienHienTai kiemTraQuyenThaoTac(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            UUID chuSoHuuTaiNguyenId,
            Map<String, ?> clientPayload
    ) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");

        if (thaoTac.isChoPhepKhach()) {
            kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);
            return phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookieValue).orElse(null);
        }

        PrincipalPhienHienTai principal = kiemTraPhienVaQuyenHienTai(rawSessionCookieValue);
        kiemTraVaiTro(principal, thaoTac);
        kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);
        if (thaoTac.isYeuCauChuSoHuuTaiNguyen()) {
            kiemTraChuSoHuu(principal, chuSoHuuTaiNguyenId, null);
        }
        return principal;
    }

    /**
     * Kiểm tra quyền thực hiện thao tác nghiệp vụ với tuỳ chọn che giấu sự tồn tại của tài nguyên riêng tư
     * (ví dụ: GET /api/listings/{listingId}/boost-quote hoặc GET /api/me/listings/{listingId} — S2-02 Bảng 1 & Bảng 2):
     * - Kiểm tra phiên và vai trò RBAC trước khi kiểm tra payload (sai vai trò vẫn trả 403 KHONG_DU_QUYEN).
     * - Sau khi đúng vai trò, nếu tài nguyên không tồn tại (chuSoHuuTaiNguyenId == null) hoặc khác chủ sở hữu,
     *   ném ngoại lệ do {@code ngoaiLeKhiKhongPhaiChuSoHuu} cung cấp (ví dụ ListingNotFoundException -> 404 KHONG_TIM_THAY).
     */
    public PrincipalPhienHienTai kiemTraQuyenThaoTacCheTonTaiTaiNguyen(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            Supplier<UUID> docChuSoHuuTaiNguyen,
            Supplier<? extends RuntimeException> ngoaiLeKhiKhongPhaiChuSoHuu,
            Map<String, ?> clientPayload
    ) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");
        Objects.requireNonNull(ngoaiLeKhiKhongPhaiChuSoHuu, "ngoaiLeKhiKhongPhaiChuSoHuu không được null");

        if (thaoTac.isChoPhepKhach()) {
            kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);
            return phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookieValue).orElse(null);
        }

        PrincipalPhienHienTai principal = kiemTraPhienVaQuyenHienTai(rawSessionCookieValue);
        kiemTraVaiTro(principal, thaoTac);
        kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);
        if (thaoTac.isYeuCauChuSoHuuTaiNguyen()) {
            UUID chuSoHuuId = docChuSoHuuTaiNguyen != null ? docChuSoHuuTaiNguyen.get() : null;
            if (chuSoHuuId == null || !principal.getTaiKhoanId().equals(chuSoHuuId)) {
                throw ngoaiLeKhiKhongPhaiChuSoHuu.get();
            }
        }
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
        Objects.requireNonNull(hanhDongGhiTrongTransaction, "hanhDongGhiTrongTransaction không được null");
        return thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
                rawSessionCookieValue,
                thaoTac,
                List.of(),
                docChuSoHuuTaiNguyenTrongKhoa,
                null,
                taiNguyenIdCanKhoa,
                CurrentPermissionAndCsrfService::kiemTraKhongCoTruongTuKhaiBiCam,
                clientPayload,
                (freshPrincipal, ignoredLockedAccounts) -> hanhDongGhiTrongTransaction.apply(freshPrincipal)
        );
    }

    /**
     * Biến thể giao dịch ghi dành cho các thao tác trên tài nguyên riêng tư cần che giấu sự tồn tại bằng 404
     * khi tài nguyên không tồn tại hoặc thuộc chủ khác (ví dụ: UC-11 POST /api/listings/{listingId}/boost-purchases):
     * - Kiểm tra vai trò RBAC trước khi gọi {@code docChuSoHuuTaiNguyenTrongKhoa} (sai vai trò trả 403 KHONG_DU_QUYEN).
     * - Cho phép {@code docChuSoHuuTaiNguyenTrongKhoa} thực thi theo đúng thứ tự khóa (ví dụ khóa vi_dien_tu trước rồi khóa tin_bat_dong_san).
     * - Khi {@code chuSoHuuId == null} hoặc khác người gọi, ném ngoại lệ từ {@code ngoaiLeKhiKhongPhaiChuSoHuu} (ví dụ 404 KHONG_TIM_THAY).
     */
    public <T> T thucThiGhiCoCheTonTaiTaiNguyenTrongTransaction(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            Supplier<UUID> docChuSoHuuTaiNguyenTrongKhoa,
            Supplier<? extends RuntimeException> ngoaiLeKhiKhongPhaiChuSoHuu,
            UUID taiNguyenIdCanKhoa,
            Map<String, ?> clientPayload,
            Function<PrincipalPhienHienTai, T> hanhDongGhiTrongTransaction
    ) {
        Objects.requireNonNull(ngoaiLeKhiKhongPhaiChuSoHuu, "ngoaiLeKhiKhongPhaiChuSoHuu không được null");
        Objects.requireNonNull(hanhDongGhiTrongTransaction, "hanhDongGhiTrongTransaction không được null");
        return thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
                rawSessionCookieValue,
                thaoTac,
                List.of(),
                docChuSoHuuTaiNguyenTrongKhoa,
                ngoaiLeKhiKhongPhaiChuSoHuu,
                taiNguyenIdCanKhoa,
                CurrentPermissionAndCsrfService::kiemTraKhongCoTruongTuKhaiBiCam,
                clientPayload,
                (freshPrincipal, ignoredLockedAccounts) -> hanhDongGhiTrongTransaction.apply(freshPrincipal)
        );
    }

    /**
     * Bộ điều phối giao dịch ghi cho các luồng tác động hoặc tham chiếu khóa ngoại tới NHIỀU tài khoản
     * (UC-09 gửi báo cáo, UC-14a xử lý báo cáo, UC-14b đề xuất khóa, UC-16/17/18 quản trị tài khoản — S2-02 Bảng 4).
     */
    public <T> T thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            Collection<UUID> taiKhoanLienQuanIds,
            Supplier<UUID> docChuSoHuuTaiNguyenTrongKhoa,
            UUID taiNguyenIdCanKhoa,
            Consumer<Map<String, ?>> trinhKiemTraPayload,
            Map<String, ?> clientPayload,
            BiFunction<PrincipalPhienHienTai, Map<UUID, TaiKhoan>, T> hanhDongGhiTrongTransaction
    ) {
        return thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
                rawSessionCookieValue,
                thaoTac,
                taiKhoanLienQuanIds,
                docChuSoHuuTaiNguyenTrongKhoa,
                null,
                taiNguyenIdCanKhoa,
                trinhKiemTraPayload,
                clientPayload,
                hanhDongGhiTrongTransaction
        );
    }

    public <T> T thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
            String rawSessionCookieValue,
            ThaoTacNghiepVu thaoTac,
            Collection<UUID> taiKhoanLienQuanIds,
            Supplier<UUID> docChuSoHuuTaiNguyenTrongKhoa,
            Supplier<? extends RuntimeException> ngoaiLeKhiKhongPhaiChuSoHuu,
            UUID taiNguyenIdCanKhoa,
            Consumer<Map<String, ?>> trinhKiemTraPayload,
            Map<String, ?> clientPayload,
            BiFunction<PrincipalPhienHienTai, Map<UUID, TaiKhoan>, T> hanhDongGhiTrongTransaction
    ) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");
        Objects.requireNonNull(hanhDongGhiTrongTransaction, "hanhDongGhiTrongTransaction không được null");

        // Kiểm tra vòng ngoài phiên và vai trò RBAC trước khi kiểm tra allowlist/blacklist của payload
        // để khách chưa đăng nhập (401), tài khoản bị khóa (403) hoặc sai vai trò (403) không dò được cấu trúc DTO quản trị.
        PrincipalPhienHienTai initialPrincipal = kiemTraPhienVaQuyenHienTai(rawSessionCookieValue);
        kiemTraVaiTro(initialPrincipal, thaoTac);

        if (trinhKiemTraPayload != null) {
            trinhKiemTraPayload.accept(clientPayload);
        } else {
            kiemTraKhongCoTruongTuKhaiBiCam(clientPayload);
        }

        List<UUID> allAccountIds = new ArrayList<>();
        allAccountIds.add(initialPrincipal.getTaiKhoanId());
        if (taiKhoanLienQuanIds != null) {
            allAccountIds.addAll(taiKhoanLienQuanIds);
        }
        List<UUID> orderedAccountIds = MaTranQuyenSprint2.sapXepThuTuKhoaUuid(allAccountIds);
        List<ReentrantLock> orderedAccountLocks = orderedAccountIds.stream()
                .map(this::lockForAccount)
                .toList();

        ReentrantLock sessionLock = lockForSession(initialPrincipal.getPhienId());
        ReentrantLock resourceLock = taiNguyenIdCanKhoa != null ? lockForResource(taiNguyenIdCanKhoa) : null;

        // Tuân thủ thứ tự khóa cố định: mọi tai_khoan (ORDER BY id ASC) -> phien_xac_thuc -> tài nguyên nghiệp vụ
        return withOrderedLocks(orderedAccountLocks, () -> {
            sessionLock.lock();
            try {
                if (resourceLock != null) {
                    resourceLock.lock();
                }
                try {
                    return executeInTransactionIfAvailable(() -> {
                        // [Bậc 1] Khóa toàn bộ dòng tai_khoan liên quan theo ORDER BY id ASC FOR UPDATE
                        List<TaiKhoan> lockedAccounts =
                                docDanhSachTaiKhoanTuKhoChinhForUpdateTheoThuTu(orderedAccountIds);
                        Map<UUID, TaiKhoan> lockedAccountMap = new LinkedHashMap<>();
                        for (TaiKhoan tk : lockedAccounts) {
                            lockedAccountMap.put(tk.getId(), tk);
                        }

                        // [Bậc 2] Khóa dòng phien_xac_thuc của người gọi
                        PhienXacThuc freshSession = docPhienTuKhoChinhTheoIdForUpdate(
                                        initialPrincipal.getPhienId(),
                                        initialPrincipal.getMaCookieBam()
                                )
                                .orElseThrow(UnauthenticatedSessionException::new);

                        if (freshSession.isDaThuHoi()) {
                            throw new UnauthenticatedSessionException();
                        }

                        TaiKhoan freshAccount = lockedAccountMap.get(freshSession.getTaiKhoanId());
                        if (freshAccount == null) {
                            throw new UnauthenticatedSessionException();
                        }

                        kiemTraTrangThaiTaiKhoanVaPhien(freshSession, freshAccount);

                        PrincipalPhienHienTai freshPrincipal =
                                PrincipalPhienHienTai.tuPhienVaTaiKhoan(freshSession, freshAccount);

                        // Kiểm tra vai trò RBAC trước khi truy cập tài nguyên để sai vai trò luôn nhận 403 KHONG_DU_QUYEN
                        kiemTraVaiTro(freshPrincipal, thaoTac);
                        if (thaoTac.isYeuCauChuSoHuuTaiNguyen()) {
                            UUID chuSoHuuId = docChuSoHuuTaiNguyenTrongKhoa != null
                                    ? docChuSoHuuTaiNguyenTrongKhoa.get()
                                    : null;
                            kiemTraChuSoHuu(freshPrincipal, chuSoHuuId, ngoaiLeKhiKhongPhaiChuSoHuu);
                        }

                        return hanhDongGhiTrongTransaction.apply(
                                freshPrincipal,
                                Collections.unmodifiableMap(lockedAccountMap)
                        );
                    });
                } finally {
                    if (resourceLock != null) {
                        resourceLock.unlock();
                    }
                }
            } finally {
                sessionLock.unlock();
            }
        });
    }

    /**
     * Khóa tài khoản nguyên tử theo chuẩn thiết kế phiên (thiet-ke-phien-sprint-1.md §3.6, PHIEN-03, PHIEN-08, TS1-019, PX-03, PX-04):
     * - Mở/tham gia một giao dịch CSDL bao trọn toàn bộ bước đọc khóa tai_khoan, thu hồi phiên và cập nhật tai_khoan
     *   (nếu lưu tài khoản thất bại ở bước cuối, mọi cập nhật thu hồi phiên đều được rollback nguyên tử).
     * - Khóa dòng tai_khoan trước, sau đó khóa các dòng phien_xac_thuc của tài khoản theo ORDER BY id ASC.
     * - Cập nhật trang_thai = 'bi_khoa', tăng moc_thu_hoi = moc_thu_hoi + 1.
     * - Thu hồi toàn bộ phiên đang hoạt động của tài khoản với ly_do_thu_hoi = 'khoa_tai_khoan'.
     */
    public TaiKhoan khoaTaiKhoanNguyenTu(UUID taiKhoanId, Instant thoiDiem) {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Instant now = thoiDiem != null ? thoiDiem : Instant.now();

        ReentrantLock accountLock = lockForAccount(taiKhoanId);
        accountLock.lock();
        try {
            return executeInTransactionIfAvailable(() -> {
                TaiKhoan current = docTaiKhoanTuKhoChinhForUpdate(taiKhoanId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + taiKhoanId));

                TaiKhoan lockedAndBumped = current
                        .capNhatTrangThai(TrangThaiTaiKhoan.BI_KHOA, now)
                        .tangMocThuHoi("san_sang", now);

                List<PhienXacThuc> rawSessions = docDanhSachPhienTheoTaiKhoan(taiKhoanId).stream()
                        .filter(Objects::nonNull)
                        .filter(s -> s.getId() != null)
                        .sorted(Comparator.comparing(PhienXacThuc::getId, MaTranQuyenSprint2.THU_TU_KHOA_UUID_POSTGRES))
                        .toList();
                Map<UUID, PhienXacThuc> uniqueSessionsById = new LinkedHashMap<>();
                for (PhienXacThuc s : rawSessions) {
                    uniqueSessionsById.putIfAbsent(s.getId(), s);
                }
                List<UUID> orderedSessionIds = MaTranQuyenSprint2.sapXepThuTuKhoaUuid(uniqueSessionsById.keySet());
                List<ReentrantLock> orderedSessionLocks = orderedSessionIds.stream()
                        .map(this::lockForSession)
                        .toList();
                List<PhienXacThuc> sessions = orderedSessionIds.stream()
                        .map(uniqueSessionsById::get)
                        .toList();

                return withOrderedLocks(orderedSessionLocks, () -> {
                    List<PhienXacThuc> originalUnrevokedSessions = new ArrayList<>();
                    try {
                        for (PhienXacThuc session : sessions) {
                            if (!session.isDaThuHoi()) {
                                PhienXacThuc freshSession = docPhienTuKhoChinhTheoIdForUpdate(
                                                session.getId(),
                                                session.getMaCookieBam()
                                        )
                                        .orElse(session);
                                if (!freshSession.isDaThuHoi()) {
                                    PhienXacThuc revoked = freshSession.thuHoi(PhienXacThuc.LY_DO_KHOA_TAI_KHOAN, now, true);
                                    luuPhienVaoKhoChinh(revoked);
                                    originalUnrevokedSessions.add(freshSession);
                                }
                            }
                        }

                        return luuTaiKhoanVaoKhoChinh(lockedAndBumped);
                    } catch (RuntimeException ex) {
                        // Khi chạy ở chế độ in-memory không có DataSourceTransactionManager, khôi phục lại toàn bộ các phiên
                        // đã thu hồi trước điểm lỗi (kể cả khi lỗi xảy ra ở lần lưu phiên thứ 2 hoặc ở lần lưu tài khoản cuối);
                        // khi chạy trên PostgreSQL có TransactionOperations, ngoại lệ ném ra tiếp tục kích hoạt rollback SQL toàn phần.
                        if (transactionOperations == null) {
                            for (int i = originalUnrevokedSessions.size() - 1; i >= 0; i--) {
                                phienXacThucRepository.save(originalUnrevokedSessions.get(i));
                            }
                        }
                        throw ex;
                    }
                });
            });
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

    public static void kiemTraTruongChoPhepTaoTaiKhoanQuanTri(Map<String, ?> clientPayload) {
        String invalidField = MaTranQuyenSprint2.timTruongKhongDuocPhepTaoTaiKhoanQuanTri(clientPayload);
        if (invalidField != null) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Trường không thuộc danh sách cho phép trên yêu cầu tạo tài khoản quản trị: " + invalidField,
                    List.of(new FieldErrorItem(
                            invalidField,
                            "TRUONG_KHONG_DUOC_PHEP",
                            "Chỉ cho phép các trường email, hoTen, soDienThoai, vaiTro, matKhau."
                    ))
            );
        }
    }

    public static void kiemTraTruongChoPhepCapNhatTaiKhoanQuanTri(Map<String, ?> clientPayload) {
        String invalidField = MaTranQuyenSprint2.timTruongKhongDuocPhepCapNhatTaiKhoanQuanTri(clientPayload);
        if (invalidField != null) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Trường không thuộc danh sách cho phép trên yêu cầu cập nhật tài khoản quản trị: " + invalidField,
                    List.of(new FieldErrorItem(
                            invalidField,
                            "TRUONG_KHONG_DUOC_PHEP",
                            "Chỉ cho phép các trường hoTen, soDienThoai, vaiTro."
                    ))
            );
        }
    }

    private static void kiemTraVaiTro(
            PrincipalPhienHienTai principal,
            ThaoTacNghiepVu thaoTac
    ) {
        if (!MaTranQuyenSprint1.duocPhepTheoVaiTro(principal.getVaiTro(), thaoTac)) {
            throw new InsufficientPermissionException(
                    "Vai trò " + principal.getVaiTro().getGiaTri() + " không được phép thực hiện thao tác " + thaoTac.name() + "."
            );
        }
    }

    private static void kiemTraChuSoHuu(
            PrincipalPhienHienTai principal,
            UUID chuSoHuuTaiNguyenId,
            Supplier<? extends RuntimeException> ngoaiLeKhiKhongPhaiChuSoHuu
    ) {
        if (chuSoHuuTaiNguyenId == null || !principal.getTaiKhoanId().equals(chuSoHuuTaiNguyenId)) {
            if (ngoaiLeKhiKhongPhaiChuSoHuu != null) {
                throw ngoaiLeKhiKhongPhaiChuSoHuu.get();
            }
            throw new InsufficientPermissionException(
                    "Tài khoản hiện tại không phải chủ sở hữu của tài nguyên."
            );
        }
    }

    private static void kiemTraVaiTroVaChuSoHuu(
            PrincipalPhienHienTai principal,
            ThaoTacNghiepVu thaoTac,
            UUID chuSoHuuTaiNguyenId
    ) {
        kiemTraVaiTro(principal, thaoTac);
        if (thaoTac.isYeuCauChuSoHuuTaiNguyen()) {
            kiemTraChuSoHuu(principal, chuSoHuuTaiNguyenId, null);
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

    private <T> T executeInTransactionIfAvailable(Supplier<T> action) {
        if (transactionOperations != null) {
            return transactionOperations.execute(status -> action.get());
        }
        return action.get();
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

    private Optional<PhienXacThuc> docPhienTuKhoChinhTheoIdForUpdate(UUID phienId, String maCookieBamFallback) {
        try {
            Optional<PhienXacThuc> byId = phienXacThucRepository.findByIdForUpdate(phienId);
            if (byId.isPresent()) {
                return byId;
            }
            return phienXacThucRepository.findByMaCookieBamForUpdate(maCookieBamFallback);
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

    private Optional<TaiKhoan> docTaiKhoanTuKhoChinhForUpdate(UUID taiKhoanId) {
        try {
            return taiKhoanRepository.findByIdForUpdate(taiKhoanId);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Kho lưu trữ tài khoản đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private List<TaiKhoan> docDanhSachTaiKhoanTuKhoChinhForUpdateTheoThuTu(Collection<UUID> orderedIds) {
        try {
            return taiKhoanRepository.findAllByIdsForUpdateInOrder(orderedIds);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(
                    "Kho lưu trữ tài khoản đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
    }

    private static <T> T withOrderedLocks(List<ReentrantLock> locks, Supplier<T> action) {
        int acquired = 0;
        try {
            for (ReentrantLock lock : locks) {
                lock.lock();
                acquired++;
            }
            return action.get();
        } finally {
            for (int i = acquired - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
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

    public static ReentrantLock sharedAccountLock(UUID taiKhoanId) {
        return accountRowLocks.computeIfAbsent(taiKhoanId, ignored -> new ReentrantLock());
    }

    public static ReentrantLock sharedSessionLock(UUID phienId) {
        return sessionRowLocks.computeIfAbsent(phienId, ignored -> new ReentrantLock());
    }

    private ReentrantLock lockForAccount(UUID taiKhoanId) {
        return sharedAccountLock(taiKhoanId);
    }

    private ReentrantLock lockForSession(UUID phienId) {
        return sharedSessionLock(phienId);
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
