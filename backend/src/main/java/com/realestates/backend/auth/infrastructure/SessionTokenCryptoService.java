package com.realestates.backend.auth.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dịch vụ mã hóa / giải mã token provider (access_token, refresh_token) bằng AES-256-GCM
 * theo đặc tả thiet-ke-phien-sprint-1.md §1 (PHIEN-05) và §6:
 * - Thuật toán: AES-256-GCM, nonce ngẫu nhiên 96-bit (12 byte), authentication tag 128-bit.
 * - AAD (Additional Authenticated Data) gắn chặt: phien_xac_thuc.id + loại token ("access"/"refresh") + phien_ban_khoa_ma_hoa.
 * - Hỗ trợ keyring nhiều phiên bản khóa (SESSION_TOKEN_KEYRING, SESSION_TOKEN_ACTIVE_KEY_VERSION) để xoay khóa an toàn.
 */
@Component
public class SessionTokenCryptoService {

    public static final String PURPOSE_ACCESS = "access";
    public static final String PURPOSE_REFRESH = "refresh";
    public static final String DEFAULT_KEY_VERSION = "v1";

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96-bit nonce
    private static final int GCM_TAG_LENGTH_BITS = 128; // 128-bit auth tag
    private static final String DEFAULT_DEV_PLACEHOLDER_KEY =
            "v1:THAY_BANG_KHOA_MA_HOA_PHIEN_LOCAL_32_BYTES_DEV";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SecretKey> keyRing;
    private final String activeKeyVersion;

