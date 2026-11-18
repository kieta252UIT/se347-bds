import { Outlet, NavLink, Link, useNavigate, useLocation } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "@/stores/useToastStore";
import { BrandHomeIcon } from "@/components/auth/AuthIcons";
import "./staff.css";

interface NavItem {
  to: string;
  label: string;
  icon: React.ReactNode;
}

const kdvNavItems: NavItem[] = [
  {
    to: "/kdv/dashboard",
    label: "Tổng quan công việc",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
      </svg>
    ),
  },
  {
    to: "/kdv/tham-dinh",
    label: "Thẩm định tin đăng",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 11l3 3L22 4" />
        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
      </svg>
    ),
  },
  {
    to: "/kdv/bao-cao",
    label: "Báo cáo & Đề xuất khóa",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
  },
];

const adminNavItems: NavItem[] = [
  {
    to: "/admin/dashboard",
    label: "Tổng quan vận hành",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
      </svg>
    ),
  },
  {
    to: "/admin/thong-ke",
    label: "Thống kê & Phân tích",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <line x1="18" y1="20" x2="18" y2="10" />
        <line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
    ),
  },
  {
    to: "/admin/tai-khoan",
    label: "Quản lý tài khoản",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    ),
  },
  {
    to: "/admin/doi-soat",
    label: "Đối soát & Điều chỉnh ví",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2" y="5" width="20" height="14" rx="2" />
        <line x1="2" y1="10" x2="22" y2="10" />
      </svg>
    ),
  },
  {
    to: "/admin/khieu-nai",
    label: "Giải quyết khiếu nại",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
      </svg>
    ),
  },
  {
    to: "/admin/giam-sat",
    label: "Giám sát hoạt động",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
      </svg>
    ),
  },
];

const routeTitles: Record<string, string> = {
  "/kdv/dashboard": "Tổng quan Kiểm duyệt viên",
  "/kdv/tham-dinh": "Thẩm định tin đăng bất động sản",
  "/kdv/bao-cao": "Xử lý báo cáo vi phạm & Đề xuất khóa",
  "/admin/dashboard": "Tổng quan vận hành Quản trị viên",
  "/admin/thong-ke": "Thống kê đa tiêu chí & Phân tích dữ liệu",
  "/admin/tai-khoan": "Quản lý tài khoản & Phân quyền",
  "/admin/doi-soat": "Đối soát giao dịch & Điều chỉnh ví",
  "/admin/khieu-nai": "Giải quyết khiếu nại người dùng",
  "/admin/giam-sat": "Giám sát hoạt động & Nhật ký hệ thống",
};

export function AdminLayout() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    await logout();
    toast.info("Đã đăng xuất khỏi Cổng Nội Bộ an toàn.", "Đăng xuất thành công");
    navigate("/staff/login", { replace: true });
  };

  const navItems = isAdmin ? adminNavItems : kdvNavItems;
  const pageTitle = routeTitles[location.pathname] || (isAdmin ? "Phân hệ Quản trị viên" : "Phân hệ Kiểm duyệt viên");
  const avatarChar = (user?.hoTen || user?.email || "S").trim().charAt(0).toUpperCase();

  return (
    <div className="staff-shell">
      {/* Sidebar điều hướng nội bộ */}
      <aside className={`staff-sidebar ${isAdmin ? "admin-theme" : ""}`}>
        <div className="staff-sidebar-header">
          <Link to={isAdmin ? "/admin/dashboard" : "/kdv/dashboard"} className="staff-brand-link">
            <span className="staff-brand-mark">
              <BrandHomeIcon />
            </span>
            <span>HomeFinder Staff</span>
          </Link>
          <div className="staff-role-pill">
            <span>{isAdmin ? "Vai trò: Quản trị viên" : "Vai trò: Kiểm duyệt viên"}</span>
          </div>
        </div>

        <nav className="staff-nav" aria-label="Điều hướng phân hệ nội bộ">
          <div className="staff-nav-section-label">
            {isAdmin ? "Điều hành & Quản trị" : "Nghiệp vụ Kiểm duyệt"}
          </div>

          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `staff-nav-link ${isActive ? "active" : ""}`}
            >
              <span className="staff-nav-link-left">
                {item.icon}
                <span>{item.label}</span>
              </span>
            </NavLink>
          ))}

          <div className="staff-nav-section-label" style={{ marginTop: "1rem" }}>
            Liên kết nhanh
          </div>
          <Link to="/" className="staff-nav-link">
            <span className="staff-nav-link-left">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                <polyline points="15 3 21 3 21 9" />
                <line x1="10" y1="14" x2="21" y2="3" />
              </svg>
              <span>Xem trang công khai</span>
            </span>
          </Link>
        </nav>

        <div className="staff-sidebar-footer">
          <div className="staff-user-card">
            <div className="staff-user-avatar">{avatarChar}</div>
            <div className="staff-user-info">
              <div className="staff-user-name">{user?.hoTen || "Nhân sự nội bộ"}</div>
              <div className="staff-user-email">{user?.email}</div>
            </div>
          </div>
          <button type="button" onClick={handleLogout} className="staff-logout-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      {/* Vùng làm việc chính */}
      <div className="staff-workspace">
        <header className="staff-topbar">
          <div className="staff-topbar-left">
            <h2 className="staff-topbar-title">{pageTitle}</h2>
          </div>
          <div className="staff-topbar-right">
            <span className="staff-status-indicator">
              <span className="staff-status-dot" />
              Đang hoạt động • {isAdmin ? "Quản trị viên" : "Kiểm duyệt viên"}
            </span>
          </div>
        </header>

        <main className="staff-main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
