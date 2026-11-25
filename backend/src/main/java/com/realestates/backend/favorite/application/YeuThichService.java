package com.realestates.backend.favorite.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.favorite.domain.YeuThichRepository;
import com.realestates.backend.favorite.domain.YeuThichRepository.TrangYeuThich;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.domain.KieuGia;
import com.realestates.backend.search.dto.TrangTinCongKhai;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Yêu thích (layTinYeuThich, luuTinYeuThich, boLuuTinYeuThich): chỉ Người dùng đủ quyền, taiKhoanId luôn lấy từ phiên.
 * Ghi (PUT/DELETE) kiểm lại quyền/trạng thái tài khoản dưới khóa trong transaction (TS1-019).
 * PUT tin sai định dạng/không công khai = 404; DELETE luôn 204.
 */
@Service
public class YeuThichService {

    private static final String THONG_DIEP_KHONG_HOP_LE = "Dữ liệu gửi lên không hợp lệ.";
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern SO_NGUYEN = Pattern.compile("^(0|[1-9][0-9]{0,8})$");

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final YeuThichRepository yeuThichRepository;

    public YeuThichService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            YeuThichRepository yeuThichRepository
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.yeuThichRepository = Objects.requireNonNull(yeuThichRepository, "yeuThichRepository không được null");
    }

    public void luu(String rawSessionCookie, String listingId) {
        kiemTraQuyen(rawSessionCookie);
        UUID tinId = docTinId(listingId);
        if (tinId == null) {
            throw new ListingNotFoundException();
        }
        boolean daLuu = ghiTrongTransaction(rawSessionCookie, tinId,
                nguoiDung -> yeuThichRepository.luu(nguoiDung.getTaiKhoanId(), tinId));
        if (!daLuu) {
            throw new ListingNotFoundException();
        }
    }

    public void bo(String rawSessionCookie, String listingId) {
        kiemTraQuyen(rawSessionCookie);
        UUID tinId = docTinId(listingId);
        if (tinId == null) {
            return;
        }
        ghiTrongTransaction(rawSessionCookie, tinId, nguoiDung -> {
            yeuThichRepository.bo(nguoiDung.getTaiKhoanId(), tinId);
            return Boolean.TRUE;
        });
    }

    public TrangTinCongKhai danhSach(String rawSessionCookie, String rawTrang, String rawKichThuoc) {
        UUID taiKhoanId = kiemTraQuyen(rawSessionCookie).getTaiKhoanId();
        List<FieldErrorItem> loi = new ArrayList<>();
        int trang = docSoNguyen(rawTrang, "trang", 0, 0, TimKiemTinService.TRANG_TOI_DA, loi);
        int kichThuoc = docSoNguyen(rawKichThuoc, "kichThuoc", TimKiemTinService.KICH_THUOC_MAC_DINH, 1,
                TimKiemTinService.KICH_THUOC_TOI_DA, loi);
        if (!loi.isEmpty()) {
            throw new AuthValidationException(TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE, THONG_DIEP_KHONG_HOP_LE, loi);
        }
        TrangYeuThich ketQua;
        try {
            ketQua = yeuThichRepository.docDanhSach(taiKhoanId, (long) trang * kichThuoc, kichThuoc);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
        return new TrangTinCongKhai(
                ketQua.noiDung().stream().map(d -> TimKiemTinService.tomTat(d, KieuGia.TONG)).toList(),
                trang,
                kichThuoc,
                ketQua.tongSo()
        );
    }

    private PrincipalPhienHienTai kiemTraQuyen(String rawSessionCookie) {
        return permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie, ThaoTacNghiepVu.THEM_GO_XEM_DANH_SACH_YEU_THICH, null, null);
    }

    private <T> T ghiTrongTransaction(
            String rawSessionCookie,
            UUID tinId,
            java.util.function.Function<PrincipalPhienHienTai, T> hanhDong
    ) {
        try {
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie, ThaoTacNghiepVu.THEM_GO_XEM_DANH_SACH_YEU_THICH, null, tinId, null, hanhDong);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    /** null nếu sai định dạng. */
    private static UUID docTinId(String listingId) {
        if (listingId == null || !UUID_CHUAN.matcher(listingId).matches()) {
            return null;
        }
        return UUID.fromString(listingId);
    }

    private static int docSoNguyen(String tho, String truong, int macDinh, int toiThieu, int toiDa, List<FieldErrorItem> loi) {
        if (tho == null) {
            return macDinh;
        }
        if (SO_NGUYEN.matcher(tho).matches()) {
            int so = Integer.parseInt(tho);
            if (so >= toiThieu && so <= toiDa) {
                return so;
            }
        }
        loi.add(new FieldErrorItem(truong, TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE,
                truong + " phải là số nguyên từ " + toiThieu + " đến " + toiDa + "."));
        return macDinh;
    }
}
