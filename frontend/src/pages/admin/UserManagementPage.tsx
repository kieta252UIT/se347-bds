import { useState } from "react";
import { toast } from "@/stores/useToastStore";

interface ManagedAccount {
  id: string;
  email: string;
  hoTen: string;
  soDienThoai: string;
  vaiTro: "nguoi_dung" | "kiem_duyet_vien" | "quan_tri_vien";
  trangThai: "hoat_dong" | "bi_khoa";
  emailDaXacMinh: boolean;
  mocThuHoi: number;
  lockProposalFromKdv?: string;
  externalAppealNote?: string;
}

const initialAccounts: ManagedAccount[] = [
  {
    id: "bfd23ad3-a23e-4b36-b736-a947d8237389",
    email: "khanhhbdt123@gmail.com",
    hoTen: "Trần Quốc Khánh",
    soDienThoai: "0912345678",
    vaiTro: "quan_tri_vien",
    trangThai: "hoat_dong",
    emailDaXacMinh: true,
    mocThuHoi: 0,
  },
  {
    id: "f333a13a-83d5-401c-8045-29ea59a2950b",
    email: "caok104@gmail.com",
    hoTen: "Kiểm Duyệt Viên Cao",
    soDienThoai: "0901234567",
    vaiTro: "kiem_duyet_vien",
    trangThai: "hoat_dong",
    emailDaXacMinh: true,
    mocThuHoi: 0,
  },
  {
    id: "208d0f5f-99b8-42d3-8e89-b30d1e1f861e",
    email: "murafbest2@gmail.com",
    hoTen: "Phạm Khoa",
    soDienThoai: "0938112233",
    vaiTro: "nguoi_dung",
    trangThai: "hoat_dong",
    emailDaXacMinh: true,
    mocThuHoi: 1,
  },
  {
    id: "77a19012-41bc-4e92-8812-301928371201",
    email: "canho.dongnam@gmail.com",
    hoTen: "Trương Hoàng Long",
    soDienThoai: "0977231098",
    vaiTro: "nguoi_dung",
    trangThai: "hoat_dong",
    emailDaXacMinh: true,
    mocThuHoi: 0,
    lockProposalFromKdv: "Đề xuất từ Kiểm duyệt viên (BC-8810): Tái phạm đăng tin trùng lặp và giá ảo 4 lần trong tháng.",
  },
  {
    id: "89b44102-1190-4a10-9102-884120391044",
    email: "hoangminh.land@gmail.com",
    hoTen: "Hoàng Minh",
    soDienThoai: "0965110922",
    vaiTro: "nguoi_dung",
    trangThai: "bi_khoa",
    emailDaXacMinh: true,
    mocThuHoi: 3,
    externalAppealNote: "Email kháng cáo ngày 18/11: Đã cung cấp bản sao công chứng giấy ủy quyền môi giới hợp lệ.",
  },
];

const roleDisplayLabel: Record<ManagedAccount["vaiTro"], string> = {
  quan_tri_vien: "Quản trị viên",
  kiem_duyet_vien: "Kiểm duyệt viên",
  nguoi_dung: "Người dùng",
};

