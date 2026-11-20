package com.realestates.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.listing.domain.QuyTacQuanLyTin;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuyTacQuanLyTinUnitTest {

    @Test
    @DisplayName("Chỉ tin đã duyệt hoặc bị từ chối được sửa")
    void trangThaiChoPhepSua() {
        assertThat(QuyTacQuanLyTin.choPhepSua("da_duyet")).isTrue();
        assertThat(QuyTacQuanLyTin.choPhepSua("bi_tu_choi")).isTrue();
        assertThat(QuyTacQuanLyTin.choPhepSua("cho_kiem_duyet")).isFalse();
        assertThat(QuyTacQuanLyTin.choPhepSua("da_xoa")).isFalse();
        assertThat(QuyTacQuanLyTin.choPhepSua("bi_go")).isFalse();
        assertThat(QuyTacQuanLyTin.choPhepSua(null)).isFalse();
    }

    @Test
    @DisplayName("Tin chờ duyệt, đã duyệt hoặc bị từ chối được xóa; tin đã xóa hoặc bị gỡ thì không")
    void trangThaiChoPhepXoa() {
        assertThat(QuyTacQuanLyTin.choPhepXoa("cho_kiem_duyet")).isTrue();
        assertThat(QuyTacQuanLyTin.choPhepXoa("da_duyet")).isTrue();
        assertThat(QuyTacQuanLyTin.choPhepXoa("bi_tu_choi")).isTrue();
        assertThat(QuyTacQuanLyTin.choPhepXoa("da_xoa")).isFalse();
        assertThat(QuyTacQuanLyTin.choPhepXoa("bi_go")).isFalse();
        assertThat(QuyTacQuanLyTin.choPhepXoa(null)).isFalse();
    }

    @Test
    @DisplayName("Phiên bản đã xem phải khớp cả ETag lẫn mã phiên bản hiện tại")
    void dungPhienBanDaXem() {
        UUID hienTai = UUID.randomUUID();

        assertThat(QuyTacQuanLyTin.dungPhienBanDaXem(2L, 2L, hienTai, hienTai)).isTrue();
        assertThat(QuyTacQuanLyTin.dungPhienBanDaXem(2L, 1L, hienTai, hienTai)).isFalse();
        assertThat(QuyTacQuanLyTin.dungPhienBanDaXem(2L, 2L, hienTai, UUID.randomUUID())).isFalse();
        assertThat(QuyTacQuanLyTin.dungPhienBanDaXem(2L, 2L, hienTai, null)).isFalse();
        assertThat(QuyTacQuanLyTin.dungPhienBanCapNhat(3L, 3L)).isTrue();
        assertThat(QuyTacQuanLyTin.dungPhienBanCapNhat(3L, 2L)).isFalse();
    }

    @Test
    @DisplayName("Dữ liệu audit có đủ trạng thái trước và sau")
    void duLieuAudit() {
        UUID truoc = UUID.randomUUID();
        UUID sau = UUID.randomUUID();

        String sua = QuyTacQuanLyTin.duLieuAuditSua("da_duyet", truoc, 0L, sau, 2, 1L);
        String xoa = QuyTacQuanLyTin.duLieuAuditXoa("cho_kiem_duyet", 1L, 2L);

        assertThat(sua).contains("\"truoc\"", "\"sau\"", "\"da_duyet\"", "\"cho_kiem_duyet\"", truoc.toString(), sau.toString());
        assertThat(xoa).contains("\"truoc\"", "\"sau\"", "\"cho_kiem_duyet\"", "\"da_xoa\"");
    }
}

