import { create } from "zustand";
import type { AuthState, UserProfile, UserRole } from "@/types/auth";
import api from "@/services/api";

interface AuthStore extends AuthState {
  setAuth: (user: UserProfile) => void;
  clearAuth: () => void;
  setLoading: (loading: boolean) => void;
  checkSession: () => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthStore>((set) => ({
  user: null,
  role: "KHACH",
  isAuthenticated: false,
  isLoading: true,

  setAuth: (user: UserProfile) => {
    set({
      user,
      role: user.vaiTro,
      isAuthenticated: true,
      isLoading: false,
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

  setLoading: (isLoading: boolean) => {
    set({ isLoading });
  },

  checkSession: async () => {
    try {
      set({ isLoading: true });
      // Thử lấy thông tin người dùng từ cookie phiên hiện tại
      const response = await api.get<UserProfile>("/auth/me");
      if (response.data) {
        set({
          user: response.data,
          role: response.data.vaiTro || "NGUOI_DUNG",
          isAuthenticated: true,
          isLoading: false,
        });
      }
    } catch {
      // Khi chưa đăng nhập hoặc phiên hết hạn/không có cookie
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
