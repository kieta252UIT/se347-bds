package com.realestates.backend.moderation.dto;

import java.util.UUID;

/** Schema AnhHienThi: chỉ trả đường dẫn đọc qua backend, không lộ object key. */
public record AnhHienThi(UUID id, int thuTu, String duongDanDoc) {

    public static AnhHienThi tu(UUID id, int thuTu) {
        return new AnhHienThi(id, thuTu, "/api/images/" + id + "/content");
    }
}
