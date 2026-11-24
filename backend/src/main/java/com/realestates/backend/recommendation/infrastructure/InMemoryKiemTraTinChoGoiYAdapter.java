package com.realestates.backend.recommendation.infrastructure;

import com.realestates.backend.recommendation.domain.TieuChiGoiY;
import com.realestates.backend.recommendation.domain.ViTriToaDo;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryKiemTraTinChoGoiYAdapter implements KiemTraTinChoGoiYPort {

    private final CopyOnWriteArrayList<ThongTinTinGoiY> danhSachTin = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void khoiTaoDuLieuMau() {
        if (!danhSachTin.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        // 1. Căn hộ Quận 1 (gần trung tâm)
        danhSachTin.add(new ThongTinTinGoiY(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                "Nhà phố Bến Nghé Quận 1 - Mặt tiền sang trọng",
                "Nhà phố vị trí đắc địa ngay trung tâm Bến Nghé Quận 1",
                "ban",
                "nha_pho",
                new BigDecimal("12000000000"),
                "vnd_tong",
                new BigDecimal("12000000000"),
                new BigDecimal("120000000"),
                new BigDecimal("100"),
                "Nguyễn Du, Bến Nghé, Quận 1",
                "Quan 1",
                new ViTriToaDo(new BigDecimal("10.7769"), new BigDecimal("106.7009")),
                now.minus(1, ChronoUnit.DAYS),
                null,
                "da_duyet",
                "hoat_dong",
                false
        ));

        // 2. Căn hộ Bình Thạnh (cách Quận 1 ~3km)
        danhSachTin.add(new ThongTinTinGoiY(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                "Căn hộ cao cấp Saigon Pearl view sông Sài Gòn",
                "Căn hộ 2 phòng ngủ nội thất cao cấp đầy đủ tiện ích",
                "ban",
                "can_ho",
                new BigDecimal("4500000000"),
                "vnd_tong",
                new BigDecimal("4500000000"),
                new BigDecimal("60000000"),
                new BigDecimal("75"),
                "Nguyễn Hữu Cảnh, Phường 22, Bình Thạnh",
                "Binh Thanh",
                new ViTriToaDo(new BigDecimal("10.7915"), new BigDecimal("106.7176")),
                now.minus(2, ChronoUnit.DAYS),
                null,
                "da_duyet",
                "hoat_dong",
                false
        ));

        // 3. Đất nền Cần Giờ (xa trung tâm ~40km)
        danhSachTin.add(new ThongTinTinGoiY(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                "Đất nền ven biển Cần Giờ tiềm năng sinh lời cao",
                "Đất sổ đỏ chính chủ gần phà Bình Khánh",
                "ban",
                "dat_nen",
                new BigDecimal("1800000000"),
                "vnd_tong",
                new BigDecimal("1800000000"),
                new BigDecimal("12000000"),
                new BigDecimal("150"),
                "Rừng Sác, Cần Giờ",
                "Can Gio",
                new ViTriToaDo(new BigDecimal("10.4100"), new BigDecimal("106.9100")),
                now.minus(3, ChronoUnit.DAYS),
                null,
                "da_duyet",
                "hoat_dong",
                false
        ));

        // 4. Biệt thự Thảo Điền VIP (Có gói đẩy tin tài trợ - BR-06)
        danhSachTin.add(new ThongTinTinGoiY(
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
                "VIP - Biệt thự Thảo Điền hồ bơi sân vườn cao cấp",
                "Biệt thự phong cách Châu Âu đẳng cấp thượng lưu",
                "ban",
                "biet_thu",
                new BigDecimal("35000000000"),
                "vnd_tong",
                new BigDecimal("35000000000"),
                new BigDecimal("100000000"),
                new BigDecimal("350"),
                "Nguyễn Văn Hưởng, Thảo Điền, TP. Thủ Đức",
                "Thu Duc",
                new ViTriToaDo(new BigDecimal("10.8050"), new BigDecimal("106.7350")),
                now,
                null,
                "da_duyet",
                "hoat_dong",
                true // coGoiTaiTroHieuLuc = true
        ));
    }

    public void mockThemTin(ThongTinTinGoiY tin) {
        if (tin != null) {
            danhSachTin.removeIf(t -> t.tinId().equals(tin.tinId()));
            danhSachTin.add(tin);
        }
    }

    public void clear() {
        danhSachTin.clear();
    }

    @Override
    public List<ThongTinTinGoiY> layDanhSachTinCongKhaiHopLe(TieuChiGoiY tieuChi, Instant now) {
        return danhSachTin.stream()
                .filter(ThongTinTinGoiY::hopLeCongKhaiBR01)
                .filter(t -> thoaManTieuChi(t, tieuChi))
                .toList();
    }

    @Override
    public List<ThongTinTinGoiY> layDanhSachTinTaiTroHopLe(TieuChiGoiY tieuChi, Instant now) {
        return danhSachTin.stream()
                .filter(ThongTinTinGoiY::hopLeCongKhaiBR01)
                .filter(ThongTinTinGoiY::coGoiTaiTroHieuLuc)
                .filter(t -> thoaManTieuChi(t, tieuChi))
                .toList();
    }

    private boolean thoaManTieuChi(ThongTinTinGoiY tin, TieuChiGoiY tieuChi) {
        if (tieuChi == null) {
            return true;
        }

        if (tieuChi.coLocLoaiBds() && !tieuChi.loaiBds().equalsIgnoreCase(tin.loaiBds())) {
            return false;
        }

        if (tieuChi.coLocLoaiGiaoDich() && !tieuChi.loaiGiaoDich().equalsIgnoreCase(tin.loaiGiaoDich())) {
            return false;
        }

        if (tieuChi.coLocKhuVuc() && !tieuChi.khuVuc().equalsIgnoreCase(tin.khuVuc())) {
            return false;
        }

        if (tieuChi.giaMinVnd() != null && tin.giaTongChuan() != null
                && tin.giaTongChuan().compareTo(tieuChi.giaMinVnd()) < 0) {
            return false;
        }

        if (tieuChi.giaMaxVnd() != null && tin.giaTongChuan() != null
                && tin.giaTongChuan().compareTo(tieuChi.giaMaxVnd()) > 0) {
            return false;
        }

        return true;
    }
}
