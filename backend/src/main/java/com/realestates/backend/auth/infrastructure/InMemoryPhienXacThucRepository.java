package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Kho lưu trữ Phiên xác thực (se214.phien_xac_thuc) an toàn đa luồng,
 * đảm bảo toàn vẹn các ràng buộc duy nhất uq_phien_ma_cookie_bam và uq_phien_supabase_session_id,
 * cũng như giao dịch nguyên tử khi thay thế phiên trên cùng trình duyệt (PHIEN-02, PHIEN-04).
 */
@Repository
public class InMemoryPhienXacThucRepository implements PhienXacThucRepository {

    private final Map<UUID, PhienXacThuc> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionIdByCookieHash = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sessionIdBySupabaseSessionId = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<PhienXacThuc> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsById.get(id));
    }

    @Override
    public synchronized Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam) {
        if (maCookieBam == null || maCookieBam.isBlank()) {
            return Optional.empty();
        }
        UUID id = sessionIdByCookieHash.get(maCookieBam.trim());
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsById.get(id));
    }

    @Override
    public synchronized Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId) {
        if (supabaseSessionId == null) {
            return Optional.empty();
        }
        UUID id = sessionIdBySupabaseSessionId.get(supabaseSessionId);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsById.get(id));
    }

    @Override
    public synchronized List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId) {
        if (taiKhoanId == null) {
            return List.of();
        }
        List<PhienXacThuc> result = new ArrayList<>();
        for (PhienXacThuc session : sessionsById.values()) {
            if (taiKhoanId.equals(session.getTaiKhoanId())) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public synchronized PhienXacThuc save(PhienXacThuc phienXacThuc) {
        Objects.requireNonNull(phienXacThuc, "phienXacThuc không được null");
        validateUniqueConstraints(phienXacThuc);
        putInternal(phienXacThuc);
        return phienXacThuc;
    }

    @Override
    public synchronized Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(
            String oldMaCookieBam,
            PhienXacThuc newPhien,
            Instant now
    ) {
        Objects.requireNonNull(newPhien, "newPhien không được null");
        Instant timestamp = now != null ? now : Instant.now();

        PhienXacThuc previousOldSession = null;
        PhienXacThuc revokedOldSession = null;

        if (oldMaCookieBam != null && !oldMaCookieBam.isBlank()) {
            UUID oldId = sessionIdByCookieHash.get(oldMaCookieBam.trim());
            if (oldId != null && !oldId.equals(newPhien.getId())) {
                PhienXacThuc existingOld = sessionsById.get(oldId);
                if (existingOld != null && !existingOld.isDaThuHoi()) {
                    previousOldSession = existingOld;
                    revokedOldSession = existingOld.thuHoi(
                            PhienXacThuc.LY_DO_THAY_PHIEN_DANG_NHAP,
                            timestamp,
                            true
                    );
                }
            }
        }

        // Kiểm tra ràng buộc duy nhất cho newPhien trước khi áp thay đổi nguyên tử
        validateUniqueConstraintsForReplacement(newPhien, previousOldSession, revokedOldSession);

        if (revokedOldSession != null) {
            putInternal(revokedOldSession);
        }
        putInternal(newPhien);

        return Optional.ofNullable(revokedOldSession);
    }

    @Override
    public synchronized Optional<PhienXacThuc> revokeByMaCookieBam(
            String maCookieBam,
            String lyDoThuHoi,
            Instant now,
            boolean giuLaiCredentialDeThuHoiProvider
    ) {
        if (maCookieBam == null || maCookieBam.isBlank()) {
            return Optional.empty();
        }
        UUID id = sessionIdByCookieHash.get(maCookieBam.trim());
        if (id == null) {
            return Optional.empty();
        }
        PhienXacThuc existing = sessionsById.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.isDaThuHoi()) {
            return Optional.of(existing);
        }
        PhienXacThuc revoked = existing.thuHoi(
                lyDoThuHoi,
                now != null ? now : Instant.now(),
                giuLaiCredentialDeThuHoiProvider
        );
        putInternal(revoked);
        return Optional.of(revoked);
    }

    public synchronized void clear() {
        sessionsById.clear();
        sessionIdByCookieHash.clear();
        sessionIdBySupabaseSessionId.clear();
    }

    private void validateUniqueConstraints(PhienXacThuc candidate) {
        UUID existingByCookie = sessionIdByCookieHash.get(candidate.getMaCookieBam());
        if (existingByCookie != null && !existingByCookie.equals(candidate.getId())) {
            throw new IllegalStateException("Vi phạm uq_phien_ma_cookie_bam: ma_cookie_bam đã tồn tại.");
        }
        UUID supabaseSid = candidate.getSupabaseSessionId();
        if (supabaseSid != null) {
            UUID existingBySupabaseSid = sessionIdBySupabaseSessionId.get(supabaseSid);
            if (existingBySupabaseSid != null && !existingBySupabaseSid.equals(candidate.getId())) {
                throw new IllegalStateException(
                        "Vi phạm uq_phien_supabase_session_id: supabase_session_id đã tồn tại."
                );
            }
        }
    }

    private void validateUniqueConstraintsForReplacement(
            PhienXacThuc newPhien,
            PhienXacThuc previousOldSession,
            PhienXacThuc revokedOldSession
    ) {
        UUID existingByCookie = sessionIdByCookieHash.get(newPhien.getMaCookieBam());
        if (existingByCookie != null && !existingByCookie.equals(newPhien.getId())) {
            throw new IllegalStateException("Vi phạm uq_phien_ma_cookie_bam: ma_cookie_bam đã tồn tại.");
        }
        UUID supabaseSid = newPhien.getSupabaseSessionId();
        if (supabaseSid != null) {
            UUID existingBySupabaseSid = sessionIdBySupabaseSessionId.get(supabaseSid);
            if (existingBySupabaseSid != null && !existingBySupabaseSid.equals(newPhien.getId())) {
                // Nếu phiên cũ trên cùng trình duyệt vừa giải phóng supabase_session_id thì cho phép,
                // ngược lại nếu vẫn đang giữ thì chặn trùng
                boolean releasedByOld = previousOldSession != null
                        && existingBySupabaseSid.equals(previousOldSession.getId())
                        && revokedOldSession != null
                        && revokedOldSession.getSupabaseSessionId() == null;
                if (!releasedByOld) {
                    throw new IllegalStateException(
                            "Vi phạm uq_phien_supabase_session_id: supabase_session_id đã tồn tại."
                    );
                }
            }
        }
    }

    private void putInternal(PhienXacThuc phien) {
        PhienXacThuc previous = sessionsById.put(phien.getId(), phien);
        if (previous != null) {
            if (!previous.getMaCookieBam().equals(phien.getMaCookieBam())) {
                sessionIdByCookieHash.remove(previous.getMaCookieBam(), phien.getId());
            }
            if (previous.getSupabaseSessionId() != null
                    && !previous.getSupabaseSessionId().equals(phien.getSupabaseSessionId())) {
                sessionIdBySupabaseSessionId.remove(previous.getSupabaseSessionId(), phien.getId());
            }
        }
        sessionIdByCookieHash.put(phien.getMaCookieBam(), phien.getId());
        if (phien.getSupabaseSessionId() != null) {
            sessionIdBySupabaseSessionId.put(phien.getSupabaseSessionId(), phien.getId());
        }
    }
}
