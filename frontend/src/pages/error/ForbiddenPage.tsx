import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";

export function ForbiddenPage() {
  const { isAdmin, isKdv } = useAuth();

  const homePath = isAdmin ? "/admin/dashboard" : isKdv ? "/kdv/dashboard" : "/";
  const homeLabel = isAdmin
    ? "Về Tổng quan Quản trị viên"
    : isKdv
    ? "Về Tổng quan Kiểm duyệt viên"
    : "Về Trang Chủ";

  return (
    <div
      style={{
        minHeight: "75vh",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        textAlign: "center",
        padding: "2rem",
        fontFamily:
          '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif',
      }}
    >
      <div
        style={{
          fontSize: "0.78rem",
          fontWeight: 700,
          textTransform: "uppercase",
          letterSpacing: "0.06em",
          color: "#92400e",
          backgroundColor: "#fffbeb",
          border: "1px solid #fde68a",
          padding: "4px 12px",
          borderRadius: "999px",
          marginBottom: "0.75rem",
        }}
      >
        Từ chối quyền truy cập (403)
      </div>
      <h1 style={{ fontSize: "2rem", margin: 0, color: "#0f172a", fontWeight: 800 }}>
        Bạn không có quyền truy cập trang này
      </h1>
      <p style={{ color: "#64748b", maxWidth: "480px", marginTop: "0.65rem", lineHeight: 1.55 }}>
        Tài khoản hiện tại của bạn không thuộc nhóm vai trò được phép truy cập vào khu vực này.
      </p>
      <div style={{ display: "flex", gap: "0.75rem", marginTop: "1.5rem", flexWrap: "wrap", justifyContent: "center" }}>
        <Link
          to={homePath}
          style={{
            padding: "0.65rem 1.25rem",
            backgroundColor: "#0f3d32",
            color: "#ffffff",
            borderRadius: "8px",
            textDecoration: "none",
            fontWeight: 600,
            fontSize: "0.9rem",
          }}
        >
          {homeLabel}
        </Link>
        <Link
          to="/staff/login"
          style={{
            padding: "0.65rem 1.25rem",
            backgroundColor: "#ffffff",
            color: "#334155",
            border: "1px solid #cbd5e1",
            borderRadius: "8px",
            textDecoration: "none",
            fontWeight: 600,
            fontSize: "0.9rem",
          }}
        >
          Cổng đăng nhập nhân sự
        </Link>
      </div>
    </div>
  );
}
