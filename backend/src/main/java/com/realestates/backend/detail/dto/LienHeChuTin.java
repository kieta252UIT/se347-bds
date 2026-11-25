package com.realestates.backend.detail.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Schema LienHeChuTin: chỉ họ tên và số điện thoại (có thể null); không bao giờ có email. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LienHeChuTin(String hoTen, String soDienThoai) {
}
