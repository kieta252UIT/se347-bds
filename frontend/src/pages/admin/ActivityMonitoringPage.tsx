import { useState } from "react";

interface AuditLogEntry {
  id: string;
  timestamp: string;
  actorEmail: string;
  actorRole: "quan_tri_vien" | "kiem_duyet_vien" | "system_job";
  actionLabel: string;
  targetEntity: string;
  summary: string;
  traceId: string;
}

const auditLogs: AuditLogEntry[] = [
  {
    id: "NK-99041",
    timestamp: "18/11/2026 09:42:10",
    actorEmail: "khanhhbdt123@gmail.com",
    actorRole: "quan_tri_vien",
    actionLabel: "Khóa tài khoản người dùng",
    targetEntity: "Tài khoản: hoangminh.land@gmail.com",
    summary: "Khóa tài khoản vi phạm và thu hồi toàn bộ các phiên đăng nhập đang hoạt động trên mọi thiết bị.",
    traceId: "req-8841-a91c",
  },
  {
    id: "NK-99038",
    timestamp: "18/11/2026 09:18:04",
    actorEmail: "caok104@gmail.com",
    actorRole: "kiem_duyet_vien",
    actionLabel: "Gỡ tin & Đề xuất khóa tài khoản",
    targetEntity: "Báo cáo BC-8810 → Tài khoản: canho.dongnam@gmail.com",
    summary: "Gỡ tin vi phạm TIN-2026-0840 khỏi trang công khai và chuyển đề xuất khóa tài khoản sang Quản trị viên.",
    traceId: "req-7720-b40e",
  },
  {
    id: "NK-99032",
    timestamp: "18/11/2026 08:55:21",
    actorEmail: "caok104@gmail.com",
    actorRole: "kiem_duyet_vien",
    actionLabel: "Phê duyệt tin đăng",
    targetEntity: "Tin đăng: TIN-2026-1019 (VER-8790)",
    summary: "Phê duyệt hiển thị công khai phiên bản tin VER-8790 sau khi kiểm tra hợp lệ.",
    traceId: "req-6612-c19f",
  },
  {
    id: "NK-99015",
    timestamp: "18/11/2026 02:00:03",
    actorEmail: "Hệ thống tự động (02:00)",
    actorRole: "system_job",
    actionLabel: "Đối soát giao dịch định kỳ",
    targetEntity: "Phiên đối soát ngày 18/11/2026",
    summary: "Hoàn tất đối soát tự động 142 giao dịch cổng thanh toán: 140 giao dịch khớp, 2 giao dịch lệch chờ điều chỉnh.",
    traceId: "job-0200-reconcile",
  },
  {
    id: "NK-98994",
    timestamp: "17/11/2026 22:14:50",
    actorEmail: "khanhhbdt123@gmail.com",
    actorRole: "quan_tri_vien",
    actionLabel: "Điều chỉnh số dư ví",
    targetEntity: "Tài khoản: baopq.hn@gmail.com",
    summary: "Cộng bù +1.500.000đ vào ví người dùng theo biên bản đối soát giao dịch GD-20261117-0512.",
    traceId: "req-5109-d82a",
  },
];

