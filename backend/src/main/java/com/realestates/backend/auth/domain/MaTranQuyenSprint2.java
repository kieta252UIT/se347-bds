package com.realestates.backend.auth.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bộ quy tắc miền mở rộng cho Sprint 2 (S2-02) theo đặc tả
 * docs/ma-tran-quyen-replay-va-thu-tu-khoa-sprint-2.md:
 * - QD-S2-01 (1A): Chặn chủ tin tự báo cáo vi phạm tin của chính mình.
 * - QD-S2-02 (2A): Quyền đọc ảnh bằng chứng báo cáo riêng tư (người gửi, KDV, và QTV khi thuộc căn cứ phiếu khóa;
 *   chặn tuyệt đối Khách, người dùng khác và chủ tin bị báo cáo ở mọi vai trò).
 * - AC-08.4 / Q03: Quy tắc thống nhất xét đủ điều kiện đề xuất khóa / đề xuất lại sau khi bị Admin từ chối.
 * - S2-07 / S2-14: Chặn Quản trị viên tự hạ quyền hoặc tự khóa chính mình; kiểm tra danh sách trắng (allowlist)
 *   các trường được phép trên DTO tạo/cập nhật tài khoản quản trị.
 * - Bảng 4: Sắp xếp thứ tự khóa UUID không dấu khớp 100% với PostgreSQL ORDER BY id ASC để chống Deadlock.
 */
public final class MaTranQuyenSprint2 {

    public static final String ANH_TAM = "tam";
    public static final String ANH_DA_GAN = "da_gan";
    public static final int NGUONG_TIN_VI_PHAM_TOI_THIEU_DE_XUAT_KHOA = 3;

    /**
     * Danh sách trường duy nhất được phép xuất hiện trong body JSON của POST /api/admin/accounts (UC-15/16).
     * Từ chối mọi trường lạ hoặc trường hệ thống như id, supabaseAuthUserId, vai_tro, trangThai, mocThuHoi, soDuVnd.
     */
    public static final Set<String> CAC_TRUONG_CHO_PHEP_TAO_TAI_KHOAN_QUAN_TRI = Set.of(
            "email",
            "hoTen",
            "soDienThoai",
            "vaiTro",
            "matKhau"
    );

    /**
     * Danh sách trường duy nhất được phép xuất hiện trong body JSON của PATCH /api/admin/accounts/{accountId} (UC-16).
     * Từ chối mọi trường lạ hoặc trường hệ thống như id, email, supabaseAuthUserId, vai_tro, trangThai, mocThuHoi, soDuVnd.
     */
    public static final Set<String> CAC_TRUONG_CHO_PHEP_CAP_NHAT_TAI_KHOAN_QUAN_TRI = Set.of(
            "hoTen",
            "soDienThoai",
            "vaiTro"
    );

    /**
     * Bộ so sánh UUID theo 128-bit không dấu (unsigned byte order), khớp tuyệt đối với
     * thứ tự {@code ORDER BY id ASC} trên kiểu {@code uuid} của PostgreSQL (khác với {@link UUID#compareTo(UUID)}
     * vốn dùng so sánh 64-bit có dấu).
     */
    public static final Comparator<UUID> THU_TU_KHOA_UUID_POSTGRES = (u1, u2) -> {
        int cmpHigh = Long.compareUnsigned(u1.getMostSignificantBits(), u2.getMostSignificantBits());
        if (cmpHigh != 0) {
            return cmpHigh;
        }
        return Long.compareUnsigned(u1.getLeastSignificantBits(), u2.getLeastSignificantBits());
    };

