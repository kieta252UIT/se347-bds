package com.realestates.backend.recommendation.application;

import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.DiemGoiYRepository;
import com.realestates.backend.recommendation.domain.JobLeaseRepository;
import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.LichSuXemRepository;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service thực thi tác vụ nền tính điểm gợi ý bất động sản (Background Recommendation Job)
 * với cơ chế khóa phân tán Lease chống đụng độ khi chạy đa tiến trình theo kiến trúc SDD-23.
 */
@Service
public class JobTinhDiemGoiYNenService {

    public static final String TEN_JOB = "JOB_TINH_DIEM_GOI_Y";
    public static final Duration CUA_SO_CAP_NHAT_MAC_DINH_5_PHUT = Duration.ofMinutes(5);
    public static final Duration CUA_SO_LICH_SU_MAC_DINH_30_NGAY = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(JobTinhDiemGoiYNenService.class);

    private final JobLeaseRepository jobLeaseRepository;
    private final LichSuXemRepository lichSuXemRepository;
    private final DiemGoiYRepository diemGoiYRepository;
    private final KiemTraTinChoGoiYPort kiemTraTinPort;
    private final ThuatToanTinhDiemGoiY thuatToanTinhDiem;
    private final Clock clock;
    private final String instanceId;

    public record KetQuaChayJob(
            boolean thanhCong,
            int soUserDaTinh,
            int soDiemDaLuu,
            String thongDiep
    ) {
    }

    @Autowired
    public JobTinhDiemGoiYNenService(
            JobLeaseRepository jobLeaseRepository,
            LichSuXemRepository lichSuXemRepository,
            DiemGoiYRepository diemGoiYRepository,
            KiemTraTinChoGoiYPort kiemTraTinPort
    ) {
        this(
                jobLeaseRepository,
                lichSuXemRepository,
                diemGoiYRepository,
                kiemTraTinPort,
                new ThuatToanTinhDiemGoiY(),
                Clock.systemUTC(),
                UUID.randomUUID().toString()
        );
    }

