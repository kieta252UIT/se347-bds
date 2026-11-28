package com.realestates.backend.recommendation.controller;

import com.realestates.backend.auth.controller.AuthCsrfAndMeController;
import com.realestates.backend.config.AuthSessionCookieConfig;
import com.realestates.backend.recommendation.application.GoiYApplicationService;
import com.realestates.backend.recommendation.application.TuongTacApplicationService;
import com.realestates.backend.recommendation.dto.GhiNhanTuongTacRequest;
import com.realestates.backend.recommendation.dto.GoiYQueryRequest;
import com.realestates.backend.recommendation.dto.GoiYResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class GoiYController {

    private final GoiYApplicationService goiYApplicationService;
    private final TuongTacApplicationService tuongTacApplicationService;
    private final AuthSessionCookieConfig authSessionCookieConfig;

    @Autowired
    public GoiYController(
            GoiYApplicationService goiYApplicationService,
            TuongTacApplicationService tuongTacApplicationService,
            AuthSessionCookieConfig authSessionCookieConfig
    ) {
        this.goiYApplicationService = Objects.requireNonNull(goiYApplicationService, "goiYApplicationService không được null");
        this.tuongTacApplicationService = Objects.requireNonNull(tuongTacApplicationService, "tuongTacApplicationService không được null");
        this.authSessionCookieConfig = Objects.requireNonNull(authSessionCookieConfig, "authSessionCookieConfig không được null");
    }

    /**
     * FR-03 / UC-03: Nhận danh sách gợi ý bất động sản (Tự nhiên & Được tài trợ)
     * Hỗ trợ đầy đủ 4 ngữ cảnh (AC-03.1, BR-01, BR-06).
     */
    @GetMapping
    public ResponseEntity<GoiYResponse> layGoiY(
            HttpServletRequest httpRequest,
            @RequestParam(required = false) BigDecimal viDo,
            @RequestParam(required = false) BigDecimal kinhDo,
            @RequestParam(required = false) String loaiBds,
            @RequestParam(required = false) String loaiGiaoDich,
            @RequestParam(required = false) BigDecimal giaMin,
            @RequestParam(required = false) BigDecimal giaMax,
            @RequestParam(required = false) String khuVuc,
            @RequestParam(required = false) Integer limit
    ) {
        String sessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                httpRequest,
                authSessionCookieConfig.getSessionCookieName()
        );

        GoiYQueryRequest request = new GoiYQueryRequest(
                viDo,
                kinhDo,
                loaiBds,
                loaiGiaoDich,
                giaMin,
                giaMax,
                khuVuc,
                limit
        );

        GoiYResponse response = goiYApplicationService.layGoiY(sessionCookie, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(response);
    }

    /**
     * Ghi nhận tương tác của người dùng (xem tin chi tiết) phục vụ cập nhật điểm nền trong 5 phút.
     */
    @PostMapping("/interactions")
    public ResponseEntity<Map<String, Object>> ghiNhanTuongTac(
            HttpServletRequest httpRequest,
            @RequestBody GhiNhanTuongTacRequest request
    ) {
        String sessionCookie = AuthCsrfAndMeController.docGiaTriCookie(
                httpRequest,
                authSessionCookieConfig.getSessionCookieName()
        );

        boolean daGhiNhan = tuongTacApplicationService.ghiNhanTuongTac(sessionCookie, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, HttpHeaders.COOKIE)
                .body(Map.of(
                        "thanhCong", daGhiNhan,
                        "thongDiep", daGhiNhan ? "Đã ghi nhận tương tác" : "Khách xem tin công khai"
                ));
    }
}
