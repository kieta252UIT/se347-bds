package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public interface PhienXacThucRepository {

    Optional<PhienXacThuc> findById(UUID id);

    default Optional<PhienXacThuc> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam);

    default Optional<PhienXacThuc> findByMaCookieBamForUpdate(String maCookieBam) {
        return findByMaCookieBam(maCookieBam);
    }

    default DataSource getDataSource() {
        return null;
    }

    Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId);

    List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId);

    PhienXacThuc save(PhienXacThuc phienXacThuc);

    Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(
            String oldMaCookieBam,
            PhienXacThuc newPhien,
            Instant now
    );

    Optional<PhienXacThuc> revokeByMaCookieBam(
            String maCookieBam,
            String lyDoThuHoi,
            Instant now,
            boolean giuLaiCredentialDeThuHoiProvider
    );

    /**
     * Thu hồi toàn bộ các phiên xác thực của một tài khoản theo lý do chỉ định (UC-29 / PHIEN-04 / §5.2).
     * Phục vụ luồng đặt lại mật khẩu và khóa tài khoản, đảm bảo mọi phiên cũ lập tức mất quyền.
     *
     * @param taiKhoanId ID tài khoản
     * @param lyDoThuHoi Lý do thu hồi (vd: 'dat_lai_mat_khau')
     * @param now Thời điểm thu hồi
     * @return Số lượng phiên đã được cập nhật thu hồi
     */
    default int revokeAllByTaiKhoanId(UUID taiKhoanId, String lyDoThuHoi, Instant now) {
        return 0;
    }

    default Optional<PhienXacThuc> tryAcquireRefreshLease(UUID phienId, UUID leaseId, Instant leaseUntil, Instant now) {
        return Optional.empty();
    }

    default boolean updateRefreshedTokensWithCas(
            UUID phienId,
            UUID expectedLeaseId,
            long expectedCasVersion,
            byte[] newAccessTokenMaHoa,
            byte[] newRefreshTokenMaHoa,
            Instant newAccessTokenHetHanLuc,
            String phienBanKhoaMaHoa,
            Instant now
    ) {
        return false;
    }

    default void releaseRefreshLease(UUID phienId, UUID leaseId) {
    }
}
