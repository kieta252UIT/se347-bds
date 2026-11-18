package com.realestates.backend.listing.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class KiemTraSoLuongAnh {

    public static final int SO_ANH_TOI_THIEU = 1;
    public static final int SO_ANH_TOI_DA = 12;
    public static final String TRUONG = "anhIds";

    private static final Pattern UUID_CHUAN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    public record KetQua(List<UUID> anhIds, LoiTruongNoiDung loi) {

        public boolean hopLe() {
            return loi == null;
        }
    }

    private KiemTraSoLuongAnh() {
    }

    public static KetQua kiemTra(Object anhIds) {
        if (anhIds == null) {
            return loi("Trường bắt buộc, không được thiếu hoặc null.");
        }
        if (!(anhIds instanceof List<?> danhSach)) {
            return loi("Trường phải là mảng mã ảnh.");
        }
        if (danhSach.size() < SO_ANH_TOI_THIEU || danhSach.size() > SO_ANH_TOI_DA) {
            return loi("Số ảnh phải từ " + SO_ANH_TOI_THIEU + " đến " + SO_ANH_TOI_DA + ".");
        }
        Set<UUID> khongTrung = new LinkedHashSet<>();
        for (Object phanTu : danhSach) {
            if (!(phanTu instanceof String chuoi) || !UUID_CHUAN.matcher(chuoi).matches()) {
                return loi("Mỗi phần tử phải là mã ảnh dạng UUID.");
            }
            if (!khongTrung.add(UUID.fromString(chuoi))) {
                return loi("Không được lặp lại cùng một ảnh.");
            }
        }
        return new KetQua(List.copyOf(khongTrung), null);
    }

    private static KetQua loi(String thongDiep) {
        return new KetQua(null, new LoiTruongNoiDung(TRUONG, thongDiep));
    }
}

