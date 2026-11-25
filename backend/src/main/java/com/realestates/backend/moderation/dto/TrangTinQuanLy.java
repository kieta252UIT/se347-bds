package com.realestates.backend.moderation.dto;

import java.util.List;

/** Schema TrangTinQuanLy: phân trang theo trang/kichThuoc. */
public record TrangTinQuanLy(List<TinQuanLy> noiDung, int trang, int kichThuoc, long tongSo) {
}
