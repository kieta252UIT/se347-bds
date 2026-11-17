package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAnhTinRepository implements AnhTinRepository {

    private final ConcurrentMap<UUID, AnhTinTruyCap> anhTheoId = new ConcurrentHashMap<>();

    @Override
    public void luuAnhTam(
            UUID id,
            UUID chuId,
            String khoaTepGoc,
            String khoaAnhHienThi,
            DinhDangAnh dinhDang,
            long kichThuocByte
    ) {
        anhTheoId.put(id, new AnhTinTruyCap(
                id,
                chuId,
                AnhTinTruyCap.ANH_TAM,
                khoaTepGoc,
                khoaAnhHienThi,
                dinhDang.getMimeType(),
                null,
                null,
                null,
                null,
                false
        ));
    }

    @Override
    public Optional<AnhTinTruyCap> timAnhDeDoc(UUID anhId) {
        return Optional.ofNullable(anhTheoId.get(anhId));
    }

    @Override
    public boolean coThamChieuKhoa(String khoaDoiTuong) {
        if (khoaDoiTuong == null || khoaDoiTuong.isBlank()) {
            return false;
        }
        return anhTheoId.values().stream().anyMatch(a ->
                khoaDoiTuong.equals(a.khoaTepGoc()) || khoaDoiTuong.equals(a.khoaAnhHienThi())
        );
    }
}
