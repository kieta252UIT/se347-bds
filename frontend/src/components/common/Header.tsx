import { Link, useLocation } from "react-router";
import { useAuth } from "@/hooks/useAuth";

function HomeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m3 11 9-7 9 7" />
      <path d="M5 10v10h14V10M9 20v-6h6v6" />
    </svg>
  );
}

export function Header() {
  const { user, isAuthenticated, isKdv, isAdmin, logout } = useAuth();
  const location = useLocation();

  const isActive = (path: string) => {
    if (path === "/") return location.pathname === "/";
    return location.pathname.startsWith(path);
  };

  return (
    <header className="hf-header">
      <Link className="hf-brand" to="/" aria-label="HomeFinder - Trang chủ">
        <span>
          <HomeIcon />
        </span>
        HomeFinder
      </Link>

      <nav className="hf-nav" aria-label="Điều hướng chính">
        <Link to="/" className={isActive("/") ? "active" : ""}>
          Trang chủ
        </Link>
        <Link to="/tim-kiem" className={isActive("/tim-kiem") ? "active" : ""}>
          Tìm kiếm
        </Link>
        <Link to="/quan-ly-tin" className={isActive("/quan-ly-tin") ? "active" : ""}>
          Quản lý tin đăng
        </Link>

        {isKdv && (
          <Link to="/kdv/dashboard" className={isActive("/kdv") ? "active" : ""}>
            Cổng Kiểm duyệt viên
          </Link>
        )}
        {isAdmin && (
          <Link to="/admin/dashboard" className={isActive("/admin") ? "active" : ""}>
            Cổng Quản trị viên
          </Link>
        )}
      </nav>

      <div className="hf-header-right">
        {isAuthenticated ? (
          <>
            <span className="hf-signed-in-label">
              {user?.hoTen || user?.email || "Tài khoản của bạn"}
            </span>
            <button
              type="button"
              className="hf-login-btn"
              onClick={() => logout()}
            >
              Đăng xuất
            </button>
          </>
        ) : (
          <Link className="hf-login-btn" to="/dang-nhap">
            Đăng nhập
          </Link>
        )}
      </div>
    </header>
  );
}
