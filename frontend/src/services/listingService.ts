import api from "@/services/api";
import type {
  CreateListingPayload,
  CreateListingResult,
  ImageUploadResult,
  LoaiBdsApi,
} from "@/types/listing";

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
 * Service xử lý Nghiệp vụ Đăng tin, Chỉnh sửa tin và Quản lý tải ảnh (FR-01, FR-02 / UC-06, UC-07)
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
    const key =
      idempotencyKey ||
      (typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`);

    const response = await api.post<CreateListingResult>("/listings", payload, {
      headers: {
        "Idempotency-Key": key,
      },
    });

    return response.data;
  },

  /**
   * Cập nhật và gửi duyệt lại tin đăng (PUT /api/listings/{id})
   * (Sẵn sàng nối khi backend hoàn tất endpoint PUT)
   */
  async updateListing(
    id: string | number,
    payload: CreateListingPayload
  ): Promise<CreateListingResult> {
    const response = await api.put<CreateListingResult>(`/listings/${id}`, payload);
    return response.data;
  },
};
