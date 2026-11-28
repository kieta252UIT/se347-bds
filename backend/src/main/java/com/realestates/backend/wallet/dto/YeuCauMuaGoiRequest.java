package com.realestates.backend.wallet.dto;

public record YeuCauMuaGoiRequest(
        String maGoi,
        String maYDinh,
        Boolean dongYCongDon
) {
    public boolean isDongYCongDon() {
        return dongYCongDon != null && dongYCongDon;
    }
}
