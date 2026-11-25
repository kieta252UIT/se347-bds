import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import "@/pages/public/property-detail-page.css";

function formatRoleVi(role?: string): string {
  if (!role) return "Người dùng";
  const upper = role.toUpperCase();
  if (upper.includes("ADMIN") || upper.includes("QUAN_TRI")) {
    return "Quản trị viên";
  }
  if (upper.includes("KDV") || upper.includes("KIEM_DUYET")) {
    return "Kiểm duyệt viên";
  }
  return "Người dùng";
}

function formatStatusVi(status?: string): string {
  if (status === "bi_khoa") return "Bị khóa";
  return "Đang hoạt động";
}

export function ProfilePage() {
  const { user } = useAuth();

  const displayName = user?.hoTen?.trim() || "Chưa cập nhật họ tên";
  const initialChar =
    (user?.hoTen || user?.email || "U")
      .trim()
      .split(/\s+/)
      .pop()
      ?.charAt(0)
      .toUpperCase() || "U";

  return (
    <div className="spp-container">
      <div className="spp-header">
        <div>
          <p className="sp-eyebrow">Hồ sơ tài khoản</p>
          <h1>Thông tin cá nhân</h1>
          <p>
            Thông tin định danh và trạng thái tài khoản của bạn trên hệ thống
            HomeFinder.
          </p>
        </div>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))",
          gap: "22px",
          alignItems: "start",
        }}
      >
        <section className="pdp-card">
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "18px",
              paddingBottom: "20px",
              marginBottom: "20px",
              borderBottom: "1px solid #e8f0ec",
            }}
          >
            <div
              style={{
                width: "68px",
                height: "68px",
                borderRadius: "50%",
                background: "linear-gradient(135deg, #08775f, #14967a)",
                color: "#ffffff",
                display: "grid",
                placeItems: "center",
                fontSize: "26px",
                fontWeight: 800,
                flexShrink: 0,
              }}
            >
              {initialChar}
            </div>
            <div>
              <h2
                style={{
                  margin: "0 0 6px",
                  fontSize: "20px",
                  color: "#12271f",
                }}
              >
                {displayName}
              </h2>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
                <span className="pdp-owner-verified">
                  {user?.emailDaXacMinh
                    ? "✓ Đã xác minh email"
                    : "Chưa xác minh email"}
                </span>
                <span
                  style={{
                    display: "inline-block",
                    marginTop: "4px",
                    padding: "2px 9px",
                    borderRadius: "999px",
                    background: "#eef2f6",
                    color: "#334155",
                    fontSize: "11px",
                    fontWeight: 700,
                  }}
                >
                  {formatRoleVi(user?.vaiTro)}
                </span>
              </div>
            </div>
          </div>

          <div
            className="pdp-specs-grid"
            style={{ gridTemplateColumns: "1fr" }}
          >
            <div className="pdp-spec-item">
              <span>Họ và tên</span>
              <strong>{displayName}</strong>
            </div>
            <div className="pdp-spec-item">
              <span>Địa chỉ Email</span>
              <strong>{user?.email || "—"}</strong>
            </div>
            <div className="pdp-spec-item">
              <span>Số điện thoại liên hệ</span>
              <strong>{user?.soDienThoai || "Chưa cập nhật"}</strong>
            </div>
            <div className="pdp-spec-item">
              <span>Trạng thái tài khoản</span>
              <strong style={{ color: "#08775f" }}>
                {formatStatusVi(user?.trangThai)}
              </strong>
            </div>
            <div className="pdp-spec-item">
              <span>Mã tài khoản</span>
              <strong style={{ fontSize: "12px", fontFamily: "monospace" }}>
                {user?.id || "—"}
              </strong>
            </div>
          </div>
        </section>

        <section className="pdp-card">
          <h2 className="pdp-section-title">Lối tắt quản lý tài khoản</h2>
          <p
            style={{
              margin: "0 0 16px",
              color: "#64786f",
              fontSize: "13.5px",
              lineHeight: 1.55,
            }}
          >
            Truy cập nhanh các tiện ích cá nhân, danh sách bất động sản yêu
            thích và bảo mật tài khoản.
          </p>

          <div style={{ display: "grid", gap: "10px" }}>
            <Link
              to="/tin-da-luu"
              className="pdp-copy-phone-btn"
              style={{
                textDecoration: "none",
                justifyContent: "space-between",
                padding: "0 16px",
              }}
            >
              <span>Tin bất động sản đã lưu</span>
              <span>→</span>
            </Link>
            <Link
              to="/quan-ly-tin"
              className="pdp-copy-phone-btn"
              style={{
                textDecoration: "none",
                justifyContent: "space-between",
                padding: "0 16px",
              }}
            >
              <span>Quản lý tin đăng của tôi</span>
              <span>→</span>
            </Link>
            <Link
              to="/vi-tien"
              className="pdp-copy-phone-btn"
              style={{
                textDecoration: "none",
                justifyContent: "space-between",
                padding: "0 16px",
              }}
            >
              <span>Nạp ví & Gói đẩy tin</span>
              <span>→</span>
            </Link>
            <Link
              to="/doi-mat-khau"
              className="pdp-copy-phone-btn"
              style={{
                textDecoration: "none",
                justifyContent: "space-between",
                padding: "0 16px",
              }}
            >
              <span>Đổi mật khẩu bảo mật</span>
              <span>→</span>
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
