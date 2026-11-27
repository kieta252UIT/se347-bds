package com.realestates.backend.recommendation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface JobLeaseRepository {

    /**
     * Cố gắng giành quyền sở hữu hoặc gia hạn lease theo phương thức nguyên tử CAS (Compare-And-Swap).
     * @return true nếu giành được lease hoặc gia hạn thành công; false nếu tiến trình khác đang giữ lease còn hiệu lực.
     */
    boolean acquireOrRenewLease(String jobName, String instanceId, Duration duration, Instant now);

    /**
     * Giải phóng lease khi job kết thúc.
     */
    boolean releaseLease(String jobName, String instanceId, Instant now);

    Optional<JobLease> findByName(String jobName);
}
