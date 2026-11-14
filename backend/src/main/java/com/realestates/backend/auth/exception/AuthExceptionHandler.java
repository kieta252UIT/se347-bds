package com.realestates.backend.auth.exception;

import com.realestates.backend.auth.application.AuthRegistrationService;
import com.realestates.backend.auth.controller.AuthEmailVerificationController;
import com.realestates.backend.auth.controller.AuthRegistrationController;
import com.realestates.backend.auth.dto.LoiApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bộ xử lý ngoại lệ tập trung cho luồng đăng ký và xác minh email Auth, chuẩn hóa phản hồi theo schema LoiApi
 * (api-specification-sprint-1.md §3.4 & §8.9).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        AuthRegistrationController.class,
        AuthEmailVerificationController.class
})
public class AuthExceptionHandler {

    @ExceptionHandler(AuthValidationException.class)
    public ResponseEntity<LoiApiResponse> handleValidationException(
            AuthValidationException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMaLoi(),
                ex.getMessage(),
                ex.getTruongLoi(),
                request
        );
    }

    @ExceptionHandler(InvalidVerificationLinkException.class)
    public ResponseEntity<LoiApiResponse> handleInvalidVerificationLink(
            InvalidVerificationLinkException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<LoiApiResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(AuthProfileNeedsInspectionException.class)
    public ResponseEntity<LoiApiResponse> handleProfileNeedsInspection(
            AuthProfileNeedsInspectionException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(AuthRateLimitExceededException.class)
    public ResponseEntity<LoiApiResponse> handleRateLimitExceeded(
            AuthRateLimitExceededException ex,
            HttpServletRequest request
    ) {
        ResponseEntity<LoiApiResponse> baseResponse = buildErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
        if (ex.getRetryAfterSeconds() == null) {
            return baseResponse;
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(baseResponse.getBody());
    }

    @ExceptionHandler(VerificationSyncIncompleteException.class)
    public ResponseEntity<LoiApiResponse> handleVerificationSyncIncomplete(
            VerificationSyncIncompleteException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(AuthProviderUnavailableException.class)
    public ResponseEntity<LoiApiResponse> handleProviderUnavailable(
            AuthProviderUnavailableException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<LoiApiResponse> handlePayloadTooLarge(
            PayloadTooLargeException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.CONTENT_TOO_LARGE,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<LoiApiResponse> handleMalformedJson(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                AuthRegistrationService.MA_LOI_DU_LIEU_KHONG_HOP_LE,
                "Định dạng JSON gửi lên không hợp lệ.",
                List.of(),
                request
        );
    }

    private ResponseEntity<LoiApiResponse> buildErrorResponse(
            HttpStatus status,
            String maLoi,
            String thongDiep,
            List<com.realestates.backend.auth.dto.FieldErrorItem> truongLoi,
            HttpServletRequest request
    ) {
        String traceId = resolveTraceId(request);
        LoiApiResponse body = new LoiApiResponse(
                maLoi,
                thongDiep,
                truongLoi != null ? truongLoi : List.of(),
                traceId
        );
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(body);
    }

    private static String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            String headerTrace = request.getHeader("X-Request-Id");
            if (headerTrace != null && !headerTrace.isBlank()) {
                return headerTrace.trim();
            }
        }
        return "req-" + UUID.randomUUID();
    }
}
