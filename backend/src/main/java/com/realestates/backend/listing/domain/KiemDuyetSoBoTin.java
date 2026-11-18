package com.realestates.backend.listing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class KiemDuyetSoBoTin {

    public static final String KET_QUA_DAT = "dat";

    private static final BigDecimal VI_DO_TOI_THIEU = new BigDecimal("-90");
    private static final BigDecimal VI_DO_TOI_DA = new BigDecimal("90");
    private static final BigDecimal KINH_DO_TOI_THIEU = new BigDecimal("-180");
    private static final BigDecimal KINH_DO_TOI_DA = new BigDecimal("180");

    public record KetQua(boolean dat, String ketQuaSoBo) {
    }

    private KiemDuyetSoBoTin() {
    }

    public static KetQua danhGia(NoiDungTinDauVao noiDung, List<UUID> anhIds) {
        if (noiDung == null) {
            return new KetQua(false, "khong_dat: thieu_noi_dung");
        }
        if (noiDung.tieuDe() == null || noiDung.tieuDe().isBlank()
                || noiDung.moTa() == null || noiDung.moTa().isBlank()
                || noiDung.diaChi() == null || noiDung.diaChi().isBlank()
                || noiDung.khuVuc() == null || noiDung.khuVuc().isBlank()) {
            return new KetQua(false, "khong_dat: thieu_truong_van_ban");
        }
        if (noiDung.loaiGiaoDich() == null || noiDung.loaiBds() == null || noiDung.donViGia() == null
                || !noiDung.donViGia().phuHopVoi(noiDung.loaiGiaoDich())) {
            return new KetQua(false, "khong_dat: loai_hoac_don_vi_gia_khong_hop_le");
        }
        if (noiDung.giaGoc() == null || noiDung.giaGoc().signum() <= 0
                || noiDung.dienTichM2() == null || noiDung.dienTichM2().signum() <= 0) {
            return new KetQua(false, "khong_dat: gia_hoac_dien_tich_khong_duong");
        }
        if (noiDung.viDo() == null || noiDung.kinhDo() == null) {
            return new KetQua(false, "khong_dat: thieu_toa_do");
        }
        try {
            BigDecimal viDo = new BigDecimal(noiDung.viDo());
            BigDecimal kinhDo = new BigDecimal(noiDung.kinhDo());
            if (viDo.compareTo(VI_DO_TOI_THIEU) < 0 || viDo.compareTo(VI_DO_TOI_DA) > 0
                    || kinhDo.compareTo(KINH_DO_TOI_THIEU) < 0 || kinhDo.compareTo(KINH_DO_TOI_DA) > 0) {
                return new KetQua(false, "khong_dat: toa_do_ngoai_mien");
            }
        } catch (NumberFormatException ex) {
            return new KetQua(false, "khong_dat: toa_do_khong_hop_le");
        }
        if (anhIds == null || anhIds.size() < KiemTraSoLuongAnh.SO_ANH_TOI_THIEU
                || anhIds.size() > KiemTraSoLuongAnh.SO_ANH_TOI_DA) {
            return new KetQua(false, "khong_dat: so_luong_anh_khong_hop_le");
        }
        return new KetQua(true, KET_QUA_DAT);
    }
}
