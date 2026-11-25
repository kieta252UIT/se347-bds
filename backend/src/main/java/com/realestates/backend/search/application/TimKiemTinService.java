package com.realestates.backend.search.application;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.search.domain.BoLocTimKiem;
import com.realestates.backend.search.domain.ChuanHoaTimKiem;
import com.realestates.backend.search.domain.KieuGia;
import com.realestates.backend.search.domain.QuyDoiGiaTimKiem;
import com.realestates.backend.search.domain.SapXepTin;
import com.realestates.backend.search.domain.TimKiemRepository;
import com.realestates.backend.search.domain.TimKiemRepository.DongTin;
import com.realestates.backend.search.dto.AnhHienThi;
import com.realestates.backend.search.dto.GiaQuyDoiHienThi;
import com.realestates.backend.search.dto.KetQuaTimKiem;
import com.realestates.backend.search.dto.TinTomTat;
import com.realestates.backend.search.dto.TrangTinCongKhai;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** GET /api/listings theo openapi-sprint-1.yaml (timKiemTin) và hop-dong-tim-kiem-sprint-1.md. */
@Service
public class TimKiemTinService {

    public static final int SO_TAI_TRO_TOI_DA = 5;
    public static final int TRANG_TOI_DA = 100_000;
    public static final int KICH_THUOC_MAC_DINH = 20;
    public static final int KICH_THUOC_TOI_DA = 50;
    public static final int DO_DAI_TU_KHOA_TOI_DA = 100;
    public static final int SO_TU_KHOA_TOI_DA = 10;
    public static final int DO_DAI_KHU_VUC_TOI_DA = 120;
    public static final int DO_DAI_SO_TOI_DA = 100;

    public static final Set<String> CAC_THAM_SO = Set.of(
            "tuKhoa", "loaiGiaoDich", "loaiBds", "khuVuc", "cheDoGia", "giaMin", "giaMax",
            "dienTichMin", "dienTichMax", "nam", "bac", "tay", "dong", "sapXep", "trang", "kichThuoc"
    );

    private static final String MA_LOI = TinDangService.MA_LOI_DU_LIEU_KHONG_HOP_LE;
    private static final String THONG_DIEP_KHONG_HOP_LE = "Dữ liệu gửi lên không hợp lệ.";
    private static final String THONG_DIEP_GIAN_DOAN = "Hệ thống tìm kiếm đang tạm gián đoạn. Vui lòng thử lại sau.";
    private static final Pattern SO_KHONG_AM = Pattern.compile("^(0|[1-9][0-9]*)(\\.[0-9]+)?$");
    private static final Pattern SO_CO_DAU = Pattern.compile("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$");
    private static final Pattern SO_NGUYEN = Pattern.compile("^(0|[1-9][0-9]{0,8})$");

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final TimKiemRepository timKiemRepository;

    public TimKiemTinService(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            TimKiemRepository timKiemRepository
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(permissionAndCsrfService, "permissionAndCsrfService không được null");
        this.timKiemRepository = Objects.requireNonNull(timKiemRepository, "timKiemRepository không được null");
    }

    /** thamSo: toàn bộ query param thô (request.getParameterMap()), để chặn param lạ/lặp. */
    public KetQuaTimKiem timKiem(String rawSessionCookie, Map<String, String[]> thamSo) {
        // Công khai: khách được phép; cookie hỏng/thu hồi được coi là khách.
        permissionAndCsrfService.kiemTraQuyenThaoTac(
                rawSessionCookie,
                ThaoTacNghiepVu.TIM_LOC_SAP_XEP_TIN_CONG_KHAI,
                null,
                null
        );
        BoLocTimKiem boLoc = docBoLoc(thamSo == null ? Map.of() : thamSo);
        TimKiemRepository.KetQua ketQua;
        try {
            ketQua = timKiemRepository.timKiem(boLoc, SO_TAI_TRO_TOI_DA);
        } catch (RuntimeException ex) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
        Function<DongTin, TinTomTat> chuyen = dong -> tomTat(dong, boLoc.cheDoGia());
        return new KetQuaTimKiem(
                new TrangTinCongKhai(
                        ketQua.tuNhien().stream().map(chuyen).toList(),
                        boLoc.trang(),
                        boLoc.kichThuoc(),
                        ketQua.tongSoTuNhien()
                ),
                ketQua.taiTro().stream().map(chuyen).toList(),
                ketQua.thoiDiemTruyVan()
        );
    }

    public static TinTomTat tomTat(DongTin dong, KieuGia cheDo) {
        QuyDoiGiaTimKiem quyDoi = QuyDoiGiaTimKiem.tu(dong.donViGia(), cheDo, dong.giaGoc(), dong.dienTichM2());
        return new TinTomTat(
                dong.tinId(),
                dong.phienBanId(),
                dong.tieuDe(),
                dong.loaiGiaoDich(),
                dong.loaiBds(),
                dong.giaGoc().toPlainString(),
                dong.donViGia(),
                dong.dienTichM2().toPlainString(),
                dong.khuVuc(),
                dong.viDo().toPlainString(),
                dong.kinhDo().toPlainString(),
                dong.ngayDang(),
                dong.anhDaiDienId() == null ? null : AnhHienThi.tu(dong.anhDaiDienId(), dong.anhDaiDienThuTu()),
                new GiaQuyDoiHienThi(
                        quyDoi.hienThi().toPlainString(),
                        QuyDoiGiaTimKiem.donViHienThi(dong.loaiGiaoDich(), cheDo),
                        GiaQuyDoiHienThi.CHU_THICH
                )
        );
    }

