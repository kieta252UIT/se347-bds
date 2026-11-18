package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realestates.backend.auth.application.AuthBrowserSessionLifecyclePort;
import com.realestates.backend.auth.application.AuthLogoutService;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.PhienXacThucRepository;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TaiKhoanRepository;
import com.realestates.backend.auth.domain.ThaoTacNghiepVu;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.application.AnhStoragePort;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.domain.QuyenDocAnh;
import com.realestates.backend.listing.infrastructure.PostgreSqlAnhTinRepository;
import com.realestates.backend.listing.infrastructure.SupabaseStorageRestClientAdapter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = "app.repository.type=postgres")
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlAnhTinRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AnhTinRepository anhTinRepository;

    @Autowired
    private TaiKhoanRepository taiKhoanRepository;

    @Autowired
    private PhienXacThucRepository phienXacThucRepository;

    @Autowired
    private AnhStoragePort anhStoragePort;

    @Autowired
    private CurrentPermissionAndCsrfService permissionAndCsrfService;

    @Autowired
    private AuthLogoutService authLogoutService;

    @Autowired
    private AuthBrowserSessionLifecyclePort sessionLifecyclePort;

    private UUID taoTaiKhoan() {
        UUID taiKhoanId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten) VALUES (?, ?, ?, 'Chủ ảnh thử')",
                taiKhoanId,
                UUID.randomUUID(),
                "chu-anh-" + taiKhoanId + "@bds-test.local"
        );
        return taiKhoanId;
    }

    @Test
    @Transactional
    @DisplayName("Lưu ảnh tạm rồi đọc lại đúng chủ, trạng thái tam và chưa gắn tin")
    void luuVaDocAnhTam() {
        PostgreSqlAnhTinRepository kho = new PostgreSqlAnhTinRepository(jdbcTemplate);
        UUID chuId = taoTaiKhoan();
        UUID anhId = UUID.randomUUID();

        kho.luuAnhTam(anhId, chuId, anhId + ".png", anhId + "-display.png", DinhDangAnh.PNG, 1234L);
        Optional<AnhTinTruyCap> anh = kho.timAnhDeDoc(anhId);

        assertThat(anh).isPresent();
        assertThat(anh.get().chuAnhId()).isEqualTo(chuId);
        assertThat(anh.get().trangThaiAnh()).isEqualTo("tam");
        assertThat(anh.get().khoaTepGoc()).isEqualTo(anhId + ".png");
        assertThat(anh.get().khoaAnhHienThi()).isEqualTo(anhId + "-display.png");
        assertThat(anh.get().mimeType()).isEqualTo("image/png");
        assertThat(anh.get().phienBanTinId()).isNull();
        assertThat(anh.get().trangThaiTin()).isNull();
        assertThat(anh.get().chuTinHoatDong()).isFalse();

        assertThat(kho.coThamChieuKhoa(anhId + ".png")).isTrue();
        assertThat(kho.coThamChieuKhoa(anhId + "-display.png")).isTrue();
        assertThat(kho.coThamChieuKhoa("chua-tung-co.png")).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("Ảnh không tồn tại trả về rỗng")
    void anhKhongTonTai() {
        PostgreSqlAnhTinRepository kho = new PostgreSqlAnhTinRepository(jdbcTemplate);

        assertThat(kho.timAnhDeDoc(UUID.randomUUID())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Ảnh đã gắn trả kèm trạng thái tin, phiên bản và trạng thái chủ tin")
    void docAnhDaGanKemNguCanhTin() {
        PostgreSqlAnhTinRepository kho = new PostgreSqlAnhTinRepository(jdbcTemplate);
        UUID chuId = taoTaiKhoan();
        UUID tinId = UUID.randomUUID();
        UUID phienBanId = UUID.randomUUID();
        UUID anhId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO se214.tin_bat_dong_san (id, chu_id, trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id) "
                        + "VALUES (?, ?, 'da_duyet', ?, ?)",
                tinId, chuId, phienBanId, phienBanId
        );
        jdbcTemplate.update(
                "INSERT INTO se214.phien_ban_tin (id, tin_id, so_phien_ban, tieu_de, mo_ta, loai_giao_dich, loai_bds, "
                        + "gia_goc, don_vi_gia, dien_tich_m2, dia_chi, khu_vuc, vi_do, kinh_do, ket_qua_so_bo) "
                        + "VALUES (?, ?, 1, 'Tiêu đề thử', 'Mô tả thử', 'ban', 'can_ho', "
                        + "1000000000, 'vnd_tong', 50, '1 Đường thử', 'Khu vực thử', 10.5, 106.5, 'dat')",
                phienBanId, tinId
        );
        jdbcTemplate.update(
                "INSERT INTO se214.anh_tin (id, chu_id, phien_ban_tin_id, khoa_tep_goc, khoa_anh_hien_thi, mime_type, "
                        + "kich_thuoc_byte, thu_tu, trang_thai) "
                        + "VALUES (?, ?, ?, 'goc.png', 'hien-thi.png', 'image/png', 1000, 1, 'da_gan')",
                anhId, chuId, phienBanId
        );

        Optional<AnhTinTruyCap> anh = kho.timAnhDeDoc(anhId);

        assertThat(anh).isPresent();
        assertThat(anh.get().trangThaiAnh()).isEqualTo("da_gan");
        assertThat(anh.get().khoaAnhHienThi()).isEqualTo("hien-thi.png");
        assertThat(anh.get().phienBanTinId()).isEqualTo(phienBanId);
        assertThat(anh.get().trangThaiTin()).isEqualTo("da_duyet");
        assertThat(anh.get().phienBanHienTaiId()).isEqualTo(phienBanId);
        assertThat(anh.get().phienBanDaDuyetId()).isEqualTo(phienBanId);
        assertThat(anh.get().chuTinHoatDong()).isTrue();

        jdbcTemplate.update("UPDATE se214.tai_khoan SET trang_thai = 'bi_khoa' WHERE id = ?", chuId);
        assertThat(kho.timAnhDeDoc(anhId).get().chuTinHoatDong()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("Cấu hình thật: lưu ảnh tạm, khởi tạo lại repository (restart) thì metadata và quyền đọc vẫn toàn vẹn")
    void kiemTraMetadataVaQuyenDocTonTaiSauRestart() {
        PostgreSqlAnhTinRepository kho1 = new PostgreSqlAnhTinRepository(jdbcTemplate);
        UUID chuId = taoTaiKhoan();
        UUID anhId = UUID.randomUUID();

        // 1. Upload và lưu vào PostgreSQL
        kho1.luuAnhTam(anhId, chuId, anhId + ".png", anhId + "-display.png", DinhDangAnh.PNG, 2048L);

        // 2. Giả lập khởi động lại ứng dụng (tạo thể hiện repository mới từ kết nối DB)
        PostgreSqlAnhTinRepository khoSauRestart = new PostgreSqlAnhTinRepository(jdbcTemplate);
        Optional<AnhTinTruyCap> anh = khoSauRestart.timAnhDeDoc(anhId);

        assertThat(anh).isPresent();
        assertThat(anh.get().chuAnhId()).isEqualTo(chuId);
        assertThat(anh.get().trangThaiAnh()).isEqualTo("tam");
        assertThat(anh.get().khoaTepGoc()).isEqualTo(anhId + ".png");
        assertThat(anh.get().khoaAnhHienThi()).isEqualTo(anhId + "-display.png");
        assertThat(anh.get().mimeType()).isEqualTo("image/png");

        // 3. Kiểm tra phân quyền đọc sau khi restart
        PrincipalPhienHienTai chuPrincipal = new PrincipalPhienHienTai(
                UUID.randomUUID(), chuId, "cookie-hash", "chu@test.com", "Chủ", "0900000000",
                VaiTroTaiKhoan.NGUOI_DUNG, com.realestates.backend.auth.domain.TrangThaiTaiKhoan.HOAT_DONG, true, 0L
        );
        PrincipalPhienHienTai nguoiKhacPrincipal = new PrincipalPhienHienTai(
                UUID.randomUUID(), UUID.randomUUID(), "cookie-hash-2", "khac@test.com", "Khác", "0900000001",
                VaiTroTaiKhoan.NGUOI_DUNG, com.realestates.backend.auth.domain.TrangThaiTaiKhoan.HOAT_DONG, true, 0L
        );

        // Chủ sở hữu đọc được ảnh tạm của chính mình
        assertThat(QuyenDocAnh.duocPhepDoc(anh.get(), chuPrincipal)).isTrue();
        // Người khác hoặc khách vãng lai không được đọc ảnh tạm
        assertThat(QuyenDocAnh.duocPhepDoc(anh.get(), nguoiKhacPrincipal)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh.get(), null)).isFalse();
    }

    @Test
    @DisplayName("Wiring PostgreSQL thật: lưu TaiKhoan khớp V1, mở transaction thực cho ghi kèm kiểm quyền và chặn race đăng xuất")
    void cauHinhPostgresThat_luuTaiKhoanGiaoDichVaChanRaceLogout() throws Exception {
        assertThat(anhTinRepository).isInstanceOf(PostgreSqlAnhTinRepository.class);
        assertThat(anhStoragePort).isInstanceOf(SupabaseStorageRestClientAdapter.class);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        UUID taiKhoanId = UUID.randomUUID();
        UUID phien1Id = UUID.randomUUID();
        UUID phien2Id = UUID.randomUUID();
        UUID anhCommitId = UUID.randomUUID();
        UUID anhBiChanId = UUID.randomUUID();
        Instant now = Instant.now();

        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                taiKhoanId,
                UUID.randomUUID(),
                "pg-real-" + taiKhoanId + "@bds-test.local",
                "Người dùng PG thật",
                "0909000111",
                VaiTroTaiKhoan.NGUOI_DUNG,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L,
                "san_sang",
                now,
                now,
                now
        );

        String rawCookie1 = sessionLifecyclePort.generateRawSessionCookie();
        String hashCookie1 = sessionLifecyclePort.hashSessionCookie(rawCookie1);
        PhienXacThuc phien1 = PhienXacThuc.taoPhienMoi(
                phien1Id,
                taiKhoanId,
                hashCookie1,
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );

        String rawCookie2 = sessionLifecyclePort.generateRawSessionCookie();
        String hashCookie2 = sessionLifecyclePort.hashSessionCookie(rawCookie2);
        PhienXacThuc phien2 = PhienXacThuc.taoPhienMoi(
                phien2Id,
                taiKhoanId,
                hashCookie2,
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );

        try {
            // 1. PostgreSqlTaiKhoanRepository hoạt động chuẩn với V1 (không lỗi cột tao_luc/cap_nhat_luc)
            taiKhoanRepository.save(taiKhoan);
            assertThat(taiKhoanRepository.findById(taiKhoanId)).isPresent();
            UUID opId = UUID.randomUUID();
            assertThat(taiKhoanRepository.updateDongBoAuthCas(taiKhoanId, null, "dang_dat_lai", opId, now)).isTrue();
            assertThat(taiKhoanRepository.updateDongBoAuthCas(taiKhoanId, opId, "san_sang", null, now)).isTrue();

            phienXacThucRepository.save(phien1);
            phienXacThucRepository.save(phien2);

            // 2. Bên trong thucThiGhiCoKiemLaiQuyenTrongTransaction có transaction DB thật và khóa chặn logout chen ngang
            AtomicBoolean txActive = new AtomicBoolean(false);
            CompletableFuture<Void> logoutGiuaChung = new CompletableFuture<>();
            AtomicBoolean logoutCompletedBeforeInsertCommit = new AtomicBoolean(false);

            permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawCookie1,
                    ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                    null,
                    null,
                    Map.of("tep", "anh"),
                    fresh -> {
                        txActive.set(TransactionSynchronizationManager.isActualTransactionActive());
                        Thread logoutThread = new Thread(() -> {
                            try {
                                authLogoutService.dangXuat(rawCookie1);
                                logoutGiuaChung.complete(null);
                            } catch (Throwable t) {
                                logoutGiuaChung.completeExceptionally(t);
                            }
                        });
                        logoutThread.start();
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        logoutCompletedBeforeInsertCommit.set(logoutGiuaChung.isDone());
                        anhTinRepository.luuAnhTam(
                                anhCommitId,
                                fresh.getTaiKhoanId(),
                                anhCommitId + ".png",
                                anhCommitId + "-display.png",
                                DinhDangAnh.PNG,
                                512L
                        );
                        return null;
                    }
            );

            assertThat(txActive.get()).isTrue();
            assertThat(logoutCompletedBeforeInsertCommit.get()).isFalse();
            logoutGiuaChung.get(5, TimeUnit.SECONDS);

            // Dữ liệu tồn tại bền vững sau commit ngoài mọi @Transactional test wrapper
            Optional<AnhTinTruyCap> sauCommit = anhTinRepository.timAnhDeDoc(anhCommitId);
            assertThat(sauCommit).isPresent();
            assertThat(sauCommit.get().khoaAnhHienThi()).isEqualTo(anhCommitId + "-display.png");

            // 3. Nếu phiên đã đăng xuất xong trước khi ghi, transaction kiểm quyền từ chối và không chèn dòng
            authLogoutService.dangXuat(rawCookie2);
            assertThatThrownBy(() -> permissionAndCsrfService.thucThiGhiCoKiemLaiQuyenTrongTransaction(
                    rawCookie2,
                    ThaoTacNghiepVu.TAO_NHAP_CAP_NHAT_NHAP_GUI_DUYET_TIN,
                    null,
                    null,
                    Map.of("tep", "anh"),
                    fresh -> {
                        anhTinRepository.luuAnhTam(
                                anhBiChanId,
                                fresh.getTaiKhoanId(),
                                anhBiChanId + ".png",
                                anhBiChanId + "-display.png",
                                DinhDangAnh.PNG,
                                512L
                        );
                        return null;
                    }
            )).isInstanceOf(UnauthenticatedSessionException.class);
            assertThat(anhTinRepository.timAnhDeDoc(anhBiChanId)).isEmpty();
        } finally {
            jdbcTemplate.update("DELETE FROM se214.anh_tin WHERE id IN (?, ?)", anhCommitId, anhBiChanId);
            jdbcTemplate.update("DELETE FROM se214.phien_xac_thuc WHERE tai_khoan_id = ?", taiKhoanId);
            jdbcTemplate.update("DELETE FROM se214.tai_khoan WHERE id = ?", taiKhoanId);
        }
    }
}

