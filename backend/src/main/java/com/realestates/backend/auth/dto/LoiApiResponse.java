package com.realestates.backend.auth.dto;

import java.util.List;

/**
 * Cấu trúc lỗi chuẩn LoiApi theo api-specification-sprint-1.md §3.4 và openapi-sprint-1.yaml.
 */
public record LoiApiResponse(
        String maLoi,
        String thongDiep,
        List<FieldErrorItem> truongLoi,
        String traceId
) {
}
