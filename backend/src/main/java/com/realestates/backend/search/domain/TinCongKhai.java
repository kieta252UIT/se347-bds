package com.realestates.backend.search.domain;

/**
 * BR-01 dùng chung cho mọi đường đọc công khai (tìm kiếm, chi tiết, yêu thích): tin da_duyet của chủ hoat_dong,
 * nội dung luôn là phiên bản đã duyệt. Alias cố định: t = tin, chu = tài khoản chủ, p = phiên bản đã duyệt.
 */
public final class TinCongKhai {

    public static final String TU_BANG = """
            se214.tin_bat_dong_san t
            JOIN se214.tai_khoan chu ON chu.id = t.chu_id
            JOIN se214.phien_ban_tin p ON p.id = t.phien_ban_da_duyet_id AND p.tin_id = t.id""";

    public static final String DIEU_KIEN = "t.trang_thai = 'da_duyet' AND chu.trang_thai = 'hoat_dong'";

    private TinCongKhai() {
    }
}
