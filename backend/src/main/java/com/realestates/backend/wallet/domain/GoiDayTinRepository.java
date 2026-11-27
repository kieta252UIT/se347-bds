package com.realestates.backend.wallet.domain;

import java.util.Optional;
import java.util.UUID;

public interface GoiDayTinRepository {
    Optional<GoiDayTin> findGoiChuaKetThuc(UUID tinId);
    Optional<GoiDayTin> findGoiChuaKetThucForUpdate(UUID tinId);
    Optional<GoiDayTin> findById(UUID id);
    GoiDayTin save(GoiDayTin goi);
}
