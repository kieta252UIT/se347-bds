import { useState } from "react";
import { toast } from "@/stores/useToastStore";

interface ReconciliationRecord {
  id: string;
  txRef: string;
  userEmail: string;
  userName: string;
  gatewayAmount: string;
  internalAmount: string;
  difference: string;
  reconcileState: "khop" | "lech_can_xu_ly" | "da_dieu_chinh";
  batchTime: string;
  auditNote?: string;
}

const initialReconciliations: ReconciliationRecord[] = [
  {
    id: "DS-20261118-01",
    txRef: "GD-20261118-0092",
    userEmail: "namle.bds@gmail.com",
    userName: "Lê Hoàng Nam",
    gatewayAmount: "3.500.000 đ",
    internalAmount: "0 đ (Chưa ghi nhận)",
    difference: "+3.500.000 đ",
    reconcileState: "lech_can_xu_ly",
    batchTime: "18/11/2026 02:00 (Phiên tự động)",
  },
  {
    id: "DS-20261118-02",
    txRef: "GD-20261118-0074",
    userEmail: "maitran88@gmail.com",
    userName: "Trần Thị Mai",
    gatewayAmount: "1.200.000 đ",
    internalAmount: "1.000.000 đ",
    difference: "+200.000 đ",
    reconcileState: "lech_can_xu_ly",
    batchTime: "18/11/2026 02:00 (Phiên tự động)",
  },
  {
    id: "DS-20261118-03",
    txRef: "GD-20261118-0041",
    userEmail: "tuan.nm@gmail.com",
    userName: "Nguyễn Minh Tuấn",
    gatewayAmount: "2.000.000 đ",
    internalAmount: "2.000.000 đ",
    difference: "0 đ",
    reconcileState: "khop",
    batchTime: "18/11/2026 02:00 (Phiên tự động)",
  },
  {
    id: "DS-20261117-19",
    txRef: "GD-20261117-0512",
    userEmail: "baopq.hn@gmail.com",
    userName: "Phạm Quốc Bảo",
    gatewayAmount: "1.500.000 đ",
    internalAmount: "1.500.000 đ",
    difference: "0 đ (Đã bù)",
    reconcileState: "da_dieu_chinh",
    batchTime: "17/11/2026 02:00 (Phiên tự động)",
    auditNote: "Đã cộng bù 1.500.000đ theo biên bản đối soát cổng thanh toán ngày 17/11.",
  },
];

const adjustTypeLabel: Record<"CONG_BU" | "TRU_THU_HOI" | "BOI_HOAN_GOI", string> = {
  CONG_BU: "Cộng bù giao dịch lệch",
  BOI_HOAN_GOI: "Bồi hoàn chi phí gói đẩy tin",
  TRU_THU_HOI: "Trừ thu hồi số dư ghi nhận nhầm",
};

