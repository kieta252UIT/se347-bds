package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.GoiDayTin;
import com.realestates.backend.wallet.domain.GoiDayTinRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryGoiDayTinRepository implements GoiDayTinRepository {

    private final ConcurrentMap<UUID, GoiDayTin> storeById = new ConcurrentHashMap<>();

    private static boolean laGoiChuaKetThuc(String trangThai) {
        return "dang_hoat_dong".equals(trangThai)
                || "dang_hieu_luc".equals(trangThai)
                || "tam_dung_cho_duyet".equals(trangThai);
    }

    @Override
    public synchronized Optional<GoiDayTin> findGoiChuaKetThuc(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        return storeById.values().stream()
                .filter(g -> g.tinId().equals(tinId) && laGoiChuaKetThuc(g.trangThai()))
                .findFirst();
    }

    @Override
    public synchronized Optional<GoiDayTin> findGoiChuaKetThucForUpdate(UUID tinId) {
        return findGoiChuaKetThuc(tinId);
    }

    @Override
    public synchronized Optional<GoiDayTin> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storeById.get(id));
    }

    @Override
    public synchronized GoiDayTin save(GoiDayTin goi) {
        Objects.requireNonNull(goi, "goi không được null");
        if (laGoiChuaKetThuc(goi.trangThai())) {
            boolean duplicateUnfinished = storeById.values().stream()
                    .anyMatch(existing -> !existing.id().equals(goi.id())
                            && existing.tinId().equals(goi.tinId())
                            && laGoiChuaKetThuc(existing.trangThai()));
            if (duplicateUnfinished) {
                throw new DuplicateKeyException(
                        "Vi phạm ràng buộc duy nhất uq_goi_chua_ket_thuc: mỗi tin chỉ có tối đa một gói chưa kết thúc"
                );
            }
        }
        storeById.put(goi.id(), goi);
        return goi;
    }

    public synchronized void clear() {
        storeById.clear();
    }
}
