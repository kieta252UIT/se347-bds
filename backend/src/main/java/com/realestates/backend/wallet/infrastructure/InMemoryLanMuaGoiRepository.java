package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.LanMuaGoi;
import com.realestates.backend.wallet.domain.LanMuaGoiRepository;
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
public class InMemoryLanMuaGoiRepository implements LanMuaGoiRepository {

    private final ConcurrentMap<UUID, LanMuaGoi> storeById = new ConcurrentHashMap<>();

    @Override
    public Optional<LanMuaGoi> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storeById.get(id));
    }

    @Override
    public Optional<LanMuaGoi> findByGiaoDichViId(UUID giaoDichViId) {
        if (giaoDichViId == null) {
            return Optional.empty();
        }
        return storeById.values().stream()
                .filter(l -> l.giaoDichViId().equals(giaoDichViId))
                .findFirst();
    }

    @Override
    public List<LanMuaGoi> findByGoiId(UUID goiId) {
        if (goiId == null) {
            return List.of();
        }
        return storeById.values().stream()
                .filter(l -> l.goiId().equals(goiId))
                .sorted(Comparator.comparing(LanMuaGoi::thoiDiemMua).reversed())
                .toList();
    }

    @Override
    public LanMuaGoi save(LanMuaGoi lanMua) {
        Objects.requireNonNull(lanMua, "lanMua không được null");
        storeById.put(lanMua.id(), lanMua);
        return lanMua;
    }

    public void clear() {
        storeById.clear();
    }
}
