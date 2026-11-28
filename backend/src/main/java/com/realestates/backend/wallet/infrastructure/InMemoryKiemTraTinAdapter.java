package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.wallet.application.KiemTraTinChoGoiPort;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryKiemTraTinAdapter implements KiemTraTinChoGoiPort {

    private final Optional<TinDangRepository> tinDangRepository;
    private final ConcurrentMap<UUID, ThongTinTinKiemTra> mockStore = new ConcurrentHashMap<>();

    public InMemoryKiemTraTinAdapter(Optional<TinDangRepository> tinDangRepository) {
        this.tinDangRepository = tinDangRepository;
    }

    public void mockTin(UUID tinId, UUID chuId, String trangThai) {
        mockStore.put(tinId, new ThongTinTinKiemTra(tinId, chuId, trangThai));
    }

    public void clear() {
        mockStore.clear();
    }

    @Override
    public Optional<ThongTinTinKiemTra> khoaVaDocThongTinTin(UUID tinId) {
        if (tinId == null) {
            return Optional.empty();
        }
        if (mockStore.containsKey(tinId)) {
            return Optional.of(mockStore.get(tinId));
        }
        if (tinDangRepository.isPresent()) {
            Optional<UUID> chuIdOpt = tinDangRepository.get().khoaVaDocChuTin(tinId);
            if (chuIdOpt.isPresent()) {
                UUID chuId = chuIdOpt.get();
                var danhSach = tinDangRepository.get().docDanhSachTinCuaChu(chuId);
                for (var tin : danhSach) {
                    if (tin.tinId().equals(tinId)) {
                        return Optional.of(new ThongTinTinKiemTra(tinId, chuId, tin.trangThai()));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
