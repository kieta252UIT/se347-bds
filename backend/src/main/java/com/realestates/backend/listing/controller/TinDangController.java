package com.realestates.backend.listing.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
public class TinDangController {

    private final TinDangService tinDangService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public TinDangController(TinDangService tinDangService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.tinDangService = Objects.requireNonNull(tinDangService, "tinDangService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TinGhiKetQua> dangTin(
            HttpServletRequest request,
            @RequestHeader(value = TinDangService.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        TinGhiKetQua ketQua = tinDangService.dangTin(rawSessionCookie, idempotencyKey, rawPayload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.ETAG, "\"" + ketQua.phienBanCapNhat() + "\"")
                .header(HttpHeaders.LOCATION, "/api/me/listings/" + ketQua.tinId())
                .body(ketQua);
    }
}
