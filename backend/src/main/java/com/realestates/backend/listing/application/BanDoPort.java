package com.realestates.backend.listing.application;

import java.math.BigDecimal;

public interface BanDoPort {

    boolean xacMinhToaDo(BigDecimal viDo, BigDecimal kinhDo, String diaChi, String khuVuc);
}

