package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.DiemGoiYRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryDiemGoiYRepository implements DiemGoiYRepository {

    // Key: taiKhoanId -> Map<tinId, DiemGoiY>
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, DiemGoiY>> store = new ConcurrentHashMap<>();

    @Override
    public List<DiemGoiY> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return List.of();
        }
        var map = store.get(taiKhoanId);
        if (map == null) {
            return List.of();
        }
        return new ArrayList<>(map.values());
    }

    @Override
    public Optional<DiemGoiY> findByTaiKhoanIdAndTinId(UUID taiKhoanId, UUID tinId) {
        if (taiKhoanId == null || tinId == null) {
            return Optional.empty();
        }
        var map = store.get(taiKhoanId);
        if (map == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(tinId));
    }

    @Override
    public void save(DiemGoiY diemGoiY) {
        if (diemGoiY == null) {
            return;
        }
        store.computeIfAbsent(diemGoiY.taiKhoanId(), k -> new ConcurrentHashMap<>())
                .put(diemGoiY.tinId(), diemGoiY);
    }

    @Override
    public void saveAll(List<DiemGoiY> danhSachDiem) {
        if (danhSachDiem == null) {
            return;
        }
        for (DiemGoiY diem : danhSachDiem) {
            save(diem);
        }
    }

    @Override
    public void deleteByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId != null) {
            store.remove(taiKhoanId);
        }
    }

    public void clear() {
        store.clear();
    }
}
