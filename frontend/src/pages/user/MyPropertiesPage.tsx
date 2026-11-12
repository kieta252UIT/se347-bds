import { Link, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";

export function MyPropertiesPage() {
  const { isAuthenticated, isLoading, user } = useAuth();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div style={{ textAlign: "center", padding: "3rem" }}>
        <p style={{ color: "#6b7280" }}>Đang kiểm tra trạng thái tài khoản...</p>
      </div>
    );
  }

  // Nếu CHƯA đăng nhập: Hiển thị giao diện thông báo yêu cầu đăng nhập kèm nút chuyển hướng
  if (!isAuthenticated) {
    return (
      <div style={{
        maxWidth: "600px",
        margin: "3rem auto",
        backgroundColor: "#ffffff",
        padding: "2.5rem",
        borderRadius: "0.5rem",
        border: "1px solid #e5e7eb",
        textAlign: "center",
        boxShadow: "0 1px 3px rgba(0, 0, 0, 0.05)"
      }}>
        <div style={{ fontSize: "3rem", marginBottom: "1rem" }}>🔒</div>
        <h2 style={{ marginTop: 0, color: "#1f2937", fontSize: "1.5rem" }}>Yêu Cầu Đăng Nhập</h2>
        <p style={{ color: "#4b5563", margin: "1rem 0 2rem", lineHeight: "1.6" }}>
          Bạn cần đăng nhập tài khoản để quản lý các tin bất động sản đã đăng, theo dõi trạng thái kiểm duyệt, chỉnh sửa hoặc đăng tin mới.
        </p>

        <button
          onClick={() => navigate("/dang-nhap", { state: { from: "/quan-ly-tin" } })}
          style={{
            padding: "0.75rem 2rem",
            backgroundColor: "#2563eb",
            color: "#ffffff",
            border: "none",
            borderRadius: "0.375rem",
            fontWeight: 600,
            fontSize: "1rem",
            cursor: "pointer",
            boxShadow: "0 2px 4px rgba(37, 99, 235, 0.2)"
          }}
        >
          Chuyển sang trang Đăng nhập
        </button>
      </div>
    );
  }

  // Nếu ĐÃ đăng nhập: Hiển thị giao diện quản lý tin đăng của tôi
  return (
    <div>
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "1.5rem"
      }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "1.5rem", color: "#111827" }}>Quản Lý Tin Đăng Của Tôi</h1>
          <p style={{ color: "#6b7280", margin: "0.25rem 0 0" }}>
            Tài khoản: <strong>{user?.hoTen || user?.email}</strong> (Hạn mức 3 tin mới/tháng - BR-09)
          </p>
        </div>

        <Link
          to="/dang-tin"
          style={{
            padding: "0.6rem 1.25rem",
            backgroundColor: "#2563eb",
            color: "#ffffff",
            textDecoration: "none",
            borderRadius: "0.375rem",
            fontWeight: 500,
            fontSize: "0.875rem"
          }}
        >
          + Đăng tin mới
        </Link>
      </div>

      {/* Tabs lọc trạng thái tin */}
      <div style={{
        display: "flex",
        gap: "1rem",
        borderBottom: "1px solid #e5e7eb",
        marginBottom: "1.5rem",
        backgroundColor: "#ffffff",
        padding: "0 1rem",
        borderRadius: "0.5rem"
      }}>
        <span style={{ padding: "0.75rem 0.5rem", borderBottom: "2px solid #2563eb", color: "#2563eb", fontWeight: 600 }}>
          Tất cả (0)
        </span>
        <span style={{ padding: "0.75rem 0.5rem", color: "#6b7280", cursor: "pointer" }}>
          Đã duyệt (0)
        </span>
        <span style={{ padding: "0.75rem 0.5rem", color: "#6b7280", cursor: "pointer" }}>
          Chờ duyệt (0)
        </span>
        <span style={{ padding: "0.75rem 0.5rem", color: "#6b7280", cursor: "pointer" }}>
          Bị từ chối (0)
        </span>
      </div>

      {/* Danh sách tin đăng placeholder */}
      <div style={{
        backgroundColor: "#ffffff",
        padding: "3rem",
        borderRadius: "0.5rem",
        border: "1px solid #e5e7eb",
        textAlign: "center"
      }}>
        <div style={{ fontSize: "2.5rem", color: "#9ca3af", marginBottom: "0.5rem" }}>📄</div>
        <p style={{ color: "#6b7280", fontSize: "1rem", margin: 0 }}>
          Bạn chưa có bài đăng bất động sản nào.
        </p>
        <p style={{ color: "#9ca3af", fontSize: "0.875rem", marginTop: "0.25rem" }}>
          (Khung placeholder sẵn sàng cho task kết nối API danh sách tin ngày 18/11)
        </p>
      </div>
    </div>
  );
}
