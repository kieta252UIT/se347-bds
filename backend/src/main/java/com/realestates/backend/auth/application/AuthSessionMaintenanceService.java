package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.dto.TaiKhoanHienTai;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.auth.infrastructure.SessionTokenCryptoService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ duy trì đăng nhập qua đóng/mở trình duyệt và khởi động lại backend (S1-26)
 * theo đặc tả ADR-0012, PHIEN-02, PHIEN-08, PHIEN-15, TS1-014, TS1-015, TS1-020, TS1-071, PX-01.
 *
 * Chịu trách nhiệm:
 * 1. Duy trì phiên bền vững trong PostgreSQL qua lần đóng/mở trình duyệt thường và restart backend.
 * 2. Độc lập nhiều browser/profile của cùng một người dùng (TS1-014).
 * 3. Gia hạn trượt cookie __Host-se214_session (Max-Age=31536000) trên GET /api/me thành công (PHIEN-02, PHIEN-15).
 * 4. Tự động làm mới access token Supabase Auth theo nhu cầu dưới lease 30s + CAS versioning khi access JWT hết hạn (PHIEN-08 & TS1-071).
 * 5. Chống mất credential khi mạng/DB gián đoạn tạm thời (503 không xóa cookie) (TS1-015 & PX-18).
 */
@Service
public class AuthSessionMaintenanceService {

    public static final long TOKEN_REFRESH_BUFFER_SECONDS = 60L; // Refresh khi access token còn dưới 60s
    public static final long LEASE_DURATION_SECONDS = 30L;       // Lease 30s theo PHIEN-08
    public static final long MAX_LEASE_WAIT_MS = 2000L;          // Chờ tối đa 2s khi có lease đang chạy

    private final TaiKhoanRepository taiKhoanRepository;
    private final PhienXacThucRepository phienXacThucRepository;
    private final AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    private final SessionTokenCryptoService sessionTokenCryptoService;
    private final SupabaseAuthPort supabaseAuthPort;

    @Value("${app.security.session-clear-on-restart:false}")
    private boolean sessionClearOnRestart = false;

    private final Instant serverBootTime = Instant.now();

