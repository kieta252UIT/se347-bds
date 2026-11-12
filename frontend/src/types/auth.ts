/**
 * Định nghĩa vai trò và trạng thái tài khoản theo SRS 3.1 & du-lieu.md
 */

export type UserRole = 'KHACH' | 'NGUOI_DUNG' | 'KDV' | 'ADMIN';

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