    @Autowired
    public SessionTokenCryptoService(
            @Value("${app.security.session-token-keyring:${SESSION_TOKEN_KEYRING:}}") String configuredKeyring,
            @Value("${app.security.session-token-active-key-version:${SESSION_TOKEN_ACTIVE_KEY_VERSION:v1}}") String activeKeyVersion
    ) {
        String effectiveVersion = (activeKeyVersion != null && !activeKeyVersion.isBlank())
                ? activeKeyVersion.trim()
                : DEFAULT_KEY_VERSION;
        this.keyRing = parseKeyRing(configuredKeyring, effectiveVersion);
        if (!this.keyRing.containsKey(effectiveVersion)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy phiên bản khóa đang kích hoạt '" + effectiveVersion + "' trong keyring."
            );
        }
        this.activeKeyVersion = effectiveVersion;
    }

    public String getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public byte[] encryptAccessToken(UUID sessionId, String plainAccessToken) {
        return encryptToken(sessionId, PURPOSE_ACCESS, activeKeyVersion, plainAccessToken);
    }

    public byte[] encryptRefreshToken(UUID sessionId, String plainRefreshToken) {
        return encryptToken(sessionId, PURPOSE_REFRESH, activeKeyVersion, plainRefreshToken);
    }

    public String decryptAccessToken(UUID sessionId, String keyVersion, byte[] cipherPayload) {
        return decryptToken(sessionId, PURPOSE_ACCESS, keyVersion, cipherPayload);
    }

    public String decryptRefreshToken(UUID sessionId, String keyVersion, byte[] cipherPayload) {
        return decryptToken(sessionId, PURPOSE_REFRESH, keyVersion, cipherPayload);
    }

    public byte[] encryptToken(UUID sessionId, String purpose, String keyVersion, String plainToken) {
        Objects.requireNonNull(sessionId, "sessionId không được null");
        validatePurpose(purpose);
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalArgumentException("keyVersion không được rỗng");
        }
        if (plainToken == null || plainToken.isBlank()) {
            throw new IllegalArgumentException("plainToken không được rỗng");
        }

        String normalizedVersion = keyVersion.trim();
        SecretKey key = keyRing.get(normalizedVersion);
        if (key == null) {
            throw new IllegalArgumentException("Không tìm thấy khóa mã hóa cho phiên bản: " + normalizedVersion);
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            cipher.updateAAD(buildAad(sessionId, purpose, normalizedVersion));

            byte[] cipherTextWithTag = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH_BYTES + cipherTextWithTag.length);
            buffer.put(iv);
            buffer.put(cipherTextWithTag);
            return buffer.array();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể mã hóa token phiên bằng AES-256-GCM.", ex);
        }
    }

    public String decryptToken(UUID sessionId, String purpose, String keyVersion, byte[] cipherPayload) {
        Objects.requireNonNull(sessionId, "sessionId không được null");
        validatePurpose(purpose);
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalArgumentException("keyVersion không được rỗng");
        }
        if (cipherPayload == null || cipherPayload.length <= GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw new IllegalArgumentException("Dữ liệu mã hóa AES-256-GCM không hợp lệ hoặc bị cắt cụt.");
        }

        String normalizedVersion = keyVersion.trim();
        SecretKey key = keyRing.get(normalizedVersion);
        if (key == null) {
            throw new IllegalArgumentException("Không tìm thấy khóa giải mã cho phiên bản: " + normalizedVersion);
        }

        try {
            byte[] iv = Arrays.copyOfRange(cipherPayload, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherTextWithTag = Arrays.copyOfRange(cipherPayload, GCM_IV_LENGTH_BYTES, cipherPayload.length);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            cipher.updateAAD(buildAad(sessionId, purpose, normalizedVersion));

            byte[] plainBytes = cipher.doFinal(cipherTextWithTag);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Giải mã token phiên AES-256-GCM thất bại (sai khóa hoặc AAD/dữ liệu bị can thiệp).", ex);
        }
    }

    private static byte[] buildAad(UUID sessionId, String purpose, String keyVersion) {
        String aadString = "se214|phien_xac_thuc|" + sessionId + "|" + purpose + "|" + keyVersion;
        return aadString.getBytes(StandardCharsets.UTF_8);
    }

    private static void validatePurpose(String purpose) {
        if (!PURPOSE_ACCESS.equals(purpose) && !PURPOSE_REFRESH.equals(purpose)) {
            throw new IllegalArgumentException("purpose chỉ chấp nhận 'access' hoặc 'refresh'");
        }
    }

    private static Map<String, SecretKey> parseKeyRing(String rawKeyring, String activeVersion) {
        String source = (rawKeyring != null && !rawKeyring.isBlank())
                ? rawKeyring.trim()
                : DEFAULT_DEV_PLACEHOLDER_KEY;
        Map<String, SecretKey> parsed = new LinkedHashMap<>();
        String[] entries = source.split("[,;]");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIdx = trimmed.indexOf(':');
            String version;
            String secretMaterial;
            if (colonIdx > 0) {
                version = trimmed.substring(0, colonIdx).trim();
                secretMaterial = trimmed.substring(colonIdx + 1).trim();
            } else {
                version = activeVersion;
                secretMaterial = trimmed;
            }
            if (!version.isEmpty() && !secretMaterial.isEmpty()) {
                parsed.put(version, deriveAes256Key(secretMaterial));
            }
        }
        if (!parsed.containsKey(activeVersion) && source.equals(DEFAULT_DEV_PLACEHOLDER_KEY)) {
            parsed.put(activeVersion, deriveAes256Key(DEFAULT_DEV_PLACEHOLDER_KEY + ":" + activeVersion));
        }
        return Map.copyOf(parsed);
    }

    private static SecretKey deriveAes256Key(String secretMaterial) {
        try {
            byte[] decoded = tryDecodeBase6432Bytes(secretMaterial);
            if (decoded != null && decoded.length == 32) {
                return new SecretKeySpec(decoded, "AES");
            }
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(secretMaterial.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể khởi tạo khóa AES-256 cho phiên xác thực.", ex);
        }
    }

    private static byte[] tryDecodeBase6432Bytes(String material) {
        try {
            byte[] std = Base64.getDecoder().decode(material);
            if (std.length == 32) {
                return std;
            }
        } catch (IllegalArgumentException ignored) {
            // Không phải Base64 chuẩn
        }
        try {
            byte[] url = Base64.getUrlDecoder().decode(material);
            if (url.length == 32) {
                return url;
            }
        } catch (IllegalArgumentException ignored) {
            // Không phải Base64URL
        }
        return null;
    }
}
