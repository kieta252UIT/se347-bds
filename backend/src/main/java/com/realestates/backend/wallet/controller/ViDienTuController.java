package com.realestates.backend.wallet.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.wallet.application.ViDienTuApplicationService;
import com.realestates.backend.wallet.dto.KhoiTaoSoDuThuRequest;
import com.realestates.backend.wallet.dto.ViDienTuResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ViDienTuController {

    private final ViDienTuApplicationService viDienTuApplicationService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public ViDienTuController(
            ViDienTuApplicationService viDienTuApplicationService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.viDienTuApplicationService = Objects.requireNonNull(viDienTuApplicationService, "viDienTuApplicationService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(
            value = {"/me/wallet", "/wallet/me"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ViDienTuResponse> layViCuaToi(HttpServletRequest request) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        ViDienTuResponse response = viDienTuApplicationService.layViCuaToi(rawSessionCookie);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(response);
    }

    @PostMapping(
            value = {"/me/wallet/seed-test-balance", "/wallet/seed-test-balance"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ViDienTuResponse> khoiTaoSoDuThu(
            HttpServletRequest request,
            @RequestBody(required = false) KhoiTaoSoDuThuRequest body
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        BigDecimal soTien = body != null ? body.soTien() : null;
        String ghiChu = body != null ? body.ghiChu() : null;
        ViDienTuResponse response = viDienTuApplicationService.khoiTaoSoDuThu(rawSessionCookie, soTien, ghiChu);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(response);
    }
}
