package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Thực thể miền Phiên xác thực (bảng se214.phien_xac_thuc) theo đặc tả
 * database-design-sprint-1.md §3.2, schema-sprint-1.sql, thiet-ke-phien-sprint-1.md và class-sprint-1.mmd.
 */
public final class PhienXacThuc {

    public static final String LY_DO_DANG_XUAT = "dang_xuat";
    public static final String LY_DO_THAY_PHIEN_DANG_NHAP = "thay_phien_dang_nhap";
    public static final String LY_DO_XAC_MINH_EMAIL_CUNG_TRINH_DUYET = "xac_minh_email_cung_trinh_duyet";
    public static final String LY_DO_DAT_LAI_MAT_KHAU = "dat_lai_mat_khau";
    public static final String LY_DO_KHOA_TAI_KHOAN = "khoa_tai_khoan";
    public static final String LY_DO_THU_HOI_BAO_MAT = "thu_hoi_bao_mat";
    public static final String LY_DO_REFRESH_KHONG_HOP_LE = "refresh_khong_hop_le";

    public static final Set<String> ALLOWED_LY_DO_THU_HOI = Set.of(
            LY_DO_DANG_XUAT,
            LY_DO_THAY_PHIEN_DANG_NHAP,
            LY_DO_XAC_MINH_EMAIL_CUNG_TRINH_DUYET,
            LY_DO_DAT_LAI_MAT_KHAU,
            LY_DO_KHOA_TAI_KHOAN,
            LY_DO_THU_HOI_BAO_MAT,
            LY_DO_REFRESH_KHONG_HOP_LE
    );

    private final UUID id;
    private final UUID taiKhoanId;
    private final String maCookieBam;
    private final long mocThuHoiLucCap;
    private final Instant taoLuc;
    private final Instant thuHoiLuc;
    private final String lyDoThuHoi;
    private final UUID supabaseSessionId;
    private final byte[] accessTokenMaHoa;
    private final byte[] refreshTokenMaHoa;
    private final Instant accessTokenHetHanLuc;
    private final String phienBanKhoaMaHoa;
    private final long phienBanCapNhat;
    private final UUID refreshLeaseId;
    private final Instant refreshLeaseDenLuc;
    private final boolean canThuHoiProvider;

    public PhienXacThuc(
            UUID id,
            UUID taiKhoanId,
            String maCookieBam,
            long mocThuHoiLucCap,
            Instant taoLuc,
            Instant thuHoiLuc,
            String lyDoThuHoi,
            UUID supabaseSessionId,
            byte[] accessTokenMaHoa,
            byte[] refreshTokenMaHoa,
            Instant accessTokenHetHanLuc,
            String phienBanKhoaMaHoa,
            long phienBanCapNhat,
            UUID refreshLeaseId,
            Instant refreshLeaseDenLuc,
            boolean canThuHoiProvider
    ) {
        this.id = Objects.requireNonNull(id, "id không được null");
        this.taiKhoanId = Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        if (maCookieBam == null || maCookieBam.isBlank()) {
            throw new IllegalArgumentException("maCookieBam không được rỗng");
        }
        this.maCookieBam = maCookieBam.trim();
        if (mocThuHoiLucCap < 0) {
            throw new IllegalArgumentException("ck_phien_moc_thu_hoi: mocThuHoiLucCap phải >= 0");
        }
        this.mocThuHoiLucCap = mocThuHoiLucCap;
        this.taoLuc = Objects.requireNonNull(taoLuc, "taoLuc không được null");
        this.thuHoiLuc = thuHoiLuc;
        this.lyDoThuHoi = lyDoThuHoi;
        this.supabaseSessionId = supabaseSessionId;
        this.accessTokenMaHoa = accessTokenMaHoa != null ? Arrays.copyOf(accessTokenMaHoa, accessTokenMaHoa.length) : null;
        this.refreshTokenMaHoa = refreshTokenMaHoa != null ? Arrays.copyOf(refreshTokenMaHoa, refreshTokenMaHoa.length) : null;
        this.accessTokenHetHanLuc = accessTokenHetHanLuc;
        this.phienBanKhoaMaHoa = phienBanKhoaMaHoa;
        if (phienBanCapNhat < 0) {
            throw new IllegalArgumentException("ck_phien_version: phienBanCapNhat phải >= 0");
        }
        this.phienBanCapNhat = phienBanCapNhat;
        this.refreshLeaseId = refreshLeaseId;
        this.refreshLeaseDenLuc = refreshLeaseDenLuc;
        this.canThuHoiProvider = canThuHoiProvider;

        validateSchemaInvariants();
    }

