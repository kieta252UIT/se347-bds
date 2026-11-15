/**
 * Định nghĩa vai trò và trạng thái tài khoản theo SRS 3.1, du-lieu.md & backend DTO
 */

export type UserRole =
  | 'KHACH'
  | 'NGUOI_DUNG'
  | 'KDV'
  | 'ADMIN'
  | 'nguoi_dung'
  | 'kiem_duyet_vien'
  | 'quan_tri_vien';

export type AccountStatus = 'hoat_dong' | 'bi_khoa';

export interface UserProfile {
  id: string;
  email: string;
  hoTen?: string;
  soDienThoai?: string;
  vaiTro: UserRole;
  trangThai: AccountStatus;
  emailDaXacMinh: boolean;
}

export interface AuthState {
  user: UserProfile | null;
  role: UserRole;
  isAuthenticated: boolean;
  isLoading: boolean;
}

// Payload DTOs theo hợp đồng OpenAPI Sprint 1
export interface RegisterPayload {
  email: string;
  matKhau: string;
  hoTen: string;
  soDienThoai?: string;
}

export interface LoginPayload {
  email?: string;
  matKhau: string;
  soDienThoai?: string;
}

export interface VerifyEmailPayload {
  tokenHash?: string;
  otp?: string;
  maXacMinh?: string;
  token?: string;
  email?: string;
}

export interface ResendVerificationPayload {
  email: string;
}

// Payload DTOs quên mật khẩu (OTP email)
export interface QuenMatKhauPayload {
  email: string;
}

export interface DatLaiMatKhauPayload {
  email: string;
  otp: string;
  matKhauMoi: string;
}

// Response DTOs
export interface GuiEmailKetQua {
  trangThai: string;
  thongDiep: string;
}

export interface XacMinhKetQua {
  thanhCong: boolean;
  thongDiep: string;
  csrfToken?: string;
  canDangNhapLai?: boolean;
}

export interface DangNhapKetQua {
  taiKhoan: UserProfile;
  csrfToken?: string;
}
