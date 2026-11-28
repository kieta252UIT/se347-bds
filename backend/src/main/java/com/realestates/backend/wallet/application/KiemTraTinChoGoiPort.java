package com.realestates.backend.wallet.application;

import java.util.Optional;
import java.util.UUID;

public interface KiemTraTinChoGoiPort {

    record ThongTinTinKiemTra(
            UUID id,
            UUID chuId,
            String trangThai
    ) {
    }

    default Optional<ThongTinTinKiemTra> docThongTinTin(UUID tinId) {
        return khoaVaDocThongTinTin(tinId);
    }

    Optional<ThongTinTinKiemTra> khoaVaDocThongTinTin(UUID tinId);
}
