package com.realestates.backend.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Lớp kiểm thử cơ sở cho các bài test tích hợp cơ sở dữ liệu PostgreSQL 17 (IEEE 829 - Mục 7.2 & 12.1).
 * Sử dụng Testcontainers để khởi chạy container PostgreSQL 17 thật theo mô hình Singleton Container,
 * bảo đảm container sống xuyên suốt vòng đời toàn bộ suite kiểm thử, tránh lỗi đóng mở container làm
 * hỏng kết nối HikariPool đã được cache trong Spring ApplicationContext.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("bds_test_db")
                .withUsername("bds_test")
                .withPassword("bds_secret");
        postgres.start();
    }

    public static PostgreSQLContainer<?> getPostgresContainer() {
        return postgres;
    }
}
