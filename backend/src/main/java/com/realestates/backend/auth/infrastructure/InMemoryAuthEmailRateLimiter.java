package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.application.AuthEmailRateLimitPort;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bộ đếm chống lạm dụng trong bộ nhớ có TTL cho một backend instance theo AUTH-14 & api-specification-sprint-1.md §8.8:
 * - Nhóm gửi email (register / resend / reset-request):
 *   + 1 lần / 60 giây / email chuẩn hóa
 *   + 5 lần / 1 giờ (3600 giây) / email chuẩn hóa
 *   + 20 lần / 1 giờ (3600 giây) / IP
 * - Nhóm hoàn tất xác minh (verify / reset):
 *   + 20 lần / 5 phút (300 giây) / IP
 * - Tối đa 10.000 khóa trong map; khi đầy và sau khi dọn các khóa hết hạn vẫn chạm trần thì trả 429 QUA_NHIEU_YEU_CAU.
 */
@Component
public class InMemoryAuthEmailRateLimiter implements AuthEmailRateLimitPort {

    public static final int MAX_KEYS = 10_000;
    public static final Duration EMAIL_COOLDOWN_WINDOW = Duration.ofSeconds(60);
    public static final int MAX_EMAIL_REQUESTS_PER_COOLDOWN = 1;
    public static final Duration EMAIL_HOURLY_WINDOW = Duration.ofHours(1);
    public static final int MAX_EMAIL_REQUESTS_PER_HOUR = 5;
    public static final Duration IP_HOURLY_WINDOW = Duration.ofHours(1);
    public static final int MAX_IP_EMAIL_REQUESTS_PER_HOUR = 20;
    public static final Duration VERIFY_IP_WINDOW = Duration.ofMinutes(5);
    public static final int MAX_VERIFY_ATTEMPTS_PER_IP_WINDOW = 20;

    private static final class Bucket {
        private final Deque<Instant> timestamps = new ArrayDeque<>();
        private Instant expiresAt = Instant.EPOCH;
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private Clock clock;

    @Autowired
    public InMemoryAuthEmailRateLimiter() {
        this(Clock.systemUTC());
    }

    public InMemoryAuthEmailRateLimiter(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public synchronized void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public synchronized void clear() {
        buckets.clear();
    }

    public synchronized int size() {
        return buckets.size();
    }

    @Override
    public synchronized void checkAndRecordVerificationEmailRequest(String clientIp, String normalizedEmail) {
        Instant now = clock.instant();
        String safeIp = normalizeIp(clientIp);
        String safeEmail = normalizeEmail(normalizedEmail);

        String cooldownKey = "mail:cooldown:email:" + safeEmail;
        String emailHourKey = "mail:hour:email:" + safeEmail;
        String ipHourKey = "mail:hour:ip:" + safeIp;

        // 1. Kiểm tra trước cả 3 ngưỡng để nếu vượt ngưỡng nào thì không ghi nhận lượt thừa
        checkLimitOrThrow(cooldownKey, MAX_EMAIL_REQUESTS_PER_COOLDOWN, EMAIL_COOLDOWN_WINDOW, now);
        checkLimitOrThrow(emailHourKey, MAX_EMAIL_REQUESTS_PER_HOUR, EMAIL_HOURLY_WINDOW, now);
        checkLimitOrThrow(ipHourKey, MAX_IP_EMAIL_REQUESTS_PER_HOUR, IP_HOURLY_WINDOW, now);

        // 2. Đảm bảo không vượt trần 10.000 khóa trước khi thêm khóa mới
        ensureCapacityForKeys(now, cooldownKey, emailHourKey, ipHourKey);

        // 3. Ghi nhận lượt gửi
        recordHit(cooldownKey, EMAIL_COOLDOWN_WINDOW, now);
        recordHit(emailHourKey, EMAIL_HOURLY_WINDOW, now);
        recordHit(ipHourKey, IP_HOURLY_WINDOW, now);
    }

    @Override
    public synchronized void checkAndRecordEmailVerifyAttempt(String clientIp) {
        Instant now = clock.instant();
        String safeIp = normalizeIp(clientIp);
        String verifyIpKey = "verify:5m:ip:" + safeIp;

        checkLimitOrThrow(verifyIpKey, MAX_VERIFY_ATTEMPTS_PER_IP_WINDOW, VERIFY_IP_WINDOW, now);
        ensureCapacityForKeys(now, verifyIpKey);
        recordHit(verifyIpKey, VERIFY_IP_WINDOW, now);
    }

    private void checkLimitOrThrow(String key, int maxAllowed, Duration window, Instant now) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return;
        }
        pruneExpiredTimestamps(bucket, window, now);
        if (bucket.timestamps.isEmpty()) {
            buckets.remove(key);
            return;
        }
        if (bucket.timestamps.size() >= maxAllowed) {
            Instant oldest = bucket.timestamps.peekFirst();
            long elapsedSeconds = Duration.between(oldest, now).getSeconds();
            int retryAfterSeconds = (int) Math.max(1L, window.getSeconds() - elapsedSeconds);
            throw new AuthRateLimitExceededException(retryAfterSeconds);
        }
    }

    private void ensureCapacityForKeys(Instant now, String... candidateKeys) {
        int newKeysNeeded = 0;
        for (String key : candidateKeys) {
            if (!buckets.containsKey(key)) {
                newKeysNeeded++;
            }
        }
        if (buckets.size() + newKeysNeeded <= MAX_KEYS) {
            return;
        }
        evictExpiredBuckets(now);
        if (buckets.size() + newKeysNeeded > MAX_KEYS) {
            throw new AuthRateLimitExceededException(60);
        }
    }

    private void recordHit(String key, Duration window, Instant now) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        pruneExpiredTimestamps(bucket, window, now);
        bucket.timestamps.addLast(now);
        bucket.expiresAt = now.plus(window);
    }

    private static void pruneExpiredTimestamps(Bucket bucket, Duration window, Instant now) {
        Instant cutoff = now.minus(window);
        while (!bucket.timestamps.isEmpty()) {
            Instant first = bucket.timestamps.peekFirst();
            if (!first.isAfter(cutoff)) {
                bucket.timestamps.pollFirst();
            } else {
                break;
            }
        }
    }

    private void evictExpiredBuckets(Instant now) {
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            if (!entry.getValue().expiresAt.isAfter(now) || entry.getValue().timestamps.isEmpty()) {
                it.remove();
            }
        }
    }

    private static String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
