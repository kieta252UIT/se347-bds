package com.realestates.backend.auth.domain;

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
 *   chặn tuyệt đối Khách, người dùng khác và chủ tin bị báo cáo).
 * - S2-07 / S2-14: Chặn Quản trị viên tự hạ quyền hoặc tự khóa chính mình; kiểm tra trường tự khai bị cấm
 *   trên API quản trị tài khoản (chỉ mở ngoại lệ có kiểm soát cho trường vaiTro).
 * - Bảng 4: Sắp xếp thứ tự khóa UUID không dấu khớp 100% với PostgreSQL ORDER BY id ASC để chống Deadlock.
 */
public final class MaTranQuyenSprint2 {

    public static final String ANH_TAM = "tam";
    public static final String ANH_DA_GAN = "da_gan";

    private static final Set<String> CAC_TRUONG_DUOC_MO_TRONG_DTO_QUAN_TRI = Set.of(
            "vaiTro",
            "vai_tro"
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
     * Thông tin truy cập của một tệp ảnh bằng chứng báo cáo vi phạm (QD-S2-02).
     *
     * @param trangThaiAnh        trạng thái ảnh ('tam', 'da_gan', 'cho_don')
     * @param nguoiTaiAnhId       tai_khoan_id của người tải ảnh / gửi báo cáo
     * @param chuTinBiBaoCaoId    chu_id của tin bất động sản bị báo cáo (có thể null khi ảnh còn ở trạng thái 'tam')
     * @param thuocCanCuPhieuKhoa true nếu báo cáo chứa ảnh đã được xác nhận vi phạm và nằm trong căn cứ phiếu đề xuất khóa
     */
    public record AnhBangChungBaoCaoTruyCap(
            String trangThaiAnh,
            UUID nguoiTaiAnhId,
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
     * - Ảnh 'tam': chỉ chính người dùng đã tải ảnh được phép đọc.
     * - Ảnh 'da_gan':
     *   + NGUOI_DUNG: chỉ người gửi báo cáo (và không phải chủ tin bị báo cáo) được đọc; chủ tin bị báo cáo luôn bị chặn.
     *   + KIEM_DUYET_VIEN: được phép đọc để thẩm định báo cáo.
     *   + QUAN_TRI_VIEN: chỉ được đọc khi ảnh thuộc căn cứ của phiếu đề xuất khóa tài khoản (UC-17).
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
            return nguoiDoc.getVaiTro() == VaiTroTaiKhoan.NGUOI_DUNG
                    && nguoiDoc.getTaiKhoanId().equals(anh.nguoiTaiAnhId());
        }

        return switch (nguoiDoc.getVaiTro()) {
            case NGUOI_DUNG -> nguoiDoc.getTaiKhoanId().equals(anh.nguoiTaiAnhId())
                    && !nguoiDoc.getTaiKhoanId().equals(anh.chuTinBiBaoCaoId());
            case KIEM_DUYET_VIEN -> true;
            case QUAN_TRI_VIEN -> anh.thuocCanCuPhieuKhoa();
        };
    }

    /**
     * S2-07 / S2-14 (UC-16): Kiểm tra quyền cập nhật vai trò tài khoản bởi Quản trị viên.
     * Chốt chặn bắt buộc: Quản trị viên không được tự hạ quyền của chính mình xuống vai trò thấp hơn.
     */
    public static boolean duocPhepCapNhatVaiTroQuanTri(
            PrincipalPhienHienTai principal,
            UUID taiKhoanMucTieuId,
            VaiTroTaiKhoan vaiTroMoi
    ) {
        if (taiKhoanMucTieuId == null || vaiTroMoi == null) {
            return false;
        }
        if (!MaTranQuyenSprint1.duocPhepThucHien(
                principal,
                ThaoTacNghiepVu.CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                null
        )) {
            return false;
        }
        if (principal.getTaiKhoanId().equals(taiKhoanMucTieuId)) {
            return vaiTroMoi == VaiTroTaiKhoan.QUAN_TRI_VIEN;
        }
        return true;
    }

    /**
     * S2-09 / S2-14 (UC-17): Kiểm tra quyền khóa tài khoản bởi Quản trị viên.
     * Chốt chặn bắt buộc: Quản trị viên không được tự khóa tài khoản của chính mình.
     */
    public static boolean duocPhepKhoaTaiKhoan(
            PrincipalPhienHienTai principal,
            UUID taiKhoanBiKhoaId
    ) {
        if (taiKhoanBiKhoaId == null) {
            return false;
        }
        if (!MaTranQuyenSprint1.duocPhepThucHien(
                principal,
                ThaoTacNghiepVu.XEM_VA_QUYET_DINH_YEU_CAU_KHOA,
                null
        )) {
            return false;
        }
        return !principal.getTaiKhoanId().equals(taiKhoanBiKhoaId);
    }

    /**
     * Kiểm tra trường tự khai bị cấm riêng cho các endpoint quản trị tài khoản (POST/PATCH /api/admin/accounts),
     * nơi Quản trị viên được phép truyền trường vaiTro nhưng vẫn bị cấm truyền các trường hệ thống như
     * mocThuHoi, soDuVnd, trangThai, chuId, taiKhoanId...
     */
    public static String timTruongTuKhaiBiCamChoQuanTriTaiKhoan(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : payload.keySet()) {
            if (key != null) {
                String trimmed = key.trim();
                if (MaTranQuyenSprint1.CAC_TRUONG_TU_KHAI_BI_CAM.contains(trimmed)
                        && !CAC_TRUONG_DUOC_MO_TRONG_DTO_QUAN_TRI.contains(trimmed)) {
                    return trimmed;
                }
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
