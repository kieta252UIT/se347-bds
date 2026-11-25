import api from "@/services/api";
import { resolveLocation } from "@/utils/locationResolver";
import type {
  CreateListingPayload,
  CreateListingResult,
  ImageUploadResult,
  LoaiBdsApi,
  MyListingItem,
  ServerListingResponse,
  UpdateListingPayload,
} from "@/types/listing";

const STORAGE_KEY_MY_LISTINGS = "bds_user_created_listings";

function formatETag(val?: string | number): string {
  if (val === undefined || val === null || val === "") return '"0"';
  const str = String(val).trim();
  return str.startsWith('"') && str.endsWith('"') ? str : `"${str}"`;
}

function generateIdempotencyKey(): string {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

/**
 * Ánh xạ hiển thị thân thiện trên UI sang 5 giá trị enum miền nghiệp vụ của backend (LoaiBds)
 * can_ho, nha_rieng, dat, phong_tro, khac
 */
export function mapPropertyTypeToApi(type: string): LoaiBdsApi {
  switch (type) {
    case "Căn hộ chung cư":
      return "can_ho";
    case "Nhà riêng":
      return "nha_rieng";
    case "Đất nền":
      return "dat";
    case "Phòng trọ":
      return "phong_tro";
    case "Khác":
    default:
      return "khac";
  }
}

/**
 * Ánh xạ ngược từ enum backend sang tên tiếng Việt hiển thị
 */
export function mapApiToPropertyType(apiType?: string): string {
  switch (apiType) {
    case "can_ho":
      return "Căn hộ chung cư";
    case "nha_rieng":
      return "Nhà riêng";
    case "dat":
      return "Đất nền";
    case "phong_tro":
      return "Phòng trọ";
    case "khac":
    default:
      return "Khác";
  }
}

/**
 * Service xử lý Nghiệp vụ Đăng tin, Chỉnh sửa tin, Xóa tin và Quản lý ảnh (FR-01, FR-02 / UC-06, UC-07)
 */
export const listingService = {
  /**
   * Tải 1 tệp ảnh tạm lên backend (POST /api/images)
   * Yêu cầu trường dữ liệu multipart duy nhất là "tep" (AnhTinService.TRUONG_TEP).
   */
  async uploadImage(file: File): Promise<ImageUploadResult> {
    const formData = new FormData();
    formData.append("tep", file);

    const response = await api.post<ImageUploadResult>("/images", formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });

    return response.data;
  },

  /**
   * Tạo tin đăng mới và gửi vào hàng chờ duyệt (POST /api/listings)
   * Yêu cầu header Idempotency-Key để chống gửi trùng lặp (Idempotency check).
   */
  async createListing(
    payload: CreateListingPayload,
    idempotencyKey?: string
  ): Promise<CreateListingResult> {
    const key = idempotencyKey || generateIdempotencyKey();

    const response = await api.post<CreateListingResult>("/listings", payload, {
      headers: {
        "Idempotency-Key": key,
      },
    });

    return response.data;
  },

  /**
   * Cập nhật và gửi duyệt lại tin đăng (PUT /api/listings/{listingId})
   * Yêu cầu header If-Match định dạng ETag mạnh ("{phienBanCapNhat}"), Idempotency-Key
   * và payload chứa 12 trường nội dung + phienBanDaXemId (UUID) theo QuanLyTinService (PR #18).
   */
  async updateListing(
    id: string | number,
    payload: UpdateListingPayload,
    options?: {
      ifMatch?: string | number;
      idempotencyKey?: string;
    }
  ): Promise<CreateListingResult> {
    const ifMatchHeader = formatETag(options?.ifMatch ?? 0);
    const key = options?.idempotencyKey || generateIdempotencyKey();

    const response = await api.put<CreateListingResult>(`/listings/${id}`, payload, {
      headers: {
        "If-Match": ifMatchHeader,
        "Idempotency-Key": key,
      },
    });

    return response.data;
  },

  /**
   * Xóa tin đăng nguyên tử cùng kết thúc gói và đóng báo cáo chờ (DELETE /api/listings/{listingId})
   * Yêu cầu header If-Match định dạng ETag mạnh ("{phienBanCapNhat}"), Idempotency-Key
   */
  async deleteListing(
    id: string | number,
    options?: {
      ifMatch?: string | number;
      idempotencyKey?: string;
    }
  ): Promise<CreateListingResult> {
    const ifMatchHeader = formatETag(options?.ifMatch ?? 0);
    const key = options?.idempotencyKey || generateIdempotencyKey();

    const response = await api.delete<CreateListingResult>(`/listings/${id}`, {
      headers: {
        "If-Match": ifMatchHeader,
        "Idempotency-Key": key,
      },
    });

    return response.data;
  },

  /**
   * Dọn sạch dữ liệu cũ từng lưu trong localStorage (không còn sử dụng localStorage cho danh sách tin)
   */
  getUserCreatedListings(): MyListingItem[] {
    try {
      localStorage.removeItem(STORAGE_KEY_MY_LISTINGS);
    } catch {
      // bỏ qua
    }
    return [];
  },

  saveUserListing(item: MyListingItem): void {
    void item;
    try {
      localStorage.removeItem(STORAGE_KEY_MY_LISTINGS);
    } catch {
      // bỏ qua
    }
  },

  removeUserListing(id: string | number): void {
    void id;
    try {
      localStorage.removeItem(STORAGE_KEY_MY_LISTINGS);
    } catch {
      // bỏ qua
    }
  },

  updateUserListingStatus(id: string | number, status: MyListingItem["status"]): void {
    void id;
    void status;
    try {
      localStorage.removeItem(STORAGE_KEY_MY_LISTINGS);
    } catch {
      // bỏ qua
    }
  },

  getMyListings(fallbackMock: MyListingItem[] = []): MyListingItem[] {
    this.getUserCreatedListings();
    return fallbackMock;
  },

  /**
   * Lấy danh sách tin của người dùng đăng nhập từ backend (GET /api/me/listings)
   * Tự động chuyển đổi dữ liệu và đồng bộ vào giao diện
   */
  async fetchMyListings(): Promise<MyListingItem[]> {
    try {
      const response = await api.get<ServerListingResponse[]>("/me/listings");
      if (Array.isArray(response.data)) {
        const serverItems: MyListingItem[] = response.data.map((item) => {
          let status: MyListingItem["status"] = "pending";
          if (item.trangThai === "da_duyet") status = "approved";
          else if (item.trangThai === "bi_tu_choi") status = "rejected";
          else if (item.trangThai === "da_xoa") status = "deleted";
          else if (item.trangThai === "bi_go_vi_pham") status = "violation";
          else if (item.trangThai === "an_tin") status = "hidden";
          else if (item.trangThai === "da_ban") status = "sold";
          else status = "pending";

          const rawGia = parseFloat(String(item.giaGoc ?? "0")) || 0;
          let priceFormatted = `${rawGia.toLocaleString("vi-VN")} VNĐ`;
          if (item.loaiGiaoDich === "ban") {
            if (rawGia >= 1_000_000_000) {
              priceFormatted = `${(rawGia / 1_000_000_000).toFixed(1).replace(/\.0$/, "")} tỷ`;
            } else if (rawGia >= 1_000_000) {
              priceFormatted = `${(rawGia / 1_000_000).toFixed(1).replace(/\.0$/, "")} triệu`;
            }
          } else {
            const donVi = item.donViGia === "vnd_nam" ? "/năm" : "/tháng";
            if (rawGia >= 1_000_000_000) {
              priceFormatted = `${(rawGia / 1_000_000_000).toFixed(1).replace(/\.0$/, "")} tỷ${donVi}`;
            } else if (rawGia >= 1_000_000) {
              priceFormatted = `${(rawGia / 1_000_000).toFixed(1).replace(/\.0$/, "")} triệu${donVi}`;
            } else {
              priceFormatted = `${rawGia.toLocaleString("vi-VN")} VNĐ${donVi}`;
            }
          }

          const photoCount = item.anhIds?.length || (item.anhDaiDienUrl ? 1 : 0);
          const imageUrl =
            item.anhDaiDienUrl ||
            "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=420&q=82";

          const loc = resolveLocation({
            khuVuc: item.khuVuc,
            diaChi: item.diaChi,
            address: item.diaChi || item.khuVuc || "",
          });

          return {
            id: String(item.id),
            title: item.tieuDe || "Tin đăng bất động sản",
            price: priceFormatted,
            priceVnd: rawGia,
            area: parseFloat(String(item.dienTichM2 ?? "0")) || 0,
            address: item.diaChi || item.khuVuc || "",
            wardId: loc.wardId,
            city: loc.provinceName,
            ward: loc.wardName,
            date: item.ngayDang
              ? new Date(item.ngayDang).toLocaleDateString("vi-VN")
              : new Date().toLocaleDateString("vi-VN"),
            photos: photoCount,
            image: imageUrl,
            status,
            rejectionReason: item.lyDoTuChoi || undefined,
            phienBanHienTaiId: item.phienBanHienTaiId,
            phienBanCapNhat: item.phienBanCapNhat ?? 0,
            rawPayload: {
              tieuDe: item.tieuDe,
              moTa: item.moTa,
              loaiGiaoDich: item.loaiGiaoDich,
              loaiBds: item.loaiBds,
              giaGoc: String(item.giaGoc),
              donViGia: item.donViGia,
              dienTichM2: String(item.dienTichM2),
              diaChi: item.diaChi,
              khuVuc: item.khuVuc,
              viDo: String(item.viDo),
              kinhDo: String(item.kinhDo),
              anhIds: item.anhIds || [],
            },
          };
        });

        return serverItems.filter((x) => x.status !== "deleted");
      }
      return [];
    } catch {
      return [];
    }
  },
};