    // ------------------------------------------------------------------ đọc và kiểm tra tham số

    static BoLocTimKiem docBoLoc(Map<String, String[]> thamSo) {
        List<FieldErrorItem> loi = new ArrayList<>();
        for (Map.Entry<String, String[]> muc : thamSo.entrySet()) {
            if (!CAC_THAM_SO.contains(muc.getKey())) {
                loi.add(loiTruong(muc.getKey(), "Tham số không được hỗ trợ."));
            } else if (muc.getValue() != null && muc.getValue().length > 1) {
                loi.add(loiTruong(muc.getKey(), "Tham số không được lặp lại."));
            }
        }
        if (!loi.isEmpty()) {
            throw loiKiemTra(loi);
        }
        Function<String, String> doc = ten -> {
            String[] giaTri = thamSo.get(ten);
            return giaTri == null || giaTri.length == 0 ? null : giaTri[0];
        };

        List<String> tuKhoa = docTuKhoa(doc.apply("tuKhoa"), loi);
        LoaiGiaoDich loaiGiaoDich = docEnum(doc.apply("loaiGiaoDich"), "loaiGiaoDich", LoaiGiaoDich::tuGiaTri,
                "loaiGiaoDich chỉ nhận: ban, cho_thue.", loi);
        LoaiBds loaiBds = docEnum(doc.apply("loaiBds"), "loaiBds", LoaiBds::tuGiaTri,
                "loaiBds chỉ nhận: can_ho, nha_rieng, dat, phong_tro, khac.", loi);
        String khuVuc = docKhuVuc(doc.apply("khuVuc"), loi);
        KieuGia cheDoGia = Optional.ofNullable(docEnum(doc.apply("cheDoGia"), "cheDoGia", KieuGia::tuGiaTri,
                "cheDoGia chỉ nhận: tong, m2.", loi)).orElse(KieuGia.TONG);
        BigDecimal giaMin = docSo(doc.apply("giaMin"), "giaMin", SO_KHONG_AM, loi);
        BigDecimal giaMax = docSo(doc.apply("giaMax"), "giaMax", SO_KHONG_AM, loi);
        BigDecimal dienTichMin = docSo(doc.apply("dienTichMin"), "dienTichMin", SO_KHONG_AM, loi);
        BigDecimal dienTichMax = docSo(doc.apply("dienTichMax"), "dienTichMax", SO_KHONG_AM, loi);
        BigDecimal nam = docSo(doc.apply("nam"), "nam", SO_CO_DAU, loi);
        BigDecimal bac = docSo(doc.apply("bac"), "bac", SO_CO_DAU, loi);
        BigDecimal tay = docSo(doc.apply("tay"), "tay", SO_CO_DAU, loi);
        BigDecimal dong = docSo(doc.apply("dong"), "dong", SO_CO_DAU, loi);
        SapXepTin sapXep = Optional.ofNullable(docEnum(doc.apply("sapXep"), "sapXep", SapXepTin::tuGiaTri,
                "sapXep chỉ nhận: mac_dinh, moi_nhat.", loi)).orElse(SapXepTin.MAC_DINH);
        int trang = docSoNguyen(doc.apply("trang"), "trang", 0, 0, TRANG_TOI_DA, loi);
        int kichThuoc = docSoNguyen(doc.apply("kichThuoc"), "kichThuoc", KICH_THUOC_MAC_DINH, 1, KICH_THUOC_TOI_DA, loi);

        kiemTraKhoang(giaMin, giaMax, "giaMin", loi);
        kiemTraKhoang(dienTichMin, dienTichMax, "dienTichMin", loi);
        if ((giaMin != null || giaMax != null) && loaiGiaoDich == null && khongCoLoi(loi, "loaiGiaoDich")) {
            loi.add(loiTruong("loaiGiaoDich", "Bắt buộc khi có cận giá."));
        }
        kiemTraVung(thamSo, nam, bac, tay, dong, loi);
        if (!loi.isEmpty()) {
            throw loiKiemTra(loi);
        }
        return new BoLocTimKiem(tuKhoa, loaiGiaoDich, loaiBds, khuVuc, cheDoGia, giaMin, giaMax,
                dienTichMin, dienTichMax, nam, bac, tay, dong, sapXep, trang, kichThuoc);
    }

    private static String catHoacNull(String giaTri) {
        if (giaTri == null) {
            return null;
        }
        String cat = giaTri.strip();
        return cat.isEmpty() ? null : cat;
    }

