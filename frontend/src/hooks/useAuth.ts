import { useAuthStore } from "@/stores/useAuthStore";

export function useAuth() {
  const { user, role, isAuthenticated, isLoading, setAuth, clearAuth, logout } = useAuthStore();

  return {
    user,
    role,
    isAuthenticated,
    isLoading,
    isGuest: role === "KHACH",
    isUser: role === "NGUOI_DUNG",
    isKdv: role === "KDV",
    isAdmin: role === "ADMIN",
    setAuth,
    clearAuth,
    logout,
  };
}
