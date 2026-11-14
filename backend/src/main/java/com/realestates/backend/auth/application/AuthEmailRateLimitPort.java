package com.realestates.backend.auth.application;

/**
 * Cổng kiểm soát tần suất (chống lạm dụng) cho các thao tác xác thực email theo một backend instance
 * (UC-26 / AC-15.3 / TS1-011 / AUTH-14 / api-specification-sprint-1.md §8.8).
 *
 * Ngưỡng đã chốt trong api-specification-sprint-1.md §8.8 & quyet-dinh-hoan-thien.md:
 * - Gửi lại email xác minh (cùng nhóm gửi email): 1 lần/60 giây/email, 5 lần/giờ/email và 20 lần/giờ/IP
 *   (áp giống nhau cho mọi email chuẩn hóa trước khi kiểm tra tài khoản tồn tại).
 * - Hoàn tất xác minh email (verify): 20 lần/5 phút/IP.
 * - Tối đa 10.000 khóa trong bộ nhớ; hết chỗ trả 429 QUA_NHIEU_YEU_CAU, không để map tăng vô hạn.
 */
public interface AuthEmailRateLimitPort {

    /**
     * Kiểm tra và ghi nhận lượt yêu cầu gửi email xác minh theo IP và email đã chuẩn hóa.
     * Phải được gọi trước khi kiểm tra sự tồn tại của tài khoản để tránh liệt kê tài khoản qua timing/rate-limit.
     */
    void checkAndRecordVerificationEmailRequest(String clientIp, String normalizedEmail);

    /**
     * Kiểm tra và ghi nhận lượt hoàn tất xác minh email (verify tokenHash) theo IP.
     */
    void checkAndRecordEmailVerifyAttempt(String clientIp);

    /**
     * Kiểm tra và ghi nhận lượt đăng nhập theo (IP + email chuẩn hóa: 10 lần/5 phút)
     * và theo IP (60 lần/5 phút) theo api-specification-sprint-1.md §8.8.
     */
    default void checkAndRecordLoginAttempt(String clientIp, String normalizedEmail) {
        // Mặc định no-op cho các stub đơn giản
    }
}
