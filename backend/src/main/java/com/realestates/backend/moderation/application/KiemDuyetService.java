package com.realestates.backend.moderation.application;

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
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingNotFoundException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.domain.KiemDuyetRepository.DuLieuQuyetDinh;
import com.realestates.backend.moderation.domain.KiemDuyetRepository.KetQuaYeuCauQuyetDinh;
import com.realestates.backend.moderation.domain.KiemDuyetRepository.LoiNghiepVuKiemDuyetException;
import com.realestates.backend.moderation.dto.TinQuanLy;
import com.realestates.backend.moderation.dto.TrangTinQuanLy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class KiemDuyetService {

    public static final String HEADER_IF_MATCH = "If-Match";
    public static final String THAM_SO_TRANG = "trang";
    public static final String THAM_SO_KICH_THUOC = "kichThuoc";
    public static final int TRANG_TOI_DA = 100_000;
    public static final int KICH_THUOC_MAC_DINH = 20;
    public static final int KICH_THUOC_TOI_DA = 50;
    public static final int DO_DAI_LY_DO_TOI_DA = 1000;

    public static final String TRUONG_PHIEN_BAN_DA_XEM = "phienBanDaXemId";
    public static final String TRUONG_QUYET_DINH = "quyetDinh";
    public static final String TRUONG_LY_DO = "lyDo";
    public static final String QUYET_DINH_PHE_DUYET = "phe_duyet";
    public static final String QUYET_DINH_TU_CHOI = "tu_choi";

    private static final Set<String> CAC_TRUONG_QUYET_DINH = Set.of(
            TRUONG_PHIEN_BAN_DA_XEM, TRUONG_QUYET_DINH, TRUONG_LY_DO
    );
    private static final String TRANG_THAI_CHO = "cho_kiem_duyet";
    private static final String MA_LOI_DU_LIEU_KHONG_HOP_LE = TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE;
    private static final String THONG_DIEP_KHONG_HOP_LE = "Dữ liệu gửi lên không hợp lệ.";
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống kiểm duyệt đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern ETAG_MANH = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private static final Pattern SO_NGUYEN_KHONG_AM = Pattern.compile("^[0-9]{1,9}$");
    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final KiemDuyetRepository kiemDuyetRepository;
    private final Clock clock;

    @Autowired
    public KiemDuyetService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            KiemDuyetRepository kiemDuyetRepository
    ) {
        this(permissionAndCsrfService, kiemDuyetRepository, Clock.systemUTC());
    }

    public KiemDuyetService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            KiemDuyetRepository kiemDuyetRepository,
            Clock clock
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.kiemDuyetRepository = Objects.requireNonNull(kiemDuyetRepository, "kiemDuyetRepository không được null");
        this.clock = Objects.requireNonNull(clock, "clock không được null");
    }

    /** GET /api/moderation/listings: tin cho_kiem_duyet, ngày đăng tăng rồi id tăng. */
    public TrangTinQuanLy layDanhSachChoDuyet(String rawSessionCookie, String rawTrang, String rawKichThuoc) {
        kiemTraQuyenKdv(rawSessionCookie);
        List<FieldErrorItem> loi = new ArrayList<>();
        int trang = docSoTrongKhoang(rawTrang, THAM_SO_TRANG, 0, 0, TRANG_TOI_DA, loi);
        int kichThuoc = docSoTrongKhoang(rawKichThuoc, THAM_SO_KICH_THUOC, KICH_THUOC_MAC_DINH, 1, KICH_THUOC_TOI_DA, loi);
        if (!loi.isEmpty()) {
            throw new AuthValidationException(MA_LOI_DU_LIEU_KHONG_HOP_LE, THONG_DIEP_KHONG_HOP_LE, loi);
        }
        try {
            long tongSo = kiemDuyetRepository.demHangCho();
            long boQua = (long) trang * kichThuoc;
            List<TinQuanLy> noiDung = boQua >= tongSo
                    ? List.of()
                    : kiemDuyetRepository.docHangCho((int) boQua, kichThuoc);
            return new TrangTinQuanLy(List.copyOf(noiDung), trang, kichThuoc, tongSo);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    /** GET /api/moderation/listings/{listingId}: đúng bản hiện tại cần duyệt. */
    public TinQuanLy layBanCanDuyet(String rawSessionCookie, UUID tinId) {
        kiemTraQuyenKdv(rawSessionCookie);
        Optional<TinQuanLy> tin;
        try {
            tin = kiemDuyetRepository.docTinCanDuyet(tinId);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
        TinQuanLy ketQua = tin.orElseThrow(ListingNotFoundException::new);
        if (!TRANG_THAI_CHO.equals(ketQua.trangThai())) {
            throw new ListingConflictException(
                    ListingConflictException.MA_LOI_TRANG_THAI_KHONG_HOP_LE,
                    "Tin không còn ở trạng thái chờ kiểm duyệt."
            );
        }
        return ketQua;
    }

    /** POST /api/moderation/listings/{listingId}/decision. */
    public TinGhiKetQua quyetDinh(
            String rawSessionCookie,
            UUID tinId,
            String rawIdempotencyKey,
            String rawIfMatch,
            Map<String, Object> rawBody
    ) {
        PrincipalPhienHienTai nguoiDuyet = kiemTraQuyenKdv(rawSessionCookie);
        String maYDinh = chuanHoaVaKiemTraMaYDinh(rawIdempotencyKey);
        long phienBanCapNhatDaXem = docIfMatch(rawIfMatch);
        YeuCauQuyetDinh yeuCau = docYeuCauQuyetDinh(rawBody);

        Map<String, Object> thanBam = new LinkedHashMap<>();
        thanBam.put(TRUONG_PHIEN_BAN_DA_XEM, yeuCau.phienBanDaXemId().toString());
        thanBam.put(TRUONG_QUYET_DINH, yeuCau.quyetDinh());
        if (yeuCau.lyDo() != null) {
            thanBam.put(TRUONG_LY_DO, yeuCau.lyDo());
        }
        Map<String, Object> yeuCauDeBam = new LinkedHashMap<>();
        yeuCauDeBam.put("ifMatch", rawIfMatch.trim());
        yeuCauDeBam.put("body", thanBam);
        String duLieuBam = ChuanHoaJson.bamYeuCau("POST", duongDanQuyetDinh(tinId), yeuCauDeBam);

        boolean pheDuyet = QUYET_DINH_PHE_DUYET.equals(yeuCau.quyetDinh());
        try {
            return permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawSessionCookie,
                    ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                    null,
                    tinId,
                    null,
                    freshPrincipal -> kiemDuyetRepository.quyetDinhTrongTransaction(new DuLieuQuyetDinh(
                            tinId,
                            yeuCau.phienBanDaXemId(),
                            freshPrincipal.getTaiKhoanId(),
                            pheDuyet,
                            pheDuyet ? null : yeuCau.lyDo(),
                            phienBanCapNhatDaXem,
                            clock.instant(),
                            maYDinh,
                            duLieuBam
                    ))
            );
        } catch (LoiNghiepVuKiemDuyetException ex) {
            throw chuyenLoiNghiepVu(ex);
        } catch (DuplicateKeyException ex) {
            Optional<KetQuaYeuCauQuyetDinh> replay = traCuuYeuCauQuyetDinh(nguoiDuyet.getTaiKhoanId(), maYDinh);
            if (replay.isPresent()) {
                return phatLai(replay.get(), duLieuBam);
            }
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        } catch (AuthValidationException | UnauthenticatedSessionException | InsufficientPermissionException
                | AccountLockedException | EmailNotVerifiedException | InvalidCsrfException
                | AuthProviderUnavailableException | ListingConflictException | ListingNotFoundException
                | ListingPreconditionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    record YeuCauQuyetDinh(UUID phienBanDaXemId, String quyetDinh, String lyDo) {
    }

    /** QuyetDinhRequest với additionalProperties: false; lyDo bắt buộc khi tu_choi. */
    static YeuCauQuyetDinh docYeuCauQuyetDinh(Map<String, Object> rawBody) {
        Map<String, Object> than = rawBody == null ? Map.of() : rawBody;
        List<FieldErrorItem> loi = new ArrayList<>();
        for (String khoa : than.keySet()) {
            if (khoa == null || !CAC_TRUONG_QUYET_DINH.contains(khoa)) {
                loi.add(loiMuc(String.valueOf(khoa), "Trường không được phép trong yêu cầu quyết định."));
            }
        }

        UUID phienBanDaXemId = null;
        Object phienBanTho = than.get(TRUONG_PHIEN_BAN_DA_XEM);
        if (phienBanTho instanceof String chuoi && UUID_CHUAN.matcher(chuoi).matches()) {
            phienBanDaXemId = UUID.fromString(chuoi);
        } else {
            loi.add(loiMuc(TRUONG_PHIEN_BAN_DA_XEM, "Trường bắt buộc, phải là mã phiên bản dạng UUID."));
        }

        String quyetDinh = null;
        Object quyetDinhTho = than.get(TRUONG_QUYET_DINH);
        if (QUYET_DINH_PHE_DUYET.equals(quyetDinhTho) || QUYET_DINH_TU_CHOI.equals(quyetDinhTho)) {
            quyetDinh = (String) quyetDinhTho;
        } else {
            loi.add(loiMuc(TRUONG_QUYET_DINH, "Trường bắt buộc, chỉ nhận: phe_duyet, tu_choi."));
        }

        String lyDo = null;
        boolean coLyDo = than.containsKey(TRUONG_LY_DO);
        Object lyDoTho = than.get(TRUONG_LY_DO);
        if (coLyDo || QUYET_DINH_TU_CHOI.equals(quyetDinh)) {
            if (!(lyDoTho instanceof String chuoi) || chuoi.isBlank()) {
                loi.add(loiMuc(TRUONG_LY_DO, "Lý do là bắt buộc khi từ chối và không được để trống."));
            } else if (chuoi.trim().length() > DO_DAI_LY_DO_TOI_DA) {
                loi.add(loiMuc(TRUONG_LY_DO, "Lý do tối đa " + DO_DAI_LY_DO_TOI_DA + " ký tự."));
            } else {
                lyDo = chuoi.trim();
            }
        }

        if (!loi.isEmpty()) {
            throw new AuthValidationException(MA_LOI_DU_LIEU_KHONG_HOP_LE, THONG_DIEP_KHONG_HOP_LE, loi);
        }
        return new YeuCauQuyetDinh(phienBanDaXemId, quyetDinh, lyDo);
    }

    private PrincipalPhienHienTai kiemTraQuyenKdv(String rawSessionCookie) {
        return permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.XEM_HANG_CHO_VA_DUYET_TIN,
                null,
                null
        );
    }

    private Optional<KetQuaYeuCauQuyetDinh> traCuuYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
        try {
            return kiemDuyetRepository.timYeuCauQuyetDinh(taiKhoanId, maYDinh);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private static TinGhiKetQua phatLai(KetQuaYeuCauQuyetDinh daLuu, String duLieuBam) {
        if (!daLuu.duLieuBam().equals(duLieuBam)) {
            throw new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
                    "Mã Idempotency-Key đã được sử dụng cho một nội dung yêu cầu khác."
            );
        }
        return daLuu.ketQua();
    }

    public static String duongDanQuyetDinh(UUID tinId) {
        return "/api/moderation/listings/" + tinId.toString().toLowerCase(Locale.ROOT) + "/decision";
    }

    static String chuanHoaVaKiemTraMaYDinh(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw loiTruong(TinDangService.HEADER_IDEMPOTENCY_KEY, "Thiếu header Idempotency-Key.");
        }
        String trimmed = rawIdempotencyKey.trim();
        if (trimmed.length() > TinDangService.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw loiTruong(
                    TinDangService.HEADER_IDEMPOTENCY_KEY,
                    "Idempotency-Key vượt quá " + TinDangService.MAX_IDEMPOTENCY_KEY_LENGTH + " ký tự."
            );
        }
        return trimmed;
    }

    static long docIfMatch(String rawIfMatch) {
        if (rawIfMatch == null || rawIfMatch.isBlank()) {
            throw new ListingPreconditionException(
                    ListingPreconditionException.MA_LOI_THIEU_IF_MATCH,
                    "Thiếu header If-Match. Hãy tải lại bản cần duyệt để lấy ETag hiện tại."
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

    /** Không clamp: giá trị ngoài khoảng là 400. */
    static int docSoTrongKhoang(
            String raw,
            String truong,
            int macDinh,
            int toiThieu,
            int toiDa,
            List<FieldErrorItem> loi
    ) {
        if (raw == null) {
            return macDinh;
        }
        String giaTri = raw.trim();
        if (SO_NGUYEN_KHONG_AM.matcher(giaTri).matches()) {
            int so = Integer.parseInt(giaTri);
            if (so >= toiThieu && so <= toiDa) {
                return so;
            }
        }
        loi.add(loiMuc(truong, truong + " phải là số nguyên từ " + toiThieu + " đến " + toiDa + "."));
        return macDinh;
    }

    private static AuthValidationException loiIfMatchKhongHopLe() {
        return loiTruong(
                HEADER_IF_MATCH,
                "If-Match phải là ETag mạnh dạng \"số\" lấy từ bản cần duyệt."
        );
    }

    private static FieldErrorItem loiMuc(String truong, String thongDiep) {
        return new FieldErrorItem(truong, MA_LOI_DU_LIEU_KHONG_HOP_LE, thongDiep);
    }

    private static AuthValidationException loiTruong(String truong, String thongDiep) {
        return new AuthValidationException(MA_LOI_DU_LIEU_KHONG_HOP_LE, THONG_DIEP_KHONG_HOP_LE, List.of(loiMuc(truong, thongDiep)));
    }

    private static RuntimeException chuyenLoiNghiepVu(LoiNghiepVuKiemDuyetException ex) {
        return switch (ex.getLoaiLoi()) {
            case TIN_KHONG_TON_TAI -> new ListingNotFoundException();
            case PHIEN_BAN_CU -> new ListingPreconditionException(
                    ListingPreconditionException.MA_LOI_PHIEN_BAN_CU,
                    ex.getMessage()
            );
            case TRANG_THAI_KHONG_HOP_LE, KHONG_DAT_BR03 -> new ListingConflictException(
                    ListingConflictException.MA_LOI_TRANG_THAI_KHONG_HOP_LE,
                    ex.getMessage()
            );
            case XUNG_DOT_MA_Y_DINH -> new ListingConflictException(
                    ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG,
                    ex.getMessage()
            );
        };
    }
}
