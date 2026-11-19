/**
 * Định nghĩa kiểu dữ liệu cho Nghiệp vụ Đăng tin và Quản lý ảnh (FR-01, FR-02 / UC-06, UC-07)
 */

export type LoaiGiaoDichApi = "ban" | "cho_thue";

export type LoaiBdsApi = "can_ho" | "nha_rieng" | "dat" | "phong_tro" | "khac";

export type DonViGiaApi =
  | "vnd_tong"
  | "vnd_m2"
  | "vnd_ngay"
  | "vnd_thang"
  | "vnd_nam"
  | "vnd_m2_ngay"
  | "vnd_m2_thang";

/**
 * Payload tạo tin đăng gửi lên POST /api/listings
 * Phải chứa đúng 12 trường cho phép của backend domain (KiemTraNoiDungTin)
 */
export interface CreateListingPayload {
  tieuDe: string;
  moTa: string;
  loaiGiaoDich: LoaiGiaoDichApi;
  loaiBds: LoaiBdsApi;
  giaGoc: string;
  donViGia: DonViGiaApi;
  dienTichM2: string;
  diaChi: string;
  khuVuc: string;
  viDo: string;
  kinhDo: string;
  anhIds: string[];
}

/**
 * Kết quả tạo tin đăng trả về từ backend (TinGhiKetQua)
 */
export interface CreateListingResult {
  tinId: string;
  phienBanHienTaiId: string;
  trangThai: string;
  phienBanCapNhat: number;
}

/**
 * Kết quả tải ảnh tạm từ POST /api/images (AnhTamKetQua)
 */
export interface ImageUploadResult {
  id: string; // UUID
  trangThai: string; // "tam"
  mimeType: string;
  kichThuocByte: number;
  duongDanDoc: string; // "/api/images/{id}/content"
}

/**
 * Trạng thái của một ảnh trong danh sách ảnh của Form đăng tin
 */
export interface ListingFormImageItem {
  clientId: string;
  file?: File;
  previewUrl: string;
  backendId?: string; // UUID từ backend
  isUploading?: boolean;
  error?: string;
}
