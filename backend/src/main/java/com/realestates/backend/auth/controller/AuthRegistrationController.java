package com.realestates.backend.auth.controller;

import com.realestates.backend.auth.application.AuthRegistrationService;
import com.realestates.backend.auth.dto.GuiEmailKetQua;
import com.realestates.backend.auth.exception.PayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
@RequestMapping({"/api/auth", "/auth"})
public class AuthRegistrationController {

    public static final long MAX_AUTH_PAYLOAD_BYTES = 16 * 1024L; // 16 KiB theo AUTH-12

    private final AuthRegistrationService authRegistrationService;

    public AuthRegistrationController(AuthRegistrationService authRegistrationService) {
        this.authRegistrationService = authRegistrationService;
    }

    @PostMapping(
            value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<GuiEmailKetQua> dangKyNguoiDung(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> rawPayload
    ) {
        if (request.getContentLengthLong() > MAX_AUTH_PAYLOAD_BYTES) {
            throw new PayloadTooLargeException();
        }

        GuiEmailKetQua result = authRegistrationService.dangKyNguoiDung(rawPayload);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(result);
    }
}
