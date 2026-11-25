package com.realestates.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.controller.CsrfAndOriginProtectionFilter;
import com.realestates.backend.auth.domain.PhienXacThuc;
import com.realestates.backend.auth.domain.TaiKhoan;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.controller.KiemDuyetController;
import com.realestates.backend.moderation.domain.KiemDuyetRepository;
import com.realestates.backend.moderation.dto.AnhHienThi;
import com.realestates.backend.moderation.dto.GiaQuyDoiHienThi;
import com.realestates.backend.moderation.dto.PhienBanNoiDung;
import com.realestates.backend.moderation.dto.TinQuanLy;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * API kiểm duyệt theo openapi-sprint-1.yaml qua MockMvc độc lập (không DB): đường dẫn, phân quyền, ETag,
 * cấu trúc TrangTinQuanLy/TinQuanLy/TinGhiKetQua, QuyetDinhRequest additionalProperties:false và ánh xạ 428/412/409/400/404.
 * Kho dữ liệu được thay bằng bản giả giữ cố định phienBanCapNhat = 7.
 */
class KiemDuyetApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final long CAP_NHAT_HIEN_TAI = 7L;
    private static final Instant GOC = Instant.parse("2026-11-18T03:00:00Z");
    private static final String DS = "/api/moderation/listings";

    private final UUID tinId = UUID.randomUUID();
    private final UUID phienBanId = UUID.randomUUID();
    private final UUID anhId = UUID.randomUUID();

    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private KhoGia khoGia;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taiKhoanRepository = new InMemoryTaiKhoanRepository();
        phienXacThucRepository = new InMemoryPhienXacThucRepository();
        sessionLifecycleAdapter = new DefaultAuthBrowserSessionLifecycleAdapter(CSRF_SECRET, phienXacThucRepository);
        cookieConfig = taoCookieConfig();
        CurrentPermissionAndCsrfService permissionAndCsrfService = new CurrentPermissionAndCsrfService(
                taiKhoanRepository,
                phienXacThucRepository,
                sessionLifecycleAdapter,
                List.of(ORIGIN)
        );
        khoGia = new KhoGia();
        KiemDuyetService kiemDuyetService = new KiemDuyetService(
                permissionAndCsrfService,
                khoGia,
                Clock.fixed(GOC, ZoneOffset.UTC)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new KiemDuyetController(kiemDuyetService, cookieConfig))
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
    @DisplayName("Người dùng thường nhận 403 KHONG_DU_QUYEN ở cả ba operation")
    void nguoiDungThuongBi403() throws Exception {
        Fixture nguoiDung = taoTaiKhoan(VaiTroTaiKhoan.NGUOI_DUNG);

        mockMvc.perform(yeuCauGet(DS, nguoiDung.rawSessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauGet(duongDanTin(), nguoiDung.rawSessionCookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauQuyetDinh(nguoiDung.rawSessionCookie(), "k-1", "\"7\"", pheDuyet()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        assertThat(khoGia.soLanQuyetDinh).isZero();
    }

    @Test
    @DisplayName("Chưa đăng nhập nhận 401 CHUA_DANG_NHAP ở cả ba operation")
    void khachBi401() throws Exception {
        mockMvc.perform(yeuCauGet(DS, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
        mockMvc.perform(yeuCauGet(duongDanTin(), null))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(yeuCauQuyetDinh(null, "k-1", "\"7\"", pheDuyet()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));
        assertThat(khoGia.soLanQuyetDinh).isZero();
    }

    @Test
    @DisplayName("Đường dẫn cũ (/queue, /versions/{id}, /approve, /reject) không còn tồn tại")
    void duongDanCuKhongConTonTai() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        mockMvc.perform(yeuCauGet("/api/moderation/queue", kdv.rawSessionCookie()))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(400, 404));
        mockMvc.perform(yeuCauGet(duongDanTin() + "/versions/" + phienBanId, kdv.rawSessionCookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(yeuCauPost(duongDanTin() + "/versions/" + phienBanId + "/approve", kdv.rawSessionCookie(), "k", "\"7\"", null))
                .andExpect(status().isNotFound());
        assertThat(khoGia.soLanQuyetDinh).isZero();
    }

    @Test
    @DisplayName("Bản cần duyệt: 200 TinQuanLy với ETag mạnh, no-store, ảnh theo AnhHienThi (không lộ object key); 404/409")
    void banCanDuyetCoEtag() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        String json = mockMvc.perform(yeuCauGet(duongDanTin(), kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"7\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.id").value(tinId.toString()))
                .andExpect(jsonPath("$.trangThai").value("cho_kiem_duyet"))
                .andExpect(jsonPath("$.phienBanCapNhat").value(7))
                .andExpect(jsonPath("$.phienBanHienTaiId").value(phienBanId.toString()))
                .andExpect(jsonPath("$.ketQuaSoBo").value("dat"))
                .andExpect(jsonPath("$.noiDung.id").value(phienBanId.toString()))
                .andExpect(jsonPath("$.noiDung.giaGoc").value("2500000000"))
                .andExpect(jsonPath("$.noiDung.dienTichM2").value("68.5"))
                .andExpect(jsonPath("$.noiDung.anh[0].id").value(anhId.toString()))
                .andExpect(jsonPath("$.noiDung.anh[0].thuTu").value(1))
                .andExpect(jsonPath("$.noiDung.anh[0].duongDanDoc").value("/api/images/" + anhId + "/content"))
                .andExpect(jsonPath("$.noiDung.anh[0].khoaAnhHienThi").doesNotExist())
                .andExpect(jsonPath("$.noiDung.giaQuyDoiHienThi.giaTri").value("2500000000.00"))
                .andExpect(jsonPath("$.noiDung.giaQuyDoiHienThi.donVi").value("vnd_tong"))
                .andReturn().getResponse().getContentAsString();
        // Trường nullable bắt buộc vẫn phải có mặt (giá trị null).
        assertThat(json).contains("\"phienBanDaDuyetId\":null", "\"lyDoTuChoi\":null", "\"lyDoNgungHienThi\":null");
        assertThat(json).doesNotContain("chuId", "khoaAnh");

        mockMvc.perform(yeuCauGet(DS + "/" + UUID.randomUUID(), kdv.rawSessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));

        khoGia.trangThaiTin = "bi_tu_choi";
        mockMvc.perform(yeuCauGet(duongDanTin(), kdv.rawSessionCookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("TRANG_THAI_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("Danh sách chờ: TrangTinQuanLy {noiDung, trang, kichThuoc, tongSo}; trang/kichThuoc sai nhận 400, không clamp")
    void danhSachChoVaPhanTrang() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        mockMvc.perform(yeuCauGet(DS + "?trang=0&kichThuoc=2", kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.noiDung.length()").value(2))
                .andExpect(jsonPath("$.noiDung[0].noiDung.tieuDe").value("Tin 0"))
                .andExpect(jsonPath("$.trang").value(0))
                .andExpect(jsonPath("$.kichThuoc").value(2))
                .andExpect(jsonPath("$.tongSo").value(3));
        assertThat(khoGia.boQuaCuoi).isZero();

        mockMvc.perform(yeuCauGet(DS + "?trang=1&kichThuoc=2", kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noiDung.length()").value(1))
                .andExpect(jsonPath("$.noiDung[0].noiDung.tieuDe").value("Tin 2"))
                .andExpect(jsonPath("$.trang").value(1));
        assertThat(khoGia.boQuaCuoi).isEqualTo(2);

        mockMvc.perform(yeuCauGet(DS, kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noiDung.length()").value(3))
                .andExpect(jsonPath("$.trang").value(0))
                .andExpect(jsonPath("$.kichThuoc").value(20));

        mockMvc.perform(yeuCauGet(DS + "?trang=5", kdv.rawSessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noiDung.length()").value(0))
                .andExpect(jsonPath("$.tongSo").value(3));

        for (String kichThuoc : List.of("0", "51", "abc", "-3", "")) {
            mockMvc.perform(yeuCauGet(DS + "?kichThuoc=" + kichThuoc, kdv.rawSessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("kichThuoc"));
        }
        for (String trang : List.of("-1", "100001", "x")) {
            mockMvc.perform(yeuCauGet(DS + "?trang=" + trang, kdv.rawSessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.truongLoi[0].truong").value("trang"));
        }
    }

    @Test
    @DisplayName("Phê duyệt: 200 TinGhiKetQua với ETag mới; thiếu If-Match 428, lệch 412, thiếu Idempotency-Key 400")
    void pheDuyetVaDieuKienTienQuyet() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        String json = mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "duyet-1", "\"7\"", pheDuyet()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tinId").value(tinId.toString()))
                .andExpect(jsonPath("$.phienBanHienTaiId").value(phienBanId.toString()))
                .andExpect(jsonPath("$.trangThai").value("da_duyet"))
                .andExpect(jsonPath("$.phienBanCapNhat").value(8))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("nguoiDuyetId", "ketQuaKiemDuyet", "lyDoTuChoi");
        assertThat(khoGia.duLieuCuoi.pheDuyet()).isTrue();
        assertThat(khoGia.duLieuCuoi.phienBanDaXemId()).isEqualTo(phienBanId);
        assertThat(khoGia.duLieuCuoi.phienBanCapNhatDaXem()).isEqualTo(7L);
        assertThat(khoGia.duLieuCuoi.maYDinh()).isEqualTo("duyet-1");
        assertThat(khoGia.duLieuCuoi.nguoiDuyetId()).isEqualTo(kdv.taiKhoan().getId());
        assertThat(khoGia.duLieuCuoi.lyDo()).isNull();

        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "duyet-2", null, pheDuyet()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.maLoi").value("THIEU_IF_MATCH"));
        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "duyet-3", "\"3\"", pheDuyet()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.maLoi").value("PHIEN_BAN_CU"));
        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "duyet-4", "W/\"7\"", pheDuyet()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                .andExpect(jsonPath("$.truongLoi[0].truong").value("If-Match"));
        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), null, "\"7\"", pheDuyet()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("Idempotency-Key"));
        assertThat(khoGia.soLanQuyetDinh).isEqualTo(1);
    }

    @Test
    @DisplayName("QuyetDinhRequest: trường lạ/tự khai bị chặn 400; thiếu phienBanDaXemId/quyetDinh 400; lyDo bắt buộc khi tu_choi")
    void quyetDinhKiemTraThan() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        Map<String, Object> lyDoNull = new HashMap<>(tuChoi("x"));
        lyDoNull.put("lyDo", null);

        Map<Map<String, Object>, String> cacBodyLoi = new LinkedHashMap<>();
        cacBodyLoi.put(them(pheDuyet(), "trangThai", "da_duyet"), "trangThai");
        cacBodyLoi.put(them(pheDuyet(), "nguoiDuyetId", UUID.randomUUID().toString()), "nguoiDuyetId");
        cacBodyLoi.put(them(pheDuyet(), "phienBanCapNhat", 7), "phienBanCapNhat");
        cacBodyLoi.put(Map.of("quyetDinh", "phe_duyet"), "phienBanDaXemId");
        cacBodyLoi.put(Map.of("phienBanDaXemId", "khong-phai-uuid", "quyetDinh", "phe_duyet"), "phienBanDaXemId");
        cacBodyLoi.put(Map.of("phienBanDaXemId", phienBanId.toString()), "quyetDinh");
        cacBodyLoi.put(Map.of("phienBanDaXemId", phienBanId.toString(), "quyetDinh", "duyet"), "quyetDinh");
        cacBodyLoi.put(Map.of("phienBanDaXemId", phienBanId.toString(), "quyetDinh", "tu_choi"), "lyDo");
        cacBodyLoi.put(lyDoNull, "lyDo");
        cacBodyLoi.put(tuChoi(""), "lyDo");
        cacBodyLoi.put(tuChoi("    "), "lyDo");
        cacBodyLoi.put(tuChoi("a".repeat(1001)), "lyDo");
        cacBodyLoi.put(them(new HashMap<>(Map.of("phienBanDaXemId", phienBanId.toString(), "quyetDinh", "tu_choi")), "lyDo", 42), "lyDo");
        int stt = 0;
        for (Map.Entry<Map<String, Object>, String> muc : cacBodyLoi.entrySet()) {
            mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "loi-" + stt++, "\"7\"", muc.getKey()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"))
                    .andExpect(jsonPath("$.truongLoi.length()").value(1))
                    .andExpect(jsonPath("$.truongLoi[0].truong").value(muc.getValue()));
        }
        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "khong-body", "\"7\"", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));
        assertThat(khoGia.soLanQuyetDinh).isZero();

        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "tc-ok", "\"7\"", tuChoi("  Ảnh sai sự thật  ")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""))
                .andExpect(jsonPath("$.trangThai").value("bi_tu_choi"));
        assertThat(khoGia.duLieuCuoi.pheDuyet()).isFalse();
        assertThat(khoGia.duLieuCuoi.lyDo()).isEqualTo("Ảnh sai sự thật");

        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "tc-1000", "\"7\"", tuChoi("c".repeat(1000))))
                .andExpect(status().isOk());
        mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "tc-428", null, tuChoi("x")))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    @DisplayName("Lỗi nghiệp vụ: PHIEN_BAN_CU 412; TRANG_THAI_KHONG_HOP_LE và BR-03 409; mã ý định khác nội dung 409; không có tin 404")
    void anhXaLoiNghiepVu() throws Exception {
        Fixture kdv = taoTaiKhoan(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        record Mong(KiemDuyetRepository.LoaiLoiKiemDuyet loai, int http, String maLoi) {
        }
        List<Mong> cacMong = List.of(
                new Mong(KiemDuyetRepository.LoaiLoiKiemDuyet.PHIEN_BAN_CU, 412, "PHIEN_BAN_CU"),
                new Mong(KiemDuyetRepository.LoaiLoiKiemDuyet.TRANG_THAI_KHONG_HOP_LE, 409, "TRANG_THAI_KHONG_HOP_LE"),
                new Mong(KiemDuyetRepository.LoaiLoiKiemDuyet.KHONG_DAT_BR03, 409, "TRANG_THAI_KHONG_HOP_LE"),
                new Mong(KiemDuyetRepository.LoaiLoiKiemDuyet.XUNG_DOT_MA_Y_DINH, 409, "MA_Y_DINH_KHAC_NOI_DUNG"),
                new Mong(KiemDuyetRepository.LoaiLoiKiemDuyet.TIN_KHONG_TON_TAI, 404, "KHONG_TIM_THAY")
        );
        int stt = 0;
        for (Mong mong : cacMong) {
            khoGia.loiPhaiNem = mong.loai();
            mockMvc.perform(yeuCauQuyetDinh(kdv.rawSessionCookie(), "loi-" + stt++, "\"7\"", pheDuyet()))
                    .andExpect(status().is(mong.http()))
                    .andExpect(jsonPath("$.maLoi").value(mong.maLoi()));
        }
    }

    // ---------------------------------------------------------------- tiện ích

    private String duongDanTin() {
        return DS + "/" + tinId;
    }

    private Map<String, Object> pheDuyet() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phienBanDaXemId", phienBanId.toString());
        body.put("quyetDinh", "phe_duyet");
        return body;
    }

    private Map<String, Object> tuChoi(String lyDo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phienBanDaXemId", phienBanId.toString());
        body.put("quyetDinh", "tu_choi");
        body.put("lyDo", lyDo);
        return body;
    }

    private static Map<String, Object> them(Map<String, Object> goc, String khoa, Object giaTri) {
        Map<String, Object> ketQua = new LinkedHashMap<>(goc);
        ketQua.put(khoa, giaTri);
        return ketQua;
    }

    private MockHttpServletRequestBuilder yeuCauQuyetDinh(
            String rawSessionCookie,
            String idempotencyKey,
            String ifMatch,
            Map<String, Object> body
    ) {
        return yeuCauPost(duongDanTin() + "/decision", rawSessionCookie, idempotencyKey, ifMatch, body);
    }

    private MockHttpServletRequestBuilder yeuCauGet(String duongDan, String rawSessionCookie) {
        MockHttpServletRequestBuilder builder = get(duongDan).accept(MediaType.APPLICATION_JSON);
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    private MockHttpServletRequestBuilder yeuCauPost(
            String duongDan,
            String rawSessionCookie,
            String idempotencyKey,
            String ifMatch,
            Map<String, Object> body
    ) {
        MockHttpServletRequestBuilder builder = post(duongDan).accept(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(ChuanHoaJson.toJsonString(body));
        }
        builder.header(HttpHeaders.ORIGIN, ORIGIN);
        if (idempotencyKey != null) {
            builder.header(TinDangService.HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        }
        if (ifMatch != null) {
            builder.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        String csrf = rawSessionCookie != null
                ? sessionLifecycleAdapter.issueSessionCsrfToken(rawSessionCookie)
                : sessionLifecycleAdapter.issueAnonymousCsrfToken();
        builder.header(AuthSessionCookieConfig.CSRF_HEADER_NAME, csrf);
        builder.cookie(new Cookie(AuthSessionCookieConfig.CSRF_COOKIE_NAME, csrf));
        if (rawSessionCookie != null) {
            builder.cookie(new Cookie(cookieConfig.getSessionCookieName(), rawSessionCookie));
        }
        return builder;
    }

    private Fixture taoTaiKhoan(VaiTroTaiKhoan vaiTro) {
        Instant now = Instant.now();
        TaiKhoan taiKhoan = TaiKhoan.khoiPhucTuLuuTru(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "kiem-duyet-" + UUID.randomUUID() + "@bds-test.local",
                "Tài khoản thử",
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

    /** Kho giả: ba tin chờ cố định; một tin có phienBanCapNhat = 7; quyết định mô phỏng điều kiện If-Match. */
    private final class KhoGia implements KiemDuyetRepository {

        int soLanQuyetDinh;
        int boQuaCuoi = -1;
        String trangThaiTin = "cho_kiem_duyet";
        DuLieuQuyetDinh duLieuCuoi;
        LoaiLoiKiemDuyet loiPhaiNem;

        private TinQuanLy tin(UUID id, UUID banId, String tieuDe, String trangThai) {
            PhienBanNoiDung noiDung = new PhienBanNoiDung(
                    banId, 1, tieuDe, "Mô tả", "ban", "can_ho", "2500000000", "vnd_tong", "68.5",
                    "12 Tôn Đức Thắng", "TP. Hồ Chí Minh", "10.7769", "106.7009",
                    List.of(AnhHienThi.tu(anhId, 1)), GOC,
                    GiaQuyDoiHienThi.tinhCheDoTong("ban", new BigDecimal("2500000000"), "vnd_tong", new BigDecimal("68.5"))
            );
            return new TinQuanLy(id, trangThai, GOC, CAP_NHAT_HIEN_TAI, banId, null, noiDung, "dat", null, null);
        }

        @Override
        public List<TinQuanLy> docHangCho(int boQua, int gioiHan) {
            boQuaCuoi = boQua;
            List<TinQuanLy> tatCa = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tatCa.add(tin(UUID.randomUUID(), UUID.randomUUID(), "Tin " + i, "cho_kiem_duyet"));
            }
            return tatCa.subList(Math.min(boQua, 3), Math.min(boQua + gioiHan, 3));
        }

        @Override
        public long demHangCho() {
            return 3;
        }

        @Override
        public Optional<TinQuanLy> docTinCanDuyet(UUID id) {
            if (!tinId.equals(id)) {
                return Optional.empty();
            }
            return Optional.of(tin(tinId, phienBanId, "Tiêu đề", trangThaiTin));
        }

        @Override
        public Optional<KetQuaYeuCauQuyetDinh> timYeuCauQuyetDinh(UUID taiKhoanId, String maYDinh) {
            return Optional.empty();
        }

        @Override
        public TinGhiKetQua quyetDinhTrongTransaction(DuLieuQuyetDinh duLieu) {
            if (loiPhaiNem != null) {
                throw new LoiNghiepVuKiemDuyetException(loiPhaiNem, "Lỗi giả lập " + loiPhaiNem);
            }
            if (duLieu.phienBanCapNhatDaXem() != CAP_NHAT_HIEN_TAI) {
                throw new LoiNghiepVuKiemDuyetException(LoaiLoiKiemDuyet.PHIEN_BAN_CU, "Tin đã thay đổi.");
            }
            soLanQuyetDinh++;
            duLieuCuoi = duLieu;
            return new TinGhiKetQua(
                    duLieu.tinId(),
                    duLieu.phienBanDaXemId(),
                    duLieu.pheDuyet() ? "da_duyet" : "bi_tu_choi",
                    CAP_NHAT_HIEN_TAI + 1
            );
        }
    }
}
