import { Link } from "react-router";

export function NotFoundPage() {
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
      <h1 style={{ fontSize: "5rem", margin: 0, color: "#dc2626", fontWeight: "bold" }}>404</h1>
      <h2 style={{ fontSize: "1.5rem", marginTop: "0.5rem", color: "#1f2937" }}>Trang Không Tồn Tại</h2>
      <p style={{ color: "#6b7280", maxWidth: "450px", marginTop: "0.5rem" }}>
        Đường dẫn bạn yêu cầu không có trên hệ thống hoặc đã bị thay đổi vị trí.
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
