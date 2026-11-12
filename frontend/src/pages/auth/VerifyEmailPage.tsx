import { Link } from "react-router";

export function VerifyEmailPage() {
  return (
    <div>
      <h2 style={{ marginTop: 0, textAlign: "center" }}>Xác Minh Email</h2>
      <p style={{ color: "#6b7280", fontSize: "0.875rem", textAlign: "center", marginBottom: "1.5rem" }}>
        Xác minh email qua liên kết một lần
      </p>

      <div style={{ textAlign: "center", padding: "1rem 0" }}>
        <p style={{ fontSize: "0.875rem", color: "#374151" }}>
          Vui lòng kiểm tra hộp thư email của bạn để nhấn vào liên kết xác minh.
        </p>

        <button
          type="button"
          style={{
            marginTop: "1rem",
            padding: "0.5rem 1rem",
            backgroundColor: "#f3f4f6",
            border: "1px solid #d1d5db",
            borderRadius: "0.375rem",
            cursor: "pointer"
          }}
        >
          Gửi lại email xác minh
        </button>
      </div>

      <div style={{ marginTop: "1.5rem", textAlign: "center" }}>
        <Link to="/dang-nhap" style={{ color: "#2563eb", textDecoration: "none", fontSize: "0.875rem" }}>
          ← Về trang Đăng nhập
        </Link>
      </div>
    </div>
  );
}
