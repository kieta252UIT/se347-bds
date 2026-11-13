package com.realestates.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Cấp độ 3: Kiểm thử giao tiếp API & Bảo mật (API Contract & Security Testing) theo IEEE 829 - Mục 7.3.
 * Kiểm tra:
 * - Tuân thủ định dạng phản hồi lỗi chuẩn RFC 7807 (maLoi, thongDiep, truongLoi, traceId).
 * - Kiểm tra các mã HTTP: 200, 404, 405.
 * - Header bảo mật chống rò rỉ bộ nhớ đệm (Cache-Control: no-store).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiContractAndSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ForwardedHeaderFilter forwardedHeaderFilter;

    @LocalServerPort
    private int port;

    private MockMvc getMockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(forwardedHeaderFilter)
                .build();
    }

    @Test
    @DisplayName("CONTRACT-01: GET /api/hello trả về 200 OK với body plain text")
    void shouldReturnHelloOk() throws Exception {
        getMockMvc().perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello from Spring Boot!"));
    }

    @Test
    @DisplayName("CONTRACT-02 / RFC-7807: Gọi endpoint không tồn tại trả về đúng schema ApiErrorResponse 404")
    void shouldReturnJsonLoiApi404WithStandardSchema() throws Exception {
        getMockMvc().perform(get("/api/chuc-nang-chua-lam"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"))
                .andExpect(jsonPath("$.thongDiep").value("Endpoint API khong ton tai."))
                .andExpect(jsonPath("$.truongLoi").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.thoiDiem").isNotEmpty());
    }

    @Test
    @DisplayName("CONTRACT-03: Gửi phương thức không hỗ trợ (DELETE /api/hello) trả về 405 PHUONG_THUC_KHONG_HO_TRO")
    void shouldReturnMethodNotAllowedWhenCallingUnsupportedMethod() throws Exception {
        getMockMvc().perform(delete("/api/hello"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.maLoi").value("PHUONG_THUC_KHONG_HO_TRO"))
                .andExpect(jsonPath("$.thongDiep").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("SECURITY-01: Gửi request giả mạo body rỗng vào POST /api/hello trả về 405 kèm header an toàn")
    void shouldProtectAgainstInvalidPayloadOnStaticGet() throws Exception {
        getMockMvc().perform(post("/api/hello")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }
}
