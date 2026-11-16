import { useEffect } from "react";
import { RouterProvider } from "react-router";
import { router } from "@/routes";
import { useAuthStore } from "@/stores/useAuthStore";
import { ensureCsrfToken } from "@/services/api";

export default function App() {
  const checkSession = useAuthStore((state) => state.checkSession);

  useEffect(() => {
    // Khởi tạo trước CSRF token và khôi phục phiên làm việc khi người dùng mở lại hoặc reload trình duyệt (SDD-09, ADR-0013)
    ensureCsrfToken().catch(() => {});
    checkSession();
  }, [checkSession]);

  return <RouterProvider router={router} />;
}
