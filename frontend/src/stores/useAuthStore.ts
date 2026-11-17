import { create } from "zustand";
import type { AuthState, UserProfile, UserRole } from "@/types/auth";
import api from "@/services/api";

export interface AuthNotice {
  text: string;
  unverifiedEmail?: string;
}

interface AuthStore extends AuthState {
  authNotice: AuthNotice | null;
  setAuth: (user: UserProfile) => void;
  clearAuth: () => void;
  setAuthNotice: (notice: AuthNotice | null) => void;
  clearAuthNotice: () => void;
  handleSessionInvalidated: (reasonMessage: string, unverifiedEmail?: string) => void;
  setLoading: (loading: boolean) => void;
  checkSession: () => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthStore>((set) => ({
  user: null,
  role: "KHACH",
  isAuthenticated: false,
  isLoading: true,
  authNotice: null,

  setAuth: (user: UserProfile) => {
    set({
      user,
      role: user.vaiTro,
      isAuthenticated: true,
      isLoading: false,
      authNotice: null,
    });
  },

  clearAuth: () => {
    set({
      user: null,
      role: "KHACH",
      isAuthenticated: false,
      isLoading: false,
    });
  },

  setAuthNotice: (authNotice: AuthNotice | null) => {
    set({ authNotice });
  },

  clearAuthNotice: () => {
    set({ authNotice: null });
  },

  handleSessionInvalidated: (reasonMessage: string, unverifiedEmail?: string) => {
    set({
      user: null,
      role: "KHACH",
      isAuthenticated: false,
      isLoading: false,
      authNotice: {
        text: reasonMessage,
        unverifiedEmail,
      },
    });
  },

  setLoading: (isLoading: boolean) => {
    set({ isLoading });
  },

  checkSession: async () => {
    try {
      set({ isLoading: true });
      // Thử lấy thông tin người dùng từ cookie phiên hiện tại (GET /api/me theo openapi-sprint-1.yaml)
      const response = await api.get<UserProfile>("/me");
      if (response.data) {
        if (response.data.trangThai === "bi_khoa") {
          set({
            user: null,
            role: "KHACH",
            isAuthenticated: false,
            isLoading: false,
            authNotice: {
              text: "Tài khoản của bạn đã bị khóa bởi Quản trị viên.",
            },
          });
          return;
        }
        set({
          user: response.data,
          role: response.data.vaiTro || "NGUOI_DUNG",
          isAuthenticated: true,
          isLoading: false,
        });
      }
    } catch {
      // Khi chưa đăng nhập hoặc phiên hết hạn/bị thu hồi/bị khóa
      set({
        user: null,
        role: "KHACH" as UserRole,
        isAuthenticated: false,
        isLoading: false,
      });
    }
  },

  logout: async () => {
    try {
      await api.post("/auth/logout");
    } catch {
      // Bỏ qua lỗi mạng khi logout để luôn xóa trạng thái ở client
    } finally {
      set({
        user: null,
        role: "KHACH",
        isAuthenticated: false,
        isLoading: false,
      });
    }
  },
}));
