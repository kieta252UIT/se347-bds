package com.realestates.backend.moderation;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.moderation.application.KiemDuyetService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hỗ trợ dựng dữ liệu thử cho kiểm duyệt trên PostgreSQL thật. Mọi fixture đi qua luồng thật:
 * đăng tin = TinDangService.dangTin, sửa tin = QuanLyTinService.suaTin (tạm dừng gói), xóa = QuanLyTinService.xoaTin,
 * tin đã duyệt = KiemDuyetService.quyetDinh (phe_duyet).
 */
public final class KiemDuyetPgTestSupport {

    public static final Instant THOI_DIEM = Instant.parse("2026-11-18T03:00:00Z");
    public static final Instant THOI_DIEM_DUYET = THOI_DIEM.plus(Duration.ofHours(2));

    public record TaiKhoanSession(TaiKhoan taiKhoan, String rawCookie) {
    }

    public record TinCho(UUID tinId, UUID phienBanId, UUID anhId, TaiKhoanSession chu) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final TaiKhoanRepository taiKhoanRepository;
    private final PhienXacThucRepository phienXacThucRepository;
    private final AnhTinRepository anhTinRepository;
    private final TinDangService tinDangService;
    private final QuanLyTinService quanLyTinService;
    private final KiemDuyetService kiemDuyetService;
    private final AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    private final List<UUID> taiKhoanDaTao = new ArrayList<>();

    public KiemDuyetPgTestSupport(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AnhTinRepository anhTinRepository,
            TinDangService tinDangService,
            QuanLyTinService quanLyTinService,
            KiemDuyetService kiemDuyetService,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.taiKhoanRepository = taiKhoanRepository;
        this.phienXacThucRepository = phienXacThucRepository;
        this.anhTinRepository = anhTinRepository;
        this.tinDangService = tinDangService;
        this.quanLyTinService = quanLyTinService;
        this.kiemDuyetService = kiemDuyetService;
        this.sessionLifecyclePort = sessionLifecyclePort;
    }

