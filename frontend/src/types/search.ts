import type {
  DonViGia,
  GiaQuyDoiHienThiDto,
  LoaiBds,
  LoaiGiaoDich,
} from "@/utils/priceFormatter";
import type { AnhHienThiDto, PhienBanNoiDungDto } from "@/types/moderation";

export type CheDoGia = "tong" | "m2";
export type SapXepTimKiem = "mac_dinh" | "moi_nhat";

export interface TinTomTatDto {
  id: string;
  phienBanId?: string;
  tieuDe: string;
  loaiGiaoDich: LoaiGiaoDich;
  loaiBds: LoaiBds;
  giaGoc: string;
  donViGia: DonViGia;
  giaQuyDoiHienThi?: GiaQuyDoiHienThiDto | null;
  dienTichM2: string;
  khuVuc: string;
  viDo: string | number;
  kinhDo: string | number;
  anhDaiDien?: AnhHienThiDto | null;
  anhDaiDienUrl?: string | null;
  laTaiTro?: boolean;
  ngayDang: string;
}

export interface TrangTinCongKhaiDto {
  noiDung: TinTomTatDto[];
  trang: number;
  kichThuoc: number;
  tongSo: number;
}

export interface KetQuaTimKiemDto {
  tuNhien: TrangTinCongKhaiDto;
  taiTro: TinTomTatDto[];
  thoiDiemTruyVan: string;
}

export interface LienHeChuTinDto {
  hoTen: string | null;
  soDienThoai: string | null;
}

export interface TinCongKhaiDto {
  id: string;
  trangThai: "da_duyet";
  ngayDang: string;
  noiDung: PhienBanNoiDungDto;
  lienHe: LienHeChuTinDto;
}

export interface SearchQueryParams {
  tuKhoa?: string;
  loaiGiaoDich?: LoaiGiaoDich;
  loaiBds?: LoaiBds;
  khuVuc?: string;
  cheDoGia?: CheDoGia;
  giaMin?: string;
  giaMax?: string;
  dienTichMin?: string;
  dienTichMax?: string;
  nam?: number;
  bac?: number;
  tay?: number;
  dong?: number;
  sapXep?: SapXepTimKiem;
  trang?: number;
  kichThuoc?: number;
}
