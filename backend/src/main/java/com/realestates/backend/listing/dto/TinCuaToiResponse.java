package com.realestates.backend.listing.dto;

import com.realestates.backend.listing.domain.TinDangRepository.DuLieuTinCuaToi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TinCuaToiResponse(
        UUID id,
        String tieuDe,
        String moTa,
        String loaiGiaoDich,
        String loaiBds,
        BigDecimal giaGoc,
        String donViGia,
        BigDecimal dienTichM2,
        String diaChi,
        String khuVuc,
        BigDecimal viDo,
        BigDecimal kinhDo,
        String trangThai,
        String lyDoTuChoi,
        UUID phienBanHienTaiId,
        int soPhienBan,
        long phienBanCapNhat,
        Instant ngayDang,
        List<UUID> anhIds,
        String anhDaiDienUrl
) {
    public static TinCuaToiResponse fromDuLieu(DuLieuTinCuaToi duLieu) {
        List<UUID> anhIds = duLieu.anhIds() != null ? duLieu.anhIds() : List.of();
        String anhDaiDien = !anhIds.isEmpty() ? "/api/images/" + anhIds.get(0) + "/content" : null;
        return new TinCuaToiResponse(
                duLieu.tinId(),
                duLieu.tieuDe(),
                duLieu.moTa(),
                duLieu.loaiGiaoDich(),
                duLieu.loaiBds(),
                duLieu.giaGoc(),
                duLieu.donViGia(),
                duLieu.dienTichM2(),
                duLieu.diaChi(),
                duLieu.khuVuc(),
                duLieu.viDo(),
                duLieu.kinhDo(),
                duLieu.trangThai(),
                duLieu.lyDoTuChoi(),
                duLieu.phienBanHienTaiId(),
                duLieu.soPhienBan(),
                duLieu.phienBanCapNhat(),
                duLieu.ngayDang(),
                anhIds,
                anhDaiDien
        );
    }
}
