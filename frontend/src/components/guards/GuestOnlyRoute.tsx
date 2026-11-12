import { Navigate, Outlet } from "react-router";
import { useAuthStore } from "@/stores/useAuthStore";

interface GuestOnlyRouteProps {
  children?: React.ReactNode;
}

export function GuestOnlyRoute({ children }: GuestOnlyRouteProps) {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "50vh" }}>
        <p>Đang tải...</p>
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children ? <>{children}</> : <Outlet />;
}
