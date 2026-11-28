package com.realestates.backend.recommendation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LichSuXemRepository {

    void save(LichSuXem lichSu);

    List<LichSuXem> findByTaiKhoanId(UUID taiKhoanId);

    /**
     * Lấy lịch sử tương tác của người dùng trong khoảng thời gian xác định (cửa sổ trượt 30 ngày).
     */
    List<LichSuXem> findByTaiKhoanIdTrongKhoang(UUID taiKhoanId, Instant tuThoiDiem, Instant denThoiDiem);

    /**
     * Tìm danh sách các tài khoản có phát sinh tương tác trong khoảng thời gian xác định
     * (phục vụ quét cửa sổ 5 phút của job nền theo AC-03.2 & Q28).
     */
    List<UUID> findTaiKhoanCoTuongTacTrongKhoang(Instant tuThoiDiem, Instant denThoiDiem);
}