    private static List<String> docTuKhoa(String tho, List<FieldErrorItem> loi) {
        String giaTri = catHoacNull(tho);
        if (giaTri == null) {
            return List.of();
        }
        if (giaTri.codePointCount(0, giaTri.length()) > DO_DAI_TU_KHOA_TOI_DA) {
            loi.add(loiTruong("tuKhoa", "tuKhoa tối đa " + DO_DAI_TU_KHOA_TOI_DA + " ký tự."));
            return List.of();
        }
        List<String> tu = ChuanHoaTimKiem.tachTu(ChuanHoaTimKiem.chuanHoa(giaTri));
        if (tu.isEmpty()) {
            loi.add(loiTruong("tuKhoa", "tuKhoa phải có ít nhất một chữ hoặc số."));
        } else if (tu.size() > SO_TU_KHOA_TOI_DA) {
            loi.add(loiTruong("tuKhoa", "tuKhoa tối đa " + SO_TU_KHOA_TOI_DA + " từ khác nhau."));
        }
        return tu;
    }

    private static String docKhuVuc(String tho, List<FieldErrorItem> loi) {
        String giaTri = catHoacNull(tho);
        if (giaTri == null) {
            return null;
        }
        if (giaTri.codePointCount(0, giaTri.length()) > DO_DAI_KHU_VUC_TOI_DA) {
            loi.add(loiTruong("khuVuc", "khuVuc tối đa " + DO_DAI_KHU_VUC_TOI_DA + " ký tự."));
            return null;
        }
        String chuan = ChuanHoaTimKiem.chuanHoaKhuVuc(giaTri);
        return chuan.isEmpty() ? null : chuan;
    }

    private static <T> T docEnum(
            String tho,
            String truong,
            Function<String, Optional<T>> tuGiaTri,
            String thongDiep,
            List<FieldErrorItem> loi
    ) {
        if (tho == null) {
            return null;
        }
        Optional<T> giaTri = tuGiaTri.apply(tho);
        if (giaTri.isEmpty()) {
            loi.add(loiTruong(truong, thongDiep));
            return null;
        }
        return giaTri.get();
    }

    private static BigDecimal docSo(String tho, String truong, Pattern mau, List<FieldErrorItem> loi) {
        if (tho == null) {
            return null;
        }
        if (tho.length() > DO_DAI_SO_TOI_DA || !mau.matcher(tho).matches()) {
            loi.add(loiTruong(truong, truong + " phải là số thập phân dạng chuỗi, dấu chấm, không số mũ."));
            return null;
        }
        return new BigDecimal(tho);
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
        loi.add(loiTruong(truong, truong + " phải là số nguyên từ " + toiThieu + " đến " + toiDa + "."));
        return macDinh;
    }

    private static void kiemTraKhoang(BigDecimal min, BigDecimal max, String truong, List<FieldErrorItem> loi) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            loi.add(loiTruong(truong, "Cận dưới không được lớn hơn cận trên."));
        }
    }

    private static void kiemTraVung(
            Map<String, String[]> thamSo,
            BigDecimal nam,
            BigDecimal bac,
            BigDecimal tay,
            BigDecimal dong,
            List<FieldErrorItem> loi
    ) {
        List<String> ten = List.of("nam", "bac", "tay", "dong");
        long coMat = ten.stream().filter(thamSo::containsKey).count();
        if (coMat == 0) {
            return;
        }
        if (coMat < ten.size()) {
            for (String t : ten) {
                if (!thamSo.containsKey(t)) {
                    loi.add(loiTruong(t, "Vùng bản đồ cần đủ nam, bac, tay, dong."));
                }
            }
            return;
        }
        if (nam == null || bac == null || tay == null || dong == null) {
            return;
        }
        BigDecimal chin = BigDecimal.valueOf(90);
        BigDecimal mot80 = BigDecimal.valueOf(180);
        for (Map.Entry<String, BigDecimal> vi : Map.of("nam", nam, "bac", bac).entrySet()) {
            if (vi.getValue().abs().compareTo(chin) > 0) {
                loi.add(loiTruong(vi.getKey(), "Vĩ độ phải trong [-90, 90]."));
            }
        }
        for (Map.Entry<String, BigDecimal> kinh : Map.of("tay", tay, "dong", dong).entrySet()) {
            if (kinh.getValue().abs().compareTo(mot80) > 0) {
                loi.add(loiTruong(kinh.getKey(), "Kinh độ phải trong [-180, 180]."));
            }
        }
        if (nam.compareTo(bac) > 0) {
            loi.add(loiTruong("nam", "nam không được lớn hơn bac."));
        }
        if (tay.compareTo(dong) > 0) {
            loi.add(loiTruong("tay", "tay không được lớn hơn dong; không hỗ trợ vùng cắt kinh tuyến 180."));
        }
    }

    private static boolean khongCoLoi(List<FieldErrorItem> loi, String truong) {
        return loi.stream().noneMatch(muc -> truong.equals(muc.truong()));
    }

    private static AuthValidationException loiKiemTra(List<FieldErrorItem> loi) {
        return new AuthValidationException(MA_LOI, THONG_DIEP_KHONG_HOP_LE, List.copyOf(loi));
    }

    private static FieldErrorItem loiTruong(String truong, String thongDiep) {
        return new FieldErrorItem(truong, MA_LOI, thongDiep);
    }
}
