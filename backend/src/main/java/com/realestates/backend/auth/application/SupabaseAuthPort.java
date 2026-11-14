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
     * Xác minh mã liên kết email một lần (token_hash) với mục đích cố định type = "signup" (AUTH-15 / UC-26).
     * Tuyệt đối không dùng type = "email" hoặc nhận type từ phía client.
     * Nếu Supabase Auth phát hành session tạm trong phản hồi verify, adapter tự thu hồi cục bộ (scope=local)
     * và không bao giờ trả access/refresh token ra ngoài.
     */
    default SupabaseVerifiedIdentity verifySignupEmailTokenHash(String tokenHash) {
        throw new UnsupportedOperationException("Chưa cài đặt verifySignupEmailTokenHash");
    }

    /**
     * Xác minh mã OTP 6 chữ số (0-9) hoặc mã xác minh đi kèm email với mục đích cố định type = "signup" (AUTH-15 / UC-26).
     */
    default SupabaseVerifiedIdentity verifySignupEmailOtp(String normalizedEmail, String sixDigitOtp) {
        return verifySignupEmailTokenHash(sixDigitOtp);
    }

    /**
     * Tra cứu người dùng đã tạo trên Supabase Auth theo email (phục vụ gửi lại xác minh khi kho in-memory khởi động lại
     * hoặc khi bước tạo hồ sơ nội bộ lúc đăng ký bị gián đoạn theo §8.4).
     */
    default java.util.Optional<SupabaseVerifiedIdentity> findProviderUserByEmail(String normalizedEmail) {
        return java.util.Optional.empty();
    }

    /**
     * Xóa bù trừ (compensation) user trên Supabase Auth nếu bước lưu bản ghi tai_khoan
     * phía ứng dụng gặp sự cố, giúp lần thử lại (retry đăng ký dở - TS1-007 / AUTH-10) không bị kẹt.
     */
    default void deleteUserOnCompensation(UUID supabaseAuthUserId) {
        // Mặc định no-op cho các stub đơn giản
    }

    /**
     * Xác thực email + mật khẩu với Supabase Auth (POST /auth/v1/token?grant_type=password)
     * phục vụ đăng nhập (UC-27 / FR-15 / AC-15.4 / PHIEN-02).
     */
    default SupabaseAuthenticatedSession authenticateWithPassword(String normalizedEmail, String rawPassword) {
        throw new UnsupportedOperationException("Chưa cài đặt authenticateWithPassword");
    }

    /**
     * Thu hồi cục bộ (scope=local) phiên Supabase Auth tương ứng với accessToken (best-effort)
     * khi quá trình kiểm tra hậu kỳ hoặc lưu phiên PostgreSQL thất bại (thiet-ke-phien-sprint-1.md §3.1).
     */
    default void revokeProviderSessionLocal(String accessToken) {
        // Mặc định no-op cho các stub đơn giản
    }
}
