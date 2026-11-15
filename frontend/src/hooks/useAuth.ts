import { useAuthStore } from "@/stores/useAuthStore";

export function useAuth() {
  const { user, role, isAuthenticated, isLoading, setAuth, clearAuth, logout } = useAuthStore();

  return {
    user,
    role,
    isAuthenticated,
    isLoading,
    isGuest: role === "KHACH",
    isUser: role === "NGUOI_DUNG" || role === "nguoi_dung",
    isKdv: role === "KDV" || role === "kiem_duyet_vien",
    isAdmin: role === "ADMIN" || role === "quan_tri_vien",
    setAuth,
    clearAuth,
    logout,
  };
}
