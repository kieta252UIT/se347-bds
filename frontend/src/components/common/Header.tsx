import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useLocation, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { toast } from "@/stores/useToastStore";

function HomeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m3 11 9-7 9 7" />
      <path d="M5 10v10h14V10M9 20v-6h6v6" />
    </svg>
  );
}

function BellIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}

function UserProfileIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="8" r="4" />
      <path d="M5.5 21a6.5 6.5 0 0 1 13 0" />
    </svg>
  );
}

function SavedHeartIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20.8 8.5c0 5.5-8.8 10.4-8.8 10.4S3.2 14 3.2 8.5A4.3 4.3 0 0 1 11 6l1 1.2L13 6a4.3 4.3 0 0 1 7.8 2.5Z" />
    </svg>
  );
}

function WalletMenuIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="2.5" y="5.5" width="19" height="14" rx="2.5" />
      <path d="M16 12.5h5.5" />
      <circle cx="16.5" cy="12.5" r="1" fill="currentColor" />
      <path d="M6 5.5V4.2A1.7 1.7 0 0 1 7.7 2.5h9.6" />
    </svg>
  );
}

function KeyPasswordIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="7.5" cy="15.5" r="5.5" />
      <path d="m21 2-9.6 9.6" />
      <path d="m15.5 7.5 3 3L22 7l-3-3" />
    </svg>
  );
}

function LogoutMenuIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  );
}

function getAvatarInitial(nameOrEmail?: string): string {
  if (!nameOrEmail) return "U";
  const trimmed = nameOrEmail.trim();
  if (!trimmed) return "U";
  const words = trimmed.split(/\s+/);
  const target = words[words.length - 1] || trimmed;
  return target.charAt(0).toUpperCase();
}

