import React, { useState } from "react";
import { Link, useNavigate } from "react-router";
import { authService } from "@/services/authService";
import {
  BrandHomeIcon,
  ShieldCheckIcon,
  LockIcon,
  EyeIcon,
  EyeOffIcon,
  AlertIcon,
} from "@/components/auth/AuthIcons";
import "./auth.css";

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<"email" | "otp" | "done">("email");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const isValidNewPassword =
    newPassword.length >= 8 && /[A-Z]/.test(newPassword) && /[0-9]/.test(newPassword);

  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail || !trimmedEmail.includes("@")) {
      setMessage({ type: "error", text: "Vui lòng nhập địa chỉ email hợp lệ." });
      return;
    }

    try {
      setLoading(true);
      setMessage(null);
      await authService.requestPasswordReset({ email: trimmedEmail });
      setMessage({
        type: "success",
        text: "Mã OTP 6 chữ số đã được gửi tới email của bạn. Hãy kiểm tra hộp thư.",
      });
      setStep("otp");
    } catch (err: unknown) {
      const errObj = err as { message?: string; thongDiep?: string };
      setMessage({
        type: "error",
        text: errObj?.message || errObj?.thongDiep || "Có lỗi xảy ra. Vui lòng thử lại sau.",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedOtp = otp.trim();
    if (!/^\d{6}$/.test(trimmedOtp)) {
      setMessage({ type: "error", text: "Vui lòng nhập mã OTP hợp lệ gồm đúng 6 chữ số (0-9)." });
      return;
    }
    if (!isValidNewPassword) {
      setMessage({
        type: "error",
        text: "Mật khẩu mới chưa đạt yêu cầu (tối thiểu 8 ký tự, có chữ hoa và số).",
      });
      return;
    }

    try {
      setLoading(true);
      setMessage(null);
      await authService.resetPassword({
        email: email.trim().toLowerCase(),
        otp: trimmedOtp,
        matKhauMoi: newPassword,
      });
      setStep("done");
      setMessage({
        type: "success",
        text: "Đặt lại mật khẩu thành công! Tất cả phiên cũ đã được thu hồi. Hãy đăng nhập lại bằng mật khẩu mới.",
      });
    } catch (err: unknown) {
      const errObj = err as { message?: string; thongDiep?: string };
      setMessage({
        type: "error",
        text: errObj?.message || errObj?.thongDiep || "Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="Giới thiệu HomeFinder">
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
            Khôi phục mật khẩu
            <br />
            tài khoản an toàn
          </h1>
          <p className="brand-desc">
            Xác thực qua mã OTP 6 chữ số gửi về email chính chủ và thu hồi toàn bộ phiên đăng nhập cũ để bảo vệ tài khoản.
          </p>

          <div
            className="property-image"
            role="img"
            aria-label="Khu căn hộ cao tầng hiện đại"
            style={{
              backgroundImage: `url('https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=1000&auto=format&fit=crop&q=80')`,
            }}
          />

          <div className="brand-trust-list">
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Xác thực mã OTP 6 chữ số</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Thu hồi tự động mọi phiên đăng nhập cũ</span>
            </div>
          </div>
        </div>

        <footer className="brand-footer">
          <span>© 2026 HomeFinder Inc. Nền tảng kết nối an tâm.</span>
        </footer>
      </section>

      <section className="form-panel">
        <div className="login-card">
          <header className="card-header">
            <h2>{step === "email" ? "Quên mật khẩu" : "Đặt lại mật khẩu mới"}</h2>
            <p>
              {step === "email"
                ? "Nhập địa chỉ email đã đăng ký để nhận mã OTP 6 chữ số"
                : `Nhập mã OTP 6 chữ số đã gửi tới ${email}`}
            </p>
          </header>

          {message && (
            <div className={`notice ${message.type === "success" ? "success" : ""}`} role="status">
              <AlertIcon />
              <div style={{ flex: 1 }}>
                <span>{message.text}</span>
              </div>
            </div>
          )}

          {step === "done" ? (
            <div style={{ textAlign: "center", marginTop: "1.25rem" }}>
              <button
                type="button"
                className="submit-button"
                onClick={() => navigate("/dang-nhap")}
              >
                Đi tới trang Đăng nhập →
              </button>
            </div>
          ) : step === "email" ? (
            <form onSubmit={handleSendOtp} noValidate>
              <div className="field-group">
                <label htmlFor="resetEmail">Email đăng ký *</label>
                <input
                  id="resetEmail"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@example.com"
                  className="text-field"
                  required
                />
              </div>

              <button type="submit" className="submit-button" disabled={loading} style={{ marginTop: "1rem" }}>
                {loading ? "Đang gửi mã OTP..." : "Gửi mã OTP 6 chữ số"}
              </button>
            </form>
          ) : (
            <form onSubmit={handleResetPassword} noValidate>
              <div className="field-group">
                <label htmlFor="resetOtp">Mã OTP (6 chữ số) *</label>
                <input
                  id="resetOtp"
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="123456"
                  className="text-field"
                  style={{
                    letterSpacing: "0.35em",
                    fontWeight: 700,
                    textAlign: "center",
                    fontSize: "1.2rem",
                  }}
                  required
                />
              </div>

              <div className="field-group" style={{ marginTop: "1rem" }}>
                <label htmlFor="newPassword">Mật khẩu mới *</label>
                <div className="icon-field">
                  <LockIcon />
                  <input
                    id="newPassword"
                    type={showPassword ? "text" : "password"}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Tối thiểu 8 ký tự, có chữ hoa và số"
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

              <button type="submit" className="submit-button" disabled={loading} style={{ marginTop: "1.25rem" }}>
                {loading ? "Đang xử lý..." : "Xác nhận và đặt lại mật khẩu"}
              </button>

              <div style={{ textAlign: "center", marginTop: "0.85rem", fontSize: "0.88rem", color: "#64748b" }}>
                Chưa nhận được mã?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setStep("email");
                    setMessage(null);
                    setOtp("");
                  }}
                  className="register-now-btn"
                >
                  Gửi lại mã OTP
                </button>
              </div>
            </form>
          )}

          <div className="auth-footer-divider">
            <p>
              Nhớ mật khẩu?{" "}
              <Link to="/dang-nhap" className="register-now-btn">
                Quay lại Đăng nhập
              </Link>
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}

export default ForgotPasswordPage;
