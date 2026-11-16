package com.realestates.backend.auth.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng lưu trữ thực thể Tài khoản (tai_khoan).
 */
public interface TaiKhoanRepository {
    boolean existsByEmail(String normalizedEmail);

    default Optional<TaiKhoan> findById(UUID id) {
        return Optional.empty();
    }

    Optional<TaiKhoan> findByEmail(String normalizedEmail);

    Optional<TaiKhoan> findBySupabaseAuthUserId(UUID supabaseAuthUserId);

    TaiKhoan save(TaiKhoan taiKhoan);
}
