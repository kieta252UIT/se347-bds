package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.BanDoPort;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.domain.TinDangRepository;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingPreconditionException;
import com.realestates.backend.listing.infrastructure.PostgreSqlAnhTinRepository;
import com.realestates.backend.listing.infrastructure.PostgreSqlTinDangRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
class PostgreSqlSuaXoaTinTests {

    private static final Instant THOI_DIEM = Instant.parse("2026-11-18T03:00:00Z");

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

    @Autowired
    private BanDoPort banDoPort;

    private QuanLyTinService quanLyTinService() {
        return new QuanLyTinService(
                permissionAndCsrfService,
                tinDangRepository,
                banDoPort,
                Clock.fixed(THOI_DIEM, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("PostgreSQL thật: sửa tin đã duyệt tạo phiên bản 2, giữ bản đã duyệt, tạm dừng gói đang chạy với đúng thời lượng còn lại, không tăng hạn mức")
    void suaTinTamDungGoiDangChay() {
        assertThat(tinDangRepository).isInstanceOf(PostgreSqlTinDangRepository.class);
        assertThat(anhTinRepository).isInstanceOf(PostgreSqlAnhTinRepository.class);
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        try {
            TinDaTao tin = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiId = taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(Duration.ofHours(123)), "7 days");
            UUID anhMoi = luuAnhTamPg(chu.taiKhoan().getId());

            TinGhiKetQua ketQua = quanLyTinService().suaTin(
                    chu.rawCookie(),
                    tin.tinId(),
                    "sua-1",
                    "\"0\"",
                    payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId(), anhMoi))
            );

            assertThat(ketQua.trangThai()).isEqualTo("cho_kiem_duyet");
            assertThat(ketQua.phienBanCapNhat()).isEqualTo(1L);
            Map<String, Object> tinRow = jdbcTemplate.queryForMap(
                    "SELECT trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id, phien_ban_cap_nhat "
                            + "FROM se214.tin_bat_dong_san WHERE id = ?",
                    tin.tinId()
            );
            assertThat(tinRow.get("trang_thai")).isEqualTo("cho_kiem_duyet");
            assertThat(tinRow.get("phien_ban_hien_tai_id")).isEqualTo(ketQua.phienBanHienTaiId());
            assertThat(tinRow.get("phien_ban_da_duyet_id")).isEqualTo(tin.phienBanId());
            assertThat(((Number) tinRow.get("phien_ban_cap_nhat")).longValue()).isEqualTo(1L);

            assertThat(dem("SELECT count(*) FROM se214.phien_ban_tin WHERE tin_id = ?", tin.tinId())).isEqualTo(2);
            assertThat(dem(
                    "SELECT so_phien_ban FROM se214.phien_ban_tin WHERE id = ? AND ket_qua_kiem_duyet = 'cho_kiem_duyet'",
                    ketQua.phienBanHienTaiId()
            )).isEqualTo(2);
            assertThat(dem("SELECT count(*) FROM se214.anh_tin WHERE phien_ban_tin_id = ?", ketQua.phienBanHienTaiId()))
                    .isEqualTo(2);
            assertThat(dem("SELECT count(*) FROM se214.anh_tin WHERE phien_ban_tin_id = ?", tin.phienBanId())).isEqualTo(1);

            Map<String, Object> goiRow = jdbcTemplate.queryForMap(
                    "SELECT trang_thai, het_han_luc, EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint AS giay "
                            + "FROM se214.goi_day_tin WHERE id = ?",
                    goiId
            );
            assertThat(goiRow.get("trang_thai")).isEqualTo("tam_dung_cho_duyet");
            assertThat(goiRow.get("het_han_luc")).isNull();
            assertThat(((Number) goiRow.get("giay")).longValue()).isEqualTo(Duration.ofHours(123).toSeconds());

            Map<String, Object> suKien = jdbcTemplate.queryForMap(
                    "SELECT loai, nguoi_thuc_hien_id, EXTRACT(EPOCH FROM thoi_luong_truoc)::bigint AS truoc, "
                            + "EXTRACT(EPOCH FROM thoi_luong_sau)::bigint AS sau FROM se214.su_kien_goi WHERE goi_id = ?",
                    goiId
            );
            assertThat(suKien.get("loai")).isEqualTo("tam_dung");
            assertThat(suKien.get("nguoi_thuc_hien_id")).isEqualTo(chu.taiKhoan().getId());
            assertThat(((Number) suKien.get("truoc")).longValue()).isEqualTo(Duration.ofHours(123).toSeconds());
            assertThat(((Number) suKien.get("sau")).longValue()).isEqualTo(Duration.ofHours(123).toSeconds());

            assertThat(dem("SELECT sum(so_tin_moi) FROM se214.han_muc_thang WHERE tai_khoan_id = ?", chu.taiKhoan().getId()))
                    .isEqualTo(1);
            assertThat(dem(
                    "SELECT count(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'sua_tin'",
                    chu.taiKhoan().getId()
            )).isEqualTo(1);
            assertThat(dem(
                    "SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'sua_tin'",
                    tin.tinId()
            )).isEqualTo(1);

            TinGhiKetQua replay = quanLyTinService().suaTin(
                    chu.rawCookie(),
                    tin.tinId(),
                    "sua-1",
                    "\"0\"",
                    payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId(), anhMoi))
            );
            assertThat(replay).isEqualTo(ketQua);
            assertThat(dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isEqualTo(1);
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: gói đã tạm dừng hoặc đã quá hạn không bị đổi khi sửa tin")
    void suaTinKhongDoiGoiDaTamDungHoacQuaHan() {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        try {
            TinDaTao tinTamDung = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiTamDung = taoGoi(tinTamDung.tinId(), "tam_dung_cho_duyet", null, "2 days");
            TinDaTao tinQuaHan = taoTinDaDuyet(chu, kdv, "tao-2");
            UUID goiQuaHan = taoGoi(tinQuaHan.tinId(), "dang_hoat_dong", THOI_DIEM.minus(Duration.ofHours(1)), "1 day");

            quanLyTinService().suaTin(
                    chu.rawCookie(),
                    tinTamDung.tinId(),
                    "sua-1",
                    "\"0\"",
                    payloadSua(tinTamDung, "Tiêu đề sau khi sửa", List.of(tinTamDung.anhId()))
            );
            quanLyTinService().suaTin(
                    chu.rawCookie(),
                    tinQuaHan.tinId(),
                    "sua-2",
                    "\"0\"",
                    payloadSua(tinQuaHan, "Tiêu đề sau khi sửa", List.of(tinQuaHan.anhId()))
            );

            assertThat(dem("SELECT EXTRACT(EPOCH FROM thoi_luong_con_lai)::bigint FROM se214.goi_day_tin WHERE id = ?", goiTamDung))
                    .isEqualTo(Duration.ofDays(2).toSeconds());
            assertThat(dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'tam_dung_cho_duyet'", goiTamDung))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'dang_hoat_dong' AND het_han_luc IS NOT NULL", goiQuaHan))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id IN (?, ?)", goiTamDung, goiQuaHan)).isZero();
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: sửa tin lỗi ảnh thì hoàn tác toàn bộ, gói vẫn chạy và không có phiên bản hay ý định mới")
    void suaTinLoiAnhHoanTacToanBo() {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TaiKhoanSession nguoiKhac = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        try {
            TinDaTao tin = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiId = taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(Duration.ofDays(3)), "3 days");
            UUID anhNguoiKhac = luuAnhTamPg(nguoiKhac.taiKhoan().getId());

            assertThatThrownBy(() -> quanLyTinService().suaTin(
                    chu.rawCookie(),
                    tin.tinId(),
                    "sua-1",
                    "\"0\"",
                    payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId(), anhNguoiKhac))
            )).isInstanceOf(AuthValidationException.class);

            assertThat(dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'da_duyet' AND phien_ban_cap_nhat = 0", tin.tinId()))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.phien_ban_tin WHERE tin_id = ?", tin.tinId())).isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'dang_hoat_dong' AND het_han_luc IS NOT NULL", goiId))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isZero();
            assertThat(dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'sua_tin'", chu.taiKhoan().getId()))
                    .isZero();
            assertThat(dem("SELECT count(*) FROM se214.anh_tin WHERE id = ? AND trang_thai = 'tam'", anhNguoiKhac)).isEqualTo(1);
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId(), nguoiKhac.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: hai yêu cầu sửa đồng thời cùng If-Match chỉ một yêu cầu thành công, gói chỉ tạm dừng một lần")
    void haiYeuCauSuaDongThoi() throws Exception {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        try {
            TinDaTao tin = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiId = taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(Duration.ofDays(4)), "4 days");

            List<String> ketQua = chayDongThoi(List.of(
                    () -> thuSua(chu, tin, "dong-thoi-a", "Tiêu đề A sau khi sửa"),
                    () -> thuSua(chu, tin, "dong-thoi-b", "Tiêu đề B sau khi sửa")
            ));

            assertThat(ketQua).containsExactlyInAnyOrder("THANH_CONG", "PHIEN_BAN_CU");
            assertThat(dem("SELECT count(*) FROM se214.phien_ban_tin WHERE tin_id = ?", tin.tinId())).isEqualTo(2);
            assertThat(dem("SELECT phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId())).isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isEqualTo(1);
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: xóa tin kết thúc gói tạm dừng, đóng mọi báo cáo chờ xử lý, ghi sự kiện gói, và replay không ghi lại")
    void xoaTinKetThucGoiTamDungVaDongBaoCao() {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TaiKhoanSession nguoiBaoCao = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        try {
            TinDaTao tin = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiId = taoGoi(tin.tinId(), "tam_dung_cho_duyet", null, "2 days");
            UUID baoCao1 = taoBaoCao(tin, nguoiBaoCao.taiKhoan().getId());
            UUID baoCao2 = taoBaoCao(tin, kdv.taiKhoan().getId());

            TinGhiKetQua ketQua = quanLyTinService().xoaTin(chu.rawCookie(), tin.tinId(), "xoa-1", "\"0\"");

            assertThat(ketQua.trangThai()).isEqualTo("da_xoa");
            assertThat(ketQua.phienBanCapNhat()).isEqualTo(1L);
            assertThat(dem("SELECT count(*) FROM se214.tin_bat_dong_san WHERE id = ? AND trang_thai = 'da_xoa' AND phien_ban_cap_nhat = 1", tin.tinId()))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'ket_thuc_do_go_tin' AND het_han_luc IS NULL AND thoi_luong_con_lai = interval '0 seconds'", goiId))
                    .isEqualTo(1);
            Map<String, Object> suKien = jdbcTemplate.queryForMap(
                    "SELECT loai, EXTRACT(EPOCH FROM thoi_luong_truoc)::bigint AS truoc, "
                            + "EXTRACT(EPOCH FROM thoi_luong_sau)::bigint AS sau FROM se214.su_kien_goi WHERE goi_id = ?",
                    goiId
            );
            assertThat(suKien.get("loai")).isEqualTo("ket_thuc_do_xoa");
            assertThat(((Number) suKien.get("truoc")).longValue()).isEqualTo(Duration.ofDays(2).toSeconds());
            assertThat(((Number) suKien.get("sau")).longValue()).isZero();
            assertThat(dem("SELECT count(*) FROM se214.bao_cao_vi_pham WHERE tin_id = ? AND trang_thai = 'cho_xu_ly'", tin.tinId())).isZero();
            assertThat(dem("SELECT count(*) FROM se214.bao_cao_vi_pham WHERE id IN (?, ?) AND trang_thai = 'da_dong' "
                    + "AND ly_do_dong = 'tin_da_xoa' AND dong_luc IS NOT NULL", baoCao1, baoCao2)).isEqualTo(2);
            assertThat(dem("SELECT sum(so_tin_moi) FROM se214.han_muc_thang WHERE tai_khoan_id = ?", chu.taiKhoan().getId()))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.yeu_cau_ghi WHERE tai_khoan_id = ? AND loai_thao_tac = 'xoa_tin'", chu.taiKhoan().getId()))
                    .isEqualTo(1);
            assertThat(dem("SELECT count(*) FROM se214.nhat_ky_kiem_toan WHERE tin_id = ? AND hanh_dong = 'xoa_tin'", tin.tinId()))
                    .isEqualTo(1);

            TinGhiKetQua replay = quanLyTinService().xoaTin(chu.rawCookie(), tin.tinId(), "xoa-1", "\"0\"");
            assertThat(replay).isEqualTo(ketQua);
            assertThat(dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isEqualTo(1);

            assertThatThrownBy(() -> quanLyTinService().xoaTin(chu.rawCookie(), tin.tinId(), "xoa-2", "\"1\""))
                    .isInstanceOf(ListingConflictException.class);
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId(), nguoiBaoCao.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: xóa tin có gói đang chạy ghi thời lượng còn lại trước khi kết thúc")
    void xoaTinGoiDangChay() {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        try {
            TinDaTao tin = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiId = taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(Duration.ofMinutes(90)), "1 day");

            quanLyTinService().xoaTin(chu.rawCookie(), tin.tinId(), "xoa-1", "\"0\"");

            assertThat(dem("SELECT count(*) FROM se214.goi_day_tin WHERE id = ? AND trang_thai = 'ket_thuc_do_go_tin' AND het_han_luc IS NULL", goiId))
                    .isEqualTo(1);
            Map<String, Object> suKien = jdbcTemplate.queryForMap(
                    "SELECT EXTRACT(EPOCH FROM thoi_luong_truoc)::bigint AS truoc, EXTRACT(EPOCH FROM thoi_luong_sau)::bigint AS sau "
                            + "FROM se214.su_kien_goi WHERE goi_id = ? AND loai = 'ket_thuc_do_xoa'",
                    goiId
            );
            assertThat(((Number) suKien.get("truoc")).longValue()).isEqualTo(Duration.ofMinutes(90).toSeconds());
            assertThat(((Number) suKien.get("sau")).longValue()).isZero();
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId()));
        }
    }

    @Test
    @DisplayName("PostgreSQL thật: sửa và xóa đồng thời cùng If-Match chỉ một thao tác thành công")
    void suaVaXoaDongThoi() throws Exception {
        TaiKhoanSession chu = taoTaiKhoanVaPhien(VaiTroTaiKhoan.NGUOI_DUNG);
        TaiKhoanSession kdv = taoTaiKhoanVaPhien(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        try {
            TinDaTao tin = taoTinDaDuyet(chu, kdv, "tao-1");
            UUID goiId = taoGoi(tin.tinId(), "dang_hoat_dong", THOI_DIEM.plus(Duration.ofDays(4)), "4 days");

            List<String> ketQua = chayDongThoi(List.of(
                    () -> thuSua(chu, tin, "dong-thoi-sua", "Tiêu đề sau khi sửa"),
                    () -> thuXoa(chu, tin, "dong-thoi-xoa")
            ));

            assertThat(ketQua).containsExactlyInAnyOrder("THANH_CONG", "PHIEN_BAN_CU");
            assertThat(dem("SELECT count(*) FROM se214.su_kien_goi WHERE goi_id = ?", goiId)).isEqualTo(1);
            assertThat(dem("SELECT phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", tin.tinId())).isEqualTo(1);
        } finally {
            donDep(List.of(chu.taiKhoan().getId(), kdv.taiKhoan().getId()));
        }
    }

    private String thuSua(TaiKhoanSession chu, TinDaTao tin, String maYDinh, String tieuDe) {
        try {
            quanLyTinService().suaTin(
                    chu.rawCookie(),
                    tin.tinId(),
                    maYDinh,
                    "\"0\"",
                    payloadSua(tin, tieuDe, List.of(tin.anhId()))
            );
            return "THANH_CONG";
        } catch (ListingPreconditionException ex) {
            return ex.getMaLoi();
        }
    }

    private String thuXoa(TaiKhoanSession chu, TinDaTao tin, String maYDinh) {
        try {
            quanLyTinService().xoaTin(chu.rawCookie(), tin.tinId(), maYDinh, "\"0\"");
            return "THANH_CONG";
        } catch (ListingPreconditionException ex) {
            return ex.getMaLoi();
        }
    }

    private UUID taoBaoCao(TinDaTao tin, UUID nguoiGuiId) {
        UUID baoCaoId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.bao_cao_vi_pham (id, tin_id, nguoi_gui_id, phien_ban_tham_chieu_id, ly_do) "
                        + "VALUES (?, ?, ?, ?, 'Nội dung không đúng sự thật')",
                baoCaoId,
                tin.tinId(),
                nguoiGuiId,
                tin.phienBanId()
        );
        return baoCaoId;
    }

    private <T> List<T> chayDongThoi(List<Callable<T>> cacViec) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(cacViec.size());
        CountDownLatch xuatPhat = new CountDownLatch(1);
        try {
            List<Future<T>> cacKetQua = new ArrayList<>();
            for (Callable<T> viec : cacViec) {
                cacKetQua.add(executor.submit(() -> {
                    xuatPhat.await();
                    return viec.call();
                }));
            }
            xuatPhat.countDown();
            List<T> ketQua = new ArrayList<>();
            for (Future<T> future : cacKetQua) {
                ketQua.add(future.get(20, TimeUnit.SECONDS));
            }
            return ketQua;
        } finally {
            executor.shutdownNow();
        }
    }

    private record TinDaTao(UUID tinId, UUID phienBanId, UUID anhId) {
    }

    private TinDaTao taoTinDaDuyet(TaiKhoanSession chu, TaiKhoanSession kdv, String maYDinh) {
        UUID anhId = luuAnhTamPg(chu.taiKhoan().getId());
        TinGhiKetQua ketQua = tinDangService.dangTin(
                chu.rawCookie(),
                maYDinh + "-" + UUID.randomUUID(),
                duLieuTin("Căn hộ 2 phòng ngủ view sông", List.of(anhId.toString()))
        );
        jdbcTemplate.update(
                "UPDATE se214.phien_ban_tin SET ket_qua_kiem_duyet = 'da_duyet', nguoi_duyet_id = ?, thoi_diem_quyet_dinh = ? WHERE id = ?",
                kdv.taiKhoan().getId(),
                Timestamp.from(THOI_DIEM),
                ketQua.phienBanHienTaiId()
        );
        jdbcTemplate.update(
                "UPDATE se214.tin_bat_dong_san SET trang_thai = 'da_duyet', phien_ban_da_duyet_id = ? WHERE id = ?",
                ketQua.phienBanHienTaiId(),
                ketQua.tinId()
        );
        return new TinDaTao(ketQua.tinId(), ketQua.phienBanHienTaiId(), anhId);
    }

    private UUID taoGoi(UUID tinId, String trangThai, Instant hetHanLuc, String thoiLuongConLai) {
        UUID goiId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.goi_day_tin (id, tin_id, trang_thai, thoi_luong_con_lai, het_han_luc) "
                        + "VALUES (?, ?, ?, CAST(? AS interval), ?)",
                goiId,
                tinId,
                trangThai,
                thoiLuongConLai,
                hetHanLuc == null ? null : Timestamp.from(hetHanLuc)
        );
        return goiId;
    }

    private UUID luuAnhTamPg(UUID chuId) {
        UUID anhId = UUID.randomUUID();
        anhTinRepository.luuAnhTam(anhId, chuId, anhId + "-goc.png", anhId + "-hienthi.png", DinhDangAnh.PNG, 1024L);
        return anhId;
    }

    private int dem(String sql, Object... thamSo) {
        Number ketQua = jdbcTemplate.queryForObject(sql, Number.class, thamSo);
        return ketQua == null ? 0 : ketQua.intValue();
    }

    private static Map<String, Object> duLieuTin(String tieuDe, List<String> anhIds) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("tieuDe", tieuDe);
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

    private static Map<String, Object> payloadSua(TinDaTao tin, String tieuDe, List<UUID> anhIds) {
        Map<String, Object> payload = duLieuTin(tieuDe, anhIds.stream().map(UUID::toString).toList());
        payload.put("phienBanDaXemId", tin.phienBanId().toString());
        return payload;
    }

    private TaiKhoanSession taoTaiKhoanVaPhien(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        UUID taiKhoanId = UUID.randomUUID();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                taiKhoanId,
                UUID.randomUUID(),
                "pg-sua-xoa-" + taiKhoanId + "@bds-test.local",
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
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                taiKhoanId,
                sessionLifecyclePort.hashSessionCookie(rawCookie),
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new TaiKhoanSession(taiKhoan, rawCookie);
    }

    private void donDep(List<UUID> taiKhoanIds) {
        String placeholders = String.join(", ", Collections.nCopies(taiKhoanIds.size(), "?"));
        Object[] args = taiKhoanIds.toArray();
        String tinCuaCacChu = "SELECT id FROM se214.tin_bat_dong_san WHERE chu_id IN (" + placeholders + ")";
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = replica");
            jdbcTemplate.update("DELETE FROM se214.su_kien_goi WHERE goi_id IN (SELECT id FROM se214.goi_day_tin WHERE tin_id IN (" + tinCuaCacChu + "))", args);
            jdbcTemplate.update("DELETE FROM se214.goi_day_tin WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.bao_cao_vi_pham WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.nhat_ky_kiem_toan WHERE nguoi_thuc_hien_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.yeu_cau_ghi WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.anh_tin WHERE chu_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.phien_ban_tin WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.tin_bat_dong_san WHERE chu_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.han_muc_thang WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.phien_xac_thuc WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.tai_khoan WHERE id IN (" + placeholders + ")", args);
        });
    }

    private record TaiKhoanSession(TaiKhoan taiKhoan, String rawCookie) {
    }
}

