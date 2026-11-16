package com.realestates.backend.auth.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Thông tin Principal của phiên xác thực hiện tại sau khi đã qua kiểm tra hiệu lực phiên
 * và trạng thái tài khoản trên kho lưu trữ chính (PHIEN-03, PHIEN-04, ma-tran-quyen-va-trang-thai-sprint-1.md).
 */
public final class PrincipalPhienHienTai {

    private final UUID phienId;
    private final UUID taiKhoanId;
    private final String maCookieBam;
    private final String email;
    private final String hoTen;
    private final String soDienThoai;
    private final VaiTroTaiKhoan vaiTro;
    private final TrangThaiTaiKhoan trangThai;
    private final boolean emailDaXacMinh;
    private final long mocThuHoi;

    public PrincipalPhienHienTai(
            UUID phienId,
            UUID taiKhoanId,
            String maCookieBam,
            String email,
            String hoTen,
            String soDienThoai,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh,
            long mocThuHoi
    ) {
        this.phienId = Objects.requireNonNull(phienId, "phienId không được null");
        this.taiKhoanId = Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        this.maCookieBam = Objects.requireNonNull(maCookieBam, "maCookieBam không được null");
        this.email = Objects.requireNonNull(email, "email không được null");
        this.hoTen = Objects.requireNonNull(hoTen, "hoTen không được null");
        this.soDienThoai = soDienThoai;
        this.vaiTro = Objects.requireNonNull(vaiTro, "vaiTro không được null");
        this.trangThai = Objects.requireNonNull(trangThai, "trangThai không được null");
        this.emailDaXacMinh = emailDaXacMinh;
        this.mocThuHoi = mocThuHoi;
    }

    public static PrincipalPhienHienTai tuPhienVaTaiKhoan(PhienXacThuc phien, TaiKhoan taiKhoan) {
        Objects.requireNonNull(phien, "phien không được null");
        Objects.requireNonNull(taiKhoan, "taiKhoan không được null");
        return new PrincipalPhienHienTai(
                phien.getId(),
                taiKhoan.getId(),
                phien.getMaCookieBam(),
                taiKhoan.getEmail(),
                taiKhoan.getHoTen(),
                taiKhoan.getSoDienThoai(),
                taiKhoan.getVaiTro(),
                taiKhoan.getTrangThai(),
                taiKhoan.isEmailDaXacMinh(),
                taiKhoan.getMocThuHoi()
        );
    }

    public UUID getPhienId() {
        return phienId;
    }

    public UUID getTaiKhoanId() {
        return taiKhoanId;
    }

    public String getMaCookieBam() {
        return maCookieBam;
    }

    public String getEmail() {
        return email;
    }

    public String getHoTen() {
        return hoTen;
    }

    public String getSoDienThoai() {
        return soDienThoai;
    }

    public VaiTroTaiKhoan getVaiTro() {
        return vaiTro;
    }

    public TrangThaiTaiKhoan getTrangThai() {
        return trangThai;
    }

    public boolean isEmailDaXacMinh() {
        return emailDaXacMinh;
    }

    public long getMocThuHoi() {
        return mocThuHoi;
    }
}
