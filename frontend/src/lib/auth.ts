import { authService } from "@/services/authService";
import type { GuiEmailKetQua, DangNhapKetQua, XacMinhKetQua } from "@/types/auth";

/**
 * Interface authApi tương thích với mã nguồn component của người dùng
 */
export const authApi = {
  /**
   * Đăng nhập với identifier (email hoặc sđt) và mật khẩu
   */
  async login(identifier: string, matKhau: string): Promise<DangNhapKetQua> {
    const isEmail = identifier.includes("@");
    return authService.login({
      email: isEmail ? identifier.trim() : undefined,
      soDienThoai: !isEmail ? identifier.trim() : undefined,
      matKhau,
    });
  },

  /**
   * Đăng ký tài khoản người dùng
   */
  async register(
    hoTen: string,
    email: string,
    matKhau: string,
    soDienThoai?: string
  ): Promise<{ session?: unknown; result: GuiEmailKetQua }> {
    const result = await authService.register({
      hoTen: hoTen.trim(),
      email: email.trim().toLowerCase(),
      matKhau,
      soDienThoai: soDienThoai?.trim() || undefined,
    });
    return { result };
  },

  /**
   * Gửi OTP qua backend proxy
   */
  async sendOtp(phone: string): Promise<void> {
    // Dự phòng OTP theo form UI
    await authService.resendVerification({ email: phone });
  },

  /**
   * Xác thực OTP
   */
  async verifyOtp(phone: string, otp: string): Promise<XacMinhKetQua> {
    return authService.verifyEmail({ email: phone, otp });
  },

  /**
   * Xác minh email hoàn tất
   */
  async verifyEmail(tokenHash?: string, otp?: string, email?: string): Promise<XacMinhKetQua> {
    return authService.verifyEmail({ tokenHash, otp, email });
  },

  /**
   * Yêu cầu gửi lại email xác minh
   */
  async resendVerification(email: string): Promise<GuiEmailKetQua> {
    return authService.resendVerification({ email: email.trim().toLowerCase() });
  },

  /**
   * Đăng nhập mạng xã hội OAuth
   */
  async startOAuth(provider: "google" | "facebook"): Promise<void> {
    window.location.href = `/api/auth/oauth/${provider}`;
  },
};
