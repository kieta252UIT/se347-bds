package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.AuthPasswordResetService;
import com.realestates.backend.auth.dto.DatLaiKetQua;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.exception.PayloadTooLargeException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping({"/api/auth/password", "/auth/password"})
public class AuthPasswordResetController {

    public static final long MAX_AUTH_PAYLOAD_BYTES = AuthRegistrationController.MAX_AUTH_PAYLOAD_BYTES;

    private final AuthPasswordResetService authPasswordResetService;

    public AuthPasswordResetController(AuthPasswordResetService authPasswordResetService) {
        this.authPasswordResetService = Objects.requireNonNull(authPasswordResetService, "authPasswordResetService không được null");
    }

    @PostMapping(
            value = "/reset-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<GuiEmailKetQua> yeuCauDatLaiMatKhau(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        if (request != null && request.getContentLengthLong() > MAX_AUTH_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException();
        }

        String clientIp = resolveClientIp(request);
        GuiEmailKetQua result = authPasswordResetService.yeuCauDatLaiMatKhau(rawPayload, clientIp);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(result);
    }

    @PostMapping(
            value = "/reset",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DatLaiKetQua> hoanTatDatLaiMatKhau(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        if (request != null && request.getContentLengthLong() > MAX_AUTH_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException();
        }

        String clientIp = resolveClientIp(request);
        DatLaiKetQua result = authPasswordResetService.hoanTatDatLaiMatKhau(rawPayload, clientIp);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(result);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "127.0.0.1";
        }
        return request.getRemoteAddr().trim();
    }
}
