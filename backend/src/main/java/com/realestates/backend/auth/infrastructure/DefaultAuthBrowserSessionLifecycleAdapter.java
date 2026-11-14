package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter mặc định quản lý thu hồi phiên browser hiện tại và phát hành CSRF token ngữ cảnh anonymous
 * khi xác minh email thành công (AUTH-07, AUTH-09, PHIEN-05, PHIEN-06).
 */
@Component
public class DefaultAuthBrowserSessionLifecycleAdapter implements AuthBrowserSessionLifecyclePort {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final SecureRandom secureRandom = new SecureRandom();
    private final Set<String> revokedSessionCookieHashes = ConcurrentHashMap.newKeySet();
    private final String csrfHmacSecret;

    public DefaultAuthBrowserSessionLifecycleAdapter(
            @Value("${app.security.csrf-hmac-secret:THAY_BANG_CHUOI_BI_MAT_CSRF_LOCAL_32_BYTES}") String csrfHmacSecret
    ) {
        this.csrfHmacSecret = csrfHmacSecret;
    }

    @Override
    public void revokeCurrentBrowserSessionIfPresent(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            return;
        }
        String cookieHash = sha256Base64Url(rawSessionCookieValue.trim());
        revokedSessionCookieHashes.add(cookieHash);
    }

    @Override
    public String issueAnonymousCsrfToken() {
        byte[] nonceBytes = new byte[32];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String message = "v1|csrf|anonymous|" + nonce;
        String mac = hmacSha256Base64Url(message, csrfHmacSecret);
        return "v1." + nonce + "." + mac;
    }

    public boolean isSessionCookieRevoked(String rawSessionCookieValue) {
        if (rawSessionCookieValue == null || rawSessionCookieValue.isBlank()) {
            return false;
        }
        return revokedSessionCookieHashes.contains(sha256Base64Url(rawSessionCookieValue.trim()));
    }

    public void clearRevokedSessions() {
        revokedSessionCookieHashes.clear();
    }

    private static String sha256Base64Url(String input) {
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
