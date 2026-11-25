package com.realestates.backend.favorite.domain;

import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import java.util.List;
import java.util.UUID;

public interface YeuThichRepository {

    /** Một trang yêu thích còn công khai và tổng số mục còn công khai (cùng một câu SQL). */
    record TrangYeuThich(List<DongTin> noiDung, long tongSo) {
    }

    /**
     * Upsert quan hệ lưu nếu tin đang công khai (BR-01), idempotent và giữ luu_luc ban đầu.
     * Trả false nếu tin không tồn tại hoặc không công khai (không ghi gì).
     */
    boolean luu(UUID taiKhoanId, UUID tinId);

    /** Bỏ lưu, idempotent: không có gì để xóa thì không làm gì. */
    void bo(UUID taiKhoanId, UUID tinId);

    /** Chỉ tin còn công khai; luu_luc giảm rồi tin_id tăng; OFFSET/LIMIT. */
    TrangYeuThich docDanhSach(UUID taiKhoanId, long boQua, int gioiHan);
}
