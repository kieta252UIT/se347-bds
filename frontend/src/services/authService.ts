import api from "@/services/api";
import type {
  RegisterPayload,
  LoginPayload,
  VerifyEmailPayload,
  ResendVerificationPayload,
  QuenMatKhauPayload,
  DatLaiMatKhauPayload,
  GuiEmailKetQua,
  XacMinhKetQua,
  DatLaiKetQua,
  DangNhapKetQua,
} from "@/types/auth";

/**
 * Service tập trung xử lý các API Xác thực và Phiên (FR-15 / UC-25, UC-26, UC-27)
 */
export const authService = {
  /**
   * Đăng ký tài khoản Người dùng công khai (UC-25 / POST /api/auth/register)
   */
  async register(payload: RegisterPayload): Promise<GuiEmailKetQua> {
    const response = await api.post<GuiEmailKetQua>("/auth/register", payload);
    return response.data;
  },

  /**
   * Đăng nhập tạo phiên opaque và nhận cookie HttpOnly (UC-27 / POST /api/auth/login)
   */
  async login(payload: LoginPayload): Promise<DangNhapKetQua> {
    const response = await api.post<DangNhapKetQua>("/auth/login", payload);
    return response.data;
  },

  /**
   * Hoàn tất xác minh email qua tokenHash từ link hoặc mã OTP (UC-26 / POST /api/auth/email/verify)
   */
  async verifyEmail(payload: VerifyEmailPayload): Promise<XacMinhKetQua> {
    const response = await api.post<XacMinhKetQua>("/auth/email/verify", payload);
    return response.data;
  },

  /**
   * Yêu cầu gửi lại liên kết / email xác minh (UC-26 / POST /api/auth/email/verification-requests)
   */
  async resendVerification(payload: ResendVerificationPayload): Promise<GuiEmailKetQua> {
    const response = await api.post<GuiEmailKetQua>("/auth/email/verification-requests", payload);
    return response.data;
  },

  /**
   * Đăng xuất thu hồi phiên (UC-28 / POST /api/auth/logout)
   */
  async logout(): Promise<void> {
    await api.post("/auth/logout");
  },

  /**
   * Yêu cầu gửi OTP đặt lại mật khẩu qua email (POST /api/auth/password/reset-requests)
   */
  async requestPasswordReset(payload: QuenMatKhauPayload): Promise<GuiEmailKetQua> {
    const response = await api.post<GuiEmailKetQua>("/auth/password/reset-requests", payload);
    return response.data;
  },

  /**
   * Xác nhận OTP 6 chữ số hoặc tokenHash và đặt lại mật khẩu mới (POST /api/auth/password/reset)
   */
  async resetPassword(payload: DatLaiMatKhauPayload): Promise<DatLaiKetQua> {
    const trimmedOtp = payload.otp?.trim();
    const trimmedTokenHash = payload.tokenHash?.trim() || trimmedOtp || "";
    const body: Record<string, string> = {
      tokenHash: trimmedTokenHash,
      matKhauMoi: payload.matKhauMoi,
    };
    if (trimmedOtp) {
      body.otp = trimmedOtp;
    }
    if (payload.email && payload.email.trim()) {
      body.email = payload.email.trim().toLowerCase();
    }
    const response = await api.post<DatLaiKetQua>("/auth/password/reset", body);
    return response.data;
  },
};
