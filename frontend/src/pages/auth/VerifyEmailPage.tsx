import { useEffect, useState } from "react";
import { useSearchParams, useLocation, useNavigate, Link } from "react-router";
import { authApi } from "@/lib/auth";
import { useAuthStore } from "@/stores/useAuthStore";
import { toast } from "@/stores/useToastStore";
import {
  BrandHomeIcon,
  ShieldCheckIcon,
  AlertIcon,
} from "@/components/auth/AuthIcons";
import "./auth.css";

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();

  // Hỗ trợ đọc tokenHash từ cả query string (?token_hash=...) và URL fragment (#token_hash=...)
  const hashParams = new URLSearchParams(location.hash.replace(/^#/, ""));
  const tokenHashParam =
    searchParams.get("tokenHash") ||
    searchParams.get("token_hash") ||
    searchParams.get("token") ||
    hashParams.get("tokenHash") ||
    hashParams.get("token_hash") ||
    hashParams.get("token") ||
    "";
  const initialEmail = searchParams.get("email") || hashParams.get("email") || "";

  const [email, setEmail] = useState(initialEmail);
  const [otp, setOtp] = useState("");
  const [showResendEmailInput, setShowResendEmailInput] = useState(false);
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [verifying, setVerifying] = useState(Boolean(tokenHashParam));
  const [verifySuccess, setVerifySuccess] = useState(false);
  const [message, setMessage] = useState<{ type: "error" | "success"; text: string } | null>(null);

  // Tự động kích hoạt xác minh khi URL có tokenHash
  useEffect(() => {
    if (!tokenHashParam) return;

    let isMounted = true;
    const executeVerify = async () => {
      try {
        setVerifying(true);
        setMessage(null);

        // Gọi API hoàn tất xác minh (POST /api/auth/email/verify)
        const result = await authApi.verifyEmail(tokenHashParam);

        if (isMounted) {
          const isSuccess = result.trangThai === "da_xac_minh" || result.thanhCong === true;
          if (isSuccess) {
            // Backend đã thu hồi phiên hiện tại trên browser (nếu có) và yêu cầu đăng nhập lại (canDangNhapLai = true)
            useAuthStore.getState().clearAuth();
            setVerifySuccess(true);
            toast.success(
              result.thongDiep || "Xác minh email thành công! Tài khoản của bạn đã được kích hoạt.",
              "Xác minh thành công"
            );
            setMessage({
              type: "success",
              text: result.thongDiep || "Xác minh email thành công! Tài khoản của bạn đã được kích hoạt.",
            });
          } else {
            setMessage({
              type: "error",
              text: result.thongDiep || "Liên kết xác minh không hợp lệ hoặc đã hết hạn.",
            });
          }
        }
      } catch (err: unknown) {
        if (isMounted) {
          const errObj = err as { message?: string; thongDiep?: string };
          setMessage({
            type: "error",
            text: errObj?.message || errObj?.thongDiep || "Liên kết xác minh không hợp lệ hoặc đã hết hạn.",
          });
        }
      } finally {
        if (isMounted) {
          setVerifying(false);
        }
      }
    };

    executeVerify();

    return () => {
      isMounted = false;
    };
  }, [tokenHashParam]);

  // Xác minh bằng mã OTP 6 chữ số (POST /api/auth/email/verify)
  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedEmail = email.trim().toLowerCase();
    const trimmedOtp = otp.trim();

    if (!/^\d{6}$/.test(trimmedOtp)) {
      setMessage({ type: "error", text: "Mã OTP xác minh phải gồm đúng 6 chữ số (0-9)." });
      return;
    }

    if (!trimmedEmail || !trimmedEmail.includes("@")) {
      setShowResendEmailInput(true);
      setMessage({ type: "error", text: "Vui lòng nhập địa chỉ email đã đăng ký." });
      return;
    }

    try {
      setLoading(true);
      setMessage(null);

      const result = await authApi.verifyEmail(undefined, trimmedOtp, trimmedEmail);
      const isSuccess = result.trangThai === "da_xac_minh" || result.thanhCong === true;

      if (isSuccess) {
        // Đồng bộ trạng thái: xóa phiên cũ trên client vì backend đã thu hồi phiên hiện tại (canDangNhapLai = true)
        useAuthStore.getState().clearAuth();
        setVerifySuccess(true);
        toast.success(
          result.thongDiep || "Xác minh email thành công! Tài khoản của bạn đã được kích hoạt.",
          "Xác minh thành công"
        );
        setMessage({
          type: "success",
          text: result.thongDiep || "Xác minh email thành công! Tài khoản của bạn đã được kích hoạt.",
        });
      } else {
        setMessage({
          type: "error",
          text: result.thongDiep || "Mã OTP không hợp lệ hoặc đã hết hạn.",
        });
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string; thongDiep?: string };
      setMessage({
        type: "error",
        text: errObj?.message || errObj?.thongDiep || "Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng kiểm tra lại.",
      });
    } finally {
      setLoading(false);
    }
  };

  // Gửi lại email / mã OTP xác minh (POST /api/auth/email/verification-requests)
  const handleResend = async () => {
    if (!showResendEmailInput) {
      setShowResendEmailInput(true);
      setMessage(null);
      return;
    }

    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail || !trimmedEmail.includes("@")) {
      setMessage({ type: "error", text: "Vui lòng nhập địa chỉ email hợp lệ để nhận lại mã xác minh." });
      return;
    }

    try {
      setResending(true);
      setMessage(null);

      const result = await authApi.resendVerification(trimmedEmail);
      setMessage({
        type: "success",
        text: result.thongDiep || "Đã gửi lại mã OTP xác minh tới email của bạn. Hãy kiểm tra hộp thư.",
      });
    } catch (err: unknown) {
      const errObj = err as { message?: string; thongDiep?: string };
      setMessage({
        type: "error",
        text: errObj?.message || errObj?.thongDiep || "Có lỗi xảy ra khi gửi lại mã xác minh. Vui lòng thử lại sau.",
      });
    } finally {
      setResending(false);
    }
  };

  return (
    <main className="login-page">
      {/* Brand panel bên trái */}
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
            Xác minh tài khoản
            <br />
            bất động sản
          </h1>

          <p className="brand-desc">
            Xác minh địa chỉ email để bảo mật tài khoản và bắt đầu đăng tin, nhận gợi ý cá nhân hóa.
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
              <span>Xác thực email danh tính chính chủ</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Bảo vệ tài khoản và lịch sử giao dịch</span>
            </div>
            <div className="brand-trust-item">
              <ShieldCheckIcon />
              <span>Kích hoạt quyền đăng tin và mua gói</span>
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
            <h2>Xác minh địa chỉ email</h2>
            <p>Nhập mã OTP 6 chữ số đã được gửi tới hộp thư của bạn</p>
          </header>

          {/* Đang xác minh tự động từ liên kết email */}
          {verifying && (
            <div
              style={{
                padding: "1.25rem",
                backgroundColor: "#f0fdf4",
                border: "1px solid #bbf7d0",
                borderRadius: "8px",
                textAlign: "center",
                marginBottom: "1.25rem",
              }}
            >
              <div style={{ fontSize: "1.75rem", marginBottom: "0.4rem" }}>⏳</div>
              <p style={{ margin: 0, fontWeight: 600, color: "#166534", fontSize: "0.95rem" }}>
                Đang tiến hành xác minh email của bạn...
              </p>
            </div>
          )}

          {/* Thông báo thông điệp */}
          {message && !verifying && (
            <div className={`notice ${message.type === "success" ? "success" : ""}`} role="status">
              <AlertIcon />
              <div style={{ flex: 1 }}>
                <span>{message.text}</span>
              </div>
            </div>
          )}

          {/* Nếu xác minh thành công -> Hiện nút đăng nhập */}
          {verifySuccess ? (
            <div style={{ textAlign: "center", marginTop: "1.5rem" }}>
              <button
                type="button"
                className="submit-button"
                onClick={() => navigate("/dang-nhap")}
                style={{ backgroundColor: "#0f3d32" }}
              >
                Đăng nhập ngay vào hệ thống →
              </button>
            </div>
          ) : (
            /* Form nhập mã OTP 6 chữ số; ô nhập email chỉ xuất hiện khi người dùng bấm gửi lại mã xác minh */
            !verifying && (
              <form onSubmit={handleVerifyOtp} style={{ marginTop: "0.5rem" }} noValidate>
                <div className="field-group">
                  <label htmlFor="verifyOtp">Mã xác minh OTP (6 chữ số) *</label>
                  <input
                    id="verifyOtp"
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
                    autoFocus
                    required
                  />
                </div>

                <p style={{ fontSize: "0.85rem", color: "#64748b", margin: "0.6rem 0 1.25rem", lineHeight: 1.5 }}>
                  Kiểm tra hộp thư đến (hoặc mục Spam){email ? ` của ${email}` : ""} để lấy mã OTP gồm 6 chữ số (có hiệu lực trong 60 phút) và nhập vào ô trên.
                </p>

                <button
                  type="submit"
                  className="submit-button"
                  disabled={loading || resending}
                >
                  {loading ? "Đang xác minh..." : "Xác nhận mã OTP"}
                </button>

                {showResendEmailInput && (
                  <div
                    style={{
                      marginTop: "1.25rem",
                      paddingTop: "1rem",
                      borderTop: "1px solid #e2e8f0",
                    }}
                  >
                    <div className="field-group">
                      <label htmlFor="verifyEmail">Địa chỉ email nhận mã xác minh *</label>
                      <input
                        id="verifyEmail"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="name@example.com"
                        className="text-field"
                        required
                      />
                    </div>
                    <button
                      type="button"
                      onClick={handleResend}
                      disabled={resending || loading}
                      className="submit-button"
                      style={{
                        marginTop: "0.75rem",
                        backgroundColor: "#334155",
                      }}
                    >
                      {resending ? "Đang gửi lại..." : "Gửi lại mã tới email này"}
                    </button>
                  </div>
                )}

                {!showResendEmailInput && (
                  <div style={{ textAlign: "center", marginTop: "1rem", fontSize: "0.88rem", color: "#64748b" }}>
                    Chưa nhận được mã hoặc mã đã hết hạn?{" "}
                    <button
                      type="button"
                      onClick={handleResend}
                      disabled={resending || loading}
                      className="register-now-btn"
                    >
                      Gửi lại mã xác minh
                    </button>
                  </div>
                )}
              </form>
            )
          )}

          <div className="auth-footer-divider">
            <p>
              Đã có tài khoản đã kích hoạt?{" "}
              <Link to="/dang-nhap" className="register-now-btn">
                Đăng nhập
              </Link>
              {" "}hoặc{" "}
              <Link to="/dang-ky" className="register-now-btn">
                Đăng ký tài khoản khác
              </Link>
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}

export default VerifyEmailPage;
