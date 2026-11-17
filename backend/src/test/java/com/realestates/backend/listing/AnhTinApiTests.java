package com.realestates.backend.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
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
import com.realestates.backend.listing.controller.AnhTinController;
import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.infrastructure.InMemoryAnhTinRepository;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnhTinApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private CurrentPermissionAndCsrfService permissionAndCsrfService;
    private KhoAnhGia khoAnh;
    private InMemoryAnhTinRepository anhTinRepository;
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
        khoAnh = new KhoAnhGia();
        anhTinRepository = new InMemoryAnhTinRepository();
        mockMvc = taoMockMvc(anhTinRepository, khoAnh);
    }

    private MockMvc taoMockMvc(AnhTinRepository anhTinRepository, AnhStoragePort anhStoragePort) {
        AnhTinService anhTinService = new AnhTinService(permissionAndCsrfService, anhTinRepository, anhStoragePort);
        return MockMvcBuilders.standaloneSetup(new AnhTinController(anhTinService, cookieConfig))
                .setControllerAdvice(new AuthSessionSecurityExceptionHandler(), new AuthExceptionHandler(), new GlobalApiExceptionHandler())
                .addFilters(new CsrfAndOriginProtectionFilter(permissionAndCsrfService, cookieConfig))
                .build();
    }

    @Test
    @DisplayName("Người dùng đăng nhập tải ảnh PNG hợp lệ nhận 201, ảnh được lưu vào Storage")
    void nguoiDungTaiAnhHopLe() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        byte[] png = taoAnh("png");

        String json = mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", png)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.trangThai").value("tam"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.kichThuocByte").value(png.length))
                .andReturn().getResponse().getContentAsString();

        String anhId = JsonPath.read(json, "$.id");
        assertThat(JsonPath.<String>read(json, "$.duongDanDoc")).isEqualTo("/api/images/" + anhId + "/content");
        assertThat(khoAnh.doiTuong.get(anhId + ".png")).isEqualTo(png);
        assertThat(khoAnh.doiTuong.containsKey(anhId + "-display.png")).isTrue();

        Optional<AnhTinTruyCap> daLuu = anhTinRepository.timAnhDeDoc(UUID.fromString(anhId));
        assertThat(daLuu).isPresent();
        assertThat(daLuu.get().chuAnhId()).isEqualTo(nguoiDung.taiKhoan().getId());
        assertThat(daLuu.get().trangThaiAnh()).isEqualTo("tam");
        assertThat(daLuu.get().khoaAnhHienThi()).isEqualTo(anhId + "-display.png");
    }

    @Test
    @DisplayName("Chưa đăng nhập tải ảnh nhận 401 và không lưu gì")
    void chuaDangNhapBi401() throws Exception {
        mockMvc.perform(yeuCauTai(null, tep("tep", "anh.png", "image/png", taoAnh("png"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Kiểm duyệt viên không có quyền tải ảnh tin nhận 403")
    void kiemDuyetVienBi403() throws Exception {
        Fixture kiemDuyetVien = taoNguoiDung(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        mockMvc.perform(yeuCauTai(kiemDuyetVien.rawSessionCookie(), tep("tep", "anh.png", "image/png", taoAnh("png"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Thiếu CSRF bị chặn 403 trước khi xử lý ảnh")
    void thieuCsrfBi403() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);

        mockMvc.perform(multipart("/api/images")
                        .file(tep("tep", "anh.png", "image/png", taoAnh("png")))
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, nguoiDung.rawSessionCookie())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("CSRF_KHONG_HOP_LE"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Tệp PDF đặt đuôi png bị chặn 400 và không lưu gì")
    void tepPdfGiaDuoiBi400() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        byte[] pdf = "%PDF-1.4\n1 0 obj<<>>endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

        mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", pdf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("tep"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Tệp rỗng và tệp vượt 10.000.000 byte bị chặn 400")
    void tepRongVaTepQuaLonBi400() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        byte[] png = taoAnh("png");
        byte[] quaLon = new byte[10_000_001];
        System.arraycopy(png, 0, quaLon, 0, png.length);

        mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("tep"));
        mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", quaLon)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("tep"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Gửi hai tệp hoặc sai tên trường bị chặn 400")
    void saiSoLuongTepBi400() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        byte[] png = taoAnh("png");

        mockMvc.perform(yeuCauTai(
                        nguoiDung.rawSessionCookie(),
                        tep("tep", "a.png", "image/png", png),
                        tep("tep", "b.png", "image/png", png)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));
        mockMvc.perform(yeuCauTai(
                        nguoiDung.rawSessionCookie(),
                        tep("tep", "a.png", "image/png", png),
                        tep("khac", "b.png", "image/png", png)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("file", "a.png", "image/png", png)))
                .andExpect(status().isBadRequest());

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Body không phải multipart bị chặn 400")
    void bodyKhongPhaiMultipartBi400() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        String csrf = sessionLifecycleAdapter.issueSessionCsrfToken(nguoiDung.rawSessionCookie());

        mockMvc.perform(post("/api/images")
                        .contentType("application/json")
                        .content("{}")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf)
                        .cookie(
                                new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, nguoiDung.rawSessionCookie()),
                                new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("Storage lỗi trả 503 TAM_GIAN_DOAN và không ghi metadata")
    void storageLoiBi503() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        khoAnh.loi = true;

        mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", taoAnh("png"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value("TAM_GIAN_DOAN"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Ghi metadata lỗi trả 503 TAM_GIAN_DOAN")
    void ghiMetadataLoiBi503() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        AnhTinRepository khoLoi = new AnhTinRepository() {
            @Override
            public void luuAnhTam(UUID id, UUID chuId, String khoaTepGoc, String khoaAnhHienThi, DinhDangAnh dinhDang, long kichThuocByte) {
                throw new IllegalStateException("Mất kết nối cơ sở dữ liệu");
            }

            @Override
            public Optional<AnhTinTruyCap> timAnhDeDoc(UUID anhId) {
                return Optional.empty();
            }

            @Override
            public boolean coThamChieuKhoa(String khoaDoiTuong) {
                return false;
            }
        };
        MockMvc mockMvcKhoLoi = taoMockMvc(khoLoi, khoAnh);

        mockMvcKhoLoi.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", taoAnh("png"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value("TAM_GIAN_DOAN"));
    }

    @Test
    @DisplayName("Chủ đọc được ảnh tạm của mình qua backend với đúng kiểu nội dung và no-store")
    void chuDocDuocAnhTam() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        byte[] png = taoAnh("png");
        String anhId = taiAnhLen(chu, png);

        mockMvc.perform(get("/api/images/" + anhId + "/content")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, chu.rawSessionCookie())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(png));
    }

    @Test
    @DisplayName("Khách, người dùng khác và kiểm duyệt viên không đọc được ảnh tạm, nhận 404")
    void nguoiKhacKhongDocDuocAnhTam() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        Fixture nguoiKhac = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        Fixture kiemDuyetVien = taoNguoiDung(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        String anhId = taiAnhLen(chu, taoAnh("png"));

        mockMvc.perform(get("/api/images/" + anhId + "/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
        mockMvc.perform(get("/api/images/" + anhId + "/content")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, nguoiKhac.rawSessionCookie())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/images/" + anhId + "/content")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, kiemDuyetVien.rawSessionCookie())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cookie phiên không hợp lệ được coi như khách, ảnh tạm trả 404 chứ không lỗi")
    void cookieKhongHopLeLaKhach() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        String anhId = taiAnhLen(chu, taoAnh("png"));

        mockMvc.perform(get("/api/images/" + anhId + "/content")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, "cookie-khong-ton-tai")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ảnh không tồn tại nhận 404, mã ảnh sai định dạng nhận 400")
    void anhKhongTonTaiVaMaSaiDinhDang() throws Exception {
        mockMvc.perform(get("/api/images/" + UUID.randomUUID() + "/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
        mockMvc.perform(get("/api/images/khong-phai-uuid/content"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("Storage lỗi khi đọc ảnh của chủ trả 503 TAM_GIAN_DOAN")
    void storageLoiKhiDocBi503() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        String anhId = taiAnhLen(chu, taoAnh("png"));
        khoAnh.loi = true;

        mockMvc.perform(get("/api/images/" + anhId + "/content")
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, chu.rawSessionCookie())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value("TAM_GIAN_DOAN"));
    }

    @Test
    @DisplayName("Bù trừ xóa đối tượng trên Storage khi ghi cơ sở dữ liệu gặp lỗi")
    void buTruXoaStorageKhiGhiRepoLoi() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        AnhTinRepository repoLoi = new InMemoryAnhTinRepository() {
            @Override
            public void luuAnhTam(UUID id, UUID chuId, String khoaTepGoc, String khoaAnhHienThi, DinhDangAnh dinhDang, long kichThuocByte) {
                throw new AuthProviderUnavailableException("DB gặp sự cố.");
            }
        };
        MockMvc mvcVoiRepoLoi = taoMockMvc(repoLoi, khoAnh);

        mvcVoiRepoLoi.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", taoAnh("png"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maLoi").value("TAM_GIAN_DOAN"));

        // Xác nhận cơ chế bù trừ đã xóa sạch các object vừa tải lên storage
        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Gửi sai Content-Type ở endpoint tải ảnh trả về thông điệp kiểu nội dung không hỗ trợ")
    void saiContentTypeTra400VoiThongDiepDung() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        String csrf = sessionLifecycleAdapter.issueSessionCsrfToken(nguoiDung.rawSessionCookie());

        mockMvc.perform(post("/api/images")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"tep\": \"test\"}")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf)
                        .cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf))
                        .cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, nguoiDung.rawSessionCookie())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.thongDiep").value("Kiểu nội dung yêu cầu không được hỗ trợ."));
    }

    @Test
    @DisplayName("Kiểm lại quyền trong transaction: tài khoản bị khóa giữa chừng thì từ chối ghi và dọn Storage")
    void kiemLaiQuyenKhiTaiKhoanBiKhoaGiuaChung() throws Exception {
        Fixture nguoiDung = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);

        khoAnh.hanhDongKhiLuu = () -> {
            TaiKhoan current = nguoiDung.taiKhoan();
            TaiKhoan biKhoa = TaiKhoan.khoiPhucTuLuuTru(
                    current.getId(),
                    current.getSupabaseAuthUserId(),
                    current.getEmail(),
                    current.getHoTen(),
                    current.getSoDienThoai(),
                    current.getVaiTro(),
                    TrangThaiTaiKhoan.BI_KHOA,
                    current.isEmailDaXacMinh(),
                    current.getMocThuHoi(),
                    current.getTrangThaiDongBoAuth(),
                    current.getMaThaoTacAuth(),
                    current.getCapNhatAuthLuc(),
                    current.getTaoLuc(),
                    Instant.now()
            );
            taiKhoanRepository.save(biKhoa);
        };

        mockMvc.perform(yeuCauTai(nguoiDung.rawSessionCookie(), tep("tep", "anh.png", "image/png", taoAnh("png"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("TAI_KHOAN_BI_KHOA"));

        assertThat(khoAnh.doiTuong.isEmpty()).isTrue();
    }

    private String taiAnhLen(Fixture nguoiTai, byte[] noiDung) throws Exception {
        String json = mockMvc.perform(yeuCauTai(nguoiTai.rawSessionCookie(), tep("tep", "anh.png", "image/png", noiDung)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private RequestBuilder yeuCauTai(String rawSessionCookie, MockMultipartFile... cacTep) {
        var builder = multipart("/api/images");
        for (MockMultipartFile tep : cacTep) {
            builder.file(tep);
        }
        String csrf = rawSessionCookie != null
                ? sessionLifecycleAdapter.issueSessionCsrfToken(rawSessionCookie)
                : sessionLifecycleAdapter.issueAnonymousCsrfToken();
        builder.header(HttpHeaders.ORIGIN, ORIGIN);
        builder.header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf);
        builder.cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf));
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(AuthSessionCookieConfig.SESSION_COOKIE_NAME, rawSessionCookie));
        }
        return builder;
    }

    private static MockMultipartFile tep(String tenTruong, String tenTep, String kieuNoiDung, byte[] noiDung) {
        return new MockMultipartFile(tenTruong, tenTep, kieuNoiDung, noiDung);
    }

    private static byte[] taoAnh(String dinhDang) throws IOException {
        BufferedImage anh = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream dauRa = new ByteArrayOutputStream();
        ImageIO.write(anh, dinhDang, dauRa);
        return dauRa.toByteArray();
    }

    private Fixture taoNguoiDung(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "nguoi-dung-" + UUID.randomUUID() + "@bds-test.local",
                "Người dùng thử",
                "0901234567",
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

    private static class KhoAnhGia implements AnhStoragePort {

        private final Map<String, byte[]> doiTuong = new ConcurrentHashMap<>();
        private volatile boolean loi;
        private volatile Runnable hanhDongKhiLuu;

        @Override
        public void luu(String khoaDoiTuong, byte[] noiDung, String mimeType) {
            if (loi) {
                throw new AuthProviderUnavailableException("Storage giả lập đang lỗi.");
            }
            doiTuong.put(khoaDoiTuong, noiDung);
            if (hanhDongKhiLuu != null) {
                hanhDongKhiLuu.run();
            }
        }

        @Override
        public byte[] doc(String khoaDoiTuong) {
            byte[] noiDung = loi ? null : doiTuong.get(khoaDoiTuong);
            if (noiDung == null) {
                throw new AuthProviderUnavailableException("Storage giả lập không đọc được.");
            }
            return noiDung;
        }

        @Override
        public void xoa(String khoaDoiTuong) {
            if (khoaDoiTuong != null) {
                doiTuong.remove(khoaDoiTuong);
            }
        }
    }
}

