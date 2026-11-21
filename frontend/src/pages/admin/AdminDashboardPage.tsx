import { useState } from "react";
import { toast } from "@/stores/useToastStore";

type AnalyticsSubTab = "revenue" | "listings";
type TimePreset = "7d" | "30d" | "q4_2026" | "y_2026" | "custom";

interface PeriodRevenuePoint {
  label: string;
  topupAmount: string;
  packageRevenue: string;
  refundAmount: string;
  topupPct: number;
  pkgPct: number;
  refundPct: number;
}

const revenueSeriesByPreset: Record<TimePreset, PeriodRevenuePoint[]> = {
  "7d": [
    { label: "12/11", topupAmount: "18,5 tr", packageRevenue: "14,2 tr", refundAmount: "1,2 tr", topupPct: 68, pkgPct: 54, refundPct: 14 },
    { label: "13/11", topupAmount: "22,0 tr", packageRevenue: "17,8 tr", refundAmount: "0,8 tr", topupPct: 80, pkgPct: 65, refundPct: 10 },
    { label: "14/11", topupAmount: "19,4 tr", packageRevenue: "15,6 tr", refundAmount: "1,5 tr", topupPct: 72, pkgPct: 58, refundPct: 16 },
    { label: "15/11", topupAmount: "26,8 tr", packageRevenue: "21,0 tr", refundAmount: "0,5 tr", topupPct: 92, pkgPct: 78, refundPct: 8 },
    { label: "16/11", topupAmount: "24,1 tr", packageRevenue: "19,5 tr", refundAmount: "1,8 tr", topupPct: 86, pkgPct: 72, refundPct: 19 },
    { label: "17/11", topupAmount: "28,5 tr", packageRevenue: "23,4 tr", refundAmount: "0,9 tr", topupPct: 96, pkgPct: 85, refundPct: 11 },
    { label: "18/11", topupAmount: "25,2 tr", packageRevenue: "20,8 tr", refundAmount: "1,1 tr", topupPct: 88, pkgPct: 76, refundPct: 12 },
  ],
  "30d": [
    { label: "Tuần 1", topupAmount: "112 tr", packageRevenue: "88 tr", refundAmount: "5,4 tr", topupPct: 74, pkgPct: 60, refundPct: 14 },
    { label: "Tuần 2", topupAmount: "134 tr", packageRevenue: "106 tr", refundAmount: "4,8 tr", topupPct: 84, pkgPct: 70, refundPct: 12 },
    { label: "Tuần 3", topupAmount: "148 tr", packageRevenue: "119 tr", refundAmount: "6,2 tr", topupPct: 92, pkgPct: 78, refundPct: 16 },
    { label: "Tuần 4", topupAmount: "164 tr", packageRevenue: "132 tr", refundAmount: "7,8 tr", topupPct: 98, pkgPct: 86, refundPct: 18 },
  ],
  q4_2026: [
    { label: "Tháng 10", topupAmount: "480 tr", packageRevenue: "392 tr", refundAmount: "18,5 tr", topupPct: 82, pkgPct: 68, refundPct: 15 },
    { label: "Tháng 11", topupAmount: "558 tr", packageRevenue: "445 tr", refundAmount: "24,2 tr", topupPct: 95, pkgPct: 80, refundPct: 19 },
    { label: "Tháng 12 (KH)", topupAmount: "610 tr", packageRevenue: "490 tr", refundAmount: "16,0 tr", topupPct: 100, pkgPct: 86, refundPct: 13 },
  ],
  y_2026: [
    { label: "Quý 1", topupAmount: "1,12 tỷ", packageRevenue: "890 tr", refundAmount: "42 tr", topupPct: 68, pkgPct: 55, refundPct: 14 },
    { label: "Quý 2", topupAmount: "1,38 tỷ", packageRevenue: "1,10 tỷ", refundAmount: "51 tr", topupPct: 80, pkgPct: 66, refundPct: 16 },
    { label: "Quý 3", topupAmount: "1,54 tỷ", packageRevenue: "1,25 tỷ", refundAmount: "48 tr", topupPct: 90, pkgPct: 76, refundPct: 15 },
    { label: "Quý 4", topupAmount: "1,65 tỷ", packageRevenue: "1,33 tỷ", refundAmount: "58 tr", topupPct: 98, pkgPct: 84, refundPct: 18 },
  ],
  custom: [
    { label: "Kỳ 1", topupAmount: "64 tr", packageRevenue: "51 tr", refundAmount: "2,9 tr", topupPct: 78, pkgPct: 64, refundPct: 13 },
    { label: "Kỳ 2", topupAmount: "79 tr", packageRevenue: "63 tr", refundAmount: "3,4 tr", topupPct: 90, pkgPct: 74, refundPct: 15 },
    { label: "Kỳ 3", topupAmount: "85 tr", packageRevenue: "69 tr", refundAmount: "2,1 tr", topupPct: 96, pkgPct: 80, refundPct: 10 },
  ],
};