    @Autowired
    public AuthSessionMaintenanceService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            SessionTokenCryptoService sessionTokenCryptoService,
            SupabaseAuthPort supabaseAuthPort
    ) {
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.phienXacThucRepository = Objects.requireNonNull(phienXacThucRepository, "phienXacThucRepository không được null");
        this.sessionLifecyclePort = Objects.requireNonNull(sessionLifecyclePort, "sessionLifecyclePort không được null");
        this.sessionTokenCryptoService = sessionTokenCryptoService;
        this.supabaseAuthPort = supabaseAuthPort;
    }

    public AuthSessionMaintenanceService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            SessionTokenCryptoService sessionTokenCryptoService
    ) {
        this(taiKhoanRepository, phienXacThucRepository, sessionLifecyclePort, sessionTokenCryptoService, null);
    }

    /**
     * Xác thực và duy trì phiên đăng nhập hiện tại từ cookie thô.
     * Kiểm tra trạng thái tài khoản, epoch, và tự động làm mới access token nếu token provider đã hết hạn kỹ thuật (PHIEN-08, ADR-0012).
     */
    public PrincipalPhienHienTai duyTriVaKiemTraPhien(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            throw new UnauthenticatedSessionException();
        }

        String maCookieBam = sessionLifecyclePort.hashSessionCookie(rawSessionCookieValue.trim());
        PhienXacThuc phien = docPhienTuKhoChinh(maCookieBam)
                .orElseThrow(UnauthenticatedSessionException::new);

        if (phien.isDaThuHoi()) {
            throw new UnauthenticatedSessionException();
        }

        if (sessionClearOnRestart && phien.getTaoLuc() != null && phien.getTaoLuc().isBefore(serverBootTime)) {
            phienXacThucRepository.revokeByMaCookieBam(
                    maCookieBam,
                    PhienXacThuc.LY_DO_THU_HOI_BAO_MAT,
                    Instant.now(),
                    false
            );
            throw new UnauthenticatedSessionException();
        }

        TaiKhoan taiKhoan = docTaiKhoanTuKhoChinh(phien.getTaiKhoanId())
                .orElseThrow(UnauthenticatedSessionException::new);

        kiemTraTrangThaiTaiKhoanVaPhien(phien, taiKhoan);

        // Kiểm tra và tự động làm mới access token nếu sắp hết hạn dưới cơ chế lease + CAS (PHIEN-08)
        phien = refreshAccessTokenNeuCan(phien, taiKhoan);

        return PrincipalPhienHienTai.tuPhienVaTaiKhoan(phien, taiKhoan);
    }

    /**
     * Tự động làm mới access token Supabase Auth nếu cần theo PHIEN-08.
     * Quy tắc:
     * - Chỉ làm mới khi access_token sắp hết hạn (buffer 60s) hoặc đã hết hạn kỹ thuật.
     * - Lease 30s đảm bảo chỉ duy nhất một thread/tiến trình gọi Supabase Auth.
     * - Các request đồng thời chờ tối đa 2s để dùng chung token mới.
     * - Nếu provider 503 tạm thời: giải phóng lease, giữ nguyên phiên hiện tại và ném 503 để client thử lại (chống mất credential).
     * - Nếu phiên bị thu hồi trong lúc chờ refresh, không hồi sinh phiên cũ.
     */
    public PhienXacThuc refreshAccessTokenNeuCan(PhienXacThuc phien, TaiKhoan taiKhoan) {
        if (supabaseAuthPort == null || phien == null || phien.isDaThuHoi()) {
            return phien;
        }

        Instant now = Instant.now();
        if (!phien.isCanRefreshAccessToken(now, TOKEN_REFRESH_BUFFER_SECONDS)) {
            return phien;
        }

        UUID leaseId = UUID.randomUUID();
        Instant leaseExpiresAt = now.plusSeconds(LEASE_DURATION_SECONDS);

        // Thử xin lease 30s
        Optional<PhienXacThuc> acquired = phienXacThucRepository.tryAcquireRefreshLease(phien.getId(), leaseId, leaseExpiresAt, now);
        if (acquired.isEmpty()) {
            // Có request khác đang refresh cho phiên này: chờ tối đa 2s để dùng chung token mới (PHIEN-08 Mục 1)
            return choVaDocLaiPhienSauKhiWorkerKhacRefresh(phien.getId(), phien.getMaCookieBam());
        }

        PhienXacThuc leasedPhien = acquired.get();
        if (leasedPhien.isDaThuHoi()) {
            phienXacThucRepository.releaseRefreshLease(phien.getId(), leaseId);
            throw new UnauthenticatedSessionException();
        }

        return thucHienGoiSupabaseVaLuuTokenMoi(leasedPhien, leaseId);
    }

    private PhienXacThuc thucHienGoiSupabaseVaLuuTokenMoi(PhienXacThuc leasedPhien, UUID leaseId) {
        if (sessionTokenCryptoService == null) {
            phienXacThucRepository.releaseRefreshLease(leasedPhien.getId(), leaseId);
            return leasedPhien;
        }

        // 1. Giải mã refresh_token hiện tại bằng SessionTokenCryptoService
        String rawRefreshToken;
        try {
            rawRefreshToken = sessionTokenCryptoService.decryptRefreshToken(
                    leasedPhien.getId(),
                    leasedPhien.getPhienBanKhoaMaHoa(),
                    leasedPhien.getRefreshTokenMaHoa()
            );
        } catch (Exception ex) {
            phienXacThucRepository.releaseRefreshLease(leasedPhien.getId(), leaseId);
            throw new SecurityException("Không thể giải mã refresh token để làm mới phiên (PX-19).", ex);
        }

        // 2. Gọi provider làm mới token ngoài transaction (timeout 10s theo PHIEN-07)
        SupabaseRefreshedSession refreshedSession;
        try {
            refreshedSession = supabaseAuthPort.refreshSessionToken(rawRefreshToken);
        } catch (AuthProviderUnavailableException ex) {
            // Lỗi mạng hoặc 503 tạm thời: giải phóng lease, không coi là logout (ADR-0012, TS1-015, PX-18)
            phienXacThucRepository.releaseRefreshLease(leasedPhien.getId(), leaseId);
            throw ex;
        } catch (Exception ex) {
            // Provider từ chối token (refresh token đã bị thu hồi/không dùng được): thu hồi phiên và yêu cầu đăng nhập lại
            phienXacThucRepository.releaseRefreshLease(leasedPhien.getId(), leaseId);
            phienXacThucRepository.revokeByMaCookieBam(
                    leasedPhien.getMaCookieBam(),
                    PhienXacThuc.LY_DO_REFRESH_KHONG_HOP_LE,
                    Instant.now(),
                    false
            );
            throw new UnauthenticatedSessionException("Phiên đăng nhập đã hết hiệu lực do token làm mới không hợp lệ.");
        }

        // 3. Mã hóa cặp token mới với nonce ngẫu nhiên mới và phiên bản khóa hiện hành bằng SessionTokenCryptoService
        byte[] newAccessTokenMaHoa = sessionTokenCryptoService.encryptAccessToken(
                leasedPhien.getId(),
                refreshedSession.accessToken()
        );
        byte[] newRefreshTokenMaHoa = sessionTokenCryptoService.encryptRefreshToken(
                leasedPhien.getId(),
                refreshedSession.refreshToken()
        );
        String activeKeyVersion = sessionTokenCryptoService.getActiveKeyVersion();
        Instant newExpiry = Instant.now().plusSeconds(refreshedSession.expiresInSeconds());

        // 4. Lưu lại với CAS version và kiểm tra leaseId còn khớp
        boolean casSuccess = phienXacThucRepository.updateRefreshedTokensWithCas(
                leasedPhien.getId(),
                leaseId,
                leasedPhien.getPhienBanCapNhat(),
                newAccessTokenMaHoa,
                newRefreshTokenMaHoa,
                newExpiry,
                activeKeyVersion,
                Instant.now()
        );

        if (!casSuccess) {
            // CAS thua (phiên đã bị thu hồi hoặc tài khoản bị khóa trong lúc chờ): không ghi đè và ném lỗi xác thực
            phienXacThucRepository.releaseRefreshLease(leasedPhien.getId(), leaseId);
            throw new UnauthenticatedSessionException("Phiên xác thực đã thay đổi trạng thái trong quá trình làm mới.");
        }

        return phienXacThucRepository.findById(leasedPhien.getId()).orElse(leasedPhien);
    }

    private PhienXacThuc choVaDocLaiPhienSauKhiWorkerKhacRefresh(UUID phienId, String maCookieBam) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < MAX_LEASE_WAIT_MS) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AuthProviderUnavailableException("Bị gián đoạn khi chờ làm mới phiên.", e);
            }

            Optional<PhienXacThuc> reloadedOpt = phienXacThucRepository.findById(phienId);
            if (reloadedOpt.isPresent()) {
                PhienXacThuc reloaded = reloadedOpt.get();
                if (reloaded.isDaThuHoi()) {
                    throw new UnauthenticatedSessionException();
                }
                // Nếu lease đã giải phóng (worker trước hoàn tất) và token đã mới
                if (reloaded.getRefreshLeaseId() == null
                        && !reloaded.isCanRefreshAccessToken(Instant.now(), TOKEN_REFRESH_BUFFER_SECONDS)) {
                    return reloaded;
                }
            }
        }
        // Quá 2s mà lease vẫn chưa xong: trả 503 để client thử lại (PHIEN-08 Mục 1)
        throw new AuthProviderUnavailableException("Dịch vụ xác thực đang bận xử lý làm mới phiên, vui lòng thử lại sau.");
    }

    private static void kiemTraTrangThaiTaiKhoanVaPhien(PhienXacThuc phien, TaiKhoan taiKhoan) {
        if (!phien.getTaiKhoanId().equals(taiKhoan.getId())) {
            throw new UnauthenticatedSessionException();
        }
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

    public boolean isSessionClearOnRestart() {
        return sessionClearOnRestart;
    }

    public void setSessionClearOnRestart(boolean sessionClearOnRestart) {
        this.sessionClearOnRestart = sessionClearOnRestart;
    }
}
