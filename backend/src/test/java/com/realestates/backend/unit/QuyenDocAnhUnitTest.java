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

    @Test
    @DisplayName("AC-08.2 / Q37: Trong ngữ cảnh báo cáo, KDV đọc được ảnh của phien_ban_da_duyet_id kể cả khi bản sửa đang chờ duyệt hoặc bị từ chối")
    void kdvDocDuocAnhBanDaDuyetTrongNguCanhBaoCaoKhiBanSuaChoDuyetHoacBiTuChoi() {
        UUID tinBaoCaoId = UUID.randomUUID();
        PrincipalPhienHienTai kdv = nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN);
        PrincipalPhienHienTai admin = nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.QUAN_TRI_VIEN);
        PrincipalPhienHienTai chuTin = nguoiDoc(CHU_ID, VaiTroTaiKhoan.NGUOI_DUNG);

        // 1. Tin đang cho_kiem_duyet bản sửa mới: ảnh bản đã duyệt cũ được đọc qua ngữ cảnh báo cáo, ảnh bản sửa chưa duyệt bị chặn
        AnhTinTruyCap anhBanDaDuyetKhiDangSua = anhDaGan(
                PHIEN_BAN_DA_DUYET,
                AnhTinTruyCap.ANH_DA_GAN,
                AnhTinTruyCap.TIN_CHO_KIEM_DUYET,
                true
        );
        AnhTinTruyCap anhBanSuaChuaDuyet = anhDaGan(
                PHIEN_BAN_HIEN_TAI,
                AnhTinTruyCap.ANH_DA_GAN,
                AnhTinTruyCap.TIN_CHO_KIEM_DUYET,
                true
        );

        assertThat(QuyenDocAnh.duocPhepDoc(anhBanDaDuyetKhiDangSua, kdv)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanDaDuyetKhiDangSua, tinBaoCaoId, tinBaoCaoId, kdv)).isTrue();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanSuaChuaDuyet, tinBaoCaoId, tinBaoCaoId, kdv)).isFalse();

        // 2. Bản sửa bị từ chối (trangThaiTin = bi_tu_choi), chủ tin thậm chí bị khóa: KDV vẫn đọc được bản đã duyệt gần nhất để xử lý báo cáo
        AnhTinTruyCap anhBanDaDuyetKhiSuaBiTuChoi = anhDaGan(
                PHIEN_BAN_DA_DUYET,
                AnhTinTruyCap.ANH_DA_GAN,
                "bi_tu_choi",
                false
        );
        assertThat(QuyenDocAnh.duocPhepDoc(anhBanDaDuyetKhiSuaBiTuChoi, kdv)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanDaDuyetKhiSuaBiTuChoi, tinBaoCaoId, tinBaoCaoId, kdv)).isTrue();

        // 3. Sai vai trò hoặc lệch tinId của báo cáo thì bị chặn
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanDaDuyetKhiDangSua, tinBaoCaoId, UUID.randomUUID(), kdv)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanDaDuyetKhiDangSua, tinBaoCaoId, tinBaoCaoId, admin)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanDaDuyetKhiDangSua, tinBaoCaoId, tinBaoCaoId, chuTin)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanDaDuyetKhiDangSua, tinBaoCaoId, tinBaoCaoId, null)).isFalse();
    }

    @Test
    @DisplayName("AC-08.2 & AC-08.3: Khi bản sửa mới được phê duyệt (phien_ban_da_duyet_id đổi) hoặc tin bị xóa, ảnh bản cũ không còn đọc được qua ngữ cảnh báo cáo")
    void kdvKhongDocAnhBanCuSauKhiBanMoiDuocDuyetHoacKhiTinDaXoa() {
        UUID tinBaoCaoId = UUID.randomUUID();
        UUID phienBanMoiVuaDuyet = UUID.randomUUID();
        PrincipalPhienHienTai kdv = nguoiDoc(UUID.randomUUID(), VaiTroTaiKhoan.KIEM_DUYET_VIEN);

        // Bản sửa mới đã được duyệt -> phienBanDaDuyetId tiến lên phienBanMoiVuaDuyet
        AnhTinTruyCap anhBanCuTruocDay = new AnhTinTruyCap(
                UUID.randomUUID(),
                CHU_ID,
                AnhTinTruyCap.ANH_DA_GAN,
                "goc-v1.png",
                "hien-thi-v1.png",
                "image/png",
                PHIEN_BAN_DA_DUYET,
                AnhTinTruyCap.TIN_DA_DUYET,
                phienBanMoiVuaDuyet,
                phienBanMoiVuaDuyet,
                true
        );
        AnhTinTruyCap anhBanMoiHienHanh = new AnhTinTruyCap(
                UUID.randomUUID(),
                CHU_ID,
                AnhTinTruyCap.ANH_DA_GAN,
                "goc-v2.png",
                "hien-thi-v2.png",
                "image/png",
                phienBanMoiVuaDuyet,
                AnhTinTruyCap.TIN_DA_DUYET,
                phienBanMoiVuaDuyet,
                phienBanMoiVuaDuyet,
                true
        );
        AnhTinTruyCap anhTinDaXoa = anhDaGan(PHIEN_BAN_DA_DUYET, AnhTinTruyCap.ANH_DA_GAN, "da_xoa", true);

        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanCuTruocDay, tinBaoCaoId, tinBaoCaoId, kdv)).isFalse();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhBanMoiHienHanh, tinBaoCaoId, tinBaoCaoId, kdv)).isTrue();
        assertThat(QuyenDocAnh.duocPhepDocTrongNguCanhBaoCao(anhTinDaXoa, tinBaoCaoId, tinBaoCaoId, kdv)).isFalse();
    }
}
