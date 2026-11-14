package com.realestates.backend.auth.dto;

/**
 * Chi tiết lỗi cấp trường dữ liệu trong LoiApi.truongLoi theo api-specification-sprint-1.md §3.4.
 */
public record FieldErrorItem(
        String truong,
        String maLoi,
        String thongDiep
) {
}
