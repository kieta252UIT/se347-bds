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
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryGiaoDichViRepository implements GiaoDichViRepository {

    private final ConcurrentMap<UUID, GiaoDichVi> storeById = new ConcurrentHashMap<>();

    @Override
    public Optional<GiaoDichVi> findByViIdAndLoaiAndMaYDinh(UUID viId, String loai, String maYDinh) {
        if (viId == null || loai == null || maYDinh == null) {
            return Optional.empty();
        }
        return storeById.values().stream()
                .filter(gd -> gd.viId().equals(viId)
                        && gd.loai().equalsIgnoreCase(loai.trim())
                        && gd.maYDinh().equals(maYDinh.trim())
                        && "thanh_cong".equals(gd.trangThai()))
                .findFirst();
    }

    @Override
    public List<GiaoDichVi> findByViId(UUID viId) {
        if (viId == null) {
            return List.of();
        }
        return storeById.values().stream()
                .filter(gd -> gd.viId().equals(viId))
                .sorted(Comparator.comparing(GiaoDichVi::thoiDiemGhiNhan).reversed())
                .toList();
    }

    @Override
    public List<GiaoDichVi> findByViIdAndTrangThai(UUID viId, String trangThai) {
        if (viId == null || trangThai == null) {
            return List.of();
        }
        return storeById.values().stream()
                .filter(gd -> gd.viId().equals(viId) && gd.trangThai().equalsIgnoreCase(trangThai.trim()))
                .sorted(Comparator.comparing(GiaoDichVi::thoiDiemGhiNhan).reversed())
                .toList();
    }

    @Override
    public GiaoDichVi save(GiaoDichVi giaoDich) {
        Objects.requireNonNull(giaoDich, "giaoDich không được null");
        storeById.put(giaoDich.id(), giaoDich);
        return giaoDich;
    }

    public void clear() {
        storeById.clear();
    }
}
