package com.realestates.backend.auth.infrastructure;

import com.realestates.backend.auth.application.SupabaseAuthPort;
import com.realestates.backend.auth.application.SupabaseAuthenticatedSession;
import com.realestates.backend.auth.application.SupabaseRefreshedSession;
import com.realestates.backend.auth.application.SupabaseRegisteredUser;
import com.realestates.backend.auth.application.SupabaseVerifiedIdentity;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.exception.AuthRateLimitExceededException;
import com.realestates.backend.auth.exception.EmailAlreadyExistsException;
import com.realestates.backend.auth.exception.EmailNotVerifiedException;
import com.realestates.backend.auth.exception.InvalidCredentialsException;
import com.realestates.backend.auth.exception.InvalidVerificationLinkException;
import com.realestates.backend.auth.exception.UnauthenticatedSessionException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Adapter giao tiếp với Supabase Auth (GoTrue HTTP API) phục vụ đăng ký tài khoản,
 * xác minh email, đăng nhập, làm mới token và thu hồi phiên.
 */
@Component
public class SupabaseAuthRestClientAdapter implements SupabaseAuthPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final Pattern JWT_SESSION_ID_PATTERN =
            Pattern.compile("\"session_id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");

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

        Map<String, Object> requestBody = Map.of(
                "type", "signup",
                "email", normalizedEmail
        );

        try {
            restClient.post()
                    .uri(baseUrl + "/auth/v1/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw mapSupabaseVerifyHttpError(ex);
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
                    .body(verifyBody)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientResponseException ex) {
            throw mapSupabaseVerifyHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }

        SupabaseVerifiedIdentity verifiedIdentity = extractVerifiedIdentity(response);
        String callbackAccessToken = extractAccessTokenIfPresent(response);
        if (callbackAccessToken != null && !callbackAccessToken.isBlank()) {
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
            if (response != null && response.get("users") instanceof List<?> userList) {
                for (Object rawUser : userList) {
                    if (rawUser instanceof Map<?, ?> rawMap) {
                        Map<String, Object> userMap = (Map<String, Object>) rawMap;
                        Object emailObj = userMap.get("email");
                        if (emailObj instanceof String emailStr
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

    @Override
    public SupabaseAuthenticatedSession authenticateWithPassword(String normalizedEmail, String rawPassword) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String apiKey = !isBlank(properties.getAnonKey())
                ? properties.getAnonKey().trim()
                : (!isBlank(properties.getServiceRoleKey()) ? properties.getServiceRoleKey().trim() : "");

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new AuthProviderUnavailableException();
        }

        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("email", normalizedEmail);
        loginBody.put("password", rawPassword);

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/auth/v1/token?grant_type=password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(loginBody)
                    .retrieve()
                    .body(MAP_TYPE);
        } catch (RestClientResponseException ex) {
            throw mapSupabaseLoginHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }

        SupabaseVerifiedIdentity identity = extractVerifiedIdentity(response);
        String accessToken = extractAccessTokenIfPresent(response);
        String refreshToken = extractRefreshTokenIfPresent(response);
        if (isBlank(accessToken) || isBlank(refreshToken)) {
            throw new AuthProviderUnavailableException("Phản hồi đăng nhập từ Supabase Auth thiếu access_token hoặc refresh_token.");
        }

        Instant expiresAt = extractAccessTokenExpiresAt(response);
        UUID supabaseSessionId = extractSupabaseSessionId(response, accessToken.trim());

        return new SupabaseAuthenticatedSession(
                identity.supabaseAuthUserId(),
                identity.email(),
                identity.emailConfirmed(),
                supabaseSessionId,
                accessToken.trim(),
                refreshToken.trim(),
                expiresAt,
                identity.hoTenMetadata(),
                identity.soDienThoaiMetadata()
        );
    }

    @Override
    public SupabaseRefreshedSession refreshSessionToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException("rawRefreshToken không được rỗng");
        }
        String baseUrl = properties.getNormalizedBaseUrl();
        String apiKey = !isBlank(properties.getAnonKey())
                ? properties.getAnonKey().trim()
                : (!isBlank(properties.getServiceRoleKey()) ? properties.getServiceRoleKey().trim() : "");

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new AuthProviderUnavailableException();
        }

        Map<String, Object> refreshBody = Map.of(
                "refresh_token", rawRefreshToken.trim()
        );

        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/auth/v1/token?grant_type=refresh_token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("apikey", apiKey)
                    .body(refreshBody)
                    .retrieve()
                    .body(MAP_TYPE);

            if (response == null) {
                throw new AuthProviderUnavailableException();
            }

            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Number expiresIn = (Number) response.get("expires_in");
            long expiresSec = expiresIn != null ? expiresIn.longValue() : 3600L;

            UUID supabaseUserId = extractSupabaseUserId(response);
            UUID supabaseSessionId = extractSupabaseSessionId(response, accessToken != null ? accessToken.trim() : "");

            return new SupabaseRefreshedSession(
                    accessToken != null ? accessToken : "",
                    refreshToken != null ? refreshToken : rawRefreshToken.trim(),
                    expiresSec,
                    supabaseSessionId,
                    supabaseUserId
            );
        } catch (RestClientResponseException ex) {
            throw mapSupabaseRefreshHttpError(ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
        }
    }

    @Override
    public void revokeProviderSessionLocal(String accessToken) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String apiKey = !isBlank(properties.getAnonKey())
                ? properties.getAnonKey().trim()
                : (!isBlank(properties.getServiceRoleKey()) ? properties.getServiceRoleKey().trim() : "");
        if (baseUrl.isBlank() || apiKey.isBlank() || isBlank(accessToken)) {
            return;
        }
        try {
            restClient.post()
                    .uri(baseUrl + "/auth/v1/logout?scope=local")
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + accessToken.trim())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // Nếu 400/401/404 tức là token đã vô hiệu hoặc đã hết hạn trên Supabase -> coi như đã thu hồi xong
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 404) {
                return;
            }
            throw new AuthProviderUnavailableException("Không thể thu hồi phiên trên Supabase Auth.", ex);
        } catch (RestClientException ex) {
            throw new AuthProviderUnavailableException("Lỗi kết nối khi thu hồi phiên trên Supabase Auth.", ex);
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
                    .header("apikey", serviceRoleKey.trim())
                    .header("Authorization", "Bearer " + serviceRoleKey.trim())
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
                    .header("apikey", anonKey.trim())
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
            Object hoTenObj = metaMap.get("ho_ten");
            if (hoTenObj instanceof String hoTenStr) {
                hoTen = hoTenStr;
            }
            Object sdtObj = metaMap.get("so_dien_thoai");
            if (sdtObj instanceof String sdtStr) {
                soDienThoai = sdtStr;
            }
        }

        return new SupabaseVerifiedIdentity(
                supabaseUserId,
                email,
                emailConfirmed,
                hoTen,
                soDienThoai
        );
    }

    private static boolean isEmailConfirmedInPayload(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        Object confirmedAt = payload.get("email_confirmed_at");
        if (confirmedAt instanceof String s && !s.isBlank()) {
            return true;
        }
        Object confirmedFlag = payload.get("email_confirm");
        return Boolean.TRUE.equals(confirmedFlag);
    }

    private static String extractAccessTokenIfPresent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object token = response.get("access_token");
        if (token instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        if (response.get("session") instanceof Map<?, ?> sessionMap) {
            Object nestedToken = sessionMap.get("access_token");
            if (nestedToken instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }

    private static String extractRefreshTokenIfPresent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object token = response.get("refresh_token");
        if (token instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        if (response.get("session") instanceof Map<?, ?> sessionMap) {
            Object nestedToken = sessionMap.get("refresh_token");
            if (nestedToken instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }

    private static Instant extractAccessTokenExpiresAt(Map<String, Object> response) {
        if (response != null) {
            Object expObj = response.get("expires_at");
            if (expObj instanceof Number num) {
                return Instant.ofEpochSecond(num.longValue());
            }
            Object inObj = response.get("expires_in");
            if (inObj instanceof Number num) {
                return Instant.now().plusSeconds(num.longValue());
            }
        }
        return Instant.now().plusSeconds(3600L);
    }

    private static UUID extractSupabaseSessionId(Map<String, Object> response, String accessToken) {
        if (response != null) {
            Object rawSid = response.get("session_id");
            if (rawSid == null && response.get("session") instanceof Map<?, ?> sessionMap) {
                rawSid = sessionMap.get("id");
                if (rawSid == null) {
                    rawSid = sessionMap.get("session_id");
                }
            }
            if (rawSid instanceof String sidStr && !sidStr.isBlank()) {
                try {
                    return UUID.fromString(sidStr.trim());
                } catch (IllegalArgumentException ignored) {
                    // Tiếp tục giải mã claim session_id từ JWT
                }
            }
        }

        // Giải mã claim session_id từ payload JWT của GoTrue nếu có
        if (accessToken != null && !accessToken.isBlank()) {
            String[] parts = accessToken.split("\\.");
            if (parts.length >= 2) {
                try {
                    byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                    String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
                    Matcher matcher = JWT_SESSION_ID_PATTERN.matcher(payloadJson);
                    if (matcher.find()) {
                        return UUID.fromString(matcher.group(1));
                    }
                } catch (Exception ignored) {
                    // Fallback sang UUID xác định từ accessToken nếu chuỗi token mock không phải JWT
                }
            }
            return UUID.nameUUIDFromBytes(("supabase-session:" + accessToken).getBytes(StandardCharsets.UTF_8));
        }

        return UUID.randomUUID();
    }

    private static RuntimeException mapSupabaseLoginHttpError(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String bodyLower = ex.getResponseBodyAsString() != null
                ? ex.getResponseBodyAsString().toLowerCase(Locale.ROOT)
                : "";

        if (status.value() == 429
                || bodyLower.contains("over_email_send_rate_limit")
                || bodyLower.contains("over_request_rate_limit")) {
            return new AuthRateLimitExceededException();
        }

        if (bodyLower.contains("email_not_confirmed")
                || bodyLower.contains("email not confirmed")) {
            return new EmailNotVerifiedException();
        }

        if (status.value() == 400
                || status.value() == 401
                || status.value() == 403
                || status.value() == 422
                || bodyLower.contains("invalid_credentials")
                || bodyLower.contains("invalid_grant")
                || bodyLower.contains("invalid login credentials")
                || bodyLower.contains("user_not_found")
                || bodyLower.contains("user_banned")) {
            return new InvalidCredentialsException();
        }

        return new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
    }

    private static RuntimeException mapSupabaseRefreshHttpError(RestClientResponseException ex) {
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
                || status.value() == 422
                || bodyLower.contains("invalid_grant")
                || bodyLower.contains("invalid_token")
                || bodyLower.contains("refresh_token_not_found")) {
            return new UnauthenticatedSessionException("Phiên đăng nhập đã hết hạn hoặc refresh token không hợp lệ.");
        }

        return new AuthProviderUnavailableException(AuthProviderUnavailableException.THONG_DIEP_MAC_DINH, ex);
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
