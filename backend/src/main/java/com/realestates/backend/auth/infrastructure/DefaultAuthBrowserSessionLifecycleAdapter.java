package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter mặc định quản lý vòng đời cookie phiên opaque (__Host-se214_session),
 * băm SHA-256 ma_cookie_bam, thu hồi phiên trình duyệt hiện tại và phát hành CSRF token
 * theo ngữ cảnh anonymous hoặc session (AUTH-07, AUTH-09, PHIEN-04, PHIEN-05, PHIEN-06).
 */
@Component
public class DefaultAuthBrowserSessionLifecycleAdapter implements AuthBrowserSessionLifecyclePort {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int SESSION_SECRET_BYTES = 32; // 256-bit opaque secret
    private static final int CSRF_NONCE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Set<String> revokedSessionCookieHashes = ConcurrentHashMap.newKeySet();
    private final String csrfHmacSecret;
    private final PhienXacThucRepository phienXacThucRepository;

    public DefaultAuthBrowserSessionLifecycleAdapter(
            String csrfHmacSecret
    ) {
        this(csrfHmacSecret, null);
    }

    @Autowired
    public DefaultAuthBrowserSessionLifecycleAdapter(
            @Value("${app.security.csrf-hmac-secret:THAY_BANG_CHUOI_BI_MAT_CSRF_LOCAL_32_BYTES}") String csrfHmacSecret,
            PhienXacThucRepository phienXacThucRepository
    ) {
        this.csrfHmacSecret = csrfHmacSecret;
        this.phienXacThucRepository = phienXacThucRepository;
    }

    @Override
    public String generateRawSessionCookie() {
        byte[] secretBytes = new byte[SESSION_SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }

    @Override
    public String hashSessionCookie(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            throw new IllegalArgumentException("rawSessionCookieValue không được rỗng");
        }
        return sha256Base64Url(rawSessionCookieValue.trim());
    }

    @Override
    public void revokeCurrentBrowserSessionIfPresent(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            return;
        }
        String cookieHash = sha256Base64Url(rawSessionCookieValue.trim());
        revokedSessionCookieHashes.add(cookieHash);
        if (phienXacThucRepository != null) {
            phienXacThucRepository.revokeByMaCookieBam(
                    cookieHash,
                    PhienXacThuc.LY_DO_XAC_MINH_EMAIL_CUNG_TRINH_DUYET,
                    Instant.now(),
                    true
            );
        }
    }

    public void recordRevokedCookieHash(String cookieHash) {
        if (cookieHash != null && !cookieHash.isBlank()) {
            revokedSessionCookieHashes.add(cookieHash.trim());
        }
    }

    @Override
    public String issueAnonymousCsrfToken() {
        byte[] nonceBytes = new byte[CSRF_NONCE_BYTES];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String message = "v1|csrf|anonymous|" + nonce;
        String mac = hmacSha256Base64Url(message, csrfHmacSecret);
        return "v1." + nonce + "." + mac;
    }

    @Override
    public String issueSessionCsrfToken(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            throw new IllegalArgumentException("rawSessionCookieValue không được rỗng khi cấp CSRF theo phiên");
        }
        String sessionContextHash = sha256Base64Url(rawSessionCookieValue.trim());
        byte[] nonceBytes = new byte[CSRF_NONCE_BYTES];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String message = "v1|csrf|" + sessionContextHash + "|" + nonce;
        String mac = hmacSha256Base64Url(message, csrfHmacSecret);
        return "v1." + nonce + "." + mac;
    }

    public boolean verifyCsrfTokenForContext(String csrfToken, String rawSessionCookieOrNull) {
        if (csrfToken == null || csrfToken.isBlank()) {
            return false;
        }
        String[] parts = csrfToken.trim().split("\\.");
        if (parts.length != 3 || !"v1".equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            return false;
        }
        String context = (rawSessionCookieOrNull != null && !rawSessionCookieOrNull.isBlank())
                ? sha256Base64Url(rawSessionCookieOrNull.trim())
                : "anonymous";
        String expectedMessage = "v1|csrf|" + context + "|" + parts[1];
        String expectedMac = hmacSha256Base64Url(expectedMessage, csrfHmacSecret);
        return MessageDigest.isEqual(
                expectedMac.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean isSessionCookieRevoked(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            return false;
        }
        String cookieHash = sha256Base64Url(rawSessionCookieValue.trim());
        if (revokedSessionCookieHashes.contains(cookieHash)) {
            return true;
        }
        if (phienXacThucRepository != null) {
            return phienXacThucRepository.findByMaCookieBam(cookieHash)
                    .map(PhienXacThuc::isDaThuHoi)
                    .orElse(false);
        }
        return false;
    }

    public void clearRevokedSessions() {
        revokedSessionCookieHashes.clear();
    }

    public static String sha256Base64Url(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tính SHA-256 cho cookie phiên.", ex);
        }
    }

    private static String hmacSha256Base64Url(String message, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            byte[] keyBytes = (secret != null && !secret.isBlank()
                    ? secret
                    : "THAY_BANG_CHUOI_BI_MAT_CSRF_LOCAL_32_BYTES").getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            byte[] signature = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể ký HMAC-SHA-256 cho CSRF token.", ex);
        }
    }
}
