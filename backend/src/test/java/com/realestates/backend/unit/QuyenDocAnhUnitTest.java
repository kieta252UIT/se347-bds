package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.auth.domain.PrincipalPhienHienTai;
import com.realestates.backend.auth.domain.TrangThaiTaiKhoan;
import com.realestates.backend.auth.domain.VaiTroTaiKhoan;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.QuyenDocAnh;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuyenDocAnhUnitTest {

    private static final UUID CHU_ID = UUID.randomUUID();
    private static final UUID PHIEN_BAN_HIEN_TAI = UUID.randomUUID();
    private static final UUID PHIEN_BAN_DA_DUYET = UUID.randomUUID();
    private static final UUID PHIEN_BAN_CU = UUID.randomUUID();

    private static PrincipalPhienHienTai nguoiDoc(UUID taiKhoanId, VaiTroTaiKhoan vaiTro) {
        return new PrincipalPhienHienTai(
                UUID.randomUUID(),
                taiKhoanId,
                "ma-cookie-bam",
                "nguoi.doc@example.com",
                "Người Đọc",
                null,
                vaiTro,
                TrangThaiTaiKhoan.HOAT_DONG,
                true,
                0L
        );
    }

    private static AnhTinTruyCap anhTam() {
        return new AnhTinTruyCap(
                UUID.randomUUID(), CHU_ID, AnhTinTruyCap.ANH_TAM, "goc.png", null, "image/png",
                null, null, null, null, false
        );
    }

    private static AnhTinTruyCap anhDaGan(
            UUID phienBanCuaAnh,
            String trangThaiAnh,
            String trangThaiTin,
            boolean chuTinHoatDong
    ) {
        return new AnhTinTruyCap(
                UUID.randomUUID(), CHU_ID, trangThaiAnh, "goc.png", "hien-thi.png", "image/png",
                phienBanCuaAnh, trangThaiTin, PHIEN_BAN_HIEN_TAI, PHIEN_BAN_DA_DUYET, chuTinHoatDong
        );
    }

    @Test
    @DisplayName("Ảnh tạm chỉ chủ upload đọc được")
    void anhTamChiChuDocDuoc() {
        AnhTinTruyCap anh = anhTam();

        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(CHU_ID, VaiTroTaiKhoan.NGUOI_DUNG))).isTrue();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.NGUOI_DUNG))).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN))).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN))).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, null)).isFalse();
    }

    @Test
    @DisplayName("Ảnh của phiên bản đã duyệt thuộc tin đã duyệt và chủ hoạt động ai cũng đọc được kể cả khách")
    void anhTinDaDuyetCongKhai() {
        AnhTinTruyCap anh = anhDaGan(PHIEN_BAN_DA_DUYET, AnhTinTruyCap.ANH_DA_GAN, AnhTinTruyCap.TIN_DA_DUYET, true);

        assertThat(QuyenDocAnh.duocPhepDoc(anh, null)).isTrue();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.NGUOI_DUNG))).isTrue();
    }

    @Test
    @DisplayName("Ảnh tin đã duyệt không công khai khi chủ không còn hoạt động hoặc ảnh thuộc phiên bản khác phiên bản đã duyệt")
    void anhTinDaDuyetKhongCongKhaiKhiKhongDuDieuKien() {
        AnhTinTruyCap chuBiKhoa = anhDaGan(PHIEN_BAN_DA_DUYET, AnhTinTruyCap.ANH_DA_GAN, AnhTinTruyCap.TIN_DA_DUYET, false);
        AnhTinTruyCap phienBanCu = anhDaGan(PHIEN_BAN_CU, AnhTinTruyCap.ANH_DA_GAN, AnhTinTruyCap.TIN_DA_DUYET, true);

        assertThat(QuyenDocAnh.duocPhepDoc(chuBiKhoa, null)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(phienBanCu, null)).isFalse();
    }

    @Test
    @DisplayName("Ảnh tin chờ kiểm duyệt chỉ chủ và kiểm duyệt viên đọc được")
    void anhTinChoKiemDuyet() {
        AnhTinTruyCap anh = anhDaGan(PHIEN_BAN_HIEN_TAI, AnhTinTruyCap.ANH_DA_GAN, AnhTinTruyCap.TIN_CHO_KIEM_DUYET, true);

        assertThat(QuyenDocAnh.duocPhepDoc(anh, null)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.NGUOI_DUNG))).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN))).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(CHU_ID, VaiTroTaiKhoan.NGUOI_DUNG))).isTrue();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN))).isTrue();
    }

    @Test
    @DisplayName("Kiểm duyệt viên không đọc được ảnh của phiên bản không còn là phiên bản đang chờ duyệt")
    void kiemDuyetVienKhongDocAnhPhienBanCu() {
        AnhTinTruyCap anh = anhDaGan(PHIEN_BAN_CU, AnhTinTruyCap.ANH_DA_GAN, AnhTinTruyCap.TIN_CHO_KIEM_DUYET, true);

        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN))).isFalse();
    }

    @Test
    @DisplayName("Chủ đọc được ảnh tin của mình ở trạng thái khác như bị từ chối, đã xóa")
    void chuDocDuocAnhTinCuaMinh() {
        AnhTinTruyCap anh = anhDaGan(PHIEN_BAN_HIEN_TAI, AnhTinTruyCap.ANH_DA_GAN, "bi_tu_choi", true);

        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(CHU_ID, VaiTroTaiKhoan.NGUOI_DUNG))).isTrue();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, null)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.NGUOI_DUNG))).isFalse();
    }

    @Test
    @DisplayName("Ảnh chờ dọn không ai đọc được")
    void anhChoDonKhongAiDocDuoc() {
        AnhTinTruyCap anh = anhDaGan(PHIEN_BAN_DA_DUYET, "cho_don", AnhTinTruyCap.TIN_DA_DUYET, true);

        assertThat(QuyenDocAnh.duocPhepDoc(anh, null)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDoc(anh, nguoiDoc(CHU_ID, VaiTroTaiKhoan.NGUOI_DUNG))).isFalse();
    }

    @Test
    @DisplayName("Ảnh không tồn tại không ai đọc được")
    void anhKhongTonTai() {
        assertThat(QuyenDocAnh.duocPhepDoc(null, null)).isFalse();
    }

    @Test
    @DisplayName("Khóa đối tượng cần đọc ưu tiên bản hiển thị, nếu chưa có thì dùng tệp gốc")
    void khoaDoiTuongCanDoc() {
        assertThat(anhTam().khoaDoiTuongCanDoc()).isEqualTo("goc.png");
        assertThat(
                anhDaGan(PHIEN_BAN_DA_DUYET, AnhTinTruyCap.ANH_DA_GAN, AnhTinTruyCap.TIN_DA_DUYET, true).khoaDoiTuongCanDoc()
        ).isEqualTo("hien-thi.png");
    }
}
