package com.realestates.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * DTO phản hồi cho GET /api/csrf (operationId: layCsrf) theo schema CsrfKetQua
 * trong openapi-sprint-1.yaml và api-specification-sprint-1.md §8.6.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CsrfKetQua(String csrfToken) {

    public CsrfKetQua {
        if (csrfToken == null || csrfToken.isBlank()) {
            throw new IllegalArgumentException("csrfToken không được rỗng");
        }
        csrfToken = Objects.requireNonNull(csrfToken).trim();
    }
}
