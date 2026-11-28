package com.realestates.backend.wallet.infrastructure;

import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.domain.ViDienTuRepository;
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
public class InMemoryViDienTuRepository implements ViDienTuRepository {

    private final ConcurrentMap<UUID, ViDienTu> storeById = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> taiKhoanToViId = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<ViDienTu> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return Optional.empty();
        }
        UUID viId = taiKhoanToViId.get(taiKhoanId);
        if (viId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storeById.get(viId));
    }

    @Override
    public synchronized Optional<ViDienTu> findByTaiKhoanIdForUpdate(UUID taiKhoanId) {
        return findByTaiKhoanId(taiKhoanId);
    }

    @Override
    public synchronized Optional<ViDienTu> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(storeById.get(id));
    }

    @Override
    public synchronized Optional<ViDienTu> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    @Override
    public synchronized ViDienTu save(ViDienTu vi) {
        Objects.requireNonNull(vi, "vi không được null");
        UUID existingViId = taiKhoanToViId.get(vi.taiKhoanId());
        if (existingViId != null && !existingViId.equals(vi.id())) {
            throw new DuplicateKeyException(
                    "Vi phạm ràng buộc duy nhất vi_dien_tu_tai_khoan_id_key: mỗi tài khoản chỉ có một ví điện tử"
            );
        }
        storeById.put(vi.id(), vi);
        taiKhoanToViId.put(vi.taiKhoanId(), vi.id());
        return vi;
    }

    public synchronized void clear() {
        storeById.clear();
        taiKhoanToViId.clear();
    }
}
