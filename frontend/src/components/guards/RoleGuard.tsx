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
    return <Navigate to="/dang-nhap" replace />;
  }

  if (!allowedRoles.includes(role)) {
    return <Navigate to="/403" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
}
