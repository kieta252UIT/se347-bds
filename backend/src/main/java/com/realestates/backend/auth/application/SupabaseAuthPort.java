package com.realestates.backend.auth.application;

import java.util.UUID;

/**
 * Cổng giao tiếp với Supabase Auth phục vụ luồng đăng ký tài khoản Người dùng (UC-25 / AUTH-10).
 */
public interface SupabaseAuthPort {
    /**
     * Tạo user mới trên Supabase Auth ở trạng thái chưa xác minh email (email_confirm = false)
     * và cố định metadata vai_tro = "nguoi_dung".
     */
    SupabaseRegisteredUser registerUser(
            String normalizedEmail,
            String rawPassword,
            String normalizedHoTen,
            String normalizedSoDienThoai
    );

    /**
     * Kích hoạt gửi email xác minh đăng ký (type = signup) qua Supabase Auth.
     */
    void sendSignupVerificationEmail(String normalizedEmail);

    /**
     * Xóa bù trừ (compensation) user trên Supabase Auth nếu bước lưu bản ghi tai_khoan
     * phía ứng dụng gặp sự cố, giúp lần thử lại (retry đăng ký dở - TS1-007 / AUTH-10) không bị kẹt.
     */
    default void deleteUserOnCompensation(UUID supabaseAuthUserId) {
        // Mặc định no-op cho các stub đơn giản
    }
}
