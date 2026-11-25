package com.realestates.backend.detail.infrastructure;

import com.realestates.backend.detail.domain.ChiTietTinRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Bản tối thiểu cho chế độ in-memory (chỉ để ngữ cảnh Spring khởi động được): không có dữ liệu. */
@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryChiTietTinRepository implements ChiTietTinRepository {

    @Override
    public Optional<DongChiTiet> docChiTiet(UUID tinId) {
        return Optional.empty();
    }
}
