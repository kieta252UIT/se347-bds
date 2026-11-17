package com.realestates.backend.exception;

import com.realestates.backend.dto.ApiErrorResponse;
import com.realestates.backend.dto.FieldErrorItem;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception ex) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ApiErrorResponse.of("KHONG_TIM_THAY", "Endpoint API khong ton tai.")
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorResponse.of("PHUONG_THUC_KHONG_HO_TRO", "Phuong thuc HTTP khong duoc ho tro cho endpoint nay.")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorItem> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new FieldErrorItem(
                        err.getField(),
                        err.getCode() != null ? err.getCode() : "TRUONG_KHONG_HOP_LE",
                        err.getDefaultMessage() != null ? err.getDefaultMessage() : "Gia tri khong hop le."
                ))
                .toList();
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorResponse.of("DU_LIEU_KHONG_HOP_LE", "Mot hoac nhieu truong khong hop le.", fieldErrors)
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorResponse.of("DU_LIEU_KHONG_HOP_LE", "Du lieu yeu cau khong hop le hoac sai dinh dang.")
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildErrorResponse(
                HttpStatus.CONTENT_TOO_LARGE,
                ApiErrorResponse.of("NOI_DUNG_QUA_LON", "Kich thuoc tep hoac du lieu tai len vuot gioi han cho phep.")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorResponse.of("LOI_HE_THONG", "Da xay ra loi he thong ngoai du kien.")
        );
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, ApiErrorResponse body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.VARY, "Cookie")
                .body(body);
    }
}
