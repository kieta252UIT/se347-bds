package com.realestates.backend.auth.application;

import java.util.UUID;

/**
 * Thông tin danh tính tin cậy trả về từ Supabase Auth sau khi xác minh mã liên kết email một lần (type = signup)
 * theo api-specification-sprint-1.md §8.4, §8.5.
 * Access/refresh token phát sinh từ callback chỉ được dọn cục bộ phía adapter, tuyệt đối không trả ra ngoài.
 */
public record SupabaseVerifiedIdentity(
        UUID supabaseAuthUserId,
        String email,
        boolean emailConfirmed,
        String hoTenMetadata,
        String soDienThoaiMetadata
) {
}
