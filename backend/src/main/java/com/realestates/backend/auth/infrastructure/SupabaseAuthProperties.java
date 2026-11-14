package com.realestates.backend.auth.infrastructure;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình kết nối Supabase Auth đọc từ biến môi trường / application.yaml
 * (kèm cơ chế nạp dự phòng từ file .env cục bộ khi chạy local dev).
 */
@Component
@ConfigurationProperties(prefix = "supabase")
public class SupabaseAuthProperties {

    private String url = "";
    private String anonKey = "";
    private String serviceRoleKey = "";
    private String jwtSecret = "";
    private int authConnectTimeoutMs = 3000;
    private int authReadTimeoutMs = 10000;

    @PostConstruct
    public void loadDotEnvFallbackIfNeeded() {
        if (hasText(url) && (hasText(serviceRoleKey) || hasText(anonKey))) {
            return;
        }
        Map<String, String> dotEnv = readLocalDotEnv();
        if (!hasText(url)) {
            url = dotEnv.getOrDefault("SUPABASE_URL", "");
        }
        if (!hasText(anonKey)) {
            anonKey = dotEnv.getOrDefault("SUPABASE_ANON_KEY", "");
        }
        if (!hasText(serviceRoleKey)) {
            serviceRoleKey = dotEnv.getOrDefault("SUPABASE_SERVICE_ROLE_KEY", "");
        }
        if (!hasText(jwtSecret)) {
            jwtSecret = dotEnv.getOrDefault("SUPABASE_JWT_SECRET", "");
        }
    }

    private Map<String, String> readLocalDotEnv() {
        List<Path> candidates = List.of(
                Path.of(".env"),
                Path.of("backend", ".env")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    Map<String, String> values = new HashMap<>();
                    for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int sep = trimmed.indexOf('=');
                        if (sep > 0) {
                            String key = trimmed.substring(0, sep).trim();
                            String val = trimmed.substring(sep + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\""))
                                    || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            values.put(key, val);
                        }
                    }
                    return values;
                } catch (IOException ignored) {
                    // Bỏ qua nếu không đọc được file .env cục bộ
                }
            }
        }
        return Map.of();
    }

    public String getNormalizedBaseUrl() {
        if (!hasText(url)) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAnonKey() {
        return anonKey;
    }

    public void setAnonKey(String anonKey) {
        this.anonKey = anonKey;
    }

    public String getServiceRoleKey() {
        return serviceRoleKey;
    }

    public void setServiceRoleKey(String serviceRoleKey) {
        this.serviceRoleKey = serviceRoleKey;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getAuthConnectTimeoutMs() {
        return authConnectTimeoutMs;
    }

    public void setAuthConnectTimeoutMs(int authConnectTimeoutMs) {
        this.authConnectTimeoutMs = authConnectTimeoutMs;
    }

    public int getAuthReadTimeoutMs() {
        return authReadTimeoutMs;
    }

    public void setAuthReadTimeoutMs(int authReadTimeoutMs) {
        this.authReadTimeoutMs = authReadTimeoutMs;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
