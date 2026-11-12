import { Outlet, Link } from "react-router";

export function AuthLayout() {
  return (
    <div style={{
      minHeight: "100vh",
      display: "flex",
      flexDirection: "column",
      justifyContent: "center",
      alignItems: "center",
      backgroundColor: "#f9fafb",
      padding: "2rem"
    }}>
      <div style={{ marginBottom: "1.5rem", textAlign: "center" }}>
        <Link to="/" style={{ textDecoration: "none", fontSize: "1.75rem", fontWeight: "bold", color: "#1d4ed8" }}>
          BĐS Marketplace
        </Link>
        <p style={{ marginTop: "0.25rem", color: "#6b7280", fontSize: "0.875rem" }}>
          Hệ thống tìm kiếm và gợi ý bất động sản
        </p>
      </div>

      <div style={{
        width: "100%",
        maxWidth: "420px",
        backgroundColor: "#ffffff",
        padding: "2rem",
        borderRadius: "0.5rem",
        boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)",
        border: "1px solid #e5e7eb"
      }}>
        <Outlet />
      </div>

      <div style={{ marginTop: "1.5rem" }}>
        <Link to="/" style={{ color: "#4b5563", fontSize: "0.875rem", textDecoration: "none" }}>
          ← Quay lại Trang chủ
        </Link>
      </div>
    </div>
  );
}
