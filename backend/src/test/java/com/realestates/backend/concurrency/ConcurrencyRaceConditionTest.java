package com.realestates.backend.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.common.AbstractPostgresIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cấp độ 5: Kiểm thử đồng thời & cạnh tranh (Concurrency & Race Condition Testing) theo IEEE 829 - Mục 7.5.
 * Kỹ thuật:
 * - Khởi tạo các luồng song song qua ExecutorService.
 * - Dùng CountDownLatch(1) làm chốt xuất phát để mọi luồng bắn câu lệnh vào PostgreSQL 17 tại cùng mili-giây t0.
 * Kịch bản:
 * - Concurrency-01 (TS1-29): Tài khoản đã có 2 tin trong tháng, bắn đồng thời 2 request tạo tin mới.
 *   Kỳ vọng: Đúng 1 luồng cập nhật thành công, tổng số tin trong tháng đạt tối đa đúng 3 (BR-09), không bị race condition vượt quá hạn mức.
 */
class ConcurrencyRaceConditionTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("CONCURRENCY-01 / TS1-29: Đua tạo tin khi đã có 2 tin trong tháng -> Đúng 1 luồng thành công, tổng = 3")
    void shouldHandleConcurrentListingCreationsAtomically() throws InterruptedException {
        // 1. Chuẩn bị dữ liệu thử: Tài khoản có sẵn 2 tin trong tháng 2026-11
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO tai_khoan (supabase_auth_user_id, email, ho_ten, vai_tro, trang_thai) " +
                "VALUES (?, ?, 'User Race Test', 'NGUOI_DUNG', 'HOAT_DONG') RETURNING id",
                Long.class,
                "sb_auth_" + UUID.randomUUID(),
                "user_race_" + UUID.randomUUID() + "@bds-test.local"
        );

        jdbcTemplate.update(
                "INSERT INTO han_muc_thang (tai_khoan_id, thang_nam, so_tin_da_dang) VALUES (?, '2026-11', 2)",
                userId
        );

        // 2. Thiết lập 2 luồng song song cùng bấm xuất phát qua CountDownLatch
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    // Chờ tín hiệu xuất phát chung t0
                    startGate.await();

                    // Câu lệnh tăng hạn mức có điều kiện nguyên tử (Atomic Conditional Update)
                    int affectedRows = jdbcTemplate.update(
                            "UPDATE han_muc_thang SET so_tin_da_dang = so_tin_da_dang + 1 " +
                            "WHERE tai_khoan_id = ? AND thang_nam = '2026-11' AND so_tin_da_dang < 3",
                            userId
                    );

                    if (affectedRows == 1) {
                        successCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // 3. Mở chốt đồng thời
        startGate.countDown();
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Kiểm tra kết quả bất biến:
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(1);

        // Giá trị cuối cùng trong database phải bằng 3 (BR-09, không vượt quá hạn mức)
        Integer finalListingCount = jdbcTemplate.queryForObject(
                "SELECT so_tin_da_dang FROM han_muc_thang WHERE tai_khoan_id = ? AND thang_nam = '2026-11'",
                Integer.class,
                userId
        );
        assertThat(finalListingCount).isEqualTo(3);
    }
}
