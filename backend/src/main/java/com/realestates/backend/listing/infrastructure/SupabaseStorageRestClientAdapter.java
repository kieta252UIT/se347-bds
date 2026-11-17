package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.infrastructure.SupabaseAuthProperties;
import com.realestates.backend.listing.application.AnhStoragePort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.repository.type", havingValue = "postgres")
public class SupabaseStorageRestClientAdapter implements AnhStoragePort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageRestClientAdapter.class);
    private static final String THONG_DIEP_GIAN_DOAN = "Dịch vụ lưu trữ ảnh đang tạm gián đoạn. Vui lòng thử lại sau.";

    private final SupabaseAuthProperties properties;
    private final String bucket;
    private final RestClient restClient;

    @Autowired
    public SupabaseStorageRestClientAdapter(
            SupabaseAuthProperties properties,
            @Value("${supabase.storage-bucket:anh-tin}") String bucket
    ) {
        this(properties, bucket, taoRestClient(properties));
    }

    public SupabaseStorageRestClientAdapter(SupabaseAuthProperties properties, String bucket, RestClient restClient) {
        this.properties = properties;
        this.bucket = bucket;
        this.restClient = restClient;
    }

    private static RestClient taoRestClient(SupabaseAuthProperties properties) {
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
    public void luu(String khoaDoiTuong, byte[] noiDung, String mimeType) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String serviceRoleKey = properties.getServiceRoleKey();
        if (baseUrl.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN);
        }
        try {
            restClient.post()
                    .uri(baseUrl + "/storage/v1/object/" + bucket + "/" + khoaDoiTuong)
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header("apikey", serviceRoleKey.trim())
                    .header("Authorization", "Bearer " + serviceRoleKey.trim())
                    .body(noiDung)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            ghiLogLoiStorage("lưu", khoaDoiTuong, ex);
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    @Override
    public byte[] doc(String khoaDoiTuong) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String serviceRoleKey = properties.getServiceRoleKey();
        if (baseUrl.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN);
        }
        byte[] noiDung;
        try {
            noiDung = restClient.get()
                    .uri(baseUrl + "/storage/v1/object/authenticated/" + bucket + "/" + khoaDoiTuong)
                    .header("apikey", serviceRoleKey.trim())
                    .header("Authorization", "Bearer " + serviceRoleKey.trim())
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException ex) {
            ghiLogLoiStorage("đọc", khoaDoiTuong, ex);
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
        if (noiDung == null || noiDung.length == 0) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN);
        }
        return noiDung;
    }

    @Override
    public void xoa(String khoaDoiTuong) {
        xoaDoiTuong(khoaDoiTuong, false);
    }

    @Override
    public boolean xoaNeuTonTai(String khoaDoiTuong) {
        return xoaDoiTuong(khoaDoiTuong, true);
    }

    private boolean xoaDoiTuong(String khoaDoiTuong, boolean boQuaKhiKhongTimThay) {
        String baseUrl = properties.getNormalizedBaseUrl();
        String serviceRoleKey = properties.getServiceRoleKey();
        if (baseUrl.isBlank() || serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN);
        }
        try {
            restClient.delete()
                    .uri(baseUrl + "/storage/v1/object/" + bucket + "/" + khoaDoiTuong)
                    .header("apikey", serviceRoleKey.trim())
                    .header("Authorization", "Bearer " + serviceRoleKey.trim())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            if (boQuaKhiKhongTimThay) {
                return false;
            }
            ghiLogLoiStorage("xóa", khoaDoiTuong, ex);
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        } catch (RestClientException ex) {
            ghiLogLoiStorage("xóa", khoaDoiTuong, ex);
            throw new AuthProviderUnavailableException(THONG_DIEP_GIAN_DOAN, ex);
        }
    }

    private static void ghiLogLoiStorage(String hanhDong, String khoaDoiTuong, RestClientException ex) {
        if (ex instanceof HttpClientErrorException clientEx) {
            log.warn("Lỗi client 4xx từ Supabase Storage khi {} {}: status={}", hanhDong, khoaDoiTuong, clientEx.getStatusCode());
        } else if (ex instanceof HttpServerErrorException serverEx) {
            log.warn("Lỗi server 5xx từ Supabase Storage khi {} {}: status={}", hanhDong, khoaDoiTuong, serverEx.getStatusCode());
        } else {
            log.warn("Lỗi kết nối Supabase Storage khi {} {}: {}", hanhDong, khoaDoiTuong, ex.getMessage());
        }
    }
}
