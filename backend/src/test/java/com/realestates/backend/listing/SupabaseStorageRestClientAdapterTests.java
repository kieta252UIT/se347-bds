package com.realestates.backend.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.auth.infrastructure.SupabaseAuthProperties;
import com.realestates.backend.listing.infrastructure.SupabaseStorageRestClientAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseStorageRestClientAdapterTests {

    private static final byte[] NOI_DUNG = {1, 2, 3};

    private MockRestServiceServer server;
    private SupabaseStorageRestClientAdapter adapter;

    @BeforeEach
    void setUp() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setUrl("http://supabase.test/");
        properties.setServiceRoleKey("khoa-dich-vu");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SupabaseStorageRestClientAdapter(properties, "anh-tin", builder.build());
    }

    @Test
    @DisplayName("Lưu ảnh gửi đúng đường dẫn bucket riêng tư, khóa dịch vụ và kiểu nội dung")
    void luuGuiDungYeuCau() {
        server.expect(requestTo("http://supabase.test/storage/v1/object/anh-tin/abc.png"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "khoa-dich-vu"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer khoa-dich-vu"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(content().bytes(NOI_DUNG))
                .andRespond(withSuccess("{\"Key\":\"anh-tin/abc.png\"}", MediaType.APPLICATION_JSON));

        adapter.luu("abc.png", NOI_DUNG, "image/png");

        server.verify();
    }

    @Test
    @DisplayName("Storage trả lỗi khi lưu thì báo tạm gián đoạn")
    void luuLoiStorageThanhTamGianDoan() {
        server.expect(requestTo("http://supabase.test/storage/v1/object/anh-tin/abc.png"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.luu("abc.png", NOI_DUNG, "image/png"))
                .isInstanceOf(AuthProviderUnavailableException.class);
    }

    @Test
    @DisplayName("Đọc ảnh dùng đường dẫn authenticated và trả đúng byte")
    void docGuiDungYeuCauVaTraNoiDung() {
        server.expect(requestTo("http://supabase.test/storage/v1/object/authenticated/anh-tin/abc.png"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "khoa-dich-vu"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer khoa-dich-vu"))
                .andRespond(withSuccess(NOI_DUNG, MediaType.IMAGE_PNG));

        assertThat(adapter.doc("abc.png")).isEqualTo(NOI_DUNG);
        server.verify();
    }

    @Test
    @DisplayName("Không tìm thấy đối tượng khi đọc thì báo tạm gián đoạn")
    void docKhongTimThayThanhTamGianDoan() {
        server.expect(requestTo("http://supabase.test/storage/v1/object/authenticated/anh-tin/abc.png"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> adapter.doc("abc.png"))
                .isInstanceOf(AuthProviderUnavailableException.class);
    }

    @Test
    @DisplayName("Chưa cấu hình Supabase thì lưu và đọc đều báo tạm gián đoạn")
    void chuaCauHinhThanhTamGianDoan() {
        SupabaseStorageRestClientAdapter chuaCauHinh = new SupabaseStorageRestClientAdapter(
                new SupabaseAuthProperties(),
                "anh-tin",
                RestClient.builder().build()
        );

        assertThatThrownBy(() -> chuaCauHinh.luu("abc.png", NOI_DUNG, "image/png"))
                .isInstanceOf(AuthProviderUnavailableException.class);
        assertThatThrownBy(() -> chuaCauHinh.doc("abc.png"))
                .isInstanceOf(AuthProviderUnavailableException.class);
    }
}

