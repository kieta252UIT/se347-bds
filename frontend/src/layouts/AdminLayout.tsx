import { Outlet, Link, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";

export function AdminLayout() {
  const { user, isKdv, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate("/dang-nhap");
  };

  return (
    <div style={{ display: "flex", minHeight: "100vh", backgroundColor: "#f3f4f6" }}>
      {/* Sidebar */}
      <aside style={{
        width: "260px",
        backgroundColor: "#1f2937",
        color: "#ffffff",
        display: "flex",
        flexDirection: "column"
      }}>
        <div style={{ padding: "1.5rem", borderBottom: "1px solid #374151" }}>
          <Link to="/" style={{ color: "#ffffff", textDecoration: "none", fontSize: "1.25rem", fontWeight: "bold" }}>
            BĐS Portal
          </Link>
          <div style={{ marginTop: "0.25rem", fontSize: "0.75rem", color: "#9ca3af" }}>
            {isAdmin ? "Khu vực Quản trị viên" : "Khu vực Kiểm duyệt viên"}
          </div>
        </div>

        <nav style={{ flex: 1, padding: "1rem", display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          <div style={{ fontSize: "0.75rem", fontWeight: "bold", color: "#9ca3af", textTransform: "uppercase", margin: "0.5rem 0" }}>
            Nghiệp vụ
          </div>

          {(isKdv || isAdmin) && (
            <>
              <Link to="/kdv/tham-dinh" style={{ color: "#d1d5db", textDecoration: "none", padding: "0.5rem", borderRadius: "0.25rem" }}>
                📋 Thẩm định tin chờ duyệt
              </Link>
              <Link to="/kdv/bao-cao" style={{ color: "#d1d5db", textDecoration: "none", padding: "0.5rem", borderRadius: "0.25rem" }}>
                ⚠️ Xử lý báo cáo vi phạm
              </Link>
            </>
          )}

          {isAdmin && (
            <>
              <div style={{ fontSize: "0.75rem", fontWeight: "bold", color: "#9ca3af", textTransform: "uppercase", margin: "1rem 0 0.5rem" }}>
                Quản trị hệ thống
              </div>
              <Link to="/admin/thong-ke" style={{ color: "#d1d5db", textDecoration: "none", padding: "0.5rem", borderRadius: "0.25rem" }}>
                📊 Thống kê & Giám sát
              </Link>
              <Link to="/admin/tai-khoan" style={{ color: "#d1d5db", textDecoration: "none", padding: "0.5rem", borderRadius: "0.25rem" }}>
                👥 Quản lý tài khoản
              </Link>
              <Link to="/admin/doi-soat" style={{ color: "#d1d5db", textDecoration: "none", padding: "0.5rem", borderRadius: "0.25rem" }}>
                💳 Đối soát giao dịch ví
              </Link>
            </>
          )}
        </nav>

        <div style={{ padding: "1rem", borderTop: "1px solid #374151" }}>
          <div style={{ fontSize: "0.875rem", marginBottom: "0.5rem" }}>
            {user?.hoTen || user?.email}
          </div>
          <button
            onClick={handleLogout}
            style={{
              width: "100%",
              padding: "0.5rem",
              backgroundColor: "#ef4444",
              color: "#ffffff",
              border: "none",
              borderRadius: "0.25rem",
              cursor: "pointer"
            }}
          >
            Đăng xuất
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        <header style={{
          backgroundColor: "#ffffff",
          padding: "1rem 2rem",
          borderBottom: "1px solid #e5e7eb",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center"
        }}>
          <h2 style={{ margin: 0, fontSize: "1.25rem" }}>Bảng điều khiển tác vụ</h2>
          <Link to="/" style={{ color: "#2563eb", textDecoration: "none", fontSize: "0.875rem" }}>
            Về trang giao diện khách →
          </Link>
        </header>

        <main style={{ flex: 1, padding: "2rem" }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
