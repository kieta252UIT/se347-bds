import { Link } from "react-router";

export function ForbiddenPage() {
  return (
    <div style={{
      minHeight: "70vh",
      display: "flex",
      flexDirection: "column",
      justifyContent: "center",
      alignItems: "center",
      textAlign: "center",
      padding: "2rem"
    }}>
      <h1 style={{ fontSize: "5rem", margin: 0, color: "#d97706", fontWeight: "bold" }}>403</h1>
      <h2 style={{ fontSize: "1.5rem", marginTop: "0.5rem", color: "#1f2937" }}>Truy Cập Bị Từ Chối</h2>
      <p style={{ color: "#6b7280", maxWidth: "450px", marginTop: "0.5rem" }}>
        Bạn không có quyền truy cập vào tài nguyên hoặc khu vực quản trị này (theo quy tắc phân quyền người dùng).
      </p>
      <Link
        to="/"
        style={{
          marginTop: "1.5rem",
          padding: "0.625rem 1.25rem",
          backgroundColor: "#2563eb",
          color: "#ffffff",
          borderRadius: "0.375rem",
          textDecoration: "none",
          fontWeight: 500
        }}
      >
        Về Trang Chủ
      </Link>
    </div>
  );
}
