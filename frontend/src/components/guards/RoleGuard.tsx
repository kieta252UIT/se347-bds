import { Navigate, Outlet } from "react-router";
import type { UserRole } from "@/types/auth";
import { useAuthStore } from "@/stores/useAuthStore";

interface RoleGuardProps {
  allowedRoles: UserRole[];
  children?: React.ReactNode;
}

export function RoleGuard({ allowedRoles, children }: RoleGuardProps) {
  const { role, isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "50vh" }}>
        <p>Đang kiểm tra quyền truy cập...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/staff/login" replace />;
  }

  const hasPermission = allowedRoles.some((r) => {
    const rLower = r.toLowerCase();
    const userRoleLower = role.toLowerCase();
    if (rLower === userRoleLower) return true;
    if ((rLower === "admin" || rLower === "quan_tri_vien") && (userRoleLower === "admin" || userRoleLower === "quan_tri_vien")) return true;
    if ((rLower === "kdv" || rLower === "kiem_duyet_vien") && (userRoleLower === "kdv" || userRoleLower === "kiem_duyet_vien")) return true;
    if (rLower === "nguoi_dung" && userRoleLower === "nguoi_dung") return true;
    return false;
  });

  if (!hasPermission) {
    return <Navigate to="/403" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
}
