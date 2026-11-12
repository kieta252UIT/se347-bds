import { Link } from "react-router";

export function LoginPage() {
  return (
    <div>
      <h2 style={{ marginTop: 0, textAlign: "center" }}>Đăng Nhập</h2>
      <p style={{ color: "#6b7280", fontSize: "0.875rem", textAlign: "center", marginBottom: "1.5rem" }}>
        Đăng nhập vào tài khoản hệ thống BĐS
      </p>

      <form onSubmit={(e) => e.preventDefault()} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        <div>
          <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 500, marginBottom: "0.25rem" }}>
            Email
          </label>
          <input
            type="email"
            placeholder="name@example.com"
            style={{ width: "100%", padding: "0.5rem", borderRadius: "0.25rem", border: "1px solid #d1d5db" }}
          />
        </div>

        <div>
          <label style={{ display: "block", fontSize: "0.875rem", fontWeight: 500, marginBottom: "0.25rem" }}>
            Mật khẩu
          </label>
          <input
            type="password"
            placeholder="••••••••"
            style={{ width: "100%", padding: "0.5rem", borderRadius: "0.25rem", border: "1px solid #d1d5db" }}
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Link to="/quen-mat-khau" style={{ fontSize: "0.75rem", color: "#2563eb", textDecoration: "none" }}>
            Quên mật khẩu?
          </Link>
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
          Đăng nhập
        </button>
      </form>

      <div style={{ marginTop: "1.5rem", textAlign: "center", fontSize: "0.875rem", color: "#6b7280" }}>
        Chưa có tài khoản?{" "}
        <Link to="/dang-ky" style={{ color: "#2563eb", textDecoration: "none", fontWeight: 500 }}>
          Đăng ký ngay
        </Link>
      </div>
    </div>
  );
}