export function ActivityMonitoringPage() {
  const [roleFilter, setRoleFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState("");

  const filteredLogs = auditLogs.filter((log) => {
    if (roleFilter !== "ALL" && log.actorRole !== roleFilter) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return (
        log.actorEmail.toLowerCase().includes(q) ||
        log.actionLabel.toLowerCase().includes(q) ||
        log.targetEntity.toLowerCase().includes(q) ||
        log.summary.toLowerCase().includes(q) ||
        log.traceId.toLowerCase().includes(q)
      );
    }
    return true;
  });

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Giám sát Hoạt động & Nhật ký Hệ thống</h1>
          <p>
            Tra cứu lịch sử thao tác của nhân sự vận hành, theo dõi trạng thái phiên đăng nhập và các tiến trình tự động trên hệ thống.
          </p>
        </div>
      </div>

      {/* 4 thẻ giám sát */}
      <div className="staff-kpi-grid">
        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Tổng bản ghi nhật ký</span>
            <span className="staff-badge blue">Lưu vết</span>
          </div>
          <div className="staff-kpi-value">1.482</div>
          <div className="staff-kpi-sub">
            <span>Ghi nhận 100% thao tác Kiểm duyệt & Quản trị</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Phiên đăng nhập đang hoạt động</span>
            <span className="staff-badge green">Trực tuyến</span>
          </div>
          <div className="staff-kpi-value">68</div>
          <div className="staff-kpi-sub">
            <span>Bảo mật phiên người dùng và nhân sự</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Trạng thái đồng bộ tài khoản</span>
            <span className="staff-badge green">Bình thường</span>
          </div>
          <div className="staff-kpi-value">0 lỗi</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-up">Tất cả tài khoản đồng bộ ổn định</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Kiểm soát thao tác trùng lặp</span>
            <span className="staff-badge slate">Bảo vệ giao dịch</span>
          </div>
          <div className="staff-kpi-value">Hoạt động</div>
          <div className="staff-kpi-sub">
            <span>Đã ngăn chặn 9 yêu cầu gửi trùng hôm nay</span>
          </div>
        </div>
      </div>

      {/* Bộ lọc nhật ký */}
      <div className="staff-filter-bar">
        <div className="staff-filter-field" style={{ flex: 2 }}>
          <label htmlFor="audit-search">Tìm theo email người thực hiện, hành động, đối tượng hoặc mã truy vết</label>
          <input
            id="audit-search"
            type="text"
            className="staff-input"
            placeholder="Nhập email, tên hành động, mã tin hoặc mã truy vết..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className="staff-filter-field">
          <label htmlFor="audit-role">Nhóm người thực hiện</label>
          <select
            id="audit-role"
            className="staff-select"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
          >
            <option value="ALL">Tất cả nhóm</option>
            <option value="quan_tri_vien">Quản trị viên</option>
            <option value="kiem_duyet_vien">Kiểm duyệt viên</option>
            <option value="system_job">Tiến trình tự động (02:00)</option>
          </select>
        </div>
      </div>

      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">Sổ nhật ký hoạt động hệ thống</h3>
            <p className="staff-card-subtitle">
              Mọi quyết định thẩm định tin, gỡ vi phạm, khóa tài khoản và điều chỉnh ví đều được lưu lại đầy đủ
            </p>
          </div>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Mã nhật ký / Thời điểm</th>
                <th>Người thực hiện</th>
                <th>Loại hành động</th>
                <th>Đối tượng tác động</th>
                <th>Nội dung chi tiết</th>
                <th>Mã truy vết</th>
              </tr>
            </thead>
            <tbody>
              {filteredLogs.map((log) => (
                <tr key={log.id}>
                  <td>
                    <div style={{ fontWeight: 700 }}>{log.id}</div>
                    <div style={{ fontSize: "0.75rem", color: "#64748b" }}>{log.timestamp}</div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{log.actorEmail}</div>
                    {log.actorRole === "quan_tri_vien" && (
                      <span className="staff-badge blue">Quản trị viên</span>
                    )}
                    {log.actorRole === "kiem_duyet_vien" && (
                      <span className="staff-badge green">Kiểm duyệt viên</span>
                    )}
                    {log.actorRole === "system_job" && (
                      <span className="staff-badge purple">Tự động 02:00</span>
                    )}
                  </td>
                  <td style={{ fontWeight: 600 }}>
                    {log.actionLabel}
                  </td>
                  <td style={{ fontWeight: 600, color: "#0f3d32" }}>{log.targetEntity}</td>
                  <td style={{ maxWidth: "360px", color: "#334155" }}>{log.summary}</td>
                  <td>
                    <span style={{ fontSize: "0.78rem", color: "#64748b" }}>{log.traceId}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
