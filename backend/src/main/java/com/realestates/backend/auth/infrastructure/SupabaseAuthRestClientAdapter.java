package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adapter giao tiếp với Supabase Auth (GoTrue HTTP API) phục vụ đăng ký tài khoản (UC-25 / AUTH-10).
 * Cố định vai trò tạo mới là "nguoi_dung", không bao giờ truyền vai trò KDV hoặc Admin.
 */
@Component
public class SupabaseAuthRestClientAdapter implements SupabaseAuthPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final SupabaseAuthProperties properties;
    private final RestClient restClient;

    @Autowired
    public SupabaseAuthRestClientAdapter(SupabaseAuthProperties properties) {
        this(properties, createTimeoutRestClient(properties));
    }

    public SupabaseAuthRestClientAdapter(SupabaseAuthProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient createTimeoutRestClient(SupabaseAuthProperties properties) {
        int connectMs = Math.max(500, properties.getAuthConnectTimeoutMs());
        int readMs = Math.max(1000, properties.getAuthReadTimeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readMs));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public SupabaseRegisteredUser registerUser(
            String normalizedEmail,
            String rawPassword,
            String normalizedHoTen,
            String normalizedSoDienThoai
    ) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String serviceRoleKey = properties.getServiceRoleKey();
        String anonKey = properties.getAnonKey();

        if (baseUrl.isBlank() || (isBlank(serviceRoleKey) && isBlank(anonKey))) {
            throw new AuthProviderUnavailableException();
        }

        Map<String, Object> userMetadata = new LinkedHashMap<>();
        userMetadata.put("ho_ten", normalizedHoTen);
        if (normalizedSoDienThoai != null && !normalizedSoDienThoai.isBlank()) {
            userMetadata.put("so_dien_thoai", normalizedSoDienThoai);
        }
        // Cố định vai trò Người dùng ("nguoi_dung")
        userMetadata.put("vai_tro", VaiTroTaiKhoan.NGUOI_DUNG.getGiaTri());

        if (!isBlank(serviceRoleKey)) {
            return createUserViaAdminApi(baseUrl, serviceRoleKey.trim(), normalizedEmail, rawPassword, userMetadata);
        }
        return createUserViaPublicSignupApi(baseUrl, anonKey.trim(), normalizedEmail, rawPassword, userMetadata);
    }

    @Override
    public void sendSignupVerificationEmail(String normalizedEmail) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String apiKey = !isBlank(properties.getAnonKey())
                ? properties.getAnonKey().trim()
                : (!isBlank(properties.getServiceRoleKey()) ? properties.getServiceRoleKey().trim() : "");

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new AuthProviderUnavailableException();
        }

        Map<String, Object> resendBody = Map.of(
                "type", "signup",
                "email", normalizedEmail
        );

        try {
            restClient.post()
                    .uri(baseUrl + "/auth/v1/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(resendBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapSupabaseHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }
    }

    @Override
    public void deleteUserOnCompensation(UUID supabaseAuthUserId) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String serviceRoleKey = properties.getServiceRoleKey();
        if (supabaseAuthUserId == null || baseUrl.isBlank() || isBlank(serviceRoleKey)) {
            return;
        }
        try {
            restClient.delete()
                    .uri(baseUrl + "/auth/v1/admin/users/" + supabaseAuthUserId)
                    .header("apikey", serviceRoleKey.trim())
                    .header("Authorization", "Bearer " + serviceRoleKey.trim())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Bỏ qua lỗi khi xóa bù trừ để giữ nguyên ngoại lệ gốc
        }
    }

    private SupabaseRegisteredUser createUserViaAdminApi(
            String baseUrl,
            String serviceRoleKey,
            String normalizedEmail,
            String rawPassword,
            Map<String, Object> userMetadata
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("email", normalizedEmail);
        requestBody.put("password", rawPassword);
        requestBody.put("email_confirm", false);
        requestBody.put("user_metadata", userMetadata);
        requestBody.put("app_metadata", Map.of("vai_tro", VaiTroTaiKhoan.NGUOI_DUNG.getGiaTri()));

        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/auth/v1/admin/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .body(requestBody)
                    .retrieve()
                    .body(MAP_TYPE);

            UUID supabaseUserId = extractSupabaseUserId(response);
            return new SupabaseRegisteredUser(supabaseUserId, normalizedEmail, false);
        } catch (RestClientResponseException ex) {
            throw mapSupabaseHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }
    }

    private SupabaseRegisteredUser createUserViaPublicSignupApi(
            String baseUrl,
            String anonKey,
            String normalizedEmail,
            String rawPassword,
            Map<String, Object> userMetadata
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("email", normalizedEmail);
        requestBody.put("password", rawPassword);
        requestBody.put("data", userMetadata);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/auth/v1/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer " + anonKey)
                    .body(requestBody)
                    .retrieve()
                    .body(MAP_TYPE);

            if (isObfuscatedDuplicateSignupResponse(response)) {
                throw new EmailAlreadyExistsException();
            }

            UUID supabaseUserId = extractSupabaseUserId(response);
            return new SupabaseRegisteredUser(supabaseUserId, normalizedEmail, true);
        } catch (RestClientResponseException ex) {
            throw mapSupabaseHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean isObfuscatedDuplicateSignupResponse(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        Object identities = response.get("identities");
        if (identities == null && response.get("user") instanceof Map<?, ?> userMap) {
            identities = ((Map<String, Object>) userMap).get("identities");
        }
        return identities instanceof List<?> list && list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static UUID extractSupabaseUserId(Map<String, Object> response) {
        if (response == null) {
            throw new AuthProviderUnavailableException();
        }
        Object rawId = response.get("id");
        if (rawId == null && response.get("user") instanceof Map<?, ?> userMap) {
            rawId = ((Map<String, Object>) userMap).get("id");
        }
        if (rawId instanceof String idStr && !idStr.isBlank()) {
            try {
                return UUID.fromString(idStr.trim());
            } catch (IllegalArgumentException ex) {
                throw new AuthProviderUnavailableException("Phản hồi định danh từ Supabase Auth không hợp lệ.", ex);
            }
        }
        throw new AuthProviderUnavailableException();
    }

    private static RuntimeException mapSupabaseHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String bodyLower = ex.getResponseBodyAsString() != null
                ? ex.getResponseBodyAsString().toLowerCase(Locale.ROOT)
                : "";

        if (status.value() == 429
                || bodyLower.contains("over_email_send_rate_limit")
                || bodyLower.contains("over_request_rate_limit")) {
            return new AuthRateLimitExceededException();
        }

        if (status.value() == 409
                || bodyLower.contains("email_exists")
                || bodyLower.contains("user_already_exists")
                || bodyLower.contains("already registered")
                || bodyLower.contains("already been registered")) {
            return new EmailAlreadyExistsException();
        }

        return new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
    }

    private static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }
}
