import { type FormEvent, useState } from "react";
import { useNavigate, useLocation, Link } from "react-router";
import { authApi } from "@/lib/auth";
import { useAuthStore } from "@/stores/useAuthStore";
import { toast } from "@/stores/useToastStore";
import ForgotPasswordModal from "./ForgotPasswordModal";
import {
  AlertIcon,
  UserIcon,
  LockIcon,
  EyeIcon,
  EyeOffIcon,
  BrandHomeIcon,
  ShieldCheckIcon,
} from "@/components/auth/AuthIcons";
import "./auth.css";

type StaffRoleTarget = "kiem_duyet_vien" | "quan_tri_vien";

export function StaffLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((state) => state.setAuth);
  const logout = useAuthStore((state) => state.logout);
  const authNotice = useAuthStore((state) => state.authNotice);
  const clearAuthNotice = useAuthStore((state) => state.clearAuthNotice);

  const [selectedRole, setSelectedRole] = useState<StaffRoleTarget>("kiem_duyet_vien");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(true);
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mismatchRoleSuggestion, setMismatchRoleSuggestion] = useState<StaffRoleTarget | null>(null);
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);
  const [isForgotModalOpen, setIsForgotModalOpen] = useState(false);

  const activeError = error || authNotice?.text || null;
  const activeUnverifiedEmail = unverifiedEmail || authNotice?.unverifiedEmail || null;

  const isAdminSelected = selectedRole === "quan_tri_vien";

  const handleRoleTabChange = (role: StaffRoleTarget) => {
    setSelectedRole(role);
    setError(null);
    setMismatchRoleSuggestion(null);
    clearAuthNotice();
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setMismatchRoleSuggestion(null);
    setUnverifiedEmail(null);
    clearAuthNotice();

    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail || !password) {
      setError("Vui lòng nhập đầy đủ địa chỉ email công vụ và mật khẩu.");
      setLoading(false);
      return;
    }

    try {
      const result = await authApi.login(trimmedEmail, password);
      const taiKhoan = result?.taiKhoan;

      if (!taiKhoan) {
        setError("Phản hồi xác thực không hợp lệ từ máy chủ.");
        setLoading(false);
        return;
      }

      const rawRole = (taiKhoan.vaiTro || "").toLowerCase();
      const isUserRole = rawRole === "nguoi_dung";
      const isKdvRole = rawRole === "kiem_duyet_vien" || rawRole === "kdv";
      const isAdminRole = rawRole === "quan_tri_vien" || rawRole === "admin";

      // 1. Chặn tài khoản Người dùng thông thường truy cập Cổng Nội Bộ
      if (isUserRole || (!isKdvRole && !isAdminRole)) {
        await logout();
        setError(
          "Tài khoản khách hàng không có quyền truy cập Cổng Nội Bộ. Vui lòng đăng nhập tại trang dành cho khách hàng."
        );
        setLoading(false);
        return;
      }

      // 2. Kiểm tra khớp với vai trò đã chọn trên thanh chuyển đổi
      if (selectedRole === "kiem_duyet_vien" && !isKdvRole) {
        await logout();
        setMismatchRoleSuggestion("quan_tri_vien");
        setError(
          "Tài khoản này thuộc vai trò Quản trị viên, không khớp với tư cách Kiểm duyệt viên đang chọn."
        );
        setLoading(false);
        return;
      }

      if (selectedRole === "quan_tri_vien" && !isAdminRole) {
        await logout();
        setMismatchRoleSuggestion("kiem_duyet_vien");
        setError(
          "Tài khoản này thuộc vai trò Kiểm duyệt viên, không khớp với tư cách Quản trị viên đang chọn."
        );
        setLoading(false);
        return;
      }

      // 3. Hợp lệ đúng vai trò -> Lưu trạng thái và điều hướng vào Dashboard tương ứng
      setAuth(taiKhoan);
      const displayName = taiKhoan.hoTen || taiKhoan.email;
      const roleLabel = isAdminRole ? "Quản trị viên" : "Kiểm duyệt viên";
      toast.success(
        `Xin chào ${displayName}. Đã đăng nhập với vai trò ${roleLabel}.`,
        "Đăng nhập nội bộ thành công"
      );

      const fromPath = (location.state as { from?: { pathname?: string } })?.from?.pathname;
      if (isAdminRole) {
        const target = fromPath && fromPath.startsWith("/admin") ? fromPath : "/admin/dashboard";
        navigate(target, { replace: true });
      } else {
        const target = fromPath && fromPath.startsWith("/kdv") ? fromPath : "/kdv/dashboard";
        navigate(target, { replace: true });
      }
    } catch (err: unknown) {
      const errObj = err as { code?: string; maLoi?: string; message?: string; thongDiep?: string };
      const errorCode = errObj?.code || errObj?.maLoi;

      if (errorCode === "EMAIL_CHUA_XAC_MINH") {
        setUnverifiedEmail(trimmedEmail);
        setError(
          "Tài khoản nội bộ chưa hoàn tất xác minh email. Vui lòng xác minh trước khi truy cập."
        );
      } else if (errorCode === "TAI_KHOAN_BI_KHOA") {
        setError("Tài khoản nhân sự này hiện đang bị khóa. Vui lòng liên hệ Quản trị viên cấp cao.");
      } else if (errorCode === "THONG_TIN_XAC_THUC_KHONG_CHINH_XAC") {
        setError("Email hoặc mật khẩu không chính xác.");
      } else if (errorCode === "QUA_GIOI_HAN_YEU_CAU") {
        setError("Bạn đã thử đăng nhập quá nhiều lần. Vui lòng đợi ít phút rồi thử lại.");
      } else {
        setError(
          errObj?.thongDiep || errObj?.message || "Email hoặc mật khẩu không chính xác."
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      {/* Cột trái: Thông tin cổng vận hành nội bộ */}
      <section
        className="brand-panel"
        aria-label="Cổng vận hành nội bộ HomeFinder"
        style={{
          background: isAdminSelected
            ? "linear-gradient(155deg, #0f172a 0%, #070b14 100%)"
            : "linear-gradient(155deg, #0f3d32 0%, #061e18 100%)",
          transition: "background 0.25s ease",
        }}
      >
        <header className="brand-header" style={{ justifyContent: "space-between" }}>
          <Link to="/" className="brand" style={{ textDecoration: "none" }}>
            <span className="brand-mark">
              <BrandHomeIcon />
            </span>
            <span>HomeFinder</span>
          </Link>
          <span
            style={{
              fontSize: "0.72rem",
              fontWeight: 700,
              letterSpacing: "0.06em",
              textTransform: "uppercase",
              padding: "4px 10px",
              borderRadius: "6px",
              backgroundColor: "rgba(255, 255, 255, 0.12)",
              color: "#e2e8f0",
              border: "1px solid rgba(255, 255, 255, 0.16)",
            }}
          >
            Cổng nội bộ
          </span>
        </header>

        <div className="brand-body">
          <div
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              width: "fit-content",
              padding: "5px 12px",
              borderRadius: "999px",
              fontSize: "0.8rem",
              fontWeight: 600,
              backgroundColor: isAdminSelected
                ? "rgba(56, 189, 248, 0.14)"
                : "rgba(34, 197, 94, 0.14)",
              color: isAdminSelected ? "#7dd3fc" : "#86efac",
              border: isAdminSelected
                ? "1px solid rgba(56, 189, 248, 0.28)"
                : "1px solid rgba(34, 197, 94, 0.28)",
            }}
          >
            <span>
              {isAdminSelected ? "Phân hệ Quản trị viên" : "Phân hệ Kiểm duyệt viên"}
            </span>
          </div>

          <h1 className="brand-headline">
            {isAdminSelected
              ? "Quản trị tài khoản, đối soát ví và giám sát toàn hệ thống"
              : "Thẩm định chất lượng tin đăng và xử lý báo cáo vi phạm"}
          </h1>

          <p className="brand-desc">
            {isAdminSelected
              ? "Không gian làm việc chuyên biệt dành cho Quản trị viên để điều hành tài khoản, theo dõi thống kê doanh thu - bồi hoàn, đối soát giao dịch và giải quyết khiếu nại."
              : "Không gian làm việc dành riêng cho Kiểm duyệt viên nhằm kiểm soát hàng chờ tin đăng mới, tin sửa nộp lại và bảo vệ người dùng trước các tin rao vi phạm."}
          </p>

          <div
            style={{
              backgroundColor: "rgba(255, 255, 255, 0.05)",
              border: "1px solid rgba(255, 255, 255, 0.12)",
              borderRadius: "12px",
              padding: "1.15rem 1.25rem",
              display: "flex",
              flexDirection: "column",
              gap: "0.65rem",
            }}
          >
            <div
              style={{
                fontSize: "0.76rem",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "rgba(255, 255, 255, 0.6)",
              }}
            >
              {isAdminSelected
                ? "Chức năng chính của Quản trị viên"
                : "Chức năng chính của Kiểm duyệt viên"}
            </div>
            {isAdminSelected ? (
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                  gap: "8px",
                  fontSize: "0.84rem",
                  color: "#e2e8f0",
                }}
              >
                <div>• Quản lý & Phân quyền tài khoản</div>
                <div>• Thống kê doanh thu & Bồi hoàn</div>
                <div>• Thống kê tin đăng & Báo cáo</div>
                <div>• Giám sát lịch sử hoạt động</div>
                <div>• Đối soát & Điều chỉnh số dư ví</div>
                <div>• Giải quyết khiếu nại khách hàng</div>
              </div>
            ) : (
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: "6px",
                  fontSize: "0.85rem",
                  color: "#e2e8f0",
                }}
              >
                <div>• Thẩm định tin đăng mới và tin chỉnh sửa nộp lại</div>
                <div>• Kiểm tra báo cáo phản ánh từ người dùng & gỡ tin vi phạm</div>
                <div>• Gửi đề xuất khóa tài khoản tái phạm lên Quản trị viên</div>
              </div>
            )}
          </div>

          <div className="brand-trust-list">
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Phân tách độc lập quyền hạn giữa Kiểm duyệt viên và Quản trị viên</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Bảo vệ phiên đăng nhập an toàn và thu hồi tự động khi có thay đổi</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Ghi nhận đầy đủ lịch sử thao tác phục vụ kiểm tra và đối chiếu</span>
            </div>
          </div>
        </div>

        <footer className="brand-footer">
          © 2026 HomeFinder Internal Portal. Chỉ dành cho nhân sự được cấp quyền.
        </footer>
      </section>

      {/* Cột phải: Form đăng nhập nội bộ */}
      <section className="form-panel" aria-label="Biểu mẫu đăng nhập nhân sự">
        <div className="login-card">
          <div className="card-header">
            <div
              style={{
                fontSize: "0.76rem",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.06em",
                color: isAdminSelected ? "#0f172a" : "#0f3d32",
                marginBottom: "0.35rem",
              }}
            >
              Xác thực nhân sự nội bộ
            </div>
            <h2>Đăng nhập Cổng Vận Hành</h2>
            <p>Chọn vai trò làm việc của bạn và nhập thông tin tài khoản được cấp</p>
          </div>

          {/* Nút chuyển đổi vai trò: Kiểm duyệt viên vs Quản trị viên */}
          <div style={{ marginBottom: "1.25rem" }}>
            <label
              style={{
                display: "block",
                fontSize: "0.82rem",
                fontWeight: 600,
                color: "#475569",
                marginBottom: "6px",
              }}
            >
              Đăng nhập với vai trò
            </label>
            <div className="method-tabs" role="tablist" aria-label="Chọn vai trò đăng nhập" style={{ marginBottom: 0 }}>
              <button
                type="button"
                role="tab"
                aria-selected={selectedRole === "kiem_duyet_vien"}
                className={selectedRole === "kiem_duyet_vien" ? "active" : ""}
                onClick={() => handleRoleTabChange("kiem_duyet_vien")}
                style={
                  selectedRole === "kiem_duyet_vien"
                    ? { color: "#0f3d32", borderBottom: "2px solid #0f3d32" }
                    : undefined
                }
              >
                Kiểm duyệt viên
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={selectedRole === "quan_tri_vien"}
                className={selectedRole === "quan_tri_vien" ? "active" : ""}
                onClick={() => handleRoleTabChange("quan_tri_vien")}
                style={
                  selectedRole === "quan_tri_vien"
                    ? { color: "#0f172a", borderBottom: "2px solid #0f172a" }
                    : undefined
                }
              >
                Quản trị viên
              </button>
            </div>
          </div>

          {activeError && (
            <div className="notice" role="alert" style={{ flexDirection: "column", alignItems: "flex-start" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <AlertIcon />
                <span>{activeError}</span>
              </div>

              {mismatchRoleSuggestion && (
                <button
                  type="button"
                  onClick={() => handleRoleTabChange(mismatchRoleSuggestion)}
                  style={{
                    marginTop: "8px",
                    marginLeft: "26px",
                    padding: "5px 10px",
                    borderRadius: "6px",
                    border: "1px solid #dc2626",
                    backgroundColor: "#ffffff",
                    color: "#991b1b",
                    fontSize: "0.8rem",
                    fontWeight: 700,
                    cursor: "pointer",
                  }}
                >
                  Chuyển sang tab{" "}
                  {mismatchRoleSuggestion === "quan_tri_vien" ? "Quản trị viên" : "Kiểm duyệt viên"} →
                </button>
              )}

              {activeUnverifiedEmail && (
                <Link
                  to={`/xac-minh-email?email=${encodeURIComponent(activeUnverifiedEmail)}`}
                  style={{
                    marginTop: "6px",
                    marginLeft: "26px",
                    color: "#0f3d32",
                    fontWeight: 700,
                    textDecoration: "underline",
                  }}
                >
                  Xác minh email tài khoản ngay →
                </Link>
              )}
            </div>
          )}

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <div className="field-group">
              <label htmlFor="staff-email">Email công vụ</label>
              <div className="icon-field">
                <UserIcon />
                <input
                  id="staff-email"
                  name="email"
                  type="email"
                  placeholder={
                    isAdminSelected ? "admin@homefinder.vn" : "moderator@homefinder.vn"
                  }
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="email"
                  required
                />
              </div>
            </div>

            <div className="field-group" style={{ marginTop: "1rem" }}>
              <label htmlFor="staff-password">Mật khẩu</label>
              <div className="icon-field">
                <LockIcon />
                <input
                  id="staff-password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="right-icon-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showPassword ? <EyeIcon /> : <EyeOffIcon />}
                </button>
              </div>
            </div>

            <div className="form-sub-row">
              <label className="remember-me">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                <span>Duy trì phiên làm việc</span>
              </label>

              <button
                type="button"
                onClick={() => setIsForgotModalOpen(true)}
                className="forgot-password-link"
                style={{ background: "none", border: "none", padding: 0, cursor: "pointer" }}
              >
                Quên mật khẩu?
              </button>
            </div>

            <button
              className="submit-button"
              type="submit"
              disabled={loading}
              style={{
                backgroundColor: isAdminSelected ? "#0f172a" : "#0f3d32",
              }}
            >
              {loading
                ? "Đang xác thực quyền truy cập..."
                : isAdminSelected
                ? "Đăng nhập Quản trị viên"
                : "Đăng nhập Kiểm duyệt viên"}
            </button>
          </form>

          <div className="auth-footer-divider">
            <p>
              Bạn là khách hàng tìm mua / đăng tin BĐS?{" "}
              <Link to="/dang-nhap" className="register-now-btn">
                Về trang đăng nhập khách hàng
              </Link>
            </p>
          </div>
        </div>
      </section>

      {isForgotModalOpen && (
        <ForgotPasswordModal
          onClose={() => setIsForgotModalOpen(false)}
          onSwitchToRegister={() => {
            setIsForgotModalOpen(false);
          }}
        />
      )}
    </main>
  );
}

export default StaffLoginPage;
