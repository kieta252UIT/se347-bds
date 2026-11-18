import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";

const pendingListingsPreview = [
  {
    id: "TIN-2026-1042",
    title: "Căn hộ 2PN Vinhomes Grand Park phân khu Origami tầng trung",
    owner: "Nguyễn Minh Tuấn (tuan.nm@gmail.com)",
    type: "Tin đăng mới",
    area: "TP. Thủ Đức, TP.HCM",
    price: "2,85 tỷ",
    submittedAt: "14 phút trước",
  },
  {
    id: "TIN-2026-1039",
    title: "Nhà phố mặt tiền đường số 7 Khu dân cư Trung Sơn 4x20m",
    owner: "Lê Hoàng Nam (namle.bds@gmail.com)",
    type: "Tin sửa & nộp lại",
    area: "Bình Chánh, TP.HCM",
    price: "11,2 tỷ",
    submittedAt: "32 phút trước",
  },
  {
    id: "TIN-2026-1035",
    title: "Đất nền sổ hồng riêng KĐT Hòa Xuân 100m² đường 7m5",
    owner: "Trần Thị Mai (maitran88@gmail.com)",
    type: "Tin đăng mới",
    area: "Cẩm Lệ, Đà Nẵng",
    price: "3,45 tỷ",
    submittedAt: "1 giờ trước",
  },
  {
    id: "TIN-2026-1028",
    title: "Chung cư cao cấp The Zei Mỹ Đình 3PN 108m² full nội thất",
    owner: "Phạm Quốc Bảo (baopq.hn@gmail.com)",
    type: "Tin sửa & nộp lại",
    area: "Nam Từ Liêm, Hà Nội",
    price: "6,9 tỷ",
    submittedAt: "2 giờ trước",
  },
];

const urgentReportsPreview = [
  {
    reportId: "BC-8821",
    listingId: "TIN-2026-0912",
    reason: "Giá rao ảo thấp hơn thực tế 70%, dẫn dụ đặt cọc",
    reporter: "hoang.vu92@gmail.com",
    accusedOwner: "bds.giare.sg@gmail.com",
    violationCount: 3,
    status: "Chờ xử lý",
  },
  {
    reportId: "BC-8819",
    listingId: "TIN-2026-0894",
    reason: "Sử dụng ảnh sổ hồng cắt ghép từ dự án khác",
    reporter: "thanh.nguyen@gmail.com",
    accusedOwner: "nhadat.phatloc@gmail.com",
    violationCount: 2,
    status: "Chờ xử lý",
  },
  {
    reportId: "BC-8810",
    listingId: "TIN-2026-0840",
    reason: "Tin đăng trùng lặp 4 lần cùng một căn hộ",
    reporter: "minhchau.le@gmail.com",
    accusedOwner: "canho.dongnam@gmail.com",
    violationCount: 1,
    status: "Đã chuyển đề xuất khóa",
  },
];

