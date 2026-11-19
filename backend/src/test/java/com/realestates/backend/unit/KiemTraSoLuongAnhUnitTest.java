package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.listing.domain.KiemTraSoLuongAnh;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KiemTraSoLuongAnhUnitTest {

    private static List<Object> taoDanhSach(int soLuong) {
        List<Object> danhSach = new ArrayList<>();
        for (int i = 0; i < soLuong; i++) {
            danhSach.add(UUID.randomUUID().toString());
        }
        return danhSach;
    }

    @Test
    @DisplayName("1 và 12 ảnh hợp lệ")
    void bienHopLe() {
        assertThat(KiemTraSoLuongAnh.kiemTra(taoDanhSach(1)).hopLe()).isTrue();

        KiemTraSoLuongAnh.KetQua muoiHai = KiemTraSoLuongAnh.kiemTra(taoDanhSach(12));
        assertThat(muoiHai.hopLe()).isTrue();
        assertThat(muoiHai.anhIds()).hasSize(12);
    }

    @Test
    @DisplayName("0 ảnh và 13 ảnh bị từ chối")
    void bienKhongHopLe() {
        assertThat(KiemTraSoLuongAnh.kiemTra(taoDanhSach(0)).hopLe()).isFalse();
        assertThat(KiemTraSoLuongAnh.kiemTra(taoDanhSach(13)).hopLe()).isFalse();
        assertThat(KiemTraSoLuongAnh.kiemTra(taoDanhSach(13)).loi().truong()).isEqualTo("anhIds");
    }

    @Test
    @DisplayName("Thiếu, null hoặc không phải mảng bị từ chối")
    void khongPhaiMang() {
        assertThat(KiemTraSoLuongAnh.kiemTra(null).hopLe()).isFalse();
        assertThat(KiemTraSoLuongAnh.kiemTra("0b7a2f1e-4c3d-4b5a-9d6e-1a2b3c4d5e6f").hopLe()).isFalse();
    }

    @Test
    @DisplayName("Phần tử không phải UUID chuẩn bị từ chối")
    void phanTuKhongPhaiUuid() {
        assertThat(KiemTraSoLuongAnh.kiemTra(List.of("abc")).hopLe()).isFalse();
        assertThat(KiemTraSoLuongAnh.kiemTra(List.of("1-1-1-1-1")).hopLe()).isFalse();
        assertThat(KiemTraSoLuongAnh.kiemTra(List.of(123)).hopLe()).isFalse();
    }

    @Test
    @DisplayName("Ảnh lặp lại kể cả khác chữ hoa chữ thường bị từ chối")
    void anhLapLai() {
        String ma = UUID.randomUUID().toString();

        assertThat(KiemTraSoLuongAnh.kiemTra(List.of(ma, ma)).hopLe()).isFalse();
        assertThat(KiemTraSoLuongAnh.kiemTra(List.of(ma, ma.toUpperCase())).hopLe()).isFalse();
    }
}

