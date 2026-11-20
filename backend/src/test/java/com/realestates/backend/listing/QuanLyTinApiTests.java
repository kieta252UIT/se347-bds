package com.realestates.backend.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.infrastructure.DefaultAuthBrowserSessionLifecycleAdapter;
import com.realestates.backend.auth.infrastructure.InMemoryPhienXacThucRepository;
import com.realestates.backend.auth.infrastructure.InMemoryTaiKhoanRepository;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.exception.GlobalApiExceptionHandler;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.controller.QuanLyTinController;
import com.realestates.backend.listing.controller.TinDangController;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.ChuanHoaJson;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.listing.infrastructure.InMemoryAnhTinRepository;
import com.realestates.backend.listing.infrastructure.InMemoryTinDangRepository;
import com.realestates.backend.listing.infrastructure.MacDinhBanDoAdapter;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

class QuanLyTinApiTests {

    private static final String ORIGIN = "https://localhost";
    private static final String CSRF_SECRET = "TEST_CSRF_HMAC_SECRET_KEY_32_BYTES_MINIMUM";
    private static final LocalDate THANG_MUOI_MOT = LocalDate.of(2026, 11, 1);

    private InMemoryTaiKhoanRepository taiKhoanRepository;
    private InMemoryPhienXacThucRepository phienXacThucRepository;
    private DefaultAuthBrowserSessionLifecycleAdapter sessionLifecycleAdapter;
    private AuthSessionCookieConfig cookieConfig;
    private InMemoryAnhTinRepository anhTinRepository;
    private InMemoryTinDangRepository tinDangRepository;
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
        anhTinRepository = new InMemoryAnhTinRepository();
        tinDangRepository = new InMemoryTinDangRepository(anhTinRepository);
        Clock dongHo = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC);
        MacDinhBanDoAdapter banDo = new MacDinhBanDoAdapter("sandbox");
        TinDangService tinDangService = new TinDangService(permissionAndCsrfService, tinDangRepository, banDo, dongHo);
        QuanLyTinService quanLyTinService = new QuanLyTinService(permissionAndCsrfService, tinDangRepository, banDo, dongHo);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TinDangController(tinDangService, cookieConfig),
                        new QuanLyTinController(quanLyTinService, cookieConfig)
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
    @DisplayName("Sửa tin đã duyệt: 200, tin về chờ kiểm duyệt, có phiên bản 2, giữ bản đã duyệt, không tăng hạn mức")
    void suaTinDaDuyetThanhCong() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");
        UUID anhMoi = luuAnhTam(chu);
        Map<String, Object> payload = payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId(), anhMoi));

        String body = mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", payload))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.tinId").value(tin.tinId().toString()))
                .andExpect(jsonPath("$.trangThai").value("cho_kiem_duyet"))
                .andExpect(jsonPath("$.phienBanCapNhat").value(1))
                .andReturn().getResponse().getContentAsString();

        UUID phienBanMoiId = UUID.fromString(JsonPath.read(body, "$.phienBanHienTaiId"));
        assertThat(phienBanMoiId).isNotEqualTo(tin.phienBanId());
        var tinLuu = tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow();
        assertThat(tinLuu.trangThai()).isEqualTo("cho_kiem_duyet");
        assertThat(tinLuu.phienBanHienTaiId()).isEqualTo(phienBanMoiId);
        assertThat(tinLuu.phienBanDaDuyetId()).isEqualTo(tin.phienBanId());
        assertThat(tinDangRepository.timPhienBanTheoId(phienBanMoiId).orElseThrow().soPhienBan()).isEqualTo(2);
        assertThat(tinDangRepository.timPhienBanTheoId(tin.phienBanId())).isPresent();
        assertThat(anhTinRepository.timAnhDeDoc(tin.anhId()).orElseThrow().phienBanTinId()).isEqualTo(tin.phienBanId());
        assertThat(anhTinRepository.timAnhDeDoc(anhMoi).orElseThrow().phienBanTinId()).isEqualTo(phienBanMoiId);
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), THANG_MUOI_MOT)).isEqualTo(1);
    }

    @Test
    @DisplayName("Sửa tin bị từ chối cũng được phép")
    void suaTinBiTuChoiThanhCong() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTin(chu, "tao-1");
        tinDangRepository.datTrangThaiTinChoTest(tin.tinId(), "bi_tu_choi", null);

        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"",
                        payloadSua(tin, "Tiêu đề đã chỉnh theo lý do từ chối", List.of(tin.anhId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("cho_kiem_duyet"));
    }

    @Test
    @DisplayName("Gửi lại cùng Idempotency-Key và cùng nội dung trả kết quả cũ; khác nội dung nhận 409")
    void suaTinIdempotency() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");
        Map<String, Object> payload = payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId()));

        String lanDau = mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String lanHai = mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", payload))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(lanHai, "$.phienBanHienTaiId"))
                .isEqualTo((String) JsonPath.read(lanDau, "$.phienBanHienTaiId"));
        assertThat(tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow().phienBanCapNhat()).isEqualTo(1);

        Map<String, Object> payloadKhac = payloadSua(tin, "Một tiêu đề khác hoàn toàn", List.of(tin.anhId()));
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", payloadKhac))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("MA_Y_DINH_KHAC_NOI_DUNG"));
    }

    @Test
    @DisplayName("Sửa tin thiếu hoặc sai điều kiện tiên quyết: thiếu If-Match 428, If-Match cũ hoặc phiên bản đã xem sai 412")
    void suaTinDieuKienTienQuyet() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");
        Map<String, Object> payload = payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId()));

        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", null, payload))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.maLoi").value("THIEU_IF_MATCH"));
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-2", "\"5\"", payload))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.maLoi").value("PHIEN_BAN_CU"));
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-3", "W/\"0\"", payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));

        Map<String, Object> phienBanSai = new LinkedHashMap<>(payload);
        phienBanSai.put("phienBanDaXemId", UUID.randomUUID().toString());
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-4", "\"0\"", phienBanSai))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.maLoi").value("PHIEN_BAN_CU"));

        assertThat(tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow().phienBanCapNhat()).isZero();
    }

    @Test
    @DisplayName("Sửa tin thiếu Idempotency-Key hoặc thiếu phienBanDaXemId nhận 400")
    void suaTinThieuDauVao() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");
        Map<String, Object> payload = payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId()));

        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), null, "\"0\"", payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.maLoi").value("DU_LIEU_KHONG_HOP_LE"));

        Map<String, Object> thieuPhienBan = new LinkedHashMap<>(payload);
        thieuPhienBan.remove("phienBanDaXemId");
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", thieuPhienBan))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("phienBanDaXemId"));
    }

    @Test
    @DisplayName("Tin đang chờ duyệt hoặc đã xóa không được sửa: 409 TRANG_THAI_KHONG_HOP_LE")
    void suaTinSaiTrangThai() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao choDuyet = taoTin(chu, "tao-1");
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), choDuyet.tinId(), "sua-1", "\"0\"",
                        payloadSua(choDuyet, "Tiêu đề sau khi sửa", List.of(choDuyet.anhId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("TRANG_THAI_KHONG_HOP_LE"));

        TinDaTao daXoa = taoTinDaDuyet(chu, "tao-2");
        tinDangRepository.datTrangThaiTinChoTest(daXoa.tinId(), "da_xoa", daXoa.phienBanId());
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), daXoa.tinId(), "sua-2", "\"0\"",
                        payloadSua(daXoa, "Tiêu đề sau khi sửa", List.of(daXoa.anhId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("TRANG_THAI_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("Sửa tin: chủ khác và kiểm duyệt viên nhận 403, tin không tồn tại nhận 404, chưa đăng nhập nhận 401")
    void suaTinPhanQuyen() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        Fixture nguoiKhac = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        Fixture kiemDuyetVien = taoNguoiDung(VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");
        Map<String, Object> payload = payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId()));

        mockMvc.perform(yeuCauSua(nguoiKhac.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauSua(kiemDuyetVien.rawSessionCookie(), tin.tinId(), "sua-2", "\"0\"", payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), UUID.randomUUID(), "sua-3", "\"0\"", payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
        mockMvc.perform(yeuCauSua(null, tin.tinId(), "sua-4", "\"0\"", payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.maLoi").value("CHUA_DANG_NHAP"));

        assertThat(tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow().phienBanCapNhat()).isZero();
    }

    @Test
    @DisplayName("Sửa tin với nội dung sai hoặc ảnh không được dùng lại nhận 400, không tạo phiên bản mới")
    void suaTinDuLieuSai() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        Fixture nguoiKhac = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");
        TinDaTao tinKhacCuaChu = taoTin(chu, "tao-2");
        UUID anhNguoiKhac = luuAnhTam(nguoiKhac);

        Map<String, Object> giaSai = payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tin.anhId()));
        giaSai.put("giaGoc", "-1");
        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-1", "\"0\"", giaSai))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("giaGoc"));

        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-2", "\"0\"",
                        payloadSua(tin, "Tiêu đề sau khi sửa", List.of(anhNguoiKhac))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("anhIds"));

        mockMvc.perform(yeuCauSua(chu.rawSessionCookie(), tin.tinId(), "sua-3", "\"0\"",
                        payloadSua(tin, "Tiêu đề sau khi sửa", List.of(tinKhacCuaChu.anhId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.truongLoi[0].truong").value("anhIds"));

        assertThat(tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow().phienBanCapNhat()).isZero();
        assertThat(anhTinRepository.timAnhDeDoc(anhNguoiKhac).orElseThrow().trangThaiAnh())
                .isEqualTo(AnhTinTruyCap.ANH_TAM);
    }

    @Test
    @DisplayName("Xóa tin ở các trạng thái chờ duyệt, đã duyệt, bị từ chối: 200, tin thành đã xóa, ETag tăng")
    void xoaTinThanhCong() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao choDuyet = taoTin(chu, "tao-1");
        TinDaTao daDuyet = taoTinDaDuyet(chu, "tao-2");
        TinDaTao biTuChoi = taoTin(chu, "tao-3");
        tinDangRepository.datTrangThaiTinChoTest(biTuChoi.tinId(), "bi_tu_choi", null);

        for (TinDaTao tin : List.of(choDuyet, daDuyet, biTuChoi)) {
            mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-" + tin.tinId(), "\"0\""))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                    .andExpect(jsonPath("$.trangThai").value("da_xoa"))
                    .andExpect(jsonPath("$.phienBanCapNhat").value(1));
            assertThat(tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow().trangThai()).isEqualTo("da_xoa");
        }
        assertThat(tinDangRepository.docSoTinMoiTrongThang(chu.taiKhoan().getId(), THANG_MUOI_MOT)).isEqualTo(3);
    }

    @Test
    @DisplayName("Xóa tin: gửi lại cùng Idempotency-Key trả kết quả cũ kể cả khi tin đã xóa; key mới thì 409")
    void xoaTinIdempotencyVaDaXoa() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");

        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-1", "\"0\"")).andExpect(status().isOk());
        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-1", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.trangThai").value("da_xoa"));
        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-1", "\"1\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("MA_Y_DINH_KHAC_NOI_DUNG"));
        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-2", "\"1\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.maLoi").value("TRANG_THAI_KHONG_HOP_LE"));
    }

    @Test
    @DisplayName("Xóa tin: thiếu If-Match 428, If-Match cũ 412, thiếu Idempotency-Key 400, chủ khác 403, không tồn tại 404")
    void xoaTinDieuKienVaPhanQuyen() throws Exception {
        Fixture chu = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        Fixture nguoiKhac = taoNguoiDung(VaiTroTaiKhoan.NGUOI_DUNG);
        TinDaTao tin = taoTinDaDuyet(chu, "tao-1");

        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-1", null))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.maLoi").value("THIEU_IF_MATCH"));
        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), "xoa-2", "\"7\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.maLoi").value("PHIEN_BAN_CU"));
        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), tin.tinId(), null, "\"0\""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(yeuCauXoa(nguoiKhac.rawSessionCookie(), tin.tinId(), "xoa-3", "\"0\""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.maLoi").value("KHONG_DU_QUYEN"));
        mockMvc.perform(yeuCauXoa(chu.rawSessionCookie(), UUID.randomUUID(), "xoa-4", "\"0\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));

        assertThat(tinDangRepository.timTinTheoId(tin.tinId()).orElseThrow().trangThai()).isEqualTo("da_duyet");
    }

    private MockHttpServletRequestBuilder yeuCauXoa(String rawSessionCookie, UUID tinId, String idempotencyKey, String ifMatch) {
        return hoanThienYeuCau(delete("/api/listings/" + tinId), rawSessionCookie, idempotencyKey, ifMatch);
    }

    private record TinDaTao(UUID tinId, UUID phienBanId, UUID anhId) {
    }

    private TinDaTao taoTin(Fixture chu, String idempotencyKey) throws Exception {
        UUID anhId = luuAnhTam(chu);
        Map<String, Object> payload = duLieuTin("Căn hộ 2 phòng ngủ view sông", List.of(anhId.toString()));
        String body = mockMvc.perform(yeuCauDangTin(chu.rawSessionCookie(), idempotencyKey, payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new TinDaTao(
                UUID.fromString(JsonPath.read(body, "$.tinId")),
                UUID.fromString(JsonPath.read(body, "$.phienBanHienTaiId")),
                anhId
        );
    }

    private TinDaTao taoTinDaDuyet(Fixture chu, String idempotencyKey) throws Exception {
        TinDaTao tin = taoTin(chu, idempotencyKey);
        tinDangRepository.datTrangThaiTinChoTest(tin.tinId(), "da_duyet", tin.phienBanId());
        return tin;
    }

    private UUID luuAnhTam(Fixture chu) {
        UUID anhId = UUID.randomUUID();
        anhTinRepository.luuAnhTam(
                anhId,
                chu.taiKhoan().getId(),
                anhId + ".png",
                anhId + "-display.png",
                DinhDangAnh.PNG,
                1024L
        );
        return anhId;
    }

    private static Map<String, Object> duLieuTin(String tieuDe, List<String> anhIds) {
        Map<String, Object> duLieu = new LinkedHashMap<>();
        duLieu.put("tieuDe", tieuDe);
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

    private static Map<String, Object> payloadSua(TinDaTao tin, String tieuDe, List<UUID> anhIds) {
        Map<String, Object> payload = duLieuTin(tieuDe, anhIds.stream().map(UUID::toString).toList());
        payload.put("phienBanDaXemId", tin.phienBanId().toString());
        return payload;
    }

    private MockHttpServletRequestBuilder yeuCauDangTin(String rawSessionCookie, String idempotencyKey, Map<String, Object> payload) {
        MockHttpServletRequestBuilder builder = post("/api/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ChuanHoaJson.toJsonString(payload));
        return hoanThienYeuCau(builder, rawSessionCookie, idempotencyKey, null);
    }

    private MockHttpServletRequestBuilder yeuCauSua(
            String rawSessionCookie,
            UUID tinId,
            String idempotencyKey,
            String ifMatch,
            Map<String, Object> payload
    ) {
        MockHttpServletRequestBuilder builder = put("/api/listings/" + tinId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ChuanHoaJson.toJsonString(payload));
        return hoanThienYeuCau(builder, rawSessionCookie, idempotencyKey, ifMatch);
    }

    private MockHttpServletRequestBuilder hoanThienYeuCau(
            MockHttpServletRequestBuilder builder,
            String rawSessionCookie,
            String idempotencyKey,
            String ifMatch
    ) {
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
}

