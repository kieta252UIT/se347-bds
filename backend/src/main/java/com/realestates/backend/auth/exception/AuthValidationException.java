package com.realestates.backend.auth.exception;

import com.realestates.backend.auth.dto.FieldErrorItem;
import java.util.List;

public class AuthValidationException extends RuntimeException {
    private final String maLoi;
    private final List<FieldErrorItem> truongLoi;

    public AuthValidationException(String maLoi, String thongDiep, List<FieldErrorItem> truongLoi) {
        super(thongDiep);
        this.maLoi = maLoi;
        this.truongLoi = truongLoi != null ? List.copyOf(truongLoi) : List.of();
    }

    public String getMaLoi() {
        return maLoi;
    }

    public List<FieldErrorItem> getTruongLoi() {
        return truongLoi;
    }
}
