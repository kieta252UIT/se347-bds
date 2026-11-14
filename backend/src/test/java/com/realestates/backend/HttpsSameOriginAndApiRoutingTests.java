package com.realestates.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.database.TestDatabaseSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.ForwardedHeaderFilter;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class HttpsSameOriginAndApiRoutingTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthSessionCookieConfig authSessionCookieConfig;

    @Autowired
    private ForwardedHeaderFilter forwardedHeaderFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .addFilters(this.forwardedHeaderFilter)
                .build();
    }

    @Test
    @DisplayName("ADR-0013 / PHIEN-02: Cookie __Host-se214_session va __Host-se214_csrf co du co HttpOnly, Secure, SameSite=Lax, Path=/, khong co Domain")
    void sessionAndCsrfCookiesShouldMatchHostPrefixContract() {
        ResponseCookie sessionCookie = authSessionCookieConfig.createSessionCookie("raw_session_token");
        String sessionHeader = sessionCookie.toString();

        assertThat(sessionCookie.getName()).isEqualTo("__Host-se214_session");
        assertThat(sessionHeader)
                .contains("__Host-se214_session=raw_session_token")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=31536000")
                .doesNotContainIgnoringCase("Domain=");

        ResponseCookie csrfCookie = authSessionCookieConfig.createCsrfCookie("nonce.sig");
        String csrfHeader = csrfCookie.toString();

        assertThat(csrfCookie.getName()).isEqualTo("__Host-se214_csrf");
        assertThat(csrfHeader)
                .contains("__Host-se214_csrf=nonce.sig")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContainIgnoringCase("Domain=");
    }

    @Test
    @DisplayName("ADR-0013: Dinh tuyen /api/hello qua X-Forwarded-Proto https hoat dong dung")
    void apiHelloShouldRouteUnderApiPrefixWithForwardedHttps() throws Exception {
        mockMvc.perform(get("/api/hello")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "localhost")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello from Spring Boot!"));
    }

    @Test
    @DisplayName("TS1-004: GET /api/khong-ton-tai tra JSON LoiApi 404 KHONG_TIM_THAY, khong tra HTML fallback")
    void unknownApiEndpointShouldReturnJsonLoiApi404() throws Exception {
        mockMvc.perform(get("/api/khong-ton-tai").accept(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.maLoi").value("KHONG_TIM_THAY"))
                .andExpect(jsonPath("$.thongDiep").isNotEmpty())
                .andExpect(jsonPath("$.truongLoi").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("ADR-0013: CORS tren /api/** cho phep cung nguon https://localhost kem credentials")
    void corsOnApiShouldAllowHttpsLocalhostWithCredentials() throws Exception {
        mockMvc.perform(options("/api/hello")
                        .header(HttpHeaders.ORIGIN, "https://localhost")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://localhost"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }
}
