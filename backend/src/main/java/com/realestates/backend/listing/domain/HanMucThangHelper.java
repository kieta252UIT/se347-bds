package com.realestates.backend.listing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class HanMucThangHelper {

    public static final ZoneId MUI_GIO_VIET_NAM = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int GIOI_HAN_TIN_MOI_MOI_THANG = 3;

    private HanMucThangHelper() {
    }

    public static LocalDate dauThangVietNam(Instant thoiDiem) {
        Instant moc = thoiDiem != null ? thoiDiem : Instant.now();
        return ZonedDateTime.ofInstant(moc, MUI_GIO_VIET_NAM)
                .toLocalDate()
                .withDayOfMonth(1);
    }
}
