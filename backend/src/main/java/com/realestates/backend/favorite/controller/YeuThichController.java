package com.realestates.backend.favorite.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.favorite.application.YeuThichService;
import com.realestates.backend.search.dto.TrangTinCongKhai;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/me/favorites, PUT/DELETE /api/me/favorites/{listingId} (204; CSRF do CsrfAndOriginProtectionFilter). */
@RestController
@RequestMapping("/api/me/favorites")
public class YeuThichController {

    private final YeuThichService yeuThichService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public YeuThichController(YeuThichService yeuThichService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.yeuThichService = Objects.requireNonNull(yeuThichService, "yeuThichService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TrangTinCongKhai> danhSach(
            HttpServletRequest request,
            @RequestParam(value = "trang", required = false) String trang,
            @RequestParam(value = "kichThuoc", required = false) String kichThuoc
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(yeuThichService.danhSach(docCookie(request), trang, kichThuoc));
    }

    @PutMapping("/{listingId}")
    public ResponseEntity<Void> luu(HttpServletRequest request, @PathVariable("listingId") String listingId) {
        yeuThichService.luu(docCookie(request), listingId);
        return khongNoiDung();
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> bo(HttpServletRequest request, @PathVariable("listingId") String listingId) {
        yeuThichService.bo(docCookie(request), listingId);
        return khongNoiDung();
    }

    private String docCookie(HttpServletRequest request) {
        return AuthCsrfAndMeController.docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
    }

    private static ResponseEntity<Void> khongNoiDung() {
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .build();
    }
}
