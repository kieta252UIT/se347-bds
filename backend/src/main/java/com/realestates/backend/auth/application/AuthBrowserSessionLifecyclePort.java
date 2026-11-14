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
}
