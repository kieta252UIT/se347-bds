package com.realestates.backend.detail.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.detail.application.ChiTietTinService;
import com.realestates.backend.detail.dto.TinCongKhai;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/listings/{listingId} (xemTinCongKhai): chi tiết kèm liên hệ, khách được phép. listingId nhận dạng chuỗi để
 * id sai định dạng cũng trả 404 như id không tồn tại. QuanLyTinController chỉ map PUT/DELETE nên không xung đột.
 */
@RestController
@RequestMapping("/api/listings/{listingId}")
public class ChiTietTinController {

    private final ChiTietTinService chiTietTinService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public ChiTietTinController(ChiTietTinService chiTietTinService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.chiTietTinService = Objects.requireNonNull(chiTietTinService, "chiTietTinService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TinCongKhai> xem(HttpServletRequest request, @PathVariable("listingId") String listingId) {
        String cookie = AuthCsrfAndMeController.docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(chiTietTinService.xem(cookie, listingId));
    }
}
