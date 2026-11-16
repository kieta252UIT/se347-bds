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

/**
 * Kho lưu trữ Tài khoản an toàn đa luồng, đảm bảo ràng buộc duy nhất nguyên tử
 * trên email định danh (lower(email)) và supabase_auth_user_id theo AUTH-01 & TS1-007.
 */
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

    public synchronized void clear() {
        accountsById.clear();
        accountsByEmail.clear();
        accountsBySupabaseId.clear();
    }

    private static String normalizeKey(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
