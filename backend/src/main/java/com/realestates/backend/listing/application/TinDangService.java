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
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.KiemDuyetSoBoTin;
import com.realestates.backend.listing.domain.KiemTraNoiDungTin;
import com.realestates.backend.listing.domain.KiemTraSoLuongAnh;
import com.realestates.backend.listing.domain.LoiTruongNoiDung;
import com.realestates.backend.listing.domain.NoiDungTinDauVao;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.domain.TinDangRepository.DuLieuTaoTin;
import com.realestates.backend.listing.domain.TinDangRepository.KetQuaYeuCauGhi;
import com.realestates.backend.listing.domain.TinDangRepository.LoiNghiepVuTaoTinException;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.MapServiceUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TinDangService {

    public static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = "DU_LIEU_KHONG_HOP_LE";
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final TinDangRepository tinDangRepository;
    private final BanDoPort banDoPort;
    private final Clock clock;

    @Autowired
    public TinDangService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TinDangRepository tinDangRepository,
            BanDoPort banDoPort
    ) {
        this(permissionAndCsrfService, tinDangRepository, banDoPort, Clock.systemUTC());
    }

    public TinDangService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TinDangRepository tinDangRepository,
            BanDoPort banDoPort,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.tinDangRepository = Objects.requireNonNull(tinDangRepository, "tinDangRepository không được null");
        this.banDoPort = Objects.requireNonNull(banDoPort, "banDoPort không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    public TinGhiKetQua dangTin(
            String rawSessionCookie,
            String rawIdempotencyKey,
            Map<String, Object> rawPayload
    ) {
        // Vòng 1: Kiểm tra phiên, trạng thái tài khoản, xác minh email và vai trò trước tiên
        PrincipalPhienHienTai nguoiDang = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                null,
                null
        );

        String maYDinh = chuanHoaVaKiemTraMaYDinh(rawIdempotencyKey);
        if (rawPayload == null) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu tạo tin không được để trống.",
                    List.of()
            );
        }

        String duLieuBam = ChuanHoaJson.bamYeuCau("POST", "/api/listings", rawPayload);

        // Vòng 2: Kiểm tra replay ý định đã commit trước khi kiểm tra điều kiện thay đổi được (hạn mức, trạng thái ảnh)
        Optional<KetQuaYeuCauGhi> daGhi = traCuuYeuCauGhi(nguoiDang.getTaiKhoanId(), maYDinh);
        if (daGhi.isPresent()) {
            return xuLyYeuCauGhiDaTonTai(daGhi.get(), duLieuBam);
        }

        // Vòng 3: Kiểm tra đủ 11 trường nội dung + danh sách anhIds (1..12 UUID không trùng) + kiểm duyệt sơ bộ
        KiemTraNoiDungTin.KetQua ketQuaNoiDung = KiemTraNoiDungTin.kiemTra(rawPayload);
        KiemTraSoLuongAnh.KetQua ketQuaAnh = KiemTraSoLuongAnh.kiemTra(
                rawPayload.get(KiemTraNoiDungTin.TRUONG_ANH_IDS)
        );
        List<FieldErrorItem> loiDauVao = gopLoiDauVao(ketQuaNoiDung.loi(), ketQuaAnh.loi());
        if (!loiDauVao.isEmpty()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu gửi lên không hợp lệ.",
                    loiDauVao
            );
        }

        NoiDungTinDauVao noiDung = ketQuaNoiDung.noiDung();
        List<UUID> anhIds = ketQuaAnh.anhIds();

        KiemDuyetSoBoTin.KetQua ketQuaSoBo = KiemDuyetSoBoTin.danhGia(noiDung, anhIds);
        if (!ketQuaSoBo.dat()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu tin chưa đạt kiểm duyệt sơ bộ.",
                    List.of()
            );
        }

        // Vòng 4: Gọi Port bản đồ NGOÀI transaction CSDL
        xacMinhToaDoBanDo(noiDung);

        Instant thoiDiemHienTai = clock.instant();
        LocalDate thangVietNam = HanMucThangHelper.dauThangVietNam(thoiDiemHienTai);
        String duLieuAuditJson = taoDuLieuAudit(noiDung, anhIds, ketQuaSoBo.ketQuaSoBo());

        // Vòng 5: Trong 1 transaction DB ngắn: khóa tài khoản -> phiên -> hạn mức tháng -> ảnh -> ghi tất cả
        try {
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie,
                    ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                    null,
                    null,
                    rawPayload,
                    freshPrincipal -> tinDangRepository.taoTinMoiTrongTransaction(
                            new DuLieuTaoTin(
                                    freshPrincipal.getTaiKhoanId(),
                                    noiDung,
                                    anhIds,
                                    ketQuaSoBo.ketQuaSoBo(),
                                    thangVietNam,
                                    thoiDiemHienTai,
                                    maYDinh,
                                    duLieuBam,
                                    duLieuAuditJson
                            )
                    )
            );
        } catch (LoiNghiepVuTaoTinException ex) {
            throw chuyenLoiNghiepVu(ex);
        } catch (DuplicateKeyException ex) {
            // Giao dịch xung đột vừa rollback hoàn toàn; tra cứu lại kết quả đã commit trong giao dịch mới
            Optional<KetQuaYeuCauGhi> replay = traCuuYeuCauGhi(nguoiDang.getTaiKhoanId(), maYDinh);
            if (replay.isPresent()) {
                return xuLyYeuCauGhiDaTonTai(replay.get(), duLieuBam);
            }
            throw new AuthProviderUnavailableException("Hệ thống lưu tin đang tạm gián đoạn. Vui lòng thử lại sau.", ex);
        } catch (AuthValidationException | UnauthenticatedSessionException | InsufficientPermissionException
                | AccountLockedException | EmailNotVerifiedException | InvalidCsrfException
                | AuthProviderUnavailableException | ListingConflictException | MapServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException("Hệ thống lưu tin đang tạm gián đoạn. Vui lòng thử lại sau.", ex);
        }
    }

    private void xacMinhToaDoBanDo(NoiDungTinDauVao noiDung) {
        boolean hopLe;
        try {
            hopLe = banDoPort.xacMinhToaDo(
                    new BigDecimal(noiDung.viDo()),
                    new BigDecimal(noiDung.kinhDo()),
                    noiDung.diaChi(),
                    noiDung.khuVuc()
            );
        } catch (MapServiceUnavailableException | AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new MapServiceUnavailableException(
                    MapServiceUnavailableException.MA_LOI_TAM_GIAN_DOAN,
                    "Dịch vụ bản đồ đang tạm gián đoạn. Vui lòng thử lại sau.",
                    ex
            );
        }
        if (!hopLe) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu gửi lên không hợp lệ.",
                    List.of(
                            new FieldErrorItem(
                                    KiemTraNoiDungTin.TRUONG_VI_DO,
                                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                                    "Tọa độ vị trí không được dịch vụ bản đồ xác minh hợp lệ."
                            ),
                            new FieldErrorItem(
                                    KiemTraNoiDungTin.TRUONG_KINH_DO,
                                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                                    "Tọa độ vị trí không được dịch vụ bản đồ xác minh hợp lệ."
                            )
                    )
            );
        }
    }

    private Optional<KetQuaYeuCauGhi> traCuuYeuCauGhi(UUID taiKhoanId, String maYDinh) {
        try {
            return tinDangRepository.timYeuCauGhi(taiKhoanId, TinDangRepository.LOAI_THAO_TAC_DANG_TIN, maYDinh);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException("Hệ thống lưu tin đang tạm gián đoạn. Vui lòng thử lại sau.", ex);
        }
    }

    private static TinGhiKetQua xuLyYeuCauGhiDaTonTai(KetQuaYeuCauGhi yeuCau, String duLieuBamMoi) {
        if (!yeuCau.duLieuBam().equals(duLieuBamMoi)) {
            throw new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
                    "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
            );
        }
        return yeuCau.ketQua();
    }

    private static String chuanHoaVaKiemTraMaYDinh(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu gửi lên không hợp lệ.",
                    List.of(new FieldErrorItem(
                            HEADER_IDEMPOTENCY_KEY,
                            MA_LOI_DU_LIEU_KHONG_HOP_LE,
                            "Thiếu header Idempotency-Key."
                    ))
            );
        }
        String trimmed = rawIdempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu gửi lên không hợp lệ.",
                    List.of(new FieldErrorItem(
                            HEADER_IDEMPOTENCY_KEY,
                            MA_LOI_DU_LIEU_KHONG_HOP_LE,
                            "Idempotency-Key vượt quá " + MAX_IDEMPOTENCY_KEY_LENGTH + " ký tự."
                    ))
            );
        }
        return trimmed;
    }

    private static List<FieldErrorItem> gopLoiDauVao(List<LoiTruongNoiDung> loiNoiDung, LoiTruongNoiDung loiAnh) {
        List<FieldErrorItem> items = new ArrayList<>();
        if (loiNoiDung != null) {
            for (LoiTruongNoiDung item : loiNoiDung) {
                items.add(new FieldErrorItem(item.truong(), MA_LOI_DU_LIEU_KHONG_HOP_LE, item.thongDiep()));
            }
        }
        if (loiAnh != null) {
            items.add(new FieldErrorItem(loiAnh.truong(), MA_LOI_DU_LIEU_KHONG_HOP_LE, loiAnh.thongDiep()));
        }
        return List.copyOf(items);
    }

    private static RuntimeException chuyenLoiNghiepVu(LoiNghiepVuTaoTinException ex) {
        return switch (ex.getLoaiLoi()) {
            case XUNG_DOT_MA_Y_DINH -> new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
                    ex.getMessage()
            );
            case VUOT_HAN_MUC_THANG -> new ListingConflictException(
                    ListingConflictException.MA_LOI_HAN_MUC_THANG,
                    ex.getMessage()
            );
            case ANH_KHONG_HOP_LE -> new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu gửi lên không hợp lệ.",
                    List.of(new FieldErrorItem(
                            KiemTraNoiDungTin.TRUONG_ANH_IDS,
                            MA_LOI_DU_LIEU_KHONG_HOP_LE,
                            ex.getMessage()
                    ))
            );
        };
    }

    private static String taoDuLieuAudit(NoiDungTinDauVao noiDung, List<UUID> anhIds, String ketQuaSoBo) {
        Map<String, Object> sau = new LinkedHashMap<>();
        sau.put("trangThai", TinDangRepository.TRANG_THAI_CHO_KIEM_DUYET);
        sau.put("loaiGiaoDich", noiDung.loaiGiaoDich().getGiaTri());
        sau.put("loaiBds", noiDung.loaiBds().getGiaTri());
        sau.put("giaGoc", noiDung.giaGoc().toPlainString());
        sau.put("donViGia", noiDung.donViGia().getGiaTri());
        sau.put("dienTichM2", noiDung.dienTichM2().toPlainString());
        sau.put("khuVuc", noiDung.khuVuc());
        sau.put("viDo", noiDung.viDo());
        sau.put("kinhDo", noiDung.kinhDo());
        sau.put("ketQuaSoBo", ketQuaSoBo);
        sau.put("anhIds", anhIds.stream().map(UUID::toString).toList());

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("truoc", null);
        audit.put("sau", sau);
        return ChuanHoaJson.toJsonString(audit);
    }
}
