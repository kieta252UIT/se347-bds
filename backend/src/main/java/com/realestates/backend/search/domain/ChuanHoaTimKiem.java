package com.realestates.backend.search.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chuẩn hóa tiếng Việt không dấu, chữ thường cho tìm kiếm. Java và SQL dùng CÙNG một bảng ký tự
 * ({@link #NGUON}/{@link #DICH}) để từ khóa và dữ liệu luôn chuẩn hóa giống nhau, không phụ thuộc locale
 * của PostgreSQL hay extension unaccent.
 */
public final class ChuanHoaTimKiem {

    /** Ký tự có dấu/hoa (Latin) và chữ cái ASCII thường tương ứng; cùng độ dài, dùng cho SQL translate(). */
    static final String NGUON;
    static final String DICH;

    static {
        StringBuilder nguon = new StringBuilder();
        StringBuilder dich = new StringBuilder();
        for (char c = 'A'; c <= 'Z'; c++) {
            nguon.append(c);
            dich.append(Character.toLowerCase(c));
        }
        // Latin-1 Supplement, Latin Extended-A/B và Latin Extended Additional (đủ toàn bộ chữ tiếng Việt).
        int[][] khoang = {{0x00C0, 0x024F}, {0x1E00, 0x1EFF}};
        for (int[] k : khoang) {
            for (int cp = k[0]; cp <= k[1]; cp++) {
                char c = (char) cp;
                char goc = chuCaiGoc(c);
                if (goc != 0) {
                    nguon.append(c);
                    dich.append(goc);
                }
            }
        }
        NGUON = nguon.toString();
        DICH = dich.toString();
    }

    private ChuanHoaTimKiem() {
    }

    /** NFC rồi thay từng ký tự theo bảng; ký tự ngoài bảng giữ nguyên (giống SQL translate). */
    public static String chuanHoa(String vanBan) {
        String nfc = Normalizer.normalize(vanBan, Normalizer.Form.NFC);
        StringBuilder ketQua = new StringBuilder(nfc.length());
        for (int i = 0; i < nfc.length(); i++) {
            char c = nfc.charAt(i);
            int viTri = NGUON.indexOf(c);
            ketQua.append(viTri >= 0 ? DICH.charAt(viTri) : c);
        }
        return ketQua.toString();
    }

    /** Khu vực: chuẩn hóa rồi mỗi dãy ký tự ngoài [a-z0-9] thành một khoảng trắng, trim (khớp {@link #sqlKhuVuc}). */
    public static String chuanHoaKhuVuc(String vanBan) {
        return chuanHoa(vanBan).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /** Bản SQL của {@link #chuanHoaKhuVuc}; bieuThuc do mã cung cấp. */
    public static String sqlKhuVuc(String bieuThuc) {
        return "btrim(regexp_replace(" + sql(bieuThuc) + ", '[^a-z0-9]+', ' ', 'g'))";
    }

    /** Các từ khác nhau (chữ/số liên tiếp) của văn bản đã chuẩn hóa, giữ thứ tự xuất hiện. */
    public static List<String> tachTu(String daChuanHoa) {
        Set<String> tu = new LinkedHashSet<>();
        for (String manh : daChuanHoa.split("[^\\p{L}\\p{N}]+")) {
            if (!manh.isEmpty()) {
                tu.add(manh);
            }
        }
        return new ArrayList<>(tu);
    }

    /** Biểu thức SQL chuẩn hóa một biểu thức text (cột/hằng do mã cung cấp, không phải dữ liệu người dùng). */
    public static String sql(String bieuThuc) {
        return "translate(normalize(" + bieuThuc + ", NFC), '" + NGUON + "', '" + DICH + "')";
    }

    /** Chữ cái ASCII thường gốc của c nếu c là chữ Latin có dấu hoặc hoa; 0 nếu không thuộc bảng. */
    private static char chuCaiGoc(char c) {
        if (c == 'đ' || c == 'Đ') {
            return 'd';
        }
        String boDau = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        if (boDau.length() != 1) {
            return 0;
        }
        char goc = Character.toLowerCase(boDau.charAt(0));
        return goc >= 'a' && goc <= 'z' && goc != c ? goc : 0;
    }
}
