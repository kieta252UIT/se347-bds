package com.realestates.backend.auth.exception;

import com.realestates.backend.auth.application.CurrentPermissionAndCsrfService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bộ xử lý ngoại lệ tập trung cho kiểm quyền hiện tại, phiên xác thực và bảo vệ CSRF
 * theo schema LoiApi (api-specification-sprint-1.md §3.4, §8.6, §8.7, §8.9 và thiet-ke-phien-sprint-1.md).
 * Đảm bảo mọi phản hồi lỗi đều có Cache-Control: no-store, Vary: Cookie và KHÔNG đính kèm Set-Cookie.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
public class AuthSessionSecurityExceptionHandler {

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
                ex.getMaLoi() != null ? ex.getMaLoi() : CurrentPermissionAndCsrfService.MA_LOI_DU_LIEU_KHONG_HOP_LE,
                ex.getMessage(),
                ex.getTruongLoi(),
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
                .contentType(MediaType.APPLICATION_JSON)
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
