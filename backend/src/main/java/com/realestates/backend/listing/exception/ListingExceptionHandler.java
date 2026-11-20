package com.realestates.backend.listing.exception;

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

@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RestControllerAdvice
public class ListingExceptionHandler {

    @ExceptionHandler(ListingConflictException.class)
    public ResponseEntity<LoiApiResponse> handleListingConflict(
            ListingConflictException ex,
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

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<LoiApiResponse> handleListingNotFound(
            ListingNotFoundException ex,
            HttpServletRequest request
    ) {
        return AuthExceptionHandler.buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(ListingPreconditionException.class)
    public ResponseEntity<LoiApiResponse> handleListingPrecondition(
            ListingPreconditionException ex,
            HttpServletRequest request
    ) {
        return AuthExceptionHandler.buildErrorResponse(
                ex.laThieuIfMatch() ? HttpStatus.PRECONDITION_REQUIRED : HttpStatus.PRECONDITION_FAILED,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(MapServiceUnavailableException.class)
    public ResponseEntity<LoiApiResponse> handleMapUnavailable(
            MapServiceUnavailableException ex,
            HttpServletRequest request
    ) {
        return AuthExceptionHandler.buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMaLoi(),
                ex.getMessage(),
                List.of(),
                request
        );
    }
}
