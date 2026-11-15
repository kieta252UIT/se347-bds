package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.AuthLogoutService;
import com.realestates.backend.auth.dto.DangXuatKetQua;
import com.realestates.backend.config.AuthSessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cung cấp endpoint đăng xuất:
 * - POST /api/auth/logout (operationId: dangXuatPhienHienTai)
 *
 * Theo đặc tả openapi-sprint-1.yaml, api-specification-sprint-1.md §8.8 và thiet-ke-phien-sprint-1.md §3.2.
 */
@RestController
@RequestMapping
public class AuthLogoutController {

    private final AuthLogoutService authLogoutService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public AuthLogoutController(
            AuthLogoutService authLogoutService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.authLogoutService = Objects.requireNonNull(authLogoutService, "authLogoutService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    /**
     * POST /api/auth/logout (operationId: dangXuatPhienHienTai).
     * Thu hồi đúng phiên app tương ứng, xóa cookie __Host-se214_session (Max-Age=0)
     * và trả csrfToken ngữ cảnh khách mới cùng cookie __Host-se214_csrf mới.
     */
    @PostMapping(
            value = {"/api/auth/logout", "/api/auth/dang-xuat", "/auth/logout", "/auth/dang-xuat"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DangXuatKetQua> dangXuatPhienHienTai(HttpServletRequest request) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );

        DangXuatKetQua ketQua = authLogoutService.dangXuat(rawSessionCookie);

        ResponseCookie clearedSessionCookie = authSessionCookieConfig.clearSessionCookie();
        ResponseCookie newCsrfCookie = authSessionCookieConfig.createCsrfCookie(ketQua.csrfToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie.toString())
                .header(HttpHeaders.SET_COOKIE, newCsrfCookie.toString())
                .body(ketQua);
    }
}
