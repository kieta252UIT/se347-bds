package com.realestates.backend.search;

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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dựng dữ liệu thử cho tìm kiếm trên PostgreSQL thật. Đăng tin = TinDangService.dangTin, sửa = QuanLyTinService.suaTin,
 * xóa = QuanLyTinService.xoaTin. Phê duyệt/từ chối/gỡ (chưa có mã nghiệp vụ trong nhánh này) mô phỏng bằng SQL trực tiếp
 * đúng như một lần phê duyệt thật. Gói đẩy tin chèn thẳng vào goi_day_tin. Mỗi tin có chủ riêng vì hạn mức 3 tin mới/tháng.
 */
public final class TimKiemPgTestSupport {

    public record TaiKhoanSession(TaiKhoan taiKhoan, String rawCookie) {
    }

    public record TinTao(UUID tinId, UUID phienBanId, UUID anhId, TaiKhoanSession chu) {
    }

    /** Nội dung một tin; các trường dạng chuỗi giống payload thật của API đăng tin. */
    public record NoiDung(
            String tieuDe,
            String loaiGiaoDich,
            String loaiBds,
            String giaGoc,
            String donViGia,
            String dienTichM2,
            String khuVuc
    ) {

        public static NoiDung banMau(String khuVuc) {
            return new NoiDung("Căn hộ 2 phòng ngủ view sông", "ban", "can_ho", "2000000000", "vnd_tong", "50", khuVuc);
        }

        public NoiDung voiTieuDe(String giaTri) {
            return new NoiDung(giaTri, loaiGiaoDich, loaiBds, giaGoc, donViGia, dienTichM2, khuVuc);
        }

        public NoiDung voiBan(String loaiBdsMoi, String giaGocMoi, String donViMoi, String dienTichMoi) {
            return new NoiDung(tieuDe, "ban", loaiBdsMoi, giaGocMoi, donViMoi, dienTichMoi, khuVuc);
        }

        public NoiDung voiThue(String loaiBdsMoi, String giaGocMoi, String donViMoi, String dienTichMoi) {
            return new NoiDung(tieuDe, "cho_thue", loaiBdsMoi, giaGocMoi, donViMoi, dienTichMoi, khuVuc);
        }

        public NoiDung voiLoaiBds(String giaTri) {
            return new NoiDung(tieuDe, loaiGiaoDich, giaTri, giaGoc, donViGia, dienTichM2, khuVuc);
        }

        public NoiDung voiGia(String giaTri) {
            return new NoiDung(tieuDe, loaiGiaoDich, loaiBds, giaTri, donViGia, dienTichM2, khuVuc);
        }

        public NoiDung voiDienTich(String giaTri) {
            return new NoiDung(tieuDe, loaiGiaoDich, loaiBds, giaGoc, donViGia, giaTri, khuVuc);
        }

        Map<String, Object> thanhPayload(List<String> anhIds) {
            Map<String, Object> duLieu = new LinkedHashMap<>();
            duLieu.put("tieuDe", tieuDe);
            duLieu.put("moTa", "Kết cấu 1 trệt 3 lầu, đường rộng 8m.");
            duLieu.put("loaiGiaoDich", loaiGiaoDich);
            duLieu.put("loaiBds", loaiBds);
            duLieu.put("giaGoc", giaGoc);
            duLieu.put("donViGia", donViGia);
            duLieu.put("dienTichM2", dienTichM2);
            duLieu.put("diaChi", "18 Lý Tự Trọng, Quận 1");
            duLieu.put("khuVuc", khuVuc);
            duLieu.put("viDo", "10.7772");
            duLieu.put("kinhDo", "106.7015");
            duLieu.put("anhIds", anhIds);
            return duLieu;
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final TaiKhoanRepository taiKhoanRepository;
    private final PhienXacThucRepository phienXacThucRepository;
    private final AnhTinRepository anhTinRepository;
    private final TinDangService tinDangService;
    private final QuanLyTinService quanLyTinService;
    private final AuthBrowserSessionLifecyclePort sessionLifecyclePort;
    private final List<UUID> taiKhoanDaTao = new ArrayList<>();
    private TaiKhoanSession kiemDuyetVien;

    public TimKiemPgTestSupport(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            TaiKhoanRepository taiKhoanRepository,
            PhienXacThucRepository phienXacThucRepository,
            AnhTinRepository anhTinRepository,
            TinDangService tinDangService,
            QuanLyTinService quanLyTinService,
            AuthBrowserSessionLifecyclePort sessionLifecyclePort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.taiKhoanRepository = taiKhoanRepository;
        this.phienXacThucRepository = phienXacThucRepository;
        this.anhTinRepository = anhTinRepository;
        this.tinDangService = tinDangService;
        this.quanLyTinService = quanLyTinService;
        this.sessionLifecyclePort = sessionLifecyclePort;
    }

    public TaiKhoanSession taoTaiKhoan(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        UUID taiKhoanId = UUID.randomUUID();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                taiKhoanId,
                UUID.randomUUID(),
                "pg-tim-kiem-" + taiKhoanId + "@bds-test.local",
                "Tài khoản tìm kiếm thử",
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

    /** Đăng tin qua luồng thật với chủ mới; tin ở trạng thái cho_kiem_duyet. */
    public TinTao taoTinChoDuyet(NoiDung noiDung) {
        return taoTinChoDuyet(taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG), noiDung);
    }

    public TinTao taoTinChoDuyet(TaiKhoanSession chu, NoiDung noiDung) {
        return taoTinChoDuyet(chu, noiDung, 1);
    }

    /** Như trên nhưng đính kèm soAnh ảnh (thứ tự anhIds = thứ tự thu_tu 1..soAnh). anhId của TinTao là ảnh đầu tiên. */
    public TinTao taoTinChoDuyet(TaiKhoanSession chu, NoiDung noiDung, int soAnh) {
        List<String> anhIds = new ArrayList<>();
        UUID anhDau = null;
        for (int i = 0; i < soAnh; i++) {
            UUID anhId = luuAnhTam(chu.taiKhoan().getId());
            anhDau = anhDau == null ? anhId : anhDau;
            anhIds.add(anhId.toString());
        }
        TinGhiKetQua ketQua = tinDangService.dangTin(
                chu.rawCookie(),
                "tao-" + UUID.randomUUID(),
                noiDung.thanhPayload(anhIds)
        );
        return new TinTao(ketQua.tinId(), ketQua.phienBanHienTaiId(), anhDau, chu);
    }

    /** Đăng (luồng thật) rồi phê duyệt bằng SQL mô phỏng đúng một lần phê duyệt, kèm ngay_dang xác định. */
    public TinTao taoTinDaDuyet(NoiDung noiDung, Instant ngayDang) {
        TinTao tin = taoTinChoDuyet(noiDung);
        duyetPhienBanHienTai(tin.tinId());
        datNgayDang(tin.tinId(), ngayDang);
        return tin;
    }

    /** Mô phỏng phê duyệt phiên bản hiện tại: phiên bản da_duyet + người duyệt KDV + thời điểm; tin da_duyet, +1 phienBanCapNhat. */
    public void duyetPhienBanHienTai(UUID tinId) {
        UUID phienBanId = phienBanHienTai(tinId);
        UUID kdvId = kiemDuyetVien().taiKhoan().getId();
        jdbcTemplate.update(
                "UPDATE se214.phien_ban_tin SET ket_qua_kiem_duyet = 'da_duyet', nguoi_duyet_id = ?, "
                        + "thoi_diem_quyet_dinh = now() WHERE id = ?",
                kdvId,
                phienBanId
        );
        jdbcTemplate.update(
                "UPDATE se214.tin_bat_dong_san SET trang_thai = 'da_duyet', phien_ban_da_duyet_id = ?, "
                        + "phien_ban_cap_nhat = phien_ban_cap_nhat + 1 WHERE id = ?",
                phienBanId,
                tinId
        );
    }

    /** Mô phỏng từ chối tin mới (chưa từng được duyệt). */
    public void tuChoiPhienBanHienTai(UUID tinId) {
        UUID phienBanId = phienBanHienTai(tinId);
        UUID kdvId = kiemDuyetVien().taiKhoan().getId();
        jdbcTemplate.update(
                "UPDATE se214.phien_ban_tin SET ket_qua_kiem_duyet = 'bi_tu_choi', nguoi_duyet_id = ?, "
                        + "thoi_diem_quyet_dinh = now(), ly_do_tu_choi = 'Thong tin khong ro rang' WHERE id = ?",
                kdvId,
                phienBanId
        );
        jdbcTemplate.update(
                "UPDATE se214.tin_bat_dong_san SET trang_thai = 'bi_tu_choi', phien_ban_cap_nhat = phien_ban_cap_nhat + 1 "
                        + "WHERE id = ?",
                tinId
        );
    }

    /** Mô phỏng gỡ vì vi phạm. */
    public void goViPham(UUID tinId) {
        jdbcTemplate.update(
                "UPDATE se214.tin_bat_dong_san SET trang_thai = 'bi_go_vi_pham', ly_do_ngung_hien_thi = 'Vi pham', "
                        + "phien_ban_cap_nhat = phien_ban_cap_nhat + 1 WHERE id = ?",
                tinId
        );
    }

    /** Sửa tin đã duyệt qua luồng thật: tạo phiên bản mới chờ duyệt, giữ phiên bản đã duyệt. */
    public TinGhiKetQua suaTin(TinTao tin, NoiDung noiDungMoi) {
        UUID phienBanHienTai = phienBanHienTai(tin.tinId());
        List<UUID> anhIds = jdbcTemplate.query(
                "SELECT id FROM se214.anh_tin WHERE phien_ban_tin_id = ? AND trang_thai = 'da_gan' ORDER BY thu_tu",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                phienBanHienTai
        );
        Map<String, Object> payload = noiDungMoi.thanhPayload(anhIds.stream().map(UUID::toString).toList());
        payload.put(QuanLyTinService.TRUONG_PHIEN_BAN_DA_XEM, phienBanHienTai.toString());
        return quanLyTinService.suaTin(tin.chu().rawCookie(), tin.tinId(), "sua-" + UUID.randomUUID(), etag(tin.tinId()), payload);
    }

    public TinGhiKetQua xoaTin(TinTao tin) {
        return quanLyTinService.xoaTin(tin.chu().rawCookie(), tin.tinId(), "xoa-" + UUID.randomUUID(), etag(tin.tinId()));
    }

    public void khoaChu(TinTao tin) {
        jdbcTemplate.update("UPDATE se214.tai_khoan SET trang_thai = 'bi_khoa' WHERE id = ?", tin.chu().taiKhoan().getId());
    }

    public void datNgayDang(UUID tinId, Instant ngayDang) {
        jdbcTemplate.update("UPDATE se214.tin_bat_dong_san SET ngay_dang = ? WHERE id = ?", Timestamp.from(ngayDang), tinId);
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

    /** Gói đang hoạt động còn hạn 1 ngày. */
    public UUID taoGoiDangHoatDong(UUID tinId) {
        return taoGoi(tinId, "dang_hoat_dong", Instant.now().plusSeconds(86_400), "1 day");
    }

    public String khoaAnhHienThi(UUID phienBanId) {
        return jdbcTemplate.queryForObject(
                "SELECT khoa_anh_hien_thi FROM se214.anh_tin WHERE phien_ban_tin_id = ? AND trang_thai = 'da_gan' "
                        + "ORDER BY thu_tu LIMIT 1",
                String.class,
                phienBanId
        );
    }

    public String etag(UUID tinId) {
        Long giaTri = jdbcTemplate.queryForObject(
                "SELECT phien_ban_cap_nhat FROM se214.tin_bat_dong_san WHERE id = ?", Long.class, tinId);
        return "\"" + (giaTri == null ? 0L : giaTri) + "\"";
    }

    public UUID phienBanHienTai(UUID tinId) {
        return jdbcTemplate.queryForObject(
                "SELECT phien_ban_hien_tai_id FROM se214.tin_bat_dong_san WHERE id = ?", UUID.class, tinId);
    }

    public UUID phienBanDaDuyet(UUID tinId) {
        return jdbcTemplate.queryForObject(
                "SELECT phien_ban_da_duyet_id FROM se214.tin_bat_dong_san WHERE id = ?", UUID.class, tinId);
    }

    public UUID luuAnhTam(UUID chuId) {
        UUID anhId = UUID.randomUUID();
        anhTinRepository.luuAnhTam(anhId, chuId, anhId + "-goc.png", anhId + "-hienthi.png", DinhDangAnh.PNG, 1024L);
        return anhId;
    }

    /** Ghi thẳng một dòng yêu thích với luu_luc xác định (dùng để tạo các mốc bằng nhau/khác nhau). */
    public void chenYeuThich(UUID taiKhoanId, UUID tinId, Instant luuLuc) {
        jdbcTemplate.update(
                "INSERT INTO se214.yeu_thich_tin (tai_khoan_id, tin_id, luu_luc) VALUES (?, ?, ?)",
                taiKhoanId,
                tinId,
                Timestamp.from(luuLuc)
        );
    }

    public int demYeuThich(UUID taiKhoanId, UUID tinId) {
        Integer so = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM se214.yeu_thich_tin WHERE tai_khoan_id = ? AND tin_id = ?",
                Integer.class,
                taiKhoanId,
                tinId
        );
        return so == null ? 0 : so;
    }

    public Instant luuLucYeuThich(UUID taiKhoanId, UUID tinId) {
        return jdbcTemplate.queryForObject(
                "SELECT luu_luc FROM se214.yeu_thich_tin WHERE tai_khoan_id = ? AND tin_id = ?",
                Timestamp.class,
                taiKhoanId,
                tinId
        ).toInstant();
    }

    public void datHoTenVaSoDienThoai(UUID taiKhoanId, String hoTen, String soDienThoai) {
        jdbcTemplate.update(
                "UPDATE se214.tai_khoan SET ho_ten = ?, so_dien_thoai = ? WHERE id = ?",
                hoTen,
                soDienThoai,
                taiKhoanId
        );
    }

    public void khoaTaiKhoan(UUID taiKhoanId) {
        jdbcTemplate.update("UPDATE se214.tai_khoan SET trang_thai = 'bi_khoa' WHERE id = ?", taiKhoanId);
    }

    public String emailTaiKhoan(UUID taiKhoanId) {
        return jdbcTemplate.queryForObject("SELECT email FROM se214.tai_khoan WHERE id = ?", String.class, taiKhoanId);
    }

    private TaiKhoanSession kiemDuyetVien() {
        if (kiemDuyetVien == null) {
            kiemDuyetVien = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        }
        return kiemDuyetVien;
    }

    /** Dọn toàn bộ dữ liệu của mọi tài khoản đã tạo (yêu thích, tin, phiên bản, ảnh, gói, nhật ký, yêu cầu ghi, phiên, tài khoản). */
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
            jdbcTemplate.update("DELETE FROM se214.yeu_thich_tin WHERE tai_khoan_id IN (" + placeholders + ")", args);
            jdbcTemplate.update("DELETE FROM se214.yeu_thich_tin WHERE tin_id IN (" + tinCuaCacChu + ")", args);
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
        kiemDuyetVien = null;
    }
}
