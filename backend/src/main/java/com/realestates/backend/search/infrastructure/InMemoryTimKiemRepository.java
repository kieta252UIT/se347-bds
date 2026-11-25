package com.realestates.backend.search.infrastructure;

import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.TimKiemRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Bản tối thiểu cho chế độ in-memory (chỉ để ngữ cảnh Spring khởi động được): không có dữ liệu tìm kiếm. */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTimKiemRepository implements TimKiemRepository {

    @Override
    public KetQua timKiem(BoLocTimKiem boLoc, int soTaiTroToiDa) {
        return new KetQua(List.of(), 0, List.of(), Instant.now());
    }
}
