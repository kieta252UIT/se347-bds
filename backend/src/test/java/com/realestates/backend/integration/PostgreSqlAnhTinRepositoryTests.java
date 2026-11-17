package com.realestates.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.realestates.backend.database.TestDatabaseSettings;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import com.realestates.backend.listing.infrastructure.PostgreSqlAnhTinRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseSettings.SpringInitializer.class)
class PostgreSqlAnhTinRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID taoTaiKhoan() {
        UUID taiKhoanId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO se214.tai_khoan (id, supabase_auth_user_id, email, ho_ten) VALUES (?, ?, ?, 'Chủ ảnh thử')",
                taiKhoanId,
                UUID.randomUUID(),
                "chu-anh-" + taiKhoanId + "@bds-test.local"
        );
        return taiKhoanId;
    }

    @Test
    @Transactional
    @DisplayName("Lưu ảnh tạm rồi đọc lại đúng chủ, trạng thái tam và chưa gắn tin")
    void luuVaDocAnhTam() {
        PostgreSqlAnhTinRepository kho = new PostgreSqlAnhTinRepository(jdbcTemplate);
        UUID chuId = taoTaiKhoan();
        UUID anhId = UUID.randomUUID();

        kho.luuAnhTam(anhId, chuId, anhId + ".png", DinhDangAnh.PNG, 1234L);
        Optional<AnhTinTruyCap> anh = kho.timAnhDeDoc(anhId);

        assertThat(anh).isPresent();
        assertThat(anh.get().chuAnhId()).isEqualTo(chuId);
        assertThat(anh.get().trangThaiAnh()).isEqualTo("tam");
        assertThat(anh.get().khoaTepGoc()).isEqualTo(anhId + ".png");
        assertThat(anh.get().khoaAnhHienThi()).isNull();
        assertThat(anh.get().mimeType()).isEqualTo("image/png");
        assertThat(anh.get().phienBanTinId()).isNull();
        assertThat(anh.get().trangThaiTin()).isNull();
        assertThat(anh.get().chuTinHoatDong()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("Ảnh không tồn tại trả về rỗng")
    void anhKhongTonTai() {
        PostgreSqlAnhTinRepository kho = new PostgreSqlAnhTinRepository(jdbcTemplate);

        assertThat(kho.timAnhDeDoc(UUID.randomUUID())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("Ảnh đã gắn trả kèm trạng thái tin, phiên bản và trạng thái chủ tin")
    void docAnhDaGanKemNguCanhTin() {
        PostgreSqlAnhTinRepository kho = new PostgreSqlAnhTinRepository(jdbcTemplate);
        UUID chuId = taoTaiKhoan();
        UUID tinId = UUID.randomUUID();
        UUID phienBanId = UUID.randomUUID();
        UUID anhId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO se214.tin_bat_dong_san (id, chu_id, trang_thai, phien_ban_hien_tai_id, phien_ban_da_duyet_id) "
                        + "VALUES (?, ?, 'da_duyet', ?, ?)",
                tinId, chuId, phienBanId, phienBanId
        );
        jdbcTemplate.update(
                "INSERT INTO se214.phien_ban_tin (id, tin_id, so_phien_ban, tieu_de, mo_ta, loai_giao_dich, loai_bds, "
                        + "gia_goc, don_vi_gia, dien_tich_m2, dia_chi, khu_vuc, vi_do, kinh_do, ket_qua_so_bo) "
                        + "VALUES (?, ?, 1, 'Tiêu đề thử', 'Mô tả thử', 'ban', 'can_ho', "
                        + "1000000000, 'vnd_tong', 50, '1 Đường thử', 'Khu vực thử', 10.5, 106.5, 'dat')",
                phienBanId, tinId
        );
        jdbcTemplate.update(
                "INSERT INTO se214.anh_tin (id, chu_id, phien_ban_tin_id, khoa_tep_goc, khoa_anh_hien_thi, mime_type, "
                        + "kich_thuoc_byte, thu_tu, trang_thai) "
                        + "VALUES (?, ?, ?, 'goc.png', 'hien-thi.png', 'image/png', 1000, 1, 'da_gan')",
                anhId, chuId, phienBanId
        );

        Optional<AnhTinTruyCap> anh = kho.timAnhDeDoc(anhId);

        assertThat(anh).isPresent();
        assertThat(anh.get().trangThaiAnh()).isEqualTo("da_gan");
        assertThat(anh.get().khoaAnhHienThi()).isEqualTo("hien-thi.png");
        assertThat(anh.get().phienBanTinId()).isEqualTo(phienBanId);
        assertThat(anh.get().trangThaiTin()).isEqualTo("da_duyet");
        assertThat(anh.get().phienBanHienTaiId()).isEqualTo(phienBanId);
        assertThat(anh.get().phienBanDaDuyetId()).isEqualTo(phienBanId);
        assertThat(anh.get().chuTinHoatDong()).isTrue();

        jdbcTemplate.update("UPDATE se214.tai_khoan SET trang_thai = 'bi_khoa' WHERE id = ?", chuId);
        assertThat(kho.timAnhDeDoc(anhId).get().chuTinHoatDong()).isFalse();
    }
}

