package com.realestates.backend.listing.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.application.QuanLyTinService;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings/{listingId}")
public class QuanLyTinController {

    private final QuanLyTinService quanLyTinService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public QuanLyTinController(QuanLyTinService quanLyTinService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.quanLyTinService = Objects.requireNonNull(quanLyTinService, "quanLyTinService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @PutMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TinGhiKetQua> suaTin(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @RequestHeader(value = TinDangService.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = QuanLyTinService.HEADER_IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        TinGhiKetQua ketQua = quanLyTinService.suaTin(rawSessionCookie, tinId, idempotencyKey, ifMatch, rawPayload);
        return phanHoiThanhCong(ketQua);
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TinGhiKetQua> xoaTin(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @RequestHeader(value = TinDangService.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = QuanLyTinService.HEADER_IF_MATCH, required = false) String ifMatch
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        TinGhiKetQua ketQua = quanLyTinService.xoaTin(rawSessionCookie, tinId, idempotencyKey, ifMatch);
        return phanHoiThanhCong(ketQua);
    }

    private static ResponseEntity<TinGhiKetQua> phanHoiThanhCong(TinGhiKetQua ketQua) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.ETAG, "\"" + ketQua.phienBanCapNhat() + "\"")
                .body(ketQua);
    }
}

