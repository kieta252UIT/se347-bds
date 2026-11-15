package com.realestates.backend.auth.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bộ mã hóa đối xứng AES-256-GCM cho access token và refresh token lưu trong bảng phien_xac_thuc
 * theo đặc tả thiet-ke-phien-sprint-1.md §5, §6 và PX-19.
 *
 * Cấu trúc phong bì mã hóa lưu trong bytea:
 * [Nonce ngẫu nhiên 96-bit (12 bytes)][Ciphertext + Authentication Tag 128-bit (16 bytes)]
 * Dữ liệu xác thực bổ sung (AAD): <phienId>|<purpose>|<keyVersion>
 */
@Component
public class SessionTokenCipher {

    public static final String MAC_DINH_KHOA_VERSION = "v1";
    public static final int GCM_NONCE_LENGTH_BYTES = 12; // 96-bit IV
    public static final int GCM_TAG_LENGTH_BITS = 128;  // 128-bit authentication tag
    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] activeSecretKeyBytes;
    private final String activeKeyVersion;

    public SessionTokenCipher() {
        this("THAY_BANG_CHUOI_BI_MAT_MA_HOA_TOKEN_32_BYTES_CHO_LOCAL", MAC_DINH_KHOA_VERSION);
    }

    public SessionTokenCipher(
            @Value("${app.security.token-encryption-key:THAY_BANG_CHUOI_BI_MAT_MA_HOA_TOKEN_32_BYTES_CHO_LOCAL}") String rawSecretKey,
            @Value("${app.security.token-key-version:v1}") String activeKeyVersion
    ) {
        this.activeKeyVersion = (activeKeyVersion != null && !activeKeyVersion.isBlank())
                ? activeKeyVersion.trim()
                : MAC_DINH_KHOA_VERSION;

        byte[] rawBytes = rawSecretKey != null ? rawSecretKey.getBytes(StandardCharsets.UTF_8) : new byte[0];
        // Đảm bảo khóa đúng 256-bit (32 bytes)
        if (rawBytes.length >= 32) {
            this.activeSecretKeyBytes = Arrays.copyOf(rawBytes, 32);
        } else {
            byte[] padded = new byte[32];
            System.arraycopy(rawBytes, 0, padded, 0, rawBytes.length);
            this.activeSecretKeyBytes = padded;
        }
    }

    /**
     * Mã hóa chuỗi token thô (access token hoặc refresh token) bằng AES-256-GCM với nonce 96-bit ngẫu nhiên
     * và AAD gắn chặt với định danh phiên, mục đích và phiên bản khóa.
     */
    public byte[] encryptToken(String rawToken, String phienId, String purpose, String keyVersion) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        Objects.requireNonNull(phienId, "phienId không được null khi mã hóa token");
        Objects.requireNonNull(purpose, "purpose không được null khi mã hóa token");
        String versionToUse = (keyVersion != null && !keyVersion.isBlank()) ? keyVersion : this.activeKeyVersion;

        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(this.activeSecretKeyBytes, AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] aad = buildAad(phienId, purpose, versionToUse);
            cipher.updateAAD(aad);

            byte[] plainBytes = rawToken.getBytes(StandardCharsets.UTF_8);
            byte[] cipherAndTag = cipher.doFinal(plainBytes);

            ByteBuffer buffer = ByteBuffer.allocate(nonce.length + cipherAndTag.length);
            buffer.put(nonce);
            buffer.put(cipherAndTag);
            return buffer.array();
        } catch (Exception ex) {
            throw new SecurityException("Không thể mã hóa token phiên bằng AES-GCM.", ex);
        }
    }

    /**
     * Giải mã phong bì mã hóa AES-256-GCM.
     * Kiểm tra chặt chẽ tính toàn vẹn thông qua tag 128-bit và AAD. Ném SecurityException nếu bị sửa đổi (PX-19).
     */
    public String decryptToken(byte[] encryptedEnvelope, String phienId, String purpose, String keyVersion) {
        if (encryptedEnvelope == null || encryptedEnvelope.length <= GCM_NONCE_LENGTH_BYTES) {
            return null;
        }
        Objects.requireNonNull(phienId, "phienId không được null khi giải mã token");
        Objects.requireNonNull(purpose, "purpose không được null khi giải mã token");
        String versionToUse = (keyVersion != null && !keyVersion.isBlank()) ? keyVersion : this.activeKeyVersion;

        try {
            byte[] nonce = Arrays.copyOfRange(encryptedEnvelope, 0, GCM_NONCE_LENGTH_BYTES);
            byte[] cipherAndTag = Arrays.copyOfRange(encryptedEnvelope, GCM_NONCE_LENGTH_BYTES, encryptedEnvelope.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(this.activeSecretKeyBytes, AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] aad = buildAad(phienId, purpose, versionToUse);
            cipher.updateAAD(aad);

            byte[] decryptedBytes = cipher.doFinal(cipherAndTag);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new SecurityException("Giải mã token thất bại do ciphertext bị sửa đổi hoặc sai khóa/AAD (PX-19).", ex);
        }
    }

    public String getActiveKeyVersion() {
        return activeKeyVersion;
    }

    private static byte[] buildAad(String phienId, String purpose, String keyVersion) {
        String aadString = phienId.trim() + "|" + purpose.trim() + "|" + keyVersion.trim();
        return aadString.getBytes(StandardCharsets.UTF_8);
    }
}
