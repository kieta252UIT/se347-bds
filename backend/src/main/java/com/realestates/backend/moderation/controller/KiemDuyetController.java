package com.realestates.backend.moderation.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.application.TinDangService;
import com.realestates.backend.listing.dto.TinGhiKetQua;
import com.realestates.backend.moderation.application.KiemDuyetService;
import com.realestates.backend.moderation.dto.TinQuanLy;
import com.realestates.backend.moderation.dto.TrangTinQuanLy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ba operation kiểm duyệt của openapi-sprint-1.yaml: layDanhSachChoDuyet, layBanCanDuyet, quyetDinhKiemDuyet. */
@RestController
@RequestMapping("/api/moderation/listings")
public class KiemDuyetController {

    private final KiemDuyetService kiemDuyetService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public KiemDuyetController(KiemDuyetService kiemDuyetService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.kiemDuyetService = Objects.requireNonNull(kiemDuyetService, "kiemDuyetService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TrangTinQuanLy> layDanhSachChoDuyet(
            HttpServletRequest request,
            @RequestParam(value = KiemDuyetService.THAM_SO_TRANG, required = false) String trang,
            @RequestParam(value = KiemDuyetService.THAM_SO_KICH_THUOC, required = false) String kichThuoc
    ) {
        TrangTinQuanLy ketQua = kiemDuyetService.layDanhSachChoDuyet(docCookiePhien(request), trang, kichThuoc);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(ketQua);
    }

    @GetMapping(value = "/{listingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TinQuanLy> layBanCanDuyet(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId
    ) {
        TinQuanLy ketQua = kiemDuyetService.layBanCanDuyet(docCookiePhien(request), tinId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.ETAG, "\"" + ketQua.phienBanCapNhat() + "\"")
                .body(ketQua);
    }

    @PostMapping(value = "/{listingId}/decision", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TinGhiKetQua> quyetDinhKiemDuyet(
            HttpServletRequest request,
            @PathVariable("listingId") UUID tinId,
            @RequestHeader(value = TinDangService.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(value = KiemDuyetService.HEADER_IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        TinGhiKetQua ketQua = kiemDuyetService.quyetDinh(docCookiePhien(request), tinId, idempotencyKey, ifMatch, body);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.ETAG, "\"" + ketQua.phienBanCapNhat() + "\"")
                .body(ketQua);
    }

    private String docCookiePhien(HttpServletRequest request) {
        return AuthCsrfAndMeController.docGiaTriCookie(request, authSessionCookieConfig.getSessionCookieName());
    }
}