    /**
     * Thông tin truy cập của một tệp ảnh bằng chứng báo cáo vi phạm đọc từ CSDL (QD-S2-02).
     *
     * @param trangThaiAnh        trạng thái ảnh ('tam', 'da_gan', 'cho_don')
     * @param nguoiTaiAnhId       tai_khoan_id của người tải ảnh / gửi báo cáo
     * @param baoCaoId            id của báo cáo vi phạm mà ảnh đã gắn vào (bắt buộc khác null khi trangThaiAnh = 'da_gan')
     * @param chuTinBiBaoCaoId    chu_id của tin bất động sản bị báo cáo (bắt buộc khác null khi trangThaiAnh = 'da_gan')
     * @param thuocCanCuPhieuKhoa cờ đọc từ CSDL: true khi báo cáo chứa ảnh đã 'da_xac_nhan' và nằm trong căn cứ một phiếu khóa
     */
    public record AnhBangChungBaoCaoTruyCap(
            String trangThaiAnh,
            UUID nguoiTaiAnhId,
            UUID baoCaoId,
            UUID chuTinBiBaoCaoId,
            boolean thuocCanCuPhieuKhoa
    ) {
    }

    private MaTranQuyenSprint2() {
    }

    /**
     * QD-S2-01 (1A): Kiểm tra người dùng có được phép gửi báo cáo vi phạm trên một tin đăng hay không.
     * Yêu cầu phiên là NGUOI_DUNG đang hoạt động, đã xác minh email, và KHÔNG phải chủ sở hữu của tin đăng đó.
     */
    public static boolean duocPhepGuiBaoCaoTin(PrincipalPhienHienTai principal, UUID chuTinId) {
        if (chuTinId == null) {
            return false;
        }
        if (!MaTranQuyenSprint1.duocPhepThucHien(principal, ThaoTacNghiepVu.GUI_BAO_CAO_VI_PHAM, null)) {
            return false;
        }
        return !principal.getTaiKhoanId().equals(chuTinId);
    }

    /**
     * QD-S2-02 (2A): Kiểm tra quyền đọc tệp ảnh bằng chứng báo cáo riêng tư.
     * - Khách, tài khoản bị khóa/chưa xác minh email, ảnh trạng thái khác 'tam'/'da_gan': luôn bị từ chối (false).
     * - Ảnh 'tam': chỉ chính NGUOI_DUNG đã tải ảnh (và chưa gắn vào báo cáo) được đọc.
     * - Ảnh 'da_gan':
     *   + Bắt buộc có đủ liên kết baoCaoId và chuTinBiBaoCaoId từ CSDL.
     *   + Chủ tin bị báo cáo luôn bị từ chối ở MỌI vai trò (kể cả khi tài khoản đó được cấp KDV hoặc Admin về sau).
     *   + NGUOI_DUNG: chỉ người gửi báo cáo đó được đọc.
     *   + KIEM_DUYET_VIEN: được phép đọc để thẩm định báo cáo.
     *   + QUAN_TRI_VIEN: chỉ được đọc khi CSDL xác nhận ảnh thuộc báo cáo đã xác nhận nằm trong căn cứ phiếu khóa (UC-17).
     */
    public static boolean duocPhepDocAnhBangChungBaoCao(
            AnhBangChungBaoCaoTruyCap anh,
            PrincipalPhienHienTai nguoiDoc
    ) {
        if (anh == null || nguoiDoc == null || anh.nguoiTaiAnhId() == null) {
            return false;
        }
        if (nguoiDoc.getTrangThai() != TrangThaiTaiKhoan.HOAT_DONG || !nguoiDoc.isEmailDaXacMinh()) {
            return false;
        }

        boolean laAnhTam = ANH_TAM.equals(anh.trangThaiAnh());
        boolean laAnhDaGan = ANH_DA_GAN.equals(anh.trangThaiAnh());
        if (!laAnhTam && !laAnhDaGan) {
            return false;
        }

        if (laAnhTam) {
            if (anh.baoCaoId() != null) {
                return false;
            }
            return nguoiDoc.getVaiTro() == VaiTroTaiKhoan.NGUOI_DUNG
                    && nguoiDoc.getTaiKhoanId().equals(anh.nguoiTaiAnhId());
        }

        if (anh.baoCaoId() == null || anh.chuTinBiBaoCaoId() == null) {
            return false;
        }
        if (nguoiDoc.getTaiKhoanId().equals(anh.chuTinBiBaoCaoId())) {
            return false;
        }

        return switch (nguoiDoc.getVaiTro()) {
            case NGUOI_DUNG -> nguoiDoc.getTaiKhoanId().equals(anh.nguoiTaiAnhId());
            case KIEM_DUYET_VIEN -> true;
            case QUAN_TRI_VIEN -> anh.thuocCanCuPhieuKhoa();
        };
    }

