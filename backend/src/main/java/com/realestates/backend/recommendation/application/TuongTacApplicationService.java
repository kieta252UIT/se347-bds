package com.realestates.backend.recommendation.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.LichSuXemRepository;
import com.realestates.backend.recommendation.dto.GhiNhanTuongTacRequest;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TuongTacApplicationService {

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final LichSuXemRepository lichSuXemRepository;
    private final KiemTraTinChoGoiYPort kiemTraTinPort;
    private final Clock clock;

    @Autowired
    public TuongTacApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            LichSuXemRepository lichSuXemRepository,
            KiemTraTinChoGoiYPort kiemTraTinPort
    ) {
        this(permissionAndCsrfService, lichSuXemRepository, kiemTraTinPort, Clock.systemUTC());
    }

    public TuongTacApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            LichSuXemRepository lichSuXemRepository,
            KiemTraTinChoGoiYPort kiemTraTinPort,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.lichSuXemRepository = Objects.requireNonNull(lichSuXemRepository, "lichSuXemRepository không được null");
        this.kiemTraTinPort = Objects.requireNonNull(kiemTraTinPort, "kiemTraTinPort không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    /**
     * Ghi nhận tương tác của người dùng theo quyền (FR-03, UC-03, Q28, BR-01).
     * - Khách: Xem tin công khai bình thường, không ghi vào CSDL lich_su_xem định danh (trả về false).
     * - Người dùng: Kiểm tra tin thỏa BR-01 và lưu lịch sử tương tác để cập nhật điểm gợi ý nền (trả về true).
     * - Tin không tồn tại hoặc không thỏa BR-01: Báo lỗi KHONG_TIM_THAY (ListingNotFoundException).
     */
    public boolean ghiNhanTuongTac(String rawSessionCookie, GhiNhanTuongTacRequest request) {
        Objects.requireNonNull(request, "request không được null");
        Objects.requireNonNull(request.tinId(), "tinId không được null");

        Instant now = clock.instant();

        // BR-01: Tin đăng phải tồn tại và đang công khai hợp lệ
        boolean tinHopLe = kiemTraTinPort.kiemTraTinHopLeCongKhai(request.tinId(), now);
        if (!tinHopLe) {
            throw new ListingNotFoundException();
        }

        // Kiểm tra quyền phía server bằng cookie phiên HttpOnly
        Optional<PrincipalPhienHienTai> principalOpt = permissionAndCsrfService
                .phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookie);

        if (principalOpt.isEmpty()) {
            return false; // Khách xem tin công khai, không gắn với tài khoản định danh
        }

        UUID taiKhoanId = principalOpt.get().getTaiKhoanId();
        LichSuXem lichSu = LichSuXem.taoMoi(taiKhoanId, request.tinId(), now);
        lichSuXemRepository.save(lichSu);

        return true;
    }
}