export function UserManagementPage() {
  const [accounts, setAccounts] = useState<ManagedAccount[]>(initialAccounts);
  const [tabFilter, setTabFilter] = useState<"ALL" | "PROPOSED_LOCK" | "LOCKED" | "STAFF">("ALL");
  const [roleFilter, setRoleFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState("");

  // Modal Tạo & Phân quyền tài khoản
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [newEmail, setNewEmail] = useState("");
  const [newName, setNewName] = useState("");
  const [newPhone, setNewPhone] = useState("");
  const [newRole, setNewRole] = useState<ManagedAccount["vaiTro"]>("kiem_duyet_vien");

  // Modal Cập nhật & Phân quyền tài khoản
  const [editingAccount, setEditingAccount] = useState<ManagedAccount | null>(null);
  const [editName, setEditName] = useState("");
  const [editPhone, setEditPhone] = useState("");
  const [editRole, setEditRole] = useState<ManagedAccount["vaiTro"]>("nguoi_dung");

  // Modal Khóa / Mở khóa tài khoản
  const [lockTarget, setLockTarget] = useState<ManagedAccount | null>(null);
  const [lockNote, setLockNote] = useState("");

  const filteredAccounts = accounts.filter((acc) => {
    if (tabFilter === "PROPOSED_LOCK" && !acc.lockProposalFromKdv) return false;
    if (tabFilter === "LOCKED" && acc.trangThai !== "bi_khoa") return false;
    if (tabFilter === "STAFF" && acc.vaiTro === "nguoi_dung") return false;
    if (roleFilter !== "ALL" && acc.vaiTro !== roleFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return (
        acc.email.toLowerCase().includes(q) ||
        acc.hoTen.toLowerCase().includes(q) ||
        acc.soDienThoai.includes(q)
      );
    }
    return true;
  });

  const handleCreateAccount = () => {
    if (!newEmail.trim() || !newName.trim()) {
      toast.error("Vui lòng nhập đầy đủ họ tên và địa chỉ email.", "Thiếu thông tin");
      return;
    }

    const created: ManagedAccount = {
      id: `acc-${Date.now()}`,
      email: newEmail.trim().toLowerCase(),
      hoTen: newName.trim(),
      soDienThoai: newPhone.trim() || "0900000000",
      vaiTro: newRole,
      trangThai: "hoat_dong",
      emailDaXacMinh: true,
      mocThuHoi: 0,
    };

    setAccounts((prev) => [created, ...prev]);
    toast.success(
      `Đã tạo tài khoản ${created.email} với vai trò '${roleDisplayLabel[created.vaiTro]}' thành công.`,
      "Tạo & Phân quyền thành công"
    );
    setCreateModalOpen(false);
    setNewEmail("");
    setNewName("");
    setNewPhone("");
  };

  const handleSaveEdit = () => {
    if (!editingAccount) return;
    setAccounts((prev) =>
      prev.map((acc) =>
        acc.id === editingAccount.id
          ? {
              ...acc,
              hoTen: editName.trim() || acc.hoTen,
              soDienThoai: editPhone.trim() || acc.soDienThoai,
              vaiTro: editRole,
            }
          : acc
      )
    );
    toast.success(
      `Đã cập nhật hồ sơ và vai trò của ${editingAccount.email} thành '${roleDisplayLabel[editRole]}'.`,
      "Cập nhật tài khoản"
    );
    setEditingAccount(null);
  };

  const handleConfirmToggleLock = () => {
    if (!lockTarget) return;
    if (!lockNote.trim()) {
      toast.error("Vui lòng ghi rõ lý do khóa hoặc mở khóa để lưu vào nhật ký hệ thống.", "Thiếu lý do");
      return;
    }

    const willLock = lockTarget.trangThai === "hoat_dong";
    setAccounts((prev) =>
      prev.map((acc) =>
        acc.id === lockTarget.id
          ? {
              ...acc,
              trangThai: willLock ? "bi_khoa" : "hoat_dong",
              mocThuHoi: willLock ? acc.mocThuHoi + 1 : acc.mocThuHoi,
              lockProposalFromKdv: willLock ? undefined : acc.lockProposalFromKdv,
              externalAppealNote: !willLock ? undefined : acc.externalAppealNote,
            }
          : acc
      )
    );

    if (willLock) {
      toast.warning(
        `Đã khóa tài khoản ${lockTarget.email} và tự động đăng xuất khỏi toàn bộ thiết bị đang mở.`,
        "Đã khóa tài khoản"
      );
    } else {
      toast.success(
        `Đã mở khóa tài khoản ${lockTarget.email}. Người dùng có thể đăng nhập trở lại.`,
        "Đã mở khóa tài khoản"
      );
    }

    setLockTarget(null);
    setLockNote("");
  };

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Quản lý Tài khoản & Phân quyền</h1>
          <p>
            Tra cứu danh sách tài khoản, tạo tài khoản nhân sự nội bộ, cập nhật vai trò và xử lý khóa hoặc mở khóa tài khoản.
          </p>
        </div>
        <div className="staff-header-actions">
          <button
            type="button"
            className="staff-btn staff-btn-dark"
            onClick={() => setCreateModalOpen(true)}
          >
            + Tạo & Phân quyền tài khoản
          </button>
        </div>
      </div>

      {/* Sub-tabs theo tình huống nghiệp vụ */}
      <div className="staff-subtabs">
        <button
          type="button"
          className={`staff-subtab-btn ${tabFilter === "ALL" ? "active" : ""}`}
          onClick={() => setTabFilter("ALL")}
        >
          Tất cả tài khoản ({accounts.length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${tabFilter === "PROPOSED_LOCK" ? "active" : ""}`}
          onClick={() => setTabFilter("PROPOSED_LOCK")}
        >
          Đề xuất khóa từ Kiểm duyệt viên ({accounts.filter((a) => a.lockProposalFromKdv).length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${tabFilter === "LOCKED" ? "active" : ""}`}
          onClick={() => setTabFilter("LOCKED")}
        >
          Tài khoản đang bị khóa ({accounts.filter((a) => a.trangThai === "bi_khoa").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${tabFilter === "STAFF" ? "active" : ""}`}
          onClick={() => setTabFilter("STAFF")}
        >
          Nhân sự nội bộ ({accounts.filter((a) => a.vaiTro !== "nguoi_dung").length})
        </button>
      </div>

      {/* Bộ lọc tìm kiếm */}
      <div className="staff-filter-bar">
        <div className="staff-filter-field" style={{ flex: 2 }}>
          <label htmlFor="acc-search">Tìm theo Email, Họ tên hoặc Số điện thoại</label>
          <input
            id="acc-search"
            type="text"
            className="staff-input"
            placeholder="Nhập email, tên hoặc số điện thoại..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className="staff-filter-field">
          <label htmlFor="acc-role-filter">Lọc theo vai trò</label>
          <select
            id="acc-role-filter"
            className="staff-select"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
          >
            <option value="ALL">Tất cả vai trò</option>
            <option value="quan_tri_vien">Quản trị viên</option>
            <option value="kiem_duyet_vien">Kiểm duyệt viên</option>
            <option value="nguoi_dung">Người dùng</option>
          </select>
        </div>
      </div>

      {/* Bảng danh sách tài khoản */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">Danh sách tài khoản hệ thống</h3>
            <p className="staff-card-subtitle">
              Khi thực hiện khóa tài khoản, toàn bộ các phiên đăng nhập đang mở của tài khoản đó sẽ bị thu hồi ngay lập tức
            </p>
          </div>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Họ tên & Email</th>
                <th>Số điện thoại</th>
                <th>Vai trò</th>
                <th>Trạng thái</th>
                <th>Số lần thu hồi phiên</th>
                <th>Ghi chú đề xuất / Kháng cáo</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredAccounts.map((acc) => (
                <tr key={acc.id}>
                  <td>
                    <div style={{ fontWeight: 700, color: "#0f172a" }}>{acc.hoTen}</div>
                    <div style={{ fontSize: "0.78rem", color: "#475569" }}>{acc.email}</div>
                  </td>
                  <td>{acc.soDienThoai}</td>
                  <td>
                    {acc.vaiTro === "quan_tri_vien" && (
                      <span className="staff-badge blue">Quản trị viên</span>
                    )}
                    {acc.vaiTro === "kiem_duyet_vien" && (
                      <span className="staff-badge green">Kiểm duyệt viên</span>
                    )}
                    {acc.vaiTro === "nguoi_dung" && (
                      <span className="staff-badge slate">Người dùng</span>
                    )}
                  </td>
                  <td>
                    {acc.trangThai === "hoat_dong" ? (
                      <span className="staff-badge green">Đang hoạt động</span>
                    ) : (
                      <span className="staff-badge red">Đã khóa</span>
                    )}
                  </td>
                  <td>
                    <span>{acc.mocThuHoi} lần</span>
                  </td>
                  <td style={{ maxWidth: "280px" }}>
                    {acc.lockProposalFromKdv && (
                      <div style={{ fontSize: "0.78rem", color: "#7c3aed", fontWeight: 600 }}>
                        {acc.lockProposalFromKdv}
                      </div>
                    )}
                    {acc.externalAppealNote && (
                      <div style={{ fontSize: "0.78rem", color: "#0369a1", fontWeight: 600 }}>
                        {acc.externalAppealNote}
                      </div>
                    )}
                    {!acc.lockProposalFromKdv && !acc.externalAppealNote && (
                      <span style={{ color: "#94a3b8", fontSize: "0.78rem" }}>—</span>
                    )}
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                      <button
                        type="button"
                        className="staff-btn staff-btn-outline staff-btn-sm"
                        onClick={() => {
                          setEditingAccount(acc);
                          setEditName(acc.hoTen);
                          setEditPhone(acc.soDienThoai);
                          setEditRole(acc.vaiTro);
                        }}
                      >
                        Sửa / Phân quyền
                      </button>
                      <button
                        type="button"
                        className={`staff-btn staff-btn-sm ${
                          acc.trangThai === "hoat_dong" ? "staff-btn-danger" : "staff-btn-success"
                        }`}
                        onClick={() => {
                          setLockTarget(acc);
                          setLockNote(
                            acc.lockProposalFromKdv ||
                              acc.externalAppealNote ||
                              ""
                          );
                        }}
                      >
                        {acc.trangThai === "hoat_dong" ? "Khóa tài khoản" : "Mở khóa"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal Tạo và phân quyền tài khoản */}
      {createModalOpen && (
        <div className="staff-modal-backdrop" role="dialog" aria-modal="true">
          <div className="staff-modal">
            <div className="staff-modal-header">
              <h3>Tạo & Phân quyền tài khoản mới</h3>
              <button
                type="button"
                className="staff-btn staff-btn-outline staff-btn-sm"
                onClick={() => setCreateModalOpen(false)}
              >
                Đóng
              </button>
            </div>
            <div className="staff-modal-body">
              <div className="staff-filter-field">
                <label htmlFor="new-acc-name">Họ và tên nhân sự / người dùng *</label>
                <input
                  id="new-acc-name"
                  type="text"
                  className="staff-input"
                  placeholder="Ví dụ: Nguyễn Văn Kiểm Duyệt"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                />
              </div>
              <div className="staff-filter-field">
                <label htmlFor="new-acc-email">Địa chỉ Email *</label>
                <input
                  id="new-acc-email"
                  type="email"
                  className="staff-input"
                  placeholder="moderator2@homefinder.vn"
                  value={newEmail}
                  onChange={(e) => setNewEmail(e.target.value)}
                />
              </div>
              <div className="staff-filter-field">
                <label htmlFor="new-acc-phone">Số điện thoại</label>
                <input
                  id="new-acc-phone"
                  type="text"
                  className="staff-input"
                  placeholder="0901234567"
                  value={newPhone}
                  onChange={(e) => setNewPhone(e.target.value)}
                />
              </div>
              <div className="staff-filter-field">
                <label htmlFor="new-acc-role">Gán vai trò *</label>
                <select
                  id="new-acc-role"
                  className="staff-select"
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value as ManagedAccount["vaiTro"])}
                >
                  <option value="kiem_duyet_vien">Kiểm duyệt viên</option>
                  <option value="quan_tri_vien">Quản trị viên</option>
                  <option value="nguoi_dung">Người dùng</option>
                </select>
              </div>
            </div>
            <div className="staff-modal-footer">
              <button
                type="button"
                className="staff-btn staff-btn-outline"
                onClick={() => setCreateModalOpen(false)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="staff-btn staff-btn-dark"
                onClick={handleCreateAccount}
              >
                Tạo tài khoản & Cấp quyền
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Cập nhật thông tin & Đổi vai trò */}
      {editingAccount && (
        <div className="staff-modal-backdrop" role="dialog" aria-modal="true">
          <div className="staff-modal">
            <div className="staff-modal-header">
              <h3>Cập nhật & Phân quyền tài khoản</h3>
              <button
                type="button"
                className="staff-btn staff-btn-outline staff-btn-sm"
                onClick={() => setEditingAccount(null)}
              >
                Đóng
              </button>
            </div>
            <div className="staff-modal-body">
              <div style={{ fontSize: "0.85rem", color: "#475569" }}>
                Đang chỉnh sửa tài khoản: <strong>{editingAccount.email}</strong>
              </div>
              <div className="staff-filter-field">
                <label htmlFor="edit-acc-name">Họ và tên</label>
                <input
                  id="edit-acc-name"
                  type="text"
                  className="staff-input"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                />
              </div>
              <div className="staff-filter-field">
                <label htmlFor="edit-acc-phone">Số điện thoại</label>
                <input
                  id="edit-acc-phone"
                  type="text"
                  className="staff-input"
                  value={editPhone}
                  onChange={(e) => setEditPhone(e.target.value)}
                />
              </div>
              <div className="staff-filter-field">
                <label htmlFor="edit-acc-role">Vai trò hệ thống</label>
                <select
                  id="edit-acc-role"
                  className="staff-select"
                  value={editRole}
                  onChange={(e) => setEditRole(e.target.value as ManagedAccount["vaiTro"])}
                >
                  <option value="nguoi_dung">Người dùng</option>
                  <option value="kiem_duyet_vien">Kiểm duyệt viên</option>
                  <option value="quan_tri_vien">Quản trị viên</option>
                </select>
              </div>
            </div>
            <div className="staff-modal-footer">
              <button
                type="button"
                className="staff-btn staff-btn-outline"
                onClick={() => setEditingAccount(null)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="staff-btn staff-btn-dark"
                onClick={handleSaveEdit}
              >
                Lưu cập nhật
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Khóa / Mở khóa tài khoản */}
      {lockTarget && (
        <div className="staff-modal-backdrop" role="dialog" aria-modal="true">
          <div className="staff-modal">
            <div className="staff-modal-header">
              <h3>
                {lockTarget.trangThai === "hoat_dong"
                  ? "Khóa tài khoản & Thu hồi phiên đăng nhập"
                  : "Mở khóa tài khoản"}
              </h3>
              <button
                type="button"
                className="staff-btn staff-btn-outline staff-btn-sm"
                onClick={() => setLockTarget(null)}
              >
                Đóng
              </button>
            </div>
            <div className="staff-modal-body">
              <div
                style={{
                  backgroundColor: "#f8fafc",
                  padding: "0.85rem",
                  borderRadius: "8px",
                  border: "1px solid #e2e8f0",
                  fontSize: "0.85rem",
                }}
              >
                <div>
                  Tài khoản: <strong>{lockTarget.hoTen}</strong> ({lockTarget.email})
                </div>
                {lockTarget.trangThai === "hoat_dong" && (
                  <div style={{ color: "#dc2626", marginTop: "4px", fontWeight: 600 }}>
                    Khi khóa, tài khoản sẽ bị ngừng quyền truy cập và tự động đăng xuất khỏi tất cả các thiết bị đang mở.
                  </div>
                )}
              </div>

              <div>
                <label
                  htmlFor="lock-audit-note"
                  style={{ display: "block", fontSize: "0.82rem", fontWeight: 600, marginBottom: "6px" }}
                >
                  Căn cứ quyết định (Đề xuất từ Kiểm duyệt viên hoặc Email kháng cáo) *
                </label>
                <textarea
                  id="lock-audit-note"
                  rows={4}
                  className="staff-textarea"
                  placeholder="Nhập lý do khóa hoặc căn cứ mở khóa để lưu vào nhật ký hệ thống..."
                  value={lockNote}
                  onChange={(e) => setLockNote(e.target.value)}
                />
              </div>
            </div>
            <div className="staff-modal-footer">
              <button
                type="button"
                className="staff-btn staff-btn-outline"
                onClick={() => setLockTarget(null)}
              >
                Hủy
              </button>
              <button
                type="button"
                className={`staff-btn ${
                  lockTarget.trangThai === "hoat_dong" ? "staff-btn-danger" : "staff-btn-success"
                }`}
                onClick={handleConfirmToggleLock}
              >
                {lockTarget.trangThai === "hoat_dong"
                  ? "Xác nhận Khóa tài khoản"
                  : "Xác nhận Mở khóa"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