const packageBreakdown = [
  {
    packageCode: "GOI-VIP-07",
    packageName: "Gói Đẩy Tin VIP 7 ngày (Hiển thị ưu tiên đầu trang)",
    purchases: 186,
    stackedCount: 42,
    grossRevenue: "164.500.000 đ",
    refundedCases: 6,
    refundedAmount: "3.900.000 đ",
    netShare: "48,2%",
  },
  {
    packageCode: "GOI-STD-15",
    packageName: "Gói Đẩy Tin Tiêu Chuẩn 15 ngày",
    purchases: 245,
    stackedCount: 68,
    grossRevenue: "118.200.000 đ",
    refundedCases: 5,
    refundedAmount: "2.400.000 đ",
    netShare: "34,9%",
  },
  {
    packageCode: "GOI-PLUS-30",
    packageName: "Gói Cộng Dồn Dài Hạn 30 ngày",
    purchases: 94,
    stackedCount: 31,
    grossRevenue: "62.800.000 đ",
    refundedCases: 3,
    refundedAmount: "1.500.000 đ",
    netShare: "16,9%",
  },
];

const regionalListingStats = [
  {
    region: "TP. Hồ Chí Minh",
    totalSubmitted: 412,
    approved: 348,
    rejected: 45,
    removedViolation: 19,
    reportCount: 34,
    approvalRate: "84,5%",
  },
  {
    region: "Hà Nội",
    totalSubmitted: 318,
    approved: 274,
    rejected: 32,
    removedViolation: 12,
    reportCount: 22,
    approvalRate: "86,2%",
  },
  {
    region: "Đà Nẵng",
    totalSubmitted: 164,
    approved: 145,
    rejected: 14,
    removedViolation: 5,
    reportCount: 9,
    approvalRate: "88,4%",
  },
  {
    region: "Bình Dương & Đồng Nai",
    totalSubmitted: 126,
    approved: 102,
    rejected: 17,
    removedViolation: 7,
    reportCount: 14,
    approvalRate: "81,0%",
  },
];

