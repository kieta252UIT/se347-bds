package com.realestates.backend.wallet.exception;

public class ListingNotEligibleForPackageException extends RuntimeException {

    private final String maLoi;

    public ListingNotEligibleForPackageException(String maLoi, String message) {
        super(message);
        this.maLoi = maLoi;
    }

    public static ListingNotEligibleForPackageException tinChuaDuyet() {
        return new ListingNotEligibleForPackageException("TIN_CHUA_DUYET", "Chỉ tin ở trạng thái 'da_duyet' mới được mua gói đẩy tin");
    }

    public static ListingNotEligibleForPackageException khongPhaiChuTin() {
        return new ListingNotEligibleForPackageException("KHONG_PHAI_CHU_TIN", "Chỉ chủ sở hữu tin mới có quyền mua gói đẩy tin");
    }

    public String getMaLoi() {
        return maLoi;
    }
}
