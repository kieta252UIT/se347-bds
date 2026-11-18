package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.listing.domain.DonViGiaTin;
import com.realestates.backend.listing.domain.KiemTraNoiDungTin;
import com.realestates.backend.listing.domain.LoaiBds;
import com.realestates.backend.listing.domain.LoaiGiaoDich;
import com.realestates.backend.listing.domain.LoiTruongNoiDung;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KiemTraNoiDungTinUnitTest {

    private static final List<String> CAC_TRUONG_BAT_BUOC = List.of(
            "tieuDe", "moTa", "loaiGiaoDich", "loaiBds", "giaGoc", "donViGia",
            "dienTichM2", "diaChi", "khuVuc", "viDo", "kinhDo"
    );

    private static Map<String, Object> duLieuHopLe() {
        Map<String, Object> duLieu = new HashMap<>();
        duLieu.put("tieuDe", "Căn hộ 2 phòng ngủ gần trung tâm");
        duLieu.put("moTa", "Căn hộ đầy đủ nội thất, view thoáng.");
        duLieu.put("loaiGiaoDich", "ban");
        duLieu.put("loaiBds", "can_ho");
        duLieu.put("giaGoc", "2500000000");
        duLieu.put("donViGia", "vnd_tong");
        duLieu.put("dienTichM2", "65.5");
        duLieu.put("diaChi", "12 Nguyễn Huệ, Quận 1");
        duLieu.put("khuVuc", "TP. Hồ Chí Minh");
        duLieu.put("viDo", "10.7769");
        duLieu.put("kinhDo", "106.7009");
        duLieu.put("anhIds", List.of("0b7a2f1e-4c3d-4b5a-9d6e-1a2b3c4d5e6f"));
        return duLieu;
    }

    private static List<String> cacTruongLoi(KiemTraNoiDungTin.KetQua ketQua) {
        return ketQua.loi().stream().map(LoiTruongNoiDung::truong).toList();
    }

    @Test
    @DisplayName("Dữ liệu bán hợp lệ trả về nội dung đã chuyển kiểu")
    void duLieuBanHopLe() {
        KiemTraNoiDungTin.KetQua ketQua = KiemTraNoiDungTin.kiemTra(duLieuHopLe());

        assertThat(ketQua.hopLe()).isTrue();
        assertThat(ketQua.noiDung().loaiGiaoDich()).isEqualTo(LoaiGiaoDich.BAN);
        assertThat(ketQua.noiDung().loaiBds()).isEqualTo(LoaiBds.CAN_HO);
        assertThat(ketQua.noiDung().giaGoc()).isEqualTo(new BigDecimal("2500000000"));
        assertThat(ketQua.noiDung().donViGia()).isEqualTo(DonViGiaTin.VND_TONG);
        assertThat(ketQua.noiDung().dienTichM2()).isEqualTo(new BigDecimal("65.5"));
    }

    @Test
    @DisplayName("Thiếu, null, rỗng hoặc toàn khoảng trắng ở từng trường bắt buộc đều bị báo đúng trường")
    void truongBatBuocThieuNullRongHoacKhoangTrang() {
        for (String truong : CAC_TRUONG_BAT_BUOC) {
            Map<String, Object> thieu = duLieuHopLe();
            thieu.remove(truong);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(thieu))).containsExactly(truong);

            Map<String, Object> giaTriNull = duLieuHopLe();
            giaTriNull.put(truong, null);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(giaTriNull))).containsExactly(truong);

            Map<String, Object> rong = duLieuHopLe();
            rong.put(truong, "");
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(rong))).containsExactly(truong);

            Map<String, Object> khoangTrang = duLieuHopLe();
            khoangTrang.put(truong, "   ");
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(khoangTrang))).containsExactly(truong);
        }
    }

    @Test
    @DisplayName("Dữ liệu null bị báo thiếu đủ 11 trường bắt buộc")
    void duLieuNullBaoThieuTatCaTruongBatBuoc() {
        KiemTraNoiDungTin.KetQua ketQua = KiemTraNoiDungTin.kiemTra(null);

        assertThat(ketQua.hopLe()).isFalse();
        assertThat(cacTruongLoi(ketQua)).containsExactlyElementsOf(CAC_TRUONG_BAT_BUOC);
    }

    @Test
    @DisplayName("Trường bắt buộc không phải chuỗi bị từ chối")
    void truongBatBuocKhongPhaiChuoi() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("tieuDe", 123);

        assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("tieuDe");
    }

    @Test
    @DisplayName("Giá gốc không dương, sai định dạng hoặc không phải chuỗi bị từ chối")
    void giaGocKhongHopLe() {
        List<Object> cacGiaTriSai = List.of("0", "0.00", "-1", "abc", "1e5", "1,5", "NaN", "Infinity", "01", " 5", "5 ", ".5", "5.", 2500000000L);
        for (Object giaTriSai : cacGiaTriSai) {
            Map<String, Object> duLieu = duLieuHopLe();
            duLieu.put("giaGoc", giaTriSai);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("giaGoc");
        }
    }

    @Test
    @DisplayName("Giá gốc và diện tích dương hợp lệ được chấp nhận kể cả số thập phân nhỏ")
    void giaVaDienTichDuongHopLe() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("giaGoc", "0.5");
        duLieu.put("dienTichM2", "0.01");

        KiemTraNoiDungTin.KetQua ketQua = KiemTraNoiDungTin.kiemTra(duLieu);

        assertThat(ketQua.hopLe()).isTrue();
        assertThat(ketQua.noiDung().giaGoc()).isEqualTo(new BigDecimal("0.5"));
        assertThat(ketQua.noiDung().dienTichM2()).isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("Diện tích không dương hoặc sai định dạng bị từ chối")
    void dienTichKhongHopLe() {
        List<Object> cacGiaTriSai = List.of("0", "-65", "abc", "6,5", "1e2", 65);
        for (Object giaTriSai : cacGiaTriSai) {
            Map<String, Object> duLieu = duLieuHopLe();
            duLieu.put("dienTichM2", giaTriSai);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("dienTichM2");
        }
    }

    @Test
    @DisplayName("Đơn vị giá phải đúng với loại giao dịch bán hoặc cho thuê")
    void donViGiaPhaiDungVoiLoaiGiaoDich() {
        for (String donVi : List.of("vnd_tong", "vnd_m2")) {
            Map<String, Object> ban = duLieuHopLe();
            ban.put("loaiGiaoDich", "ban");
            ban.put("donViGia", donVi);
            assertThat(KiemTraNoiDungTin.kiemTra(ban).hopLe()).isTrue();

            Map<String, Object> choThue = duLieuHopLe();
            choThue.put("loaiGiaoDich", "cho_thue");
            choThue.put("donViGia", donVi);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(choThue))).containsExactly("donViGia");
        }
        for (String donVi : List.of("vnd_ngay", "vnd_thang", "vnd_nam", "vnd_m2_ngay", "vnd_m2_thang")) {
            Map<String, Object> choThue = duLieuHopLe();
            choThue.put("loaiGiaoDich", "cho_thue");
            choThue.put("donViGia", donVi);
            assertThat(KiemTraNoiDungTin.kiemTra(choThue).hopLe()).isTrue();

            Map<String, Object> ban = duLieuHopLe();
            ban.put("loaiGiaoDich", "ban");
            ban.put("donViGia", donVi);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(ban))).containsExactly("donViGia");
        }
    }

    @Test
    @DisplayName("Đơn vị giá ngoài danh sách bị từ chối")
    void donViGiaNgoaiDanhSach() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("donViGia", "usd");

        assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("donViGia");
    }

    @Test
    @DisplayName("Loại giao dịch sai thì chỉ báo loại giao dịch, không báo thêm đơn vị giá")
    void loaiGiaoDichSaiKhongBaoThemDonViGia() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("loaiGiaoDich", "tang");

        assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("loaiGiaoDich");
    }

    @Test
    @DisplayName("Loại BĐS ngoài danh sách bị từ chối")
    void loaiBdsNgoaiDanhSach() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("loaiBds", "biet_thu");

        assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("loaiBds");
    }

    @Test
    @DisplayName("Trường lạ và trường tự khai quyền hoặc chủ sở hữu bị từ chối")
    void truongLaVaTruongTuKhaiBiTuChoi() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("chuId", UUID.randomUUID().toString());
        duLieu.put("vaiTro", "quan_tri_vien");
        duLieu.put("truongLa", "x");

        assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu)))
                .containsExactlyInAnyOrder("chuId", "vaiTro", "truongLa");
    }

    @Test
    @DisplayName("Nhiều lỗi cùng lúc được báo đủ theo thứ tự trường")
    void nhieuLoiCungLuc() {
        Map<String, Object> duLieu = duLieuHopLe();
        duLieu.put("tieuDe", " ");
        duLieu.put("giaGoc", "-1");
        duLieu.put("dienTichM2", "0");

        assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu)))
                .containsExactly("tieuDe", "giaGoc", "dienTichM2");
    }

    @Test
    @DisplayName("Vĩ độ trong [-90, 90] hợp lệ; ngoài miền hoặc sai định dạng bị từ chối")
    void viDoHopLeVaKhongHopLe() {
        for (String viDoDung : List.of("-90", "90", "0", "-0", "10.7769", "-12.345")) {
            Map<String, Object> hopLe = duLieuHopLe();
            hopLe.put("viDo", viDoDung);
            assertThat(KiemTraNoiDungTin.kiemTra(hopLe).hopLe()).isTrue();
        }

        List<Object> cacGiaTriSai = List.of("-90.0001", "90.0001", "91", "-91", "abc", "NaN", "Infinity", "1e2", "10,77", 10.5);
        for (Object giaTriSai : cacGiaTriSai) {
            Map<String, Object> duLieu = duLieuHopLe();
            duLieu.put("viDo", giaTriSai);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("viDo");
        }
    }

    @Test
    @DisplayName("Kinh độ trong [-180, 180] hợp lệ; ngoài miền hoặc sai định dạng bị từ chối")
    void kinhDoHopLeVaKhongHopLe() {
        for (String kinhDoDung : List.of("-180", "180", "0", "-0", "106.7009", "-120.5")) {
            Map<String, Object> hopLe = duLieuHopLe();
            hopLe.put("kinhDo", kinhDoDung);
            assertThat(KiemTraNoiDungTin.kiemTra(hopLe).hopLe()).isTrue();
        }

        List<Object> cacGiaTriSai = List.of("-180.0001", "180.0001", "181", "-181", "abc", "NaN", "Infinity", "1e2", "106,7", 106.7);
        for (Object giaTriSai : cacGiaTriSai) {
            Map<String, Object> duLieu = duLieuHopLe();
            duLieu.put("kinhDo", giaTriSai);
            assertThat(cacTruongLoi(KiemTraNoiDungTin.kiemTra(duLieu))).containsExactly("kinhDo");
        }
    }
}

