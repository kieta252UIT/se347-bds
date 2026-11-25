package com.realestates.backend.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.BanDoPort;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport.TaiKhoanSession;
import com.realestates.backend.moderation.KiemDuyetPgTestSupport.TinCho;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Kiểm thử đồng thời cho kiểm duyệt trên PostgreSQL thật: ExecutorService + CountDownLatch làm chốt xuất phát,
 * không dùng sleep.
 */
@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class KiemDuyetConcurrencyTest {

    private static final Instant THOI_DIEM = KiemDuyetPgTestSupport.THOI_DIEM;
    private static final Instant THOI_DIEM_DUYET = KiemDuyetPgTestSupport.THOI_DIEM_DUYET;
    private static final String THANH_CONG = "THANH_CONG";

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
    private TinDangRepository tinDangRepository;

    @Autowired
    private KiemDuyetRepository kiemDuyetRepository;

    @Autowired
    private TinDangService tinDangService;

    @Autowired
    private CurrentPermissionAndCsrfService permissionAndCsrfService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    @Autowired
    private BanDoPort banDoPort;

    private KiemDuyetService kiemDuyetService;
    private KiemDuyetPgTestSupport ht;

    @BeforeEach
    void setUp() {
        kiemDuyetService = new KiemDuyetService(
                permissionAndCsrfService,
                kiemDuyetRepository,
                Clock.fixed(THOI_DIEM_DUYET, ZoneOffset.UTC)
        );
        QuanLyTinService quanLyTinService = new QuanLyTinService(
                permissionAndCsrfService,
                tinDangRepository,
                banDoPort,
                Clock.fixed(THOI_DIEM, ZoneOffset.UTC)
        );
        ht = new KiemDuyetPgTestSupport(
                jdbcTemplate,
                transactionManager,
                taiKhoanRepository,
                phienXacThucRepository,
                anhTinRepository,
                tinDangService,
                quanLyTinService,
                kiemDuyetService,
                sessionLifecyclePort
        );
    }

    @AfterEach
    void donDep() {
        ht.donDep();
    }

    @Test
    @DisplayName("CONCURRENCY-KD-01: hai kiểm duyệt viên cùng quyết định một phiên bản -> đúng 1 thành công, 1 nhật ký; bên thua nhận lỗi, thử lại với ETag mới nhận TRANG_THAI_KHONG_HOP_LE")
    void haiKiemDuyetVienCungQuyetDinh() throws Exception {
        for (boolean duyetCaHai : new boolean[]{true, false}) {
            TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
            TaiKhoanSession kdv1 = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
            TaiKhoanSession kdv2 = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
            TinCho tin = ht.taoTinMoiChoDuyet(chu);

            List<String> ketQua = ht.chayDongThoi(List.of(
                    nhan(() -> ht.pheDuyet(kdv1, tin.tinId(), tin.phienBanId(), "dong-thoi-1", "\"0\"")),
                    nhan(() -> duyetCaHai
                            ? ht.pheDuyet(kdv2, tin.tinId(), tin.phienBanId(), "dong-thoi-2", "\"0\"")
                            : ht.tuChoi(kdv2, tin.tinId(), tin.phienBanId(), "dong-thoi-2", "\"0\"", "Không đạt"))
            ));

            // Cả hai cùng giữ ETag "0": bên đến sau thấy phien_ban_cap_nhat đã đổi nên nhận 412 PHIEN_BAN_CU.
            assertThat(ketQua).containsExactlyInAnyOrder(THANH_CONG, "PHIEN_BAN_CU");
            assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')", tin.tinId()))
                    .isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tin.tinId()))
                    .isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet <> 'cho_kiem_duyet'", tin.phienBanId()))
                    .isEqualTo(1);
            assertThat(ht.phienBanCapNhat(tin.tinId())).isEqualTo(1L);

            String etagMoi = ht.etag(tin.tinId());
            assertThatThrownBy(() -> ht.pheDuyet(kdv1, tin.tinId(), tin.phienBanId(), "thu-lai-1", etagMoi))
                    .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
            assertThatThrownBy(() -> ht.pheDuyet(kdv2, tin.tinId(), tin.phienBanId(), "thu-lai-2", etagMoi))
                    .isInstanceOfSatisfying(ListingConflictException.class, ex -> assertThat(ex.getMaLoi()).isEqualTo("TRANG_THAI_KHONG_HOP_LE"));
            assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong IN ('phe_duyet', 'tu_choi')", tin.tinId()))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("CONCURRENCY-KD-02: nhiều yêu cầu cùng Idempotency-Key cùng lúc -> mọi yêu cầu trả cùng một kết quả, đúng 1 nhật ký, 1 yêu cầu ghi")
    void cungIdempotencyKeyDongThoi() throws Exception {
        TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinCho tinDuyet = ht.taoTinMoiChoDuyet(chu);
        TinCho tinTuChoi = ht.taoTinMoiChoDuyet(chu);

        List<TinGhiKetQua> cacKetQuaDuyet = ht.chayDongThoi(List.of(
                () -> ht.pheDuyet(kdv, tinDuyet.tinId(), tinDuyet.phienBanId(), "cung-khoa-duyet", "\"0\""),
                () -> ht.pheDuyet(kdv, tinDuyet.tinId(), tinDuyet.phienBanId(), "cung-khoa-duyet", "\"0\""),
                () -> ht.pheDuyet(kdv, tinDuyet.tinId(), tinDuyet.phienBanId(), "cung-khoa-duyet", "\"0\""),
                () -> ht.pheDuyet(kdv, tinDuyet.tinId(), tinDuyet.phienBanId(), "cung-khoa-duyet", "\"0\"")
        ));
        assertThat(cacKetQuaDuyet).hasSize(4);
        assertThat(cacKetQuaDuyet).allSatisfy(kq -> assertThat(kq).isEqualTo(cacKetQuaDuyet.get(0)));
        assertThat(cacKetQuaDuyet.get(0).trangThai()).isEqualTo("da_duyet");
        assertThat(cacKetQuaDuyet.get(0).phienBanCapNhat()).isEqualTo(1L);
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tinDuyet.tinId())).isEqualTo(1);
        assertThat(ht.dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id = ? AND loai_thao_tac = 'quyet_dinh'", tinDuyet.tinId())).isEqualTo(1);
        assertThat(ht.phienBanCapNhat(tinDuyet.tinId())).isEqualTo(1L);

        Callable<TinGhiKetQua> tuChoi = () -> ht.tuChoi(kdv, tinTuChoi.tinId(), tinTuChoi.phienBanId(), "cung-khoa-tu-choi", "\"0\"", "Không đạt");
        List<TinGhiKetQua> cacKetQuaTuChoi = ht.chayDongThoi(List.of(tuChoi, tuChoi, tuChoi));
        assertThat(cacKetQuaTuChoi).allSatisfy(kq -> assertThat(kq).isEqualTo(cacKetQuaTuChoi.get(0)));
        assertThat(cacKetQuaTuChoi.get(0).trangThai()).isEqualTo("bi_tu_choi");
        assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'tu_choi'", tinTuChoi.tinId())).isEqualTo(1);
        assertThat(ht.phienBanCapNhat(tinTuChoi.tinId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("CONCURRENCY-KD-03: phê duyệt đua với chủ tin xóa -> hoặc (duyệt rồi mới xóa) hoặc (xóa trước, duyệt với ETag cũ nhận PHIEN_BAN_CU); không bao giờ lệch trạng thái")
    void pheDuyetDuaVoiXoa() throws Exception {
        for (int lan = 0; lan < 5; lan++) {
            TaiKhoanSession chu = ht.taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);
            TaiKhoanSession kdv = ht.taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
            TinCho tin = ht.taoTinMoiChoDuyet(chu);
            UUID goiId = ht.taoGoi(tin.tinId(), "tam_dung_cho_duyet", null, "2 days");

            List<String> ketQua = ht.chayDongThoi(List.of(
                    nhan(() -> ht.pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "dua-duyet", "\"0\"")),
                    nhan(() -> ht.xoaTin(chu, tin.tinId(), "\"0\""))
            ));
            String ketQuaDuyet = ketQua.get(0);
            String ketQuaXoa = ketQua.get(1);

            boolean duyetTruoc = THANH_CONG.equals(ketQuaDuyet);
            if (duyetTruoc) {
                // Duyệt thắng: ETag "0" của chủ tin đã cũ; chủ tin tải lại ETag rồi xóa thành công.
                assertThat(ketQuaXoa).isEqualTo("PHIEN_BAN_CU");
                ht.xoaTin(chu, tin.tinId(), ht.etag(tin.tinId()));
            } else {
                // Xóa thắng: phien_ban_cap_nhat đã tăng nên ETag "0" của KDV đã cũ (412); tin không bị hồi sinh.
                assertThat(ketQuaDuyet).isEqualTo("PHIEN_BAN_CU");
                assertThat(ketQuaXoa).isEqualTo(THANH_CONG);
            }

            assertThat(ht.dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'da_xoa'", tin.tinId())).isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'phe_duyet'", tin.tinId()))
                    .isEqualTo(duyetTruoc ? 1 : 0);
            assertThat(ht.dem("SELECT count(*) FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = ?", tin.phienBanId(),
                    duyetTruoc ? "da_duyet" : "cho_kiem_duyet")).isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'xoa_tin'", tin.tinId())).isEqualTo(1);
            // Gói luôn kết thúc, không bao giờ còn chạy trên tin đã xóa.
            assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'ket_thuc_do_go_tin' AND het_han_luc IS NULL", goiId))
                    .isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.goi_day_tin WHERE tin_id = ? AND trang_thai = 'dang_hoat_dong'", tin.tinId())).isZero();
            assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'ket_thuc_do_xoa'", goiId)).isEqualTo(1);
            assertThat(ht.dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'chay_tiep'", goiId)).isEqualTo(duyetTruoc ? 1 : 0);
        }
    }

    /** Chuyển kết quả hoặc lỗi nghiệp vụ thành nhãn để so sánh. */
    private static <T> Callable<String> nhan(Callable<T> viec) {
        return () -> {
            try {
                viec.call();
                return THANH_CONG;
            } catch (ListingConflictException ex) {
                return ex.getMaLoi();
            } catch (ListingPreconditionException ex) {
                return ex.getMaLoi();
            }
        };
    }
}
