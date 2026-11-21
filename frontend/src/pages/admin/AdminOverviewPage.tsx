import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";

const pendingAdminTasks = [
  {
    id: "CV-01",
    category: "Đề xuất khóa từ Kiểm duyệt viên",
    priorityLabel: "Cần phê duyệt",
    subject: "Tài khoản: canho.dongnam@gmail.com (Trương Hoàng Long)",
    detail: "Tái phạm đăng tin ảo và trùng lặp 4 lần trong tháng. Kiểm duyệt viên đề xuất khóa tài khoản.",
    link: "/admin/tai-khoan",
    badgeClass: "purple",
  },
  {
    id: "CV-02",
    category: "Lệch đối soát giao dịch tự động",
    priorityLabel: "Đối soát tài chính",
    subject: "Giao dịch nạp ví #GD-20261118-0092 (3.500.000đ)",
    detail: "Cổng thanh toán báo thành công nhưng phản hồi cộng tiền vào ví người dùng bị trễ.",
    link: "/admin/doi-soat",
    badgeClass: "amber",
  },
  {
    id: "CV-03",
    category: "Khiếu nại gói đẩy tin & gỡ bài",
    priorityLabel: "Khiếu nại mới",
    subject: "Khiếu nại #KN-4012 từ khách hàng phamgia.bds@gmail.com",
    detail: "Yêu cầu xem xét lại quyết định gỡ tin TIN-2026-0811 và bồi hoàn 4 ngày gói VIP.",
    link: "/admin/khieu-nai",
    badgeClass: "red",
  },
  {
    id: "CV-04",
    category: "Kháng cáo mở khóa qua Email",
    priorityLabel: "Hỗ trợ tài khoản",
    subject: "Tài khoản: hoangminh.land@gmail.com",
    detail: "Người dùng gửi giấy ủy quyền chính chủ qua email kháng cáo xin mở khóa tài khoản.",
    link: "/admin/tai-khoan",
    badgeClass: "blue",
  },
];

