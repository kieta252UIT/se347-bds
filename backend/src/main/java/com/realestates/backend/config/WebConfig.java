package com.realestates.backend.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.security.allowed-origins:https://localhost,http://localhost:5173}")
    private String allowedOriginsProperty;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", clazz -> {
            if (!clazz.isAnnotationPresent(RestController.class)) {
                return false;
            }
            RequestMapping mapping = clazz.getAnnotation(RequestMapping.class);
            String[] paths = mapping == null
                    ? new String[0]
                    : (mapping.value().length > 0 ? mapping.value() : mapping.path());
            if (paths.length == 0) {
                return true;
            }
            return Arrays.stream(paths)
                    .noneMatch(path -> path.equals("/api") || path.startsWith("/api/"));
        });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = parseAllowedOrigins().toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-CSRF-Token", "Idempotency-Key", "If-Match", "Accept")
                .exposedHeaders("ETag", "Location", "Retry-After")
                .allowCredentials(true);
    }

    public List<String> parseAllowedOrigins() {
        return Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
