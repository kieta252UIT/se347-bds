import api from "@/services/api";
import type {
  KetQuaTimKiemDto,
  SearchQueryParams,
  TinCongKhaiDto,
  TinTomTatDto,
} from "@/types/search";
import type { PropertyItem } from "@/components/property/PropertyCard";
import {
  formatDienTichM2,
  formatGiaGocVaDonVi,
  formatGiaQuyDoi,
  formatLoaiBdsLabel,
  formatLoaiGiaoDichLabel,
} from "@/utils/priceFormatter";

const DEFAULT_FALLBACK_IMAGE =
  "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=900&q=82";

/**
 * Chuyển đổi DTO TinTomTat từ Backend sang cấu trúc hiển thị trên PropertyCard
 * Giữ nguyên cả giá gốc + đơn vị gốc (TK-06) và giá quy đổi tham chiếu (TK-07).
 */
export function mapTinTomTatToPropertyItem(dto: TinTomTatDto): PropertyItem {
  const originalPrice = formatGiaGocVaDonVi(dto.giaGoc, dto.donViGia);
  const convertedText = formatGiaQuyDoi(dto.giaQuyDoiHienThi);
  const imageUrl =
    dto.anhDaiDien?.duongDanDoc || dto.anhDaiDienUrl || DEFAULT_FALLBACK_IMAGE;

  return {
    id: dto.id,
    title: dto.tieuDe,
    price: originalPrice,
    convertedPrice: convertedText,
    priceValue: Number(dto.giaGoc) || 0,
    place: dto.khuVuc,
    size: formatDienTichM2(dto.dienTichM2),
    image: imageUrl,
    type: formatLoaiBdsLabel(dto.loaiBds),
    transaction: formatLoaiGiaoDichLabel(dto.loaiGiaoDich),
    tag: dto.laTaiTro
      ? "Được tài trợ"
      : formatLoaiGiaoDichLabel(dto.loaiGiaoDich),
    lat: Number(dto.viDo) || undefined,
    lng: Number(dto.kinhDo) || undefined,
    postedAt: dto.ngayDang,
  };
}

/**
 * Xây dựng query params tuân thủ nghiêm ngặt CAC_THAM_SO của TimKiemTinService:
 * - Chỉ đính kèm các tham số có giá trị khác rỗng.
 * - Chỉ gửi giaMin / giaMax / cheDoGia khi đã có loaiGiaoDich (TK-03).
 * - Chỉ gửi nam, bac, tay, dong khi đủ cả 4 tọa độ khung bản đồ.
 */
export function buildSearchUrlParams(
  params: SearchQueryParams
): URLSearchParams {
  const searchParams = new URLSearchParams();

  if (params.tuKhoa && params.tuKhoa.trim()) {
    searchParams.set("tuKhoa", params.tuKhoa.trim());
  }
  if (params.loaiGiaoDich) {
    searchParams.set("loaiGiaoDich", params.loaiGiaoDich);
  }
  if (params.loaiBds) {
    searchParams.set("loaiBds", params.loaiBds);
  }
  if (params.khuVuc && params.khuVuc.trim()) {
    searchParams.set("khuVuc", params.khuVuc.trim());
  }

  // TK-03: Bộ lọc giá chỉ gửi khi đã chọn loại giao dịch (ban | cho_thue)
  const hasPriceMin = Boolean(params.giaMin && params.giaMin.trim());
  const hasPriceMax = Boolean(params.giaMax && params.giaMax.trim());
  if (params.loaiGiaoDich && (hasPriceMin || hasPriceMax)) {
    if (params.cheDoGia) {
      searchParams.set("cheDoGia", params.cheDoGia);
    }
    if (hasPriceMin) {
      searchParams.set("giaMin", params.giaMin!.trim());
    }
    if (hasPriceMax) {
      searchParams.set("giaMax", params.giaMax!.trim());
    }
  }

  if (params.dienTichMin && params.dienTichMin.trim()) {
    searchParams.set("dienTichMin", params.dienTichMin.trim());
  }
  if (params.dienTichMax && params.dienTichMax.trim()) {
    searchParams.set("dienTichMax", params.dienTichMax.trim());
  }

  const hasAllBounds =
    params.nam !== undefined &&
    params.bac !== undefined &&
    params.tay !== undefined &&
    params.dong !== undefined;
  if (hasAllBounds) {
    searchParams.set("nam", String(params.nam));
    searchParams.set("bac", String(params.bac));
    searchParams.set("tay", String(params.tay));
    searchParams.set("dong", String(params.dong));
  }

  if (params.sapXep) {
    searchParams.set("sapXep", params.sapXep);
  }
  if (params.trang !== undefined) {
    searchParams.set("trang", String(params.trang));
  }
  if (params.kichThuoc !== undefined) {
    searchParams.set("kichThuoc", String(params.kichThuoc));
  }

  return searchParams;
}

export const searchService = {
  /**
   * GET /api/listings
   * Tìm kiếm công khai trả về hai vùng độc lập: taiTro (tối đa 5) và tuNhien (phân trang).
   */
  async searchListings(
    params: SearchQueryParams = {},
    signal?: AbortSignal
  ): Promise<KetQuaTimKiemDto> {
    const query = buildSearchUrlParams(params).toString();
    const url = query ? `/listings?${query}` : "/listings";
    const response = await api.get<KetQuaTimKiemDto>(url, { signal });
    return response.data;
  },

  /**
   * GET /api/listings/{listingId}
   * Lấy chi tiết tin đăng công khai kèm thông tin liên hệ chủ tin.
   */
  async getPublicListingDetail(listingId: string): Promise<TinCongKhaiDto> {
    const response = await api.get<TinCongKhaiDto>(
      `/listings/${encodeURIComponent(listingId)}`
    );
    return response.data;
  },
};
