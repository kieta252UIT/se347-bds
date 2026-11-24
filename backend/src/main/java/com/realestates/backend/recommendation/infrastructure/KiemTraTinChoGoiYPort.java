package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.TieuChiGoiY;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KiemTraTinChoGoiYPort {

    record ThongTinTinGoiY(
            UUID tinId,
            UUID chuId,
            String tieuDe,
            String moTa,
            String loaiGiaoDich,
            String loaiBds,
            BigDecimal giaGoc,
            String donViGia,
            BigDecimal giaTongChuan,
            BigDecimal giaM2Chuan,
            BigDecimal dienTichM2,
            String diaChi,
            String khuVuc,
            ViTriToaDo toaDo,
            Instant ngayDang,
            UUID anhDaiDienId,
            String trangThaiTin,
            String trangThaiChu,
            boolean coGoiTaiTroHieuLuc
    ) {
        public boolean hopLeCongKhaiBR01() {
            return "da_duyet".equalsIgnoreCase(trangThaiTin)
                    && "hoat_dong".equalsIgnoreCase(trangThaiChu);
        }
    }

    /**
     * Lấy danh sách các tin bất động sản hợp lệ công khai (tin.da_duyet AND chu.hoat_dong theo BR-01)
     * đồng thời thỏa mãn tiêu chí lọc nếu có.
     */
    List<ThongTinTinGoiY> layDanhSachTinCongKhaiHopLe(TieuChiGoiY tieuChi, Instant now);

    /**
     * Lấy danh sách các tin đang có gói đẩy tin còn hiệu lực thực tế (BR-06)
     * và thỏa mãn tiêu chí lọc phục vụ khu vực Được tài trợ.
     */
    List<ThongTinTinGoiY> layDanhSachTinTaiTroHopLe(TieuChiGoiY tieuChi, Instant now);
}
