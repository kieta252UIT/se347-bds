package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.JobLease;
import com.realestates.backend.recommendation.domain.JobLeaseRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class PostgreSqlJobLeaseRepository implements JobLeaseRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<JobLease> ROW_MAPPER = (rs, rowNum) -> new JobLease(
            rs.getString("job_name"),
            rs.getString("instance_id"),
            rs.getTimestamp("lease_until").toInstant(),
            rs.getTimestamp("last_heartbeat").toInstant()
    );

    @Autowired
    public PostgreSqlJobLeaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate không được null");
    }

    @Override
    public boolean acquireOrRenewLease(String jobName, String instanceId, Duration duration, Instant now) {
        Instant leaseUntil = now.plus(duration);

        String sql = """
                INSERT INTO se214.job_lease (job_name, instance_id, lease_until, last_heartbeat)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (job_name)
                DO UPDATE SET instance_id = EXCLUDED.instance_id,
                              lease_until = EXCLUDED.lease_until,
                              last_heartbeat = EXCLUDED.last_heartbeat
                WHERE se214.job_lease.lease_until < ?
                   OR se214.job_lease.instance_id = ?
                """;

        int rows = jdbcTemplate.update(
                sql,
                jobName,
                instanceId,
                Timestamp.from(leaseUntil),
                Timestamp.from(now),
                Timestamp.from(now),
                instanceId
        );

        return rows > 0;
    }

    @Override
    public boolean releaseLease(String jobName, String instanceId, Instant now) {
        String sql = """
                UPDATE se214.job_lease
                SET lease_until = ?
                WHERE job_name = ? AND instance_id = ?
                """;
        int rows = jdbcTemplate.update(sql, Timestamp.from(now), jobName, instanceId);
        return rows > 0;
    }

    @Override
    public Optional<JobLease> findByName(String jobName) {
        String sql = "SELECT job_name, instance_id, lease_until, last_heartbeat FROM se214.job_lease WHERE job_name = ?";
        List<JobLease> list = jdbcTemplate.query(sql, ROW_MAPPER, jobName);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }
}
