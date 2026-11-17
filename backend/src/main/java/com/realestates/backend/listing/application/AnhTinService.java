package com.realestates.backend.listing.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InsufficientPermissionException;
import com.realestates.backend.auth.exception.InvalidCsrfException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.domain.KiemTraAnh;
import com.realestates.backend.listing.domain.QuyenDocAnh;
import com.realestates.backend.listing.dto.AnhTamKetQua;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnhTinService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final String TRUONG_TEP = "tep";
    public static final String TRANG_THAI_ANH_TAM = "tam";

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final AnhTinRepository anhTinRepository;
    private final AnhStoragePort anhStoragePort;

    public AnhTinService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            AnhTinRepository anhTinRepository,
            AnhStoragePort anhStoragePort
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.anhTinRepository = Objects.requireNonNull(anhTinRepository, "anhTinRepository không được null");
        this.anhStoragePort = Objects.requireNonNull(anhStoragePort, "anhStoragePort không được null");
    }

    public PrincipalPhienHienTai xacThucNguoiTaiAnh(String rawSessionCookie) {
        return permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                null,
                null
        );
    }

    public AnhTamKetQua taiAnhTam(String rawSessionCookie, byte[] noiDung) {
        // Vòng 1: Xác thực sơ bộ quyền trước khi tốn tài nguyên xử lý tệp
        xacThucNguoiTaiAnh(rawSessionCookie);

        // Vòng 2: Phân tích, giải mã an toàn chống decompression bomb và chuẩn bị bản hiển thị
        if (noiDung == null || noiDung.length == 0) {
            throw loiTep("Tệp ảnh rỗng.");
        }
        if (!KiemTraAnh.kichThuocHopLe(noiDung.length)) {
            throw loiTep("Tệp ảnh vượt quá " + KiemTraAnh.KICH_THUOC_TOI_DA_BYTE + " byte.");
        }
        DinhDangAnh dinhDang = KiemTraAnh.nhanDang(noiDung)
                .orElseThrow(() -> loiTep("Nội dung tệp không phải ảnh JPEG, PNG hoặc WebP hợp lệ."));

        BufferedImage image = KiemTraAnh.giaiMaCoGioiHan(noiDung)
                .orElseThrow(() -> loiTep("Nội dung tệp không phải ảnh JPEG, PNG hoặc WebP hợp lệ hoặc kích thước vượt quá giới hạn."));

        KiemTraAnh.BanAnhHienThi banHienThi;
        try {
            banHienThi = KiemTraAnh.taoBanHienThi(image, dinhDang);
        } catch (Exception ex) {
            throw loiTep("Không thể tạo bản hiển thị cho ảnh.");
        }

        UUID anhId = UUID.randomUUID();
        String khoaTepGoc = anhId + "." + dinhDang.getDuoiTep();
        String khoaAnhHienThi = anhId + "-display." + banHienThi.duoiTep();

        // Vòng 3: Ghi Storage NGOÀI transaction để không giữ khóa DB suốt cuộc gọi mạng
        anhStoragePort.luu(khoaTepGoc, noiDung, dinhDang.getMimeType());
        try {
            anhStoragePort.luu(khoaAnhHienThi, banHienThi.noiDung(), banHienThi.mimeType());
        } catch (RuntimeException ex) {
            buTruXoaDoiTuong(khoaTepGoc, null);
            throw ex;
        }

        // Vòng 4: Mở transaction ngắn, kiểm lại tài khoản, phiên theo thứ tự khóa đã thiết kế rồi mới ghi metadata
        try {
            permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie,
                    ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                    null,
                    null,
                    Map.of(TRUONG_TEP, "anh"),
                    freshPrincipal -> {
                        anhTinRepository.luuAnhTam(
                                anhId,
                                freshPrincipal.getTaiKhoanId(),
                                khoaTepGoc,
                                khoaAnhHienThi,
                                dinhDang,
                                noiDung.length
                        );
                        return null;
                    }
            );
        } catch (AuthProviderUnavailableException ex) {
            buTruXoaDoiTuong(khoaTepGoc, khoaAnhHienThi);
            throw ex;
        } catch (UnauthenticatedSessionException | InsufficientPermissionException | AccountLockedException
                | EmailNotVerifiedException | InvalidCsrfException ex) {
            buTruXoaDoiTuong(khoaTepGoc, khoaAnhHienThi);
            throw ex;
        } catch (RuntimeException ex) {
            buTruXoaDoiTuong(khoaTepGoc, khoaAnhHienThi);
            throw new AuthProviderUnavailableException("Kho lưu trữ ảnh đang tạm gián đoạn. Vui lòng thử lại sau.", ex);
        }

        return new AnhTamKetQua(
                anhId,
                TRANG_THAI_ANH_TAM,
                dinhDang.getMimeType(),
                noiDung.length,
                "/api/images/" + anhId + "/content"
        );
    }

    public AnhTamKetQua taiAnhTam(PrincipalPhienHienTai nguoiTai, byte[] noiDung) {
        Objects.requireNonNull(nguoiTai, "nguoiTai không được null");
        if (noiDung == null || noiDung.length == 0) {
            throw loiTep("Tệp ảnh rỗng.");
        }
        if (!KiemTraAnh.kichThuocHopLe(noiDung.length)) {
            throw loiTep("Tệp ảnh vượt quá " + KiemTraAnh.KICH_THUOC_TOI_DA_BYTE + " byte.");
        }
        DinhDangAnh dinhDang = KiemTraAnh.nhanDang(noiDung)
                .orElseThrow(() -> loiTep("Nội dung tệp không phải ảnh JPEG, PNG hoặc WebP hợp lệ."));

        BufferedImage image = KiemTraAnh.giaiMaCoGioiHan(noiDung)
                .orElseThrow(() -> loiTep("Nội dung tệp không phải ảnh JPEG, PNG hoặc WebP hợp lệ hoặc kích thước vượt quá giới hạn."));

        KiemTraAnh.BanAnhHienThi banHienThi;
        try {
            banHienThi = KiemTraAnh.taoBanHienThi(image, dinhDang);
        } catch (Exception ex) {
            throw loiTep("Không thể tạo bản hiển thị cho ảnh.");
        }

        UUID anhId = UUID.randomUUID();
        String khoaTepGoc = anhId + "." + dinhDang.getDuoiTep();
        String khoaAnhHienThi = anhId + "-display." + banHienThi.duoiTep();

        anhStoragePort.luu(khoaTepGoc, noiDung, dinhDang.getMimeType());
        try {
            anhStoragePort.luu(khoaAnhHienThi, banHienThi.noiDung(), banHienThi.mimeType());
        } catch (RuntimeException ex) {
            buTruXoaDoiTuong(khoaTepGoc, null);
            throw ex;
        }

        try {
            anhTinRepository.luuAnhTam(
                    anhId,
                    nguoiTai.getTaiKhoanId(),
                    khoaTepGoc,
                    khoaAnhHienThi,
                    dinhDang,
                    noiDung.length
            );
        } catch (AuthProviderUnavailableException ex) {
            buTruXoaDoiTuong(khoaTepGoc, khoaAnhHienThi);
            throw ex;
        } catch (RuntimeException ex) {
            buTruXoaDoiTuong(khoaTepGoc, khoaAnhHienThi);
            throw new AuthProviderUnavailableException("Kho lưu trữ ảnh đang tạm gián đoạn. Vui lòng thử lại sau.", ex);
        }

        return new AnhTamKetQua(
                anhId,
                TRANG_THAI_ANH_TAM,
                dinhDang.getMimeType(),
                noiDung.length,
                "/api/images/" + anhId + "/content"
        );
    }

    public Optional<AnhDoc> docAnh(UUID anhId, String rawSessionCookie) {
        PrincipalPhienHienTai nguoiDoc = permissionAndCsrfService
                .phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookie)
                .orElse(null);
        Optional<AnhTinTruyCap> anh = timAnh(anhId);
        if (anh.isEmpty() || !QuyenDocAnh.duocPhepDoc(anh.get(), nguoiDoc)) {
            return Optional.empty();
        }
        String khoaCanDoc = anh.get().khoaDoiTuongCanDoc();
        byte[] noiDung = anhStoragePort.doc(khoaCanDoc);
        String mimeType = xacDinhMimeType(khoaCanDoc, anh.get().mimeType());
        return Optional.of(new AnhDoc(mimeType, noiDung));
    }

    public void donDepAnhMoCoi(String khoaDoiTuong) {
        if (khoaDoiTuong == null || khoaDoiTuong.isBlank()) {
            return;
        }
        if (!anhTinRepository.coThamChieuKhoa(khoaDoiTuong)) {
            try {
                anhStoragePort.xoa(khoaDoiTuong);
            } catch (Exception ignored) {
            }
        }
    }

    private void buTruXoaDoiTuong(String khoaTepGoc, String khoaAnhHienThi) {
        if (khoaTepGoc != null) {
            try {
                if (!anhTinRepository.coThamChieuKhoa(khoaTepGoc)) {
                    anhStoragePort.xoa(khoaTepGoc);
                }
            } catch (Exception ignored) {
            }
        }
        if (khoaAnhHienThi != null) {
            try {
                if (!anhTinRepository.coThamChieuKhoa(khoaAnhHienThi)) {
                    anhStoragePort.xoa(khoaAnhHienThi);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private Optional<AnhTinTruyCap> timAnh(UUID anhId) {
        try {
            return anhTinRepository.timAnhDeDoc(anhId);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException("Kho lưu trữ ảnh đang tạm gián đoạn. Vui lòng thử lại sau.", ex);
        }
    }

    private static String xacDinhMimeType(String khoa, String macDinh) {
        if (khoa != null) {
            if (khoa.endsWith(".png")) {
                return "image/png";
            }
            if (khoa.endsWith(".jpg") || khoa.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (khoa.endsWith(".webp")) {
                return "image/webp";
            }
        }
        return macDinh;
    }

    private static AuthValidationException loiTep(String thongDiep) {
        return new AuthValidationException(
                MA_LOI_DU_LIEU_KHONG_HOP_LE,
                "Dữ liệu gửi lên không hợp lệ.",
                List.of(new FieldErrorItem(TRUONG_TEP, MA_LOI_DU_LIEU_KHONG_HOP_LE, thongDiep))
        );
    }
}
