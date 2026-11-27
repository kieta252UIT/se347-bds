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

    Optional<ThongTinTinKiemTra> khoaVaDocThongTinTin(UUID tinId);
}
