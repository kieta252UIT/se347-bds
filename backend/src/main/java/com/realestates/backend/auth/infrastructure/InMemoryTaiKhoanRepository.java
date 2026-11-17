package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTaiKhoanRepository implements TaiKhoanRepository {

    private final ConcurrentMap<UUID, TaiKhoan> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TaiKhoan> accountsByEmail = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TaiKhoan> accountsBySupabaseId = new ConcurrentHashMap<>();

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        if (normalizedEmail == null) {
            return false;
        }
        return accountsByEmail.containsKey(normalizeKey(normalizedEmail));
    }

    @Override
    public Optional<TaiKhoan> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsById.get(id));
    }

    @Override
    public Optional<TaiKhoan> findByEmail(String normalizedEmail) {
        if (normalizedEmail == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsByEmail.get(normalizeKey(normalizedEmail)));
    }

    @Override
    public Optional<TaiKhoan> findBySupabaseAuthUserId(UUID supabaseAuthUserId) {
        if (supabaseAuthUserId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsBySupabaseId.get(supabaseAuthUserId));
    }

    @Override
    public synchronized TaiKhoan save(TaiKhoan taiKhoan) {
        String emailKey = normalizeKey(taiKhoan.getEmail());
        TaiKhoan existingByEmail = accountsByEmail.get(emailKey);
        if (existingByEmail != null && !existingByEmail.getId().equals(taiKhoan.getId())) {
            throw new EmailAlreadyExistsException();
        }
        TaiKhoan existingBySupabaseId = accountsBySupabaseId.get(taiKhoan.getSupabaseAuthUserId());
        if (existingBySupabaseId != null && !existingBySupabaseId.getId().equals(taiKhoan.getId())) {
            throw new EmailAlreadyExistsException();
        }
        TaiKhoan previousById = accountsById.get(taiKhoan.getId());
        if (previousById != null) {
            String previousEmailKey = normalizeKey(previousById.getEmail());
            if (!previousEmailKey.equals(emailKey)) {
                accountsByEmail.remove(previousEmailKey, previousById);
            }
            if (!previousById.getSupabaseAuthUserId().equals(taiKhoan.getSupabaseAuthUserId())) {
                accountsBySupabaseId.remove(previousById.getSupabaseAuthUserId(), previousById);
            }
        }
        accountsById.put(taiKhoan.getId(), taiKhoan);
        accountsByEmail.put(emailKey, taiKhoan);
        accountsBySupabaseId.put(taiKhoan.getSupabaseAuthUserId(), taiKhoan);
        return taiKhoan;
    }

    @Override
    public synchronized boolean updateDongBoAuthCas(
            UUID taiKhoanId,
            UUID expectedMaThaoTacAuth,
            String newTrangThaiDongBoAuth,
            UUID newMaThaoTacAuth,
            java.time.Instant now
    ) {
        if (taiKhoanId == null) {
            return false;
        }
        TaiKhoan current = accountsById.get(taiKhoanId);
        if (current == null) {
            return false;
        }
        if (!java.util.Objects.equals(current.getMaThaoTacAuth(), expectedMaThaoTacAuth)) {
            return false;
        }
        java.time.Instant timestamp = now != null ? now : java.time.Instant.now();
        TaiKhoan updated = TaiKhoan.khoiPhucTuLuuTru(
                current.getId(),
                current.getSupabaseAuthUserId(),
                current.getEmail(),
                current.getHoTen(),
                current.getSoDienThoai(),
                current.getVaiTro(),
                current.getTrangThai(),
                current.isEmailDaXacMinh(),
                current.getMocThuHoi(),
                newTrangThaiDongBoAuth,
                newMaThaoTacAuth,
                timestamp,
                current.getTaoLuc(),
                timestamp
        );
        save(updated);
        return true;
    }

    @Override
    public synchronized java.util.List<TaiKhoan> findPendingAuthSyncAccounts(java.time.Instant olderThan) {
        java.util.List<TaiKhoan> result = new java.util.ArrayList<>();
        for (TaiKhoan account : accountsById.values()) {
            if (("dang_dat_lai".equals(account.getTrangThaiDongBoAuth()) || "can_khoi_phuc".equals(account.getTrangThaiDongBoAuth()))
                    && (olderThan == null || !account.getCapNhatAuthLuc().isAfter(olderThan))) {
                result.add(account);
            }
        }
        result.sort((a, b) -> a.getCapNhatAuthLuc().compareTo(b.getCapNhatAuthLuc()));
        return result;
    }

    public synchronized void clear() {
        accountsById.clear();
        accountsByEmail.clear();
        accountsBySupabaseId.clear();
    }

    private static String normalizeKey(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
