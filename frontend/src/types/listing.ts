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
 * Payload sửa tin đăng gửi lên PUT /api/listings/{listingId}
 * Gồm đúng 12 trường nội dung + trường phienBanDaXemId (UUID) theo QuanLyTinService
 */
export interface UpdateListingPayload extends CreateListingPayload {
  phienBanDaXemId: string;
}

/**
 * Các trạng thái tin đăng trong hệ thống (khớp với domain backend TinDangRepository)
 */
export type ListingStatus =
  | "approved" // da_duyet
  | "pending" // cho_kiem_duyet
  | "rejected" // bi_tu_choi
  | "violation" // bi_go_vi_pham
  | "hidden" // an_tin
  | "sold" // da_ban
  | "deleted"; // da_xoa

/**
 * Cấu trúc thông tin một tin đăng trong danh sách quản lý "Tin của tôi"
 */
export interface MyListingItem {
  id: string; // UUID hoặc id định danh
  title: string;
  price: string;
  priceVnd?: number;
  area: number;
  address: string;
  wardId?: number;
  city?: string;
  ward?: string;
  date: string;
  photos: number;
  image: string;
  status: ListingStatus;
  rejectionReason?: string;
  phienBanHienTaiId?: string; // UUID phiên bản hiện tại
  phienBanCapNhat?: number; // Số phiên bản (phục vụ If-Match)
  rawPayload?: Partial<CreateListingPayload>;
}

/**
 * Cấu trúc dữ liệu tin trả về từ GET /api/me/listings
 */
export interface ServerListingResponse {
  id: string;
  tieuDe?: string;
  moTa?: string;
  loaiGiaoDich?: LoaiGiaoDichApi;
  loaiBds?: LoaiBdsApi;
  giaGoc?: string | number;
  donViGia?: DonViGiaApi;
  dienTichM2?: string | number;
  diaChi?: string;
  khuVuc?: string;
  viDo?: string | number;
  kinhDo?: string | number;
  trangThai?: string;
  lyDoTuChoi?: string;
  phienBanHienTaiId?: string;
  soPhienBan?: number;
  phienBanCapNhat?: number;
  ngayDang?: string;
  anhIds?: string[];
  anhDaiDienUrl?: string;
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
