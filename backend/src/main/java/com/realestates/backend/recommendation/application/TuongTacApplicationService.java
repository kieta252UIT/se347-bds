package com.realestates.backend.recommendation.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.recommendation.domain.LichSuXem;
import com.realestates.backend.recommendation.domain.LichSuXemRepository;
import com.realestates.backend.recommendation.dto.GhiNhanTuongTacRequest;
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
    private final Clock clock;

    @Autowired
    public TuongTacApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            LichSuXemRepository lichSuXemRepository
    ) {
        this(permissionAndCsrfService, lichSuXemRepository, Clock.systemUTC());
    }

    public TuongTacApplicationService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            LichSuXemRepository lichSuXemRepository,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.lichSuXemRepository = Objects.requireNonNull(lichSuXemRepository, "lichSuXemRepository không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    /**
     * Ghi nhận tương tác của người dùng theo quyền (FR-03, UC-03, Q28).
     * Nếu là người dùng đã đăng nhập, lưu vào lịch sử tương tác để cập nhật điểm gợi ý nền trong 5 phút.
     */
    public boolean ghiNhanTuongTac(String rawSessionCookie, GhiNhanTuongTacRequest request) {
        Objects.requireNonNull(request, "request không được null");

        Optional<PrincipalPhienHienTai> principalOpt = permissionAndCsrfService
                .phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookie);

        if (principalOpt.isEmpty()) {
            return false; // Khách xem tin công khai, không gắn với tài khoản định danh
        }

        UUID taiKhoanId = principalOpt.get().getTaiKhoanId();
        Instant now = clock.instant();

        LichSuXem lichSu = LichSuXem.taoMoi(taiKhoanId, request.tinId(), now);
        lichSuXemRepository.save(lichSu);

        return true;
    }
}
