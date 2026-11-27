package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.JobLease;
import com.realestates.backend.recommendation.domain.JobLeaseRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryJobLeaseRepository implements JobLeaseRepository {

    private final ConcurrentHashMap<String, JobLease> store = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean acquireOrRenewLease(String jobName, String instanceId, Duration duration, Instant now) {
        JobLease current = store.get(jobName);

        if (current == null || !current.conHieuLuc(now) || current.thuocVe(instanceId)) {
            JobLease updated = new JobLease(
                    jobName,
                    instanceId,
                    now.plus(duration),
                    now
            );
            store.put(jobName, updated);
            return true;
        }

        return false;
    }

    @Override
    public synchronized boolean releaseLease(String jobName, String instanceId, Instant now) {
        JobLease current = store.get(jobName);
        if (current != null && current.thuocVe(instanceId)) {
            // Giải phóng lease bằng cách đặt leaseUntil = now
            store.put(jobName, new JobLease(jobName, instanceId, now, now));
            return true;
        }
        return false;
    }

    @Override
    public Optional<JobLease> findByName(String jobName) {
        return Optional.ofNullable(store.get(jobName));
    }

    public void clear() {
        store.clear();
    }
}
