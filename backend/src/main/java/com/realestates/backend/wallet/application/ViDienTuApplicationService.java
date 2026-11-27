package com.realestates.backend.wallet.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.wallet.domain.GiaoDichVi;
import com.realestates.backend.wallet.domain.GiaoDichViRepository;
import com.realestates.backend.wallet.domain.ViDienTu;
import com.realestates.backend.wallet.domain.ViDienTuRepository;
import com.realestates.backend.wallet.dto.ViDienTuResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViDienTuApplicationService {

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final ViDienTuRepository viDienTuRepository;
    private final GiaoDichViRepository giaoDichViRepository;
    private final Clock clock;

    @Autowired
    public ViDienTuApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            ViDienTuRepository viDienTuRepository,
            GiaoDichViRepository giaoDichViRepository
    ) {
        this(permissionAndCsrfService, viDienTuRepository, giaoDichViRepository, Clock.systemUTC());
    }

    public ViDienTuApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            ViDienTuRepository viDienTuRepository,
            GiaoDichViRepository giaoDichViRepository,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.viDienTuRepository = Objects.requireNonNull(viDienTuRepository, "viDienTuRepository không được null");
        this.giaoDichViRepository = Objects.requireNonNull(giaoDichViRepository, "giaoDichViRepository không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    public ViDienTuResponse layViCuaToi(String rawSessionCookie) {
        PrincipalPhienHienTai principal = permissionAndCsrfService.kiemTraPhienVaQuyenHienTai(rawSessionCookie);

        UUID taiKhoanId = principal.getTaiKhoanId();
        ViDienTu vi = viDienTuRepository.findByTaiKhoanId(taiKhoanId)
                .orElseGet(() -> viDienTuRepository.save(ViDienTu.khoiTao(taiKhoanId)));

        List<GiaoDichVi> danhSachGiaoDich = giaoDichViRepository.findByViId(vi.id());

        List<ViDienTuResponse.GiaoDichViItemDto> giaoDichDtos = danhSachGiaoDich.stream()
                .map(gd -> new ViDienTuResponse.GiaoDichViItemDto(
                        gd.id(),
                        gd.loai(),
                        gd.trangThai(),
                        gd.chieuBienDong(),
                        gd.soTien(),
                        gd.soDuSau(),
                        gd.thoiDiemGhiNhan(),
                        gd.ghiChu()
                ))
                .toList();

        return new ViDienTuResponse(
                vi.id(),
                vi.taiKhoanId(),
                vi.soDuVnd(),
                vi.capNhatLuc(),
                giaoDichDtos
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ViDienTuResponse khoiTaoSoDuThu(String rawSessionCookie, BigDecimal soTien, String ghiChu) {
        PrincipalPhienHienTai principal = permissionAndCsrfService.kiemTraPhienVaQuyenHienTai(rawSessionCookie);
        BigDecimal amount = (soTien != null && soTien.compareTo(BigDecimal.ZERO) > 0)
                ? soTien
                : new BigDecimal("500000");
        String note = (ghiChu != null && !ghiChu.isBlank())
                ? ghiChu
                : "Khởi tạo số dư thử nghiệm phục vụ kiểm thử Sprint 2";
        khoiTaoSoDuThuNhatQuan(principal.getTaiKhoanId(), amount, note);
        return layViCuaToi(rawSessionCookie);
    }

    @Transactional(rollbackFor = Exception.class)
    public ViDienTu khoiTaoSoDuThuNhatQuan(UUID taiKhoanId, BigDecimal soTien, String ghiChu) {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(soTien, "soTien không được null");
        if (soTien.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền khởi tạo phải lớn hơn 0");
        }

        Instant now = clock.instant();
        ViDienTu vi = viDienTuRepository.findByTaiKhoanIdForUpdate(taiKhoanId)
                .orElseGet(() -> viDienTuRepository.save(ViDienTu.khoiTao(taiKhoanId)));

        BigDecimal soDuTruoc = vi.soDuVnd();
        vi = vi.congTien(soTien, now);
        viDienTuRepository.save(vi);

        GiaoDichVi butToan = GiaoDichVi.taoButToanCongTien(
                vi.id(),
                "khoi_tao_thu_nghiem",
                soTien,
                soDuTruoc,
                vi.soDuVnd(),
                "SEED_INTENT_" + UUID.randomUUID(),
                ghiChu != null ? ghiChu : "Khởi tạo số dư thử nghiệm minh bạch theo sổ giao dịch",
                now
        );
        giaoDichViRepository.save(butToan);

        return vi;
    }

    public boolean kiemToanNhatQuanSoDu(UUID viId) {
        Objects.requireNonNull(viId, "viId không được null");
        ViDienTu vi = viDienTuRepository.findById(viId).orElseThrow(() -> new IllegalArgumentException("Ví không tồn tại"));
        List<GiaoDichVi> danhSach = giaoDichViRepository.findByViIdAndTrangThai(viId, "thanh_cong");

        BigDecimal tong = BigDecimal.ZERO;
        for (GiaoDichVi gd : danhSach) {
            if ("cong".equals(gd.chieuBienDong())) {
                tong = tong.add(gd.soTien());
            } else if ("tru".equals(gd.chieuBienDong())) {
                tong = tong.subtract(gd.soTien());
            }
        }
        return vi.soDuVnd().compareTo(tong) == 0;
    }
}
