package com.realestates.backend.wallet.exception;

public class TestBalanceSeedDisabledException extends RuntimeException {

    public static final String MA_LOI = "KHONG_CHO_PHEP_KHOI_TAO_SO_DU_THU";

    public TestBalanceSeedDisabledException() {
        super("Thao tác khởi tạo số dư thử nghiệm chỉ được phép trong môi trường kiểm thử đã cấu hình; không cho phép người dùng tự cộng số dư trong cấu hình sản phẩm.");
    }

    public String getMaLoi() {
        return MA_LOI;
    }
}
