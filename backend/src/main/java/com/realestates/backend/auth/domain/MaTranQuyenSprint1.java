package com.realestates.backend.auth.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bộ quy tắc miền kiểm tra Ma trận quyền hạn (RBAC) và quyền sở hữu tài nguyên (Ownership)
 * trong Sprint 1 theo đặc tả ma-tran-quyen-va-trang-thai-sprint-1.md (Bảng 1 & Bảng 2).
 *
 * Nguyên tắc cốt lõi:
 * - Vai trò phải lấy từ bản ghi tai_khoan trong kho lưu trữ chính, không tin dữ liệu tự khai từ client.
 * - Quản trị viên (quan_tri_vien) không tự động kế thừa quyền duyệt tin/xử lý báo cáo của Kiểm duyệt viên
 *   và không tự động kế thừa quyền đăng/sửa/xóa tin cá nhân của Người dùng.
 * - Mọi thao tác trên tài nguyên cá nhân phải kiểm tra chuId trong DB khớp với taiKhoanId của phiên.
 */
public final class MaTranQuyenSprint1 {

    public static final Set<String> CAC_TRUONG_TU_KHAI_BI_CAM = Set.of(
            "vaiTro",
            "vai_tro",
            "role",
            "roles",
            "isAdmin",
            "is_admin",
            "trangThai",
            "trang_thai",
            "emailDaXacMinh",
            "email_da_xac_minh",
            "mocThuHoi",
            "moc_thu_hoi",
            "chuId",
            "chu_id",
            "ownerId",
            "owner_id",
            "taiKhoanId",
            "tai_khoan_id",
            "nguoiKiemDuyetId",
            "nguoi_kiem_duyet_id",
            "nguoiXuLyId",
            "nguoi_xu_ly_id",
            "nguoiGuiId",
            "nguoi_gui_id",
            "nguoiQuyetDinhId",
            "nguoi_quyet_dinh_id",
            "nguoiThucHienId",
            "nguoi_thuc_hien_id",
            "soDu",
            "so_du",
            "soDuVnd",
            "so_du_vnd",
            "giaDaMua",
            "gia_da_mua",
            "thoiLuongConLai",
            "thoi_luong_con_lai",
            "hetHanLuc",
            "het_han_luc",
            "soLanViPham",
            "so_lan_vi_pham"
    );

    private MaTranQuyenSprint1() {
    }

    /**
     * Kiểm tra một vai trò có được phép thực hiện thao tác nghiệp vụ theo Bảng 1 hay không.
     *
     * @param vaiTro  vai trò hiện tại trong bảng tai_khoan (null nếu là Khách chưa đăng nhập)
     * @param thaoTac thao tác nghiệp vụ cần kiểm
     * @return true nếu vai trò được phép theo Bảng 1
     */
    public static boolean duocPhepTheoVaiTro(VaiTroTaiKhoan vaiTro, ThaoTacNghiepVu thaoTac) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");
        if (thaoTac.isChoPhepKhach()) {
            return true;
        }
        if (vaiTro == null) {
            return false;
        }
        return switch (thaoTac) {
            case TIM_LOC_SAP_XEP_TIN_CONG_KHAI,
                 XEM_CHI_TIET_TIN_DA_DUYET,
                 XEM_GOI_Y_BAT_DONG_SAN,
                 DANG_KY_XAC_MINH_EMAIL_DANG_NHAP_DAT_LAI_MAT_KHAU -> true;
            case LAY_HO_SO_HIEN_TAI,
                 DANG_XUAT -> true;
            case THEM_GO_XEM_DANH_SACH_YEU_THICH,
                 GHI_NHAN_LUOT_XEM_TIN,
                 TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                 CHINH_SUA_TIN_CUA_MINH,
                 XOA_MEM_TIN_CUA_MINH,
                 XEM_TIN_CUA_TOI_CHI_DOC,
                 TAI_ANH_BANG_CHUNG_BAO_CAO,
                 GUI_BAO_CAO_VI_PHAM,
                 XEM_THONG_BAO_CUA_TOI,
                 XEM_VI_VA_SO_GIAO_DICH,
                 XEM_BAO_GIA_GOI_DAY_TIN,
                 KICH_HOAT_GOI_DAY_TIN -> vaiTro == VaiTroTaiKhoan.NGUOI_DUNG;
            case XEM_HANG_CHO_VA_DUYET_TIN,
                 XEM_VA_XU_LY_BAO_CAO_VI_PHAM,
                 TAO_YEU_CAU_KHOA_TAI_KHOAN -> vaiTro == VaiTroTaiKhoan.KIEM_DUYET_VIEN;
            case TAO_CAP_NHAT_KHOA_MO_KHOA_TAI_KHOAN,
                 CAP_NHAT_HO_SO_VA_VAI_TRO_QUAN_TRI,
                 XEM_VA_QUYET_DINH_YEU_CAU_KHOA,
                 MO_KHOA_TAI_KHOAN_CO_CAN_CU -> vaiTro == VaiTroTaiKhoan.QUAN_TRI_VIEN;
        };
    }

    /**
     * Kiểm tra đầy đủ quyền vai trò (RBAC) và quyền sở hữu tài nguyên (chuId trong DB)
     * theo Bảng 1 và Bảng 2.
     *
     * @param principal            principal phiên hiện tại (null nếu là Khách)
     * @param thaoTac              thao tác nghiệp vụ
     * @param chuSoHuuTaiNguyenId  chu_id của tài nguyên đọc từ DB (bắt buộc khi thao tác yêu cầu chủ sở hữu)
     * @return true nếu được phép thực hiện
     */
    public static boolean duocPhepThucHien(
            PrincipalPhienHienTai principal,
            ThaoTacNghiepVu thaoTac,
            UUID chuSoHuuTaiNguyenId
    ) {
        Objects.requireNonNull(thaoTac, "thaoTac không được null");
        if (thaoTac.isChoPhepKhach()) {
            return true;
        }
        if (principal == null) {
            return false;
        }
        if (principal.getTrangThai() != TrangThaiTaiKhoan.HOAT_DONG || !principal.isEmailDaXacMinh()) {
            return false;
        }
        if (!duocPhepTheoVaiTro(principal.getVaiTro(), thaoTac)) {
            return false;
        }
        if (thaoTac.isYeuCauChuSoHuuTaiNguyen()) {
            if (chuSoHuuTaiNguyenId == null) {
                return false;
            }
            return principal.getTaiKhoanId().equals(chuSoHuuTaiNguyenId);
        }
        return true;
    }

    /**
     * Phát hiện trường tự khai quyền/vai trò/chủ sở hữu/trạng thái bị cấm trong payload từ client
     * (Bảng 2: Không tin bất kỳ vaiTro, trangThai, chuId... tự khai nào từ client).
     */
    public static String timTruongTuKhaiBiCam(Map<String, ?> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : payload.keySet()) {
            if (key != null && CAC_TRUONG_TU_KHAI_BI_CAM.contains(key.trim())) {
                return key.trim();
            }
        }
        return null;
    }
}
