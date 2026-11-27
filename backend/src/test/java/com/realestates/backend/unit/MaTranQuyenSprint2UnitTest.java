package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.MaTranQuyenSprint1;
import com.realestates.backend.auth.domain.MaTranQuyenSprint2;
import com.realestates.backend.auth.domain.MaTranQuyenSprint2.AnhBangChungBaoCaoTruyCap;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kiểm thử đơn vị và đồng thời cho Ma trận quyền, quyền đọc ảnh bằng chứng riêng tư, điều kiện đề xuất khóa,
 * chốt chặn tự hạ quyền/tự khóa, allowlist DTO quản trị và bộ điều phối khóa đa tài khoản của Sprint 2 (S2-02).
 */
class MaTranQuyenSprint2UnitTest {

    @Test
    @DisplayName("Bảng 1 (S2-02): Tách biệt kiểm tra vai trò RBAC, Ownership (bao gồm thiếu chuId) và trạng thái khóa/chưa xác minh email")
    void rbacOwnershipAndAccountStateForSprint2Operations() {
        UUID userOneId = UUID.fromString("208d0f5f-99b8-42d3-8e89-b30d1e1f861e");
        UUID userTwoId = UUID.fromString("77a19012-41bc-4e92-8812-301928371201");
        PrincipalPhienHienTai userOne = principal(userOneId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai unverifiedUser = principal(userOneId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, false);
        PrincipalPhienHienTai lockedUser = principal(userOneId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA, true);
        PrincipalPhienHienTai kdv = principal(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai admin = principal(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        // 1. Tách riêng duocPhepTheoVaiTro (chỉ kiểm vai trò, chưa xét trạng thái/ownership)
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(null, ThaoTacNghiepVu.XEM_GOI_Y_BAT_DONG_SAN)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(null, ThaoTacNghiepVu.XEM_VI_VA_SO_GIAO_DICH)).isFalse();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.NGUOI_DUNG, ThaoTacNghiepVu.XEM_BAO_GIA_GOI_DAY_TIN)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.KIEM_DUYET_VIEN, ThaoTacNghiepVu.TAO_YEU_CAU_KHOA_TAI_KHOAN)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepTheoVaiTro(VaiTroTaiKhoan.QUAN_TRI_VIEN, ThaoTacNghiepVu.TAO_YEU_CAU_KHOA_TAI_KHOAN)).isFalse();

