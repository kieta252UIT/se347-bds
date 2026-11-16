import React, { useState } from "react";
import { useNavigate } from "react-router";
import { authApi } from "@/lib/auth";
import {
  LockIcon,
  EyeIcon,
  EyeOffIcon,
  ThinCheckIcon,
  BrandHomeIcon,
  ShieldCheckIcon,
} from "@/components/auth/AuthIcons";
import "./auth.css";

interface RegistrationProps {
  onSwitchToLogin?: () => void;
}

export function RegisterPage({ onSwitchToLogin }: RegistrationProps) {
  const navigate = useNavigate();

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [formData, setFormData] = useState({
    fullName: "",
    mainPhone: "",
    mainEmail: "",
    agreedToTerms: true,
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: "error" | "success"; text: string } | null>(null);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  const handleSwitchToLogin = () => {
    if (onSwitchToLogin) {
      onSwitchToLogin();
    } else {
      navigate("/dang-nhap");
    }
  };

  const isValidPassword = password.length >= 8 && /[A-Z]/.test(password) && /[0-9]/.test(password);
  const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.mainEmail);
  const isValidPhone = formData.mainPhone ? /^0\d{9}$/.test(formData.mainPhone) : true;
  const isFormValid =
    formData.fullName.trim().length > 0 &&
    isValidEmail &&
    isValidPhone &&
    isValidPassword &&
    password === confirmPassword &&
    formData.agreedToTerms;

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.agreedToTerms) {
      setMessage({ type: "error", text: "Vui lòng xác nhận đồng ý với Điều khoản." });
      return;
    }

    if (!isValidEmail) {
      setMessage({ type: "error", text: "Vui lòng nhập email hợp lệ." });
      return;
    }

    if (formData.mainPhone && !isValidPhone) {
      setMessage({ type: "error", text: "Vui lòng nhập số điện thoại hợp lệ (10 số, bắt đầu bằng 0)." });
      return;
    }

    if (password.length < 8 || !/[A-Z]/.test(password) || !/[0-9]/.test(password)) {
      setMessage({ type: "error", text: "Mật khẩu chưa đạt yêu cầu bảo mật." });
      return;
    }
    if (password !== confirmPassword) {
      setMessage({ type: "error", text: "Mật khẩu xác nhận không khớp." });
      return;
    }

    try {
      setLoading(true);
      setMessage(null);

      // Đăng ký qua backend proxy (POST /api/auth/register). Backend gán vai trò người dùng (nguoi_dung).
      await authApi.register(
        formData.fullName,
        formData.mainEmail,
        password,
        formData.mainPhone || undefined
      );

      // Tự động chuyển hướng ngay sang màn hình xác minh email kèm địa chỉ email vừa đăng ký
      navigate(`/xac-minh-email?email=${encodeURIComponent(formData.mainEmail.trim().toLowerCase())}`);
    } catch (err: unknown) {
      const errObj = err as { message?: string; thongDiep?: string };
      setMessage({
        type: "error",
        text: errObj?.message || errObj?.thongDiep || "Có lỗi xảy ra khi đăng ký tài khoản.",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      {/* Brand panel bên trái */}
      <section className="brand-panel registration-brand-panel" aria-label="Giới thiệu HomeFinder">
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
            Tạo tài khoản HomeFinder để lưu tin yêu thích và nhận gợi ý bất động sản phù hợp.
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
      <section className="form-panel registration-form-panel">
        <div className="login-card registration-card">
          <header className="card-header">
            <h2>Đăng ký tài khoản mới</h2>
            <p>Tạo tài khoản HomeFinder để nhận gợi ý phù hợp với nhu cầu của bạn.</p>
          </header>

          {message && (
            <div className={`notice ${message.type === "success" ? "success" : ""}`} role="status">
              <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" />
                <path d="M12 7.5v5.2M12 16.3v.2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
              <div style={{ flex: 1 }}>
                <span>{message.text}</span>
              </div>
            </div>
          )}

          <form onSubmit={handleRegister} noValidate>
            <div className="form-grid">
              <div className="field-group">
                <label>Họ và tên *</label>
                <input
                  type="text"
                  name="fullName"
                  value={formData.fullName}
                  onChange={handleInputChange}
                  className="text-field"
                  placeholder="Nguyễn Văn A"
                  required
                />
              </div>

              <div className="field-group">
                <label>Số điện thoại chính</label>
                <input
                  type="tel"
                  name="mainPhone"
                  value={formData.mainPhone}
                  onChange={handleInputChange}
                  className="text-field"
                  placeholder="090 123 4567"
                />
              </div>

              <div className="field-group" style={{ gridColumn: "1 / -1" }}>
                <label>Email *</label>
                <input
                  type="email"
                  name="mainEmail"
                  value={formData.mainEmail}
                  onChange={handleInputChange}
                  className="text-field"
                  placeholder="name@example.com"
                  required
                />
              </div>

              <div className="field-group">
                <label>Mật khẩu *</label>
                <div className="icon-field">
                  <LockIcon />
                  <input
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Tối thiểu 8 ký tự"
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

              <div className="field-group">
                <label>Xác nhận mật khẩu *</label>
                <div className="icon-field">
                  <LockIcon />
                  <input
                    type={showConfirmPassword ? "text" : "password"}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Nhập lại mật khẩu"
                    required
                  />
                  <button
                    type="button"
                    className="right-icon-btn"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    aria-label={showConfirmPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                  >
                    {showConfirmPassword ? <EyeIcon /> : <EyeOffIcon />}
                  </button>
                </div>
              </div>
            </div>

            {/* Điều kiện mật khẩu */}
            <div style={{ display: "flex", flexDirection: "column", gap: "6px", marginTop: "12px", marginLeft: "2px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", color: password.length >= 8 ? "#15803d" : "#64748b" }}>
                <span style={{ color: password.length >= 8 ? "#22c55e" : "#94a3b8" }}><ThinCheckIcon /></span>
                <span style={{ fontSize: "13px" }}>Mật khẩu tối thiểu 8 ký tự</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", color: /[A-Z]/.test(password) ? "#15803d" : "#64748b" }}>
                <span style={{ color: /[A-Z]/.test(password) ? "#22c55e" : "#94a3b8" }}><ThinCheckIcon /></span>
                <span style={{ fontSize: "13px" }}>Chứa ít nhất 1 ký tự viết hoa</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", color: /[0-9]/.test(password) ? "#15803d" : "#64748b" }}>
                <span style={{ color: /[0-9]/.test(password) ? "#22c55e" : "#94a3b8" }}><ThinCheckIcon /></span>
                <span style={{ fontSize: "13px" }}>Chứa ít nhất 1 ký tự số</span>
              </div>
            </div>

            <label className="checkbox-field" style={{ marginTop: "16px" }}>
              <input
                type="checkbox"
                name="agreedToTerms"
                checked={formData.agreedToTerms}
                onChange={handleInputChange}
              />
              <span>
                Tôi đồng ý với các <a href="#terms">Điều khoản sử dụng</a> và <a href="#privacy">Chính sách bảo mật</a> của HomeFinder.
              </span>
            </label>

            <button
              className="submit-button"
              type="submit"
              disabled={loading || !isFormValid}
              style={{
                marginTop: "18px",
                background: isFormValid ? "" : "#cbd5e1",
                color: isFormValid ? "" : "#64748b",
                boxShadow: isFormValid ? "" : "none",
                cursor: isFormValid ? "pointer" : "not-allowed",
              }}
            >
              {loading ? "Đang xử lý..." : "Đăng ký ngay"}
            </button>
          </form>

          <div className="auth-footer-divider">
            <p>
              Bạn đã có tài khoản?{" "}
              <button type="button" onClick={handleSwitchToLogin} className="register-now-btn">
                Đăng nhập
              </button>
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}

export default RegisterPage;
