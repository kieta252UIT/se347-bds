package com.realestates.backend.wallet.exception;

import com.realestates.backend.auth.dto.LoiApiResponse;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE + 6)
@RestControllerAdvice
public class WalletPackageExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<LoiApiResponse> handleInsufficientBalance(
            InsufficientBalanceException ex,
            HttpServletRequest request
    ) {
        return AuthExceptionHandler.buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(PackageAccumulationRequiredException.class)
    public ResponseEntity<LoiApiResponse> handlePackageAccumulationRequired(
            PackageAccumulationRequiredException ex,
            HttpServletRequest request
    ) {
        return AuthExceptionHandler.buildErrorResponse(
                HttpStatus.CONFLICT,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(ListingNotEligibleForPackageException.class)
    public ResponseEntity<LoiApiResponse> handleListingNotEligible(
            ListingNotEligibleForPackageException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = "KHONG_PHAI_CHU_TIN".equals(ex.getMaLoi())
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return AuthExceptionHandler.buildErrorResponse(
                status,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(InvalidPurchaseIntentException.class)
    public ResponseEntity<LoiApiResponse> handleInvalidPurchaseIntent(
            InvalidPurchaseIntentException ex,
            HttpServletRequest request
    ) {
        return AuthExceptionHandler.buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }
}
