package com.realestates.backend.auth.exception;

import com.realestates.backend.auth.application.AuthRegistrationService;
import com.realestates.backend.auth.dto.FieldErrorItem;
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
 * Bộ xử lý ngoại lệ tập trung cho toàn bộ luồng Auth, phiên xác thực, phân quyền và bảo vệ CSRF
 * chuẩn hóa phản hồi theo schema LoiApi (api-specification-sprint-1.md §3.4 & §8.9).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(UnauthenticatedSessionException.class)
    public ResponseEntity<LoiApiResponse> handleUnauthenticatedSession(
            UnauthenticatedSessionException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<LoiApiResponse> handleInsufficientPermission(
            InsufficientPermissionException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(InvalidCsrfException.class)
    public ResponseEntity<LoiApiResponse> handleInvalidCsrf(
            InvalidCsrfException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

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

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<LoiApiResponse> handlePasswordPolicyViolation(
            PasswordPolicyViolationException ex,
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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<LoiApiResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<LoiApiResponse> handleEmailNotVerified(
            EmailNotVerifiedException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<LoiApiResponse> handleAccountLocked(
            AccountLockedException ex,
            HttpServletRequest request
    ) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(AuthSyncInProgressException.class)
    public ResponseEntity<LoiApiResponse> handleAuthSyncInProgress(
            AuthSyncInProgressException ex,
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

    @ExceptionHandler(PasswordResetInProgressException.class)
    public ResponseEntity<LoiApiResponse> handlePasswordResetInProgress(
            PasswordResetInProgressException ex,
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

    @ExceptionHandler(PasswordResetSyncIncompleteException.class)
    public ResponseEntity<LoiApiResponse> handlePasswordResetSyncIncomplete(
            PasswordResetSyncIncompleteException ex,
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

    public static ResponseEntity<LoiApiResponse> buildErrorResponse(
            HttpStatus status,
            String maLoi,
            String thongDiep,
            List<FieldErrorItem> truongLoi,
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
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(body);
    }

    public static String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            String headerTrace = request.getHeader("X-Request-Id");
            if (headerTrace != null && !headerTrace.isBlank()) {
                return headerTrace.trim();
            }
        }
        return "req-" + UUID.randomUUID();
    }
}
