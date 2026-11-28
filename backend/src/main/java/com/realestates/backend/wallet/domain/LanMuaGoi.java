package com.realestates.backend.wallet.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LanMuaGoi(
        UUID id,
        UUID goiId,
        UUID giaoDichViId,
        BigDecimal giaDaMua,
        Duration thoiLuongDaCap,
        Instant thoiDiemMua
) {
    public LanMuaGoi {
        Objects.requireNonNull(id, "id không được null");
        Objects.requireNonNull(goiId, "goiId không được null");
        Objects.requireNonNull(giaoDichViId, "giaoDichViId không được null");
        Objects.requireNonNull(giaDaMua, "giaDaMua không được null");
        Objects.requireNonNull(thoiLuongDaCap, "thoiLuongDaCap không được null");
        Objects.requireNonNull(thoiDiemMua, "thoiDiemMua không được null");
    }

    public static LanMuaGoi taoMoi(
            UUID goiId,
            UUID giaoDichViId,
            BigDecimal giaDaMua,
            Duration thoiLuongDaCap,
            Instant now
    ) {
        return new LanMuaGoi(
                UUID.randomUUID(),
                goiId,
                giaoDichViId,
                giaDaMua,
                thoiLuongDaCap,
                now != null ? now : Instant.now()
        );
    }
}
