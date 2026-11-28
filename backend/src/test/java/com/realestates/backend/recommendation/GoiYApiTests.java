package com.realestates.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.exception.ListingExceptionHandler;
import com.realestates.backend.recommendation.application.GoiYApplicationService;
import com.realestates.backend.recommendation.application.ThuatToanTinhDiemGoiY;
import com.realestates.backend.recommendation.application.TuongTacApplicationService;
import com.realestates.backend.recommendation.controller.GoiYController;
import com.realestates.backend.recommendation.domain.DiemGoiY;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import com.realestates.backend.recommendation.infrastructure.InMemoryDiemGoiYRepository;
import com.realestates.backend.recommendation.infrastructure.InMemoryKiemTraTinChoGoiYAdapter;
import com.realestates.backend.recommendation.infrastructure.InMemoryLichSuXemRepository;
import com.realestates.backend.recommendation.infrastructure.KiemTraTinChoGoiYPort.ThongTinTinGoiY;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GoiYApiTests {

    private MockMvc mockMvc;
    private CurrentPermissionAndCsrfService permissionService;
    private InMemoryKiemTraTinChoGoiYAdapter kiemTraTinAdapter;
    private InMemoryDiemGoiYRepository diemGoiYRepository;
    private InMemoryLichSuXemRepository lichSuXemRepository;

    private final Instant now = Instant.parse("2026-11-24T10:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final String sessionCookieName = "sb_session";

    private UUID tinThuongId;
    private UUID tinTaiTroId;

    @BeforeEach
    void setUp() {
        permissionService = Mockito.mock(CurrentPermissionAndCsrfService.class);
        kiemTraTinAdapter = new InMemoryKiemTraTinChoGoiYAdapter();
        kiemTraTinAdapter.clear();
        diemGoiYRepository = new InMemoryDiemGoiYRepository();
        lichSuXemRepository = new InMemoryLichSuXemRepository();

        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        Random random = new Random(100);

        GoiYApplicationService goiYApplicationService = new GoiYApplicationService(
                permissionService,
                kiemTraTinAdapter,
                diemGoiYRepository,
                new ThuatToanTinhDiemGoiY(),
                fixedClock,
                random
        );

        TuongTacApplicationService tuongTacApplicationService = new TuongTacApplicationService(
                permissionService,
                lichSuXemRepository,
                kiemTraTinAdapter,
                fixedClock
        );

        AuthSessionCookieConfig cookieConfig = Mockito.mock(AuthSessionCookieConfig.class);
        Mockito.when(cookieConfig.getSessionCookieName()).thenReturn(sessionCookieName);

        GoiYController controller = new GoiYController(
                goiYApplicationService,
                tuongTacApplicationService,
                cookieConfig
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ListingExceptionHandler())
                .build();

        // Chuẩn bị dữ liệu mẫu
        tinThuongId = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinThuongId, UUID.randomUUID(), "Căn hộ Masteri Thảo Điền", "Mô tả", "ban", "can_ho",
                new BigDecimal("3500000000"), "vnd_tong", new BigDecimal("3500000000"),
                BigDecimal.ZERO, new BigDecimal("70"), "Thảo Điền, Quận 2", "Thu Duc",
                new ViTriToaDo(new BigDecimal("10.8030"), new BigDecimal("106.7380")),
                now, null, "da_duyet", "hoat_dong", false
        ));

        tinTaiTroId = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinTaiTroId, UUID.randomUUID(), "Biệt thự Saigon Mystery VIP", "Mô tả", "ban", "biet_thu",
                new BigDecimal("25000000000"), "vnd_tong", new BigDecimal("25000000000"),
                BigDecimal.ZERO, new BigDecimal("200"), "Bát Nàn, Thạnh Mỹ Lợi", "Thu Duc",
                new ViTriToaDo(new BigDecimal("10.7720"), new BigDecimal("106.7550")),
                now, null, "da_duyet", "hoat_dong", true
        ));
    }

    @Test
    @DisplayName("API GET /api/recommendations - Khách không truyền GPS -> trả C1_KHACH_KHONG_GPS và dùng vị trí mặc định TP.HCM")
    void testGetRecommendations_KhachKhongGps() throws Exception {
        Mockito.when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.VARY, HttpHeaders.COOKIE))
                .andExpect(jsonPath("$.nguCanh").value(GoiYApplicationService.NGU_CANH_C1_KHACH_KHONG_GPS))
                .andExpect(jsonPath("$.dungViTriMacDinh").value(true))
                .andExpect(jsonPath("$.tongSoTuNhien").value(1))
                .andExpect(jsonPath("$.tongSoTaiTro").value(1))
                .andExpect(jsonPath("$.goiYTuNhien[0].tinId").value(tinThuongId.toString()))
                .andExpect(jsonPath("$.duocTaiTro[0].tinId").value(tinTaiTroId.toString()));
    }

    @Test
    @DisplayName("API GET /api/recommendations - Người dùng có cookie và truyền GPS -> trả C4_NGUOI_DUNG_CO_GPS")
    void testGetRecommendations_NguoiDungCoGps() throws Exception {
        PrincipalPhienHienTai principal = new PrincipalPhienHienTai(
                UUID.randomUUID(), userId, "hash", "user@test.com", "Test User", "0900000000",
                VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true, 0L
        );
        Mockito.when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai("token-123"))
                .thenReturn(Optional.of(principal));

        // Lưu điểm nền cho tin thường
        diemGoiYRepository.save(new DiemGoiY(userId, tinThuongId, new BigDecimal("98.5"), now));

        mockMvc.perform(get("/api/recommendations")
                        .cookie(new Cookie(sessionCookieName, "token-123"))
                        .param("viDo", "10.8000")
                        .param("kinhDo", "106.7300")
                        .param("loaiBds", "can_ho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nguCanh").value(GoiYApplicationService.NGU_CANH_C4_NGUOI_DUNG_CO_GPS))
                .andExpect(jsonPath("$.dungViTriMacDinh").value(false))
                .andExpect(jsonPath("$.goiYTuNhien[0].tinId").value(tinThuongId.toString()))
                .andExpect(jsonPath("$.goiYTuNhien[0].diemGoiY").value(98.5));
    }

    @Test
    @DisplayName("API POST /api/recommendations/interactions - Khách xem tin công khai -> trả 200 thanhCong: false")
    void testPostInteraction_KhachXemTin() throws Exception {
        Mockito.when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null))
                .thenReturn(Optional.empty());

        String jsonBody = """
                {
                    "tinId": "%s",
                    "loaiTuongTac": "xem"
                }
                """.formatted(tinThuongId);

        mockMvc.perform(post("/api/recommendations/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.thanhCong").value(false))
                .andExpect(jsonPath("$.thongDiep").value("Khách xem tin công khai"));

        assertThat(lichSuXemRepository.findByTaiKhoanId(userId)).isEmpty();
    }

    @Test
    @DisplayName("API POST /api/recommendations/interactions - Người dùng xem tin công khai -> trả 200 thanhCong: true và lưu DB")
    void testPostInteraction_NguoiDungXemTin() throws Exception {
        PrincipalPhienHienTai principal = new PrincipalPhienHienTai(
                UUID.randomUUID(), userId, "hash", "user@test.com", "Test User", "0900000000",
                VaiTroTaiKhoan.NGUOI_DUNG, TrangThaiTaiKhoan.HOAT_DONG, true, 0L
        );
        Mockito.when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai("token-123"))
                .thenReturn(Optional.of(principal));

        String jsonBody = """
                {
                    "tinId": "%s",
                    "loaiTuongTac": "xem"
                }
                """.formatted(tinThuongId);

        mockMvc.perform(post("/api/recommendations/interactions")
                        .cookie(new Cookie(sessionCookieName, "token-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thanhCong").value(true))
                .andExpect(jsonPath("$.thongDiep").value("Đã ghi nhận tương tác"));

        assertThat(lichSuXemRepository.findByTaiKhoanId(userId)).hasSize(1);
    }

    @Test
    @DisplayName("API POST /api/recommendations/interactions - Tin không công khai BR-01 -> trả 404 KHONG_TIM_THAY")
    void testPostInteraction_TinKhongCongKhai_Tra404() throws Exception {
        UUID tinKhongTonTai = UUID.randomUUID();

        String jsonBody = """
                {
                    "tinId": "%s",
                    "loaiTuongTac": "xem"
                }
                """.formatted(tinKhongTonTai);

        mockMvc.perform(post("/api/recommendations/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"));
    }

    @Test
    @DisplayName("API GET /api/recommendations - Lọc chặt chẽ BR-01 và BR-06 ngay lúc đọc")
    void testApiGetRecommendations_LocBR01VaBR06KhiDoc() throws Exception {
        Mockito.when(permissionService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(null))
                .thenReturn(Optional.empty());

        // Tin C: Bị gỡ do vi phạm
        UUID tinViPham = UUID.randomUUID();
        kiemTraTinAdapter.mockThemTin(new ThongTinTinGoiY(
                tinViPham, UUID.randomUUID(), "Tin vi phạm", "Mô tả", "ban", "can_ho",
                new BigDecimal("2000000000"), "vnd_tong", new BigDecimal("2000000000"),
                BigDecimal.ZERO, new BigDecimal("50"), "Địa chỉ", "Quan 1",
                ViTriToaDo.TOA_DO_MAC_DINH_TPHCM, now, null, "bi_go_vi_pham", "hoat_dong", false
        ));

        // Gọi API GET gợi ý
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tongSoTuNhien").value(1))
                .andExpect(jsonPath("$.goiYTuNhien[0].tinId").value(tinThuongId.toString()))
                .andExpect(jsonPath("$.tongSoTaiTro").value(1))
                .andExpect(jsonPath("$.duocTaiTro[0].tinId").value(tinTaiTroId.toString()))
                // Tin vi phạm tuyệt đối không có mặt trong cả 2 danh sách
                .andExpect(jsonPath("$.goiYTuNhien[?(@.tinId == '%s')]".formatted(tinViPham)).doesNotExist())
                .andExpect(jsonPath("$.duocTaiTro[?(@.tinId == '%s')]".formatted(tinViPham)).doesNotExist());
    }
}
