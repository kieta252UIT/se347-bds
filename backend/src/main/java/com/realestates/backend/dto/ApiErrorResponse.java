package com.realestates.backend.dto;

import java.util.List;
import java.util.UUID;

public record ApiErrorResponse(
        String maLoi,
        String thongDiep,
        List<FieldErrorItem> truongLoi,
        String traceId
) {
    public static ApiErrorResponse of(String maLoi, String thongDiep) {
        return new ApiErrorResponse(maLoi, thongDiep, List.of(), generateTraceId());
    }

    public static ApiErrorResponse of(String maLoi, String thongDiep, List<FieldErrorItem> truongLoi) {
        return new ApiErrorResponse(
                maLoi,
                thongDiep,
                truongLoi == null ? List.of() : List.copyOf(truongLoi),
                generateTraceId()
        );
    }

    public static String generateTraceId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
