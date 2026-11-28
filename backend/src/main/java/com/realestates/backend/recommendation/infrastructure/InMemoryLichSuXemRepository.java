package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.LichSuXemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryLichSuXemRepository implements LichSuXemRepository {

    private final CopyOnWriteArrayList<LichSuXem> store = new CopyOnWriteArrayList<>();

    @Override
    public void save(LichSuXem lichSu) {
        if (lichSu != null) {
            store.add(lichSu);
        }
    }

    @Override
    public List<LichSuXem> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return List.of();
        }
        return store.stream()
                .filter(ls -> ls.taiKhoanId().equals(taiKhoanId))
                .toList();
    }

    @Override
    public List<LichSuXem> findByTaiKhoanIdTrongKhoang(UUID taiKhoanId, Instant tuThoiDiem, Instant denThoiDiem) {
        if (taiKhoanId == null || tuThoiDiem == null || denThoiDiem == null) {
            return List.of();
        }
        return store.stream()
                .filter(ls -> ls.taiKhoanId().equals(taiKhoanId))
                .filter(ls -> !ls.thoiDiemXem().isBefore(tuThoiDiem) && !ls.thoiDiemXem().isAfter(denThoiDiem))
                .sorted(java.util.Comparator.comparing(LichSuXem::thoiDiemXem).reversed())
                .limit(100)
                .toList();
    }

    @Override
    public List<UUID> findTaiKhoanCoTuongTacTrongKhoang(Instant tuThoiDiem, Instant denThoiDiem) {
        if (tuThoiDiem == null || denThoiDiem == null) {
            return List.of();
        }
        return store.stream()
                .filter(ls -> !ls.thoiDiemXem().isBefore(tuThoiDiem) && !ls.thoiDiemXem().isAfter(denThoiDiem))
                .map(LichSuXem::taiKhoanId)
                .distinct()
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