    /**
     * AC-08.4 / Q03: Quy tắc thống nhất kiểm tra một tài khoản chủ tin có đủ điều kiện để KDV gửi phiếu đề xuất khóa
     * (dùng chung cho cả tính cờ {@code coTheDeXuatKhoa} trên giao diện KDV lẫn kiểm chứng tại POST /api/moderation/lock-requests).
     *
     * @param soTinViPhamDaXacNhan số lượng tin vi phạm duy nhất (COUNT(su_kien_vi_pham)) của chủ tin
     * @param coPhieuChoAdmin      true nếu chủ tin đang có một phiếu ở trạng thái 'cho_admin'
     * @param mocTuChoiGanNhat     thời điểm Admin từ chối phiếu khóa gần nhất (null nếu chưa từng bị từ chối)
     * @param mocViPhamMoiNhat     thời điểm phát sinh sự kiện vi phạm mới nhất của chủ tin (null nếu chưa có vi phạm)
     */
    public static boolean duDieuKienDeXuatKhoa(
            int soTinViPhamDaXacNhan,
            boolean coPhieuChoAdmin,
            Instant mocTuChoiGanNhat,
            Instant mocViPhamMoiNhat
    ) {
        if (soTinViPhamDaXacNhan < NGUONG_TIN_VI_PHAM_TOI_THIEU_DE_XUAT_KHOA || coPhieuChoAdmin || mocViPhamMoiNhat == null) {
            return false;
        }
        if (mocTuChoiGanNhat == null) {
            return true;
        }
        return mocViPhamMoiNhat.isAfter(mocTuChoiGanNhat);
    }

    /**
     * S2-07 / S2-14 (UC-16): Kiểm tra quyền cập nhật hồ sơ / vai trò tài khoản bởi Quản trị viên dưới khóa dòng.
     * Nếu request PATCH chỉ sửa hồ sơ và không gửi {@code vaiTroMoiTrongRequest} (null), dùng {@code vaiTroHienTaiTrongDb}
     * đã đọc dưới khóa để kiểm tra bất biến Quản trị viên không tự hạ quyền của chính mình.
     */
    public static boolean duocPhepCapNhatTaiKhoanQuanTri(
            PrincipalPhienHienTai principal,
            UUID taiKhoanMucTieuId,
            VaiTroTaiKhoan vaiTroHienTaiTrongDb,
            VaiTroTaiKhoan vaiTroMoiTrongRequest
    ) {
        if (taiKhoanMucTieuId == null || vaiTroHienTaiTrongDb == null) {
            return false;
        }
        if (!MaTranQuyenSprint1.duocPhepThucHien(
                principal,
                ThaoTacNghiepVu.CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                null
        )) {
            return false;
        }
        VaiTroTaiKhoan vaiTroSauCapNhat = vaiTroMoiTrongRequest != null ? vaiTroMoiTrongRequest : vaiTroHienTaiTrongDb;
        if (principal.getTaiKhoanId().equals(taiKhoanMucTieuId)) {
            return vaiTroSauCapNhat == VaiTroTaiKhoan.QUAN_TRI_VIEN;
        }
        return true;
    }

