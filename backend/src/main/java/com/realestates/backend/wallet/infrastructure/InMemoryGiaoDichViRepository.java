package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.GiaoDichVi;
import com.realestates.backend.wallet.domain.GiaoDichViRepository;
import java.util.Comparator;
import java.util.List;
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
public class InMemoryGiaoDichViRepository implements GiaoDichViRepository {

    private final ConcurrentMap<UUID, GiaoDichVi> storeById = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<GiaoDichVi> findByViIdAndLoaiAndMaYDinh(UUID viId, String loai, String maYDinh) {
        if (viId == null || loai == null || maYDinh == null) {
            return Optional.empty();
        }
        return storeById.values().stream()
                .filter(gd -> gd.viId().equals(viId)
                        && gd.loai().equalsIgnoreCase(loai.trim())
                        && gd.maYDinh() != null
                        && gd.maYDinh().equals(maYDinh.trim())
                        && "thanh_cong".equals(gd.trangThai()))
                .findFirst();
    }

    @Override
    public synchronized List<GiaoDichVi> findByViId(UUID viId) {
        if (viId == null) {
            return List.of();
        }
        return storeById.values().stream()
                .filter(gd -> gd.viId().equals(viId))
                .sorted(Comparator.comparing(GiaoDichVi::thoiDiemGhiNhan).reversed())
                .toList();
    }

    @Override
    public synchronized List<GiaoDichVi> findByViIdAndTrangThai(UUID viId, String trangThai) {
        if (viId == null || trangThai == null) {
            return List.of();
        }
        return storeById.values().stream()
                .filter(gd -> gd.viId().equals(viId) && gd.trangThai().equalsIgnoreCase(trangThai.trim()))
                .sorted(Comparator.comparing(GiaoDichVi::thoiDiemGhiNhan).reversed())
                .toList();
    }

    @Override
    public synchronized GiaoDichVi save(GiaoDichVi giaoDich) {
        Objects.requireNonNull(giaoDich, "giaoDich không được null");
        if (giaoDich.maYDinh() != null && !giaoDich.maYDinh().trim().isEmpty()) {
            String normalizedIntent = giaoDich.maYDinh().trim();
            boolean duplicateIntent = storeById.values().stream()
                    .anyMatch(existing -> !existing.id().equals(giaoDich.id())
                            && existing.viId().equals(giaoDich.viId())
                            && existing.loai().equalsIgnoreCase(giaoDich.loai())
                            && existing.maYDinh() != null
                            && existing.maYDinh().trim().equals(normalizedIntent));
            if (duplicateIntent) {
                throw new DuplicateKeyException(
                        "Vi phạm ràng buộc duy nhất uq_giao_dich_vi_y_dinh (vi_id, loai, ma_y_dinh)"
                );
            }
        }
        storeById.put(giaoDich.id(), giaoDich);
        return giaoDich;
    }

    public synchronized void clear() {
        storeById.clear();
    }
}
