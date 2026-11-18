package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.domain.AnhTinRepository;
import com.realestates.backend.listing.domain.AnhTinTruyCap;
import com.realestates.backend.listing.domain.DinhDangAnh;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAnhTinRepository implements AnhTinRepository {

    private record BanGhiAnh(AnhTinTruyCap truyCap, Instant taoLuc, Integer thuTu) {
    }

    private final ConcurrentMap<UUID, BanGhiAnh> anhTheoId = new ConcurrentHashMap<>();

    @Override
    public void luuAnhTam(
            UUID id,
            UUID chuId,
            String khoaTepGoc,
            String khoaAnhHienThi,
            DinhDangAnh dinhDang,
            long kichThuocByte
    ) {
        anhTheoId.put(id, new BanGhiAnh(
                new AnhTinTruyCap(
                        id,
                        chuId,
                        AnhTinTruyCap.ANH_TAM,
                        khoaTepGoc,
                        khoaAnhHienThi,
                        dinhDang.getMimeType(),
                        null,
                        null,
                        null,
                        null,
                        false
                ),
                Instant.now(),
                null
        ));
    }

    @Override
    public Optional<AnhTinTruyCap> timAnhDeDoc(UUID anhId) {
        BanGhiAnh banGhi = anhTheoId.get(anhId);
        return banGhi == null ? Optional.empty() : Optional.of(banGhi.truyCap());
    }

    @Override
    public boolean coThamChieuKhoa(String khoaDoiTuong) {
        if (khoaDoiTuong == null || khoaDoiTuong.isBlank()) {
            return false;
        }
        return anhTheoId.values().stream()
                .map(BanGhiAnh::truyCap)
                .anyMatch(a -> khoaDoiTuong.equals(a.khoaTepGoc()) || khoaDoiTuong.equals(a.khoaAnhHienThi()));
    }

    @Override
    public boolean coThamChieuKhoaNgoaiAnh(UUID anhId, String khoaDoiTuong) {
        if (khoaDoiTuong == null || khoaDoiTuong.isBlank()) {
            return false;
        }
        return anhTheoId.values().stream()
                .map(BanGhiAnh::truyCap)
                .anyMatch(a -> !a.anhId().equals(anhId)
                        && (khoaDoiTuong.equals(a.khoaTepGoc()) || khoaDoiTuong.equals(a.khoaAnhHienThi())));
    }

    @Override
    public int danhDauAnhTamQuaHanChoDon(Instant mocHetHan) {
        if (mocHetHan == null) {
            return 0;
        }
        int count = 0;
        for (UUID id : List.copyOf(anhTheoId.keySet())) {
            BanGhiAnh banGhi = anhTheoId.get(id);
            if (banGhi != null
                    && AnhTinTruyCap.ANH_TAM.equals(banGhi.truyCap().trangThaiAnh())
                    && banGhi.truyCap().phienBanTinId() == null
                    && !banGhi.taoLuc().isAfter(mocHetHan)) {
                AnhTinTruyCap cu = banGhi.truyCap();
                AnhTinTruyCap choDon = new AnhTinTruyCap(
                        cu.anhId(),
                        cu.chuAnhId(),
                        "cho_don",
                        cu.khoaTepGoc(),
                        cu.khoaAnhHienThi(),
                        cu.mimeType(),
                        null,
                        null,
                        null,
                        null,
                        false
                );
                if (anhTheoId.replace(id, banGhi, new BanGhiAnh(choDon, banGhi.taoLuc(), null))) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public List<AnhChoDon> layDanhSachAnhChoDon(int gioiHan) {
        if (gioiHan <= 0) {
            return List.of();
        }
        List<AnhChoDon> ketQua = new ArrayList<>();
        for (BanGhiAnh banGhi : anhTheoId.values()) {
            AnhTinTruyCap a = banGhi.truyCap();
            if ("cho_don".equals(a.trangThaiAnh()) && a.phienBanTinId() == null) {
                ketQua.add(new AnhChoDon(a.anhId(), a.khoaTepGoc(), a.khoaAnhHienThi()));
                if (ketQua.size() >= gioiHan) {
                    break;
                }
            }
        }
        return List.copyOf(ketQua);
    }

    @Override
    public boolean xoaMetadataAnhChoDon(UUID anhId) {
        BanGhiAnh banGhi = anhTheoId.get(anhId);
        if (banGhi == null || !"cho_don".equals(banGhi.truyCap().trangThaiAnh()) || banGhi.truyCap().phienBanTinId() != null) {
            return false;
        }
        return anhTheoId.remove(anhId, banGhi);
    }

    public synchronized boolean kiemTraTatCaAnhTamHopLeChoChu(UUID chuId, List<UUID> anhIds) {
        if (chuId == null || anhIds == null || anhIds.isEmpty()) {
            return false;
        }
        for (UUID anhId : anhIds) {
            BanGhiAnh banGhi = anhTheoId.get(anhId);
            if (banGhi == null) {
                return false;
            }
            AnhTinTruyCap a = banGhi.truyCap();
            if (!chuId.equals(a.chuAnhId())
                    || !AnhTinTruyCap.ANH_TAM.equals(a.trangThaiAnh())
                    || a.khoaAnhHienThi() == null
                    || a.khoaAnhHienThi().isBlank()) {
                return false;
            }
        }
        return true;
    }

    public synchronized void ganAnhVaoPhienBanChoDuyet(UUID chuId, UUID phienBanId, List<UUID> anhIds) {
        for (int i = 0; i < anhIds.size(); i++) {
            UUID anhId = anhIds.get(i);
            BanGhiAnh cu = anhTheoId.get(anhId);
            if (cu == null || !chuId.equals(cu.truyCap().chuAnhId())) {
                throw new IllegalStateException("Ảnh không hợp lệ khi gắn: " + anhId);
            }
            AnhTinTruyCap a = cu.truyCap();
            AnhTinTruyCap daGan = new AnhTinTruyCap(
                    a.anhId(),
                    a.chuAnhId(),
                    AnhTinTruyCap.ANH_DA_GAN,
                    a.khoaTepGoc(),
                    a.khoaAnhHienThi(),
                    a.mimeType(),
                    phienBanId,
                    AnhTinTruyCap.TIN_CHO_KIEM_DUYET,
                    phienBanId,
                    null,
                    true
            );
            anhTheoId.put(anhId, new BanGhiAnh(daGan, cu.taoLuc(), i + 1));
        }
    }
}
