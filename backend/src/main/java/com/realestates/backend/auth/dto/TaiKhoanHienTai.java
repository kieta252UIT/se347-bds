package com.realestates.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import java.util.Objects;
import java.util.UUID;

/**
 * DTO biểu diễn thông tin Tài khoản hiện tại trả về cho Frontend theo schema TaiKhoanHienTai
 * trong openapi-sprint-1.yaml. Tuyệt đối không lộ supabaseAuthUserId, mocThuHoi hay token provider.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaiKhoanHienTai(
        UUID id,
        String email,
        String hoTen,
        String soDienThoai,
        VaiTroTaiKhoan vaiTro,
        TrangThaiTaiKhoan trangThai,
        boolean emailDaXacMinh
) {
    public static TaiKhoanHienTai tuTaiKhoan(TaiKhoan taiKhoan) {
        Objects.requireNonNull(taiKhoan, "taiKhoan không được null");
        return new TaiKhoanHienTai(
                taiKhoan.getId(),
                taiKhoan.getEmail(),
                taiKhoan.getHoTen(),
                taiKhoan.getSoDienThoai(),
                taiKhoan.getVaiTro(),
                taiKhoan.getTrangThai(),
                taiKhoan.isEmailDaXacMinh()
        );
    }
}
