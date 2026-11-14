package com.realestates.backend.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Chỉ dùng DB thử riêng; tuyệt đối không fallback sang DB_URL của ứng dụng. */
public record TestDatabaseSettings(String url, String username, String password) {

    public static TestDatabaseSettings fromEnvironment() {
        if (!"true".equals(System.getenv("ALLOW_TEST_SCHEMA_RESET"))) {
            throw new IllegalStateException("Test yêu cầu ALLOW_TEST_SCHEMA_RESET=true trên DB thử riêng.");
        }
        return new TestDatabaseSettings(required("DB_TEST_URL"), required("DB_TEST_USERNAME"),
                required("DB_TEST_PASSWORD"));
    }

    public Connection connect() throws SQLException {
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("DB_TEST_URL phải là PostgreSQL JDBC URL.");
        }
        Connection connection = DriverManager.getConnection(url, username, password);
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database()")) {
            result.next();
            if (!result.getString(1).endsWith("_test")) {
                throw new IllegalStateException("Từ chối chạy test: tên DB thực tế phải kết thúc bằng _test.");
            }
        } catch (SQLException | RuntimeException ex) {
            connection.close();
            throw ex;
        }
        return connection;
    }

    public Flyway flyway(String... locations) {
        return Flyway.configure().dataSource(url, username, password)
                .schemas("se214").defaultSchema("se214").createSchemas(true)
                .cleanDisabled(true).baselineOnMigrate(false)
                .locations(locations.length == 0 ? new String[]{"classpath:db/migration"} : locations)
                .load();
    }

    public void resetSchema() throws SQLException {
        // connect() kiểm lại DB đích trước mỗi thao tác phá hủy trong môi trường thử.
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS se214 CASCADE");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Thiếu biến môi trường thử " + name);
        }
        return value;
    }

    public static class SpringInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestDatabaseSettings db = fromEnvironment();
            try (var ignored = db.connect()) {
                TestPropertyValues.of(
                        "spring.datasource.url=" + db.url(),
                        "spring.datasource.username=" + db.username(),
                        "spring.datasource.password=" + db.password(),
                        "spring.flyway.url=" + db.url(),
                        "spring.flyway.user=" + db.username(),
                        "spring.flyway.password=" + db.password()
                ).applyTo(context.getEnvironment());
            } catch (SQLException ex) {
                throw new IllegalStateException("Không kết nối được PostgreSQL thử.", ex);
            }
        }
    }
}
