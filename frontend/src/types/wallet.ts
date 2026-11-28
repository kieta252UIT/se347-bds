export interface GiaoDichViItemDto {
  id: string;
  loai: string;
  trangThai: string;
  chieuBienDong: "cong" | "tru" | string;
  soTien: number;
  soDuSau: number;
  thoiDiemGhiNhan: string;
  ghiChu?: string | null;
}

export interface ViDienTuResponse {
  viId: string;
  taiKhoanId: string;
  soDuVnd: number;
  capNhatLuc: string;
  giaoDichGanDay: GiaoDichViItemDto[];
}

export interface GoiHienTaiDto {
  coGoi: boolean;
  trangThai: string | null;
  hetHanLuc: string | null;
  thoiLuongConLaiGiay: number | null;
}

export interface GoiHoTroDto {
  maGoi: string;
  tenGoi: string;
  giaVnd: number;
  thoiLuongNgay: number;
}

export interface ThongTinMuaGoiResponse {
  tinId: string;
  trangThaiTin?: string;
  duDieuKienMua?: boolean;
  canXacNhanCongDon?: boolean;
  soDuHienTaiVnd: number;
  goiHienTai: GoiHienTaiDto;
  cacGoiHoTro: GoiHoTroDto[];
}

export interface YeuCauMuaGoiRequest {
  maGoi: string;
  maYDinh?: string;
  dongYCongDon: boolean;
}

export interface ThongTinGoiKetQua {
  id: string;
  trangThai: string;
  hetHanLuc: string | null;
  thoiLuongConLaiGiay?: number | null;
}

export interface KetQuaMuaGoiResponse {
  thanhCong: boolean;
  thongDiep: string;
  maGiaoDich: string;
  lanMuaId?: string | null;
  soDuConLaiVnd: number;
  goi: ThongTinGoiKetQua;
}
