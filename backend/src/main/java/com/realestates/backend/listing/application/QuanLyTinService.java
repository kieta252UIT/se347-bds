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
import com.realestates.backend.listing.domain.KiemDuyetSoBoTin;
import com.realestates.backend.listing.domain.KiemTraNoiDungTin;
import com.realestates.backend.listing.domain.KiemTraSoLuongAnh;
import com.realestates.backend.listing.domain.NoiDungTinDauVao;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.domain.TinDangRepository.DuLieuSuaTin;
import com.realestates.backend.listing.domain.TinDangRepository.DuLieuXoaTin;
import com.realestates.backend.listing.domain.TinDangRepository.KetQuaYeuCauGhi;
import com.realestates.backend.listing.domain.TinDangRepository.LoiNghiepVuQuanLyTinException;
import com.realestates.backend.listing.dto.TinCuaToiResponse;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.listing.exception.MapServiceUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class QuanLyTinService {

    public static final String HEADER_IF_MATCH = "If-Match";
    public static final String TRUONG_PHIEN_BAN_DA_XEM = "phienBanDaXemId";

    private static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE;
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống lưu tin đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern ETAG_MANH = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final TinDangRepository tinDangRepository;
    private final BanDoPort banDoPort;
    private final Clock clock;

    @Autowired
    public QuanLyTinService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TinDangRepository tinDangRepository,
            BanDoPort banDoPort
    ) {
        this(permissionAndCsrfService, tinDangRepository, banDoPort, Clock.systemUTC());
    }

    public QuanLyTinService(
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

    public List<TinCuaToiResponse> layDanhSachTinCuaToi(String rawSessionCookie) {
        PrincipalPhienHienTai nguoiDoc = permissionAndCsrfService.kiemTraPhienVaQuyenHienTai(rawSessionCookie);
        return tinDangRepository.docDanhSachTinCuaChu(nguoiDoc.getTaiKhoanId()).stream()
                .map(TinCuaToiResponse::fromDuLieu)
                .toList();
    }

    public TinGhiKetQua suaTin(
            String rawSessionCookie,
            UUID tinId,
            String rawIdempotencyKey,
            String rawIfMatch,
            Map<String, Object> rawPayload
    ) {
        PrincipalPhienHienTai nguoiSua = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                null,
                rawPayload
        );

        String maYDinh = TinDangService.chuanHoaVaKiemTraMaYDinh(rawIdempotencyKey);
        long phienBanCapNhatDaXem = docIfMatch(rawIfMatch);
        if (rawPayload == null) {
            throw new AuthValidationException(
                    MA_LOI_DU_LIEU_KHONG_HOP_LE,
                    "Dữ liệu sửa tin không được để trống.",
                    List.of()
            );
        }

        Map<String, Object> yeuCauDeBam = new LinkedHashMap<>();
        yeuCauDeBam.put("ifMatch", rawIfMatch.trim());
        yeuCauDeBam.put("body", rawPayload);
        String duLieuBam = ChuanHoaJson.bamYeuCau("PUT", "/api/listings/" + tinId, yeuCauDeBam);

        Optional<KetQuaYeuCauGhi> daGhi = traCuuYeuCauGhi(
                nguoiSua.getTaiKhoanId(),
                TinDangRepository.LOAI_THAO_TAC_SUA_TIN,
                maYDinh
        );
        if (daGhi.isPresent()) {
            return TinDangService.xuLyYeuCauGhiDaTonTai(daGhi.get(), duLieuBam);
        }

        Map<String, Object> noiDungTho = new LinkedHashMap<>(rawPayload);
        Object phienBanDaXemTho = noiDungTho.remove(TRUONG_PHIEN_BAN_DA_XEM);
        KiemTraNoiDungTin.KetQua ketQuaNoiDung = KiemTraNoiDungTin.kiemTra(noiDungTho);
        KiemTraSoLuongAnh.KetQua ketQuaAnh = KiemTraSoLuongAnh.kiemTra(
                noiDungTho.get(KiemTraNoiDungTin.TRUONG_ANH_IDS)
        );
        List<FieldErrorItem> loiDauVao = new ArrayList<>(
                TinDangService.gopLoiDauVao(ketQuaNoiDung.loi(), ketQuaAnh.loi())
        );
        UUID phienBanDaXemId = docPhienBanDaXem(phienBanDaXemTho, loiDauVao);
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
        TinDangService.xacMinhToaDoBanDo(banDoPort, noiDung);

        Instant thoiDiemHienTai = clock.instant();
        return thucThiGhi(
                rawSessionCookie,
                ThaoTacNghiepVu.CHINH_SUA_TIN_CUA_MINH,
                tinId,
                rawPayload,
                nguoiSua.getTaiKhoanId(),
                TinDangRepository.LOAI_THAO_TAC_SUA_TIN,
                maYDinh,
                duLieuBam,
                freshPrincipal -> tinDangRepository.suaTinTrongTransaction(new DuLieuSuaTin(
                        tinId,
                        freshPrincipal.getTaiKhoanId(),
                        phienBanDaXemId,
                        phienBanCapNhatDaXem,
                        noiDung,
                        anhIds,
                        ketQuaSoBo.ketQuaSoBo(),
                        thoiDiemHienTai,
                        maYDinh,
                        duLieuBam
                ))
        );
    }

    public TinGhiKetQua xoaTin(
            String rawSessionCookie,
            UUID tinId,
            String rawIdempotencyKey,
            String rawIfMatch
    ) {
        PrincipalPhienHienTai nguoiXoa = permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                null,
                null
        );

        String maYDinh = TinDangService.chuanHoaVaKiemTraMaYDinh(rawIdempotencyKey);
        long phienBanCapNhatDaXem = docIfMatch(rawIfMatch);

        Map<String, Object> yeuCauDeBam = new LinkedHashMap<>();
        yeuCauDeBam.put("ifMatch", rawIfMatch.trim());
        String duLieuBam = ChuanHoaJson.bamYeuCau("DELETE", "/api/listings/" + tinId, yeuCauDeBam);

        Optional<KetQuaYeuCauGhi> daGhi = traCuuYeuCauGhi(
                nguoiXoa.getTaiKhoanId(),
                TinDangRepository.LOAI_THAO_TAC_XOA_TIN,
                maYDinh
        );
        if (daGhi.isPresent()) {
            return TinDangService.xuLyYeuCauGhiDaTonTai(daGhi.get(), duLieuBam);
        }

        Instant thoiDiemHienTai = clock.instant();
        return thucThiGhi(
                rawSessionCookie,
                ThaoTacNghiepVu.XOA_MEM_TIN_CUA_MINH,
                tinId,
                null,
                nguoiXoa.getTaiKhoanId(),
                TinDangRepository.LOAI_THAO_TAC_XOA_TIN,
                maYDinh,
                duLieuBam,
                freshPrincipal -> tinDangRepository.xoaTinTrongTransaction(new DuLieuXoaTin(
                        tinId,
                        freshPrincipal.getTaiKhoanId(),
                        phienBanCapNhatDaXem,
                        thoiDiemHienTai,
                        maYDinh,
                        duLieuBam
                ))
        );
    }

    private TinGhiKetQua thucThiGhi(
            String rawSessionCookie,
            ThaoTacNghiepVu thaoTac,
            UUID tinId,
            Map<String, Object> rawPayload,
            UUID taiKhoanId,
            String loaiThaoTac,
            String maYDinh,
            String duLieuBam,
            Function<PrincipalPhienHienTai, TinGhiKetQua> hanhDong
    ) {
        try {
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie,
                    thaoTac,
                    () -> tinDangRepository.khoaVaDocChuTin(tinId).orElseThrow(ListingNotFoundException::new),
                    tinId,
                    rawPayload,
                    hanhDong
            );
        } catch (LoiNghiepVuQuanLyTinException ex) {
            throw chuyenLoiNghiepVu(ex);
        } catch (DuplicateKeyException ex) {
            Optional<KetQuaYeuCauGhi> replay = traCuuYeuCauGhi(taiKhoanId, loaiThaoTac, maYDinh);
            if (replay.isPresent()) {
                return TinDangService.xuLyYeuCauGhiDaTonTai(replay.get(), duLieuBam);
            }
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        } catch (AuthValidationException | UnauthenticatedSessionException | InsufficientPermissionException
                | AccountLockedException | EmailNotVerifiedException | InvalidCsrfException
                | AuthProviderUnavailableException | ListingConflictException | ListingNotFoundException
                | ListingPreconditionException | MapServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private Optional<KetQuaYeuCauGhi> traCuuYeuCauGhi(UUID taiKhoanId, String loaiThaoTac, String maYDinh) {
        try {
            return tinDangRepository.timYeuCauGhi(taiKhoanId, loaiThaoTac, maYDinh);
        } catch (AuthProviderUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private static long docIfMatch(String rawIfMatch) {
        if (rawIfMatch == null || rawIfMatch.isBlank()) {
            throw new ListingPreconditionException(
                    ListingPreconditionException.MA_LOI_THIEU_IF_MATCH,
                    "Thiếu header If-Match. Hãy tải lại tin để lấy phiên bản hiện tại."
            );
        }
        String giaTri = rawIfMatch.trim();
        if (ETAG_MANH.matcher(giaTri).matches()) {
            try {
                return Long.parseLong(giaTri.substring(1, giaTri.length() - 1));
            } catch (NumberFormatException ex) {
                throw loiIfMatchKhongHopLe();
            }
        }
        throw loiIfMatchKhongHopLe();
    }

    private static AuthValidationException loiIfMatchKhongHopLe() {
        return new AuthValidationException(
                MA_LOI_DU_LIEU_KHONG_HOP_LE,
                "Dữ liệu gửi lên không hợp lệ.",
                List.of(new FieldErrorItem(
                        HEADER_IF_MATCH,
                        MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        "If-Match phải là ETag mạnh dạng \"số\" lấy từ bản quản lý tin."
                ))
        );
    }

    private static UUID docPhienBanDaXem(Object giaTriTho, List<FieldErrorItem> loiDauVao) {
        if (giaTriTho instanceof String chuoi && UUID_CHUAN.matcher(chuoi).matches()) {
            return UUID.fromString(chuoi);
        }
        loiDauVao.add(new FieldErrorItem(
                TRUONG_PHIEN_BAN_DA_XEM,
                MA_LOI_DU_LIEU_KHONG_HOP_LE,
                "Trường bắt buộc, phải là mã phiên bản dạng UUID."
        ));
        return null;
    }

    private static RuntimeException chuyenLoiNghiepVu(LoiNghiepVuQuanLyTinException ex) {
        return switch (ex.getLoaiLoi()) {
            case TIN_KHONG_TON_TAI -> new ListingNotFoundException();
            case PHIEN_BAN_CU -> new ListingPreconditionException(
                    ListingPreconditionException.MA_LOI_PHIEN_BAN_CU,
                    ex.getMessage()
            );
            case TRANG_THAI_KHONG_HOP_LE -> new ListingConflictException(
                    ListingConflictException.MA_LOI_TRANG_THAI_KHONG_HOP_LE,
                    ex.getMessage()
            );
            case XUNG_DOT_MA_Y_DINH -> new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
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
}

