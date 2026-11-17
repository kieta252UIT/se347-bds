package com.realestates.backend.auth.application;

import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Worker khôi phục trạng thái đồng bộ Auth - DB khi xảy ra sự cố giữa các bước đặt lại mật khẩu
 * theo đặc tả Sprint 1: thiet-ke-phien-sprint-1.md (§5.3 Worker khôi phục, §5.4 Quy trình vận hành),
 * ADR-0010, TS1-018.
 *
 * Nhiệm vụ chính:
 * 1. Khởi động và định kỳ mỗi 60s quét các tài khoản ở trạng thái 'dang_dat_lai' hoặc 'can_khoi_phuc'
 *    lâu hơn 30 giây (điều kiện kiểm tra, không tự mở gate).
 * 2. Gọi Admin getUserById đối chiếu app_metadata.se214_password_reset_operation với ma_thao_tac_auth:
 *    - Khớp: CAS hoàn tất app sang 'san_sang', xóa operation hiện hành; giữ nguyên epoch, khóa, vai trò.
 *    - Chưa trả lời / thiếu / khác: giữ 'can_khoi_phuc', ghi log cảnh báo kỹ thuật, không kết luận thất bại.
 * 3. Cung cấp lệnh CLI / quản trị nội bộ dongThaoTacThatBaiBoiQuanTri (§5.4) cho nhánh hiếm chưa rõ kết quả:
 *    chỉ đóng thất bại khi có operation ID dự kiến và bằng chứng đã kiểm tra; CAS mở gate về 'san_sang',
 *    giữ epoch đã tăng, cho phép người dùng xin link mới.
 */
