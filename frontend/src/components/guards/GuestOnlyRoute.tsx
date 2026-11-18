import { Navigate, Outlet } from "react-router";
import { useAuthStore } from "@/stores/useAuthStore";

interface GuestOnlyRouteProps {
  children?: React.ReactNode;
}

export function GuestOnlyRoute({ children }: GuestOnlyRouteProps) {
  const { isAuthenticated, isLoading, role } = useAuthStore();

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "50vh" }}>
        <p>Đang tải...</p>
      </div>
    );
  }

  if (isAuthenticated) {
    const r = (role || "").toLowerCase();
    if (r === "quan_tri_vien" || r === "admin") {
      return <Navigate to="/admin/dashboard" replace />;
    }
    if (r === "kiem_duyet_vien" || r === "kdv") {
      return <Navigate to="/kdv/dashboard" replace />;
    }
    return <Navigate to="/" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
}
