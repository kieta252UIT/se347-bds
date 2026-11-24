package com.realestates.backend.recommendation.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.DiemGoiYRepository;
import com.realestates.backend.recommendation.domain.TieuChiGoiY;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.dto.GoiYQueryRequest;
import com.realestates.backend.recommendation.dto.GoiYResponse;
import com.realestates.backend.recommendation.dto.TinGoiYItemDto;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoiYApplicationService {

    public static final String NGU_CANH_C1_KHACH_KHONG_GPS = "C1_KHACH_KHONG_GPS";
    public static final String NGU_CANH_C2_KHACH_CO_GPS = "C2_KHACH_CO_GPS";
    public static final String NGU_CANH_C3_NGUOI_DUNG_KHONG_GPS = "C3_NGUOI_DUNG_KHONG_GPS";
    public static final String NGU_CANH_C4_NGUOI_DUNG_CO_GPS = "C4_NGUOI_DUNG_CO_GPS";

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final KiemTraTinChoGoiYPort kiemTraTinPort;
    private final DiemGoiYRepository diemGoiYRepository;
    private final ThuatToanTinhDiemGoiY thuatToanTinhDiem;
    private final Clock clock;
    private final Random random;

    @Autowired
    public GoiYApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            KiemTraTinChoGoiYPort kiemTraTinPort,
            DiemGoiYRepository diemGoiYRepository
    ) {
        this(permissionAndCsrfService, kiemTraTinPort, diemGoiYRepository, new ThuatToanTinhDiemGoiY(), Clock.systemUTC(), new Random());
    }

    public GoiYApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            KiemTraTinChoGoiYPort kiemTraTinPort,
            DiemGoiYRepository diemGoiYRepository,
            ThuatToanTinhDiemGoiY thuatToanTinhDiem,
            Clock clock,
            Random random
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.kiemTraTinPort = Objects.requireNonNull(kiemTraTinPort, "kiemTraTinPort không được null");
        this.diemGoiYRepository = Objects.requireNonNull(diemGoiYRepository, "diemGoiYRepository không được null");
        this.thuatToanTinhDiem = Objects.requireNonNull(thuatToanTinhDiem, "thuatToanTinhDiem không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
        this.random = Objects.requireNonNull(random, "random không được null");
    }

    /**
     * Nhận gợi ý bất động sản theo 4 ngữ cảnh độc lập (AC-03.1, AC-03.2, AC-03.3, BR-01, BR-06).
     */
    public GoiYResponse layGoiY(String rawSessionCookie, GoiYQueryRequest query) {
        Instant now = clock.instant();

        // 1. Phân giải danh tính (Khách vs Người dùng)
        Optional<PrincipalPhienHienTai> principalOpt = permissionAndCsrfService
                .phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookie);
        boolean laNguoiDung = principalOpt.isPresent();
        UUID taiKhoanId = principalOpt.map(PrincipalPhienHienTai::getTaiKhoanId).orElse(null);

        // 2. Phân giải vị trí áp dụng (Có GPS vs Không có GPS -> Mặc định TP.HCM)
        boolean coGps = query != null && query.viDo() != null && query.kinhDo() != null;
        ViTriToaDo toaDoApDung = coGps
                ? new ViTriToaDo(query.viDo(), query.kinhDo())
                : ViTriToaDo.TOA_DO_MAC_DINH_TPHCM;
        boolean dungViTriMacDinh = !coGps;

        // 3. Xác định mã ngữ cảnh (C1, C2, C3, C4)
        String nguCanh;
        if (!laNguoiDung) {
            nguCanh = dungViTriMacDinh ? NGU_CANH_C1_KHACH_KHONG_GPS : NGU_CANH_C2_KHACH_CO_GPS;
        } else {
            nguCanh = dungViTriMacDinh ? NGU_CANH_C3_NGUOI_DUNG_KHONG_GPS : NGU_CANH_C4_NGUOI_DUNG_CO_GPS;
        }

        // 4. Chuẩn bị tiêu chí lọc
        TieuChiGoiY tieuChi = query == null
                ? new TieuChiGoiY(null, null, null, null, null)
                : new TieuChiGoiY(query.loaiBds(), query.loaiGiaoDich(), query.giaMin(), query.giaMax(), query.khuVuc());

        // 5. Truy vấn danh sách tin công khai hợp lệ (BR-01)
        List<ThongTinTinGoiY> danhSachTinCongKhai = kiemTraTinPort.layDanhSachTinCongKhaiHopLe(tieuChi, now);

        // BR-06: Loại bỏ các tin có gói tài trợ đang hiệu lực khỏi danh sách gợi ý tự nhiên
        List<ThongTinTinGoiY> danhSachTuNhienHopLe = danhSachTinCongKhai.stream()
                .filter(t -> !t.coGoiTaiTroHieuLuc())
                .filter(ThongTinTinGoiY::hopLeCongKhaiBR01)
                .toList();

        // 6. Xếp hạng danh sách gợi ý tự nhiên
        int limit = query == null ? 20 : query.getLimitOrFallback();
        List<TinGoiYItemDto> danhSachTuNhien = xepHangGoiYTuNhien(
                danhSachTuNhienHopLe,
                taiKhoanId,
                toaDoApDung,
                now,
                limit
        );

        // 7. Truy vấn khu vực Được tài trợ riêng biệt (BR-06: Ngẫu nhiên tối đa 5 tin)
        List<TinGoiYItemDto> danhSachTaiTro = layKhuVucDuocTaiTro(tieuChi, toaDoApDung, now);

        return new GoiYResponse(
                danhSachTuNhien,
                danhSachTaiTro,
                nguCanh,
                dungViTriMacDinh,
                danhSachTuNhien.size(),
                danhSachTaiTro.size()
        );
    }

    private List<TinGoiYItemDto> xepHangGoiYTuNhien(
            List<ThongTinTinGoiY> danhSachTin,
            UUID taiKhoanId,
            ViTriToaDo toaDoApDung,
            Instant now,
            int limit
    ) {
        if (danhSachTin.isEmpty()) {
            return Collections.emptyList();
        }

        // Nếu là người dùng có danh tính: kiểm tra điểm nền
        if (taiKhoanId != null) {
            Map<UUID, BigDecimal> mapDiemNen = diemGoiYRepository.findByTaiKhoanId(taiKhoanId).stream()
                    .collect(Collectors.toMap(DiemGoiY::tinId, DiemGoiY::diem, (d1, d2) -> d1));

            if (!mapDiemNen.isEmpty()) {
                // Có điểm nền: xếp hạng theo điểm nền giảm dần, khóa phụ là khoảng cách và độ mới
                return danhSachTin.stream()
                        .map(tin -> {
                            BigDecimal khoangCach = tin.toaDo() != null
                                    ? tin.toaDo().tinhKhoangCachKm(toaDoApDung)
                                    : BigDecimal.valueOf(9999);
                            BigDecimal diem = mapDiemNen.getOrDefault(
                                    tin.tinId(),
                                    thuatToanTinhDiem.tinhDiemNguoiMoiHoacKhach(tin, toaDoApDung, now)
                            );
                            return toDto(tin, khoangCach, diem, false);
                        })
                        .sorted(Comparator.comparing(TinGoiYItemDto::diemGoiY, Comparator.reverseOrder())
                                .thenComparing(TinGoiYItemDto::khoangCachKm)
                                .thenComparing(TinGoiYItemDto::ngayDang, Comparator.reverseOrder())
                                .thenComparing(TinGoiYItemDto::tinId))
                        .limit(limit)
                        .toList();
            }
        }

        // Fallback: Khách hoặc Người mới chưa có điểm nền -> "Gần trước, mới sau"
        return danhSachTin.stream()
                .map(tin -> {
                    BigDecimal khoangCach = tin.toaDo() != null
                            ? tin.toaDo().tinhKhoangCachKm(toaDoApDung)
                            : BigDecimal.valueOf(9999);
                    BigDecimal diem = thuatToanTinhDiem.tinhDiemNguoiMoiHoacKhach(tin, toaDoApDung, now);
                    return toDto(tin, khoangCach, diem, false);
                })
                .sorted(Comparator.comparing(TinGoiYItemDto::khoangCachKm)
                        .thenComparing(TinGoiYItemDto::ngayDang, Comparator.reverseOrder())
                        .thenComparing(TinGoiYItemDto::tinId))
                .limit(limit)
                .toList();
    }

    private List<TinGoiYItemDto> layKhuVucDuocTaiTro(TieuChiGoiY tieuChi, ViTriToaDo toaDoApDung, Instant now) {
        List<ThongTinTinGoiY> danhSachTaiTro = kiemTraTinPort.layDanhSachTinTaiTroHopLe(tieuChi, now);
        if (danhSachTaiTro.isEmpty()) {
            return Collections.emptyList();
        }

        // Q16: Chọn ngẫu nhiên không lặp tối đa 5 tin, mọi tin có xác suất bằng nhau
        List<ThongTinTinGoiY> danhSachXaoTron = new ArrayList<>(danhSachTaiTro);
        Collections.shuffle(danhSachXaoTron, random);

        return danhSachXaoTron.stream()
                .limit(5)
                .map(tin -> {
                    BigDecimal khoangCach = tin.toaDo() != null
                            ? tin.toaDo().tinhKhoangCachKm(toaDoApDung)
                            : BigDecimal.valueOf(9999);
                    return toDto(tin, khoangCach, BigDecimal.valueOf(100), true);
                })
                .toList();
    }

    private TinGoiYItemDto toDto(ThongTinTinGoiY tin, BigDecimal khoangCachKm, BigDecimal diem, boolean duocTaiTro) {
        BigDecimal viDo = tin.toaDo() != null ? tin.toaDo().viDo() : null;
        BigDecimal kinhDo = tin.toaDo() != null ? tin.toaDo().kinhDo() : null;
        return new TinGoiYItemDto(
                tin.tinId(),
                tin.tieuDe(),
                tin.moTa(),
                tin.loaiGiaoDich(),
                tin.loaiBds(),
                tin.giaGoc(),
                tin.donViGia(),
                tin.dienTichM2(),
                tin.diaChi(),
                tin.khuVuc(),
                viDo,
                kinhDo,
                khoangCachKm,
                diem,
                tin.ngayDang(),
                tin.anhDaiDienId(),
                duocTaiTro
        );
    }
}