        // 2. Các thao tác của Người dùng: chặn Khách, KDV, Admin, tài khoản chưa xác minh email hoặc bị khóa
        for (ThaoTacNghiepVu thaoTacNguoiDung : List.of(
                ThaoTacNghiepVu.GHI_NHAN_LUOT_XEM_TIN,
                ThaoTacNghiepVu.TAI_ANH_BANG_CHUNG_BAO_CAO,
                ThaoTacNghiepVu.XEM_THONG_BAO_CUA_TOI,
                ThaoTacNghiepVu.XEM_VI_VA_SO_GIAO_DICH
        )) {
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacNguoiDung, null)).isTrue();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(unverifiedUser, thaoTacNguoiDung, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(lockedUser, thaoTacNguoiDung, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(null, thaoTacNguoiDung, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, thaoTacNguoiDung, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, thaoTacNguoiDung, null)).isFalse();
        }

        // 3. Các thao tác yêu cầu chính chủ tài nguyên: chặn cả khi khác chủ hoặc chuId == null
        for (ThaoTacNghiepVu thaoTacChinhChu : List.of(
                ThaoTacNghiepVu.XEM_TIN_CUA_TOI_CHI_DOC,
                ThaoTacNghiepVu.XEM_BAO_GIA_GOI_DAY_TIN,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN
        )) {
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacChinhChu, userOneId)).isTrue();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacChinhChu, userTwoId)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacChinhChu, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(unverifiedUser, thaoTacChinhChu, userOneId)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, thaoTacChinhChu, userOneId)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, thaoTacChinhChu, userOneId)).isFalse();
        }

        // 4. Quản trị tài khoản / phiếu khóa / mở khóa: chỉ Quản trị viên hoạt động & đã xác minh email
        PrincipalPhienHienTai unverifiedAdmin = principal(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, false);
        for (ThaoTacNghiepVu thaoTacQuanTri : List.of(
                ThaoTacNghiepVu.CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                ThaoTacNghiepVu.XEM_VA_QUYET_DINH_YEU_CAU_KHOA,
                ThaoTacNghiepVu.MO_KHOA_TAI_KHOAN_CO_CAN_CU
        )) {
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, thaoTacQuanTri, null)).isTrue();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(unverifiedAdmin, thaoTacQuanTri, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, thaoTacQuanTri, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacQuanTri, null)).isFalse();
        }
    }

    @Test
    @DisplayName("QD-S2-01 (1A): Chặn chủ tin tự báo cáo tin của mình, chặn chưa xác minh email và thiếu chuTinId")
    void blockOwnerAndInvalidPrincipalsFromReportingListing() {
        UUID chuTinId = UUID.randomUUID();
        UUID nguoiBaoCaoId = UUID.randomUUID();

        PrincipalPhienHienTai nguoiBaoCao = principal(nguoiBaoCaoId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai nguoiChuaXacMinh = principal(nguoiBaoCaoId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, false);
        PrincipalPhienHienTai chuTin = principal(chuTinId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai nguoiBiKhoa = principal(UUID.randomUUID(), VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA, true);
        PrincipalPhienHienTai kdv = principal(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(nguoiBaoCao, chuTinId)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(nguoiBaoCao, null)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(nguoiChuaXacMinh, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(chuTin, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(nguoiBiKhoa, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(kdv, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(null, chuTinId)).isFalse();
    }

    @Test
    @DisplayName("QD-S2-02 (2A) & P1-5: Chặn ảnh da_gan thiếu liên kết và chặn chủ tin bị báo cáo ở MỌI vai trò (kể cả khi được cấp KDV/Admin)")
    void enforcePrivateReportEvidenceImageReadRulesAcrossAllEdgeCases() {
        UUID reporterId = UUID.randomUUID();
        UUID listingOwnerId = UUID.randomUUID();
        UUID baoCaoId = UUID.randomUUID();

        PrincipalPhienHienTai reporter = principal(reporterId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai unverifiedReporter = principal(reporterId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, false);
        PrincipalPhienHienTai listingOwnerAsUser = principal(listingOwnerId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai listingOwnerPromotedToKdv = principal(listingOwnerId, VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai listingOwnerPromotedToAdmin = principal(listingOwnerId, VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai kdv = principal(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai admin = principal(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        // 1. Ảnh tạm hợp lệ vs ảnh tạm lỗi dữ liệu (đã có baoCaoId)
        AnhBangChungBaoCaoTruyCap anhTam = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_TAM, reporterId, null, null, false
        );
        AnhBangChungBaoCaoTruyCap anhTamLoiLienKet = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_TAM, reporterId, baoCaoId, null, false
        );
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, reporter)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, unverifiedReporter)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, kdv)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, admin)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTamLoiLienKet, reporter)).isFalse();

        // 2. Ảnh da_gan nhưng thiếu baoCaoId hoặc thiếu chuTinBiBaoCaoId -> từ chối cho mọi vai trò
        AnhBangChungBaoCaoTruyCap anhDaGanThieuBaoCao = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_DA_GAN, reporterId, null, listingOwnerId, true
        );
        AnhBangChungBaoCaoTruyCap anhDaGanThieuChuTin = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_DA_GAN, reporterId, baoCaoId, null, true
        );
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanThieuBaoCao, kdv)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanThieuChuTin, kdv)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanThieuChuTin, admin)).isFalse();

        // 3. Ảnh da_gan hợp lệ (thuộc căn cứ phiếu khóa vs chưa thuộc căn cứ phiếu khóa)
        AnhBangChungBaoCaoTruyCap anhChuaThuocPhieuKhoa = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_DA_GAN, reporterId, baoCaoId, listingOwnerId, false
        );
        AnhBangChungBaoCaoTruyCap anhThuocPhieuKhoa = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_DA_GAN, reporterId, baoCaoId, listingOwnerId, true
        );

        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhChuaThuocPhieuKhoa, reporter)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhChuaThuocPhieuKhoa, kdv)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhChuaThuocPhieuKhoa, admin)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhThuocPhieuKhoa, admin)).isTrue();

        // 4. Chủ tin bị báo cáo bị chặn tuyệt đối dù đang là NGUOI_DUNG, được cấp lên KDV hay được cấp lên QUAN_TRI_VIEN
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhThuocPhieuKhoa, listingOwnerAsUser)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhThuocPhieuKhoa, listingOwnerPromotedToKdv)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhThuocPhieuKhoa, listingOwnerPromotedToAdmin)).isFalse();
    }

    @Test
    @DisplayName("AC-08.4 / Q03 (P1-2): Hàm thống nhất xét đủ điều kiện đề xuất khóa và đề xuất lại sau mốc Admin từ chối")
    void evaluateLockProposalEligibilityConsistently() {
        Instant t1 = Instant.parse("2026-11-27T08:00:00Z");
        Instant tReject = Instant.parse("2026-11-27T09:00:00Z");
        Instant tAfterReject = Instant.parse("2026-11-27T09:00:01Z");

        // Chưa đủ 3 tin vi phạm -> false
        assertThat(MaTranQuyenSprint2.duDieuKienDeXuatKhoa(2, false, null, t1)).isFalse();

        // Đủ 3 tin vi phạm, chưa có phiếu cho_admin, chưa từng bị từ chối -> true
        assertThat(MaTranQuyenSprint2.duDieuKienDeXuatKhoa(3, false, null, t1)).isTrue();

        // Đang có phiếu cho_admin mở -> false
        assertThat(MaTranQuyenSprint2.duDieuKienDeXuatKhoa(4, true, null, t1)).isFalse();

        // Đã bị từ chối lúc tReject, không có sự kiện vi phạm mới sau tReject (kể cả bằng đúng tReject) -> false
        assertThat(MaTranQuyenSprint2.duDieuKienDeXuatKhoa(3, false, tReject, t1)).isFalse();
        assertThat(MaTranQuyenSprint2.duDieuKienDeXuatKhoa(3, false, tReject, tReject)).isFalse();

        // Có thêm ít nhất 1 tin vi phạm xác nhận mới sau tReject và không có phiếu chờ -> true
        assertThat(MaTranQuyenSprint2.duDieuKienDeXuatKhoa(4, false, tReject, tAfterReject)).isTrue();
    }

    @Test
    @DisplayName("S2-07 / S2-14 & P1-5: Chặn tự hạ quyền (kể cả PATCH chỉ sửa hồ sơ), tự khóa, khóa Admin khác và kiểm tra allowlist DTO quản trị")
    void enforceAdminInvariantsAndStrictAllowlists() {
        UUID adminOneId = UUID.randomUUID();
        UUID adminTwoId = UUID.randomUUID();
        UUID userTargetId = UUID.randomUUID();
        PrincipalPhienHienTai adminOne = principal(adminOneId, VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        // 1. PATCH chỉ sửa hồ sơ (vaiTroMoiTrongRequest = null): dùng vaiTroHienTaiTrongDb dưới khóa
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatTaiKhoanQuanTri(
                adminOne, adminOneId, VaiTroTaiKhoan.QUAN_TRI_VIEN, null
        )).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatTaiKhoanQuanTri(
                adminOne, adminOneId, VaiTroTaiKhoan.QUAN_TRI_VIEN, VaiTroTaiKhoan.KIEM_DUYET_VIEN
        )).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatTaiKhoanQuanTri(
                adminOne, userTargetId, VaiTroTaiKhoan.NGUOI_DUNG, null
        )).isTrue();

        // 2. Khóa tài khoản: cấm tự khóa và cấm khóa tài khoản vừa được cấp lên QUAN_TRI_VIEN sau lúc lập phiếu
        assertThat(MaTranQuyenSprint2.duocPhepKhoaTaiKhoan(adminOne, userTargetId, VaiTroTaiKhoan.NGUOI_DUNG)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepKhoaTaiKhoan(adminOne, userTargetId, VaiTroTaiKhoan.KIEM_DUYET_VIEN)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepKhoaTaiKhoan(adminOne, adminOneId, VaiTroTaiKhoan.QUAN_TRI_VIEN)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepKhoaTaiKhoan(adminOne, adminTwoId, VaiTroTaiKhoan.QUAN_TRI_VIEN)).isFalse();

        // 3. Allowlist chặt cho POST /api/admin/accounts và PATCH /api/admin/accounts/{id}
        assertThat(MaTranQuyenSprint2.timTruongKhongDuocPhepTaoTaiKhoanQuanTri(
                Map.of("email", "kdv@example.com", "hoTen", "KDV Mới", "vaiTro", "kiem_duyet_vien")
        )).isNull();
        assertThat(MaTranQuyenSprint2.timTruongKhongDuocPhepTaoTaiKhoanQuanTri(
                Map.of("email", "kdv@example.com", "hoTen", "KDV", "id", UUID.randomUUID().toString())
        )).isEqualTo("id");
        assertThat(MaTranQuyenSprint2.timTruongKhongDuocPhepTaoTaiKhoanQuanTri(
                Map.of("email", "kdv@example.com", "hoTen", "KDV", "supabaseAuthUserId", UUID.randomUUID().toString())
        )).isEqualTo("supabaseAuthUserId");
        assertThat(MaTranQuyenSprint2.timTruongKhongDuocPhepTaoTaiKhoanQuanTri(
                Map.of("email", "kdv@example.com", "hoTen", "KDV", "vai_tro", "quan_tri_vien")
        )).isEqualTo("vai_tro");

        assertThat(MaTranQuyenSprint2.timTruongKhongDuocPhepCapNhatTaiKhoanQuanTri(
                Map.of("hoTen", "Tên mới", "soDienThoai", "0909123456")
        )).isNull();
        assertThat(MaTranQuyenSprint2.timTruongKhongDuocPhepCapNhatTaiKhoanQuanTri(
                Map.of("hoTen", "Tên mới", "email", "doi.email@example.com")
        )).isEqualTo("email");
        assertThatThrownBy(() -> CurrentPermissionAndCsrfService.kiemTraTruongChoPhepCapNhatTaiKhoanQuanTri(
                Map.of("hoTen", "Tên mới", "soDuVnd", 1000000)
        )).isInstanceOf(AuthValidationException.class);
    }

    @Test
    @DisplayName("Bảng 4: Sắp xếp thứ tự khóa UUID không dấu khớp tuyệt đối với ORDER BY id ASC của PostgreSQL")
    void sortUuidLockOrderUnsignedMatchingPostgresOrder() {
        UUID uLow = UUID.fromString("208d0f5f-99b8-42d3-8e89-b30d1e1f861e");
        UUID uMid = UUID.fromString("89b44102-1190-4a10-9102-884120391044");
        UUID uHigh = UUID.fromString("bfd23ad3-a23e-4b36-b736-a947d8237389");

        List<UUID> sorted = MaTranQuyenSprint2.sapXepThuTuKhoaUuid(
                Arrays.asList(uHigh, null, uLow, uMid, uLow)
        );

        assertThat(sorted).containsExactly(uLow, uMid, uHigh);
    }

    @Test
    @DisplayName("P0-1: Hai Quản trị viên cập nhật chéo nhau đồng thời qua thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction không bị đảo khóa / Deadlock")
    void concurrentCrossAccountTransactionsAcquireLocksInDeterministicOrderWithoutDeadlock() throws Exception {
        InMemoryTaiKhoanRepository taiKhoanRepo = new InMemoryTaiKhoanRepository();
        InMemoryPhienXacThucRepository phienRepo = new InMemoryPhienXacThucRepository();
        DefaultAuthBrowserSessionLifecycleAdapter sessionAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM",
                phienRepo
        );
        CurrentPermissionAndCsrfService service = new CurrentPermissionAndCsrfService(
                taiKhoanRepo,
                phienRepo,
                sessionAdapter,
                List.of("https://localhost")
        );

        // Cố tình chọn 1 UUID có MSB dương ('208d...') và 1 UUID có MSB âm ('bfd2...') để kiểm chứng thứ tự khóa không dấu
        UUID adminLowId = UUID.fromString("208d0f5f-99b8-42d3-8e89-b30d1e1f861e");
        UUID adminHighId = UUID.fromString("bfd23ad3-a23e-4b36-b736-a947d8237389");

        String cookieLow = seedAdminAndSession(taiKhoanRepo, phienRepo, sessionAdapter, adminLowId, "admin.low@example.com");
        String cookieHigh = seedAdminAndSession(taiKhoanRepo, phienRepo, sessionAdapter, adminHighId, "admin.high@example.com");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                CountDownLatch startGate = new CountDownLatch(1);

                Future<List<UUID>> futureA = executor.submit(() -> {
                    startGate.await(5, TimeUnit.SECONDS);
                    return service.thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
                            cookieLow,
                            ThaoTacNghiepVu.CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                            List.of(adminHighId),
                            null,
                            null,
                            CurrentPermissionAndCsrfService::kiemTraTruongChoPhepCapNhatTaiKhoanQuanTri,
                            Map.of("hoTen", "Cập nhật từ Low"),
                            (principal, lockedAccounts) -> new ArrayList<>(lockedAccounts.keySet())
                    );
                });

                Future<List<UUID>> futureB = executor.submit(() -> {
                    startGate.await(5, TimeUnit.SECONDS);
                    return service.thucThiGhiNhieuTaiKhoanCoKiemLaiQuyenTrongTransaction(
                            cookieHigh,
                            ThaoTacNghiepVu.CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                            List.of(adminLowId),
                            null,
                            null,
                            CurrentPermissionAndCsrfService::kiemTraTruongChoPhepCapNhatTaiKhoanQuanTri,
                            Map.of("hoTen", "Cập nhật từ High"),
                            (principal, lockedAccounts) -> new ArrayList<>(lockedAccounts.keySet())
                    );
                });

                startGate.countDown();

                List<UUID> orderSeenByA = futureA.get(5, TimeUnit.SECONDS);
                List<UUID> orderSeenByB = futureB.get(5, TimeUnit.SECONDS);

                assertThat(orderSeenByA).containsExactly(adminLowId, adminHighId);
                assertThat(orderSeenByB).containsExactly(adminLowId, adminHighId);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("P1-1: khoaTaiKhoanNguyenTu bao trọn trong một transaction và rollback/khôi phục phiên khi bước lưu tài khoản cuối gặp lỗi")
    void khoaTaiKhoanNguyenTuRollsBackWhenFinalAccountSaveFails() {
        UUID userId = UUID.randomUUID();
        InMemoryTaiKhoanRepository baseTaiKhoanRepo = new InMemoryTaiKhoanRepository();
        InMemoryPhienXacThucRepository phienRepo = new InMemoryPhienXacThucRepository();
        DefaultAuthBrowserSessionLifecycleAdapter sessionAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM",
                phienRepo
        );

        Instant now = Instant.now();
        TaiKhoan activeUser = TaiKhoan.khoiPhucTuLuuTru(
                userId,
                UUID.randomUUID(),
                "rollback.lock@example.com",
                "User Rollback",
                "0901234567",
                VaiTroTaiKhoan.NGUOI_DUNG,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                now,
                now,
                now
        );
        baseTaiKhoanRepo.save(activeUser);

        String rawCookie = sessionAdapter.generateRawSessionCookie();
        UUID phienId = UUID.randomUUID();
        PhienXacThuc activeSession = PhienXacThuc.taoPhienMoi(
                phienId,
                userId,
                sessionAdapter.hashSessionCookie(rawCookie),
                0L,
                now,
                UUID.randomUUID(),
                "access".getBytes(StandardCharsets.UTF_8),
                "refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienRepo.save(activeSession);

        // Repository giả lập lỗi DB đúng ở bước lưu TaiKhoan sau khi các phiên đã bị gọi thu hồi
        com.realestates.backend.auth.domain.TaiKhoanRepository failingSaveRepo = new com.realestates.backend.auth.domain.TaiKhoanRepository() {
            @Override
            public boolean existsByEmail(String normalizedEmail) {
                return baseTaiKhoanRepo.existsByEmail(normalizedEmail);
            }

            @Override
            public Optional<TaiKhoan> findById(UUID id) {
                return baseTaiKhoanRepo.findById(id);
            }

            @Override
            public Optional<TaiKhoan> findByIdForUpdate(UUID id) {
                return baseTaiKhoanRepo.findByIdForUpdate(id);
            }

            @Override
            public Optional<TaiKhoan> findByEmail(String normalizedEmail) {
                return baseTaiKhoanRepo.findByEmail(normalizedEmail);
            }

            @Override
            public Optional<TaiKhoan> findBySupabaseAuthUserId(UUID supabaseAuthUserId) {
                return baseTaiKhoanRepo.findBySupabaseAuthUserId(supabaseAuthUserId);
            }

            @Override
            public TaiKhoan save(TaiKhoan taiKhoan) {
                throw new RuntimeException("Simulated DB failure on final account save");
            }
        };

        // 1. Kiểm chứng TransactionManager thực sự mở transaction và nhận lệnh rollback khi gọi độc lập
        AtomicBoolean txBegun = new AtomicBoolean(false);
        AtomicBoolean txRolledBack = new AtomicBoolean(false);
        AbstractPlatformTransactionManager probeTxManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                txBegun.set(true);
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                txRolledBack.set(true);
            }
        };

        CurrentPermissionAndCsrfService txService = new CurrentPermissionAndCsrfService(
                failingSaveRepo,
                phienRepo,
                sessionAdapter,
                List.of("https://localhost"),
                null,
                new TransactionTemplate(probeTxManager)
        );

        assertThatThrownBy(() -> txService.khoaTaiKhoanNguyenTu(userId, now.plusSeconds(10)))
                .isInstanceOf(AuthProviderUnavailableException.class);
        assertThat(txBegun.get()).isTrue();
        assertThat(txRolledBack.get()).isTrue();

        // 2. Khôi phục phiên cho kịch bản in-memory (không có TransactionOperations): phiên không bị kẹt ở trạng thái thu hồi dở dang
        phienRepo.save(activeSession);
        CurrentPermissionAndCsrfService inMemoryService = new CurrentPermissionAndCsrfService(
                failingSaveRepo,
                phienRepo,
                sessionAdapter,
                List.of("https://localhost")
        );

        assertThatThrownBy(() -> inMemoryService.khoaTaiKhoanNguyenTu(userId, now.plusSeconds(20)))
                .isInstanceOf(AuthProviderUnavailableException.class);
        assertThat(phienRepo.findById(phienId)).isPresent();
        assertThat(phienRepo.findById(phienId).get().isDaThuHoi()).isFalse();
        assertThat(baseTaiKhoanRepo.findById(userId).get().getTrangThai()).isEqualTo(TrangThaiTaiKhoan.HOAT_DONG);

        // 3. Kịch bản P2: Có 2 phiên hoạt động, lần lưu phiên thứ 1 thành công nhưng lần lưu phiên thứ 2 ném lỗi -> phiên 1 được khôi phục nguyên trạng
        UUID secondPhienId = UUID.randomUUID();
        String secondRawCookie = sessionAdapter.generateRawSessionCookie();
        PhienXacThuc secondActiveSession = PhienXacThuc.taoPhienMoi(
                secondPhienId,
                userId,
                sessionAdapter.hashSessionCookie(secondRawCookie),
                0L,
                now,
                UUID.randomUUID(),
                "access-2".getBytes(StandardCharsets.UTF_8),
                "refresh-2".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienRepo.save(secondActiveSession);

        java.util.concurrent.atomic.AtomicInteger revokeSaveCalls = new java.util.concurrent.atomic.AtomicInteger(0);
        com.realestates.backend.auth.domain.PhienXacThucRepository failOnSecondSessionSaveRepo =
                new com.realestates.backend.auth.domain.PhienXacThucRepository() {
                    @Override
                    public Optional<PhienXacThuc> findById(UUID id) {
                        return phienRepo.findById(id);
                    }

                    @Override
                    public Optional<PhienXacThuc> findByMaCookieBam(String maCookieBam) {
                        return phienRepo.findByMaCookieBam(maCookieBam);
                    }

                    @Override
                    public Optional<PhienXacThuc> findBySupabaseSessionId(UUID supabaseSessionId) {
                        return phienRepo.findBySupabaseSessionId(supabaseSessionId);
                    }

                    @Override
                    public List<PhienXacThuc> findByTaiKhoanId(UUID taiKhoanId) {
                        return phienRepo.findByTaiKhoanId(taiKhoanId);
                    }

                    @Override
                    public PhienXacThuc save(PhienXacThuc phien) {
                        if (phien.isDaThuHoi() && revokeSaveCalls.incrementAndGet() == 2) {
                            throw new RuntimeException("Simulated failure on 2nd session revoke save");
                        }
                        return phienRepo.save(phien);
                    }

                    @Override
                    public Optional<PhienXacThuc> replaceBrowserSessionAndSaveNew(
                            String oldMaCookieBam,
                            PhienXacThuc newPhien,
                            Instant t
                    ) {
                        return phienRepo.replaceBrowserSessionAndSaveNew(oldMaCookieBam, newPhien, t);
                    }

                    @Override
                    public Optional<PhienXacThuc> revokeByMaCookieBam(
                            String maCookieBam,
                            String lyDoThuHoi,
                            Instant t,
                            boolean giuLaiCredential
                    ) {
                        return phienRepo.revokeByMaCookieBam(maCookieBam, lyDoThuHoi, t, giuLaiCredential);
                    }
                };

        CurrentPermissionAndCsrfService secondSessionFailService = new CurrentPermissionAndCsrfService(
                baseTaiKhoanRepo,
                failOnSecondSessionSaveRepo,
                sessionAdapter,
                List.of("https://localhost")
        );

        assertThatThrownBy(() -> secondSessionFailService.khoaTaiKhoanNguyenTu(userId, now.plusSeconds(30)))
                .isInstanceOf(AuthProviderUnavailableException.class);
        assertThat(phienRepo.findById(phienId).orElseThrow().isDaThuHoi()).isFalse();
        assertThat(phienRepo.findById(secondPhienId).orElseThrow().isDaThuHoi()).isFalse();
        assertThat(baseTaiKhoanRepo.findById(userId).orElseThrow().getTrangThai()).isEqualTo(TrangThaiTaiKhoan.HOAT_DONG);
    }

    @Test
    @DisplayName("P1-2: Báo giá và Mua gói đẩy tin trả 403 cho sai vai trò, trả 404 cho tin không tồn tại hoặc khác chủ, nhưng giữ nguyên 403 cho sửa tin Sprint 1")
    void boostQuoteAndPurchaseHideForeignOrMissingListingWith404WhilePreserving403ForWrongRoleAndSprint1() {
        InMemoryTaiKhoanRepository taiKhoanRepo = new InMemoryTaiKhoanRepository();
        InMemoryPhienXacThucRepository phienRepo = new InMemoryPhienXacThucRepository();
        DefaultAuthBrowserSessionLifecycleAdapter sessionAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(
                "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM",
                phienRepo
        );
        CurrentPermissionAndCsrfService service = new CurrentPermissionAndCsrfService(
                taiKhoanRepo,
                phienRepo,
                sessionAdapter,
                List.of("https://localhost")
        );

        UUID ownerOneId = UUID.randomUUID();
        UUID ownerTwoId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID listingOfTwo = UUID.randomUUID();

        String cookieUserOne = seedAccountWithRole(taiKhoanRepo, phienRepo, sessionAdapter, ownerOneId, "u1@example.com", VaiTroTaiKhoan.NGUOI_DUNG);
        String cookieAdmin = seedAccountWithRole(taiKhoanRepo, phienRepo, sessionAdapter, adminId, "ad@example.com", VaiTroTaiKhoan.QUAN_TRI_VIEN);

        AtomicBoolean resourceLookupCalledOnWrongRole = new AtomicBoolean(false);

        // 1. Sai vai trò (Admin gọi mua gói): bị chặn 403 KHONG_DU_QUYEN TRƯỚC KHI chạm vào hàm đọc tài nguyên
        assertThatThrownBy(() -> service.thucThiGhiCoCheTonTaiTaiNguyenTrongTransaction(
                cookieAdmin,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                () -> {
                    resourceLookupCalledOnWrongRole.set(true);
                    return ownerTwoId;
                },
                ListingNotFoundException::new,
                listingOfTwo,
                Map.of("maGoi", "7_ngay"),
                p -> "NEVER"
        )).isInstanceOf(InsufficientPermissionException.class);
        assertThat(resourceLookupCalledOnWrongRole.get()).isFalse();

        // 2. Đúng vai trò Người dùng nhưng tin không tồn tại (null) -> 404 ListingNotFoundException (cả báo giá & mua gói)
        assertThatThrownBy(() -> service.kiemTraQuyenThaoTacCheTonTaiTaiNguyen(
                cookieUserOne,
                ThaoTacNghiepVu.XEM_BAO_GIA_GOI_DAY_TIN,
                () -> null,
                ListingNotFoundException::new,
                null
        )).isInstanceOf(ListingNotFoundException.class);

        assertThatThrownBy(() -> service.thucThiGhiCoCheTonTaiTaiNguyenTrongTransaction(
                cookieUserOne,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                () -> null,
                ListingNotFoundException::new,
                UUID.randomUUID(),
                Map.of("maGoi", "7_ngay"),
                p -> "NEVER"
        )).isInstanceOf(ListingNotFoundException.class);

        // 3. Đúng vai trò Người dùng nhưng tin thuộc người khác (ownerTwoId) -> 404 ListingNotFoundException (che giấu sự tồn tại)
        assertThatThrownBy(() -> service.kiemTraQuyenThaoTacCheTonTaiTaiNguyen(
                cookieUserOne,
                ThaoTacNghiepVu.XEM_BAO_GIA_GOI_DAY_TIN,
                () -> ownerTwoId,
                ListingNotFoundException::new,
                null
        )).isInstanceOf(ListingNotFoundException.class);

        assertThatThrownBy(() -> service.thucThiGhiCoCheTonTaiTaiNguyenTrongTransaction(
                cookieUserOne,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                () -> ownerTwoId,
                ListingNotFoundException::new,
                listingOfTwo,
                Map.of("maGoi", "30_ngay"),
                p -> "NEVER"
        )).isInstanceOf(ListingNotFoundException.class);

        // 4. Chính chủ (ownerOneId) -> thành công 100%
        String purchaseResult = service.thucThiGhiCoCheTonTaiTaiNguyenTrongTransaction(
                cookieUserOne,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN,
                () -> ownerOneId,
                ListingNotFoundException::new,
                UUID.randomUUID(),
                Map.of("maGoi", "30_ngay"),
                p -> "PURCHASED_BY_" + p.getTaiKhoanId()
        );
        assertThat(purchaseResult).isEqualTo("PURCHASED_BY_" + ownerOneId);

        // 5. Giữ nguyên hành vi Sprint 1 (TS1-013): sửa tin của người khác qua thucThiGhiCoKiemLaiQuyenTrongTransaction vẫn trả 403
        assertThatThrownBy(() -> service.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                cookieUserOne,
                ThaoTacNghiepVu.CHINH_SUA_TIN_CUA_MINH,
                () -> ownerTwoId,
                listingOfTwo,
                Map.of("tieuDe", "Sửa tin người khác"),
                p -> "NEVER"
        )).isInstanceOf(InsufficientPermissionException.class);
    }

    private static String seedAdminAndSession(
            InMemoryTaiKhoanRepository taiKhoanRepo,
            InMemoryPhienXacThucRepository phienRepo,
            DefaultAuthBrowserSessionLifecycleAdapter sessionAdapter,
            UUID accountId,
            String email
    ) {
        return seedAccountWithRole(taiKhoanRepo, phienRepo, sessionAdapter, accountId, email, VaiTroTaiKhoan.QUAN_TRI_VIEN);
    }

    private static String seedAccountWithRole(
            InMemoryTaiKhoanRepository taiKhoanRepo,
            InMemoryPhienXacThucRepository phienRepo,
            DefaultAuthBrowserSessionLifecycleAdapter sessionAdapter,
            UUID accountId,
            String email,
            VaiTroTaiKhoan vaiTro
    ) {
        Instant now = Instant.now();
        TaiKhoan tk = TaiKhoan.khoiPhucTuLuuTru(
                accountId,
                UUID.randomUUID(),
                email,
                "Account " + email,
                "0901234567",
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                now,
                now,
                now
        );
        taiKhoanRepo.save(tk);

        String rawCookie = sessionAdapter.generateRawSessionCookie();
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                accountId,
                sessionAdapter.hashSessionCookie(rawCookie),
                0L,
                now,
                UUID.randomUUID(),
                "access".getBytes(StandardCharsets.UTF_8),
                "refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienRepo.save(phien);
        return rawCookie;
    }

    private static PrincipalPhienHienTai principal(
            UUID taiKhoanId,
            VaiTroTaiKhoan vaiTro,
            TrangThaiTaiKhoan trangThai,
            boolean emailDaXacMinh
    ) {
        return new PrincipalPhienHienTai(
                UUID.randomUUID(),
                taiKhoanId,
                "cookie-hash-" + taiKhoanId,
                "user-" + taiKhoanId + "@example.com",
                "Người dùng thử",
                "0901234567",
                vaiTro,
                trangThai,
                emailDaXacMinh,
                0L
        );
    }
}
