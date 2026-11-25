package com.realestates.backend.favorite.infrastructure;

import com.realestates.backend.favorite.domain.YeuThichRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Bản tối thiểu cho chế độ in-memory (chỉ để ngữ cảnh Spring khởi động được): không có dữ liệu. */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryYeuThichRepository implements YeuThichRepository {

    @Override
    public boolean luu(UUID taiKhoanId, UUID tinId) {
        return false;
    }

    @Override
    public void bo(UUID taiKhoanId, UUID tinId) {
        // không có dữ liệu
    }

    @Override
    public TrangYeuThich docDanhSach(UUID taiKhoanId, long boQua, int gioiHan) {
        return new TrangYeuThich(List.of(), 0);
    }
}
