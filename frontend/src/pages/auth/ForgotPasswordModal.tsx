import React, { useState } from "react";
import { authService } from "@/services/authService";
import { LockIcon, EyeIcon, EyeOffIcon } from "@/components/auth/AuthIcons";

type Step = "email" | "otp";

interface ForgotPasswordModalProps {
  onClose: () => void;
  onSwitchToRegister?: () => void;
}

export default function ForgotPasswordModal({ onClose, onSwitchToRegister }: ForgotPasswordModalProps) {
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const isValidNewPassword = newPassword.length >= 8 && /[A-Z]/.test(newPassword) && /[0-9]/.test(newPassword);

  // Bước 1: Gửi OTP về email
  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !email.includes("@")) {
      setMessage({ type: "error", text: "Vui lòng nhập địa chỉ email hợp lệ." });
      return;
    }
    try {
      setLoading(true);
      setMessage(null);
      await authService.requestPasswordReset({ email: email.trim().toLowerCase() });
      setMessage({
        type: "success",
        text: "Mã OTP đã được gửi tới email của bạn. Hãy kiểm tra hộp thư (kể cả thư mục spam).",
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

  // Bước 2: Xác nhận OTP + đặt lại mật khẩu
  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otp.trim()) {
      setMessage({ type: "error", text: "Vui lòng nhập mã OTP đã được gửi về email." });
      return;
    }
    if (!isValidNewPassword) {
      setMessage({ type: "error", text: "Mật khẩu mới chưa đạt yêu cầu (tối thiểu 8 ký tự, có chữ hoa và số)." });
      return;
    }
    try {
      setLoading(true);
      setMessage(null);
      await authService.resetPassword({
        email: email.trim().toLowerCase(),
        otp: otp.trim(),
        matKhauMoi: newPassword,
      });
      setMessage({
        type: "success",
        text: "Mật khẩu đã được đặt lại thành công! Bạn có thể đăng nhập bằng mật khẩu mới.",
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

  const inputStyle: React.CSSProperties = {
    width: "100%",
    padding: "0.65rem 0.85rem",
    borderRadius: "0.5rem",
    border: "1px solid #cbd5e1",
    fontSize: "0.95rem",
    boxSizing: "border-box",
    outline: "none",
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: "1rem",
      }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div
        style={{
          backgroundColor: "#ffffff",
          borderRadius: "1rem",
          maxWidth: "440px",
          width: "100%",
          padding: "2rem",
          boxShadow: "0 20px 25px -5px rgba(0,0,0,0.1), 0 10px 10px -5px rgba(0,0,0,0.04)",
          position: "relative",
        }}
      >
        {/* Nút đóng */}
        <button
          onClick={onClose}
          style={{
            position: "absolute",
            top: "1.25rem",
            right: "1.25rem",
            border: "none",
            background: "none",
            fontSize: "1.5rem",
            cursor: "pointer",
            color: "#6b7280",
            lineHeight: 1,
          }}
          aria-label="Đóng"
        >
          &times;
        </button>

        {/* Tiêu đề */}
        <h3 style={{ margin: "0 0 0.35rem", fontSize: "1.3rem", color: "#0f172a", fontWeight: 700 }}>
          {step === "email" ? "Quên mật khẩu?" : "Đặt lại mật khẩu"}
        </h3>
        <p style={{ margin: "0 0 1.5rem", fontSize: "0.88rem", color: "#64748b", lineHeight: 1.5 }}>
          {step === "email"
            ? "Nhập email tài khoản — chúng tôi sẽ gửi mã OTP để xác nhận danh tính."
            : `Nhập mã OTP đã gửi tới ${email} và mật khẩu mới của bạn.`}
        </p>

        {/* Thông báo */}
        {message && (
          <div
            style={{
              padding: "0.75rem 1rem",
              borderRadius: "0.5rem",
              marginBottom: "1.25rem",
              fontSize: "0.875rem",
              backgroundColor: message.type === "success" ? "#ecfdf5" : "#fef2f2",
              color: message.type === "success" ? "#065f46" : "#991b1b",
              border: `1px solid ${message.type === "success" ? "#a7f3d0" : "#fecaca"}`,
              lineHeight: 1.4,
            }}
          >
            {message.text}
          </div>
        )}

        {/* Bước 1: Nhập email */}
        {step === "email" && (
          <form onSubmit={handleSendOtp}>
            <div style={{ marginBottom: "1.25rem" }}>
              <label
                style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#334155", marginBottom: "0.4rem" }}
              >
                Email tài khoản
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                required
                autoFocus
                style={inputStyle}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              style={{
                width: "100%",
                padding: "0.75rem",
                backgroundColor: "#131927",
                color: "#ffffff",
                border: "none",
                borderRadius: "0.5rem",
                fontWeight: 600,
                fontSize: "0.95rem",
                cursor: loading ? "not-allowed" : "pointer",
                opacity: loading ? 0.7 : 1,
              }}
            >
              {loading ? "Đang gửi..." : "Gửi mã OTP về email"}
            </button>
          </form>
        )}

        {/* Bước 2: Nhập OTP + mật khẩu mới */}
        {step === "otp" && (
          <form onSubmit={handleResetPassword}>
            <div style={{ marginBottom: "1rem" }}>
              <label
                style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#334155", marginBottom: "0.4rem" }}
              >
                Mã OTP (8 chữ số)
              </label>
              <input
                type="text"
                inputMode="numeric"
                maxLength={8}
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                placeholder="123456"
                required
                autoFocus
                style={{ ...inputStyle, letterSpacing: "0.3em", fontWeight: 700, textAlign: "center", fontSize: "1.1rem" }}
              />
            </div>

            <div style={{ marginBottom: "0.75rem" }}>
              <label
                style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#334155", marginBottom: "0.4rem" }}
              >
                Mật khẩu mới
              </label>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  border: "1px solid #cbd5e1",
                  borderRadius: "0.5rem",
                  paddingLeft: "0.75rem",
                  gap: "6px",
                }}
              >
                <LockIcon />
                <input
                  type={showPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Tối thiểu 8 ký tự, có chữ hoa và số"
                  required
                  style={{
                    border: "none",
                    outline: "none",
                    padding: "0.65rem 0.5rem",
                    flex: 1,
                    fontSize: "0.9rem",
                    background: "transparent",
                  }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                  style={{
                    border: "none",
                    background: "none",
                    cursor: "pointer",
                    color: "#64748b",
                    padding: "0.5rem",
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  {showPassword ? <EyeIcon /> : <EyeOffIcon />}
                </button>
              </div>
              {/* Điều kiện mật khẩu mini */}
              <div style={{ display: "flex", gap: "10px", marginTop: "6px", fontSize: "12px", color: "#64748b" }}>
                <span style={{ color: newPassword.length >= 8 ? "#16a34a" : "#94a3b8" }}>✓ 8+ ký tự</span>
                <span style={{ color: /[A-Z]/.test(newPassword) ? "#16a34a" : "#94a3b8" }}>✓ Chữ hoa</span>
                <span style={{ color: /[0-9]/.test(newPassword) ? "#16a34a" : "#94a3b8" }}>✓ Số</span>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              style={{
                width: "100%",
                padding: "0.75rem",
                backgroundColor: "#131927",
                color: "#ffffff",
                border: "none",
                borderRadius: "0.5rem",
                fontWeight: 600,
                fontSize: "0.95rem",
                cursor: loading ? "not-allowed" : "pointer",
                opacity: loading ? 0.7 : 1,
                marginTop: "0.75rem",
              }}
            >
              {loading ? "Đang xử lý..." : "Đặt lại mật khẩu"}
            </button>

            {/* Gửi lại OTP */}
            <div style={{ textAlign: "center", marginTop: "0.75rem", fontSize: "0.85rem", color: "#64748b" }}>
              Không nhận được mã?{" "}
              <button
                type="button"
                onClick={() => { setStep("email"); setMessage(null); setOtp(""); setNewPassword(""); }}
                style={{ color: "#0f3d32", fontWeight: 600, background: "none", border: "none", cursor: "pointer" }}
              >
                Gửi lại
              </button>
            </div>
          </form>
        )}

        {/* Footer */}
        <div style={{ marginTop: "1.25rem", textAlign: "center", fontSize: "0.85rem", color: "#6b7280" }}>
          Nhớ mật khẩu rồi?{" "}
          <button
            type="button"
            onClick={onClose}
            style={{ color: "#0f3d32", fontWeight: 600, background: "none", border: "none", cursor: "pointer" }}
          >
            Quay lại Đăng nhập
          </button>
          {onSwitchToRegister && (
            <>
              {" "}•{" "}
              <button
                type="button"
                onClick={() => { onClose(); onSwitchToRegister(); }}
                style={{ color: "#0f3d32", fontWeight: 600, background: "none", border: "none", cursor: "pointer" }}
              >
                Đăng ký tài khoản
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
