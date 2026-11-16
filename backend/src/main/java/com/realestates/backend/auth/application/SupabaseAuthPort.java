package com.realestates.backend.auth.application;

import java.util.UUID;

/**
 * Cổng giao tiếp với Supabase Auth phục vụ luồng đăng ký, xác minh email, đăng nhập, làm mới token và thu hồi phiên.
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
     * Làm mới access token và refresh token qua Supabase Auth (POST /auth/v1/token?grant_type=refresh_token)
     * theo cơ chế rotation của provider (PHIEN-08 & TS1-071).
     */
    default SupabaseRefreshedSession refreshSessionToken(String rawRefreshToken) {
        throw new UnsupportedOperationException("Chưa cài đặt refreshSessionToken");
    }

    /**
     * Thu hồi cục bộ (scope=local) phiên Supabase Auth tương ứng với accessToken (best-effort)
     * khi quá trình kiểm tra hậu kỳ hoặc lưu phiên PostgreSQL thất bại (thiet-ke-phien-sprint-1.md §3.1).
     */
    default void revokeProviderSessionLocal(String accessToken) {
        // Mặc định no-op cho các stub đơn giản
    }

    /**
     * Kích hoạt gửi email khôi phục mật khẩu (type = recovery) qua Supabase Auth (POST /auth/v1/recover)
     * theo UC-29, FR-15, AC-15.3 và openapi-sprint-1.yaml.
     * Phản hồi trung tính: email không tồn tại vẫn tiếp nhận 202, không tạo tài khoản mới.
     */
    default void sendPasswordRecoveryEmail(String normalizedEmail) {
        throw new UnsupportedOperationException("Chưa cài đặt sendPasswordRecoveryEmail");
    }

    /**
     * Xác minh mã liên kết recovery một lần (token_hash) với mục đích cố định type = "recovery"
     * theo UC-29 / FR-15 / AC-15.3 / AC-15.4 / §5.2.
     * Tuyệt đối không nhận type từ phía client. Nếu provider phát hành session tạm, adapter tự thu hồi cục bộ.
     */
    default SupabaseVerifiedIdentity verifyPasswordRecoveryTokenHash(String tokenHash) {
        throw new UnsupportedOperationException("Chưa cài đặt verifyPasswordRecoveryTokenHash");
    }

    /**
     * Cập nhật mật khẩu mới và dấu app_metadata.se214_password_reset_operation = operationId
     * qua Supabase Admin API (PUT /auth/v1/admin/users/{user_id}) trong cùng transaction provider
     * (thiet-ke-phien-sprint-1.md §5.1, §5.2 bước 3).
     */
    default void updatePasswordWithResetMarker(UUID supabaseAuthUserId, String rawPassword, UUID operationId) {
        throw new UnsupportedOperationException("Chưa cài đặt updatePasswordWithResetMarker");
    }

    /**
     * Tra cứu dấu app_metadata.se214_password_reset_operation của người dùng qua Supabase Admin API
     * (GET /auth/v1/admin/users/{user_id}) phục vụ worker đối chiếu và phục hồi khi lỗi giữa Auth-DB
     * (thiet-ke-phien-sprint-1.md §5.3, §5.4, PHIEN-10).
     */
    default java.util.Optional<String> getPasswordResetMarker(UUID supabaseAuthUserId) {
        return java.util.Optional.empty();
    }
}
