import { Link, useLocation } from "react-router";
import { useAuth } from "@/hooks/useAuth";

export function Header() {
  const { user, isAuthenticated, isKdv, isAdmin, logout } = useAuth();
  const location = useLocation();

  const isActive = (path: string) => location.pathname === path;

  return (
    <header style={{
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "0.875rem 2rem",
      backgroundColor: "#ffffff",
      borderBottom: "1px solid #e5e7eb",
      boxShadow: "0 1px 2px 0 rgba(0, 0, 0, 0.05)"
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: "2.5rem" }}>
        <Link to="/" style={{ textDecoration: "none", fontSize: "1.25rem", fontWeight: "bold", color: "#1d4ed8" }}>
          BĐS Marketplace
        </Link>
        <nav style={{ display: "flex", gap: "1.5rem" }}>
          <Link
            to="/"
            style={{
              color: isActive("/") ? "#1d4ed8" : "#4b5563",
              fontWeight: isActive("/") ? 600 : 400,
              textDecoration: "none",
              paddingBottom: "0.25rem",
              borderBottom: isActive("/") ? "2px solid #1d4ed8" : "none"
            }}
          >
            Trang chủ
          </Link>

          <Link
            to="/tim-kiem"
            style={{
              color: isActive("/tim-kiem") ? "#1d4ed8" : "#4b5563",
              fontWeight: isActive("/tim-kiem") ? 600 : 400,
              textDecoration: "none",
              paddingBottom: "0.25rem",
              borderBottom: isActive("/tim-kiem") ? "2px solid #1d4ed8" : "none"
            }}
          >
            Tìm kiếm
          </Link>

          <Link
            to="/quan-ly-tin"
            style={{
              color: isActive("/quan-ly-tin") ? "#1d4ed8" : "#4b5563",
              fontWeight: isActive("/quan-ly-tin") ? 600 : 400,
              textDecoration: "none",
              paddingBottom: "0.25rem",
              borderBottom: isActive("/quan-ly-tin") ? "2px solid #1d4ed8" : "none"
            }}
          >
            Quản lý tin đăng
          </Link>

          {isKdv && (
            <Link to="/kdv/tham-dinh" style={{ color: "#d97706", textDecoration: "none", fontWeight: 500 }}>
              Kênh KDV
            </Link>
          )}
          {isAdmin && (
            <Link to="/admin/thong-ke" style={{ color: "#dc2626", textDecoration: "none", fontWeight: 500 }}>
              Quản trị Admin
            </Link>
          )}
        </nav>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
        {isAuthenticated ? (
          <>
            <span style={{ fontSize: "0.875rem", color: "#374151" }}>
              Xin chào, <strong>{user?.hoTen || user?.email}</strong>
            </span>
            <button
              onClick={() => logout()}
              style={{
                padding: "0.4rem 0.85rem",
                backgroundColor: "#f3f4f6",
                border: "1px solid #d1d5db",
                borderRadius: "0.375rem",
                color: "#374151",
                cursor: "pointer",
                fontSize: "0.875rem"
              }}
            >
              Đăng xuất
            </button>
          </>
        ) : (
          <Link
            to="/dang-nhap"
            style={{
              padding: "0.5rem 1.25rem",
              backgroundColor: "#2563eb",
              color: "#ffffff",
              borderRadius: "0.375rem",
              textDecoration: "none",
              fontWeight: 500,
              fontSize: "0.875rem"
            }}
          >
            Đăng nhập
          </Link>
        )}
      </div>
    </header>
  );
}
