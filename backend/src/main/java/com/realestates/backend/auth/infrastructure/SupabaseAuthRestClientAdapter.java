package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.application.SupabaseVerifiedIdentity;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import com.realestates.backend.auth.exception.InvalidVerificationLinkException;
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
    public SupabaseVerifiedIdentity verifySignupEmailTokenHash(String tokenHash) {
        // Cố định mục đích type = "signup" theo AUTH-15 & api-specification §8.5; tuyệt đối không dùng "email"
        Map<String, Object> verifyBody = Map.of(
                "type", "signup",
                "token_hash", tokenHash
        );
        return executeSignupVerifyRequest(verifyBody);
    }

    @Override
    public SupabaseVerifiedIdentity verifySignupEmailOtp(String normalizedEmail, String sixDigitOtp) {
        if (isBlank(normalizedEmail)) {
            return verifySignupEmailTokenHash(sixDigitOtp);
        }
        Map<String, Object> verifyBody = new LinkedHashMap<>();
        verifyBody.put("type", "signup");
        verifyBody.put("email", normalizedEmail.trim().toLowerCase(Locale.ROOT));
        verifyBody.put("token", sixDigitOtp);
        return executeSignupVerifyRequest(verifyBody);
    }

    private SupabaseVerifiedIdentity executeSignupVerifyRequest(Map<String, Object> verifyBody) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String apiKey = !isBlank(properties.getAnonKey())
                ? properties.getAnonKey().trim()
                : (!isBlank(properties.getServiceRoleKey()) ? properties.getServiceRoleKey().trim() : "");

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new AuthProviderUnavailableException();
        }

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/auth/v1/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(verifyBody)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientResponseException ex) {
            throw mapSupabaseVerifyHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }

        SupabaseVerifiedIdentity verifiedIdentity = extractVerifiedIdentity(response);

        // Nếu Supabase phát hành access_token tạm trong phản hồi verify, thu hồi ngay ở scope=local
        // để callback không để lại phiên mở và tuyệt đối không trả token ra ngoài (api-specification §8.5)
        String callbackAccessToken = extractAccessTokenIfPresent(response);
        if (!isBlank(callbackAccessToken)) {
            revokeCallbackSessionBestEffort(baseUrl, apiKey, callbackAccessToken.trim());
        }

        return verifiedIdentity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public java.util.Optional<SupabaseVerifiedIdentity> findProviderUserByEmail(String normalizedEmail) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String serviceRoleKey = properties.getServiceRoleKey();
        if (isBlank(normalizedEmail) || baseUrl.isBlank() || isBlank(serviceRoleKey)) {
            return java.util.Optional.empty();
        }
        String targetEmail = normalizedEmail.trim().toLowerCase(Locale.ROOT);
        try {
            Map<String, Object> response = restClient.get()
                    .uri(baseUrl + "/auth/v1/admin/users?page=1&per_page=100")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", serviceRoleKey.trim())
                    .header("Authorization", "Bearer " + serviceRoleKey.trim())
                    .retrieve()
                    .body(MAP_TYPE);
            if (response != null && response.get("users") instanceof List<?> usersList) {
                for (Object item : usersList) {
                    if (item instanceof Map<?, ?> userMapRaw) {
                        Map<String, Object> userMap = (Map<String, Object>) userMapRaw;
                        if (userMap.get("email") instanceof String emailStr
                                && targetEmail.equals(emailStr.trim().toLowerCase(Locale.ROOT))) {
                            return java.util.Optional.of(extractVerifiedIdentity(userMap));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Nếu không tra cứu được danh sách admin thì trả empty
        }
        return java.util.Optional.empty();
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

    @SuppressWarnings("unchecked")
    private static SupabaseVerifiedIdentity extractVerifiedIdentity(Map<String, Object> response) {
        if (response == null) {
            throw new AuthProviderUnavailableException();
        }
        Map<String, Object> userObject = response;
        if (response.get("user") instanceof Map<?, ?> nestedUser) {
            userObject = (Map<String, Object>) nestedUser;
        }

        UUID supabaseUserId = extractSupabaseUserId(response);
        String email = null;
        if (userObject.get("email") instanceof String rawEmail && !rawEmail.isBlank()) {
            email = rawEmail.trim().toLowerCase(Locale.ROOT);
        } else if (response.get("email") instanceof String topEmail && !topEmail.isBlank()) {
            email = topEmail.trim().toLowerCase(Locale.ROOT);
        }
        if (email == null || email.isBlank()) {
            throw new AuthProviderUnavailableException("Phản hồi xác minh từ Supabase Auth thiếu email định danh.");
        }

        boolean emailConfirmed = isEmailConfirmedInPayload(userObject) || isEmailConfirmedInPayload(response);

        String hoTen = null;
        String soDienThoai = null;
        Object rawMetadata = userObject.get("user_metadata");
        if (rawMetadata instanceof Map<?, ?> metaMap) {
            Object rawHoTen = metaMap.get("ho_ten");
            if (rawHoTen instanceof String strHoTen && !strHoTen.isBlank()) {
                hoTen = strHoTen.trim();
            }
            Object rawPhone = metaMap.get("so_dien_thoai");
            if (rawPhone instanceof String strPhone && !strPhone.isBlank()) {
                soDienThoai = strPhone.trim();
            }
        }

        return new SupabaseVerifiedIdentity(supabaseUserId, email, emailConfirmed, hoTen, soDienThoai);
    }

    private static boolean isEmailConfirmedInPayload(Map<String, Object> map) {
        if (map == null) {
            return false;
        }
        Object confirmedAt = map.get("email_confirmed_at");
        if (confirmedAt instanceof String s && !s.isBlank()) {
            return true;
        }
        Object legacyConfirmedAt = map.get("confirmed_at");
        return legacyConfirmedAt instanceof String s2 && !s2.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static String extractAccessTokenIfPresent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        if (response.get("access_token") instanceof String token && !token.isBlank()) {
            return token;
        }
        if (response.get("session") instanceof Map<?, ?> sessionMap) {
            Object nestedToken = ((Map<String, Object>) sessionMap).get("access_token");
            if (nestedToken instanceof String strToken && !strToken.isBlank()) {
                return strToken;
            }
        }
        return null;
    }

    private void revokeCallbackSessionBestEffort(String baseUrl, String apiKey, String accessToken) {
        try {
            restClient.post()
                    .uri(baseUrl + "/auth/v1/logout?scope=local")
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // Best-effort dọn phiên tạm tại provider; không làm gián đoạn kết quả xác minh đã thành công
        }
    }

    private static RuntimeException mapSupabaseVerifyHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String bodyLower = ex.getResponseBodyAsString() != null
                ? ex.getResponseBodyAsString().toLowerCase(Locale.ROOT)
                : "";

        if (status.value() == 429
                || bodyLower.contains("over_email_send_rate_limit")
                || bodyLower.contains("over_request_rate_limit")) {
            return new AuthRateLimitExceededException();
        }

        if (status.value() == 400
                || status.value() == 401
                || status.value() == 403
                || status.value() == 404
                || status.value() == 410
                || status.value() == 422
                || bodyLower.contains("otp_expired")
                || bodyLower.contains("otp_disabled")
                || bodyLower.contains("token_expired")
                || bodyLower.contains("invalid")
                || bodyLower.contains("expired")
                || bodyLower.contains("not_found")
                || bodyLower.contains("bad_code_verifier")) {
            return new InvalidVerificationLinkException();
        }

        return new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
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
