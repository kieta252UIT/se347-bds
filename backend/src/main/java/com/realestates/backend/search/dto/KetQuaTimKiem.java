package com.realestates.backend.search.dto;

import java.time.Instant;
import java.util.List;

/** Schema KetQuaTimKiem: khu tự nhiên phân trang, khu tài trợ tối đa 5, mốc truy vấn dùng chung. */
public record KetQuaTimKiem(TrangTinCongKhai tuNhien, List<TinTomTat> taiTro, Instant thoiDiemTruyVan) {
}
