import { useEffect, useState } from "react";
import { useSearchParams, useNavigate, Link } from "react-router";
import { authApi } from "@/lib/auth";
import {
  BrandHomeIcon,
  ShieldCheckIcon,
  AlertIcon,
} from "@/components/auth/AuthIcons";
import "./auth.css";

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  // Đọc params từ URL
  const tokenHashParam = searchParams.get("tokenHash") || searchParams.get("token_hash") || searchParams.get("token") || "";
  const initialEmail = searchParams.get("email") || "";

  const [email, setEmail] = useState(initialEmail);
  const [loading, setLoading] = useState(false);
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
          if (result.thanhCong) {
            setVerifySuccess(true);
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

  // Gửi lại email xác minh (POST /api/auth/email/verification-requests)
  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !email.includes("@")) {
      setMessage({ type: "error", text: "Vui lòng nhập địa chỉ email hợp lệ để nhận liên kết xác minh." });
      return;
    }

    try {
      setLoading(true);
      setMessage(null);

      const result = await authApi.resendVerification(email);
      setMessage({
        type: "success",
        text: result.thongDiep || "Đã tiếp nhận yêu cầu gửi lại email xác minh. Hãy kiểm tra hộp thư của bạn.",
      });
    } catch (err: unknown) {
      const errObj = err as { message?: string; thongDiep?: string };
      setMessage({
        type: "error",
        text: errObj?.message || errObj?.thongDiep || "Có lỗi xảy ra khi gửi lại email xác minh. Vui lòng thử lại sau.",
      });
    } finally {
      setLoading(false);
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
            <p>Hoàn tất xác thực tài khoản HomeFinder của bạn</p>
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
                Đang tiến hành xác minh liên kết email của bạn...
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
            /* Nếu chưa có token hoặc link hỏng -> Hiện form gửi lại email xác minh */
            !verifying && (
              <form onSubmit={handleResend} style={{ marginTop: "0.5rem" }} noValidate>
                <div className="field-group">
                  <label htmlFor="resendEmail">Địa chỉ email đã đăng ký</label>
                  <input
                    id="resendEmail"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@example.com"
                    className="text-field"
                    required
                  />
                </div>

                <p style={{ fontSize: "0.85rem", color: "#64748b", margin: "0.5rem 0 1.25rem", lineHeight: 1.5 }}>
                  Nếu bạn chưa nhận được thư kích hoạt hoặc liên kết đã hết hạn, vui lòng nhập email và bấm gửi lại để nhận liên kết mới.
                </p>

                <button
                  type="submit"
                  className="submit-button"
                  disabled={loading}
                >
                  {loading ? "Đang gửi yêu cầu..." : "Gửi lại email kích hoạt"}
                </button>
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
