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

    @Test
    @DisplayName("Giải mã có giới hạn thành công với ảnh PNG, JPEG và WebP thật")
    void giaiMaThanhCongVoiAnhThat() throws IOException {
        byte[] png = taoAnh("png");
        byte[] jpeg = taoAnh("jpg");
        byte[] webp = taoWebp();

        assertThat(KiemTraAnh.giaiMaCoGioiHan(png)).isPresent();
        assertThat(KiemTraAnh.giaiMaCoGioiHan(jpeg)).isPresent();
        assertThat(KiemTraAnh.giaiMaCoGioiHan(webp)).isPresent();
    }

    @Test
    @DisplayName("Tạo bản hiển thị tái mã hóa ảnh PNG, JPEG và WebP thành công")
    void taoBanHienThiThanhCong() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        KiemTraAnh.BanAnhHienThi banPng = KiemTraAnh.taoBanHienThi(img, DinhDangAnh.PNG);
        assertThat(banPng.mimeType()).isEqualTo("image/png");
        assertThat(banPng.duoiTep()).isEqualTo("png");
        assertThat(banPng.noiDung()).isNotEmpty();

        KiemTraAnh.BanAnhHienThi banJpg = KiemTraAnh.taoBanHienThi(img, DinhDangAnh.JPEG);
        assertThat(banJpg.mimeType()).isEqualTo("image/jpeg");
        assertThat(banJpg.duoiTep()).isEqualTo("jpg");
        assertThat(banJpg.noiDung()).isNotEmpty();

        KiemTraAnh.BanAnhHienThi banWebp = KiemTraAnh.taoBanHienThi(img, DinhDangAnh.WEBP);
        assertThat(banWebp.noiDung()).isNotEmpty();
    }

    @Test
    @DisplayName("Dữ liệu hỏng hoặc rác không thể giải mã")
    void duLieuHongKhongGiaiMaDuoc() {
        assertThat(KiemTraAnh.giaiMaCoGioiHan(null)).isEmpty();
        assertThat(KiemTraAnh.giaiMaCoGioiHan(new byte[0])).isEmpty();
        assertThat(KiemTraAnh.giaiMaCoGioiHan(new byte[]{1, 2, 3, 4})).isEmpty();
    }

    @Test
    @DisplayName("WebP VP8X giả 30 byte không có chunk ảnh bị từ chối")
    void webpVp8xGiaBiTuChoi() {
        byte[] vp8xGia = {
                'R', 'I', 'F', 'F', 22, 0, 0, 0,
                'W', 'E', 'B', 'P', 'V', 'P', '8', 'X',
                10, 0, 0, 0, 0, 0, 0, 0,
                7, 0, 0, 7, 0, 0
        };

        assertThat(KiemTraAnh.nhanDang(vp8xGia)).isEmpty();
        assertThat(KiemTraAnh.giaiMaCoGioiHan(vp8xGia)).isEmpty();
    }

    @Test
    @DisplayName("Ảnh vượt ngưỡng pixel cho phép bị giaiMaCoGioiHan từ chối")
    void anhVuotGioiHanPixelBiTuChoi() throws IOException {
        BufferedImage quaRong = new BufferedImage(KiemTraAnh.MAX_CHIEU_RONG + 1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream dauRa = new ByteArrayOutputStream();
        ImageIO.write(quaRong, "png", dauRa);
        byte[] pngQuaRong = dauRa.toByteArray();

        assertThat(KiemTraAnh.nhanDang(pngQuaRong)).contains(DinhDangAnh.PNG);
        assertThat(KiemTraAnh.giaiMaCoGioiHan(pngQuaRong)).isEmpty();
    }

    @Test
    @DisplayName("Ảnh JPEG có EXIF Orientation 6 được xoay đúng chiều và loại bỏ tag EXIF ở bản hiển thị")
    void jpegExifOrientation6DuocXoayDungChieu() throws IOException {
        BufferedImage goc = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = goc.createGraphics();
        try {
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 24, 8);
            g.setColor(java.awt.Color.BLUE);
            g.fillRect(0, 8, 24, 8);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream rawJpeg = new ByteArrayOutputStream();
        ImageIO.write(goc, "jpg", rawJpeg);
        byte[] jpegCoExif = ganApp1ExifOrientation6(rawJpeg.toByteArray());

        assertThat(KiemTraAnh.nhanDang(jpegCoExif)).contains(DinhDangAnh.JPEG);
        assertThat(KiemTraAnh.docExifOrientation(jpegCoExif)).isEqualTo(6);

        BufferedImage daXoay = KiemTraAnh.giaiMaCoGioiHan(jpegCoExif).orElseThrow();
        assertThat(daXoay.getWidth()).isEqualTo(16);
        assertThat(daXoay.getHeight()).isEqualTo(24);
        int topLeftRgb = daXoay.getRGB(1, 1);
        assertThat(topLeftRgb & 0xFF).isGreaterThan(150);
        assertThat((topLeftRgb >> 16) & 0xFF).isLessThan(80);

        KiemTraAnh.BanAnhHienThi banHienThi = KiemTraAnh.taoBanHienThi(daXoay, DinhDangAnh.JPEG);
        assertThat(KiemTraAnh.docExifOrientation(banHienThi.noiDung())).isEqualTo(1);
    }

    private static byte[] ganApp1ExifOrientation6(byte[] jpeg) {
        byte[] app1 = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        byte[] out = new byte[jpeg.length + app1.length];
        out[0] = jpeg[0];
        out[1] = jpeg[1];
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }
}