export function ReconciliationPage() {
  const [records, setRecords] = useState<ReconciliationRecord[]>(initialReconciliations);
  const [stateFilter, setStateFilter] = useState<"ALL" | "lech_can_xu_ly" | "khop" | "da_dieu_chinh">("ALL");

  // Modal Điều chỉnh ví
  const [adjustModalOpen, setAdjustModalOpen] = useState(false);
  const [targetRecord, setTargetRecord] = useState<ReconciliationRecord | null>(null);
  const [adjustEmail, setAdjustEmail] = useState("");
  const [adjustType, setAdjustType] = useState<"CONG_BU" | "TRU_THU_HOI" | "BOI_HOAN_GOI">("CONG_BU");
  const [adjustAmount, setAdjustAmount] = useState("");
  const [adjustReason, setAdjustReason] = useState("");

  const filteredRecords = records.filter((r) => {
    if (stateFilter !== "ALL" && r.reconcileState !== stateFilter) return false;
    return true;
  });

  const handleOpenAdjustModal = (record?: ReconciliationRecord) => {
    if (record) {
      setTargetRecord(record);
      setAdjustEmail(record.userEmail);
      setAdjustAmount(record.difference.replace(/[^\d]/g, ""));
      setAdjustType("CONG_BU");
      setAdjustReason(`Điều chỉnh khớp lệch đối soát phiên ${record.batchTime} cho mã giao dịch ${record.txRef}`);
    } else {
      setTargetRecord(null);
      setAdjustEmail("");
      setAdjustAmount("");
      setAdjustType("CONG_BU");
      setAdjustReason("");
    }
    setAdjustModalOpen(true);
  };

  const handleConfirmAdjustment = () => {
    if (!adjustEmail.trim() || !adjustAmount.trim() || !adjustReason.trim()) {
      toast.error("Vui lòng nhập đầy đủ email ví, số tiền và lý do điều chỉnh.", "Thiếu thông tin");
      return;
    }

    if (targetRecord) {
      setRecords((prev) =>
        prev.map((item) =>
          item.id === targetRecord.id
            ? {
                ...item,
                internalAmount: item.gatewayAmount,
                difference: "0 đ (Đã điều chỉnh)",
                reconcileState: "da_dieu_chinh",
                auditNote: adjustReason.trim(),
              }
            : item
        )
      );
    }

    toast.success(
      `Đã thực hiện điều chỉnh ví (${adjustTypeLabel[adjustType]}) cho tài khoản ${adjustEmail} với số tiền ${Number(adjustAmount).toLocaleString("vi-VN")} đ.`,
      "Điều chỉnh ví thành công"
    );
    setAdjustModalOpen(false);
  };

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Đối soát giao dịch & Điều chỉnh ví</h1>
          <p>
            Kiểm tra kết quả từ phiên đối soát tự động 02:00 sáng với Cổng thanh toán và thực hiện điều chỉnh số dư ví người dùng khi có sai lệch.
          </p>
        </div>
        <div className="staff-header-actions">
          <button
            type="button"
            className="staff-btn staff-btn-dark"
            onClick={() => handleOpenAdjustModal()}
          >
            + Tạo phiếu điều chỉnh ví thủ công
          </button>
        </div>
      </div>

      {/* KPI Đối soát */}
      <div className="staff-kpi-grid">
        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Phiên đối soát 02:00 gần nhất</span>
            <span className="staff-badge green">Hoàn tất</span>
          </div>
          <div className="staff-kpi-value">02:00 hôm nay</div>
          <div className="staff-kpi-sub">
            <span>Đã đối chiếu 142 giao dịch nạp ví</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Giao dịch khớp tự động</span>
            <span className="staff-badge green">Đối soát chuẩn</span>
          </div>
          <div className="staff-kpi-value">140 / 142</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-up">Tỷ lệ khớp 98,6%</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Giao dịch lệch chờ xử lý</span>
            <span className="staff-badge amber">Cần điều chỉnh</span>
          </div>
          <div className="staff-kpi-value">
            {records.filter((r) => r.reconcileState === "lech_can_xu_ly").length}
          </div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-warn">Tổng lệch: +3.700.000 đ</span>
          </div>
        </div>

        <div className="staff-kpi-card">
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Phiếu điều chỉnh ví đã lập</span>
            <span className="staff-badge blue">Đã lưu biên bản</span>
          </div>
          <div className="staff-kpi-value">
            {records.filter((r) => r.reconcileState === "da_dieu_chinh").length}
          </div>
          <div className="staff-kpi-sub">
            <span>Lưu đầy đủ lý do vào nhật ký hệ thống</span>
          </div>
        </div>
      </div>

      {/* Sub-tabs */}
      <div className="staff-subtabs">
        <button
          type="button"
          className={`staff-subtab-btn ${stateFilter === "ALL" ? "active" : ""}`}
          onClick={() => setStateFilter("ALL")}
        >
          Tất cả bản ghi đối soát ({records.length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${stateFilter === "lech_can_xu_ly" ? "active" : ""}`}
          onClick={() => setStateFilter("lech_can_xu_ly")}
        >
          Lệch cần xử lý ({records.filter((r) => r.reconcileState === "lech_can_xu_ly").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${stateFilter === "da_dieu_chinh" ? "active" : ""}`}
          onClick={() => setStateFilter("da_dieu_chinh")}
        >
          Đã điều chỉnh ví ({records.filter((r) => r.reconcileState === "da_dieu_chinh").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${stateFilter === "khop" ? "active" : ""}`}
          onClick={() => setStateFilter("khop")}
        >
          Khớp hoàn toàn ({records.filter((r) => r.reconcileState === "khop").length})
        </button>
      </div>

      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">Bảng kết quả đối soát giao dịch nạp ví</h3>
            <p className="staff-card-subtitle">
              So sánh số liệu thanh toán thực tế từ Cổng thanh toán và số dư ghi nhận trên hệ thống
            </p>
          </div>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Mã giao dịch / Phiên</th>
                <th>Tài khoản người dùng</th>
                <th>Số tiền Cổng TT</th>
                <th>Ghi nhận Nội bộ</th>
                <th>Chênh lệch</th>
                <th>Trạng thái đối soát</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredRecords.map((r) => (
                <tr key={r.id}>
                  <td>
                    <div style={{ fontWeight: 700 }}>{r.txRef}</div>
                    <div style={{ fontSize: "0.75rem", color: "#64748b" }}>{r.batchTime}</div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{r.userName}</div>
                    <div style={{ fontSize: "0.78rem", color: "#64748b" }}>{r.userEmail}</div>
                  </td>
                  <td style={{ fontWeight: 700, color: "#0f3d32" }}>{r.gatewayAmount}</td>
                  <td>{r.internalAmount}</td>
                  <td
                    style={{
                      fontWeight: 700,
                      color: r.reconcileState === "lech_can_xu_ly" ? "#d97706" : "#059669",
                    }}
                  >
                    {r.difference}
                  </td>
                  <td>
                    {r.reconcileState === "lech_can_xu_ly" && (
                      <span className="staff-badge amber">Lệch cần xử lý</span>
                    )}
                    {r.reconcileState === "khop" && (
                      <span className="staff-badge green">Khớp 100%</span>
                    )}
                    {r.reconcileState === "da_dieu_chinh" && (
                      <span className="staff-badge blue">Đã điều chỉnh ví</span>
                    )}
                  </td>
                  <td>
                    {r.reconcileState === "lech_can_xu_ly" ? (
                      <button
                        type="button"
                        className="staff-btn staff-btn-warning staff-btn-sm"
                        onClick={() => handleOpenAdjustModal(r)}
                      >
                        Điều chỉnh ví
                      </button>
                    ) : (
                      <span style={{ fontSize: "0.78rem", color: "#64748b" }}>
                        {r.auditNote || "Không cần thao tác"}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal Điều chỉnh ví */}
      {adjustModalOpen && (
        <div className="staff-modal-backdrop" role="dialog" aria-modal="true">
          <div className="staff-modal">
            <div className="staff-modal-header">
              <h3>Phiếu điều chỉnh số dư ví người dùng</h3>
              <button
                type="button"
                className="staff-btn staff-btn-outline staff-btn-sm"
                onClick={() => setAdjustModalOpen(false)}
              >
                Đóng
              </button>
            </div>
            <div className="staff-modal-body">
              <div className="staff-filter-field">
                <label htmlFor="adj-email">Email tài khoản nhận điều chỉnh *</label>
                <input
                  id="adj-email"
                  type="email"
                  className="staff-input"
                  placeholder="user@example.com"
                  value={adjustEmail}
                  onChange={(e) => setAdjustEmail(e.target.value)}
                />
              </div>

              <div className="staff-filter-field">
                <label htmlFor="adj-type">Loại nghiệp vụ điều chỉnh *</label>
                <select
                  id="adj-type"
                  className="staff-select"
                  value={adjustType}
                  onChange={(e) => setAdjustType(e.target.value as typeof adjustType)}
                >
                  <option value="CONG_BU">Cộng bù giao dịch nạp ví bị lệch</option>
                  <option value="BOI_HOAN_GOI">Bồi hoàn chi phí gói đẩy tin theo khiếu nại</option>
                  <option value="TRU_THU_HOI">Trừ thu hồi số dư ghi nhận nhầm</option>
                </select>
              </div>

              <div className="staff-filter-field">
                <label htmlFor="adj-amount">Số tiền điều chỉnh (VNĐ) *</label>
                <input
                  id="adj-amount"
                  type="number"
                  className="staff-input"
                  placeholder="3500000"
                  value={adjustAmount}
                  onChange={(e) => setAdjustAmount(e.target.value)}
                />
              </div>

              <div className="staff-filter-field">
                <label htmlFor="adj-reason">Biên bản / Lý do điều chỉnh bắt buộc *</label>
                <textarea
                  id="adj-reason"
                  rows={3}
                  className="staff-textarea"
                  placeholder="Ghi rõ mã giao dịch cổng thanh toán hoặc mã khiếu nại liên quan..."
                  value={adjustReason}
                  onChange={(e) => setAdjustReason(e.target.value)}
                />
              </div>
            </div>
            <div className="staff-modal-footer">
              <button
                type="button"
                className="staff-btn staff-btn-outline"
                onClick={() => setAdjustModalOpen(false)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="staff-btn staff-btn-dark"
                onClick={handleConfirmAdjustment}
              >
                Xác nhận điều chỉnh ví
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