export function KdvDashboardPage() {
  const { user } = useAuth();

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Bàn làm việc Kiểm duyệt viên</h1>
          <p>
            Xin chào <strong>{user?.hoTen || user?.email || "Kiểm duyệt viên"}</strong>. Theo dõi hàng chờ thẩm định tin đăng và xử lý báo cáo vi phạm từ người dùng.
          </p>
        </div>
        <div className="staff-header-actions">
          <Link to="/kdv/tham-dinh" className="staff-btn staff-btn-primary" style={{ textDecoration: "none" }}>
            Vào hàng chờ thẩm định (18) →
          </Link>
          <Link to="/kdv/bao-cao" className="staff-btn staff-btn-outline" style={{ textDecoration: "none" }}>
            Xử lý báo cáo vi phạm (7)
          </Link>
        </div>
      </div>

      {/* 4 KPI Cards */}
      <div className="staff-kpi-grid">
        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Tin đang chờ thẩm định</span>
            <span className="staff-badge amber">Hàng chờ</span>
          </div>
          <div className="staff-kpi-value">18</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-warn">12 tin đăng mới</span>
            <span>• 6 tin sửa nộp lại</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Tin đã thẩm định hôm nay</span>
            <span className="staff-badge green">Hiệu suất</span>
          </div>
          <div className="staff-kpi-value">42</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-up">35 đã phê duyệt</span>
            <span>• 7 đã từ chối</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Báo cáo vi phạm chờ xử lý</span>
            <span className="staff-badge red">Cần kiểm tra</span>
          </div>
          <div className="staff-kpi-value">7</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-danger">3 báo cáo mức nghiêm trọng</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Đề xuất khóa gửi Quản trị viên</span>
            <span className="staff-badge purple">Chờ duyệt khóa</span>
          </div>
          <div className="staff-kpi-value">4</div>
          <div className="staff-kpi-sub">
            <span>2 đang chờ Quản trị viên xét duyệt</span>
          </div>
        </div>
      </div>

      {/* Bảng 1: Hàng chờ thẩm định mới nhất */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">Hàng chờ thẩm định tin đăng mới nhất</h3>
            <p className="staff-card-subtitle">
              Danh sách các tin đăng mới và tin chỉnh sửa đang chờ Kiểm duyệt viên xét duyệt
            </p>
          </div>
          <Link to="/kdv/tham-dinh" className="staff-btn staff-btn-outline staff-btn-sm" style={{ textDecoration: "none" }}>
            Xem toàn bộ hàng chờ →
          </Link>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Mã tin</th>
                <th>Tiêu đề & Khu vực</th>
                <th>Người đăng</th>
                <th>Phân loại nộp</th>
                <th>Mức giá</th>
                <th>Thời gian gửi</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {pendingListingsPreview.map((item) => (
                <tr key={item.id}>
                  <td style={{ fontWeight: 700, color: "#0f3d32" }}>{item.id}</td>
                  <td>
                    <div style={{ fontWeight: 600, color: "#0f172a" }}>{item.title}</div>
                    <div style={{ fontSize: "0.78rem", color: "#64748b" }}>{item.area}</div>
                  </td>
                  <td>{item.owner}</td>
                  <td>
                    <span className={`staff-badge ${item.type.includes("nộp lại") ? "blue" : "amber"}`}>
                      {item.type}
                    </span>
                  </td>
                  <td style={{ fontWeight: 700 }}>{item.price}</td>
                  <td style={{ color: "#64748b" }}>{item.submittedAt}</td>
                  <td>
                    <Link
                      to="/kdv/tham-dinh"
                      className="staff-btn staff-btn-primary staff-btn-sm"
                      style={{ textDecoration: "none" }}
                    >
                      Thẩm định
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Bảng 2: Báo cáo vi phạm cần ưu tiên */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">Báo cáo vi phạm cần xử lý & Đề xuất khóa</h3>
            <p className="staff-card-subtitle">
              Kiểm tra bằng chứng phản ánh, gỡ tin vi phạm và gửi đề xuất khóa tài khoản lên Quản trị viên nếu tái phạm
            </p>
          </div>
          <Link to="/kdv/bao-cao" className="staff-btn staff-btn-outline staff-btn-sm" style={{ textDecoration: "none" }}>
            Mở trình xử lý báo cáo →
          </Link>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Mã báo cáo</th>
                <th>Mã tin bị báo cáo</th>
                <th>Nội dung vi phạm</th>
                <th>Chủ tin bị tố cáo</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {urgentReportsPreview.map((r) => (
                <tr key={r.reportId}>
                  <td style={{ fontWeight: 700 }}>{r.reportId}</td>
                  <td style={{ fontWeight: 600, color: "#0f3d32" }}>{r.listingId}</td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{r.reason}</div>
                    <div style={{ fontSize: "0.76rem", color: "#64748b" }}>Người báo cáo: {r.reporter}</div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{r.accusedOwner}</div>
                    <div style={{ fontSize: "0.75rem", color: "#dc2626", fontWeight: 600 }}>
                      Lịch sử vi phạm: {r.violationCount} lần
                    </div>
                  </td>
                  <td>
                    <span className={`staff-badge ${r.status.includes("đề xuất") ? "purple" : "red"}`}>
                      {r.status}
                    </span>
                  </td>
                  <td>
                    <Link
                      to="/kdv/bao-cao"
                      className="staff-btn staff-btn-outline staff-btn-sm"
                      style={{ textDecoration: "none" }}
                    >
                      Kiểm tra & Xử lý
                    </Link>
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
