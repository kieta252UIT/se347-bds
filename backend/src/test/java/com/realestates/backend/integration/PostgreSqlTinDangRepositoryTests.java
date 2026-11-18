package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AccountLockedException;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.AnhStoragePort;
import com.realestates.backend.listing.application.AnhTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.domain.DonViGiaTin;
import com.realestates.backend.listing.domain.HanMucThangHelper;
import com.realestates.backend.listing.domain.KiemDuyetSoBoTin;
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.listing.domain.NoiDungTinDauVao;
import com.realestates.backend.listing.domain.QuyenDocAnh;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.domain.TinDangRepository.DuLieuTaoTin;
import com.realestates.backend.listing.domain.TinDangRepository.LoaiLoiTaoTin;
import com.realestates.backend.listing.domain.TinDangRepository.LoiNghiepVuTaoTinException;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.infrastructure.MacDinhBanDoAdapter;
import com.realestates.backend.listing.infrastructure.PostgreSqlAnhTinRepository;
import com.realestates.backend.listing.infrastructure.PostgreSqlTinDangRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlTinDangRepositoryTests {

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
    private TinDangService tinDangService;

    @Autowired
    private CurrentPermissionAndCsrfService permissionAndCsrfService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    @Test
    @DisplayName("PostgreSQL thật: tạo tin ghi atomically 6 bảng (kể cả khi chưa có dòng hạn mức), phân quyền ảnh và replay/xung đột Idempotency-Key")
    void taoTinMoiDayDu6BangVaIdempotency() {
        assertThat(tinDangRepository).isInstanceOf(PostgreSqlTinDangRepository.class);
        assertThat(anhTinRepository).isInstanceOf(PostgreSqlAnhTinRepository.class);

        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TaiKhoanSession khach = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);

        try {
            UUID anh1 = luuAnhTamPg(chu.taiKhoan().getId(), "k1-goc.png", "k1-hienthi.png");
            UUID anh2 = luuAnhTamPg(chu.taiKhoan().getId(), "k2-goc.png", "k2-hienthi.png");

            Map<String, Object> payload = duLieuTinHopLe(List.of(anh1.toString(), anh2.toString()));
            TinGhiKetQua ketQua = tinDangService.dangTin(chu.rawCookie(), "pg-idem-create-1", payload);

            assertThat(ketQua.trangThai()).isEqualTo("cho_kiem_duyet");
            assertThat(ketQua.phienBanCapNhat()).isZero();

            Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                    "SELECT chu_id, trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat "
                            + "FROM se214.tin_bat_dong_san WHERE id = ?",
                    ketQua.tinId()
            );
            assertThat(tinRow.get("chu_id")).isEqualTo(chu.taiKhoan().getId());
            assertThat(tinRow.get("trang_thai")).isEqualTo("cho_kiem_duyet");
            assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(ketQua.phienBanHienTaiId());
            assertThat(tinRow.get("phien_ban_da_duyet_id")).isNull();
            assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isZero();

            Map<String, Object> pbRow = jdbcTemplate.queryForMap(
                    "SELECT so_phien_ban, ket_qua_so_bo, ket_qua_kiem_duyet FROM se214.phien_ban_tin WHERE id = ?",
                    ketQua.phienBanHienTaiId()
            );
            assertThat(((Number) pbRow.get("so_phien_ban")).intValue()).isEqualTo(1);
            assertThat(pbRow.get("ket_qua_so_bo")).isEqualTo("dat");
            assertThat(pbRow.get("ket_qua_kiem_duyet")).isEqualTo("cho_kiem_duyet");

            List<Map<String, Object>> anhRows = jdbcTemplate.queryForList(
                    "SELECT id, trang_thai, thu_tu, phien_ban_tin_id FROM se214.anh_tin "
                            + "WHERE phien_ban_tin_id = ? ORDER BY thu_tu ASC",
                    ketQua.phienBanHienTaiId()
            );
            assertThat(anhRows).hasSize(2);
            assertThat(anhRows.get(0).get("id")).isEqualTo(anh1);
            assertThat(anhRows.get(0).get("trang_thai")).isEqualTo("da_gan");
            assertThat(((Number) anhRows.get(0).get("thu_tu")).intValue()).isEqualTo(1);
            assertThat(anhRows.get(1).get("id")).isEqualTo(anh2);
            assertThat(((Number) anhRows.get(1).get("thu_tu")).intValue()).isEqualTo(2);

            LocalDate thangHienTaiVn = HanMucThangHelper.dauThangVietNam(Instant.now());
            assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), thangHienTaiVn)).isEqualTo(1);

            Integer soYeuCauGhi = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND ma_y_dinh = ?",
                    Integer.class,
                    chu.taiKhoan().getId(),
                    "pg-idem-create-1"
            );
            assertThat(soYeuCauGhi).isEqualTo(1);

            Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                    "SELECT hanh_dong, jsonb_typeof(du_lieu_truoc_sau) AS json_type FROM se214.nhat_ky_kiem_toan WHERE tin_id = ?",
                    ketQua.tinId()
            );
            assertThat(auditRow.get("hanh_dong")).isEqualTo("dang_tin");
            assertThat(auditRow.get("json_type")).isEqualTo("object");

            // Quyền đọc ảnh sau gắn tin trong PostgreSQL thật: chủ + kiểm duyệt viên đọc được, người khác & khách bị từ chối
            AnhTinTruyCap anhSauGan = anhTinRepository.timAnhDeDoc(anh1).orElseThrow();
            assertThat(anhSauGan.trangThaiTin()).isEqualTo("cho_kiem_duyet");
            assertThat(anhSauGan.phienBanHienTaiId()).isEqualTo(ketQua.phienBanHienTaiId());
            assertThat(anhSauGan.chuTinHoatDong()).isTrue();
            assertThat(QuyenDocAnh.duocPhepDoc(anhSauGan, toPrincipal(chu))).isTrue();
            assertThat(QuyenDocAnh.duocPhepDoc(anhSauGan, toPrincipal(kdv))).isTrue();
            assertThat(QuyenDocAnh.duocPhepDoc(anhSauGan, toPrincipal(khach))).isFalse();
            assertThat(QuyenDocAnh.duocPhepDoc(anhSauGan, null)).isFalse();

            // Replay cùng Idempotency-Key + payload giống hệt -> trả nguyên TinGhiKetQua và giữ nguyên hạn mức
            TinGhiKetQua replay = tinDangService.dangTin(chu.rawCookie(), "pg-idem-create-1", payload);
            assertThat(replay).isEqualTo(ketQua);
            assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), thangHienTaiVn)).isEqualTo(1);

            // Cùng Idempotency-Key nhưng đổi payload -> 409 MA_Y_DINH_KHAC_NOI_DUNG
            Map<String, Object> payloadKhac = new LinkedHashMap<>(payload);
            payloadKhac.put("tieuDe", "Tiêu đề khác gây xung đột");
            assertThatThrownBy(() -> tinDangService.dangTin(chu.rawCookie(), "pg-idem-create-1", payloadKhac))
                    .isInstanceOf(ListingConflictException.class)
                    .satisfies(ex -> assertThat(((ListingConflictException) ex).getMaLoi())
                            .isEqualTo(ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG));
        } finally {
            donDepTaiKhoanPg(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId(), khach.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: ranh giới tháng Asia/Ho_Chi_Minh và rollback toàn bộ giao dịch khi ảnh/khóa lỗi (TS1-025, TS1-028, TS1-029)")
    void hanMucMuiGioVietNamVaRollbackToanPhan() {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession nguoiKhac = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);

        try {
            // 1. Rollback khi 1 ảnh trong danh sách thuộc về người khác: không được tạo tin/hạn mức, ảnh hợp lệ giữ nguyên 'tam'
            UUID anhCuaChu = luuAnhTamPg(chu.taiKhoan().getId(), "rb-chu.png", "rb-chu-d.png");
            UUID anhNguoiKhac = luuAnhTamPg(nguoiKhac.taiKhoan().getId(), "rb-other.png", "rb-other-d.png");
            TinDangService novService = new TinDangService(
                    permissionAndCsrfService,
                    tinDangRepository,
                    new MacDinhBanDoAdapter("sandbox"),
                    Clock.fixed(Instant.parse("2026-11-30T16:59:59Z"), ZoneOffset.UTC)
            );

            assertThatThrownBy(() -> novService.dangTin(
                    chu.rawCookie(),
                    "pg-rb-1",
                    duLieuTinHopLe(List.of(anhCuaChu.toString(), anhNguoiKhac.toString()))
            )).isInstanceOf(AuthValidationException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM se214.tin_bat_dong_san WHERE chu_id = ?",
                    Integer.class,
                    chu.taiKhoan().getId()
            )).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM se214.han_muc_thang WHERE tai_khoan_id = ?",
                    Integer.class,
                    chu.taiKhoan().getId()
            )).isZero();
            assertThat(anhTinRepository.timAnhDeDoc(anhCuaChu).orElseThrow().trangThaiAnh()).isEqualTo("tam");

            // 2. Đăng 3 tin tại 23:59:59 30/11/2026 (+07:00), tin thứ 4 bị chặn HAN_MUC_THANG
            novService.dangTin(chu.rawCookie(), "nov-1", duLieuTinHopLe(List.of(anhCuaChu.toString())));
            for (int i = 2; i <= 3; i++) {
                UUID anh = luuAnhTamPg(chu.taiKhoan().getId(), "nov-" + i + ".png", "nov-" + i + "-d.png");
                novService.dangTin(chu.rawCookie(), "nov-" + i, duLieuTinHopLe(List.of(anh.toString())));
            }
            assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), LocalDate.of(2026, 11, 1)))
                    .isEqualTo(3);

            UUID anhThu4 = luuAnhTamPg(chu.taiKhoan().getId(), "nov-4.png", "nov-4-d.png");
            assertThatThrownBy(() -> novService.dangTin(
                    chu.rawCookie(),
                    "nov-4",
                    duLieuTinHopLe(List.of(anhThu4.toString()))
            )).isInstanceOf(ListingConflictException.class)
                    .satisfies(ex -> assertThat(((ListingConflictException) ex).getMaLoi())
                            .isEqualTo(ListingConflictException.MA_LOI_HAN_MUC_THANG));

            // 3. Sau 1 giây (00:00:00 01/12/2026 +07:00) -> tháng mới thành công
            TinDangService decService = new TinDangService(
                    permissionAndCsrfService,
                    tinDangRepository,
                    new MacDinhBanDoAdapter("sandbox"),
                    Clock.fixed(Instant.parse("2026-11-30T17:00:00Z"), ZoneOffset.UTC)
            );
            decService.dangTin(chu.rawCookie(), "dec-1", duLieuTinHopLe(List.of(anhThu4.toString())));
            assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), LocalDate.of(2026, 12, 1)))
                    .isEqualTo(1);

            // 4. Khóa tài khoản giữa chừng (TS1-029) -> AccountLockedException và không ghi bất kỳ dòng nào
            UUID anhBiKhoa = luuAnhTamPg(chu.taiKhoan().getId(), "locked.png", "locked-d.png");
            jdbcTemplate.update("UPDATE se214.tai_khoan SET trang_thai = 'bi_khoa' WHERE id = ?", chu.taiKhoan().getId());
            assertThatThrownBy(() -> decService.dangTin(
                    chu.rawCookie(),
                    "dec-locked",
                    duLieuTinHopLe(List.of(anhBiKhoa.toString()))
            )).isInstanceOf(AccountLockedException.class);
            assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), LocalDate.of(2026, 12, 1)))
                    .isEqualTo(1);
        } finally {
            donDepTaiKhoanPg(List.of(chu.taiKhoan().getId(), nguoiKhac.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: tranh chấp đồng thời hạn mức khi chưa có bộ đếm, khóa hàng FOR UPDATE đa kết nối và trùng Idempotency-Key (TS1-026, TS1-027)")
    void tranhChapDongThoiHanMucVaIdempotency() throws Exception {
        TaiKhoanSession taiKhoanRace = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession taiKhoanDirectDb = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession taiKhoanIdem = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            // A. 4 luồng gọi TinDangService.dangTin đồng thời khi chưa có dòng han_muc_thang nào -> đúng 3 thành công, 1 trả HAN_MUC_THANG
            List<UUID> anhRace = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                anhRace.add(luuAnhTamPg(taiKhoanRace.taiKhoan().getId(), "race-" + i + ".png", "race-" + i + "-d.png"));
            }

            CountDownLatch sanSangA = new CountDownLatch(4);
            CountDownLatch batDauA = new CountDownLatch(1);
            List<Future<Object>> ketQuaA = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                ketQuaA.add(pool.submit(() -> {
                    sanSangA.countDown();
                    batDauA.await(5, TimeUnit.SECONDS);
                    try {
                        return tinDangService.dangTin(
                                taiKhoanRace.rawCookie(),
                                "race-quota-" + idx,
                                duLieuTinHopLe(List.of(anhRace.get(idx).toString()))
                        );
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            assertThat(sanSangA.await(5, TimeUnit.SECONDS)).isTrue();
            batDauA.countDown();

            int thanhCongA = 0;
            int biChanHanMucA = 0;
            for (Future<Object> f : ketQuaA) {
                Object val = f.get(15, TimeUnit.SECONDS);
                if (val instanceof TinGhiKetQua) {
                    thanhCongA++;
                } else if (val instanceof ListingConflictException lce
                        && ListingConflictException.MA_LOI_HAN_MUC_THANG.equals(lce.getMaLoi())) {
                    biChanHanMucA++;
                } else {
                    throw new AssertionError("Kết quả bất ngờ trong cuộc đua A: " + val);
                }
            }
            assertThat(thanhCongA).isEqualTo(3);
            assertThat(biChanHanMucA).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM se214.tin_bat_dong_san WHERE chu_id = ?",
                    Integer.class,
                    taiKhoanRace.taiKhoan().getId()
            )).isEqualTo(3);

            // B. Bỏ qua khóa JVM, kiểm chứng khóa hàng PostgreSQL (INSERT ... ON CONFLICT + FOR UPDATE) khi 2 kết nối DB đua từ so_tin_moi = 2
            LocalDate thangVn = HanMucThangHelper.dauThangVietNam(Instant.now());
            jdbcTemplate.update(
                    "INSERT INTO se214.han_muc_thang (tai_khoan_id, thang, so_tin_moi) VALUES (?, ?, 2)",
                    taiKhoanDirectDb.taiKhoan().getId(),
                    Date.valueOf(thangVn)
            );
            UUID anhDirect1 = luuAnhTamPg(taiKhoanDirectDb.taiKhoan().getId(), "dir-1.png", "dir-1-d.png");
            UUID anhDirect2 = luuAnhTamPg(taiKhoanDirectDb.taiKhoan().getId(), "dir-2.png", "dir-2-d.png");

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            CountDownLatch sanSangB = new CountDownLatch(2);
            CountDownLatch batDauB = new CountDownLatch(1);
            List<Future<Object>> ketQuaB = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                UUID anhId = i == 0 ? anhDirect1 : anhDirect2;
                String maYDinh = "dir-idem-" + i;
                ketQuaB.add(pool.submit(() -> {
                    sanSangB.countDown();
                    batDauB.await(5, TimeUnit.SECONDS);
                    try {
                        return txTemplate.execute(status -> tinDangRepository.taoTinMoiTrongTransaction(
                                new DuLieuTaoTin(
                                        taiKhoanDirectDb.taiKhoan().getId(),
                                        noiDungHopLe(),
                                        List.of(anhId),
                                        KiemDuyetSoBoTin.KET_QUA_DAT,
                                        thangVn,
                                        Instant.now(),
                                        maYDinh,
                                        "hash-" + maYDinh,
                                        "{}"
                                )
                        ));
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            assertThat(sanSangB.await(5, TimeUnit.SECONDS)).isTrue();
            batDauB.countDown();

            int thanhCongB = 0;
            int biChanHanMucB = 0;
            for (Future<Object> f : ketQuaB) {
                Object val = f.get(15, TimeUnit.SECONDS);
                if (val instanceof TinGhiKetQua) {
                    thanhCongB++;
                } else if (val instanceof LoiNghiepVuTaoTinException ex
                        && ex.getLoaiLoi() == LoaiLoiTaoTin.VUOT_HAN_MUC_THANG) {
                    biChanHanMucB++;
                } else {
                    throw new AssertionError("Kết quả bất ngờ trong cuộc đua PG trực tiếp: " + val);
                }
            }
            assertThat(thanhCongB).isEqualTo(1);
            assertThat(biChanHanMucB).isEqualTo(1);
            assertThat(tinDangRepository.docSoTinMoiTrongThang(taiKhoanDirectDb.taiKhoan().getId(), thangVn)).isEqualTo(3);

            // C. Hai luồng gửi đồng thời CÙNG Idempotency-Key -> cả hai trả TinGhiKetQua y hệt nhau và chỉ ghi 1 tin
            UUID anhIdem = luuAnhTamPg(taiKhoanIdem.taiKhoan().getId(), "idem-race.png", "idem-race-d.png");
            Map<String, Object> idemPayload = duLieuTinHopLe(List.of(anhIdem.toString()));
            CountDownLatch sanSangC = new CountDownLatch(2);
            CountDownLatch batDauC = new CountDownLatch(1);
            List<Future<TinGhiKetQua>> ketQuaC = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                ketQuaC.add(pool.submit(() -> {
                    sanSangC.countDown();
                    batDauC.await(5, TimeUnit.SECONDS);
                    return tinDangService.dangTin(taiKhoanIdem.rawCookie(), "same-key-race", idemPayload);
                }));
            }
            assertThat(sanSangC.await(5, TimeUnit.SECONDS)).isTrue();
            batDauC.countDown();

            TinGhiKetQua r1 = ketQuaC.get(0).get(15, TimeUnit.SECONDS);
            TinGhiKetQua r2 = ketQuaC.get(1).get(15, TimeUnit.SECONDS);
            assertThat(r1).isEqualTo(r2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM se214.tin_bat_dong_san WHERE chu_id = ?",
                    Integer.class,
                    taiKhoanIdem.taiKhoan().getId()
            )).isEqualTo(1);
            assertThat(tinDangRepository.docSoTinMoiTrongThang(taiKhoanIdem.taiKhoan().getId(), thangVn)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            donDepTaiKhoanPg(List.of(
                    taiKhoanRace.taiKhoan().getId(),
                    taiKhoanDirectDb.taiKhoan().getId(),
                    taiKhoanIdem.taiKhoan().getId()
            ));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: dọn ảnh tạm quá hạn theo thứ tựStorage trước DB, bảo vệ shared key và retry khi Storage 5xx")
    void donDepAnhTamQuaHanTrenPostgresThat() {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        KhoAnhPgGia storageGia = new KhoAnhPgGia();
        AnhTinService cleanupService = new AnhTinService(permissionAndCsrfService, anhTinRepository, storageGia, 1440L);

        try {
            UUID anhHetHanId = UUID.randomUUID();
            UUID anhSharedHetHanId = UUID.randomUUID();
            UUID anhSharedConSongId = UUID.randomUUID();

            String keyUniqueGoc = anhHetHanId + "-goc.png";
            String keyUniqueHienThi = anhHetHanId + "-display.png";
            String keySharedGoc = "shared/postgres-goc.png";
            String keySharedHetHanHienThi = anhSharedHetHanId + "-display.png";
            String keySharedSongHienThi = anhSharedConSongId + "-display.png";

            storageGia.objects.addAll(List.of(
                    keyUniqueGoc, keyUniqueHienThi, keySharedGoc, keySharedHetHanHienThi, keySharedSongHienThi
            ));

            anhTinRepository.luuAnhTam(anhHetHanId, chu.taiKhoan().getId(), keyUniqueGoc, keyUniqueHienThi, DinhDangAnh.PNG, 500L);
            anhTinRepository.luuAnhTam(anhSharedHetHanId, chu.taiKhoan().getId(), keySharedGoc, keySharedHetHanHienThi, DinhDangAnh.PNG, 500L);
            anhTinRepository.luuAnhTam(anhSharedConSongId, chu.taiKhoan().getId(), keySharedGoc, keySharedSongHienThi, DinhDangAnh.PNG, 500L);

            Instant bayGio = Instant.now();
            Timestamp quaHanSql = Timestamp.from(bayGio.minusSeconds(25L * 3600));
            jdbcTemplate.update(
                    "UPDATE se214.anh_tin SET tao_luc = ? WHERE id IN (?, ?)",
                    quaHanSql, anhHetHanId, anhSharedHetHanId
            );

            // Storage lỗi khi xóa -> ảnh được đánh dấu cho_don nhưng vẫn giữ trong Postgres để retry
            storageGia.failDelete = true;
            assertThat(cleanupService.donDepAnhTamQuaHan(bayGio)).isZero();
            assertThat(anhTinRepository.timAnhDeDoc(anhHetHanId).orElseThrow().trangThaiAnh()).isEqualTo("cho_don");
            assertThat(anhTinRepository.timAnhDeDoc(anhSharedHetHanId).orElseThrow().trangThaiAnh()).isEqualTo("cho_don");

            // Storage hồi phục -> retry dọn 2 ảnh cho_don, không xóa keySharedGoc vì anhSharedConSongId vẫn tham chiếu
            storageGia.failDelete = false;
            assertThat(cleanupService.donDepAnhTamQuaHan(bayGio)).isEqualTo(2);
            assertThat(anhTinRepository.timAnhDeDoc(anhHetHanId)).isEmpty();
            assertThat(anhTinRepository.timAnhDeDoc(anhSharedHetHanId)).isEmpty();
            assertThat(anhTinRepository.timAnhDeDoc(anhSharedConSongId)).isPresent();
            assertThat(anhTinRepository.timAnhDeDoc(anhSharedConSongId).get().trangThaiAnh()).isEqualTo("tam");

            assertThat(storageGia.objects).doesNotContain(keyUniqueGoc, keyUniqueHienThi, keySharedHetHanHienThi);
            assertThat(storageGia.objects).contains(keySharedGoc, keySharedSongHienThi);
        } finally {
            donDepTaiKhoanPg(List.of(chu.taiKhoan().getId()));
        }
    }

    private UUID luuAnhTamPg(UUID chuId, String khoaGoc, String khoaHienThi) {
        UUID anhId = UUID.randomUUID();
        anhTinRepository.luuAnhTam(anhId, chuId, anhId + "-" + khoaGoc, anhId + "-" + khoaHienThi, DinhDangAnh.PNG, 1024L);
        return anhId;
    }

    private TaiKhoanSession taoTaiKhoanVaPhien(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        UUID taiKhoanId = UUID.randomUUID();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                taiKhoanId,
                UUID.randomUUID(),
                "pg-tin-" + taiKhoanId + "@bds-test.local",
                "Chủ tin PG",
                "0909111222",
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                now,
                now,
                now
        );
        taiKhoanRepository.save(taiKhoan);

        String rawCookie = sessionLifecyclePort.generateRawSessionCookie();
        String cookieHash = sessionLifecyclePort.hashSessionCookie(rawCookie);
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                taiKhoanId,
                cookieHash,
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new TaiKhoanSession(taiKhoan, rawCookie, phien.getId(), cookieHash);
    }

    private void donDepTaiKhoanPg(List<UUID> taiKhoanIds) {
        if (taiKhoanIds == null || taiKhoanIds.isEmpty()) {
            return;
        }
        TestDatabaseSettings.fromEnvironment();
        String placeholders = String.join(", ", Collections.nCopies(taiKhoanIds.size(), "?"));
        Object[] args = taiKhoanIds.toArray();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            // V1 đặt cặp FK vòng tròn ON DELETE RESTRICT giữa tin_bat_dong_san và phien_ban_tin (chỉ cho soft delete ở prod)
            jdbcTemplate.execute("SET LOCAL session_replication_role = replica");
            jdbcTemplate.update("DELETE FROM se214.nhat_ky_kiem_toan WHERE nguoi_thuc_hien_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.yeu_cau_ghi WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.anh_tin WHERE chu_id IN (" + placeholders + ")", args);
            jdbcTemplate.update(
                    "DELETE FROM se214.phien_ban_tin WHERE tin_id IN (SELECT id FROM se214.tin_bat_dong_san WHERE chu_id IN (" + placeholders + "))",
                    args
            );
            jdbcTemplate.update("DELETE FROM se214.tin_bat_dong_san WHERE chu_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.han_muc_thang WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.phien_xac_thuc WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.tai_khoan WHERE id IN (" + placeholders + ")", args);
        });
    }

    private static PrincipalPhienHienTai toPrincipal(TaiKhoanSession session) {
        TaiKhoan tk = session.taiKhoan();
        return new PrincipalPhienHienTai(
                session.phienId(),
                tk.getId(),
                session.cookieHash(),
                tk.getEmail(),
                tk.getHoTen(),
                tk.getSoDienThoai(),
                tk.getVaiTro(),
                tk.getTrangThai(),
                tk.isEmailDaXacMinh(),
                tk.getMocThuHoi()
        );
    }

    private static NoiDungTinDauVao noiDungHopLe() {
        return new NoiDungTinDauVao(
                "Nhà phố quận 1 sổ hồng riêng",
                "Kết cấu 1 trệt 3 lầu, đường rộng 8m.",
                LoaiGiaoDich.BAN,
                LoaiBds.NHA_RIENG,
                new BigDecimal("12000000000"),
                DonViGiaTin.VND_TONG,
                new BigDecimal("85.0"),
                "18 Lý Tự Trọng, Quận 1",
                "TP. Hồ Chí Minh",
                "10.7772",
                "106.7015"
        );
    }

    private static Map<String, Object> duLieuTinHopLe(List<String> anhIds) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("tieuDe", "Nhà phố quận 1 sổ hồng riêng");
        duLieu.put("moTa", "Kết cấu 1 trệt 3 lầu, đường rộng 8m.");
        duLieu.put("loaiGiaoDich", "ban");
        duLieu.put("loaiBds", "nha_rieng");
        duLieu.put("giaGoc", "12000000000");
        duLieu.put("donViGia", "vnd_tong");
        duLieu.put("dienTichM2", "85.0");
        duLieu.put("diaChi", "18 Lý Tự Trọng, Quận 1");
        duLieu.put("khuVuc", "TP. Hồ Chí Minh");
        duLieu.put("viDo", "10.7772");
        duLieu.put("kinhDo", "106.7015");
        duLieu.put("anhIds", anhIds);
        return duLieu;
    }

    private record TaiKhoanSession(TaiKhoan taiKhoan, String rawCookie, UUID phienId, String cookieHash) {
    }

    private static final class KhoAnhPgGia implements AnhStoragePort {

        private final Set<String> objects = ConcurrentHashMap.newKeySet();
        private volatile boolean failDelete;

        @Override
        public void luu(String khoaDoiTuong, byte[] noiDung, String mimeType) {
            objects.add(khoaDoiTuong);
        }

        @Override
        public byte[] doc(String khoaDoiTuong) {
            return new byte[]{1};
        }

        @Override
        public void xoa(String khoaDoiTuong) {
            if (failDelete) {
                throw new AuthProviderUnavailableException("Storage lỗi khi xóa");
            }
            if (khoaDoiTuong != null) {
                objects.remove(khoaDoiTuong);
            }
        }
    }
}
