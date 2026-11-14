package com.realestates.backend.auth.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Kết quả xác thực mật khẩu thành công từ Supabase Auth (POST /auth/v1/token?grant_type=password)
 * chỉ lưu giữ nội bộ tại tầng backend để mã hóa vào bảng se214.phien_xac_thuc (PHIEN-02, PHIEN-05).
 * Tuyệt đối không trả accessToken / refreshToken về cho trình duyệt.
 */
public record SupabaseAuthenticatedSession(
        UUID supabaseAuthUserId,
        String email,
        boolean emailConfirmed,
        UUID supabaseSessionId,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        String hoTenMetadata,
        String soDienThoaiMetadata
) {
    public SupabaseAuthenticatedSession {
        Objects.requireNonNull(supabaseAuthUserId, "supabaseAuthUserId không được null");
        Objects.requireNonNull(email, "email không được null");
        Objects.requireNonNull(supabaseSessionId, "supabaseSessionId không được null");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken không được rỗng");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken không được rỗng");
        }
        Objects.requireNonNull(accessTokenExpiresAt, "accessTokenExpiresAt không được null");
    }
}
