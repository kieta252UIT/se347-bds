package com.realestates.backend.listing.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.dto.TinCuaToiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/me/listings", "/me/listings"})
public class DanhSachTinCuaToiController {

    private final QuanLyTinService quanLyTinService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public DanhSachTinCuaToiController(
            QuanLyTinService quanLyTinService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.quanLyTinService = Objects.requireNonNull(quanLyTinService, "quanLyTinService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TinCuaToiResponse>> layDanhSachTinCuaToi(HttpServletRequest request) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        List<TinCuaToiResponse> ketQua = quanLyTinService.layDanhSachTinCuaToi(rawSessionCookie);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(ketQua);
    }
}
