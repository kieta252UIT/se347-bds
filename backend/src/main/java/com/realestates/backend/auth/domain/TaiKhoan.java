package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Thực thể miền Tài khoản (bảng tai_khoan) theo đặc tả database-design-sprint-1.md §3.1.
 */
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
        this.capNhatAuthLuc = Objects.requireNonNull(capNhatAuthLuc, "capNhatAuthLuc không được null");
        this.taoLuc = Objects.requireNonNull(taoLuc, "taoLuc không được null");
        this.capNhatLuc = Objects.requireNonNull(capNhatLuc, "capNhatLuc không được null");
    }

    /**
     * Tạo tài khoản mới qua luồng đăng ký công khai (UC-25 / FR-15 / AC-15.1).
     * Cố định vai trò là NGUOI_DUNG, trạng thái HOAT_DONG và emailDaXacMinh = false.
     */
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
                timestamp,
                timestamp,
                timestamp
        );
    }

    /**
     * Tạo tài khoản vai trò Người dùng đã được xác minh email trên Supabase Auth
     * (dùng khi hoàn tất hồ sơ còn thiếu từ danh tính provider tin cậy theo api-specification §8.4).
     */
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
                timestamp,
                timestamp,
                timestamp
        );
    }

    /**
     * Khôi phục thực thể TaiKhoan từ kho lưu trữ PostgreSQL (bao gồm cả các tài khoản nội bộ
     * kiem_duyet_vien, quan_tri_vien, hoặc tài khoản đang bị khóa / đang đồng bộ).
     */
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
                capNhatAuthLuc != null ? capNhatAuthLuc : fallbackNow,
                taoLuc != null ? taoLuc : fallbackNow,
                capNhatLuc != null ? capNhatLuc : fallbackNow
        );
    }

    /**
     * Trả về bản sao thực thể TaiKhoan với trạng thái email_da_xac_minh = true (UC-26 / AC-15.2).
     * Giữ nguyên vai trò, trạng thái khóa, mốc thu hồi và các thông tin hồ sơ.
     */
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
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    /**
     * Trả về bản sao thực thể TaiKhoan với trạng thái hoạt động/khóa mới.
     */
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
                this.capNhatAuthLuc,
                this.taoLuc,
                timestamp
        );
    }

    /**
     * Trả về bản sao thực thể TaiKhoan với moc_thu_hoi tăng thêm 1 và trạng thái đồng bộ Auth cập nhật.
     */
    public TaiKhoan tangMocThuHoi(String trangThaiDongBoAuthMoi, Instant now) {
        Instant timestamp = now != null ? now : Instant.now();
        String nextSyncState = trangThaiDongBoAuthMoi != null ? trangThaiDongBoAuthMoi : this.trangThaiDongBoAuth;
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
                timestamp,
                this.taoLuc,
                timestamp
        );
    }

    /**
     * Trả về bản sao thực thể TaiKhoan với trạng thái đồng bộ Auth mới ('san_sang', 'dang_dat_lai', 'can_khoi_phuc').
     */
    public TaiKhoan capNhatTrangThaiDongBoAuth(String trangThaiDongBoAuthMoi, Instant now) {
        if (!"san_sang".equals(trangThaiDongBoAuthMoi)
                && !"dang_dat_lai".equals(trangThaiDongBoAuthMoi)
                && !"can_khoi_phuc".equals(trangThaiDongBoAuthMoi)) {
            throw new IllegalArgumentException("trangThaiDongBoAuth không hợp lệ: " + trangThaiDongBoAuthMoi);
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
                trangThaiDongBoAuthMoi,
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
