package com.realestates.backend.config;
import jakarta.servlet.SessionCookieConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseCookie;

@Configuration
public class AuthSessionCookieConfig 
{

    @Value("${server.servlet.session.cookie.secure:true}")
    private boolean cookieSecure;

    @Bean
    public ServletContextInitializer sessionCookieInitializer() 
    {
        return servletContext -> 
        {
            SessionCookieConfig sessionCookieConfig = servletContext.getSessionCookieConfig();
            sessionCookieConfig.setHttpOnly(true);
            sessionCookieConfig.setSecure(cookieSecure);
            sessionCookieConfig.setPath("/");
            sessionCookieConfig.setAttribute("SameSite", "Lax");
        };
    }

    public ResponseCookie createSessionCookie(String name, String value, long maxAgeSeconds) 
    {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
