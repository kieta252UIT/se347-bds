package com.realestates.backend.wallet.domain;

import java.util.Optional;
import java.util.UUID;

public interface ViDienTuRepository {
    Optional<ViDienTu> findByTaiKhoanId(UUID taiKhoanId);
    Optional<ViDienTu> findByTaiKhoanIdForUpdate(UUID taiKhoanId);
    Optional<ViDienTu> findById(UUID id);
    Optional<ViDienTu> findByIdForUpdate(UUID id);
    ViDienTu save(ViDienTu vi);
}
