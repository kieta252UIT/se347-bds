package com.realestates.backend.recommendation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiemGoiYRepository {

    List<DiemGoiY> findByTaiKhoanId(UUID taiKhoanId);

    Optional<DiemGoiY> findByTaiKhoanIdAndTinId(UUID taiKhoanId, UUID tinId);

    void save(DiemGoiY diemGoiY);

    void saveAll(List<DiemGoiY> danhSachDiem);

    void deleteByTaiKhoanId(UUID taiKhoanId);
}
