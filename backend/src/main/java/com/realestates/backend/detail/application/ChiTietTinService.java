package com.realestates.backend.detail.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.detail.domain.ChiTietTinRepository;
import com.realestates.backend.detail.domain.ChiTietTinRepository.DongChiTiet;
import com.realestates.backend.detail.dto.LienHeChuTin;
import com.realestates.backend.detail.dto.PhienBanNoiDung;
import com.realestates.backend.detail.dto.TinCongKhai;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.search.domain.KieuGia;
import com.realestates.backend.search.domain.QuyDoiGiaTimKiem;
import com.realestates.backend.search.dto.AnhHienThi;
import com.realestates.backend.search.dto.GiaQuyDoiHienThi;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * GET /api/listings/{listingId} (xemTinCongKhai, khách được phép). Mọi trường hợp không xem được (id sai định dạng,
 * không tồn tại, không đạt BR-01) đều là cùng một 404 KHONG_TIM_THAY, không lộ lý do.
 */
@Service
public class ChiTietTinService {

    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final ChiTietTinRepository chiTietTinRepository;

    public ChiTietTinService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            ChiTietTinRepository chiTietTinRepository
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.chiTietTinRepository = Objects.requireNonNull(chiTietTinRepository, "chiTietTinRepository không được null");
    }

    public TinCongKhai xem(String rawSessionCookie, String listingId) {
        permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie, ThaoTacNghiepVu.XEM_CHI_TIET_TIN_DA_DUYET, null, null);
        UUID tinId = docTinId(listingId);
        Optional<DongChiTiet> doc;
        try {
            doc = chiTietTinRepository.docChiTiet(tinId);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
        DongChiTiet d = doc.orElseThrow(ListingNotFoundException::new);
        // Đường đọc chi tiết mặc định chế độ giá tong.
        QuyDoiGiaTimKiem quyDoi = QuyDoiGiaTimKiem.tu(d.donViGia(), KieuGia.TONG, d.giaGoc(), d.dienTichM2());
        PhienBanNoiDung noiDung = new PhienBanNoiDung(
                d.phienBanId(),
                d.soPhienBan(),
                d.tieuDe(),
                d.moTa(),
                d.loaiGiaoDich(),
                d.loaiBds(),
                d.giaGoc().toPlainString(),
                d.donViGia(),
                d.dienTichM2().toPlainString(),
                d.diaChi(),
                d.khuVuc(),
                d.viDo().toPlainString(),
                d.kinhDo().toPlainString(),
                d.anh().stream().map(a -> AnhHienThi.tu(a.id(), a.thuTu())).toList(),
                d.taoLuc(),
                new GiaQuyDoiHienThi(
                        quyDoi.hienThi().toPlainString(),
                        QuyDoiGiaTimKiem.donViHienThi(d.loaiGiaoDich(), KieuGia.TONG),
                        GiaQuyDoiHienThi.CHU_THICH
                )
        );
        return new TinCongKhai(d.tinId(), "da_duyet", d.ngayDang(), noiDung, new LienHeChuTin(d.hoTen(), d.soDienThoai()));
    }

    private static UUID docTinId(String listingId) {
        if (listingId == null || !UUID_CHUAN.matcher(listingId).matches()) {
            throw new ListingNotFoundException();
        }
        return UUID.fromString(listingId);
    }
}
