package com.realestates.backend.wallet.exception;

import java.time.Duration;
import java.time.Instant;

public class PackageAccumulationRequiredException extends RuntimeException {

    public static final String MA_LOI = "GOI_DANG_CON_HIEU_LUC_CAN_XAC_NHAN";

    private final Instant hetHanLuc;
    private final Duration thoiLuongConLai;

    public PackageAccumulationRequiredException(Instant hetHanLuc, Duration thoiLuongConLai) {
        super("Tin đăng đang có gói còn hiệu lực. Vui lòng xác nhận bạn muốn cộng dồn thời lượng.");
        this.hetHanLuc = hetHanLuc;
        this.thoiLuongConLai = thoiLuongConLai;
    }

    public String getMaLoi() {
        return MA_LOI;
    }

    public Instant getHetHanLuc() {
        return hetHanLuc;
    }

    public Duration getThoiLuongConLai() {
        return thoiLuongConLai;
    }
}
