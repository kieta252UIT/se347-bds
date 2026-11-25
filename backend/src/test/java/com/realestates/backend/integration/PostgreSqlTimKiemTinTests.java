package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.search.TimKiemPgTestSupport;
import com.realestates.backend.search.TimKiemPgTestSupport.NoiDung;
import com.realestates.backend.search.TimKiemPgTestSupport.TinTao;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.domain.TimKiemRepository;
import com.realestates.backend.search.dto.KetQuaTimKiem;
import com.realestates.backend.search.dto.TinTomTat;
import com.realestates.backend.search.infrastructure.PostgreSqlTimKiemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

/** Tìm kiếm trên PostgreSQL thật theo hop-dong-tim-kiem-sprint-1 (BR-01, TK-02/05/06/09/10). */
@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlTimKiemTinTests {

    private static final Instant GOC = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TaiKhoanRepository taiKhoanRepository;

    @Autowired
    private PhienXacThucRepository phienXacThucRepository;

    @Autowired
    private AnhTinRepository anhTinRepository;

    @Autowired
    private TimKiemRepository timKiemRepository;

    @Autowired
    private TinDangService tinDangService;

    @Autowired
    private QuanLyTinService quanLyTinService;

    @Autowired
    private TimKiemTinService timKiemTinService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    private TimKiemPgTestSupport ht;
    /** Khu vực duy nhất cho mỗi test để cô lập khỏi dữ liệu khác; lọc khuVuc là chuỗi con nên vẫn khớp. */
    private final String kv = "TKTEST-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assertThat(timKiemRepository).isInstanceOf(PostgreSqlTimKiemRepository.class);
        ht = new TimKiemPgTestSupport(
                jdbcTemplate,
                transactionManager,
                taiKhoanRepository,
                phienXacThucRepository,
                anhTinRepository,
                tinDangService,
                quanLyTinService,
                sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    private KetQuaTimKiem tim(String... cap) {
        Map<String, String[]> thamSo = new LinkedHashMap<>();
        thamSo.put("khuVuc", new String[] {kv});
        thamSo.put("kichThuoc", new String[] {"50"});
        for (int i = 0; i < cap.length; i += 2) {
            thamSo.put(cap[i], new String[] {cap[i + 1]});
        }
        return timKiemTinService.timKiem(null, thamSo);
    }

    private static List<UUID> ids(List<TinTomTat> items) {
        return items.stream().map(TinTomTat::id).toList();
    }

    private static List<UUID> ids(TinTao... tins) {
        List<UUID> ketQua = new ArrayList<>();
        for (TinTao tin : tins) {
            ketQua.add(tin.tinId());
        }
        return ketQua;
    }

    private NoiDung mau() {
        return NoiDung.banMau(kv);
    }

    @Test
    @DisplayName("BR-01: chỉ tin da_duyet của chủ hoat_dong; ẩn tin chờ duyệt bản sửa, chủ bị khóa, chờ duyệt lần đầu, bị từ chối, bị gỡ")
    void chiHienTinCongKhai() {
        TinTao hien = ht.taoTinDaDuyet(mau().voiTieuDe("Hiện"), GOC.plusSeconds(1));
        TinTao choSua = ht.taoTinDaDuyet(mau().voiTieuDe("Chờ sửa"), GOC.plusSeconds(2));
        ht.suaTin(choSua, mau().voiTieuDe("Bản sửa"));
        TinTao chuKhoa = ht.taoTinDaDuyet(mau().voiTieuDe("Chủ khóa"), GOC.plusSeconds(3));
        ht.khoaChu(chuKhoa);
        ht.taoTinChoDuyet(mau().voiTieuDe("Chờ duyệt"));
        TinTao tuChoi = ht.taoTinChoDuyet(mau().voiTieuDe("Từ chối"));
        ht.tuChoiPhienBanHienTai(tuChoi.tinId());
        TinTao biGo = ht.taoTinDaDuyet(mau().voiTieuDe("Bị gỡ"), GOC.plusSeconds(4));
        ht.goViPham(biGo.tinId());
        // Tin bị ẩn cũng không được lọt vào khu tài trợ.
        ht.taoGoiDangHoatDong(chuKhoa.tinId());

        KetQuaTimKiem kq = tim();

        assertThat(ids(kq.tuNhien().noiDung())).containsExactly(hien.tinId());
        assertThat(kq.tuNhien().tongSo()).isEqualTo(1);
        assertThat(kq.taiTro()).isEmpty();
        TinTomTat tin = kq.tuNhien().noiDung().get(0);
        assertThat(tin.phienBanId()).isEqualTo(hien.phienBanId());
        assertThat(tin.anhDaiDien().id()).isEqualTo(hien.anhId());
        assertThat(tin.anhDaiDien().duongDanDoc()).isEqualTo("/api/images/" + hien.anhId() + "/content");
        assertThat(tin.viDo()).isEqualTo("10.7772");
        assertThat(tin.giaQuyDoiHienThi().giaTri()).isEqualTo("2000000000.00");
    }

    @Test
    @DisplayName("tuKhoa: full-text không dấu/hoa thường, AND các từ; mac_dinh xếp theo rank (tiêu đề > vị trí), moi_nhat theo ngày đăng")
    void tuKhoaVaXepHang() {
        TinTao tieuDe = ht.taoTinDaDuyet(mau().voiTieuDe("Biệt thự Hoa Hồng"), GOC.plusSeconds(1));
        TinTao viTri = ht.taoTinDaDuyet(
                new NoiDung("Nhà phố", "ban", "nha_rieng", "2000000000", "vnd_tong", "50", kv + " Hoa"),
                GOC.plusSeconds(2)
        );
        TinTao khongKhop = ht.taoTinDaDuyet(mau().voiTieuDe("Đất nền"), GOC.plusSeconds(3));

        assertThat(ids(tim("tuKhoa", "HOA").tuNhien().noiDung())).containsExactly(tieuDe.tinId(), viTri.tinId());
        assertThat(ids(tim("tuKhoa", "hoa", "sapXep", "moi_nhat").tuNhien().noiDung()))
                .containsExactly(viTri.tinId(), tieuDe.tinId());
        assertThat(ids(tim("tuKhoa", "hoa hong").tuNhien().noiDung())).containsExactly(tieuDe.tinId());
        // Mô tả (trọng số C) cũng được tìm, không dấu.
        assertThat(ids(tim("tuKhoa", "TRET").tuNhien().noiDung()))
                .containsExactlyInAnyOrder(tieuDe.tinId(), viTri.tinId(), khongKhop.tinId());
        assertThat(tim("tuKhoa", "hoa", "sapXep", "moi_nhat").tuNhien().tongSo()).isEqualTo(2);
    }

    @Test
    @DisplayName("khuVuc: chuỗi con không dấu/hoa thường trong khu_vuc nối dia_chi; mac_dinh không từ khóa xếp ngày đăng giảm")
    void khuVucChuoiCon() {
        TinTao cu = ht.taoTinDaDuyet(mau(), GOC.plusSeconds(1));
        TinTao moi = ht.taoTinDaDuyet(mau(), GOC.plusSeconds(2));

        Map<String, String[]> thamSo = new LinkedHashMap<>();
        thamSo.put("khuVuc", new String[] {"LY TU TRONG"});
        thamSo.put("kichThuoc", new String[] {"50"});
        List<UUID> theoDiaChi = ids(timKiemTinService.timKiem(null, thamSo).tuNhien().noiDung());
        assertThat(theoDiaChi).contains(cu.tinId(), moi.tinId());

        assertThat(ids(tim().tuNhien().noiDung())).containsExactly(moi.tinId(), cu.tinId());
        assertThat(ids(tim("khuVuc", kv + " khong co").tuNhien().noiDung())).isEmpty();
    }

    @Test
    @DisplayName("khuVuc mục 4.1: dấu câu và nhiều khoảng trắng ở cả dữ liệu lẫn input đều quy về một khoảng trắng")
    void khuVucBoDauCauVaKhoangTrang() {
        String dau = kv + " TP. Thủ   Đức";
        TinTao tin = ht.taoTinDaDuyet(new NoiDung("Nhà phố", "ban", "nha_rieng", "2000000000", "vnd_tong", "50", dau),
                GOC.plusSeconds(1));

        for (String input : new String[] {"tp thu duc", "TP.Thủ-Đức", "  tp,,  thu...duc  "}) {
            assertThat(ids(tim("khuVuc", input).tuNhien().noiDung())).as(input).contains(tin.tinId());
        }
        assertThat(ids(tim("khuVuc", kv + " tp thu duc").tuNhien().noiDung())).containsExactly(tin.tinId());
        assertThat(ids(tim("khuVuc", kv + " tpthu duc").tuNhien().noiDung())).isEmpty();
    }

    @Test
    @DisplayName("TK-06: lọc giá bằng phân số chính xác N/D, không làm tròn trước; 100.000.000 / 3 m2 không lọt cận 33.333.333,33")
    void locGiaChinhXacTaiBien() {
        TinTao tin = ht.taoTinDaDuyet(mau().voiBan("dat", "100000000", "vnd_tong", "3"), GOC.plusSeconds(1));
        TinTao thueNam = ht.taoTinDaDuyet(mau().voiThue("can_ho", "120000001", "vnd_nam", "40"), GOC.plusSeconds(2));

        assertThat(ids(tim("loaiGiaoDich", "ban", "cheDoGia", "m2", "giaMax", "33333333.33").tuNhien().noiDung())).isEmpty();
        assertThat(ids(tim("loaiGiaoDich", "ban", "cheDoGia", "m2", "giaMax", "33333333.34").tuNhien().noiDung()))
                .containsExactly(tin.tinId());
        assertThat(ids(tim("loaiGiaoDich", "ban", "cheDoGia", "m2", "giaMin", "33333333.33").tuNhien().noiDung()))
                .containsExactly(tin.tinId());
        assertThat(ids(tim("loaiGiaoDich", "ban", "giaMin", "100000000", "giaMax", "100000000").tuNhien().noiDung()))
                .containsExactly(tin.tinId());
        // 120.000.001 / 12 = 10.000.000,083 > 10.000.000 dù hiển thị làm tròn là 10000000.08.
        assertThat(ids(tim("loaiGiaoDich", "cho_thue", "giaMax", "10000000").tuNhien().noiDung())).isEmpty();
        assertThat(ids(tim("loaiGiaoDich", "cho_thue", "giaMax", "10000000.09").tuNhien().noiDung()))
                .containsExactly(thueNam.tinId());
        assertThat(tim("loaiGiaoDich", "cho_thue").tuNhien().noiDung().get(0).giaQuyDoiHienThi().giaTri())
                .isEqualTo("10000000.08");
    }

    @Test
    @DisplayName("Lọc loaiGiaoDich, loaiBds, diện tích (gồm biên) và vùng bản đồ")
    void cacBoLocKhac() {
        TinTao canHo = ht.taoTinDaDuyet(mau().voiBan("can_ho", "2000000000", "vnd_tong", "50"), GOC.plusSeconds(1));
        TinTao thue = ht.taoTinDaDuyet(mau().voiThue("phong_tro", "5000000", "vnd_thang", "25"), GOC.plusSeconds(2));

        assertThat(ids(tim("loaiGiaoDich", "cho_thue").tuNhien().noiDung())).containsExactly(thue.tinId());
        assertThat(ids(tim("loaiBds", "can_ho").tuNhien().noiDung())).containsExactly(canHo.tinId());
        assertThat(ids(tim("dienTichMin", "25", "dienTichMax", "25").tuNhien().noiDung())).containsExactly(thue.tinId());
        assertThat(ids(tim("nam", "10.7", "bac", "10.8", "tay", "106.7", "dong", "106.71").tuNhien().noiDung()))
                .containsExactlyInAnyOrder(canHo.tinId(), thue.tinId());
        assertThat(ids(tim("nam", "10.78", "bac", "10.8", "tay", "106.7", "dong", "106.71").tuNhien().noiDung())).isEmpty();
    }

    @Test
    @DisplayName("Phân trang trang/kichThuoc ổn định khi trùng ngày đăng: không trùng, không sót; tongSo đúng")
    void phanTrang() {
        List<UUID> tatCa = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tatCa.add(ht.taoTinDaDuyet(mau(), GOC).tinId());
        }
        List<UUID> quaTrang = new ArrayList<>();
        for (int trang = 0; trang < 3; trang++) {
            KetQuaTimKiem kq = tim("kichThuoc", "2", "trang", String.valueOf(trang));
            assertThat(kq.tuNhien().tongSo()).isEqualTo(5);
            quaTrang.addAll(ids(kq.tuNhien().noiDung()));
        }
        // PostgreSQL so uuid theo byte không dấu = thứ tự chuỗi hex, khác UUID.compareTo (long có dấu).
        assertThat(quaTrang).containsExactlyElementsOf(tatCa.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList());
        assertThat(tim("kichThuoc", "2", "trang", "100000").tuNhien().noiDung()).isEmpty();
    }

    @Test
    @DisplayName("TK-10: tự nhiên loại TOÀN BỘ tin có gói còn hiệu lực; tài trợ tối đa 5, lấy mẫu ngẫu nhiên; gói hết hạn thực tế về tự nhiên")
    void taiTro() {
        List<UUID> coGoi = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            TinTao tin = ht.taoTinDaDuyet(mau(), GOC.plusSeconds(i));
            ht.taoGoiDangHoatDong(tin.tinId());
            coGoi.add(tin.tinId());
        }
        TinTao hetHan = ht.taoTinDaDuyet(mau(), GOC.plusSeconds(10));
        ht.taoGoi(hetHan.tinId(), "dang_hoat_dong", Instant.now().minusSeconds(60), "0 seconds");
        TinTao tamDung = ht.taoTinDaDuyet(mau(), GOC.plusSeconds(11));
        ht.taoGoi(tamDung.tinId(), "tam_dung_cho_duyet", null, "1 day");

        Set<UUID> daThay = new HashSet<>();
        for (int lan = 0; lan < 30; lan++) {
            KetQuaTimKiem kq = tim();
            assertThat(kq.taiTro()).hasSize(5);
            assertThat(ids(kq.taiTro())).doesNotHaveDuplicates().allMatch(coGoi::contains);
            assertThat(ids(kq.tuNhien().noiDung())).containsExactly(tamDung.tinId(), hetHan.tinId());
            assertThat(kq.tuNhien().tongSo()).isEqualTo(2);
            assertThat(kq.thoiDiemTruyVan()).isNotNull();
            daThay.addAll(ids(kq.taiTro()));
        }
        // Chọn cố định 5 tin thì không bao giờ thấy tin thứ 6, 7; xác suất ngẫu nhiên mà vẫn vậy sau 30 lần ≈ 0.
        assertThat(daThay).hasSizeGreaterThan(5);
    }

    @Test
    @DisplayName("Tài trợ tôn trọng bộ lọc và không xuất hiện ở trang tự nhiên nào")
    void taiTroTheoBoLoc() {
        TinTao taiTroCanHo = ht.taoTinDaDuyet(mau().voiLoaiBds("can_ho"), GOC.plusSeconds(1));
        ht.taoGoiDangHoatDong(taiTroCanHo.tinId());
        TinTao taiTroDat = ht.taoTinDaDuyet(mau().voiLoaiBds("dat"), GOC.plusSeconds(2));
        ht.taoGoiDangHoatDong(taiTroDat.tinId());
        TinTao tuNhien = ht.taoTinDaDuyet(mau().voiLoaiBds("can_ho"), GOC.plusSeconds(3));

        KetQuaTimKiem kq = tim("loaiBds", "can_ho");
        assertThat(ids(kq.taiTro())).containsExactly(taiTroCanHo.tinId());
        assertThat(ids(kq.tuNhien().noiDung())).containsExactly(tuNhien.tinId());
        assertThat(ids(tim("loaiBds", "can_ho", "kichThuoc", "1", "trang", "1").tuNhien().noiDung())).isEmpty();
        assertThat(ids(kq.tuNhien().noiDung())).doesNotContainAnyElementsOf(ids(taiTroCanHo, taiTroDat));
    }
}
