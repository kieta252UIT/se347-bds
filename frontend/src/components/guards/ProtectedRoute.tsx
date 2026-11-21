import { Navigate, Outlet, useLocation } from "react-router";
import { useAuthStore } from "@/stores/useAuthStore";

interface ProtectedRouteProps {
  children?: React.ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading } = useAuthStore();
  const location = useLocation();

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "50vh" }}>
        <p>Đang kiểm tra phiên đăng nhập...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    const isStaffRoute =
      location.pathname.startsWith("/admin") ||
      location.pathname.startsWith("/kdv") ||
      location.pathname.startsWith("/staff");
    return (
      <Navigate
        to={isStaffRoute ? "/staff/login" : "/dang-nhap"}
        state={{ from: location }}
        replace
      />
    );
  }

  return children ? <>{children}</> : <Outlet />;
}
