import { useEffect } from "react";
import { RouterProvider } from "react-router";
import { router } from "@/routes";
import { useAuthStore } from "@/stores/useAuthStore";

export default function App() {
  const checkSession = useAuthStore((state) => state.checkSession);

  useEffect(() => {
    // Khôi phục phiên làm việc khi người dùng mở lại hoặc reload trình duyệt (SDD-09)
    checkSession();
  }, [checkSession]);

  return <RouterProvider router={router} />;
}
