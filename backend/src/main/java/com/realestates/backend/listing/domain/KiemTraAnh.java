package com.realestates.backend.listing.domain;

import java.util.Optional;
import java.util.zip.CRC32;

public final class KiemTraAnh {

    public static final long KICH_THUOC_TOI_DA_BYTE = 10_000_000L;

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
            return doDaiKhoi >= 10;
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

