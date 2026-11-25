package com.realestates.backend.search.dto;

/** Schema GiaQuyDoiHienThi; giaTri đúng 2 chữ số thập phân. */
public record GiaQuyDoiHienThi(String giaTri, String donVi, String chuThich) {

    public static final String CHU_THICH = "Giá quy đổi tham khảo; không dùng để lọc.";
}
