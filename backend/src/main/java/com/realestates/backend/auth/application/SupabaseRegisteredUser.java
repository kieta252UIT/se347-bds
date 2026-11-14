package com.realestates.backend.auth.application;

import java.util.UUID;

/**
 * Thông tin định danh trả về từ Supabase Auth sau khi khởi tạo user mới.
 */
public record SupabaseRegisteredUser(
        UUID supabaseAuthUserId,
        String email,
        boolean verificationEmailAlreadyTriggered
) {
}