    /**
     * S2-07 / S2-14 (UC-16): Biến thể rút gọn khi {@code vaiTroMoi} bắt buộc được chỉ định tường minh.
     */
    public static boolean duocPhepCapNhatVaiTroQuanTri(
            PrincipalPhienHienTai principal,
            UUID taiKhoanMucTieuId,
            VaiTroTaiKhoan vaiTroMoi
    ) {
        if (vaiTroMoi == null) {
            return false;
        }
        return duocPhepCapNhatTaiKhoanQuanTri(principal, taiKhoanMucTieuId, vaiTroMoi, vaiTroMoi);
    }

    /**
     * S2-09 / S2-14 (UC-17): Kiểm tra quyền khóa tài khoản bởi Quản trị viên dưới khóa dòng.
     * Chốt chặn bắt buộc: Quản trị viên không được tự khóa tài khoản của chính mình và không khóa tài khoản Quản trị viên khác
     * qua phiếu đề xuất khóa của KDV.
     */
    public static boolean duocPhepKhoaTaiKhoan(
            PrincipalPhienHienTai principal,
            UUID taiKhoanBiKhoaId,
            VaiTroTaiKhoan vaiTroHienTaiCuaMucTieu
    ) {
        if (taiKhoanBiKhoaId == null || vaiTroHienTaiCuaMucTieu == null) {
            return false;
        }
        if (!MaTranQuyenSprint1.duocPhepThucHien(
                principal,
                ThaoTacNghiepVu.XEM_VA_QUYET_DINH_YEU_CAU_KHOA,
                null
        )) {
            return false;
        }
        if (principal.getTaiKhoanId().equals(taiKhoanBiKhoaId)) {
            return false;
        }
        return vaiTroHienTaiCuaMucTieu != VaiTroTaiKhoan.QUAN_TRI_VIEN;
    }

    /**
     * Kiểm tra theo danh sách trắng (allowlist) cho POST /api/admin/accounts:
     * so khớp nguyên tên trường JSON (không trim) với {@link #CAC_TRUONG_CHO_PHEP_TAO_TAI_KHOAN_QUAN_TRI},
     * trả về tên trường đầu tiên không hợp lệ hoặc null nếu hợp lệ.
     */
    public static String timTruongKhongDuocPhepTaoTaiKhoanQuanTri(Map<String, ?> payload) {
        return timTruongNgoaiDanhSachChoPhep(payload, CAC_TRUONG_CHO_PHEP_TAO_TAI_KHOAN_QUAN_TRI);
    }

    /**
     * Kiểm tra theo danh sách trắng (allowlist) cho PATCH /api/admin/accounts/{accountId}:
     * so khớp nguyên tên trường JSON (không trim) với {@link #CAC_TRUONG_CHO_PHEP_CAP_NHAT_TAI_KHOAN_QUAN_TRI},
     * trả về tên trường đầu tiên không hợp lệ hoặc null nếu hợp lệ.
     */
    public static String timTruongKhongDuocPhepCapNhatTaiKhoanQuanTri(Map<String, ?> payload) {
        return timTruongNgoaiDanhSachChoPhep(payload, CAC_TRUONG_CHO_PHEP_CAP_NHAT_TAI_KHOAN_QUAN_TRI);
    }

    private static String timTruongNgoaiDanhSachChoPhep(Map<String, ?> payload, Set<String> allowlist) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : payload.keySet()) {
            if (key == null) {
                return "";
            }
            if (!allowlist.contains(key)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Bảng 4: Chuẩn hóa danh sách UUID cần khóa (loại bỏ null/trùng lặp và sắp xếp tăng dần theo thứ tự byte không dấu
     * giống hệt {@code ORDER BY id ASC} của PostgreSQL) để khóa nhiều dòng cùng bảng mà không bao giờ gây Deadlock.
     */
    public static List<UUID> sapXepThuTuKhoaUuid(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(THU_TU_KHOA_UUID_POSTGRES)
                .toList();
    }
}
