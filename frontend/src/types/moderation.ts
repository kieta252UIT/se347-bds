import type { GiaQuyDoiHienThiDto } from "@/utils/priceFormatter";

export interface AnhHienThiDto {
  id: string;
  thuTu: number;
  duongDanDoc: string;
}

export interface PhienBanNoiDungDto {
  id: string;
  soPhienBan: number;
  tieuDe: string;
  moTa: string;
  loaiGiaoDich: "ban" | "cho_thue";
  loaiBds: "can_ho" | "nha_rieng" | "dat" | "phong_tro" | "khac";
  giaGoc: string;
  donViGia: string;
  dienTichM2: string;
  diaChi: string;
  khuVuc: string;
  viDo: string;
  kinhDo: string;
  anh: AnhHienThiDto[];
  taoLuc: string;
  giaQuyDoiHienThi: GiaQuyDoiHienThiDto | null;
}

export type TrangThaiTinApi =
  | "cho_kiem_duyet"
  | "da_duyet"
  | "bi_tu_choi"
  | "da_xoa"
  | "bi_go_vi_pham";

export interface TinQuanLyDto {
  id: string;
  trangThai: TrangThaiTinApi;
  ngayDang: string;
  phienBanCapNhat: number;
  phienBanHienTaiId: string;
  phienBanDaDuyetId: string | null;
  noiDung: PhienBanNoiDungDto;
  ketQuaSoBo: string;
  lyDoTuChoi: string | null;
  lyDoNgungHienThi: string | null;
}

export interface TrangTinQuanLyDto {
  noiDung: TinQuanLyDto[];
  trang: number;
  kichThuoc: number;
  tongSo: number;
}

export interface QuyetDinhKiemDuyetPayload {
  phienBanDaXemId: string;
  quyetDinh: "phe_duyet" | "tu_choi";
  lyDo?: string;
}

export interface TinGhiKetQuaDto {
  tinId: string;
  phienBanHienTaiId: string;
  trangThai: TrangThaiTinApi;
  phienBanCapNhat: number;
}
