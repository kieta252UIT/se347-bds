import React, { useState } from "react";
import { authService } from "@/services/authService";
import { setCsrfToken } from "@/services/api";
import { useAuthStore } from "@/stores/useAuthStore";
import { toast } from "@/stores/useToastStore";
import { LockIcon, EyeIcon, EyeOffIcon } from "@/components/auth/AuthIcons";

type Step = "email" | "otp" | "done";

function resolvePasswordResetErrorMessage(err: unknown, fallback: string): string {
  const errObj = err as { code?: string; maLoi?: string; message?: string; thongDiep?: string; status?: number };
  const code = errObj?.code || errObj?.maLoi;
  if (code === "TAM_GIAN_DOAN" || errObj?.status === 503) {
    return (
      "Không thể xác minh mã OTP lúc này do kết nối tới máy chủ xác thực bị chậm. Vui lòng kiểm tra lại mã OTP hoặc thử lại sau ít giây."
    );
  }
  if (code === "DAT_LAI_DANG_XU_LY") {
    return (
      errObj?.thongDiep ||
      errObj?.message ||
      "Thao tác đặt lại mật khẩu của tài khoản đang được xử lý. Vui lòng đợi trong giây lát."
    );
  }
  if (code === "KET_QUA_DAT_LAI_CHUA_XAC_DINH") {
    return (
      errObj?.thongDiep ||
      errObj?.message ||
      "Chưa xác định được toàn bộ kết quả đặt lại do gián đoạn kết nối. Không tự gửi lại liên tục; hãy thử lại sau khi hệ thống tự động đối chiếu (khoảng 30 giây)."
    );
  }
  if (code === "MAT_KHAU_KHONG_DAT_CHINH_SACH") {
    return (
      errObj?.thongDiep ||
      errObj?.message ||
      "Mật khẩu mới không đạt chính sách bảo mật (tối thiểu 8 ký tự, tối đa 72 bytes UTF-8)."
    );
  }
  if (code === "LIEN_KET_KHONG_HOP_LE") {
    return (
      errObj?.thongDiep ||
      errObj?.message ||
      "Mã OTP khôi phục không hợp lệ, đã hết hạn hoặc đã được sử dụng."
    );
  }
  return errObj?.thongDiep || errObj?.message || fallback;
}

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
    const trimmedOtp = otp.trim();
    if (!/^\d{6}$/.test(trimmedOtp)) {
      setMessage({ type: "error", text: "Vui lòng nhập mã OTP hợp lệ gồm đúng 6 chữ số (0-9)." });
      return;
    }
    if (!isValidNewPassword) {
      setMessage({ type: "error", text: "Mật khẩu mới chưa đạt yêu cầu (tối thiểu 8 ký tự, có chữ hoa và số)." });
      return;
    }
    try {
      setLoading(true);
      setMessage(null);
      const result = await authService.resetPassword({
        email: email.trim().toLowerCase(),
        otp: trimmedOtp,
        matKhauMoi: newPassword,
      });
      // Khi đặt lại mật khẩu thành công, toàn bộ phiên cũ trên DB đã bị thu hồi (moc_thu_hoi + 1)
      useAuthStore.getState().clearAuth();
      setCsrfToken(null);
      setStep("done");
      toast.success(
        result.thongDiep || "Mật khẩu đã được đặt lại thành công! Bạn có thể đăng nhập bằng mật khẩu mới.",
        "Đổi mật khẩu thành công"
      );
      setMessage({
        type: "success",
        text:
          result.thongDiep ||
          "Mật khẩu đã được đặt lại thành công và các phiên cũ đã thu hồi! Bạn có thể đăng nhập bằng mật khẩu mới.",
      });
    } catch (err: unknown) {
      setMessage({
        type: "error",
        text: resolvePasswordResetErrorMessage(
          err,
          "Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."
        ),
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

        {/* Bước 3: Hoàn tất đặt lại mật khẩu */}
        {step === "done" && (
          <div style={{ textAlign: "center", marginTop: "0.75rem" }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                width: "100%",
                padding: "0.75rem",
                backgroundColor: "#0f3d32",
                color: "#ffffff",
                border: "none",
                borderRadius: "0.5rem",
                fontWeight: 600,
                fontSize: "0.95rem",
                cursor: "pointer",
              }}
            >
              Đăng nhập bằng mật khẩu mới →
            </button>
          </div>
        )}

        {/* Bước 2: Nhập OTP + mật khẩu mới */}
        {step === "otp" && (
          <form onSubmit={handleResetPassword}>
            <div style={{ marginBottom: "1rem" }}>
              <label
                style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#334155", marginBottom: "0.4rem" }}
              >
                Mã OTP (6 chữ số)
              </label>
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))}
                placeholder="123456"
                required
                autoFocus
                style={{
                  ...inputStyle,
                  letterSpacing: "0.3em",
                  fontWeight: 700,
                  textAlign: "center",
                  fontSize: "1.1rem",
                  borderColor: otp.length === 6 ? "#16a34a" : "#cbd5e1",
                  backgroundColor: otp.length === 6 ? "#f0fdf4" : "#ffffff",
                  transition: "all 0.2s ease",
                }}
              />
              {otp.length < 6 ? (
                <p style={{ margin: "6px 0 0", fontSize: "0.8rem", color: "#64748b" }}>
                  ℹ️ Vui lòng nhập đủ 6 chữ số OTP để tiếp tục đặt mật khẩu mới.
                </p>
              ) : (
                <p style={{ margin: "6px 0 0", fontSize: "0.8rem", color: "#16a34a", fontWeight: 600 }}>
                  ✓ Đã nhập đủ 6 chữ số. Hãy nhập mật khẩu mới bên dưới.
                </p>
              )}
            </div>

            {/* Ô nhập mật khẩu mới: Tự động trượt hiện ra khi người dùng gõ đủ 6 chữ số OTP */}
            {otp.length === 6 && (
              <div style={{ marginBottom: "0.75rem", animation: "fadeIn 0.25s ease-in" }}>
                <label
                  style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#334155", marginBottom: "0.4rem" }}
                >
                  Mật khẩu mới
                </label>
                <div className="icon-field">
                  <LockIcon />
                  <input
                    type={showPassword ? "text" : "password"}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Tối thiểu 8 ký tự, có chữ hoa và số"
                    autoFocus
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
                {/* Điều kiện mật khẩu mini */}
                <div style={{ display: "flex", gap: "10px", marginTop: "6px", fontSize: "12px", color: "#64748b" }}>
                  <span style={{ color: newPassword.length >= 8 ? "#16a34a" : "#94a3b8" }}>✓ 8+ ký tự</span>
                  <span style={{ color: /[A-Z]/.test(newPassword) ? "#16a34a" : "#94a3b8" }}>✓ Chữ hoa</span>
                  <span style={{ color: /[0-9]/.test(newPassword) ? "#16a34a" : "#94a3b8" }}>✓ Số</span>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || otp.length < 6 || !isValidNewPassword}
              style={{
                width: "100%",
                padding: "0.75rem",
                backgroundColor: "#131927",
                color: "#ffffff",
                border: "none",
                borderRadius: "0.5rem",
                fontWeight: 600,
                fontSize: "0.95rem",
                cursor: loading || otp.length < 6 || !isValidNewPassword ? "not-allowed" : "pointer",
                opacity: loading || otp.length < 6 || !isValidNewPassword ? 0.6 : 1,
                marginTop: "0.75rem",
                transition: "opacity 0.2s ease",
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
