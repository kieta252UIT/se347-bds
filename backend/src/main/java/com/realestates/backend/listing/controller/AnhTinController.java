package com.realestates.backend.listing.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.auth.dto.FieldErrorItem;
import com.realestates.backend.auth.exception.AuthExceptionHandler;
import com.realestates.backend.auth.exception.AuthValidationException;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.listing.application.AnhDoc;
import com.realestates.backend.listing.application.AnhTinService;
import com.realestates.backend.listing.dto.AnhTamKetQua;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/images")
public class AnhTinController {

    private final AnhTinService anhTinService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    public AnhTinController(AnhTinService anhTinService, AuthSessionCookieConfig authSessionCookieConfig) {
        this.anhTinService = Objects.requireNonNull(anhTinService, "anhTinService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<AnhTamKetQua> taiAnhTam(MultipartHttpServletRequest request) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        anhTinService.xacThucNguoiTaiAnh(rawSessionCookie);
        MultipartFile tep = layDungMotTep(request);
        AnhTamKetQua ketQua = anhTinService.taiAnhTam(rawSessionCookie, docNoiDung(tep));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(ketQua);
    }

    @GetMapping("/{imageId}/content")
    public ResponseEntity<?> docAnh(@PathVariable("imageId") UUID imageId, HttpServletRequest request) {
        String rawSessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                request,
                authSessionCookieConfig.getSessionCookieName()
        );
        Optional<AnhDoc> anh = anhTinService.docAnh(imageId, rawSessionCookie);
        if (anh.isEmpty()) {
            return AuthExceptionHandler.buildErrorResponse(
                    HttpStatus.NOT_FOUND,
                    "KHONG_TIM_THAY",
                    "Không tìm thấy ảnh.",
                    List.of(),
                    request
            );
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anh.get().mimeType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .header("X-Content-Type-Options", "nosniff")
                .body(anh.get().noiDung());
    }

    private static MultipartFile layDungMotTep(MultipartHttpServletRequest request) {
        List<MultipartFile> cacTep = request.getMultiFileMap().get(AnhTinService.TRUONG_TEP);
        if (request.getMultiFileMap().size() != 1
                || cacTep == null
                || cacTep.size() != 1
                || !request.getParameterMap().isEmpty()) {
            throw loiTep("Yêu cầu chỉ được chứa đúng một tệp ảnh trong trường tep.");
        }
        return cacTep.get(0);
    }

    private static byte[] docNoiDung(MultipartFile tep) {
        try {
            return tep.getBytes();
        } catch (IOException ex) {
            throw loiTep("Không đọc được tệp tải lên.");
        }
    }

    private static AuthValidationException loiTep(String thongDiep) {
        return new AuthValidationException(
                AnhTinService.MA_LOI_DU_LIEU_KHONG_HOP_LE,
                "Dữ liệu gửi lên không hợp lệ.",
                List.of(new FieldErrorItem(
                        AnhTinService.TRUONG_TEP,
                        AnhTinService.MA_LOI_DU_LIEU_KHONG_HOP_LE,
                        thongDiep
                ))
        );
    }
}

