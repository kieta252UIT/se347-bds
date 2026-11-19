package com.realestates.backend.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.listing.application.AnhStoragePort;
import com.realestates.backend.listing.application.AnhTinService;
import com.realestates.backend.listing.application.BanDoPort;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.controller.AnhTinController;
import com.realestates.backend.listing.controller.TinDangController;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.exception.ListingConflictException;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.listing.exception.MapServiceUnavailableException;
import com.realestates.backend.listing.infrastructure.InMemoryAnhTinRepository;
import com.realestates.backend.listing.infrastructure.InMemoryTinDangRepository;
import com.realestates.backend.listing.infrastructure.MacDinhBanDoAdapter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TinDangApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private InMemoryAnhTinRepository anhTinRepository;
    private InMemoryTinDangRepository tinDangRepository;
    private KhoAnhGia khoAnh;
    private DongHoDieuChinhDuoc dongHo;
    private AnhTinService anhTinService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        cookieConfig = taoCookieConfig();
        permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ORIGIN)
        );
        anhTinRepository = new InMemoryAnhTinRepository();
        tinDangRepository = new InMemoryTinDangRepository(anhTinRepository);
        khoAnh = new KhoAnhGia();
        dongHo = new DongHoDieuChinhDuoc(Instant.parse("2026-11-15T10:00:00Z"));
        anhTinService = new AnhTinService(permissionAndCsrfService, anhTinRepository, khoAnh, 60L);
        mockMvc = taoMockMvc(new MacDinhBanDoAdapter("sandbox"));
    }

    private MockMvc taoMockMvc(BanDoPort banDoPort) {
        TinDangService tinDangService = new TinDangService(permissionAndCsrfService, tinDangRepository, banDoPort, dongHo);
        return MockMvcBuilders.standaloneSetup(
                        new TinDangController(tinDangService, cookieConfig),
                        new AnhTinController(anhTinService, cookieConfig)
                )
                .setControllerAdvice(
                        new AuthSessionSecurityExceptionHandler(),
                        new AuthExceptionHandler(),
                        new ListingExceptionHandler(),
                        new GlobalApiExceptionHandler()
                )
                .addFilters(new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig))
                .build();
    }

    @Test
    @DisplayName("TS1-021: Đăng tin hợp lệ tạo tin chờ duyệt, gắn 12 ảnh theo thứ tự và mở quyền đọc ảnh cho chủ tin và kiểm duyệt viên")
    void dangTinHopLeVaPhanQuyenDocAnhSauKhiGan() throws Exception {
        Fixture chuTin = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        Fixture kdv = taoNguoiDung(VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        Fixture khachDangNhap = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);

        List<String> anhIds = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            anhIds.add(luuAnhTamCho(chuTin.taiKhoan().getId(), new byte[]{(byte) (i + 1)}).toString());
        }

        Map<String, Object> payload = duLieuTinHopLe(anhIds);
        MvcResult ketQua = mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "idem-ok-12-anh", payload, true))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.tinId").isNotEmpty())
                .andExpect(jsonPath("$.phienBanHienTaiId").isNotEmpty())
                .andExpect(jsonPath("$.trangThai").value("cho_kiem_duyet"))
                .andExpect(jsonPath("$.phienBanCapNhat").value(0))
                .andReturn();

        String body = ketQua.getResponse().getContentAsString();
        UUID tinId = UUID.fromString(JsonPath.read(body, "$.tinId"));
        UUID phienBanId = UUID.fromString(JsonPath.read(body, "$.phienBanHienTaiId"));
        assertThat(ketQua.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/me/listings/" + tinId);

        assertThat(tinDangRepository.timTinTheoId(tinId)).isPresent()
                .get()
                .satisfies(tin -> {
                    assertThat(tin.chuId()).isEqualTo(chuTin.taiKhoan().getId());
                    assertThat(tin.trangThai()).isEqualTo("cho_kiem_duyet");
                    assertThat(tin.phienBanHienTaiId()).isEqualTo(phienBanId);
                    assertThat(tin.phienBanDaDuyetId()).isNull();
                });
        assertThat(tinDangRepository.timPhienBanTheoId(phienBanId)).isPresent()
                .get()
                .satisfies(pb -> {
                    assertThat(pb.soPhienBan()).isEqualTo(1);
                    assertThat(pb.ketQuaSoBo()).isEqualTo("dat");
                    assertThat(pb.ketQuaKiemDuyet()).isEqualTo("cho_kiem_duyet");
                });
        assertThat(tinDangRepository.layAuditTheoTinId(tinId)).hasSize(1);
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chuTin.taiKhoan().getId(), LocalDate.of(2026, 11, 1)))
                .isEqualTo(1);

        // Kiểm tra quyền đọc ảnh sau khi đã gắn vào tin chờ duyệt
        String anhDau = anhIds.get(0);
        AnhTinTruyCap daGan = anhTinRepository.timAnhDeDoc(UUID.fromString(anhDau)).orElseThrow();
        assertThat(daGan.trangThaiAnh()).isEqualTo(AnhTinTruyCap.ANH_DA_GAN);
        assertThat(daGan.phienBanTinId()).isEqualTo(phienBanId);

        mockMvc.perform(get("/api/images/{id}/content", anhDau)
                        .cookie(new Cookie(cookieConfig.getSessionCookieName(), chuTin.rawSessionCookie())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/images/{id}/content", anhDau)
                        .cookie(new Cookie(cookieConfig.getSessionCookieName(), kdv.rawSessionCookie())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/images/{id}/content", anhDau)
                        .cookie(new Cookie(cookieConfig.getSessionCookieName(), khachDangNhap.rawSessionCookie())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/images/{id}/content", anhDau))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Kiểm tra xác thực, phân quyền, khóa tài khoản và CSRF trước khi tạo tin")
    void xacThucVaPhanQuyen() throws Exception {
        Fixture chuTin = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        Fixture kdv = taoNguoiDung(VaiTroTaiKhoan.KIEM_DUYET_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        Fixture admin = taoNguoiDung(VaiTroTaiKhoan.QUAN_TRI_VIEN, TrangThaiTaiKhoan.HOAT_DONG, true);
        Fixture biKhoa = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.BI_KHOA, true);
        Fixture chuaXacMinhEmail = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, false);

        UUID anhId = luuAnhTamCho(chuTin.taiKhoan().getId(), new byte[]{9});
        Map<String, Object> payload = duLieuTinHopLe(List.of(anhId.toString()));

        mockMvc.perform(yeuCauDangTin(null, "k-401", payload, true))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-csrf", payload, false))
                .andExpect(status().isForbidden());

        mockMvc.perform(yeuCauDangTin(kdv.rawSessionCookie(), "k-kdv", payload, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        mockMvc.perform(yeuCauDangTin(admin.rawSessionCookie(), "k-admin", payload, true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        mockMvc.perform(yeuCauDangTin(biKhoa.rawSessionCookie(), "k-khoa", payload, true))
                .andExpect(status().isForbidden());

        mockMvc.perform(yeuCauDangTin(chuaXacMinhEmail.rawSessionCookie(), "k-email", payload, true))
                .andExpect(status().isForbidden());

        // Không có bất kỳ thay đổi nào được ghi xuống kho dữ liệu
        assertThat(anhTinRepository.timAnhDeDoc(anhId).orElseThrow().trangThaiAnh()).isEqualTo(AnhTinTruyCap.ANH_TAM);
    }

    @Test
    @DisplayName("TS1-022..024: Từ chối thiếu Idempotency-Key, sai nội dung, sai số lượng ảnh, ảnh không hợp lệ và lỗi dịch vụ bản đồ")
    void tuChoiDuLieuVaBanDo() throws Exception {
        Fixture chuTin = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);
        Fixture nguoiKhac = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);

        // Tạo riêng 1 ảnh hết hạn và gặp lỗi xóa storage -> chuyển sang cho_don trước khi tạo ảnh hợp lệ
        UUID anhChoDon = luuAnhTamCho(chuTin.taiKhoan().getId(), new byte[]{3});
        khoAnh.loiKhiXoa = true;
        anhTinService.donDepAnhTamQuaHan(Instant.now().plusSeconds(3700));
        khoAnh.loiKhiXoa = false;
        assertThat(anhTinRepository.timAnhDeDoc(anhChoDon).orElseThrow().trangThaiAnh()).isEqualTo("cho_don");

        UUID anhChu = luuAnhTamCho(chuTin.taiKhoan().getId(), new byte[]{1});
        UUID anhNguoiKhac = luuAnhTamCho(nguoiKhac.taiKhoan().getId(), new byte[]{2});

        // 1. Thiếu / rỗng Idempotency-Key -> 400
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "   ", duLieuTinHopLe(List.of(anhChu.toString())), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));

        // 2. Tự gán chuId / trường lạ -> 400
        Map<String, Object> truongLa = duLieuTinHopLe(List.of(anhChu.toString()));
        truongLa.put("chuId", UUID.randomUUID().toString());
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-truong-la", truongLa, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));

        // 3. Sai đơn vị giá theo loại giao dịch -> 400
        Map<String, Object> saiDonVi = duLieuTinHopLe(List.of(anhChu.toString()));
        saiDonVi.put("loaiGiaoDich", "cho_thue");
        saiDonVi.put("donViGia", "vnd_tong");
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-don-vi", saiDonVi, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));

        // 4. 0 ảnh, 13 ảnh, ảnh trùng -> 400
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-0-anh", duLieuTinHopLe(List.of()), true))
                .andExpect(status().isBadRequest());

        List<String> muoiBaAnh = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            muoiBaAnh.add(UUID.randomUUID().toString());
        }
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-13-anh", duLieuTinHopLe(muoiBaAnh), true))
                .andExpect(status().isBadRequest());

        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-trung-anh",
                        duLieuTinHopLe(List.of(anhChu.toString(), anhChu.toString())), true))
                .andExpect(status().isBadRequest());

        // 5. Ảnh không tồn tại, ảnh của người khác, ảnh đã chuyển cho_don -> 400
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-missing-img",
                        duLieuTinHopLe(List.of(UUID.randomUUID().toString())), true))
                .andExpect(status().isBadRequest());

        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-other-img",
                        duLieuTinHopLe(List.of(anhNguoiKhac.toString())), true))
                .andExpect(status().isBadRequest());

        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-cho-don-img",
                        duLieuTinHopLe(List.of(anhChoDon.toString())), true))
                .andExpect(status().isBadRequest());

        // 6. Các chế độ BanDoPort (fail -> 400, unconfigured -> 503 BAN_DO_CHUA_SAN_SANG, unavailable -> 503 TAM_GIAN_DOAN)
        MockMvc mapFailMvc = taoMockMvc(new MacDinhBanDoAdapter("fail"));
        mapFailMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-map-fail", duLieuTinHopLe(List.of(anhChu.toString())), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));

        MockMvc mapUnconfiguredMvc = taoMockMvc(new MacDinhBanDoAdapter("unconfigured"));
        mapUnconfiguredMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-map-unconf", duLieuTinHopLe(List.of(anhChu.toString())), true))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value(MapServiceUnavailableException.MA_LOI_BAN_DO_CHUA_SAN_SANG));

        MockMvc mapUnavailableMvc = taoMockMvc(new MacDinhBanDoAdapter("unavailable"));
        mapUnavailableMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "k-map-unavail", duLieuTinHopLe(List.of(anhChu.toString())), true))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value(MapServiceUnavailableException.MA_LOI_TAM_GIAN_DOAN));

        // Đảm bảo ảnh hợp lệ vẫn còn nguyên trạng thái tam sau mọi lỗi kiểm tra
        assertThat(anhTinRepository.timAnhDeDoc(anhChu).orElseThrow().trangThaiAnh()).isEqualTo(AnhTinTruyCap.ANH_TAM);
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chuTin.taiKhoan().getId(), LocalDate.of(2026, 11, 1))).isZero();
    }

    @Test
    @DisplayName("TS1-025 & TS1-027: Giới hạn 3 tin/tháng theo giờ Việt Nam, vượt hạn mức trả 409 và replay/xung đột Idempotency-Key")
    void hanMucThangMuiGioVietNamVaIdempotencyReplay() throws Exception {
        Fixture chuTin = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true);

        // 23:59:59 30/11/2026 giờ Việt Nam (+07:00) == 2026-11-30T16:59:59Z
        dongHo.datThoiDiem(Instant.parse("2026-11-30T16:59:59Z"));

        String bodyTinDau = null;
        Map<String, Object> payloadTinDau = null;
        for (int i = 1; i <= 3; i++) {
            UUID anhId = luuAnhTamCho(chuTin.taiKhoan().getId(), new byte[]{(byte) (10 + i)});
            Map<String, Object> p = duLieuTinHopLe(List.of(anhId.toString()));
            p.put("tieuDe", "Tin số " + i + " cuối tháng 11");
            String resp = mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "idem-nov-" + i, p, true))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            if (i == 1) {
                bodyTinDau = resp;
                payloadTinDau = p;
            }
        }
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chuTin.taiKhoan().getId(), LocalDate.of(2026, 11, 1)))
                .isEqualTo(3);

        // Tin thứ 4 cùng phút cuối tháng 11 -> 409 HAN_MUC_THANG
        UUID anhThu4 = luuAnhTamCho(chuTin.taiKhoan().getId(), new byte[]{99});
        Map<String, Object> payloadThu4 = duLieuTinHopLe(List.of(anhThu4.toString()));
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "idem-nov-4", payloadThu4, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value(ListingConflictException.MA_LOI_HAN_MUC_THANG));
        assertThat(anhTinRepository.timAnhDeDoc(anhThu4).orElseThrow().trangThaiAnh()).isEqualTo(AnhTinTruyCap.ANH_TAM);

        // Replay cùng Idempotency-Key & payload giống hệt tin đầu (dù đã đạt hạn mức 3/3 và ảnh đã gắn!) -> vẫn replay 201 y hệt!
        String replayBody = mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "idem-nov-1", payloadTinDau, true))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(replayBody, "$.tinId"))
                .isEqualTo((String) JsonPath.read(bodyTinDau, "$.tinId"));
        assertThat((String) JsonPath.read(replayBody, "$.phienBanHienTaiId"))
                .isEqualTo((String) JsonPath.read(bodyTinDau, "$.phienBanHienTaiId"));
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chuTin.taiKhoan().getId(), LocalDate.of(2026, 11, 1)))
                .isEqualTo(3);

        // Cùng Idempotency-Key nhưng đổi payload -> 409 MA_Y_DINH_KHAC_NOI_DUNG
        Map<String, Object> payloadKhac = new LinkedHashMap<>(payloadTinDau);
        payloadKhac.put("tieuDe", "Tiêu đề đã sửa khác payload ban đầu");
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "idem-nov-1", payloadKhac, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value(ListingConflictException.MA_LOI_MA_Y_DINH_KHAC_NOI_DUNG));

        // Sang 00:00:00 01/12/2026 giờ Việt Nam (17:00:00Z) -> hạn mức tháng 12 là 0, anhThu4 đăng thành công!
        dongHo.datThoiDiem(Instant.parse("2026-11-30T17:00:00Z"));
        mockMvc.perform(yeuCauDangTin(chuTin.rawSessionCookie(), "idem-dec-1", payloadThu4, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trangThai").value("cho_kiem_duyet"));
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chuTin.taiKhoan().getId(), LocalDate.of(2026, 12, 1)))
                .isEqualTo(1);
    }

    private UUID luuAnhTamCho(UUID chuId, byte[] bytes) {
        UUID anhId = UUID.randomUUID();
        String khoaGoc = anhId + ".png";
        String khoaHienThi = anhId + "-display.png";
        khoAnh.doiTuong.put(khoaGoc, bytes);
        khoAnh.doiTuong.put(khoaHienThi, bytes);
        anhTinRepository.luuAnhTam(anhId, chuId, khoaGoc, khoaHienThi, DinhDangAnh.PNG, bytes.length);
        return anhId;
    }

    private static Map<String, Object> duLieuTinHopLe(List<String> anhIds) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("tieuDe", "Căn hộ 2 phòng ngủ view sông");
        duLieu.put("moTa", "Đầy đủ nội thất, sổ hồng chính chủ.");
        duLieu.put("loaiGiaoDich", "ban");
        duLieu.put("loaiBds", "can_ho");
        duLieu.put("giaGoc", "2500000000");
        duLieu.put("donViGia", "vnd_tong");
        duLieu.put("dienTichM2", "68.5");
        duLieu.put("diaChi", "12 Tôn Đức Thắng, Quận 1");
        duLieu.put("khuVuc", "TP. Hồ Chí Minh");
        duLieu.put("viDo", "10.7769");
        duLieu.put("kinhDo", "106.7009");
        duLieu.put("anhIds", anhIds);
        return duLieu;
    }

    private MockHttpServletRequestBuilder yeuCauDangTin(
            String rawSessionCookie,
            String idempotencyKey,
            Map<String, Object> payload,
            boolean kemCsrf
    ) {
        var builder = post("/api/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ChuanHoaJson.toJsonString(payload))
                .header(HttpHeaders.ORIGIN, ORIGIN);
        if (idempotencyKey != null) {
            builder.header(TinDangService.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        }
        String csrf = rawSessionCookie != null
                ? sessionLifecycleAdapter.issueSessionCsrfToken(rawSessionCookie)
                : sessionLifecycleAdapter.issueAnonymousCsrfToken();
        if (kemCsrf) {
            builder.header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf);
            builder.cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf));
        }
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    private Fixture taoNguoiDung(VaiTroTaiKhoan vaiTro, TrangThaiTaiKhoan trangThai, boolean emailDaXacMinh) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "nguoi-dang-" + UUID.randomUUID() + "@bds-test.local",
                "Người đăng thử",
                "0901234567",
                vaiTro,
                trangThai,
                emailDaXacMinh,
                0L,
                "san_sang",
                now,
                now,
                now
        );
        taiKhoanRepository.save(taiKhoan);

        String rawSessionCookie = sessionLifecycleAdapter.generateRawSessionCookie();
        PhienXacThuc phien = PhienXacThuc.taoPhienMoi(
                UUID.randomUUID(),
                taiKhoan.getId(),
                sessionLifecycleAdapter.hashSessionCookie(rawSessionCookie),
                0L,
                now,
                UUID.randomUUID(),
                "cipher-access".getBytes(StandardCharsets.UTF_8),
                "cipher-refresh".getBytes(StandardCharsets.UTF_8),
                now.plusSeconds(3600),
                "v1"
        );
        phienXacThucRepository.save(phien);
        return new Fixture(taiKhoan, rawSessionCookie);
    }

    private static AuthSessionCookieConfig taoCookieConfig() {
        AuthSessionCookieConfig config = new AuthSessionCookieConfig();
        ReflectionTestUtils.setField(config, "cookieSecure", true);
        ReflectionTestUtils.setField(config, "sessionCookieName", AuthSessionCookieConfig.SESSION_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionMaxAgeSeconds", AuthSessionCookieConfig.SESSION_MAX_AGE_SECONDS);
        ReflectionTestUtils.setField(config, "csrfCookieName", AuthSessionCookieConfig.CSRF_COOKIE_NAME);
        ReflectionTestUtils.setField(config, "sessionPersistent", true);
        return config;
    }

    private record Fixture(TaiKhoan taiKhoan, String rawSessionCookie) {
    }

    private static final class DongHoDieuChinhDuoc extends Clock {

        private final AtomicReference<Instant> hienTai;

        private DongHoDieuChinhDuoc(Instant khoiTao) {
            this.hienTai = new AtomicReference<>(khoiTao);
        }

        private void datThoiDiem(Instant moi) {
            hienTai.set(moi);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return hienTai.get();
        }
    }

    private static final class KhoAnhGia implements AnhStoragePort {

        private final Map<String, byte[]> doiTuong = new ConcurrentHashMap<>();
        private volatile boolean loiKhiXoa;

        @Override
        public void luu(String khoaDoiTuong, byte[] noiDung, String mimeType) {
            doiTuong.put(khoaDoiTuong, noiDung);
        }

        @Override
        public byte[] doc(String khoaDoiTuong) {
            byte[] data = doiTuong.get(khoaDoiTuong);
            if (data == null) {
                throw new AuthProviderUnavailableException("Storage giả không đọc được.");
            }
            return data;
        }

        @Override
        public void xoa(String khoaDoiTuong) {
            if (loiKhiXoa) {
                throw new AuthProviderUnavailableException("Storage giả lỗi khi xóa.");
            }
            if (khoaDoiTuong != null) {
                doiTuong.remove(khoaDoiTuong);
            }
        }
    }
}
