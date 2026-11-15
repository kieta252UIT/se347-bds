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
import com.realestates.backend.auth.infrastructure.SessionTokenCipher;
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
    private final SessionTokenCipher sessionTokenCipher;
    private final SupabaseAuthPort supabaseAuthPort;

    @Value("${app.security.session-clear-on-restart:false}")
    private boolean sessionClearOnRestart = false;

    private final Instant serverBootTime = Instant.now();

    public AuthSessionMaintenanceService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            SessionTokenCipher sessionTokenCipher,
            SupabaseAuthPort supabaseAuthPort
    ) {
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.phienXacThucRepository = Objects.requireNonNull(phienXacThucRepository, "phienXacThucRepository không được null");
        this.sessionLifecyclePort = Objects.requireNonNull(sessionLifecyclePort, "sessionLifecyclePort không được null");
        this.sessionTokenCipher = sessionTokenCipher != null ? sessionTokenCipher : new SessionTokenCipher();
        this.supabaseAuthPort = supabaseAuthPort;
    }

    @Autowired
    public AuthSessionMaintenanceService(
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            SessionTokenCipher sessionTokenCipher
    ) {
        this(taiKhoanRepository, phienXacThucRepository, sessionLifecyclePort, sessionTokenCipher, null);
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

        // Khi cấu hình session-clear-on-restart bật: từ chối các phiên tạo trước thời điểm server khởi động lại (buộc đăng nhập lại)
        if (sessionClearOnRestart && phien.getTaoLuc() != null && phien.getTaoLuc().isBefore(serverBootTime)) {
            throw new UnauthenticatedSessionException("Phiên đăng nhập đã kết thúc do backend vừa khởi động lại.");
        }

        TaiKhoan taiKhoan = docTaiKhoanTuKhoChinh(phien.getTaiKhoanId())
                .orElseThrow(UnauthenticatedSessionException::new);

        kiemTraTrangThaiTaiKhoanVaPhien(phien, taiKhoan);

        // Kiểm tra và làm mới access token provider nếu cần theo PHIEN-08 (khi có cấu hình SupabaseAuthPort)
        PhienXacThuc activePhien = refreshAccessTokenNeuCan(phien, taiKhoan);

        return PrincipalPhienHienTai.tuPhienVaTaiKhoan(activePhien, taiKhoan);
    }

    /**
     * Đọc hồ sơ tài khoản hiện tại cho GET /api/me kèm hỗ trợ duy trì đăng nhập và refresh token.
     */
    public TaiKhoanHienTai layTaiKhoanHienTai(String rawSessionCookieValue) {
        PrincipalPhienHienTai principal = duyTriVaKiemTraPhien(rawSessionCookieValue);
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
     * Kiểm tra và thực hiện làm mới access token theo cơ chế Lease 30s + CAS versioning (PHIEN-08 & TS1-071).
     * Đảm bảo:
     * - Access JWT hết hạn kỹ thuật không làm đứt phiên ứng dụng (ADR-0012).
     * - Hai request đồng thời trên cùng phiên không gọi provider cạnh tranh và dùng chung kết quả mới.
     * - Nếu phiên bị thu hồi trong lúc chờ refresh, không hồi sinh phiên cũ.
     */
    public PhienXacThuc refreshAccessTokenNeuCan(PhienXacThuc phien, TaiKhoan taiKhoan) {
        if (supabaseAuthPort == null || phien == null || phien.isDaThuHoi()) {
            return phien;
        }

        Instant now = Instant.now();
        if (!phien.isCanRefreshAccessToken(now, TOKEN_REFRESH_BUFFER_SECONDS)) {
            // Token còn trên 60s, dùng credential hiện tại
            return phien;
        }

        // Nếu phiên không lưu credential provider (hoặc đã dọn sạch), bỏ qua bước refresh provider
        if (phien.getRefreshTokenMaHoa() == null) {
            return phien;
        }

        UUID leaseId = UUID.randomUUID();
        Instant leaseUntil = now.plusSeconds(LEASE_DURATION_SECONDS);

        // 1. Thử nhận lease 30s (nguyên tử dưới CAS/kho lưu trữ)
        Optional<PhienXacThuc> leasedOpt = phienXacThucRepository.tryAcquireRefreshLease(
                phien.getId(),
                leaseId,
                leaseUntil,
                now
        );

        if (leasedOpt.isEmpty()) {
            // Có worker khác đang giữ lease: chờ tối đa 2s để đọc lại kết quả đã làm mới (PHIEN-08 Mục 1)
            return choVaDocLaiPhienSauKhiWorkerKhacRefresh(phien.getId(), phien.getMaCookieBam());
        }

        PhienXacThuc leasedPhien = leasedOpt.get();
        String rawRefreshToken;
        try {
            rawRefreshToken = sessionTokenCipher.decryptToken(
                    leasedPhien.getRefreshTokenMaHoa(),
                    leasedPhien.getId().toString(),
                    "refresh",
                    leasedPhien.getPhienBanKhoaMaHoa()
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

        // 3. Mã hóa cặp token mới với nonce ngẫu nhiên mới và phiên bản khóa hiện hành
        String activeKeyVersion = sessionTokenCipher.getActiveKeyVersion();
        byte[] newAccessTokenMaHoa = sessionTokenCipher.encryptToken(
                refreshedSession.accessToken(),
                leasedPhien.getId().toString(),
                "access",
                activeKeyVersion
        );
        byte[] newRefreshTokenMaHoa = sessionTokenCipher.encryptToken(
                refreshedSession.refreshToken(),
                leasedPhien.getId().toString(),
                "refresh",
                activeKeyVersion
        );
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