    /**
     * Khởi tạo phiên PostgreSQL mới sau đăng nhập hợp lệ (PHIEN-02, PHIEN-04, PHIEN-05).
     */
    public static PhienXacThuc taoPhienMoi(
            UUID id,
            UUID taiKhoanId,
            String maCookieBam,
            long mocThuHoiLucCap,
            Instant taoLuc,
            UUID supabaseSessionId,
            byte[] accessTokenMaHoa,
            byte[] refreshTokenMaHoa,
            Instant accessTokenHetHanLuc,
            String phienBanKhoaMaHoa
    ) {
        return new PhienXacThuc(
                id != null ? id : UUID.randomUUID(),
                taiKhoanId,
                maCookieBam,
                mocThuHoiLucCap,
                taoLuc != null ? taoLuc : Instant.now(),
                null,
                null,
                Objects.requireNonNull(supabaseSessionId, "supabaseSessionId không được null khi tạo phiên mới"),
                Objects.requireNonNull(accessTokenMaHoa, "accessTokenMaHoa không được null khi tạo phiên mới"),
                Objects.requireNonNull(refreshTokenMaHoa, "refreshTokenMaHoa không được null khi tạo phiên mới"),
                Objects.requireNonNull(accessTokenHetHanLuc, "accessTokenHetHanLuc không được null khi tạo phiên mới"),
                Objects.requireNonNull(phienBanKhoaMaHoa, "phienBanKhoaMaHoa không được null khi tạo phiên mới"),
                0L,
                null,
                null,
                false
        );
    }

    /**
     * Kiểm tra phiên xác thực còn hiệu lực đối với thực thể TaiKhoan chủ sở hữu
     * (class-sprint-1.mmd +conHieuLuc(taiKhoan) boolean & thiet-ke-phien-sprint-1.md PHIEN-03, PHIEN-08, PHIEN-09).
     */
    public boolean conHieuLuc(TaiKhoan taiKhoan) {
        if (taiKhoan == null) {
            return false;
        }
        if (!this.taiKhoanId.equals(taiKhoan.getId())) {
            return false;
        }
        if (this.thuHoiLuc != null) {
            return false;
        }
        if (taiKhoan.getTrangThai() != TrangThaiTaiKhoan.HOAT_DONG) {
            return false;
        }
        if (!taiKhoan.isEmailDaXacMinh()) {
            return false;
        }
        if (!"san_sang".equals(taiKhoan.getTrangThaiDongBoAuth())) {
            return false;
        }
        return this.mocThuHoiLucCap == taiKhoan.getMocThuHoi();
    }

    /**
     * Thu hồi phiên xác thực và xóa các thông tin credential provider (khi đã thu hồi xong phía provider
     * hoặc không cần thu hồi bổ sung).
     */
    public PhienXacThuc thuHoi(String lyDo, Instant thoiDiem) {
        return thuHoi(lyDo, thoiDiem, false);
    }

    /**
     * Thu hồi phiên xác thực theo lý do chuẩn hóa (class-sprint-1.mmd +thuHoi(lyDo, thoiDiem) & ck_phien_don_credential).
     * Nếu giuLaiCredentialDeThuHoiProvider = true và phiên đang còn giữ credential, đặt can_thu_hoi_provider = true
     * để tiến trình thu hồi bất đồng bộ/best-effort dọn phiên tại Supabase Auth trước khi xóa hẳn credential.
     */
    public PhienXacThuc thuHoi(String lyDo, Instant thoiDiem, boolean giuLaiCredentialDeThuHoiProvider) {
        Instant revokedAt = thoiDiem != null ? thoiDiem : Instant.now();
        boolean retainCreds = giuLaiCredentialDeThuHoiProvider && this.accessTokenMaHoa != null;
        return new PhienXacThuc(
                this.id,
                this.taiKhoanId,
                this.maCookieBam,
                this.mocThuHoiLucCap,
                this.taoLuc,
                this.thuHoiLuc != null ? this.thuHoiLuc : revokedAt,
                lyDo,
                retainCreds ? this.supabaseSessionId : null,
                retainCreds ? this.accessTokenMaHoa : null,
                retainCreds ? this.refreshTokenMaHoa : null,
                retainCreds ? this.accessTokenHetHanLuc : null,
                retainCreds ? this.phienBanKhoaMaHoa : null,
                this.phienBanCapNhat + 1L,
                null,
                null,
                retainCreds
        );
    }

    /**
     * Đánh dấu đã hoàn tất thu hồi phiên tại provider và dọn sạch toàn bộ token mã hóa khỏi bản ghi phiên đã thu hồi.
     */
    public PhienXacThuc daThuHoiProviderXong() {
        if (this.thuHoiLuc == null) {
            throw new IllegalStateException("Chỉ xóa credential sau thu hồi khi phiên đã có thuHoiLuc.");
        }
        return new PhienXacThuc(
                this.id,
                this.taiKhoanId,
                this.maCookieBam,
                this.mocThuHoiLucCap,
                this.taoLuc,
                this.thuHoiLuc,
                this.lyDoThuHoi,
                null,
                null,
                null,
                null,
                null,
                this.phienBanCapNhat + 1L,
                null,
                null,
                false
        );
    }

