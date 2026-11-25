package com.realestates.backend.search.dto;

import java.util.List;

/** Schema TrangTinCongKhai. */
public record TrangTinCongKhai(List<TinTomTat> noiDung, int trang, int kichThuoc, long tongSo) {
}
