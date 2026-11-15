package com.realestates.backend.config;

import jakarta.servlet.SessionCookieConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseCookie;

@Configuration
public class AuthSessionCookieConfig {

    public static final String SESSION_COOKIE_NAME = "__Host-se214_session";
    public static final String CSRF_COOKIE_NAME = "__Host-se214_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final long SESSION_MAX_AGE_SECONDS = 31536000L;

    @Value("${app.security.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${app.security.session-cookie-name:__Host-se214_session}")
    private String sessionCookieName;

    @Value("${app.security.session-persistent:false}")
    private boolean sessionPersistent = false;

    @Value("${app.security.session-max-age-seconds:31536000}")
    private long sessionMaxAgeSeconds;

    @Value("${app.security.csrf-cookie-name:__Host-se214_csrf}")
    private String csrfCookieName;

    @Bean
    public ServletContextInitializer sessionCookieInitializer() {
        return servletContext -> {
            SessionCookieConfig sessionCookieConfig = servletContext.getSessionCookieConfig();
            sessionCookieConfig.setName(sessionCookieName);
            sessionCookieConfig.setHttpOnly(true);
            sessionCookieConfig.setSecure(cookieSecure);
            sessionCookieConfig.setPath("/");
            if (sessionPersistent && sessionMaxAgeSeconds > 0) {
                sessionCookieConfig.setMaxAge((int) sessionMaxAgeSeconds);
            } else {
                sessionCookieConfig.setMaxAge(-1); // Session cookie (xóa khi đóng trình duyệt)
            }
            sessionCookieConfig.setAttribute("SameSite", "Lax");
        };
    }

    public ResponseCookie createSessionCookie(String value) {
        if (!sessionPersistent || sessionMaxAgeSeconds <= 0) {
            return createBrowserSessionCookie(value);
        }
        return createSessionCookie(sessionCookieName, value, sessionMaxAgeSeconds);
    }

    public ResponseCookie createPersistentSessionCookie(String value) {
        return createSessionCookie(sessionCookieName, value, sessionMaxAgeSeconds);
    }

    /**
     * Tạo cookie phiên trình duyệt (Session Cookie không có Max-Age):
     * - Khi người dùng đóng tab/đóng web và mở lại thì vẫn duy trì đăng nhập trong phiên duyệt web đó.
     * - Khi đóng hẳn trình duyệt (exit browser process), trình duyệt sẽ xóa cookie khỏi RAM và buộc đăng nhập lại.
     */
    public ResponseCookie createBrowserSessionCookie(String value) {
        return ResponseCookie.from(sessionCookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .build();
    }

    public ResponseCookie createSessionCookie(String name, String value, long maxAgeSeconds) {
        var builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/");
        if (maxAgeSeconds >= 0) {
            builder.maxAge(maxAgeSeconds);
        }
        return builder.build();
    }

    public ResponseCookie clearSessionCookie() {
        return createSessionCookie(sessionCookieName, "", 0L);
    }

    public ResponseCookie createCsrfCookie(String signedToken) {
        return ResponseCookie.from(csrfCookieName, signedToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .build();
    }

    public ResponseCookie clearCsrfCookie() {
        return ResponseCookie.from(csrfCookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0L)
                .build();
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public String getCsrfCookieName() {
        return csrfCookieName;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public boolean isSessionPersistent() {
        return sessionPersistent;
    }

    public void setSessionPersistent(boolean sessionPersistent) {
        this.sessionPersistent = sessionPersistent;
    }
}
