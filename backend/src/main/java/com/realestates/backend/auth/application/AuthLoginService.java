package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.dto.DangNhapKetQua;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.dto.TaiKhoanHienTai;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProfileNeedsInspectionException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthSyncInProgressException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.SessionTokenCryptoService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ ứng dụng xử lý đăng nhập, khởi tạo phiên PostgreSQL (se214.phien_xac_thuc)
 * và phát hành giá trị cookie opaque + CSRF token theo đặc tả:
 * - UC-27 / FR-15 / AC-15.4 / TS1-012 / TS1-013
 * - thiet-ke-phien-sprint-1.md §1 (PHIEN-02..PHIEN-06), §2.1, §3.1, §6, PX-01, PX-10, PX-19
 * - api-specification-sprint-1.md §8.2, §8.3, §8.6, §8.8, §8.9
 */
@Service
public class AuthLoginService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_MAT_KHAU_UTF8_BYTES = 72;
    public static final int MIN_HO_TEN_CODE_POINTS = 1;
    public static final int MAX_HO_TEN_CODE_POINTS = 100;
    public static final int MIN_SO_DIEN_THOAI_CODE_POINTS = 1;
    public static final int MAX_SO_DIEN_THOAI_CODE_POINTS = 32;

    private static final Set<String> ALLOWED_LOGIN_FIELDS = Set.of("email", "matKhau");

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    );

    public record LoginExecutionResult(
            DangNhapKetQua responseBody,
            String rawSessionCookie,
            String csrfToken,
            PhienXacThuc createdSession
    ) {
    }

    private final TaiKhoanRepository taiKhoanRepository;
    private final PhienXacThucRepository phienXacThucRepository;
    private final SupabaseAuthPort supabaseAuthPort;
    private final AuthEmailRateLimitPort rateLimitPort;
    private final AuthBrowserSessionLifecyclePort browserSessionLifecyclePort;
    private final SessionTokenCryptoService sessionTokenCryptoService;
    private Clock clock;

    @Autowired
    public AuthLoginService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            SupabaseAuthPort supabaseAuthPort,
            AuthEmailRateLimitPort rateLimitPort,
            AuthBrowserSessionLifecyclePort browserSessionLifecyclePort,
            SessionTokenCryptoService sessionTokenCryptoService
    ) {
        this(
                taiKhoanRepository,
                phienXacThucRepository,
                supabaseAuthPort,
                rateLimitPort,
                browserSessionLifecyclePort,
                sessionTokenCryptoService,
                Clock.systemUTC()
        );
    }

    public AuthLoginService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            SupabaseAuthPort supabaseAuthPort,
            AuthEmailRateLimitPort rateLimitPort,
            AuthBrowserSessionLifecyclePort browserSessionLifecyclePort,
            SessionTokenCryptoService sessionTokenCryptoService,
            Clock clock
    ) {
        this.taiKhoanRepository = taiKhoanRepository;
        this.phienXacThucRepository = phienXacThucRepository;
        this.supabaseAuthPort = supabaseAuthPort;
        this.rateLimitPort = rateLimitPort;
        this.browserSessionLifecyclePort = browserSessionLifecyclePort;
        this.sessionTokenCryptoService = sessionTokenCryptoService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    /**
     * Thực hiện đăng nhập (POST /api/auth/login, operationId: dangNhap):
     * 1. Kiểm tra chặt schema DangNhapRequest (chỉ cho phép email và matKhau; matKhau chỉ kiểm không rỗng và <= 72 byte UTF-8).
     * 2. Kiểm tra rate limit 10 lần/5 phút/(IP + email chuẩn hóa) và 60 lần/5 phút/IP.
     * 3. Chụp trạng thái tai_khoan trước khi gọi Supabase Auth (lấy moc_thu_hoi_truoc).
     * 4. Gọi Supabase Auth xác thực mật khẩu.
     * 5. Kiểm tra hậu kỳ nguyên tử: đồng bộ hồ sơ nếu đăng ký dở, kiểm tra trang_thai_dong_bo_auth == 'san_sang',
     *    moc_thu_hoi_sau == moc_thu_hoi_truoc, email_da_xac_minh == true, trang_thai == 'hoat_dong'.
     * 6. Mã hóa token provider bằng AES-256-GCM (kèm AAD), thay thế đúng phiên cũ trên cùng trình duyệt (nếu có),
     *    lưu bản ghi PhienXacThuc với ma_cookie_bam SHA-256, phát hành cookie phiên opaque + CSRF token mới.
     */
    public LoginExecutionResult dangNhap(
            Map<String, Object> rawPayload,
            String clientIp,
            String currentBrowserSessionCookie
    ) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu đăng nhập không hợp lệ.",
                    List.of(
                            new FieldErrorItem("email", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Email là bắt buộc."),
                            new FieldErrorItem("matKhau", MA_LOI_DU_LIEU_KHONG_HOP_LE, "Mật khẩu là bắt buộc.")
                    )
            );
        }

        List<FieldErrorItem> fieldErrors = new ArrayList<>();

        for (String fieldName : rawPayload.keySet()) {
            if (!ALLOWED_LOGIN_FIELDS.contains(fieldName)) {
                fieldErrors.add(new FieldErrorItem(
                        fieldName,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Trường dữ liệu '" + fieldName + "' không được phép trong yêu cầu đăng nhập."
                ));
            }
        }

        String rawEmail = extractRequiredStringField(rawPayload, "email", fieldErrors);
        String rawMatKhau = extractRequiredStringField(rawPayload, "matKhau", fieldErrors);

        String normalizedEmail = null;
        if (rawEmail != null) {
            normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
            if (normalizedEmail.isEmpty()) {
                fieldErrors.add(new FieldErrorItem(
                        "email",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Email không được để trống."
                ));
            } else if (normalizedEmail.length() > MAX_EMAIL_LENGTH
                    || containsControlOrWhitespace(normalizedEmail)
                    || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                fieldErrors.add(new FieldErrorItem(
                        "email",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Email không đúng định dạng hoặc vượt quá 254 ký tự."
                ));
            }
        }

        if (rawMatKhau != null) {
            // Theo openapi-sprint-1.yaml (DangNhapRequest): chỉ kiểm không rỗng và trần kỹ thuật 72 byte UTF-8;
            // tuyệt đối không trim mật khẩu và không áp min 8 / chính sách tạo mật khẩu mới khi login.
            int utf8Bytes = rawMatKhau.getBytes(StandardCharsets.UTF_8).length;
            if (rawMatKhau.isEmpty() || utf8Bytes > MAX_MAT_KHAU_UTF8_BYTES) {
                fieldErrors.add(new FieldErrorItem(
                        "matKhau",
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "Mật khẩu không được để trống và tối đa 72 byte UTF-8."
                ));
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu đăng nhập không hợp lệ.",
                    fieldErrors
            );
        }

        // Kiểm tra rate limit đăng nhập: 10 lần/5 phút/(IP + email chuẩn hóa) và 60 lần/5 phút/IP
        rateLimitPort.checkAndRecordLoginAttempt(clientIp, normalizedEmail);

        // Chụp trạng thái tai_khoan trước khi gọi Supabase Auth (thiet-ke-phien-sprint-1.md §3.1)
        Optional<TaiKhoan> preLoginAccountOpt = taiKhoanRepository.findByEmail(normalizedEmail);
        Long preLoginMocThuHoi = preLoginAccountOpt.map(TaiKhoan::getMocThuHoi).orElse(null);

        // Gọi Supabase Auth xác thực email + mật khẩu
        SupabaseAuthenticatedSession authSession = supabaseAuthPort.authenticateWithPassword(
                normalizedEmail,
                rawMatKhau
        );

        try {
            return completeAuthenticatedSessionCreation(
                    normalizedEmail,
                    preLoginMocThuHoi,
                    authSession,
                    currentBrowserSessionCookie
            );
        } catch (RuntimeException ex) {
            // Best-effort thu hồi ngay phiên vừa sinh tại Supabase Auth ở scope=local
            // để không để lại phiên mồ côi khi hậu kiểm hoặc lưu DB thất bại (thiet-ke-phien-sprint-1.md §3.1)
            supabaseAuthPort.revokeProviderSessionLocal(authSession.accessToken());
            throw ex;
        }
    }

    private synchronized LoginExecutionResult completeAuthenticatedSessionCreation(
            String normalizedEmail,
            Long preLoginMocThuHoi,
            SupabaseAuthenticatedSession authSession,
            String currentBrowserSessionCookie
    ) {
        Instant now = clock.instant();
        TaiKhoan account = resolveAndValidateAccountAfterProviderAuth(
                normalizedEmail,
                preLoginMocThuHoi,
                authSession,
                now
        );

        // Tạo bản ghi phiên PostgreSQL (PhienXacThuc) kèm mã hóa AES-256-GCM cho access_token và refresh_token
        try {
            UUID newSessionId = UUID.randomUUID();
            String rawSessionCookie = browserSessionLifecyclePort.generateRawSessionCookie();
            String newMaCookieBam = browserSessionLifecyclePort.hashSessionCookie(rawSessionCookie);
            String activeKeyVersion = sessionTokenCryptoService.getActiveKeyVersion();

            byte[] encryptedAccessToken = sessionTokenCryptoService.encryptAccessToken(
                    newSessionId,
                    authSession.accessToken()
            );
            byte[] encryptedRefreshToken = sessionTokenCryptoService.encryptRefreshToken(
                    newSessionId,
                    authSession.refreshToken()
            );

            PhienXacThuc newSession = PhienXacThuc.taoPhienMoi(
                    newSessionId,
                    account.getId(),
                    newMaCookieBam,
                    account.getMocThuHoi(),
                    now,
                    authSession.supabaseSessionId(),
                    encryptedAccessToken,
                    encryptedRefreshToken,
                    authSession.accessTokenExpiresAt(),
                    activeKeyVersion
            );

            String oldMaCookieBam = (currentBrowserSessionCookie != null && !currentBrowserSessionCookie.isBlank())
                    ? browserSessionLifecyclePort.hashSessionCookie(currentBrowserSessionCookie)
                    : null;

            Optional<PhienXacThuc> revokedOldSessionOpt = phienXacThucRepository.replaceBrowserSessionAndSaveNew(
                    oldMaCookieBam,
                    newSession,
                    now
            );

            if (revokedOldSessionOpt.isPresent()) {
                cleanupRevokedOldBrowserSessionBestEffort(revokedOldSessionOpt.get());
            }

            String csrfToken = browserSessionLifecyclePort.issueSessionCsrfToken(rawSessionCookie);
            DangNhapKetQua responseBody = new DangNhapKetQua(
                    TaiKhoanHienTai.tuTaiKhoan(account),
                    csrfToken
            );

            return new LoginExecutionResult(responseBody, rawSessionCookie, csrfToken, newSession);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }
    }

    private TaiKhoan resolveAndValidateAccountAfterProviderAuth(
            String normalizedEmail,
            Long preLoginMocThuHoi,
            SupabaseAuthenticatedSession authSession,
            Instant now
    ) {
        UUID providerUserId = authSession.supabaseAuthUserId();
        Optional<TaiKhoan> bySupabaseId = taiKhoanRepository.findBySupabaseAuthUserId(providerUserId);
        Optional<TaiKhoan> byEmail = taiKhoanRepository.findByEmail(normalizedEmail);

        TaiKhoan account;
        if (bySupabaseId.isPresent()) {
            account = bySupabaseId.get();
            if (byEmail.isPresent() && !byEmail.get().getId().equals(account.getId())) {
                throw new AuthProfileNeedsInspectionException();
            }
        } else if (byEmail.isPresent()) {
            TaiKhoan existingByEmail = byEmail.get();
            if (!existingByEmail.getSupabaseAuthUserId().equals(providerUserId)) {
                throw new AuthProfileNeedsInspectionException();
            }
            account = existingByEmail;
        } else {
            // Chưa có bản ghi tai_khoan trong DB ứng dụng (ví dụ đăng ký dở trước đó trên provider - §8.4, §8.6)
            if (!authSession.emailConfirmed()) {
                throw new EmailNotVerifiedException();
            }
            String hoTenMeta = authSession.hoTenMetadata();
            if (!isValidHoTenMetadata(hoTenMeta)) {
                throw new AuthProfileNeedsInspectionException();
            }
            String normalizedPhone = normalizeOptionalPhoneMetadata(authSession.soDienThoaiMetadata());
            TaiKhoan recovered = TaiKhoan.taoNguoiDungDaXacMinh(
                    UUID.randomUUID(),
                    providerUserId,
                    normalizedEmail,
                    hoTenMeta.trim(),
                    normalizedPhone,
                    now
            );
            if (recovered.getVaiTro() != VaiTroTaiKhoan.NGUOI_DUNG) {
                throw new IllegalStateException("Khôi phục hồ sơ khi đăng nhập chỉ được gán vai trò Người dùng.");
            }
            try {
                account = taiKhoanRepository.save(recovered);
            } catch (RuntimeException ex) {
                throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
            }
        }

        // 1. Kiểm tra trạng thái đồng bộ bảo mật (dang_dat_lai / can_khoi_phuc -> 409 DONG_BO_XAC_THUC_DANG_XU_LY)
        if (!"san_sang".equals(account.getTrangThaiDongBoAuth())) {
            throw new AuthSyncInProgressException();
        }

        // 2. Kiểm tra moc_thu_hoi không bị tăng giữa lúc đang gọi Supabase Auth (PX-10 -> 503 TAM_GIAN_DOAN)
        if (preLoginMocThuHoi != null && account.getMocThuHoi() != preLoginMocThuHoi) {
            throw new AuthProviderUnavailableException();
        }

        // 3. Kiểm tra và đồng bộ trạng thái xác minh email (TS1-013 / §8.6 -> 403 EMAIL_CHUA_XAC_MINH, không tự gửi lại email)
        if (!account.isEmailDaXacMinh()) {
            if (!authSession.emailConfirmed()) {
                throw new EmailNotVerifiedException();
            }
            try {
                account = taiKhoanRepository.save(account.danhDauEmailDaXacMinh(now));
            } catch (RuntimeException ex) {
                throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
            }
        }

        // 4. Kiểm tra trạng thái khóa tài khoản (TS1-013 / AC-15.4 -> 403 TAI_KHOAN_BI_KHOA)
        if (account.getTrangThai() == TrangThaiTaiKhoan.BI_KHOA) {
            throw new AccountLockedException();
        }

        return account;
    }

    private void cleanupRevokedOldBrowserSessionBestEffort(PhienXacThuc revokedOldSession) {
        if (browserSessionLifecyclePort instanceof DefaultAuthBrowserSessionLifecycleAdapter defaultAdapter) {
            defaultAdapter.recordRevokedCookieHash(revokedOldSession.getMaCookieBam());
        }
        try {
            byte[] oldEncryptedAccess = revokedOldSession.getAccessTokenMaHoa();
            String keyVersion = revokedOldSession.getPhienBanKhoaMaHoa();
            if (oldEncryptedAccess != null && keyVersion != null) {
                String oldAccessToken = sessionTokenCryptoService.decryptAccessToken(
                        revokedOldSession.getId(),
                        keyVersion,
                        oldEncryptedAccess
                );
                supabaseAuthPort.revokeProviderSessionLocal(oldAccessToken);
            }
            phienXacThucRepository.save(revokedOldSession.daThuHoiProviderXong());
        } catch (Exception ignored) {
            // Best-effort: nếu chưa xóa được tại provider ngay lúc này thì cờ canThuHoiProvider=true vẫn được giữ
        }
    }

    private static boolean isValidHoTenMetadata(String rawHoTen) {
        if (rawHoTen == null) {
            return false;
        }
        String trimmed = rawHoTen.trim();
        if (trimmed.isBlank() || containsControlCharacter(trimmed)) {
            return false;
        }
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        return codePoints >= MIN_HO_TEN_CODE_POINTS && codePoints <= MAX_HO_TEN_CODE_POINTS;
    }

    private static String normalizeOptionalPhoneMetadata(String rawPhone) {
        if (rawPhone == null) {
            return null;
        }
        String trimmed = rawPhone.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        if (codePoints < MIN_SO_DIEN_THOAI_CODE_POINTS
                || codePoints > MAX_SO_DIEN_THOAI_CODE_POINTS
                || containsControlOrWhitespace(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String extractRequiredStringField(
            Map<String, Object> payload,
            String fieldName,
            List<FieldErrorItem> fieldErrors
    ) {
        if (!payload.containsKey(fieldName) || payload.get(fieldName) == null) {
            fieldErrors.add(new FieldErrorItem(
                    fieldName,
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Trường '" + fieldName + "' là bắt buộc."
            ));
            return null;
        }
        Object rawValue = payload.get(fieldName);
        if (!(rawValue instanceof String strValue)) {
            fieldErrors.add(new FieldErrorItem(
                    fieldName,
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Trường '" + fieldName + "' phải là chuỗi ký tự."
            ));
            return null;
        }
        return strValue;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static boolean containsControlOrWhitespace(String value) {
        return value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || Character.isWhitespace(cp));
    }
}
