package com.realestates.backend.auth.application;

import java.util.Objects;
import java.util.UUID;

/**
 * Kết quả làm mới phiên thành công từ Supabase Auth (POST /auth/v1/token?grant_type=refresh_token)
 * theo đặc tả thiet-ke-phien-sprint-1.md §4 (PHIEN-08 & TS1-071).
 */
public record SupabaseRefreshedSession(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UUID supabaseSessionId,
        UUID supabaseUserId
) {
    public SupabaseRefreshedSession {
        Objects.requireNonNull(accessToken, "accessToken không được null");
        Objects.requireNonNull(refreshToken, "refreshToken không được null");
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("expiresInSeconds phải > 0");
        }
    }
}
