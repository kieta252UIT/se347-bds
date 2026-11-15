package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
import com.realestates.backend.auth.dto.CsrfKetQua;
import com.realestates.backend.auth.dto.TaiKhoanHienTai;
import com.realestates.backend.config.AuthSessionCookieConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cung cấp các endpoint:
 * - GET /api/csrf (operationId: layCsrf): Phát hành hoặc tái sử dụng mã bảo vệ CSRF theo ngữ cảnh phiên hiện tại.
 * - GET /api/me (operationId: layTaiKhoanHienTai): Đọc hồ sơ, vai trò và trạng thái tài khoản hiện tại,
 *   gia hạn trượt cookie phiên với cùng giá trị opaque khi phiên hợp lệ.
 *
 * Theo đặc tả openapi-sprint-1.yaml, api-specification-sprint-1.md §8.6, §8.7 và thiet-ke-phien-sprint-1.md §2.2, §3.1.
 */
@RestController
@RequestMapping
public class AuthCsrfAndMeController {

    private final CurrentPermissionAndCsrfService permissionAndCsrfService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public AuthCsrfAndMeController(
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

    /**
     * GET /api/csrf (operationId: layCsrf).
     * Cấp mới hoặc tái sử dụng token CSRF gắn với ngữ cảnh hiện tại, đặt cookie __Host-se214_csrf,
     * trả 200 OK kèm CsrfKetQua, không sửa __Host-se214_session.
     */
    @GetMapping(
            value = {"/api/csrf", "/csrf"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<CsrfKetQua> layCsrf(HttpServletRequest request) {
        String rawSessionCookie = docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
        String rawCsrfCookie = docGiaTriCookie(request, authSessionCookieConfig.getCsrfCookieName());

        CsrfKetQua ketQua = permissionAndCsrfService.phatHanhHoacTaiSuDungCsrfToken(
                rawSessionCookie,
                rawCsrfCookie
        );
        ResponseCookie csrfCookie = authSessionCookieConfig.createCsrfCookie(ketQua.csrfToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .body(ketQua);
    }

    /**
     * GET /api/me (operationId: layTaiKhoanHienTai).
     * Kiểm tra phiên và trạng thái tài khoản từ kho lưu trữ chính, trả 200 OK kèm TaiKhoanHienTai
     * và gia hạn lại cookie __Host-se214_session với CÙNG giá trị opaque hiện tại (Max-Age=31536000).
     * Khi phiên không hợp lệ / bị khóa / chưa xác minh email / DB gián đoạn, ném ngoại lệ tương ứng
     * để trả 401/403/503 mà KHÔNG gửi Set-Cookie.
     */
    @GetMapping(
            value = {"/api/me", "/me"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TaiKhoanHienTai> layTaiKhoanHienTai(HttpServletRequest request) {
        String rawSessionCookie = docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
        TaiKhoanHienTai taiKhoanHienTai = permissionAndCsrfService.layTaiKhoanHienTai(rawSessionCookie);

        // Chỉ GET /api/me thành công (200 OK) mới phát lại Set-Cookie cho __Host-se214_session với CÙNG giá trị opaque
        ResponseCookie renewedSessionCookie = authSessionCookieConfig.createSessionCookie(rawSessionCookie.trim());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.SET_COOKIE, renewedSessionCookie.toString())
                .body(taiKhoanHienTai);
    }

    public static String docGiaTriCookie(HttpServletRequest request, String cookieName) {
        if (request == null || cookieName == null || cookieName.isBlank()) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie != null && cookieName.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
        }
        String rawCookieHeader = request.getHeader(HttpHeaders.COOKIE);
        if (rawCookieHeader == null || rawCookieHeader.isBlank()) {
            return null;
        }
        String[] pairs = rawCookieHeader.split(";");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String name = pair.substring(0, eqIdx).trim();
                String value = pair.substring(eqIdx + 1).trim();
                if (cookieName.equals(name) && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
