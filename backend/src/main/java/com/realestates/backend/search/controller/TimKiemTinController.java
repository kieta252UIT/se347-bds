package com.realestates.backend.search.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.search.application.TimKiemTinService;
import com.realestates.backend.search.dto.KetQuaTimKiem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/listings (timKiemTin): công khai, khách được phép. */
@RestController
@RequestMapping("/api/listings")
public class TimKiemTinController {

    private final TimKiemTinService timKiemTinService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public TimKiemTinController(TimKiemTinService timKiemTinService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.timKiemTinService = Objects.requireNonNull(timKiemTinService, "timKiemTinService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KetQuaTimKiem> timKiem(HttpServletRequest request) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        // Truyền toàn bộ query để service chặn param lạ/lặp (400), không âm thầm bỏ qua.
        KetQuaTimKiem ketQua = timKiemTinService.timKiem(rawSessionCookie, request.getParameterMap());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(ketQua);
    }
}
