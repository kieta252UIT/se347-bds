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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
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

    @Override
    public synchronized int revokeAllByTaiKhoanId(UUID taiKhoanId, String lyDoThuHoi, Instant now) {
        if (taiKhoanId == null) {
            return 0;
        }
        Instant timestamp = now != null ? now : Instant.now();
        int count = 0;
        for (PhienXacThuc session : new ArrayList<>(sessionsById.values())) {
            if (taiKhoanId.equals(session.getTaiKhoanId()) && !session.isDaThuHoi()) {
                PhienXacThuc revoked = session.thuHoi(lyDoThuHoi, timestamp, false);
                putInternal(revoked);
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized Optional<PhienXacThuc> tryAcquireRefreshLease(
            UUID phienId,
            UUID leaseId,
            Instant leaseUntil,
            Instant now
    ) {
        if (phienId == null || leaseId == null || leaseUntil == null) {
            return Optional.empty();
        }
        Instant currentTime = now != null ? now : Instant.now();
        PhienXacThuc session = sessionsById.get(phienId);
        if (session == null || session.isDaThuHoi()) {
            return Optional.empty();
        }

        if (session.getRefreshLeaseDenLuc() != null && session.getRefreshLeaseDenLuc().isAfter(currentTime)) {
            return Optional.empty();
        }

        PhienXacThuc leased = session.nhanRefreshLease(leaseId, leaseUntil);
        putInternal(leased);
        return Optional.of(leased);
    }

    @Override
    public synchronized boolean updateRefreshedTokensWithCas(
            UUID phienId,
            UUID expectedLeaseId,
            long expectedCasVersion,
            byte[] newAccessTokenMaHoa,
            byte[] newRefreshTokenMaHoa,
            Instant newAccessTokenHetHanLuc,
            String phienBanKhoaMaHoa,
            Instant now
    ) {
        if (phienId == null || expectedLeaseId == null) {
            return false;
        }
        PhienXacThuc session = sessionsById.get(phienId);
        if (session == null || session.isDaThuHoi()) {
            return false;
        }

        if (!expectedLeaseId.equals(session.getRefreshLeaseId())) {
            return false;
        }
        if (session.getPhienBanCapNhat() != expectedCasVersion) {
            return false;
        }

        PhienXacThuc updated = session.capNhatTokenSauRefresh(
                newAccessTokenMaHoa,
                newRefreshTokenMaHoa,
                newAccessTokenHetHanLuc,
                phienBanKhoaMaHoa
        );
        putInternal(updated);
        return true;
    }

    @Override
    public synchronized void releaseRefreshLease(UUID phienId, UUID leaseId) {
        if (phienId == null || leaseId == null) {
            return;
        }
        PhienXacThuc session = sessionsById.get(phienId);
        if (session != null && leaseId.equals(session.getRefreshLeaseId())) {
            PhienXacThuc released = session.giaiPhongLease();
            putInternal(released);
        }
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