    private void validateSchemaInvariants() {
        // ck_phien_lease_cap
        boolean bothLeaseNull = (refreshLeaseId == null && refreshLeaseDenLuc == null);
        boolean bothLeaseNonNull = (refreshLeaseId != null && refreshLeaseDenLuc != null);
        if (!bothLeaseNull && !bothLeaseNonNull) {
            throw new IllegalArgumentException(
                    "ck_phien_lease_cap: refreshLeaseId và refreshLeaseDenLuc phải cùng null hoặc cùng khác null"
            );
        }

        // ck_phien_credential_cap
        boolean allCredsNull = supabaseSessionId == null
                && accessTokenMaHoa == null
                && refreshTokenMaHoa == null
                && accessTokenHetHanLuc == null
                && phienBanKhoaMaHoa == null;
        boolean allCredsPresent = supabaseSessionId != null
                && accessTokenMaHoa != null && accessTokenMaHoa.length > 0
                && refreshTokenMaHoa != null && refreshTokenMaHoa.length > 0
                && accessTokenHetHanLuc != null
                && phienBanKhoaMaHoa != null && !phienBanKhoaMaHoa.isBlank();
        if (!allCredsNull && !allCredsPresent) {
            throw new IllegalArgumentException(
                    "ck_phien_credential_cap: bộ 5 trường credential provider phải cùng null hoặc cùng khác null/không rỗng"
            );
        }

        // ck_phien_ly_do
        if (lyDoThuHoi != null && !ALLOWED_LY_DO_THU_HOI.contains(lyDoThuHoi)) {
            throw new IllegalArgumentException("ck_phien_ly_do: lyDoThuHoi không hợp lệ: " + lyDoThuHoi);
        }

        // ck_phien_don_credential
        if (thuHoiLuc == null) {
            if (lyDoThuHoi != null || canThuHoiProvider || !allCredsPresent) {
                throw new IllegalArgumentException(
                        "ck_phien_don_credential: phiên đang hoạt động (thuHoiLuc == null) phải có đủ credential, lyDoThuHoi == null và canThuHoiProvider == false"
                );
            }
        } else {
            if (lyDoThuHoi == null) {
                throw new IllegalArgumentException(
                        "ck_phien_don_credential: phiên đã thu hồi (thuHoiLuc != null) bắt buộc phải có lyDoThuHoi"
                );
            }
            if (canThuHoiProvider && !allCredsPresent) {
                throw new IllegalArgumentException(
                        "ck_phien_don_credential: khi canThuHoiProvider == true thì bộ credential phải còn hiện diện để thu hồi"
                );
            }
            if (!canThuHoiProvider && !allCredsNull) {
                throw new IllegalArgumentException(
                        "ck_phien_don_credential: khi canThuHoiProvider == false trên phiên đã thu hồi thì bộ credential phải được xóa sạch (null)"
                );
            }
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaiKhoanId() {
        return taiKhoanId;
    }

    public String getMaCookieBam() {
        return maCookieBam;
    }

    public long getMocThuHoiLucCap() {
        return mocThuHoiLucCap;
    }

    public Instant getTaoLuc() {
        return taoLuc;
    }

    public Instant getThuHoiLuc() {
        return thuHoiLuc;
    }

    public String getLyDoThuHoi() {
        return lyDoThuHoi;
    }

    public UUID getSupabaseSessionId() {
        return supabaseSessionId;
    }

    public byte[] getAccessTokenMaHoa() {
        return accessTokenMaHoa != null ? Arrays.copyOf(accessTokenMaHoa, accessTokenMaHoa.length) : null;
    }

    public byte[] getRefreshTokenMaHoa() {
        return refreshTokenMaHoa != null ? Arrays.copyOf(refreshTokenMaHoa, refreshTokenMaHoa.length) : null;
    }

    public Instant getAccessTokenHetHanLuc() {
        return accessTokenHetHanLuc;
    }

    public String getPhienBanKhoaMaHoa() {
        return phienBanKhoaMaHoa;
    }

    public long getPhienBanCapNhat() {
        return phienBanCapNhat;
    }

    public UUID getRefreshLeaseId() {
        return refreshLeaseId;
    }

    public Instant getRefreshLeaseDenLuc() {
        return refreshLeaseDenLuc;
    }

    public boolean isCanThuHoiProvider() {
        return canThuHoiProvider;
    }

    public boolean isDaThuHoi() {
        return thuHoiLuc != null;
    }
}
