package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.dto.DangXuatKetQua;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.SessionTokenCryptoService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Use case: Đăng xuất phiên hiện tại và thu hồi bền vững; chặn sử dụng lại phiên cũ
 * (UC-28, FR-15, AC-15.4, PHIEN-04, PHIEN-06, PHIEN-11).
 *
 * Chịu trách nhiệm:
 * 1. Thu hồi đúng phiên ứng với cookie trên browser hiện tại trong cơ sở dữ liệu PostgreSQL.
 *    Tuyệt đối không ảnh hưởng đến các phiên đăng nhập khác của cùng người dùng trên các browser/thiết bị khác.
 * 2. Gọi Supabase Auth thu hồi phiên phạm vi local (best-effort, không chặn hoàn tất đăng xuất).
 * 3. Chặn tái sử dụng cookie phiên cũ: phiên đã thu hồi bị từ chối 401 trên mọi request kế tiếp, kể cả sau khi restart backend.
 * 4. Phát hành token CSRF mới ở ngữ cảnh khách (anonymous).
 * 5. Xử lý idempotent: yêu cầu logout khi không có cookie hoặc phiên đã thu hồi vẫn trả 200 OK sau khi qua kiểm tra Origin/CSRF.
 * 6. Báo 503 nếu kho lưu trữ dữ liệu gặp sự cố trước khi commit thu hồi (Store chưa commit thu hồi thì 503).
 */
@Service
public class AuthLogoutService {

    private final PhienXacThucRepository phienXacThucRepository;
    private final AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    private final SessionTokenCryptoService sessionTokenCryptoService;
    private final SupabaseAuthPort supabaseAuthPort;

    @Autowired
    public AuthLogoutService(
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort,
            SessionTokenCryptoService sessionTokenCryptoService,
            SupabaseAuthPort supabaseAuthPort
    ) {
        this.phienXacThucRepository = Objects.requireNonNull(phienXacThucRepository, "phienXacThucRepository không được null");
        this.sessionLifecyclePort = Objects.requireNonNull(sessionLifecyclePort, "sessionLifecyclePort không được null");
        this.sessionTokenCryptoService = sessionTokenCryptoService;
        this.supabaseAuthPort = supabaseAuthPort;
    }

    public AuthLogoutService(
            PhienXacThucRepository phienXacThucRepository,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort
    ) {
        this(phienXacThucRepository, sessionLifecyclePort, null, null);
    }

    /**
     * Thực hiện đăng xuất phiên hiện tại (UC-28).
     *
     * @param rawSessionCookieValue Giá trị cookie phiên thô (có thể null nếu cookie đã mất hoặc chưa đăng nhập)
     * @return Kết quả đăng xuất gồm trạng thái "da_dang_xuat" và csrfToken ngữ cảnh khách mới
     */
    public DangXuatKetQua dangXuat(String rawSessionCookieValue) {
        if (rawSessionCookieValue != null && !rawSessionCookieValue.isBlank()) {
            String rawCookie = rawSessionCookieValue.trim();
            String maCookieBam = sessionLifecyclePort.hashSessionCookie(rawCookie);

            Optional<PhienXacThuc> existingOpt;
            try {
                existingOpt = phienXacThucRepository.findByMaCookieBam(maCookieBam);
            } catch (AuthProviderUnavailableException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new AuthProviderUnavailableException(
                        "Kho lưu trữ phiên gặp sự cố khi tra cứu phiên đăng xuất.",
                        ex
                );
            }

            // 1. Thu hồi bền vững phiên tương ứng trong CSDL trước khi dọn dẹp RAM / Supabase.
            Optional<PhienXacThuc> revokedOpt;
            try {
                revokedOpt = phienXacThucRepository.revokeByMaCookieBam(
                        maCookieBam,
                        PhienXacThuc.LY_DO_DANG_XUAT,
                        Instant.now(),
                        existingOpt.isPresent() && existingOpt.get().getAccessTokenMaHoa() != null
                );
            } catch (AuthProviderUnavailableException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new AuthProviderUnavailableException(
                        "Kho lưu trữ phiên gặp sự cố khi thu hồi phiên đăng xuất.",
                        ex
                );
            }

            // 2. Chỉ ghi nhận vào cache RAM của adapter sau khi DB đã commit thu hồi thành công
            if (sessionLifecyclePort instanceof DefaultAuthBrowserSessionLifecycleAdapter adapter) {
                adapter.recordRevokedCookieHash(maCookieBam);
            }

            // 3. Gọi Supabase Auth thu hồi phiên local (best-effort)
            if (existingOpt.isPresent() && !existingOpt.get().isDaThuHoi()) {
                boolean providerCleanupSuccess = donDepProviderSessionLocal(existingOpt.get());
                if (providerCleanupSuccess && revokedOpt.isPresent()) {
                    try {
                        phienXacThucRepository.save(revokedOpt.get().daThuHoiProviderXong());
                    } catch (Exception ignored) {
                        // Best-effort lưu trạng thái dọn credential
                    }
                }
            }
        }

        // Phát hành token CSRF mới ở ngữ cảnh khách sau khi đăng xuất
        String anonymousCsrfToken = sessionLifecyclePort.issueAnonymousCsrfToken();
        return DangXuatKetQua.thanhCong(anonymousCsrfToken);
    }

    private boolean donDepProviderSessionLocal(PhienXacThuc phien) {
        if (supabaseAuthPort == null || sessionTokenCryptoService == null || phien.getAccessTokenMaHoa() == null) {
            return false;
        }
        try {
            String rawAccessToken = sessionTokenCryptoService.decryptAccessToken(
                    phien.getId(),
                    phien.getPhienBanKhoaMaHoa(),
                    phien.getAccessTokenMaHoa()
            );
            supabaseAuthPort.revokeProviderSessionLocal(rawAccessToken);
            return true;
        } catch (Exception ignored) {
            // Best-effort cleanup theo PHIEN-11 / openapi: lỗi provider không phục hồi quyền app và không cản trở logout thành công
            return false;
        }
    }
}