export function AdminDashboardPage() {
  const [subTab, setSubTab] = useState<AnalyticsSubTab>("revenue");
  const [timePreset, setTimePreset] = useState<TimePreset>("30d");
  const [fromDate, setFromDate] = useState("2026-10-19");
  const [toDate, setToDate] = useState("2026-11-18");
  const [regionFilter, setRegionFilter] = useState("ALL");
  const [propertyTypeFilter, setPropertyTypeFilter] = useState("ALL");
  const [packageFilter, setPackageFilter] = useState("ALL");

  const activeSeries = revenueSeriesByPreset[timePreset];

  const handleExportReport = () => {
    toast.success(
      `Đã kết xuất báo cáo thống kê (${subTab === "revenue" ? "Doanh thu & Bồi hoàn" : "Tin đăng & Báo cáo vi phạm"}) theo bộ lọc hiện tại.`,
      "Xuất báo cáo thành công"
    );
  };

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Thống kê & Phân tích Đa tiêu chí</h1>
          <p>
            Phân tích chuyên sâu dòng tiền nạp ví, doanh thu gói đẩy tin, chi phí bồi hoàn và thống kê vòng đời tin đăng, báo cáo vi phạm.
          </p>
        </div>
        <div className="staff-header-actions">
          <button type="button" className="staff-btn staff-btn-outline" onClick={handleExportReport}>
            Xuất báo cáo CSV
          </button>
        </div>
      </div>

      {/* Thanh Bộ lọc Đa tiêu chí */}
      <div className="staff-filter-bar">
        <div className="staff-filter-field">
          <label htmlFor="stat-time-preset">Khoảng thời gian</label>
          <select
            id="stat-time-preset"
            className="staff-select"
            value={timePreset}
            onChange={(e) => setTimePreset(e.target.value as TimePreset)}
          >
            <option value="7d">7 ngày gần nhất</option>
            <option value="30d">30 ngày gần nhất (Tháng này)</option>
            <option value="q4_2026">Quý 4 / 2026</option>
            <option value="y_2026">Toàn năm 2026</option>
            <option value="custom">Tùy chọn khoảng ngày</option>
          </select>
        </div>

        {timePreset === "custom" && (
          <>
            <div className="staff-filter-field">
              <label htmlFor="stat-from-date">Từ ngày</label>
              <input
                id="stat-from-date"
                type="date"
                className="staff-input"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
              />
            </div>
            <div className="staff-filter-field">
              <label htmlFor="stat-to-date">Đến ngày</label>
              <input
                id="stat-to-date"
                type="date"
                className="staff-input"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
              />
            </div>
          </>
        )}

        <div className="staff-filter-field">
          <label htmlFor="stat-region">Khu vực địa lý</label>
          <select
            id="stat-region"
            className="staff-select"
            value={regionFilter}
            onChange={(e) => setRegionFilter(e.target.value)}
          >
            <option value="ALL">Toàn quốc (Tất cả tỉnh/thành)</option>
            <option value="HCM">TP. Hồ Chí Minh</option>
            <option value="HN">Hà Nội</option>
            <option value="DN">Đà Nẵng</option>
            <option value="BD">Bình Dương & Đồng Nai</option>
          </select>
        </div>

        <div className="staff-filter-field">
          <label htmlFor="stat-prop-type">Loại hình BĐS</label>
          <select
            id="stat-prop-type"
            className="staff-select"
            value={propertyTypeFilter}
            onChange={(e) => setPropertyTypeFilter(e.target.value)}
          >
            <option value="ALL">Tất cả loại hình</option>
            <option value="CAN_HO">Căn hộ chung cư</option>
            <option value="NHA_PHO">Nhà phố / Nhà riêng</option>
            <option value="DAT_NEN">Đất nền dự án</option>
            <option value="BIET_THU">Biệt thự / Nghỉ dưỡng</option>
          </select>
        </div>

        <div className="staff-filter-field">
          <label htmlFor="stat-pkg">Loại gói đẩy tin</label>
          <select
            id="stat-pkg"
            className="staff-select"
            value={packageFilter}
            onChange={(e) => setPackageFilter(e.target.value)}
          >
            <option value="ALL">Tất cả gói dịch vụ</option>
            <option value="VIP7">Gói VIP 7 ngày</option>
            <option value="STD15">Gói Tiêu chuẩn 15 ngày</option>
            <option value="PLUS30">Gói Cộng dồn 30 ngày</option>
          </select>
        </div>
      </div>

      {/* Chuyển đổi giữa 2 phân hệ thống kê */}
      <div className="staff-subtabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={subTab === "revenue"}
          className={`staff-subtab-btn ${subTab === "revenue" ? "active" : ""}`}
          onClick={() => setSubTab("revenue")}
        >
          <span>Thống kê Doanh thu & Bồi hoàn</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={subTab === "listings"}
          className={`staff-subtab-btn ${subTab === "listings" ? "active" : ""}`}
          onClick={() => setSubTab("listings")}
        >
          <span>Thống kê Tin đăng & Báo cáo vi phạm</span>
        </button>
      </div>

      {subTab === "revenue" ? (
        <>
          {/* KPI Doanh thu & Bồi hoàn */}
          <div className="staff-kpi-grid">
            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Tổng tiền nạp ví</span>
                <span className="staff-badge blue">Dòng tiền vào</span>
              </div>
              <div className="staff-kpi-value">558,0 tr</div>
              <div className="staff-kpi-sub">
                <span className="staff-kpi-trend-up">▲ +16,2%</span>
                <span>so với kỳ trước</span>
              </div>
            </div>

            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Doanh thu gói đẩy tin</span>
                <span className="staff-badge green">Bán gói dịch vụ</span>
              </div>
              <div className="staff-kpi-value">345,5 tr</div>
              <div className="staff-kpi-sub">
                <span className="staff-kpi-trend-up">525 lượt mua & cộng dồn</span>
              </div>
            </div>

            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Tổng chi phí bồi hoàn</span>
                <span className="staff-badge amber">Hoàn tiền ví</span>
              </div>
              <div className="staff-kpi-value">7,8 tr</div>
              <div className="staff-kpi-sub">
                <span>14 ca bồi hoàn (chiếm 2,25%)</span>
              </div>
            </div>

            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Doanh thu ròng sau bồi hoàn</span>
                <span className="staff-badge green">Thực thu</span>
              </div>
              <div className="staff-kpi-value">337,7 tr</div>
              <div className="staff-kpi-sub">
                <span className="staff-kpi-trend-up">Biên thực thu đạt 97,75%</span>
              </div>
            </div>
          </div>

          {/* Biểu đồ trực quan so sánh Nạp ví - Mua gói - Bồi hoàn */}
          <div className="staff-card">
            <div className="staff-card-header">
              <div>
                <h3 className="staff-card-title">Biểu đồ biến động Doanh thu Gói đẩy tin & Bồi hoàn theo kỳ</h3>
                <p className="staff-card-subtitle">
                  Đối chiếu tổng tiền nạp ví, doanh thu kích hoạt gói đẩy tin và giá trị hoàn tiền cho người dùng
                </p>
              </div>
              <div style={{ display: "flex", gap: "14px", fontSize: "0.78rem", fontWeight: 600 }}>
                <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                  <span style={{ width: 10, height: 10, borderRadius: 2, backgroundColor: "#0f3d32" }} />
                  Tiền nạp ví
                </span>
                <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                  <span style={{ width: 10, height: 10, borderRadius: 2, backgroundColor: "#0ea5e9" }} />
                  Doanh thu gói đẩy tin
                </span>
                <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                  <span style={{ width: 10, height: 10, borderRadius: 2, backgroundColor: "#f59e0b" }} />
                  Tiền bồi hoàn
                </span>
              </div>
            </div>

            <div style={{ padding: "1rem 1.5rem 1.25rem" }}>
              <div className="staff-bar-chart">
                {activeSeries.map((pt) => (
                  <div key={pt.label} className="staff-bar-col">
                    <div className="staff-bar-group">
                      <div
                        className="staff-bar-fill"
                        title={`Nạp ví: ${pt.topupAmount}`}
                        style={{ height: `${pt.topupPct}%`, backgroundColor: "#0f3d32" }}
                      />
                      <div
                        className="staff-bar-fill"
                        title={`Mua gói: ${pt.packageRevenue}`}
                        style={{ height: `${pt.pkgPct}%`, backgroundColor: "#0ea5e9" }}
                      />
                      <div
                        className="staff-bar-fill"
                        title={`Bồi hoàn: ${pt.refundAmount}`}
                        style={{ height: `${pt.refundPct}%`, backgroundColor: "#f59e0b" }}
                      />
                    </div>
                    <span className="staff-bar-label">{pt.label}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Bảng phân tích theo từng loại Gói đẩy tin */}
          <div className="staff-card">
            <div className="staff-card-header">
              <div>
                <h3 className="staff-card-title">Cơ cấu doanh thu & bồi hoàn theo từng loại Gói đẩy tin</h3>
                <p className="staff-card-subtitle">
                  Chi tiết lượt mua mới, lượt cộng dồn thời hạn và số ca bồi hoàn theo từng gói dịch vụ
                </p>
              </div>
            </div>

            <div className="staff-table-wrap">
              <table className="staff-table">
                <thead>
                  <tr>
                    <th>Mã gói</th>
                    <th>Tên gói dịch vụ</th>
                    <th>Lượt mua mới</th>
                    <th>Lượt cộng dồn</th>
                    <th>Tổng doanh thu gộp</th>
                    <th>Số ca & Tiền bồi hoàn</th>
                    <th>Tỷ trọng</th>
                  </tr>
                </thead>
                <tbody>
                  {packageBreakdown.map((pkg) => (
                    <tr key={pkg.packageCode}>
                      <td style={{ fontWeight: 700, color: "#0f3d32" }}>{pkg.packageCode}</td>
                      <td style={{ fontWeight: 600 }}>{pkg.packageName}</td>
                      <td>{pkg.purchases} lượt</td>
                      <td>{pkg.stackedCount} lượt</td>
                      <td style={{ fontWeight: 700 }}>{pkg.grossRevenue}</td>
                      <td>
                        <span className="staff-badge amber">
                          {pkg.refundedCases} ca • {pkg.refundedAmount}
                        </span>
                      </td>
                      <td style={{ fontWeight: 700 }}>{pkg.netShare}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : (
        <>
          {/* KPI Thống kê Tin đăng & Báo cáo vi phạm */}
          <div className="staff-kpi-grid">
            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Tổng tin gửi thẩm định</span>
                <span className="staff-badge blue">Toàn sàn</span>
              </div>
              <div className="staff-kpi-value">1.020</div>
              <div className="staff-kpi-sub">
                <span>790 đăng mới • 230 sửa nộp lại</span>
              </div>
            </div>

            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Tin đã được phê duyệt</span>
                <span className="staff-badge green">85,2% tỷ lệ duyệt</span>
              </div>
              <div className="staff-kpi-value">869</div>
              <div className="staff-kpi-sub">
                <span className="staff-kpi-trend-up">Thời gian duyệt TB: 28 phút</span>
              </div>
            </div>

            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Tin bị từ chối</span>
                <span className="staff-badge amber">10,6% tổng tin</span>
              </div>
              <div className="staff-kpi-value">108</div>
              <div className="staff-kpi-sub">
                <span>76% đã chỉnh sửa và gửi duyệt lại</span>
              </div>
            </div>

            <div className="staff-kpi-card">
              <div className="staff-kpi-top">
                <span className="staff-kpi-label">Báo cáo vi phạm & Gỡ tin</span>
                <span className="staff-badge red">Đã gỡ vi phạm</span>
              </div>
              <div className="staff-kpi-value">79 / 43</div>
              <div className="staff-kpi-sub">
                <span className="staff-kpi-trend-danger">79 báo cáo • 43 tin bị gỡ</span>
              </div>
            </div>
          </div>

          {/* Bảng thống kê tin đăng & báo cáo theo Khu vực */}
          <div className="staff-card">
            <div className="staff-card-header">
              <div>
                <h3 className="staff-card-title">Thống kê trạng thái tin đăng & báo cáo vi phạm theo Khu vực</h3>
                <p className="staff-card-subtitle">
                  Tổng hợp số lượng tin đăng mới, tin đã duyệt, bị từ chối và bị gỡ do vi phạm theo từng địa phương
                </p>
              </div>
            </div>

            <div className="staff-table-wrap">
              <table className="staff-table">
                <thead>
                  <tr>
                    <th>Khu vực</th>
                    <th>Tổng tin gửi</th>
                    <th>Đã phê duyệt</th>
                    <th>Đã từ chối</th>
                    <th>Đã gỡ do vi phạm</th>
                    <th>Số báo cáo nhận</th>
                    <th>Tỷ lệ duyệt</th>
                  </tr>
                </thead>
                <tbody>
                  {regionalListingStats.map((row) => (
                    <tr key={row.region}>
                      <td style={{ fontWeight: 700 }}>{row.region}</td>
                      <td>{row.totalSubmitted}</td>
                      <td style={{ color: "#059669", fontWeight: 700 }}>{row.approved}</td>
                      <td style={{ color: "#d97706", fontWeight: 600 }}>{row.rejected}</td>
                      <td style={{ color: "#dc2626", fontWeight: 700 }}>{row.removedViolation}</td>
                      <td>{row.reportCount} báo cáo</td>
                      <td>
                        <span className="staff-badge green">{row.approvalRate}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
