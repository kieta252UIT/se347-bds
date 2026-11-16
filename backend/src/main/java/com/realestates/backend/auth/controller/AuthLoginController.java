package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.AuthLoginService;
import com.realestates.backend.auth.application.AuthLoginService.LoginExecutionResult;
import com.realestates.backend.auth.dto.DangNhapKetQua;
import com.realestates.backend.auth.exception.PayloadTooLargeException;
import com.realestates.backend.config.AuthSessionCookieConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthLoginController {

    public static final long MAX_AUTH_PAYLOAD_BYTES = AuthRegistrationController.MAX_AUTH_PAYLOAD_BYTES;

    private final AuthLoginService authLoginService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public AuthLoginController(
            AuthLoginService authLoginService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.authLoginService = authLoginService;
        this.authSessionCookieConfig = authSessionCookieConfig;
    }


    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DangNhapKetQua> dangNhap(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        if (request != null && request.getContentLengthLong() > MAX_AUTH_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException();
        }

        String clientIp = resolveClientIp(request);
        String currentSessionCookie = extractSessionCookieValue(request);

        LoginExecutionResult result = authLoginService.dangNhap(
                rawPayload,
                clientIp,
                currentSessionCookie
        );

        ResponseCookie sessionCookie = authSessionCookieConfig.createSessionCookie(result.rawSessionCookie());
        ResponseCookie csrfCookie = authSessionCookieConfig.createCsrfCookie(result.csrfToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .body(result.responseBody());
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
