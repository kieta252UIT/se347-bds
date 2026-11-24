package com.realestates.backend.wallet.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.wallet.application.QuanLyGoiDayTinApplicationService;
import com.realestates.backend.wallet.dto.KetQuaMuaGoiResponse;
import com.realestates.backend.wallet.dto.ThongTinMuaGoiResponse;
import com.realestates.backend.wallet.dto.YeuCauMuaGoiRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings/{listingId}/packages")
public class QuanLyGoiDayTinController {

    private final QuanLyGoiDayTinApplicationService quanLyGoiDayTinApplicationService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public QuanLyGoiDayTinController(
            QuanLyGoiDayTinApplicationService quanLyGoiDayTinApplicationService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.quanLyGoiDayTinApplicationService = Objects.requireNonNull(quanLyGoiDayTinApplicationService, "quanLyGoiDayTinApplicationService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(
            value = "/current",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ThongTinMuaGoiResponse> layThongTinGoiVaSoDu(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        ThongTinMuaGoiResponse response = quanLyGoiDayTinApplicationService.layThongTinGoiVaSoDu(
                rawSessionCookie,
                tinId
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(response);
    }

    @PostMapping(
            value = "/purchase",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<KetQuaMuaGoiResponse> muaGoi(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @RequestBody YeuCauMuaGoiRequest yeuCau
    ) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        KetQuaMuaGoiResponse response = quanLyGoiDayTinApplicationService.muaGoi(
                rawSessionCookie,
                tinId,
                yeuCau
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(response);
    }
}
