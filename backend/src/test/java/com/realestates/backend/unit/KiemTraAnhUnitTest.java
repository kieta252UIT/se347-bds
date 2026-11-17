package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.domain.KiemTraAnh;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KiemTraAnhUnitTest {

    private static final String WEBP_NHO_BASE64 =
            "UklGRiAAAABXRUJQVlA4TBQAAAAvAAAAAAdQgVQIIAAKmv7HiIj+Bw==";

    private static byte[] taoAnh(String dinhDang) throws IOException {
        BufferedImage anh = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream dauRa = new ByteArrayOutputStream();
        ImageIO.write(anh, dinhDang, dauRa);
        return dauRa.toByteArray();
    }

    private static byte[] taoWebp() {
        return Base64.getDecoder().decode(WEBP_NHO_BASE64);
    }

    @Test
    @DisplayName("Ảnh PNG, JPEG và WebP thật được nhận đúng định dạng")
    void nhanDangAnhThat() throws IOException {
        assertThat(KiemTraAnh.nhanDang(taoAnh("png"))).contains(DinhDangAnh.PNG);
        assertThat(KiemTraAnh.nhanDang(taoAnh("jpg"))).contains(DinhDangAnh.JPEG);
        assertThat(KiemTraAnh.nhanDang(taoWebp())).contains(DinhDangAnh.WEBP);
    }

    @Test
    @DisplayName("Tệp PDF, văn bản, rỗng và null bị từ chối dù đặt đuôi ảnh")
    void tepKhongPhaiAnhBiTuChoi() {
        byte[] pdf = "%PDF-1.4\n1 0 obj<<>>endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        byte[] vanBan = "day khong phai anh".getBytes(StandardCharsets.UTF_8);

        assertThat(KiemTraAnh.nhanDang(pdf)).isEmpty();
        assertThat(KiemTraAnh.nhanDang(vanBan)).isEmpty();
        assertThat(KiemTraAnh.nhanDang(new byte[0])).isEmpty();
        assertThat(KiemTraAnh.nhanDang(null)).isEmpty();
    }

    @Test
    @DisplayName("Ảnh bị cắt cụt hoặc hỏng cấu trúc bị từ chối")
    void anhHongBiTuChoi() throws IOException {
        byte[] png = taoAnh("png");
        byte[] jpeg = taoAnh("jpg");
        byte[] webp = taoWebp();

        assertThat(KiemTraAnh.nhanDang(Arrays.copyOf(png, png.length - 1))).isEmpty();
        assertThat(KiemTraAnh.nhanDang(Arrays.copyOf(jpeg, jpeg.length - 2))).isEmpty();
        assertThat(KiemTraAnh.nhanDang(Arrays.copyOf(webp, webp.length - 1))).isEmpty();

        byte[] pngSaiCrc = png.clone();
        pngSaiCrc[pngSaiCrc.length / 2] ^= (byte) 0xFF;
        assertThat(KiemTraAnh.nhanDang(pngSaiCrc)).isEmpty();
    }

    @Test
    @DisplayName("Chỉ có chữ ký ảnh nhưng không có cấu trúc ảnh bị từ chối")
    void chiCoChuKyBiTuChoi() {
        byte[] chuKyPng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] chuKyJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        assertThat(KiemTraAnh.nhanDang(chuKyPng)).isEmpty();
        assertThat(KiemTraAnh.nhanDang(chuKyJpeg)).isEmpty();
    }

    @Test
    @DisplayName("Ngưỡng kích thước 1 đến 10.000.000 byte")
    void nguongKichThuoc() {
        assertThat(KiemTraAnh.kichThuocHopLe(0)).isFalse();
        assertThat(KiemTraAnh.kichThuocHopLe(1)).isTrue();
        assertThat(KiemTraAnh.kichThuocHopLe(10_000_000L)).isTrue();
        assertThat(KiemTraAnh.kichThuocHopLe(10_000_001L)).isFalse();
    }

    @Test
    @DisplayName("Ảnh hợp lệ nhưng vượt 10.000.000 byte bị từ chối")
    void anhVuotKichThuocBiTuChoi() throws IOException {
        byte[] png = taoAnh("png");
        byte[] lon = new byte[10_000_001];
        System.arraycopy(png, 0, lon, 0, png.length);

        assertThat(KiemTraAnh.nhanDang(lon)).isEmpty();
    }
}

