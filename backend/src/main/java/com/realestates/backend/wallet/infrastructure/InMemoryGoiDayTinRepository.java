package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.GoiDayTin;
import com.realestates.backend.wallet.domain.GoiDayTinRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryGoiDayTinRepository implements GoiDayTinRepository {

    private final ConcurrentMap<UUID, GoiDayTin> storeById = new ConcurrentHashMap<>();

    @Override
    public Optional<GoiDayTin> findGoiChuaKetThuc(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        return storeById.values().stream()
                .filter(g -> g.tinId().equals(tinId)
                        && ("dang_hoat_dong".equals(g.trangThai()) || "tam_dung_cho_duyet".equals(g.trangThai())))
                .findFirst();
    }

    @Override
    public Optional<GoiDayTin> findGoiChuaKetThucForUpdate(UUID tinId) {
        return findGoiChuaKetThuc(tinId);
    }

    @Override
    public Optional<GoiDayTin> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storeById.get(id));
    }

    @Override
    public GoiDayTin save(GoiDayTin goi) {
        Objects.requireNonNull(goi, "goi không được null");
        storeById.put(goi.id(), goi);
        return goi;
    }

    public void clear() {
        storeById.clear();
    }
}
