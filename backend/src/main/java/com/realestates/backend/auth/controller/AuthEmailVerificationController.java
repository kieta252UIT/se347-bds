package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.AuthEmailVerificationService;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.dto.XacMinhKetQua;
import com.realestates.backend.auth.exception.PayloadTooLargeException;
import com.realestates.backend.config.AuthSessionCookieConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller xử lý xác minh email và gửi lại liên kết xác minh email
 * theo UC-26 / FR-15 / AC-15.2 / AC-15.3 / openapi-sprint-1.yaml:
 * - POST /api/auth/email/verification-requests (operationId: yeuCauGuiLaiXacMinh)
 * - POST /api/auth/email/verify (operationId: hoanTatXacMinhEmail)
 */
@RestController
@RequestMapping({"/api/auth/email", "/auth/email"})
public class AuthEmailVerificationController {

    public static final long MAX_AUTH_PAYLOAD_BYTES = AuthRegistrationController.MAX_AUTH_PAYLOAD_BYTES;

    private final AuthEmailVerificationService authEmailVerificationService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public AuthEmailVerificationController(
            AuthEmailVerificationService authEmailVerificationService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.authEmailVerificationService = authEmailVerificationService;
        this.authSessionCookieConfig = authSessionCookieConfig;
    }

    /**
     * Yêu cầu gửi lại email xác minh (operationId: yeuCauGuiLaiXacMinh).
     * Phản hồi 202 Accepted với thông báo chung không tiết lộ sự tồn tại hoặc trạng thái xác minh của email.
     */
    @PostMapping(
            value = "/verification-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<GuiEmailKetQua> yeuCauGuiLaiXacMinh(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        if (request != null && request.getContentLengthLong() > MAX_AUTH_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException();
        }

        String clientIp = resolveClientIp(request);
        GuiEmailKetQua result = authEmailVerificationService.yeuCauGuiLaiXacMinh(rawPayload, clientIp);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(result);
    }

    /**
     * Hoàn tất callback xác minh email (operationId: hoanTatXacMinhEmail).
     * Khi thành công (200 OK):
     * - Thu hồi đúng phiên hiện có trên browser (nếu có)
     * - Trả 2 dòng Set-Cookie độc lập: xóa cookie phiên (__Host-se214_session Max-Age=0)
     *   và đặt cookie CSRF mới (__Host-se214_csrf=<csrfToken>)
     * - Không tự cấp phiên đăng nhập mới (canDangNhapLai = true).
     */
    @PostMapping(
            value = "/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<XacMinhKetQua> hoanTatXacMinhEmail(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        if (request != null && request.getContentLengthLong() > MAX_AUTH_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException();
        }

        String clientIp = resolveClientIp(request);
        String currentSessionCookie = extractSessionCookieValue(request);

        XacMinhKetQua result = authEmailVerificationService.hoanTatXacMinhEmail(
                rawPayload,
                clientIp,
                currentSessionCookie
        );

        ResponseCookie clearSessionCookie = authSessionCookieConfig.clearSessionCookie();
        ResponseCookie newCsrfCookie = authSessionCookieConfig.createCsrfCookie(result.csrfToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.SET_COOKIE, clearSessionCookie.toString())
                .header(HttpHeaders.SET_COOKIE, newCsrfCookie.toString())
                .body(result);
    }

    private String extractSessionCookieValue(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        String targetCookieName = authSessionCookieConfig.getSessionCookieName();
        for (Cookie cookie : request.getCookies()) {
            if (cookie != null && targetCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "127.0.0.1";
        }
        return request.getRemoteAddr().trim();
    }
}
