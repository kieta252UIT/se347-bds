package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.listing.application.BanDoPort;
import com.realestates.backend.listing.exception.MapServiceUnavailableException;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter bản đồ mặc định cho giai đoạn chưa chọn nhà cung cấp bản đồ thương mại (OPEN-01..06 / OpenAPI dangTin).
 * Chế độ sandbox kiểm tra tọa độ hợp lệ và địa chỉ/khu vực không rỗng cục bộ, không đại diện cho geocoding thật.
 */
@Component
public class MacDinhBanDoAdapter implements BanDoPort {

    private static final BigDecimal VI_DO_MIN = new BigDecimal("-90");
    private static final BigDecimal VI_DO_MAX = new BigDecimal("90");
    private static final BigDecimal KINH_DO_MIN = new BigDecimal("-180");
    private static final BigDecimal KINH_DO_MAX = new BigDecimal("180");

    private final String mode;

    @Autowired
    public MacDinhBanDoAdapter(@Value("${app.map.mode:sandbox}") String mode) {
        this.mode = mode == null ? "sandbox" : mode.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean xacMinhToaDo(BigDecimal viDo, BigDecimal kinhDo, String diaChi, String khuVuc) {
        if ("unconfigured".equals(mode)) {
            throw new MapServiceUnavailableException(
                    MapServiceUnavailableException.MA_LOI_BAN_DO_CHUA_SAN_SANG,
                    "Dịch vụ xác minh bản đồ chưa được cấu hình."
            );
        }
        if ("unavailable".equals(mode)) {
            throw new MapServiceUnavailableException(
                    MapServiceUnavailableException.MA_LOI_TAM_GIAN_DOAN,
                    "Dịch vụ bản đồ đang tạm gián đoạn. Vui lòng thử lại sau."
            );
        }
        if ("fail".equals(mode)) {
            return false;
        }
        if (viDo == null || kinhDo == null || diaChi == null || diaChi.isBlank() || khuVuc == null || khuVuc.isBlank()) {
            return false;
        }
        return viDo.compareTo(VI_DO_MIN) >= 0 && viDo.compareTo(VI_DO_MAX) <= 0
                && kinhDo.compareTo(KINH_DO_MIN) >= 0 && kinhDo.compareTo(KINH_DO_MAX) <= 0;
    }
}
