package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;


public interface TaiKhoanRepository {
    boolean existsByEmail(String normalizedEmail);

    default Optional<TaiKhoan> findById(UUID id) {
        return Optional.empty();
    }

    default Optional<TaiKhoan> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    /**
     * Đọc và khóa dòng nhiều tài khoản theo đúng thứ tự byte không dấu của UUID
     * (khớp tuyệt đối với {@code ORDER BY id ASC FOR UPDATE} trên PostgreSQL) để chống Deadlock
     * khi một giao dịch tác động hoặc tham chiếu khóa ngoại tới nhiều tài khoản (S2-02 Bảng 4).
     */
    default List<TaiKhoan> findAllByIdsForUpdateInOrder(Collection<UUID> ids) {
        List<UUID> sortedIds = MaTranQuyenSprint2.sapXepThuTuKhoaUuid(ids);
        if (sortedIds.isEmpty()) {
            return List.of();
        }
        List<TaiKhoan> results = new ArrayList<>(sortedIds.size());
        for (UUID id : sortedIds) {
            findByIdForUpdate(id).ifPresent(results::add);
        }
        return List.copyOf(results);
    }

    default DataSource getDataSource() {
        return null;
    }

    Optional<TaiKhoan> findByEmail(String normalizedEmail);

    Optional<TaiKhoan> findBySupabaseAuthUserId(UUID supabaseAuthUserId);

    TaiKhoan save(TaiKhoan taiKhoan);

    /**
     * Cập nhật trạng thái đồng bộ Auth và mã thao tác Auth theo cơ chế CAS nguyên tử
     * (thiet-ke-phien-sprint-1.md §5.2 bước 4, 5, 6 và §5.3).
     *
     * @param taiKhoanId ID tài khoản
     * @param expectedMaThaoTacAuth Mã thao tác mong đợi hiện tại (phục vụ CAS)
     * @param newTrangThaiDongBoAuth Trạng thái đồng bộ mới ('san_sang', 'dang_dat_lai', 'can_khoi_phuc')
     * @param newMaThaoTacAuth Mã thao tác mới (null nếu newTrangThaiDongBoAuth là 'san_sang')
     * @param now Thời điểm cập nhật
     * @return true nếu cập nhật CAS thành công, false nếu mã thao tác hiện tại không khớp
     */
    default boolean updateDongBoAuthCas(
            UUID taiKhoanId,
            UUID expectedMaThaoTacAuth,
            String newTrangThaiDongBoAuth,
            UUID newMaThaoTacAuth,
            Instant now
    ) {
        return false;
    }

    default List<TaiKhoan> findPendingAuthSyncAccounts(Instant olderThan) {
        return List.of();
    }
}