    public JobTinhDiemGoiYNenService(
            JobLeaseRepository jobLeaseRepository,
            LichSuXemRepository lichSuXemRepository,
            DiemGoiYRepository diemGoiYRepository,
            KiemTraTinChoGoiYPort kiemTraTinPort,
            ThuatToanTinhDiemGoiY thuatToanTinhDiem,
            Clock clock,
            String instanceId
    ) {
        this.jobLeaseRepository = Objects.requireNonNull(jobLeaseRepository, "jobLeaseRepository không được null");
        this.lichSuXemRepository = Objects.requireNonNull(lichSuXemRepository, "lichSuXemRepository không được null");
        this.diemGoiYRepository = Objects.requireNonNull(diemGoiYRepository, "diemGoiYRepository không được null");
        this.kiemTraTinPort = Objects.requireNonNull(kiemTraTinPort, "kiemTraTinPort không được null");
        this.thuatToanTinhDiem = Objects.requireNonNull(thuatToanTinhDiem, "thuatToanTinhDiem không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
        this.instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString();
    }

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Thực hiện một chu kỳ chạy tính điểm nền (được gọi từ Spring @Scheduled hoặc gọi thủ công / test).
     */
    public KetQuaChayJob chayJobTinhDiemNen(Duration cuaSoCapNhatTuongTac) {
        Instant now = clock.instant();

        // 1. Cố gắng giành lease trong 2 phút
        boolean daGianhLease = jobLeaseRepository.acquireOrRenewLease(
                TEN_JOB,
                instanceId,
                Duration.ofMinutes(2),
                now
        );

        if (!daGianhLease) {
            log.info("Tiến trình {} bỏ qua nhịp chạy vì lease đang thuộc về tiến trình khác", instanceId);
            return new KetQuaChayJob(false, 0, 0, "Lease đang được giữ bởi tiến trình khác");
        }

        try {
            // 2. Quét danh sách người dùng có tương tác trong cửa sổ thời gian (duy trì cập nhật 5 phút theo AC-03.2 & Q28)
            Duration cuaSoQuet = cuaSoCapNhatTuongTac != null ? cuaSoCapNhatTuongTac : CUA_SO_CAP_NHAT_MAC_DINH_5_PHUT;
            Instant tuThoiDiemQuet = now.minus(cuaSoQuet);
            List<UUID> danhSachUserId = lichSuXemRepository.findTaiKhoanCoTuongTacTrongKhoang(tuThoiDiemQuet, now);

            if (danhSachUserId.isEmpty()) {
                jobLeaseRepository.releaseLease(TEN_JOB, instanceId, now);
                return new KetQuaChayJob(true, 0, 0, "Không có tương tác mới nào trong cửa sổ 5 phút");
            }

            // 3. Lấy danh sách tin công khai hợp lệ (BR-01)
            List<ThongTinTinGoiY> danhSachTin = kiemTraTinPort.layDanhSachTinCongKhaiHopLe(null, now);
            Map<UUID, ThongTinTinGoiY> mapTin = danhSachTin.stream()
                    .collect(Collectors.toMap(ThongTinTinGoiY::tinId, t -> t, (t1, t2) -> t1));

            int tongDiemDaLuu = 0;

            // 4. Tính toán điểm cho từng user trong cửa sổ trượt 30 ngày (Sliding Window 30 days)
            Instant tuThoiDiem30Ngay = now.minus(CUA_SO_LICH_SU_MAC_DINH_30_NGAY);
            for (UUID userId : danhSachUserId) {
                // Lấy lịch sử tương tác trong cửa sổ 30 ngày (loại bỏ tương tác cũ hơn 30 ngày)
                List<LichSuXem> lichSu30Ngay = lichSuXemRepository.findByTaiKhoanIdTrongKhoang(userId, tuThoiDiem30Ngay, now);
                List<DiemGoiY> danhSachDiemMoi = new ArrayList<>();

                for (ThongTinTinGoiY tin : danhSachTin) {
                    BigDecimal diem = thuatToanTinhDiem.tinhDiemTongHop(
                            tin,
                            ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, // vị trí mặc định người dùng
                            lichSu30Ngay,
                            mapTin,
                            now
                    );
                    danhSachDiemMoi.add(new DiemGoiY(userId, tin.tinId(), diem, now));
                }

                diemGoiYRepository.saveAll(danhSachDiemMoi);
                tongDiemDaLuu += danhSachDiemMoi.size();
            }

            // 5. Giải phóng lease
            jobLeaseRepository.releaseLease(TEN_JOB, instanceId, now);

            return new KetQuaChayJob(
                    true,
                    danhSachUserId.size(),
                    tongDiemDaLuu,
                    "Tính toán thành công điểm nền cho " + danhSachUserId.size() + " tài khoản"
            );

        } catch (Exception ex) {
            log.error("Lỗi trong quá trình chạy job tính điểm nền", ex);
            jobLeaseRepository.releaseLease(TEN_JOB, instanceId, now);
            throw ex;
        }
    }

    /**
     * Chạy chu kỳ tính điểm nền với cửa sổ cập nhật mặc định 5 phút và cửa sổ lịch sử 30 ngày.
     */
    public KetQuaChayJob chayJobTinhDiemNen() {
        return chayJobTinhDiemNen(CUA_SO_CAP_NHAT_MAC_DINH_5_PHUT);
    }

    /**
     * Tự động kích hoạt job theo lịch định kỳ (mặc định 5 phút = 300.000 ms theo Q28 & SDD-23).
     */
    @Scheduled(fixedDelayString = "${app.recommendation.job-interval-ms:300000}")
    public void scheduledRun() {
        try {
            chayJobTinhDiemNen();
        } catch (Exception e) {
            log.warn("Lỗi khi chạy scheduled job tính điểm nền: {}", e.getMessage());
        }
    }
}