export function AdminOverviewPage() {
  const { user } = useAuth();

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Tổng quan vận hành Quản trị viên</h1>
          <p>
            Xin chào <strong>{user?.hoTen || user?.email || "Quản trị viên"}</strong>. Trung tâm điều phối các tác vụ cần phê duyệt trong ngày, đối soát tài chính và giám sát toàn hệ thống.
          </p>
        </div>
        <div className="staff-header-actions">
          <Link to="/admin/thong-ke" className="staff-btn staff-btn-dark" style={{ textDecoration: "none" }}>
            Mở Trung tâm Thống kê đa tiêu chí →
          </Link>
          <Link to="/admin/tai-khoan" className="staff-btn staff-btn-outline" style={{ textDecoration: "none" }}>
            Quản lý tài khoản & Phân quyền
          </Link>
        </div>
      </div>

      {/* 4 KPI vận hành nhanh trong ngày */}
      <div className="staff-kpi-grid">
        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Đề xuất khóa & Kháng cáo chờ duyệt</span>
            <span className="staff-badge purple">Tài khoản</span>
          </div>
          <div className="staff-kpi-value">5</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-warn">3 đề xuất từ Kiểm duyệt viên</span>
            <span>• 2 email kháng cáo</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Giao dịch lệch đối soát 02:00</span>
            <span className="staff-badge amber">Tài chính ví</span>
          </div>
          <div className="staff-kpi-value">2</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-warn">Cần điều chỉnh ví thủ công</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Khiếu nại người dùng chờ xử lý</span>
            <span className="staff-badge red">Khiếu nại</span>
          </div>
          <div className="staff-kpi-value">4</div>
          <div className="staff-kpi-sub">
            <span>Liên quan bồi hoàn gói đẩy tin</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Trạng thái vận hành hệ thống</span>
            <span className="staff-badge green">Ổn định</span>
          </div>
          <div className="staff-kpi-value">100%</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-up">Mọi dịch vụ hoạt động bình thường</span>
          </div>
        </div>
      </div>

      {/* Bảng 1: Hàng chờ tác vụ cần Quản trị viên phê duyệt */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">Hàng chờ tác vụ ưu tiên cần Quản trị viên quyết định</h3>
            <p className="staff-card-subtitle">
              Tổng hợp từ đề xuất của Kiểm duyệt viên, phiên đối soát tự động 02:00 và khiếu nại khách hàng
            </p>
          </div>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Mã việc</th>
                <th>Phân loại</th>
                <th>Đối tượng / Hồ sơ</th>
                <th>Chi tiết cần xử lý</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {pendingAdminTasks.map((t) => (
                <tr key={t.id}>
                  <td style={{ fontWeight: 700 }}>{t.id}</td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{t.category}</div>
                    <span className={`staff-badge ${t.badgeClass}`} style={{ marginTop: "4px" }}>
                      {t.priorityLabel}
                    </span>
                  </td>
                  <td style={{ fontWeight: 600, color: "#0f172a" }}>{t.subject}</td>
                  <td style={{ color: "#475569" }}>{t.detail}</td>
                  <td>
                    <Link
                      to={t.link}
                      className="staff-btn staff-btn-dark staff-btn-sm"
                      style={{ textDecoration: "none" }}
                    >
                      Đi tới xử lý →
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Lối tắt phân hệ Quản trị viên */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
          gap: "1rem",
        }}
      >
        <div className="staff-card" style={{ marginBottom: 0, padding: "1.25rem" }}>
          <span className="staff-badge blue">Báo cáo & Số liệu</span>
          <h3 style={{ margin: "0.6rem 0 0.35rem", fontSize: "1rem" }}>Thống kê & Phân tích đa tiêu chí</h3>
          <p style={{ fontSize: "0.84rem", color: "#64748b", margin: "0 0 1rem", lineHeight: 1.5 }}>
            Lọc doanh thu nạp ví, mua gói đẩy tin, tiền bồi hoàn và thống kê trạng thái tin đăng theo khoảng thời gian, khu vực, loại hình BĐS.
          </p>
          <Link to="/admin/thong-ke" className="staff-btn staff-btn-outline staff-btn-sm" style={{ textDecoration: "none" }}>
            Mở bảng thống kê →
          </Link>
        </div>

        <div className="staff-card" style={{ marginBottom: 0, padding: "1.25rem" }}>
          <span className="staff-badge green">Nhân sự & Người dùng</span>
          <h3 style={{ margin: "0.6rem 0 0.35rem", fontSize: "1rem" }}>Quản lý Tài khoản & Phân quyền</h3>
          <p style={{ fontSize: "0.84rem", color: "#64748b", margin: "0 0 1rem", lineHeight: 1.5 }}>
            Tạo tài khoản nhân sự mới, cấp quyền Kiểm duyệt viên / Quản trị viên, khóa tài khoản vi phạm và mở khóa kháng cáo.
          </p>
          <Link to="/admin/tai-khoan" className="staff-btn staff-btn-outline staff-btn-sm" style={{ textDecoration: "none" }}>
            Quản lý tài khoản →
          </Link>
        </div>

        <div className="staff-card" style={{ marginBottom: 0, padding: "1.25rem" }}>
          <span className="staff-badge slate">Tài chính & Giám sát</span>
          <h3 style={{ margin: "0.6rem 0 0.35rem", fontSize: "1rem" }}>Đối soát, Khiếu nại & Nhật ký</h3>
          <p style={{ fontSize: "0.84rem", color: "#64748b", margin: "0 0 1rem", lineHeight: 1.5 }}>
            Kiểm tra phiên đối soát 02:00 sáng, điều chỉnh số dư ví, giải quyết khiếu nại và tra cứu lịch sử hoạt động hệ thống.
          </p>
          <Link to="/admin/giam-sat" className="staff-btn staff-btn-outline staff-btn-sm" style={{ textDecoration: "none" }}>
            Xem nhật ký giám sát →
          </Link>
        </div>
      </div>
    </div>
  );
}
