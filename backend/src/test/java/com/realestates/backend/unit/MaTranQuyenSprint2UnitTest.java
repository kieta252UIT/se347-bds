package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.auth.domain.MaTranQuyenSprint1;
import com.realestates.backend.auth.domain.MaTranQuyenSprint2;
import com.realestates.backend.auth.domain.MaTranQuyenSprint2.AnhBangChungBaoCaoTruyCap;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử đơn vị cho Ma trận quyền, quyền đọc ảnh bằng chứng riêng tư, chốt chặn tự hạ quyền/tự khóa
 * và quy tắc sắp xếp khóa UUID của Sprint 2 (S2-02).
 */
class MaTranQuyenSprint2UnitTest {

    @Test
    @DisplayName("Bảng 1 (S2-02): Phân tách đúng vai trò RBAC và Ownership cho các thao tác Gợi ý, Báo cáo, Quản trị và Ví/Gói")
    void rbacAndOwnershipForSprint2Operations() {
        UUID userOneId = UUID.fromString("208d0f5f-99b8-42d3-8e89-b30d1e1f861e");
        UUID userTwoId = UUID.fromString("77a19012-41bc-4e92-8812-301928371201");
        PrincipalPhienHienTai userOne = principal(userOneId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai kdv = principal(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai admin = principal(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        // 1. Xem gợi ý công khai: Khách và cả 3 vai trò đều được phép
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(null, ThaoTacNghiepVu.XEM_GOI_Y_BAT_DONG_SAN, null)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, ThaoTacNghiepVu.XEM_GOI_Y_BAT_DONG_SAN, null)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, ThaoTacNghiepVu.XEM_GOI_Y_BAT_DONG_SAN, null)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, ThaoTacNghiepVu.XEM_GOI_Y_BAT_DONG_SAN, null)).isTrue();

