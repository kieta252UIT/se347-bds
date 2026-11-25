import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { useAuthStore } from "@/stores/useAuthStore";
import { authService } from "@/services/authService";
import { setCsrfToken } from "@/services/api";
import { toast } from "@/stores/useToastStore";
import { EyeIcon, EyeOffIcon } from "@/components/auth/AuthIcons";
import "@/pages/public/property-detail-page.css";

function parseResetError(err: unknown, fallback: string): string {
  const errObj = err as {
    code?: string;
    maLoi?: string;
    message?: string;
    thongDiep?: string;
    status?: number;
  };
  const code = errObj?.code || errObj?.maLoi;
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
      "Mã OTP xác nhận không hợp lệ, đã hết hạn hoặc đã được sử dụng."
    );
  }
  return errObj?.thongDiep || errObj?.message || fallback;
}

export function ChangePasswordPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const accountEmail = user?.email?.trim().toLowerCase() || "";
  const [emailInput, setEmailInput] = useState("");
  const [step, setStep] = useState<"request" | "confirm">("request");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [feedback, setFeedback] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  const isValidNewPassword =
    newPassword.length >= 8 &&
    /[A-Z]/.test(newPassword) &&
    /[0-9]/.test(newPassword);

  const handleRequestOtp = async (e?: FormEvent) => {
    if (e) e.preventDefault();

    const normalizedInput = emailInput.trim().toLowerCase();
    if (!normalizedInput || !normalizedInput.includes("@")) {
      setFeedback({
        type: "error",
        text: "Vui lòng nhập địa chỉ email hợp lệ của tài khoản.",
      });
      return;
    }

    if (accountEmail && normalizedInput !== accountEmail) {
      setFeedback({
        type: "error",
        text: "Email bạn nhập không khớp với email của tài khoản đang đăng nhập.",
      });
      return;
    }

    try {
      setIsLoading(true);
      setFeedback(null);
      await authService.requestPasswordReset({ email: normalizedInput });
      setStep("confirm");
      setFeedback({
        type: "success",
        text: `Đã gửi mã xác thực OTP 6 chữ số tới email ${normalizedInput}. Vui lòng kiểm tra hộp thư.`,
      });
    } catch (err: unknown) {
      setFeedback({
        type: "error",
        text: parseResetError(
          err,
          "Không thể gửi mã xác thực lúc này. Vui lòng thử lại sau."
        ),
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleConfirmChange = async (e: FormEvent) => {
    e.preventDefault();
    const targetEmail = emailInput.trim().toLowerCase() || accountEmail;
    const trimmedOtp = otp.trim();
    if (!/^\d{6}$/.test(trimmedOtp)) {
      setFeedback({
        type: "error",
        text: "Vui lòng nhập mã OTP hợp lệ gồm đúng 6 chữ số (0–9).",
      });
      return;
    }
    if (!isValidNewPassword) {
      setFeedback({
        type: "error",
        text: "Mật khẩu mới cần tối thiểu 8 ký tự, bao gồm ít nhất 1 chữ in hoa và 1 chữ số.",
      });
      return;
    }
    if (newPassword !== confirmPassword) {
      setFeedback({
        type: "error",
        text: "Mật khẩu xác nhận không khớp với mật khẩu mới.",
      });
      return;
    }

    try {
      setIsLoading(true);
      setFeedback(null);
      const res = await authService.resetPassword({
        email: targetEmail,
        otp: trimmedOtp,
        matKhauMoi: newPassword,
      });

      // Theo hợp đồng bảo mật phiên: đặt lại mật khẩu thu hồi toàn bộ phiên hiện có (moc_thu_hoi + 1)
      useAuthStore.getState().clearAuth();
      setCsrfToken(null);
      toast.success(
        res.thongDiep ||
          "Đổi mật khẩu thành công! Vui lòng đăng nhập lại bằng mật khẩu mới.",
        "Đổi mật khẩu thành công"
      );
      navigate("/dang-nhap", { replace: true });
    } catch (err: unknown) {
      setFeedback({
        type: "error",
        text: parseResetError(
          err,
          "Không thể cập nhật mật khẩu mới. Vui lòng kiểm tra lại mã OTP."
        ),
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="spp-container">
      <div className="spp-header">
        <div>
          <p className="sp-eyebrow">Bảo mật tài khoản</p>
          <h1>Đổi mật khẩu</h1>
          <p>
            Xác thực qua mã OTP gửi về email đăng ký để cập nhật mật khẩu mới an
            toàn.
          </p>
        </div>
      </div>

      <div style={{ maxWidth: "560px", margin: "0 auto" }}>
        <section className="pdp-card">
          {feedback && (
            <div
              role="alert"
              style={{
                marginBottom: "18px",
                padding: "12px 14px",
                borderRadius: "10px",
                fontSize: "13.5px",
                lineHeight: 1.5,
                border:
                  feedback.type === "success"
                    ? "1px solid #b7e2d3"
                    : "1px solid #f2c6c6",
                background:
                  feedback.type === "success" ? "#f0faf6" : "#fff5f5",
                color: feedback.type === "success" ? "#08775f" : "#9e2a2b",
              }}
            >
              {feedback.text}
            </div>
          )}

          {step === "request" ? (
            <form
              onSubmit={handleRequestOtp}
              style={{ display: "grid", gap: "16px" }}
            >
              <div style={{ display: "grid", gap: "7px" }}>
                <label
                  htmlFor="cp-email"
                  style={{
                    fontSize: "15px",
                    fontWeight: 700,
                    color: "#1a3329",
                  }}
                >
                  Nhập lại email nhận mã xác thực
                </label>
                <input
                  id="cp-email"
                  type="email"
                  required
                  placeholder="Nhập email tài khoản đang đăng nhập..."
                  value={emailInput}
                  onChange={(e) => setEmailInput(e.target.value)}
                  style={{
                    height: "44px",
                    padding: "0 13px",
                    borderRadius: "9px",
                    border: "1px solid #d3e2db",
                    fontSize: "14px",
                  }}
                />
              </div>

              <p
                style={{
                  margin: 0,
                  color: "#556b61",
                  fontSize: "13.5px",
                  lineHeight: 1.6,
                }}
              >
                Để đảm bảo an toàn, hệ thống sẽ gửi mã OTP gồm 6 chữ số tới hộp
                thư bạn vừa xác nhận. Sau khi đổi mật khẩu thành công, các phiên
                đăng nhập cũ sẽ tự động đăng xuất để bảo vệ tài khoản.
              </p>

              <button
                type="submit"
                className="pdp-phone-call-btn"
                disabled={isLoading}
              >
                {isLoading
                  ? "Đang gửi mã OTP..."
                  : "Nhận mã xác thực OTP qua Email"}
              </button>
            </form>
          ) : (
            <form
              onSubmit={handleConfirmChange}
              style={{ display: "grid", gap: "15px" }}
            >
              <div style={{ display: "grid", gap: "6px" }}>
                <label
                  htmlFor="cp-otp"
                  style={{
                    fontSize: "13px",
                    fontWeight: 700,
                    color: "#1a3329",
                  }}
                >
                  Mã xác thực OTP (6 chữ số)
                </label>
                <input
                  id="cp-otp"
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="123456"
                  value={otp}
                  onChange={(e) =>
                    setOtp(e.target.value.replace(/\D/g, "").slice(0, 6))
                  }
                  style={{
                    height: "46px",
                    padding: "0 13px",
                    borderRadius: "9px",
                    border: "1px solid #d3e2db",
                    fontSize: "1.2rem",
                    letterSpacing: "0.35em",
                    textAlign: "center",
                    fontWeight: 700,
                  }}
                  autoFocus
                />
              </div>

              <div style={{ display: "grid", gap: "6px" }}>
                <label
                  htmlFor="cp-new-pass"
                  style={{
                    fontSize: "13px",
                    fontWeight: 700,
                    color: "#1a3329",
                  }}
                >
                  Mật khẩu mới
                </label>
                <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
                  <input
                    id="cp-new-pass"
                    type={showNewPassword ? "text" : "password"}
                    placeholder="Tối thiểu 8 ký tự, có chữ hoa và chữ số"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    style={{
                      width: "100%",
                      height: "44px",
                      padding: "0 42px 0 13px",
                      borderRadius: "9px",
                      border: "1px solid #d3e2db",
                      fontSize: "14px",
                      boxSizing: "border-box",
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPassword((prev) => !prev)}
                    aria-label={showNewPassword ? "Ẩn mật khẩu mới" : "Hiện mật khẩu mới"}
                    style={{
                      position: "absolute",
                      right: "10px",
                      border: 0,
                      background: "transparent",
                      color: "#5b7067",
                      display: "grid",
                      placeItems: "center",
                      padding: "4px",
                      cursor: "pointer",
                    }}
                  >
                    {showNewPassword ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                </div>
              </div>

              <div style={{ display: "grid", gap: "6px" }}>
                <label
                  htmlFor="cp-confirm-pass"
                  style={{
                    fontSize: "13px",
                    fontWeight: 700,
                    color: "#1a3329",
                  }}
                >
                  Nhập lại mật khẩu mới
                </label>
                <div style={{ position: "relative", display: "flex", alignItems: "center" }}>
                  <input
                    id="cp-confirm-pass"
                    type={showConfirmPassword ? "text" : "password"}
                    placeholder="Xác nhận lại mật khẩu mới"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    style={{
                      width: "100%",
                      height: "44px",
                      padding: "0 42px 0 13px",
                      borderRadius: "9px",
                      border: "1px solid #d3e2db",
                      fontSize: "14px",
                      boxSizing: "border-box",
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword((prev) => !prev)}
                    aria-label={
                      showConfirmPassword ? "Ẩn mật khẩu xác nhận" : "Hiện mật khẩu xác nhận"
                    }
                    style={{
                      position: "absolute",
                      right: "10px",
                      border: 0,
                      background: "transparent",
                      color: "#5b7067",
                      display: "grid",
                      placeItems: "center",
                      padding: "4px",
                      cursor: "pointer",
                    }}
                  >
                    {showConfirmPassword ? <EyeOffIcon /> : <EyeIcon />}
                  </button>
                </div>
              </div>

              <div
                style={{
                  display: "flex",
                  gap: "10px",
                  flexWrap: "wrap",
                  marginTop: "4px",
                }}
              >
                <button
                  type="submit"
                  className="pdp-phone-call-btn"
                  style={{ flex: 1 }}
                  disabled={isLoading}
                >
                  {isLoading ? "Đang cập nhật..." : "Xác nhận đổi mật khẩu"}
                </button>
                <button
                  type="button"
                  className="pdp-copy-phone-btn"
                  style={{ width: "auto", padding: "0 16px" }}
                  disabled={isLoading}
                  onClick={() => void handleRequestOtp()}
                >
                  Gửi lại OTP
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </div>
  );
}
