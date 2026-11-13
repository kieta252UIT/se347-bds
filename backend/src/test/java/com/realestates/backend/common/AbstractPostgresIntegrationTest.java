package com.realestates.backend.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Lớp kiểm thử cơ sở cho các bài test tích hợp cơ sở dữ liệu PostgreSQL 17 (IEEE 829 - Mục 7.2 & 12.1).
 * Sử dụng Testcontainers để khởi chạy container PostgreSQL 17 thật, bảo đảm kiểm tra được các cơ chế
 * khóa dòng (SELECT ... FOR UPDATE), số thực chính xác NUMERIC, Flyway Migration và Transaction Rollback.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("bds_test_db")
                    .withUsername("bds_test")
                    .withPassword("bds_secret");

    public static PostgreSQLContainer<?> getPostgresContainer() {
        return postgres;
    }
}