        // 2. Các thao tác cá nhân của Người dùng (không yêu cầu chuId của tin)
        for (ThaoTacNghiepVu thaoTacNguoiDung : List.of(
                ThaoTacNghiepVu.GHI_NHAN_LUOT_XEM_TIN,
                ThaoTacNghiepVu.TAI_ANH_BANG_CHUNG_BAO_CAO,
                ThaoTacNghiepVu.XEM_THONG_BAO_CUA_TOI,
                ThaoTacNghiepVu.XEM_VI_VA_SO_GIAO_DICH
        )) {
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacNguoiDung, null)).isTrue();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(null, thaoTacNguoiDung, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, thaoTacNguoiDung, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, thaoTacNguoiDung, null)).isFalse();
        }

        // 3. Các thao tác yêu cầu Người dùng chính chủ tài nguyên (AC-04.3, UC-11)
        for (ThaoTacNghiepVu thaoTacChinhChu : List.of(
                ThaoTacNghiepVu.XEM_TIN_CUA_TOI_CHI_DOC,
                ThaoTacNghiepVu.XEM_BAO_GIA_GOI_DAY_TIN,
                ThaoTacNghiepVu.KICH_HOAT_GOI_DAY_TIN
        )) {
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacChinhChu, userOneId)).isTrue();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacChinhChu, userTwoId)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, thaoTacChinhChu, userOneId)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, thaoTacChinhChu, userOneId)).isFalse();
        }

        // 4. Đề xuất khóa tài khoản: chỉ Kiểm duyệt viên (Least Privilege: Admin không làm thay KDV)
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, ThaoTacNghiepVu.TAO_YEU_CAU_KHOA_TAI_KHOAN, null)).isTrue();
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, ThaoTacNghiepVu.TAO_YEU_CAU_KHOA_TAI_KHOAN, null)).isFalse();
        assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, ThaoTacNghiepVu.TAO_YEU_CAU_KHOA_TAI_KHOAN, null)).isFalse();

        // 5. Quản trị vai trò, quyết định phiếu khóa, mở khóa có căn cứ: chỉ Quản trị viên
        for (ThaoTacNghiepVu thaoTacQuanTri : List.of(
                ThaoTacNghiepVu.CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                ThaoTacNghiepVu.XEM_VA_QUYET_DINH_YEU_CAU_KHOA,
                ThaoTacNghiepVu.MO_KHOA_TAI_KHOAN_CO_CAN_CU
        )) {
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(admin, thaoTacQuanTri, null)).isTrue();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(kdv, thaoTacQuanTri, null)).isFalse();
            assertThat(MaTranQuyenSprint1.duocPhepThucHien(userOne, thaoTacQuanTri, null)).isFalse();
        }
    }

    @Test
    @DisplayName("QD-S2-01 (1A): Người dùng được báo cáo tin người khác nhưng bị chặn tự báo cáo tin của chính mình")
    void blockOwnerFromReportingOwnListing() {
        UUID chuTinId = UUID.randomUUID();
        UUID nguoiBaoCaoId = UUID.randomUUID();

        PrincipalPhienHienTai nguoiBaoCao = principal(nguoiBaoCaoId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai chuTin = principal(chuTinId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai nguoiBiKhoa = principal(UUID.randomUUID(), VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA, true);
        PrincipalPhienHienTai kdv = principal(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(nguoiBaoCao, chuTinId)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(chuTin, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(nguoiBiKhoa, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(kdv, chuTinId)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepGuiBaoCaoTin(null, chuTinId)).isFalse();
    }

    @Test
    @DisplayName("QD-S2-02 (2A): Ảnh bằng chứng báo cáo chỉ mở cho người gửi, KDV (khi đã gắn) và Admin (khi thuộc căn cứ phiếu khóa); chặn chủ tin bị báo cáo")
    void enforcePrivateReportEvidenceImageReadRules() {
        UUID reporterId = UUID.randomUUID();
        UUID listingOwnerId = UUID.randomUUID();
        UUID thirdUserId = UUID.randomUUID();

        PrincipalPhienHienTai reporter = principal(reporterId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai lockedReporter = principal(reporterId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA, true);
        PrincipalPhienHienTai listingOwner = principal(listingOwnerId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai thirdUser = principal(thirdUserId, VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai kdv = principal(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        PrincipalPhienHienTai admin = principal(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        // 1. Ảnh tạm ('tam'): chỉ chính người tải lên đọc được; KDV, Admin, chủ tin và Khách đều bị chặn
        AnhBangChungBaoCaoTruyCap anhTam = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_TAM,
                reporterId,
                null,
                false
        );
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, reporter)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, lockedReporter)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, listingOwner)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, kdv)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, admin)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhTam, null)).isFalse();

        // 2. Ảnh đã gắn vào báo cáo nhưng CHƯA thuộc căn cứ phiếu khóa:
        // Người gửi & KDV đọc được; chủ tin bị báo cáo, người dùng khác và Admin đều bị chặn
        AnhBangChungBaoCaoTruyCap anhDaGanChuaLenPhieuKhoa = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_DA_GAN,
                reporterId,
                listingOwnerId,
                false
        );
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanChuaLenPhieuKhoa, reporter)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanChuaLenPhieuKhoa, kdv)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanChuaLenPhieuKhoa, listingOwner)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanChuaLenPhieuKhoa, thirdUser)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhDaGanChuaLenPhieuKhoa, admin)).isFalse();

        // 3. Ảnh đã gắn vào báo cáo xác nhận VÀ nằm trong căn cứ phiếu đề xuất khóa (UC-17): Admin được đọc chỉ-đọc
        AnhBangChungBaoCaoTruyCap anhThuocPhieuKhoa = new AnhBangChungBaoCaoTruyCap(
                MaTranQuyenSprint2.ANH_DA_GAN,
                reporterId,
                listingOwnerId,
                true
        );
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhThuocPhieuKhoa, admin)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhThuocPhieuKhoa, listingOwner)).isFalse();

        // 4. Ảnh trạng thái 'cho_don' bị chặn với mọi vai trò
        AnhBangChungBaoCaoTruyCap anhChoDon = new AnhBangChungBaoCaoTruyCap("cho_don", reporterId, listingOwnerId, true);
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhChoDon, reporter)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhChoDon, kdv)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepDocAnhBangChungBaoCao(anhChoDon, admin)).isFalse();
    }

    @Test
    @DisplayName("S2-07 / S2-14: Quản trị viên không được tự hạ quyền hoặc tự khóa tài khoản của chính mình")
    void preventAdminSelfDemotionAndSelfLock() {
        UUID adminOneId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();
        PrincipalPhienHienTai adminOne = principal(adminOneId, VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);

        // Được cấp/đổi vai trò cho tài khoản khác
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatVaiTroQuanTri(adminOne, otherAccountId, VaiTroTaiKhoan.KIEM_DUYET_VIEN)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatVaiTroQuanTri(adminOne, otherAccountId, VaiTroTaiKhoan.NGUOI_DUNG)).isTrue();

        // Được sửa hồ sơ chính mình khi giữ nguyên vai trò QUAN_TRI_VIEN, nhưng cấm tự hạ xuống KDV / NGUOI_DUNG
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatVaiTroQuanTri(adminOne, adminOneId, VaiTroTaiKhoan.QUAN_TRI_VIEN)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatVaiTroQuanTri(adminOne, adminOneId, VaiTroTaiKhoan.KIEM_DUYET_VIEN)).isFalse();
        assertThat(MaTranQuyenSprint2.duocPhepCapNhatVaiTroQuanTri(adminOne, adminOneId, VaiTroTaiKhoan.NGUOI_DUNG)).isFalse();

        // Được khóa tài khoản khác nhưng cấm tự khóa chính mình
        assertThat(MaTranQuyenSprint2.duocPhepKhoaTaiKhoan(adminOne, otherAccountId)).isTrue();
        assertThat(MaTranQuyenSprint2.duocPhepKhoaTaiKhoan(adminOne, adminOneId)).isFalse();
    }

    @Test
    @DisplayName("Bảng 2.2: Chặn trường tự khai đặc quyền/số dư mới của Sprint 2 và chỉ mở vaiTro có kiểm soát trên DTO quản trị")
    void detectForbiddenSelfDeclaredFieldsInSprint2Payloads() {
        assertThat(MaTranQuyenSprint1.timTruongTuKhaiBiCam(Map.of("maGoi", "30_ngay", "soDuVnd", 9999999))).isEqualTo("soDuVnd");
        assertThat(MaTranQuyenSprint1.timTruongTuKhaiBiCam(Map.of("lyDo", "Lừa đảo", "nguoiGuiId", UUID.randomUUID()))).isEqualTo("nguoiGuiId");
        assertThat(MaTranQuyenSprint1.timTruongTuKhaiBiCam(Map.of("maGoi", "7_ngay", "dongYCongDon", true))).isNull();

        // Trên DTO quản trị tài khoản: cho phép vaiTro nhưng vẫn chặn mocThuHoi, soDuVnd, trangThai
        assertThat(MaTranQuyenSprint2.timTruongTuKhaiBiCamChoQuanTriTaiKhoan(
                Map.of("hoTen", "KDV Mới", "vaiTro", "kiem_duyet_vien")
        )).isNull();
        assertThat(MaTranQuyenSprint2.timTruongTuKhaiBiCamChoQuanTriTaiKhoan(
                Map.of("hoTen", "KDV Mới", "vaiTro", "kiem_duyet_vien", "soDuVnd", 500000)
        )).isEqualTo("soDuVnd");
        assertThat(MaTranQuyenSprint2.timTruongTuKhaiBiCamChoQuanTriTaiKhoan(
                Map.of("vaiTro", "nguoi_dung", "mocThuHoi", 0)
        )).isEqualTo("mocThuHoi");
    }

    @Test
    @DisplayName("Bảng 4: Sắp xếp thứ tự khóa UUID không dấu khớp tuyệt đối với ORDER BY id ASC của PostgreSQL và loại bỏ trùng lặp")
    void sortUuidLockOrderUnsignedMatchingPostgresOrder() {
        // Lưu ý: 'bfd23ad3-...' có bit dấu MSB = 1 (âm nếu dùng UUID.compareTo của Java),
        // nhưng trên PostgreSQL ORDER BY id ASC (unsigned byte order) thì '208d0f5f-...' < '89b44102-...' < 'bfd23ad3-...'.
        UUID uLow = UUID.fromString("208d0f5f-99b8-42d3-8e89-b30d1e1f861e");
        UUID uMid = UUID.fromString("89b44102-1190-4a10-9102-884120391044");
        UUID uHigh = UUID.fromString("bfd23ad3-a23e-4b36-b736-a947d8237389");

        List<UUID> sorted = MaTranQuyenSprint2.sapXepThuTuKhoaUuid(
                Arrays.asList(uHigh, null, uLow, uMid, uLow)
        );

        assertThat(sorted).containsExactly(uLow, uMid, uHigh);
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