    public TaiKhoanSession taoTaiKhoan(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        UUID taiKhoanId = UUID.randomUUID();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                taiKhoanId,
                UUID.randomUUID(),
                "pg-kiem-duyet-" + taiKhoanId + "@bds-test.local",
                "Tài khoản kiểm duyệt thử",
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
        taiKhoanDaTao.add(taiKhoanId);

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

    /** Đăng tin mới qua luồng thật; tin ở trạng thái cho_kiem_duyet, ETag "0". */
    public TinCho taoTinMoiChoDuyet(TaiKhoanSession chu) {
        UUID anhId = luuAnhTam(chu.taiKhoan().getId());
        TinGhiKetQua ketQua = tinDangService.dangTin(
                chu.rawCookie(),
                "tao-" + UUID.randomUUID(),
                duLieuTin("Căn hộ 2 phòng ngủ view sông", List.of(anhId.toString()))
        );
        return new TinCho(ketQua.tinId(), ketQua.phienBanHienTaiId(), anhId, chu);
    }

    /** Tin đã được phê duyệt bằng luồng phê duyệt thật (không sửa SQL). */
    public TinCho taoTinDaDuyet(TaiKhoanSession chu, TaiKhoanSession kdv) {
        TinCho tin = taoTinMoiChoDuyet(chu);
        pheDuyet(kdv, tin.tinId(), tin.phienBanId(), "duyet-" + UUID.randomUUID(), etag(tin.tinId()));
        return tin;
    }

    /** POST .../decision với quyetDinh = phe_duyet. */
    public TinGhiKetQua pheDuyet(TaiKhoanSession kdv, UUID tinId, UUID phienBanDaXemId, String maYDinh, String ifMatch) {
        return kiemDuyetService.quyetDinh(kdv.rawCookie(), tinId, maYDinh, ifMatch, thanPheDuyet(phienBanDaXemId));
    }

    /** POST .../decision với quyetDinh = tu_choi. */
    public TinGhiKetQua tuChoi(TaiKhoanSession kdv, UUID tinId, UUID phienBanDaXemId, String maYDinh, String ifMatch, String lyDo) {
        return kiemDuyetService.quyetDinh(kdv.rawCookie(), tinId, maYDinh, ifMatch, thanTuChoi(phienBanDaXemId, lyDo));
    }

    public static Map<String, Object> thanPheDuyet(UUID phienBanDaXemId) {
        Map<String, Object> than = new LinkedHashMap<>();
        than.put("phienBanDaXemId", phienBanDaXemId.toString());
        than.put("quyetDinh", "phe_duyet");
        return than;
    }

    public static Map<String, Object> thanTuChoi(UUID phienBanDaXemId, String lyDo) {
        Map<String, Object> than = new LinkedHashMap<>();
        than.put("phienBanDaXemId", phienBanDaXemId.toString());
        than.put("quyetDinh", "tu_choi");
        than.put("lyDo", lyDo);
        return than;
    }

    /** Sửa tin qua luồng thật (đồng thời tạm dừng gói đang chạy); trả về phiên bản mới đang chờ duyệt. */
    public TinGhiKetQua suaTin(TaiKhoanSession chu, UUID tinId, String tieuDe) {
        UUID phienBanHienTai = jdbcTemplate.queryForObject(
                "SELECT phien_ban_hien_tai_id FROM se214.tin_bat_dong_san WHERE id = ?", UUID.class, tinId);
        List<UUID> anhIds = jdbcTemplate.query(
                "SELECT id FROM se214.anh_tin WHERE phien_ban_tin_id = ? AND trang_thai = 'da_gan' ORDER BY thu_tu",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                phienBanHienTai
        );
        Map<String, Object> payload = duLieuTin(tieuDe, anhIds.stream().map(UUID::toString).toList());
        payload.put("phienBanDaXemId", phienBanHienTai.toString());
        return quanLyTinService.suaTin(chu.rawCookie(), tinId, "sua-" + UUID.randomUUID(), etag(tinId), payload);
    }

    public TinGhiKetQua xoaTin(TaiKhoanSession chu, UUID tinId) {
        return quanLyTinService.xoaTin(chu.rawCookie(), tinId, "xoa-" + UUID.randomUUID(), etag(tinId));
    }

    public TinGhiKetQua xoaTin(TaiKhoanSession chu, UUID tinId, String etag) {
        return quanLyTinService.xoaTin(chu.rawCookie(), tinId, "xoa-" + UUID.randomUUID(), etag);
    }

    public String etag(UUID tinId) {
        return "\"" + phienBanCapNhat(tinId) + "\"";
    }

    public long phienBanCapNhat(UUID tinId) {
        Long giaTri = jdbcTemplate.queryForObject(
                "SELECT phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", Long.class, tinId);
        return giaTri == null ? 0L : giaTri;
    }

    public UUID phienBanHienTai(UUID tinId) {
        return jdbcTemplate.queryForObject(
                "SELECT phien_ban_hien_tai_id FROM se214.tin_bat_dong_san WHERE id = ?", UUID.class, tinId);
    }

    public UUID taoGoi(UUID tinId, String trangThai, Instant hetHanLuc, String thoiLuongConLai) {
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

    public UUID luuAnhTam(UUID chuId) {
        UUID anhId = UUID.randomUUID();
        anhTinRepository.luuAnhTam(anhId, chuId, anhId + "-goc.png", anhId + "-hienthi.png", DinhDangAnh.PNG, 1024L);
        return anhId;
    }

    public int dem(String sql, Object... thamSo) {
        Number ketQua = jdbcTemplate.queryForObject(sql, Number.class, thamSo);
        return ketQua == null ? 0 : ketQua.intValue();
    }

    public static Map<String, Object> duLieuTin(String tieuDe, List<String> anhIds) {
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

    public <T> List<T> chayDongThoi(List<Callable<T>> cacViec) throws Exception {
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
                ketQua.add(future.get(30, TimeUnit.SECONDS));
            }
            return ketQua;
        } finally {
            executor.shutdownNow();
        }
    }

    /** Dọn toàn bộ dữ liệu của mọi tài khoản đã tạo (tin, phiên bản, ảnh, gói, nhật ký, yêu cầu ghi, phiên, tài khoản). */
    public void donDep() {
        if (taiKhoanDaTao.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", Collections.nCopies(taiKhoanDaTao.size(), "?"));
        Object[] args = taiKhoanDaTao.toArray();
        String tinCuaCacChu = "SELECT id FROM se214.tin_bat_dong_san WHERE chu_id IN (" + placeholders + ")";
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            jdbcTemplate.execute("SET LOCAL session_replication_role = replica");
            jdbcTemplate.update("DELETE FROM se214.su_kien_goi WHERE goi_id IN (SELECT id FROM se214.goi_day_tin WHERE tin_id IN (" + tinCuaCacChu + "))", args);
            jdbcTemplate.update("DELETE FROM se214.goi_day_tin WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.bao_cao_vi_pham WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.nhat_ky_kiem_toan WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.nhat_ky_kiem_toan WHERE nguoi_thuc_hien_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.yeu_cau_ghi WHERE tin_ket_qua_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.yeu_cau_ghi WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.anh_tin WHERE chu_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.phien_ban_tin WHERE tin_id IN (" + tinCuaCacChu + ")", args);
            jdbcTemplate.update("DELETE FROM se214.tin_bat_dong_san WHERE chu_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.han_muc_thang WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.phien_xac_thuc WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.tai_khoan WHERE id IN (" + placeholders + ")", args);
        });
        taiKhoanDaTao.clear();
    }
}
