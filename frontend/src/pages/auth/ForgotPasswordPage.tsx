import React, { useState } from "react";
import { Link, useNavigate, useSearchParams, useLocation } from "react-router";
import { authService } from "@/services/authService";
import { setCsrfToken } from "@/services/api";
import { useAuthStore } from "@/stores/useAuthStore";
import { toast } from "@/stores/useToastStore";
import {
  BrandHomeIcon,
  ShieldCheckIcon,
  LockIcon,
  EyeIcon,
  EyeOffIcon,
  AlertIcon,
} from "@/components/auth/AuthIcons";
import "./auth.css";

function resolveResetErrorMessage(err: unknown, fallback: string): string {
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
      "Mã OTP hoặc liên kết khôi phục không hợp lệ, đã hết hạn hoặc đã được sử dụng."
    );
  }
  return errObj?.thongDiep || errObj?.message || fallback;
}

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();

  // Hỗ trợ nhận token_hash từ URL query (?token_hash=...) hoặc hash (#token_hash=...) theo api-specification §8.5
  const hashParams = new URLSearchParams(location.hash.replace(/^#/, ""));
  const tokenHashFromUrl =
    searchParams.get("tokenHash") ||
    searchParams.get("token_hash") ||
    searchParams.get("token") ||
    hashParams.get("tokenHash") ||
    hashParams.get("token_hash") ||
    hashParams.get("token") ||
    "";

  const [step, setStep] = useState<"email" | "otp" | "done">(tokenHashFromUrl ? "otp" : "email");
  const [email, setEmail] = useState(searchParams.get("email") || hashParams.get("email") || "");
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
      const res = await authService.requestPasswordReset({ email: trimmedEmail });
      setMessage({
        type: "success",
        text: res.thongDiep || "Mã OTP 6 chữ số đã được gửi tới email của bạn. Hãy kiểm tra hộp thư.",
      });
      setStep("otp");
    } catch (err: unknown) {
      setMessage({
        type: "error",
        text: resolveResetErrorMessage(err, "Có lỗi xảy ra. Vui lòng thử lại sau."),
      });
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedOtp = otp.trim();
    if (!tokenHashFromUrl && !/^\d{6}$/.test(trimmedOtp)) {
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
      const result = await authService.resetPassword({
        email: email.trim() ? email.trim().toLowerCase() : undefined,
        otp: !tokenHashFromUrl ? trimmedOtp : undefined,
        tokenHash: tokenHashFromUrl || trimmedOtp,
        matKhauMoi: newPassword,
      });
      // Thu hồi trạng thái phiên cục bộ vì Backend đã tăng moc_thu_hoi và thu hồi mọi phiên cũ
      useAuthStore.getState().clearAuth();
      setCsrfToken(null);
      setStep("done");
      toast.success(
        result.thongDiep || "Đặt lại mật khẩu thành công! Hãy đăng nhập lại bằng mật khẩu mới của bạn.",
        "Đổi mật khẩu thành công"
      );
      setMessage({
        type: "success",
        text:
          result.thongDiep ||
          "Đặt lại mật khẩu thành công! Tất cả phiên cũ đã được thu hồi. Hãy đăng nhập lại bằng mật khẩu mới.",
      });
    } catch (err: unknown) {
      setMessage({
        type: "error",
        text: resolveResetErrorMessage(
          err,
          "Mã OTP hoặc liên kết không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."
        ),
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
              {!tokenHashFromUrl && (
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
                      borderColor: otp.length === 6 ? "#16a34a" : undefined,
                      backgroundColor: otp.length === 6 ? "#f0fdf4" : undefined,
                      transition: "all 0.2s ease",
                    }}
                    required
                  />
                  {otp.length < 6 ? (
                    <p style={{ margin: "6px 0 0", fontSize: "0.82rem", color: "#64748b" }}>
                      ℹ️ Vui lòng nhập đủ 6 chữ số OTP để tiếp tục đặt mật khẩu mới.
                    </p>
                  ) : (
                    <p style={{ margin: "6px 0 0", fontSize: "0.82rem", color: "#16a34a", fontWeight: 600 }}>
                      ✓ Đã nhập đủ 6 chữ số. Hãy nhập mật khẩu mới bên dưới.
                    </p>
                  )}
                </div>
              )}

              {/* Ô nhập mật khẩu mới: Hiện ra khi có token từ link hoặc khi người dùng đã gõ đủ 6 số OTP */}
              {(tokenHashFromUrl || otp.length === 6) && (
                <div className="field-group" style={{ marginTop: tokenHashFromUrl ? 0 : "1.25rem" }}>
                  <label htmlFor="newPassword">Mật khẩu mới *</label>
                  <div className="icon-field">
                    <LockIcon />
                    <input
                      id="newPassword"
                      type={showPassword ? "text" : "password"}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Tối thiểu 8 ký tự, có chữ hoa và số"
                      autoFocus={!tokenHashFromUrl}
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
              )}

              <button
                type="submit"
                className="submit-button"
                disabled={loading || (!tokenHashFromUrl && otp.length < 6) || !isValidNewPassword}
                style={{
                  marginTop: "1.25rem",
                  opacity: loading || (!tokenHashFromUrl && otp.length < 6) || !isValidNewPassword ? 0.6 : 1,
                  cursor: loading || (!tokenHashFromUrl && otp.length < 6) || !isValidNewPassword ? "not-allowed" : "pointer",
                }}
              >
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
