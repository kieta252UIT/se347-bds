package com.realestates.backend.listing.domain;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class KiemTraAnh {

    public static final long KICH_THUOC_TOI_DA_BYTE = 10_000_000L;
    public static final int MAX_CHIEU_RONG = 4096;
    public static final int MAX_CHIEU_CAO = 4096;
    public static final long MAX_TONG_SO_PIXEL = 16_000_000L;

    public record BanAnhHienThi(byte[] noiDung, String duoiTep, String mimeType) {
    }

    private static final byte[] CHU_KY_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private KiemTraAnh() {
    }

    public static boolean kichThuocHopLe(long kichThuocByte) {
        return kichThuocByte >= 1 && kichThuocByte <= KICH_THUOC_TOI_DA_BYTE;
    }

    public static Optional<DinhDangAnh> nhanDang(byte[] noiDung) {
        if (noiDung == null || !kichThuocHopLe(noiDung.length)) {
            return Optional.empty();
        }
        if (laPngHopLe(noiDung)) {
            return Optional.of(DinhDangAnh.PNG);
        }
        if (laJpegHopLe(noiDung)) {
            return Optional.of(DinhDangAnh.JPEG);
        }
        if (laWebpHopLe(noiDung)) {
            return Optional.of(DinhDangAnh.WEBP);
        }
        return Optional.empty();
    }

    public static Optional<BufferedImage> giaiMaCoGioiHan(byte[] noiDung) {
        if (noiDung == null || !kichThuocHopLe(noiDung.length) || nhanDang(noiDung).isEmpty()) {
            return Optional.empty();
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(noiDung))) {
            if (iis == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int rong = reader.getWidth(0);
                int cao = reader.getHeight(0);
                if (rong <= 0 || cao <= 0 || rong > MAX_CHIEU_RONG || cao > MAX_CHIEU_CAO
                        || ((long) rong * cao) > MAX_TONG_SO_PIXEL) {
                    return Optional.empty();
                }
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    return Optional.empty();
                }
                int orientation = docExifOrientation(noiDung);
                return Optional.of(apDungExifOrientation(image, orientation));
            } finally {
                reader.dispose();
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public static BanAnhHienThi taoBanHienThi(BufferedImage image, DinhDangAnh dinhDangGoc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (dinhDangGoc == DinhDangAnh.PNG) {
            ImageIO.write(image, "png", out);
            return new BanAnhHienThi(out.toByteArray(), "png", "image/png");
        } else {
            boolean coAlpha = image.getColorModel() != null && image.getColorModel().hasAlpha();
            if (coAlpha) {
                ImageIO.write(image, "png", out);
                return new BanAnhHienThi(out.toByteArray(), "png", "image/png");
            } else {
                BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                try {
                    g.drawImage(image, 0, 0, null);
                } finally {
                    g.dispose();
                }
                ImageIO.write(rgbImage, "jpg", out);
                return new BanAnhHienThi(out.toByteArray(), "jpg", "image/jpeg");
            }
        }
    }

    public static int docExifOrientation(byte[] d) {
        if (d == null || d.length < 12) {
            return 1;
        }
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) {
            return docExifOrientationTuJpeg(d);
        }
        if (d.length >= 30 && khop(d, 0, "RIFF") && khop(d, 8, "WEBP") && khop(d, 12, "VP8X")) {
            return docExifOrientationTuWebp(d);
        }
        return 1;
    }

    public static BufferedImage apDungExifOrientation(BufferedImage src, int orientation) {
        if (src == null || orientation < 2 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        int destW = swap ? h : w;
        int destH = swap ? w : h;
        boolean hasAlpha = src.getColorModel() != null && src.getColorModel().hasAlpha();
        BufferedImage dest = new BufferedImage(
                destW,
                destH,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> {
                        dx = w - 1 - x;
                        dy = y;
                    }
                    case 3 -> {
                        dx = w - 1 - x;
                        dy = h - 1 - y;
                    }
                    case 4 -> {
                        dx = x;
                        dy = h - 1 - y;
                    }
                    case 5 -> {
                        dx = y;
                        dy = x;
                    }
                    case 6 -> {
                        dx = h - 1 - y;
                        dy = x;
                    }
                    case 7 -> {
                        dx = h - 1 - y;
                        dy = w - 1 - x;
                    }
                    case 8 -> {
                        dx = y;
                        dy = w - 1 - x;
                    }
                    default -> {
                        dx = x;
                        dy = y;
                    }
                }
                dest.setRGB(dx, dy, src.getRGB(x, y));
            }
        }
        return dest;
    }

    private static int docExifOrientationTuJpeg(byte[] d) {
        int viTri = 2;
        while (viTri < d.length) {
            if ((d[viTri] & 0xFF) != 0xFF) {
                return 1;
            }
            while (viTri < d.length && (d[viTri] & 0xFF) == 0xFF) {
                viTri++;
            }
            if (viTri >= d.length) {
                return 1;
            }
            int dauHieu = d[viTri++] & 0xFF;
            if (dauHieu == 0xD9 || dauHieu == 0xDA || dauHieu == 0x00 || dauHieu == 0xD8) {
                return 1;
            }
            if (dauHieu == 0x01 || (dauHieu >= 0xD0 && dauHieu <= 0xD7)) {
                continue;
            }
            if (viTri + 2 > d.length) {
                return 1;
            }
            int doDaiDoan = ((d[viTri] & 0xFF) << 8) | (d[viTri + 1] & 0xFF);
            if (doDaiDoan < 2 || viTri + doDaiDoan > d.length) {
                return 1;
            }
            if (dauHieu == 0xE1 && doDaiDoan >= 16
                    && d[viTri + 2] == 'E' && d[viTri + 3] == 'x' && d[viTri + 4] == 'i'
                    && d[viTri + 5] == 'f' && d[viTri + 6] == 0 && d[viTri + 7] == 0) {
                return docOrientationTuTiff(d, viTri + 8, doDaiDoan - 8);
            }
            viTri += doDaiDoan;
        }
        return 1;
    }

    private static int docExifOrientationTuWebp(byte[] d) {
        int viTri = 30;
        while (viTri + 8 <= d.length) {
            long chunkLen = docUnsigned32LittleEndian(d, viTri + 4);
            if (chunkLen < 0 || viTri + 8 + chunkLen > d.length) {
                return 1;
            }
            if (khop(d, viTri, "EXIF") && chunkLen >= 8) {
                int len = (int) chunkLen;
                int dataStart = viTri + 8;
                if (len >= 14 && d[dataStart] == 'E' && d[dataStart + 1] == 'x' && d[dataStart + 2] == 'i'
                        && d[dataStart + 3] == 'f' && d[dataStart + 4] == 0 && d[dataStart + 5] == 0) {
                    return docOrientationTuTiff(d, dataStart + 6, len - 6);
                }
                return docOrientationTuTiff(d, dataStart, len);
            }
            viTri += 8 + (int) chunkLen + ((chunkLen % 2 != 0) ? 1 : 0);
        }
        return 1;
    }

    private static int docOrientationTuTiff(byte[] d, int tiffStart, int tiffLen) {
        if (tiffLen < 8 || tiffStart < 0 || tiffStart + tiffLen > d.length) {
            return 1;
        }
        boolean littleEndian;
        if (d[tiffStart] == 'I' && d[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (d[tiffStart] == 'M' && d[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        if (docU16(d, tiffStart + 2, littleEndian) != 0x002A) {
            return 1;
        }
        long ifdOffset = docU32(d, tiffStart + 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset + 2 > tiffLen) {
            return 1;
        }
        int ifdPos = tiffStart + (int) ifdOffset;
        int entryCount = docU16(d, ifdPos, littleEndian);
        for (int i = 0; i < entryCount; i++) {
            int entryPos = ifdPos + 2 + i * 12;
            if (entryPos + 12 > tiffStart + tiffLen) {
                break;
            }
            int tag = docU16(d, entryPos, littleEndian);
            if (tag == 0x0112) {
                int type = docU16(d, entryPos + 2, littleEndian);
                long count = docU32(d, entryPos + 4, littleEndian);
                if (type == 3 && count == 1) {
                    int val = docU16(d, entryPos + 8, littleEndian);
                    return (val >= 1 && val <= 8) ? val : 1;
                }
            }
        }
        return 1;
    }

    private static int docU16(byte[] d, int viTri, boolean littleEndian) {
        if (littleEndian) {
            return (d[viTri] & 0xFF) | ((d[viTri + 1] & 0xFF) << 8);
        }
        return ((d[viTri] & 0xFF) << 8) | (d[viTri + 1] & 0xFF);
    }

    private static long docU32(byte[] d, int viTri, boolean littleEndian) {
        if (littleEndian) {
            return docUnsigned32LittleEndian(d, viTri);
        }
        return docUnsigned32BigEndian(d, viTri);
    }

    private static boolean laPngHopLe(byte[] d) {
        if (d.length < CHU_KY_PNG.length) {
            return false;
        }
        for (int i = 0; i < CHU_KY_PNG.length; i++) {
            if (d[i] != CHU_KY_PNG[i]) {
                return false;
            }
        }
        int viTri = CHU_KY_PNG.length;
        boolean daCoIhdr = false;
        boolean daCoIdat = false;
        while (viTri + 12 <= d.length) {
            long doDai = docUnsigned32BigEndian(d, viTri);
            if (doDai > d.length - viTri - 12L) {
                return false;
            }
            int doDaiInt = (int) doDai;
            String loai = new String(d, viTri + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            CRC32 crc = new CRC32();
            crc.update(d, viTri + 4, 4 + doDaiInt);
            if (crc.getValue() != docUnsigned32BigEndian(d, viTri + 8 + doDaiInt)) {
                return false;
            }
            if (!daCoIhdr) {
                if (!"IHDR".equals(loai) || doDaiInt != 13) {
                    return false;
                }
                if (docUnsigned32BigEndian(d, viTri + 8) == 0 || docUnsigned32BigEndian(d, viTri + 12) == 0) {
                    return false;
                }
                daCoIhdr = true;
            } else if ("IDAT".equals(loai)) {
                daCoIdat = true;
            } else if ("IEND".equals(loai)) {
                return daCoIdat && doDaiInt == 0;
            }
            viTri += 12 + doDaiInt;
        }
        return false;
    }

    private static boolean laJpegHopLe(byte[] d) {
        if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8 || (d[2] & 0xFF) != 0xFF) {
            return false;
        }
        int viTri = 2;
        boolean daCoSof = false;
        boolean daCoSos = false;
        while (viTri < d.length) {
            if ((d[viTri] & 0xFF) != 0xFF) {
                return false;
            }
            while (viTri < d.length && (d[viTri] & 0xFF) == 0xFF) {
                viTri++;
            }
            if (viTri >= d.length) {
                return false;
            }
            int dauHieu = d[viTri++] & 0xFF;
            if (dauHieu == 0xD9) {
                return daCoSof && daCoSos;
            }
            if (dauHieu == 0x00 || dauHieu == 0xD8) {
                return false;
            }
            if (dauHieu == 0x01 || (dauHieu >= 0xD0 && dauHieu <= 0xD7)) {
                continue;
            }
            if (viTri + 2 > d.length) {
                return false;
            }
            int doDaiDoan = ((d[viTri] & 0xFF) << 8) | (d[viTri + 1] & 0xFF);
            if (doDaiDoan < 2 || viTri + doDaiDoan > d.length) {
                return false;
            }
            if (laDauHieuSof(dauHieu)) {
                if (doDaiDoan < 8) {
                    return false;
                }
                int cao = ((d[viTri + 3] & 0xFF) << 8) | (d[viTri + 4] & 0xFF);
                int rong = ((d[viTri + 5] & 0xFF) << 8) | (d[viTri + 6] & 0xFF);
                if (cao == 0 || rong == 0) {
                    return false;
                }
                daCoSof = true;
            }
            viTri += doDaiDoan;
            if (dauHieu == 0xDA) {
                daCoSos = true;
                while (viTri < d.length) {
                    if ((d[viTri] & 0xFF) != 0xFF) {
                        viTri++;
                        continue;
                    }
                    if (viTri + 1 >= d.length) {
                        return false;
                    }
                    int ke = d[viTri + 1] & 0xFF;
                    if (ke == 0x00 || (ke >= 0xD0 && ke <= 0xD7)) {
                        viTri += 2;
                    } else if (ke == 0xFF) {
                        viTri++;
                    } else {
                        break;
                    }
                }
                if (viTri >= d.length) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean laDauHieuSof(int dauHieu) {
        return dauHieu >= 0xC0 && dauHieu <= 0xCF
                && dauHieu != 0xC4 && dauHieu != 0xC8 && dauHieu != 0xCC;
    }

    private static boolean laWebpHopLe(byte[] d) {
        if (d.length < 20 || !khop(d, 0, "RIFF") || !khop(d, 8, "WEBP")) {
            return false;
        }
        long kichThuocRiff = docUnsigned32LittleEndian(d, 4);
        if (kichThuocRiff + 8 > d.length || kichThuocRiff < 12) {
            return false;
        }
        long doDaiKhoi = docUnsigned32LittleEndian(d, 16);
        if (20 + doDaiKhoi > kichThuocRiff + 8) {
            return false;
        }
        if (khop(d, 12, "VP8 ")) {
            return doDaiKhoi >= 10
                    && (d[23] & 0xFF) == 0x9D && (d[24] & 0xFF) == 0x01 && (d[25] & 0xFF) == 0x2A;
        }
        if (khop(d, 12, "VP8L")) {
            return doDaiKhoi >= 5 && (d[20] & 0xFF) == 0x2F;
        }
        if (khop(d, 12, "VP8X")) {
            if (doDaiKhoi != 10 || d.length < 30) {
                return false;
            }
            int canvasWidth = 1 + ((d[24] & 0xFF) | ((d[25] & 0xFF) << 8) | ((d[26] & 0xFF) << 16));
            int canvasHeight = 1 + ((d[27] & 0xFF) | ((d[28] & 0xFF) << 8) | ((d[29] & 0xFF) << 16));
            if (canvasWidth <= 0 || canvasHeight <= 0 || canvasWidth > MAX_CHIEU_RONG || canvasHeight > MAX_CHIEU_CAO) {
                return false;
            }
            int viTri = 30;
            boolean coChunkAnh = false;
            while (viTri + 8 <= d.length) {
                long chunkLen = docUnsigned32LittleEndian(d, viTri + 4);
                if (viTri + 8 + chunkLen > d.length) {
                    return false;
                }
                if (khop(d, viTri, "VP8 ") || khop(d, viTri, "VP8L") || khop(d, viTri, "ANMF")) {
                    coChunkAnh = true;
                }
                viTri += 8 + (int) chunkLen + ((chunkLen % 2 != 0) ? 1 : 0);
            }
            return coChunkAnh;
        }
        return false;
    }

    private static boolean khop(byte[] d, int viTri, String chuoi) {
        for (int i = 0; i < chuoi.length(); i++) {
            if (d[viTri + i] != (byte) chuoi.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static long docUnsigned32BigEndian(byte[] d, int viTri) {
        return ((long) (d[viTri] & 0xFF) << 24)
                | ((long) (d[viTri + 1] & 0xFF) << 16)
                | ((long) (d[viTri + 2] & 0xFF) << 8)
                | (long) (d[viTri + 3] & 0xFF);
    }

    private static long docUnsigned32LittleEndian(byte[] d, int viTri) {
        return ((long) (d[viTri + 3] & 0xFF) << 24)
                | ((long) (d[viTri + 2] & 0xFF) << 16)
                | ((long) (d[viTri + 1] & 0xFF) << 8)
                | (long) (d[viTri] & 0xFF);
    }
}
