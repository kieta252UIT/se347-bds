import { Link } from "react-router";

export function ForgotPasswordPage() {
  return (
    <div>
      <h2 style={{ marginTop: 0, textAlign: "center" }}>Quên Mật Khẩu</h2>
      <p style={{ color: "#6b7280", fontSize: "0.875rem", textAlign: "center", marginBottom: "1.5rem" }}>
        Khôi phục mật khẩu tài khoản qua email
      </p>

      <form onSubmit={(e) => e.preventDefault()} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        <div>
          <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 500, marginBottom: "0.25rem" }}>
            Email đăng ký
          </label>
          <input
            type="email"
            placeholder="name@example.com"
            style={{ width: "100%", padding: "0.5rem", borderRadius: "0.25rem", border: "1px solid #d1d5db" }}
          />
        </div>

        <button
          type="button"
          style={{
            padding: "0.625rem",
            backgroundColor: "#2563eb",
            color: "#ffffff",
            border: "none",
            borderRadius: "0.375rem",
            fontWeight: 500,
            cursor: "pointer"
          }}
        >
          Gửi liên kết đặt lại mật khẩu
        </button>
      </form>

      <div style={{ marginTop: "1.5rem", textAlign: "center" }}>
        <Link to="/dang-nhap" style={{ color: "#2563eb", textDecoration: "none", fontSize: "0.875rem" }}>
          ← Quay lại Đăng nhập
        </Link>
      </div>
    </div>
  );
}
