package com.realestates.backend.listing.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.domain.KiemTraAnh;
import com.realestates.backend.listing.domain.QuyenDocAnh;
import com.realestates.backend.listing.dto.AnhTamKetQua;
import java.util.List;
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

        UUID anhId = UUID.randomUUID();
        String khoaDoiTuong = anhId + "." + dinhDang.getDuoiTep();
        anhStoragePort.luu(khoaDoiTuong, noiDung, dinhDang.getMimeType());
        try {
            anhTinRepository.luuAnhTam(anhId, nguoiTai.getTaiKhoanId(), khoaDoiTuong, dinhDang, noiDung.length);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
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
        byte[] noiDung = anhStoragePort.doc(anh.get().khoaDoiTuongCanDoc());
        return Optional.of(new AnhDoc(anh.get().mimeType(), noiDung));
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

    private static AuthValidationException loiTep(String thongDiep) {
        return new AuthValidationException(
                MA_LOI_DU_LIEU_KHONG_HOP_LE,
                "Dữ liệu gửi lên không hợp lệ.",
                List.of(new FieldErrorItem(TRUONG_TEP, MA_LOI_DU_LIEU_KHONG_HOP_LE, thongDiep))
        );
    }
}