@Service
public class AuthPasswordResetReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(AuthPasswordResetReconciliationService.class);

    public static final long PENDING_CHECK_THRESHOLD_SECONDS = 30L;

    private final TaiKhoanRepository taiKhoanRepository;
    private final SupabaseAuthPort supabaseAuthPort;

    @Autowired
    public AuthPasswordResetReconciliationService(
            TaiKhoanRepository taiKhoanRepository,
            SupabaseAuthPort supabaseAuthPort
    ) {
        this.taiKhoanRepository = Objects.requireNonNull(taiKhoanRepository, "taiKhoanRepository không được null");
        this.supabaseAuthPort = Objects.requireNonNull(supabaseAuthPort, "supabaseAuthPort không được null");
    }

    /**
     * Tự động chạy quét phục hồi khi ứng dụng khởi động hoàn tất (§5.3).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Khởi động worker khôi phục đồng bộ Auth - DB (Password Reset Reconciliation Worker)...");
        try {
            int count = reconcilePendingAccounts();
            log.info("Khôi phục hoàn tất lúc khởi động. Số tài khoản được đồng bộ: {}", count);
        } catch (Exception ex) {
            log.error("Lỗi khi chạy quét khôi phục lúc khởi động: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Định kỳ quét mỗi 60 giây (§5.3).
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void scheduledReconcile() {
        try {
            reconcilePendingAccounts();
        } catch (Exception ex) {
            log.error("Lỗi định kỳ khi quét khôi phục tài khoản dở: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Quét và xử lý các tài khoản có trạng thái dở ('dang_dat_lai', 'can_khoi_phuc') lâu hơn 30 giây.
     * Trả về số lượng tài khoản đã hoàn tất phục hồi thành công sang 'san_sang'.
     */
    public int reconcilePendingAccounts() {
        Instant olderThan = Instant.now().minusSeconds(PENDING_CHECK_THRESHOLD_SECONDS);
        List<TaiKhoan> pendingAccounts = taiKhoanRepository.findPendingAuthSyncAccounts(olderThan);

        if (pendingAccounts == null || pendingAccounts.isEmpty()) {
            return 0;
        }

        int resolvedCount = 0;
        for (TaiKhoan account : pendingAccounts) {
            UUID operationId = account.getMaThaoTacAuth();
            if (operationId == null) {
                continue;
            }

            try {
                boolean resolved = xuLyKhoiPhucChoTaiKhoan(account, operationId);
                if (resolved) {
                    resolvedCount++;
                }
            } catch (Exception ex) {
                log.warn("Không thể khôi phục tài khoản [{}] với thao tác [{}]: {}",
                        account.getId(), operationId, ex.getMessage());
            }
        }

        return resolvedCount;
    }

    private boolean xuLyKhoiPhucChoTaiKhoan(TaiKhoan account, UUID operationId) {
        UUID authUserId = account.getSupabaseAuthUserId();
        if (authUserId == null) {
            log.warn("Tài khoản [{}] không có authUserId hợp lệ; giữ nguyên gate.", account.getId());
            return false;
        }

        Optional<String> markerOpt;
        try {
            markerOpt = supabaseAuthPort.getPasswordResetMarker(authUserId);
        } catch (AuthProviderUnavailableException ex) {
            log.warn("Provider tạm thời không khả dụng khi kiểm tra tài khoản [{}] (thao tác [{}]). Giữ gate.",
                    account.getId(), operationId);
            // Nếu tài khoản đang ở 'dang_dat_lai', chuyển sang 'can_khoi_phuc' để ghi nhận chờ phục hồi
            if ("dang_dat_lai".equals(account.getTrangThaiDongBoAuth())) {
                taiKhoanRepository.updateDongBoAuthCas(
                        account.getId(),
                        operationId,
                        "can_khoi_phuc",
                        operationId,
                        Instant.now()
                );
            }
            return false;
        }

        String expectedMarker = operationId.toString();
        if (markerOpt.isPresent() && expectedMarker.equalsIgnoreCase(markerOpt.get().trim())) {
            // Bằng chứng provider đúng operation đang treo: CAS hoàn tất app sang 'san_sang'
            boolean casOk = taiKhoanRepository.updateDongBoAuthCas(
                    account.getId(),
                    operationId,
                    "san_sang",
                    null,
                    Instant.now()
            );
            if (casOk) {
                log.info("Phục hồi thành công tài khoản [{}] (Auth: {}): CAS hoàn tất sang 'san_sang'.",
                        account.getId(), authUserId);
                return true;
            } else {
                log.warn("CAS thất bại khi hoàn tất phục hồi tài khoản [{}], trạng thái có thể đã thay đổi.", account.getId());
                return false;
            }
        } else {
            // Marker chưa khớp hoặc provider chưa cập nhật: giữ 'can_khoi_phuc'
            log.warn("Tài khoản [{}] thao tác [{}] có provider marker không khớp (nhận được: {}). Tiếp tục giữ gate can_khoi_phuc.",
                    account.getId(), operationId, markerOpt.orElse("null"));
            if ("dang_dat_lai".equals(account.getTrangThaiDongBoAuth())) {
                taiKhoanRepository.updateDongBoAuthCas(
                        account.getId(),
                        operationId,
                        "can_khoi_phuc",
                        operationId,
                        Instant.now()
                );
            }
            return false;
        }
    }

    /**
     * Lệnh vận hành / CLI (§5.4): Đóng thao tác thất bại bởi quản trị viên khi có bằng chứng
     * handler Auth đã kết thúc và marker vẫn thiếu/khác.
     *
     * Chỉ đóng thất bại với operation ID dự kiến; mở gate về 'san_sang', xóa ma_thao_tac_auth,
     * giữ nguyên epoch đã tăng (không phục hồi các phiên cũ), cho phép người dùng xin link mới.
     *
     * @param taiKhoanId ID tài khoản
     * @param expectedOperationId Operation ID đang bị treo cần đóng
     * @param bangChungVanHanh Bằng chứng / lý do từ nhật ký kiểm toán vận hành
     * @return true nếu CAS đóng thao tác thành công
     */
    public boolean dongThaoTacThatBaiBoiQuanTri(
            UUID taiKhoanId,
            UUID expectedOperationId,
            String bangChungVanHanh
    ) {
        Objects.requireNonNull(taiKhoanId, "taiKhoanId không được null");
        Objects.requireNonNull(expectedOperationId, "expectedOperationId không được null");

        log.info("Quản trị viên thực hiện đóng thao tác dở: taiKhoanId={}, expectedOperationId={}, bangChung={}",
                taiKhoanId, expectedOperationId, bangChungVanHanh);

        boolean casOk = taiKhoanRepository.updateDongBoAuthCas(
                taiKhoanId,
                expectedOperationId,
                "san_sang",
                null,
                Instant.now()
        );

        if (casOk) {
            log.info("Đã đóng thao tác thất bại cho tài khoản [{}], gate đã mở về 'san_sang'.", taiKhoanId);
        } else {
            log.warn("Không thể đóng thao tác cho tài khoản [{}]: CAS không khớp expectedOperationId={}.",
                    taiKhoanId, expectedOperationId);
        }

        return casOk;
    }

    /**
     * Kiểm tra trạng thái thao tác của tài khoản để hỗ trợ chẩn đoán CLI (§5.4).
     */
    public Optional<ThongTinThaoTacAuth> kiemTraTrangThaiThaoTac(UUID taiKhoanId) {
        return taiKhoanRepository.findById(taiKhoanId).map(account -> {
            String marker = null;
            if (account.getSupabaseAuthUserId() != null) {
                try {
                    marker = supabaseAuthPort.getPasswordResetMarker(account.getSupabaseAuthUserId())
                            .orElse(null);
                } catch (Exception e) {
                    marker = "PROVIDER_ERROR: " + e.getMessage();
                }
            }
            return new ThongTinThaoTacAuth(
                    account.getId(),
                    account.getSupabaseAuthUserId(),
                    account.getTrangThaiDongBoAuth(),
                    account.getMaThaoTacAuth(),
                    marker
            );
        });
    }

    public record ThongTinThaoTacAuth(
            UUID taiKhoanId,
            UUID authUserId,
            String trangThaiDongBoAuth,
            UUID maThaoTacAuth,
            String providerMarker
    ) {}
}
