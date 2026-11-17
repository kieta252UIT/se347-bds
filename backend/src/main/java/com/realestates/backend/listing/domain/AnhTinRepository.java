package com.realestates.backend.listing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnhTinRepository {

    record AnhChoDon(UUID id, String khoaTepGoc, String khoaAnhHienThi) {
    }

    void luuAnhTam(
            UUID id,
            UUID chuId,
            String khoaTepGoc,
            String khoaAnhHienThi,
            DinhDangAnh dinhDang,
            long kichThuocByte
    );

    Optional<AnhTinTruyCap> timAnhDeDoc(UUID anhId);

    boolean coThamChieuKhoa(String khoaDoiTuong);

    default boolean coThamChieuKhoaNgoaiAnh(UUID anhId, String khoaDoiTuong) {
        return coThamChieuKhoa(khoaDoiTuong);
    }

    default int danhDauAnhTamQuaHanChoDon(Instant mocHetHan) {
        return 0;
    }

    default List<AnhChoDon> layDanhSachAnhChoDon(int gioiHan) {
        return List.of();
    }

    default boolean xoaMetadataAnhChoDon(UUID anhId) {
        return false;
    }
}
