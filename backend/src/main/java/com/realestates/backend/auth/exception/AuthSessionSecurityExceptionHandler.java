package com.realestates.backend.auth.exception;

/**
 * Lớp mở rộng giữ lại tương thích ngược cho các unit test hoặc mockMvc khởi tạo trực tiếp.
 * Toàn bộ logic xử lý exception tập trung thống nhất tại {@link AuthExceptionHandler}.
 */
public class AuthSessionSecurityExceptionHandler extends AuthExceptionHandler {
}
