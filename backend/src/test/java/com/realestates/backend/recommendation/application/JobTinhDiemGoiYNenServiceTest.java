package com.realestates.backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.*;

import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.infrastructure.InMemoryDiemGoiYRepository;
import com.realestates.backend.recommendation.infrastructure.InMemoryJobLeaseRepository;
import com.realestates.backend.recommendation.infrastructure.InMemoryKiemTraTinChoGoiYAdapter;
import com.realestates.backend.recommendation.infrastructure.InMemoryLichSuXemRepository;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobTinhDiemGoiYNenServiceTest {

    private InMemoryJobLeaseRepository leaseRepository;
    private InMemoryLichSuXemRepository lichSuXemRepository;
    private InMemoryDiemGoiYRepository diemGoiYRepository;
    private InMemoryKiemTraTinChoGoiYAdapter kiemTraTinAdapter;
    private ThuatToanTinhDiemGoiY thuatToanTinhDiem;
    private Clock fixedClock;

    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");

    @BeforeEach
    void setUp() {
        leaseRepository = new InMemoryJobLeaseRepository();
        lichSuXemRepository = new InMemoryLichSuXemRepository();
        diemGoiYRepository = new InMemoryDiemGoiYRepository();
        kiemTraTinAdapter = new InMemoryKiemTraTinChoGoiYAdapter();
        kiemTraTinAdapter.clear();
        thuatToanTinhDiem = new ThuatToanTinhDiemGoiY();
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("SDD-23: Distributed Lease chống đụng độ - Chỉ 1 tiến trình được chạy, tiến trình thứ 2 bị chặn")
    void testDistributedLease_ChongDungDoDaTienTrinh() {
        JobTinhDiemGoiYNenService instance1 = new JobTinhDiemGoiYNenService(
                leaseRepository, lichSuXemRepository, diemGoiYRepository, kiemTraTinAdapter,
                thuatToanTinhDiem, fixedClock, "instance-pod-01"
        );

        JobTinhDiemGoiYNenService instance2 = new JobTinhDiemGoiYNenService(
                leaseRepository, lichSuXemRepository, diemGoiYRepository, kiemTraTinAdapter,
                thuatToanTinhDiem, fixedClock, "instance-pod-02"
        );

        // Instance 1 chủ động giành lease trong 2 phút
        boolean gianhLease1 = leaseRepository.acquireOrRenewLease(
                JobTinhDiemGoiYNenService.TEN_JOB,
                instance1.getInstanceId(),
                Duration.ofMinutes(2),
                now
        );
        assertTrue(gianhLease1, "Instance 1 phải giành được lease ban đầu");

        // Instance 2 cố gắng chạy cùng lúc
        var ketQua2 = instance2.chayJobTinhDiemNen(Duration.ofMinutes(5));
        assertFalse(ketQua2.thanhCong(), "Instance 2 phải bị từ chối do lease đang bận");
        assertTrue(ketQua2.thongDiep().contains("Lease đang được giữ bởi tiến trình khác"));

        // Instance 1 giải phóng lease
        leaseRepository.releaseLease(JobTinhDiemGoiYNenService.TEN_JOB, instance1.getInstanceId(), now);

        // Sau khi instance 1 nhả lease, instance 2 chạy lại sẽ thành công
        var ketQua2Sau = instance2.chayJobTinhDiemNen(Duration.ofMinutes(5));
        assertTrue(ketQua2Sau.thanhCong(), "Instance 2 phải chạy thành công sau khi lease được giải phóng");
    }

    @Test
    @DisplayName("AC-03.2 & Q28: Cửa sổ 5 phút - Quét và cập nhật điểm nền cho người dùng có tương tác mới")
    void testCapNhatDiemTrongCuaSo5Phut() {
        JobTinhDiemGoiYNenService jobService = new JobTinhDiemGoiYNenService(
                leaseRepository, lichSuXemRepository, diemGoiYRepository, kiemTraTinAdapter,
                thuatToanTinhDiem, fixedClock, "worker-node-1"
        );

        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        UUID tinId = UUID.randomUUID();

        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinId, UUID.randomUUID(), "Căn hộ Q1", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "da_duyet", "hoat_dong", false
        ));

        // User 1 có tương tác xem tin cách đây 2 phút (nằm trong cửa sổ 5 phút)
        lichSuXemRepository.save(new LichSuXem(UUID.randomUUID(), user1Id, tinId, now.minus(2, ChronoUnit.MINUTES)));

        // User 2 có tương tác cách đây 10 phút (ngoài cửa sổ 5 phút)
        lichSuXemRepository.save(new LichSuXem(UUID.randomUUID(), user2Id, tinId, now.minus(10, ChronoUnit.MINUTES)));

        // Chạy job với cửa sổ 5 phút
        var ketQua = jobService.chayJobTinhDiemNen(Duration.ofMinutes(5));

        assertTrue(ketQua.thanhCong());
        // Chỉ có User 1 được tính toán
        assertEquals(1, ketQua.soUserDaTinh());
        assertEquals(1, ketQua.soDiemDaLuu());

        // Kiểm tra trong kho điểm nền
        List<DiemGoiY> diemUser1 = diemGoiYRepository.findByTaiKhoanId(user1Id);
        assertEquals(1, diemUser1.size());
        assertEquals(tinId, diemUser1.getFirst().tinId());

        // User 2 không có điểm nền mới do tương tác cũ
        List<DiemGoiY> diemUser2 = diemGoiYRepository.findByTaiKhoanId(user2Id);
        assertTrue(diemUser2.isEmpty());
    }
}