export function Header() {
  const { user, isAuthenticated, isKdv, isAdmin, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const [openDropdown, setOpenDropdown] = useState<"notif" | "user" | null>(null);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const actionsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setOpenDropdown(null);
  }, [location.pathname]);

  useEffect(() => {
    if (!openDropdown) return;

    const handleOutsideClick = (e: PointerEvent) => {
      if (!actionsRef.current?.contains(e.target as Node)) {
        setOpenDropdown(null);
      }
    };
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOpenDropdown(null);
      }
    };

    document.addEventListener("pointerdown", handleOutsideClick);
    document.addEventListener("keydown", handleEsc);
    return () => {
      document.removeEventListener("pointerdown", handleOutsideClick);
      document.removeEventListener("keydown", handleEsc);
    };
  }, [openDropdown]);

  useEffect(() => {
    if (!logoutConfirmOpen) return;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prevOverflow;
    };
  }, [logoutConfirmOpen]);

  const isActive = (path: string) => {
    if (path === "/") return location.pathname === "/";
    return location.pathname.startsWith(path);
  };

  const displayName = user?.hoTen?.trim() || user?.email || "Tài khoản của bạn";
  const avatarInitial = getAvatarInitial(user?.hoTen || user?.email);

  const handleRequestLogout = () => {
    setOpenDropdown(null);
    setLogoutConfirmOpen(true);
  };

  const handleConfirmLogout = async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    try {
      await logout();
      setLogoutConfirmOpen(false);
      toast.info("Bạn đã đăng xuất khỏi tài khoản.", "Đăng xuất thành công");
      navigate("/");
    } finally {
      setIsLoggingOut(false);
    }
  };

  return (
    <header className="hf-header">
      <Link className="hf-brand" to="/" aria-label="HomeFinder - Trang chủ">
        <span>
          <HomeIcon />
        </span>
        HomeFinder
      </Link>

      <nav className="hf-nav" aria-label="Điều hướng chính">
        <Link to="/" className={isActive("/") ? "active" : ""}>
          Trang chủ
        </Link>
        <Link to="/tim-kiem" className={isActive("/tim-kiem") ? "active" : ""}>
          Tìm kiếm
        </Link>
        <Link to="/quan-ly-tin" className={isActive("/quan-ly-tin") ? "active" : ""}>
          Quản lý tin đăng
        </Link>

        {isKdv && (
          <Link to="/kdv/dashboard" className={isActive("/kdv") ? "active" : ""}>
            Cổng Kiểm duyệt viên
          </Link>
        )}
        {isAdmin && (
          <Link to="/admin/dashboard" className={isActive("/admin") ? "active" : ""}>
            Cổng Quản trị viên
          </Link>
        )}
      </nav>

      <div className="hf-header-right" ref={actionsRef}>
        {isAuthenticated ? (
          <div className="hf-user-controls">
            {/* Nút chuông thông báo */}
            <div className="hf-dropdown-anchor">
              <button
                type="button"
                className={`hf-notif-btn ${openDropdown === "notif" ? "active" : ""}`}
                onClick={() =>
                  setOpenDropdown((curr) => (curr === "notif" ? null : "notif"))
                }
                aria-label="Thông báo"
                aria-expanded={openDropdown === "notif"}
                title="Thông báo"
              >
                <BellIcon />
              </button>

              {openDropdown === "notif" && (
                <div
                  className="hf-notif-popover"
                  role="region"
                  aria-label="Danh sách thông báo"
                >
                  <div className="hf-notif-header">Thông báo</div>
                  <div className="hf-notif-empty">
                    Hiện chưa có thông báo mới.
                  </div>
                </div>
              )}
            </div>

            {/* Nút hình tròn chứa Avatar + Menu tài khoản */}
            <div className="hf-dropdown-anchor">
              <button
                type="button"
                className={`hf-avatar-btn ${openDropdown === "user" ? "active" : ""}`}
                onClick={() =>
                  setOpenDropdown((curr) => (curr === "user" ? null : "user"))
                }
                aria-label={`Menu tài khoản ${displayName}`}
                aria-expanded={openDropdown === "user"}
                aria-haspopup="menu"
                title={displayName}
              >
                <span>{avatarInitial}</span>
              </button>

              {openDropdown === "user" && (
                <div
                  className="hf-user-menu"
                  role="menu"
                  aria-label="Tùy chọn tài khoản"
                >
                  <div className="hf-user-menu-header">
                    <strong>{displayName}</strong>
                  </div>

                  <Link
                    to="/thong-tin-ca-nhan"
                    role="menuitem"
                    className="hf-user-menu-item"
                    onClick={() => setOpenDropdown(null)}
                  >
                    <UserProfileIcon />
                    <span>Thông tin cá nhân</span>
                  </Link>

                  <Link
                    to="/tin-da-luu"
                    role="menuitem"
                    className="hf-user-menu-item"
                    onClick={() => setOpenDropdown(null)}
                  >
                    <SavedHeartIcon />
                    <span>Tin đã lưu</span>
                  </Link>

                  <Link
                    to="/vi-tien"
                    role="menuitem"
                    className="hf-user-menu-item"
                    onClick={() => setOpenDropdown(null)}
                  >
                    <WalletMenuIcon />
                    <span>Nạp ví</span>
                  </Link>

                  <Link
                    to="/doi-mat-khau"
                    role="menuitem"
                    className="hf-user-menu-item"
                    onClick={() => setOpenDropdown(null)}
                  >
                    <KeyPasswordIcon />
                    <span>Đổi mật khẩu</span>
                  </Link>

                  <button
                    type="button"
                    role="menuitem"
                    className="hf-user-menu-item logout"
                    onClick={handleRequestLogout}
                  >
                    <LogoutMenuIcon />
                    <span>Đăng xuất</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        ) : (
          <Link className="hf-login-btn" to="/dang-nhap">
            Đăng nhập
          </Link>
        )}
      </div>

      {logoutConfirmOpen &&
        createPortal(
          <div
            className="hf-confirm-backdrop"
            role="dialog"
            aria-modal="true"
            aria-labelledby="hf-logout-dialog-title"
          >
            <div className="hf-confirm-modal">
              <h3 id="hf-logout-dialog-title">Xác nhận đăng xuất</h3>
              <p>
                Bạn có chắc chắn muốn đăng xuất khỏi tài khoản{" "}
                <strong>{displayName}</strong> không?
              </p>
              <div className="hf-confirm-actions">
                <button
                  type="button"
                  className="hf-confirm-cancel"
                  disabled={isLoggingOut}
                  onClick={() => setLogoutConfirmOpen(false)}
                >
                  Hủy
                </button>
                <button
                  type="button"
                  className="hf-confirm-logout"
                  disabled={isLoggingOut}
                  onClick={handleConfirmLogout}
                >
                  {isLoggingOut ? "Đang đăng xuất..." : "Đăng xuất"}
                </button>
              </div>
            </div>
          </div>,
          document.body
        )}
    </header>
  );
}
