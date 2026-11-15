package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.exception.AuthSessionSecurityExceptionHandler;
import com.realestates.backend.auth.exception.InvalidCsrfException;
import com.realestates.backend.config.AuthSessionCookieConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bộ lọc HTTP kiểm tra bắt buộc Origin và Double-Submit CSRF Token trên mọi yêu cầu thay đổi trạng thái
 * (POST, PUT, DELETE, PATCH) trước khi đi vào bất kỳ Controller hay Use Case nào
 * (PHIEN-01, PHIEN-06, PX-05, PX-06, TS1-012, TS1-020).
 *
 * Đối với các yêu cầu đọc an toàn (GET, HEAD, OPTIONS), bộ lọc không chặn khi thiếu CSRF,
 * đồng thời hỗ trợ phân giải Principal tùy chọn cho các endpoint đọc công khai mà không làm hỏng
 * luồng Khách khi trình duyệt còn giữ cookie phiên cũ/hết hiệu lực (PX-02).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CsrfAndOriginProtectionFilter extends OncePerRequestFilter {

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public CsrfAndOriginProtectionFilter(
            CurrentPermissionAndCsrfService permissionAndCsrfService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.permissionAndCsrfService = Objects.requireNonNull(
                permissionAndCsrfService,
                "permissionAndCsrfService không được null"
        );
        this.authSessionCookieConfig = Objects.requireNonNull(
                authSessionCookieConfig,
                "authSessionCookieConfig không được null"
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );

        if (permissionAndCsrfService.laPhuongThucThayDoiTrangThai(method)) {
            String originHeader = request.getHeader(HttpHeaders.ORIGIN);
            String rawCsrfCookie = AuthCsrfAndMeController.docGiaTriCookie(
                    request,
                    authSessionCookieConfig.getCsrfCookieName()
            );
            String csrfHeader = request.getHeader(AuthSessionCookieConfig.CSRF_HEADER_NAME);

            try {
                permissionAndCsrfService.kiemTraOriginVaCsrf(
                        method,
                        originHeader,
                        rawSessionCookie,
                        rawCsrfCookie,
                        csrfHeader
                );
            } catch (InvalidCsrfException ex) {
                ghiPhanHoiLoiCsrf(request, response, ex);
                return;
            }
        } else if ("GET".equalsIgnoreCase(method) && !laEndpointMeHoacCsrf(request)) {
            // Với các yêu cầu GET công khai, nếu có cookie phiên hợp lệ thì đính kèm Principal vào request attribute;
            // nếu cookie phiên đã thu hồi/không hợp lệ thì tự động coi là Khách (không chặn GET công khai).
            try {
                permissionAndCsrfService.phanGiaiPrincipalTuyChonChoYeuCauCongKhai(rawSessionCookie)
                        .ifPresent(principal -> request.setAttribute(
                                CurrentPermissionAndCsrfService.REQUEST_ATTR_PRINCIPAL,
                                principal
                        ));
            } catch (RuntimeException ignored) {
                // Đối với endpoint GET công khai không bắt buộc đọc phiên, giữ nguyên ngữ cảnh Khách
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean laEndpointMeHoacCsrf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return "/api/me".equals(uri)
                || "/me".equals(uri)
                || "/api/csrf".equals(uri)
                || "/csrf".equals(uri);
    }

    private static void ghiPhanHoiLoiCsrf(
            HttpServletRequest request,
            HttpServletResponse response,
            InvalidCsrfException ex
    ) throws IOException {
        String traceId = AuthSessionSecurityExceptionHandler.resolveTraceId(request);
        String safeMessage = escapeJson(ex.getMessage());
        String jsonBody = "{\"maLoi\":\"" + ex.getMaLoi() + "\","
                + "\"thongDiep\":\"" + safeMessage + "\","
                + "\"truongLoi\":[],"
                + "\"traceId\":\"" + escapeJson(traceId) + "\"}";

        response.resetBuffer();
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
        response.getWriter().write(jsonBody);
        response.flushBuffer();
    }

    private static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
