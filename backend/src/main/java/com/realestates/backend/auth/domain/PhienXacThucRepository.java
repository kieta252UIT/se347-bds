package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng lưu trữ thực thể Phiên xác thực (bảng se214.phien_xac_thuc)
 * theo đặc tả database-design-sprint-1.md §3.2 và thiet-ke-phien-sprint-1.md.
 */
public interface PhienXacThucRepository {

    Optional<PhienXacThuc> findById(UUID id);

    Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam);

    Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId);

    List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId);

    PhienXacThuc save(PhienXacThuc phienXacThuc);

    /**
     * Thực hiện nguyên tử trong cùng một giao dịch lưu trữ:
     * 1. Nếu có phiên hiện tại ứng với oldMaCookieBam trên trình duyệt này và chưa bị thu hồi,
     *    thu hồi riêng phiên đó với lý do "thay_phien_dang_nhap" (không ảnh hưởng thiết bị/browser khác).
     * 2. Lưu bản ghi phiên mới newPhien.
     *
     * Trả về phiên cũ đã bị thu hồi (nếu có) để tầng ứng dụng có thể dọn phiên tại provider best-effort.
     */
    Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(
            String oldMaCookieBam,
            PhienXacThuc newPhien,
            Instant now
    );

    /**
     * Thu hồi phiên ứng với maCookieBam theo lý do chỉ định (nếu tồn tại và chưa thu hồi).
     */
    Optional<PhienXacThuc> revokeByMaCookieBam(
            String maCookieBam,
            String lyDoThuHoi,
            Instant now,
            boolean giuLaiCredentialDeThuHoiProvider
    );
}
