package com.realestates.backend.wallet.exception;

public class ListingNotEligibleForPackageException extends RuntimeException {

    public static final String MA_LOI_TRANG_THAI_KHONG_HOP_LE = "TRANG_THAI_KHONG_HOP_LE";
    public static final String MA_LOI_KHONG_PHAI_CHU_TIN = "KHONG_PHAI_CHU_TIN";

    private final String maLoi;

    public ListingNotEligibleForPackageException(String maLoi, String message) {
        super(message);
        this.maLoi = maLoi;
    }

    public static ListingNotEligibleForPackageException tinChuaDuyet() {
        return new ListingNotEligibleForPackageException(
                MA_LOI_TRANG_THAI_KHONG_HOP_LE,
                "Chỉ tin ở trạng thái 'da_duyet' mới được mua gói đẩy tin"
        );
    }

    public static ListingNotEligibleForPackageException goiDangTamDung() {
        return new ListingNotEligibleForPackageException(
                MA_LOI_TRANG_THAI_KHONG_HOP_LE,
                "Tin đang có gói ở trạng thái tạm dừng chờ duyệt; chỉ mua bổ sung khi gói đang hoạt động và tin đã duyệt"
        );
    }

    public static ListingNotEligibleForPackageException khongPhaiChuTin() {
        return new ListingNotEligibleForPackageException(
                MA_LOI_KHONG_PHAI_CHU_TIN,
                "Chỉ chủ sở hữu tin mới có quyền mua gói đẩy tin"
        );
    }

    public String getMaLoi() {
        return maLoi;
    }
}
