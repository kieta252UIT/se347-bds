package com.realestates.backend.wallet.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GiaoDichViRepository {
    Optional<GiaoDichVi> findByViIdAndLoaiAndMaYDinh(UUID viId, String loai, String maYDinh);
    List<GiaoDichVi> findByViId(UUID viId);
    List<GiaoDichVi> findByViIdAndTrangThai(UUID viId, String trangThai);
    GiaoDichVi save(GiaoDichVi giaoDich);
}
