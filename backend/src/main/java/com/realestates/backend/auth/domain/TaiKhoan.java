package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TaiKhoan {
    private final UUID id;
    private final UUID supabaseAuthUserId;
    private final String email;
    private final String hoTen;
    private final String soDienThoai;
    private final VaiTroTaiKhoan vaiTro;
    private final TrangThaiTaiKhoan trangThai;
    private final boolean emailDaXacMinh;
    private final long mocThuHoi;
    private final String trangThaiDongBoAuth;
    private final UUID maThaoTacAuth;
    private final Instant capNhatAuthLuc;
    private final Instant taoLuc;
    private final Instant capNhatLuc;

    private TaiKhoan(
            UUID id,
            UUID supabaseAuthUserId,
            String email,
            String hoTen,
            String soDienThoai,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh,
            long mocThuHoi,
            String trangThaiDongBoAuth,
            UUID maThaoTacAuth,
            Instant capNhatAuthLuc,
            Instant taoLuc,
            Instant capNhatLuc
    ) {
        this.id = Objects.requireNonNull(id, "id không được null");
        this.supabaseAuthUserId = Objects.requireNonNull(supabaseAuthUserId, "supabaseAuthUserId không được null");
        this.email = Objects.requireNonNull(email, "email không được null");
        this.hoTen = Objects.requireNonNull(hoTen, "hoTen không được null");
        this.soDienThoai = soDienThoai;
        this.vaiTro = Objects.requireNonNull(vaiTro, "vaiTro không được null");
        this.trangThai = Objects.requireNonNull(trangThai, "trangThai không được null");
        this.emailDaXacMinh = emailDaXacMinh;
        this.mocThuHoi = mocThuHoi;
        this.trangThaiDongBoAuth = Objects.requireNonNull(trangThaiDongBoAuth, "trangThaiDongBoAuth không được null");
        this.maThaoTacAuth = maThaoTacAuth;
        this.capNhatAuthLuc = Objects.requireNonNull(capNhatAuthLuc, "capNhatAuthLuc không được null");
        this.taoLuc = Objects.requireNonNull(taoLuc, "taoLuc không được null");
        this.capNhatLuc = Objects.requireNonNull(capNhatLuc, "capNhatLuc không được null");

        if ("san_sang".equals(trangThaiDongBoAuth) && maThaoTacAuth != null) {
            throw new IllegalArgumentException("ck_tai_khoan_ma_auth: maThaoTacAuth phải null khi trangThaiDongBoAuth là san_sang");
        }
        if (!"san_sang".equals(trangThaiDongBoAuth) && maThaoTacAuth == null) {
            throw new IllegalArgumentException("ck_tai_khoan_ma_auth: maThaoTacAuth không được null khi trangThaiDongBoAuth là " + trangThaiDongBoAuth);
        }
    }

    public static TaiKhoan taoNguoiDungMoi(
            UUID id,
            UUID supabaseAuthUserId,
            String normalizedEmail,
            String normalizedHoTen,
            String normalizedSoDienThoai,
            Instant now
    ) {
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                id != null ? id : UUID.randomUUID(),
                supabaseAuthUserId,
                normalizedEmail,
                normalizedHoTen,
                normalizedSoDienThoai,
                VaiTroTaiKhoan.vaiTroDangKyCongKhai(),
                TrangThaiTaiKhoan.HOAT_DONG,
                false,
                0L,
                "san_sang",
                null,
                timestamp,
                timestamp,
                timestamp
        );
    }

    public static TaiKhoan taoNguoiDungDaXacMinh(
            UUID id,
            UUID supabaseAuthUserId,
            String normalizedEmail,
            String normalizedHoTen,
            String normalizedSoDienThoai,
            Instant now
    ) {
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                id != null ? id : UUID.randomUUID(),
                supabaseAuthUserId,
                normalizedEmail,
                normalizedHoTen,
                normalizedSoDienThoai,
                VaiTroTaiKhoan.vaiTroDangKyCongKhai(),
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                null,
                timestamp,
                timestamp,
                timestamp
        );
    }

    public static TaiKhoan khoiPhucTuLuuTru(
            UUID id,
            UUID supabaseAuthUserId,
            String normalizedEmail,
            String normalizedHoTen,
            String normalizedSoDienThoai,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh,
            long mocThuHoi,
            String trangThaiDongBoAuth,
            UUID maThaoTacAuth,
            Instant capNhatAuthLuc,
            Instant taoLuc,
            Instant capNhatLuc
    ) {
        if (mocThuHoi < 0) {
            throw new IllegalArgumentException("mocThuHoi phải >= 0");
        }
        if (!"san_sang".equals(trangThaiDongBoAuth)
                && !"dang_dat_lai".equals(trangThaiDongBoAuth)
                && !"can_khoi_phuc".equals(trangThaiDongBoAuth)) {
            throw new IllegalArgumentException("trangThaiDongBoAuth không hợp lệ: " + trangThaiDongBoAuth);
        }
        Instant fallbackNow = Instant.now();
        return new TaiKhoan(
                id != null ? id : UUID.randomUUID(),
                supabaseAuthUserId,
                normalizedEmail,
                normalizedHoTen,
                normalizedSoDienThoai,
                vaiTro,
                trangThai,
                emailDaXacMinh,
                mocThuHoi,
                trangThaiDongBoAuth,
                maThaoTacAuth,
                capNhatAuthLuc != null ? capNhatAuthLuc : fallbackNow,
                taoLuc != null ? taoLuc : fallbackNow,
                capNhatLuc != null ? capNhatLuc : fallbackNow
        );
    }

    public static TaiKhoan khoiPhucTuLuuTru(
            UUID id,
            UUID supabaseAuthUserId,
            String normalizedEmail,
            String normalizedHoTen,
            String normalizedSoDienThoai,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh,
            long mocThuHoi,
            String trangThaiDongBoAuth,
            Instant capNhatAuthLuc,
            Instant taoLuc,
            Instant capNhatLuc
    ) {
        return khoiPhucTuLuuTru(
                id,
                supabaseAuthUserId,
                normalizedEmail,
                normalizedHoTen,
                normalizedSoDienThoai,
                vaiTro,
                trangThai,
                emailDaXacMinh,
                mocThuHoi,
                trangThaiDongBoAuth,
                null,
                capNhatAuthLuc,
                taoLuc,
                capNhatLuc
        );
    }

    public TaiKhoan danhDauEmailDaXacMinh(Instant now) {
        Instant timestamp = now != null ? now : Instant.now();
        if (this.emailDaXacMinh) {
            return this;
        }
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                true,
                this.mocThuHoi,
                this.trangThaiDongBoAuth,
                this.maThaoTacAuth,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan capNhatTrangThai(TrangThaiTaiKhoan trangThaiMoi, Instant now) {
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                Objects.requireNonNull(trangThaiMoi, "trangThaiMoi không được null"),
                this.emailDaXacMinh,
                this.mocThuHoi,
                this.trangThaiDongBoAuth,
                this.maThaoTacAuth,
                this.capNhatAuthLuc,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan tangMocThuHoi(String trangThaiDongBoAuthMoi, Instant now) {
        String nextSyncState = trangThaiDongBoAuthMoi != null ? trangThaiDongBoAuthMoi : this.trangThaiDongBoAuth;
        UUID nextMaThaoTac = "san_sang".equals(nextSyncState)
                ? null
                : (this.maThaoTacAuth != null ? this.maThaoTacAuth : UUID.randomUUID());
        return tangMocThuHoi(nextSyncState, nextMaThaoTac, now);
    }

    public TaiKhoan tangMocThuHoi(String trangThaiDongBoAuthMoi, UUID maThaoTacAuth, Instant now) {
        Instant timestamp = now != null ? now : Instant.now();
        String nextSyncState = trangThaiDongBoAuthMoi != null ? trangThaiDongBoAuthMoi : this.trangThaiDongBoAuth;
        UUID nextMaThaoTac = "san_sang".equals(nextSyncState) ? null : maThaoTacAuth;
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                this.emailDaXacMinh,
                this.mocThuHoi + 1L,
                nextSyncState,
                nextMaThaoTac,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan capNhatTrangThaiDongBoAuth(String trangThaiDongBoAuthMoi, Instant now) {
        UUID opId = "san_sang".equals(trangThaiDongBoAuthMoi)
                ? null
                : (this.maThaoTacAuth != null ? this.maThaoTacAuth : UUID.randomUUID());
        return capNhatTrangThaiDongBoAuth(trangThaiDongBoAuthMoi, opId, now);
    }

    public TaiKhoan capNhatTrangThaiDongBoAuth(String trangThaiDongBoAuthMoi, UUID maThaoTacAuth, Instant now) {
        if (!"san_sang".equals(trangThaiDongBoAuthMoi)
                && !"dang_dat_lai".equals(trangThaiDongBoAuthMoi)
                && !"can_khoi_phuc".equals(trangThaiDongBoAuthMoi)) {
            throw new IllegalArgumentException("trangThaiDongBoAuth không hợp lệ: " + trangThaiDongBoAuthMoi);
        }
        Instant timestamp = now != null ? now : Instant.now();
        UUID nextMaThaoTac = "san_sang".equals(trangThaiDongBoAuthMoi) ? null : maThaoTacAuth;
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                this.emailDaXacMinh,
                this.mocThuHoi,
                trangThaiDongBoAuthMoi,
                nextMaThaoTac,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan batDauDatLaiMatKhau(UUID maThaoTacAuth, Instant now) {
        if (!"san_sang".equals(this.trangThaiDongBoAuth)) {
            throw new IllegalStateException("Không thể bắt đầu đặt lại mật khẩu khi trạng thái đồng bộ là " + this.trangThaiDongBoAuth);
        }
        Objects.requireNonNull(maThaoTacAuth, "maThaoTacAuth không được null khi bắt đầu đặt lại mật khẩu");
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                this.emailDaXacMinh,
                this.mocThuHoi + 1L,
                "dang_dat_lai",
                maThaoTacAuth,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan hoanTatDatLaiMatKhau(UUID expectedMaThaoTacAuth, Instant now) {
        if (!Objects.equals(this.maThaoTacAuth, expectedMaThaoTacAuth)) {
            throw new IllegalStateException("Mã thao tác Auth không khớp.");
        }
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                this.emailDaXacMinh,
                this.mocThuHoi,
                "san_sang",
                null,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan danhDauCanKhoiPhuc(UUID expectedMaThaoTacAuth, Instant now) {
        if (!Objects.equals(this.maThaoTacAuth, expectedMaThaoTacAuth)) {
            throw new IllegalStateException("Mã thao tác Auth không khớp.");
        }
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                this.emailDaXacMinh,
                this.mocThuHoi,
                "can_khoi_phuc",
                this.maThaoTacAuth,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public TaiKhoan dongThaoTacThatBai(UUID expectedMaThaoTacAuth, Instant now) {
        if (!Objects.equals(this.maThaoTacAuth, expectedMaThaoTacAuth)) {
            throw new IllegalStateException("Mã thao tác Auth không khớp.");
        }
        Instant timestamp = now != null ? now : Instant.now();
        return new TaiKhoan(
                this.id,
                this.supabaseAuthUserId,
                this.email,
                this.hoTen,
                this.soDienThoai,
                this.vaiTro,
                this.trangThai,
                this.emailDaXacMinh,
                this.mocThuHoi,
                "san_sang",
                null,
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getSupabaseAuthUserId() {
        return supabaseAuthUserId;
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

    public String getTrangThaiDongBoAuth() {
        return trangThaiDongBoAuth;
    }

    public UUID getMaThaoTacAuth() {
        return maThaoTacAuth;
    }

    public Instant getCapNhatAuthLuc() {
        return capNhatAuthLuc;
    }

    public Instant getTaoLuc() {
        return taoLuc;
    }

    public Instant getCapNhatLuc() {
        return capNhatLuc;
    }
}
