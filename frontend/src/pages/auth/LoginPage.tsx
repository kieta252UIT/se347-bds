import { type FormEvent, useState } from "react";
import { useNavigate, useLocation, Link } from "react-router";
import { authApi } from "@/lib/auth";
import { useAuthStore } from "@/stores/useAuthStore";
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

interface LoginProps {
  onSwitchToRegister?: () => void;
  onLogin?: () => void;
}

export function LoginPage({ onSwitchToRegister, onLogin }: LoginProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((state) => state.setAuth);
  const authNotice = useAuthStore((state) => state.authNotice);
  const clearAuthNotice = useAuthStore((state) => state.clearAuthNotice);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(true);
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);
  const [isForgotModalOpen, setIsForgotModalOpen] = useState(false);

  const activeError = error || authNotice?.text || null;
  const activeUnverifiedEmail = unverifiedEmail || authNotice?.unverifiedEmail || null;

  const handleSwitchToRegister = () => {
    if (onSwitchToRegister) {
      onSwitchToRegister();
    } else {
      navigate("/dang-ky");
    }
  };

  const handleLoginSuccess = () => {
    if (onLogin) {
      onLogin();
    } else {
      const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || "/";
      navigate(from, { replace: true });
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setUnverifiedEmail(null);
    clearAuthNotice();

    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail || !password) {
      setError("Vui lòng nhập đầy đủ địa chỉ email và mật khẩu.");
      setLoading(false);
      return;
    }

    try {
      // Đăng nhập bằng Email và Mật khẩu theo SRS FR-15 / UC-27 (POST /api/auth/login)
      const result = await authApi.login(trimmedEmail, password);
      if (result?.taiKhoan) {
        setAuth(result.taiKhoan);
      }
      handleLoginSuccess();
    } catch (err: unknown) {
      const errObj = err as { code?: string; maLoi?: string; message?: string; thongDiep?: string };
      const errorCode = errObj?.code || errObj?.maLoi;

      if (errorCode === "EMAIL_CHUA_XAC_MINH") {
        setUnverifiedEmail(trimmedEmail);
        setError("Tài khoản chưa được xác minh email. Vui lòng kiểm tra hộp thư hoặc nhấn vào liên kết bên dưới để xác minh / gửi lại.");
      } else if (errorCode === "TAI_KHOAN_BI_KHOA") {
        setError("Tài khoản của bạn đã bị khóa bởi Quản trị viên (kháng cáo qua email quản trị).");
      } else {
        setError(errObj?.message || errObj?.thongDiep || "Email hoặc mật khẩu không chính xác.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      {/* Brand panel bên trái - Tự nhiên, sạch sẽ, không khoảng trống vô lý */}
      <section className="brand-panel login-brand-panel" aria-label="Giới thiệu HomeFinder">
        <header className="brand-header">
          <div className="brand">
            <span className="brand-mark" aria-hidden="true">
              <BrandHomeIcon />
            </span>
            <span>HomeFinder</span>
          </div>
        </header>

        <div className="brand-body">
          <h1 className="brand-headline">
            Tìm kiếm và gợi ý
            <br />
            bất động sản
          </h1>

          <p className="brand-desc">
            Nền tảng giúp bạn tìm đúng nơi, chọn đúng nhà và nhận gợi ý bất động sản phù hợp với nhu cầu.
          </p>

          <div
            className="property-image"
            role="img"
            aria-label="Khu căn hộ cao tầng hiện đại vào buổi tối"
            style={{
              backgroundImage: `url('https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=1000&auto=format&fit=crop&q=80')`,
            }}
          />

          <div className="brand-trust-list">
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Đảm bảo thanh toán an toàn</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Môi giới chính chủ, tin đăng chuẩn xác</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Bảo vệ người mua và chủ nhà an tâm giao dịch</span>
            </div>
          </div>
        </div>

        <footer className="brand-footer">
          <span>© 2026 HomeFinder Inc. Nền tảng kết nối an tâm.</span>
        </footer>
      </section>

      {/* Form panel bên phải */}
      <section className="form-panel">
        <div className="login-card">
          <header className="card-header">
            <h2>Đăng nhập tài khoản</h2>
            <p>Nhập email và mật khẩu để tiếp tục vào hệ thống</p>
          </header>

          {activeError && (
            <div className="notice" role="status">
              <AlertIcon />
              <div style={{ flex: 1 }}>
                <span>{activeError}</span>
                {activeUnverifiedEmail && (
                  <div style={{ marginTop: "6px" }}>
                    <Link
                      to={`/xac-minh-email?email=${encodeURIComponent(activeUnverifiedEmail)}`}
                      style={{ color: "#991b1b", fontWeight: 700, textDecoration: "underline" }}
                    >
                      Bấm vào đây để xác minh hoặc gửi lại email xác nhận →
                    </Link>
                  </div>
                )}
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <div className="field-group">
              <label htmlFor="email">Địa chỉ email</label>
              <div className="icon-field">
                <UserIcon />
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="name@example.com"
                  autoComplete="email"
                  required
                />
              </div>
            </div>

            <div className="field-group" style={{ marginTop: "14px" }}>
              <label htmlFor="password">Mật khẩu</label>
              <div className="icon-field">
                <LockIcon />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Nhập mật khẩu"
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

              <div className="form-sub-row">
                <label className="remember-me">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                  />
                  <span>Ghi nhớ đăng nhập</span>
                </label>
                <a
                  href="#forgot"
                  onClick={(e) => {
                    e.preventDefault();
                    setIsForgotModalOpen(true);
                  }}
                  className="forgot-password-link"
                >
                  Quên mật khẩu?
                </a>
              </div>
            </div>

            <button className="submit-button" type="submit" disabled={loading}>
              {loading ? "Đang xử lý..." : "Đăng nhập ngay"}
            </button>
          </form>

          <div className="auth-footer-divider">
            <p>
              Chưa có tài khoản?{" "}
              <button type="button" onClick={handleSwitchToRegister} className="register-now-btn">
                Đăng ký ngay
              </button>
            </p>
          </div>
        </div>
      </section>

      {isForgotModalOpen && (
        <ForgotPasswordModal
          onClose={() => setIsForgotModalOpen(false)}
          onSwitchToRegister={handleSwitchToRegister}
        />
      )}
    </main>
  );
}

export default LoginPage;
