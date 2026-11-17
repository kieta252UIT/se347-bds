package com.realestates.backend.listing.domain;

import java.util.Optional;
import java.util.UUID;

public interface AnhTinRepository {

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
}
