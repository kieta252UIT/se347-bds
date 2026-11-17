package com.realestates.backend.listing.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class KiemTraNoiDungTin {

    public static final String TRUONG_TIEU_DE = "tieuDe";
    public static final String TRUONG_MO_TA = "moTa";
    public static final String TRUONG_LOAI_GIAO_DICH = "loaiGiaoDich";
    public static final String TRUONG_LOAI_BDS = "loaiBds";
    public static final String TRUONG_GIA_GOC = "giaGoc";
    public static final String TRUONG_DON_VI_GIA = "donViGia";
    public static final String TRUONG_DIEN_TICH = "dienTichM2";
    public static final String TRUONG_DIA_CHI = "diaChi";
    public static final String TRUONG_KHU_VUC = "khuVuc";
    public static final String TRUONG_VI_DO = "viDo";
    public static final String TRUONG_KINH_DO = "kinhDo";
    public static final String TRUONG_ANH_IDS = "anhIds";

    private static final Set<String> CAC_TRUONG_CHO_PHEP = Set.of(
            TRUONG_TIEU_DE,
            TRUONG_MO_TA,
            TRUONG_LOAI_GIAO_DICH,
            TRUONG_LOAI_BDS,
            TRUONG_GIA_GOC,
            TRUONG_DON_VI_GIA,
            TRUONG_DIEN_TICH,
            TRUONG_DIA_CHI,
            TRUONG_KHU_VUC,
            TRUONG_VI_DO,
            TRUONG_KINH_DO,
            TRUONG_ANH_IDS
    );

    private static final Pattern SO_THAP_PHAN = Pattern.compile("(0|[1-9][0-9]*)(\\.[0-9]+)?");
    private static final Pattern SO_THAP_PHAN_CO_DAU = Pattern.compile("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$");
    private static final BigDecimal VI_DO_TOI_THIEU = new BigDecimal("-90");
    private static final BigDecimal VI_DO_TOI_DA = new BigDecimal("90");
    private static final BigDecimal KINH_DO_TOI_THIEU = new BigDecimal("-180");
    private static final BigDecimal KINH_DO_TOI_DA = new BigDecimal("180");

    public record KetQua(NoiDungTinDauVao noiDung, List<LoiTruongNoiDung> loi) {

        public boolean hopLe() {
            return loi.isEmpty();
        }
    }

    private KiemTraNoiDungTin() {
    }

    public static KetQua kiemTra(Map<String, Object> duLieu) {
        Map<String, Object> du = duLieu == null ? Map.of() : duLieu;
        List<LoiTruongNoiDung> loi = new ArrayList<>();

        for (String khoa : du.keySet()) {
            if (khoa == null || !CAC_TRUONG_CHO_PHEP.contains(khoa)) {
                loi.add(new LoiTruongNoiDung(String.valueOf(khoa), "Trường không được phép trong dữ liệu tin."));
            }
        }

        String tieuDe = docChuoiBatBuoc(du, TRUONG_TIEU_DE, loi);
        String moTa = docChuoiBatBuoc(du, TRUONG_MO_TA, loi);
        LoaiGiaoDich loaiGiaoDich = docLoaiGiaoDich(du, loi);
        LoaiBds loaiBds = docLoaiBds(du, loi);
        BigDecimal giaGoc = docSoDuong(du, TRUONG_GIA_GOC, "Giá gốc", loi);
        DonViGiaTin donViGia = docDonViGia(du, loaiGiaoDich, loi);
        BigDecimal dienTich = docSoDuong(du, TRUONG_DIEN_TICH, "Diện tích", loi);
        String diaChi = docChuoiBatBuoc(du, TRUONG_DIA_CHI, loi);
        String khuVuc = docChuoiBatBuoc(du, TRUONG_KHU_VUC, loi);
        String viDo = docToaDo(du, TRUONG_VI_DO, "Vĩ độ", VI_DO_TOI_THIEU, VI_DO_TOI_DA, loi);
        String kinhDo = docToaDo(du, TRUONG_KINH_DO, "Kinh độ", KINH_DO_TOI_THIEU, KINH_DO_TOI_DA, loi);

        if (!loi.isEmpty()) {
            return new KetQua(null, List.copyOf(loi));
        }
        return new KetQua(
                new NoiDungTinDauVao(
                        tieuDe, moTa, loaiGiaoDich, loaiBds, giaGoc, donViGia, dienTich, diaChi, khuVuc, viDo, kinhDo
                ),
                List.of()
        );
    }

    private static String docChuoiBatBuoc(Map<String, Object> du, String truong, List<LoiTruongNoiDung> loi) {
        Object giaTri = du.get(truong);
        if (giaTri == null) {
            loi.add(new LoiTruongNoiDung(truong, "Trường bắt buộc, không được thiếu hoặc null."));
            return null;
        }
        if (!(giaTri instanceof String chuoi)) {
            loi.add(new LoiTruongNoiDung(truong, "Trường phải là chuỗi."));
            return null;
        }
        if (chuoi.isBlank()) {
            loi.add(new LoiTruongNoiDung(truong, "Trường không được để trống hoặc chỉ gồm khoảng trắng."));
            return null;
        }
        return chuoi;
    }

    private static LoaiGiaoDich docLoaiGiaoDich(Map<String, Object> du, List<LoiTruongNoiDung> loi) {
        String chuoi = docChuoiBatBuoc(du, TRUONG_LOAI_GIAO_DICH, loi);
        if (chuoi == null) {
            return null;
        }
        Optional<LoaiGiaoDich> loai = LoaiGiaoDich.tuGiaTri(chuoi);
        if (loai.isEmpty()) {
            loi.add(new LoiTruongNoiDung(TRUONG_LOAI_GIAO_DICH, "Loại giao dịch chỉ nhận: ban, cho_thue."));
            return null;
        }
        return loai.get();
    }

    private static LoaiBds docLoaiBds(Map<String, Object> du, List<LoiTruongNoiDung> loi) {
        String chuoi = docChuoiBatBuoc(du, TRUONG_LOAI_BDS, loi);
        if (chuoi == null) {
            return null;
        }
        Optional<LoaiBds> loai = LoaiBds.tuGiaTri(chuoi);
        if (loai.isEmpty()) {
            loi.add(new LoiTruongNoiDung(TRUONG_LOAI_BDS, "Loại BĐS chỉ nhận: can_ho, nha_rieng, dat, phong_tro, khac."));
            return null;
        }
        return loai.get();
    }

    private static BigDecimal docSoDuong(
            Map<String, Object> du,
            String truong,
            String ten,
            List<LoiTruongNoiDung> loi
    ) {
        String chuoi = docChuoiBatBuoc(du, truong, loi);
        if (chuoi == null) {
            return null;
        }
        if (!SO_THAP_PHAN.matcher(chuoi).matches()) {
            loi.add(new LoiTruongNoiDung(
                    truong,
                    ten + " phải là số thập phân hữu hạn dạng chuỗi, dùng dấu chấm, không dấu âm, không số mũ."
            ));
            return null;
        }
        BigDecimal giaTri = new BigDecimal(chuoi);
        if (giaTri.signum() <= 0) {
            loi.add(new LoiTruongNoiDung(truong, ten + " phải lớn hơn 0."));
            return null;
        }
        return giaTri;
    }

    private static DonViGiaTin docDonViGia(
            Map<String, Object> du,
            LoaiGiaoDich loaiGiaoDich,
            List<LoiTruongNoiDung> loi
    ) {
        String chuoi = docChuoiBatBuoc(du, TRUONG_DON_VI_GIA, loi);
        if (chuoi == null) {
            return null;
        }
        Optional<DonViGiaTin> donVi = DonViGiaTin.tuGiaTri(chuoi);
        if (donVi.isEmpty()) {
            loi.add(new LoiTruongNoiDung(
                    TRUONG_DON_VI_GIA,
                    "Đơn vị giá chỉ nhận: vnd_tong, vnd_m2, vnd_ngay, vnd_thang, vnd_nam, vnd_m2_ngay, vnd_m2_thang."
            ));
            return null;
        }
        if (loaiGiaoDich != null && !donVi.get().phuHopVoi(loaiGiaoDich)) {
            loi.add(new LoiTruongNoiDung(TRUONG_DON_VI_GIA, "Đơn vị giá không phù hợp với loại giao dịch."));
            return null;
        }
        return donVi.get();
    }

    private static String docToaDo(
            Map<String, Object> du,
            String truong,
            String ten,
            BigDecimal toiThieu,
            BigDecimal toiDa,
            List<LoiTruongNoiDung> loi
    ) {
        String chuoi = docChuoiBatBuoc(du, truong, loi);
        if (chuoi == null) {
            return null;
        }
        if (!SO_THAP_PHAN_CO_DAU.matcher(chuoi).matches()) {
            loi.add(new LoiTruongNoiDung(
                    truong,
                    ten + " phải là số thập phân hữu hạn dạng chuỗi, dùng dấu chấm, không số mũ."
            ));
            return null;
        }
        BigDecimal giaTri = new BigDecimal(chuoi);
        if (giaTri.compareTo(toiThieu) < 0 || giaTri.compareTo(toiDa) > 0) {
            loi.add(new LoiTruongNoiDung(
                    truong,
                    ten + " phải nằm trong đoạn [" + toiThieu.toPlainString() + ", " + toiDa.toPlainString() + "]."
            ));
            return null;
        }
        return chuoi;
    }
}

