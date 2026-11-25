package com.realestates.backend.detail.dto;

import java.time.Instant;
import java.util.UUID;

/** Schema TinCongKhai của GET /api/listings/{listingId}; trangThai luôn da_duyet. */
public record TinCongKhai(UUID id, String trangThai, Instant ngayDang, PhienBanNoiDung noiDung, LienHeChuTin lienHe) {
}
