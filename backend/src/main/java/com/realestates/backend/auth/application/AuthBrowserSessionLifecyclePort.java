package com.realestates.backend.auth.application;

/**
 * Cổng hỗ trợ xoay ngữ cảnh CSRF anonymous và thu hồi đúng phiên browser hiện tại (nếu có)
 * khi hoàn tất xác minh email theo AUTH-07, AUTH-09, PHIEN-04, PHIEN-06, PX-20
 * (api-specification-sprint-1.md §8.2, §8.5; thiet-ke-phien-sprint-1.md §2.1, §3.2).
 *
 * Thiết kế theo dạng Port để tách biệt với các task Phiên đăng nhập / CSRF / Đăng xuất phía dưới.
 */
public interface AuthBrowserSessionLifecyclePort {

    /**
     * Thu hồi bền vững đúng phiên hiện có ứng với cookie trên browser (nếu có),
     * dù phiên đó thuộc cùng tài khoản hay tài khoản khác với liên kết xác minh (AUTH-07, AUTH-09).
     * Không thu hồi phiên trên các thiết bị/browser khác.
     */
    void revokeCurrentBrowserSessionIfPresent(String rawSessionCookieValue);

    /**
     * Sinh token CSRF mới ứng với ngữ cảnh khách (anonymous context) sau khi xóa cookie phiên.
     */
    String issueAnonymousCsrfToken();

    /**
     * Sinh giá trị bí mật ngẫu nhiên 32 byte (mã hóa Base64URL không padding) cho cookie phiên opaque
     * (__Host-se214_session) theo PHIEN-04 & thiet-ke-phien-sprint-1.md §2.1.
     */
    default String generateRawSessionCookie() {
        throw new UnsupportedOperationException("Chưa cài đặt generateRawSessionCookie");
    }

    /**
     * Băm một chiều SHA-256 (Base64URL không padding) từ giá trị cookie phiên thô để lưu vào ma_cookie_bam (PHIEN-05).
     */
    default String hashSessionCookie(String rawSessionCookieValue) {
        throw new UnsupportedOperationException("Chưa cài đặt hashSessionCookie");
    }

    /**
     * Sinh token CSRF mới gắn với ngữ cảnh phiên đăng nhập hiện tại
     * (v1|csrf|<sha256_base64url(raw_session_cookie)>|<nonce>) theo PHIEN-06 & thiet-ke-phien-sprint-1.md §2.2.
     */
    default String issueSessionCsrfToken(String rawSessionCookieValue) {
        throw new UnsupportedOperationException("Chưa cài đặt issueSessionCsrfToken");
    }
}
