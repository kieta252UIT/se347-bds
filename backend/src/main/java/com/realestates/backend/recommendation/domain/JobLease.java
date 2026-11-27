package com.realestates.backend.recommendation.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Thực thể quản lý khóa phân tán (Distributed Lease) cho các tác vụ nền định kỳ
 * theo kiến trúc SDD-23, bảo đảm đa tiến trình / đa instance không chạy trùng lặp.
 */
public record JobLease(
        String jobName,
        String instanceId,
        Instant leaseUntil,
        Instant lastHeartbeat
) {
    public JobLease {
        Objects.requireNonNull(jobName, "jobName không được null");
        Objects.requireNonNull(instanceId, "instanceId không được null");
        Objects.requireNonNull(leaseUntil, "leaseUntil không được null");
        Objects.requireNonNull(lastHeartbeat, "lastHeartbeat không được null");
    }

    public boolean conHieuLuc(Instant now) {
        return leaseUntil.isAfter(now);
    }

    public boolean thuocVe(String otherInstanceId) {
        return this.instanceId.equals(otherInstanceId);
    }
}
