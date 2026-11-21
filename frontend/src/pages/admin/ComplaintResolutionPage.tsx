import { useState } from "react";
import { toast } from "@/stores/useToastStore";

interface ComplaintItem {
  id: string;
  userEmail: string;
  userName: string;
  relatedRef: string;
  type: "GO_TIN_VI_PHAM" | "BOI_HOAN_GOI_DAY" | "GIAO_DICH_VI";
  submissionCount: number;
  subject: string;
  content: string;
  requestedRefund: string;
  submittedAt: string;
  status: "cho_giai_quyet" | "chap_thuan" | "tu_choi";
  resolutionNote?: string;
}

const initialComplaints: ComplaintItem[] = [
  {
    id: "KN-4012",
    userEmail: "phamgia.bds@gmail.com",
    userName: "Phạm Gia Hưng",
    relatedRef: "TIN-2026-0811 / Gói VIP 7 ngày",
    type: "BOI_HOAN_GOI_DAY",
    submissionCount: 1,
    subject: "Yêu cầu khôi phục tin và bồi hoàn 4 ngày gói đẩy tin VIP",
    content:
      "Tin đăng TIN-2026-0811 bị gỡ do báo cáo nhầm là không chính chủ. Tôi đã đính kèm hợp đồng ký gửi độc quyền có mộc đỏ. Đề nghị mở lại tin và hoàn 4 ngày gói VIP chưa sử dụng.",
    requestedRefund: "260.000 đ",
    submittedAt: "18/11/2026 08:15",
    status: "cho_giai_quyet",
  },
  {
    id: "KN-4009",
    userEmail: "lananh.realty@gmail.com",
    userName: "Nguyễn Lan Anh",
    relatedRef: "TIN-2026-0790",
    type: "GO_TIN_VI_PHAM",
    submissionCount: 2,
    subject: "Khiếu nại bổ sung lần 2: Đã cung cấp thêm ảnh thực tế căn hộ",
    content:
      "Lần 1 bị từ chối do thiếu ảnh mặt tiền tòa nhà. Tôi gửi lại khiếu nại kèm 5 ảnh chụp trực tiếp tại sảnh tòa B.",
    requestedRefund: "0 đ (Chỉ yêu cầu khôi phục tin)",
    submittedAt: "17/11/2026 19:40",
    status: "cho_giai_quyet",
  },
  {
    id: "KN-3998",
    userEmail: "quocviet.invest@gmail.com",
    userName: "Trần Quốc Việt",
    relatedRef: "GD-20261116-0118",
    type: "GIAO_DICH_VI",
    submissionCount: 1,
    subject: "Bị trừ tiền gói đẩy tin 2 lần khi mạng giật",
    content:
      "Lúc bấm kích hoạt gói 15 ngày tôi thấy xoay lâu nên tải lại trang, tài khoản bị trừ 2 lần nhưng thời hạn chỉ cộng 15 ngày.",
    requestedRefund: "450.000 đ",
    submittedAt: "16/11/2026 15:20",
    status: "chap_thuan",
    resolutionNote: "Đã kiểm tra lịch sử kích hoạt gói và hoàn lại 450.000đ vào ví người dùng.",
  },
];

export function ComplaintResolutionPage() {
  const [complaints, setComplaints] = useState<ComplaintItem[]>(initialComplaints);
  const [selectedId, setSelectedId] = useState<string>(initialComplaints[0].id);
  const [filterStatus, setFilterStatus] = useState<"ALL" | "cho_giai_quyet" | "chap_thuan" | "tu_choi">("ALL");
  const [resolutionText, setResolutionText] = useState("");

  const filtered = complaints.filter((c) => {
    if (filterStatus !== "ALL" && c.status !== filterStatus) return false;
    return true;
  });

  const selected = complaints.find((c) => c.id === selectedId) || filtered[0] || null;

  const handleResolve = (decision: "chap_thuan" | "tu_choi") => {
    if (!selected) return;
    if (!resolutionText.trim()) {
      toast.error("Vui lòng nhập nội dung phản hồi giải quyết khiếu nại cho người dùng.", "Thiếu nội dung phản hồi");
      return;
    }

    setComplaints((prev) =>
      prev.map((item) =>
        item.id === selected.id
          ? { ...item, status: decision, resolutionNote: resolutionText.trim() }
          : item
      )
    );

    if (decision === "chap_thuan") {
      toast.success(
        `Đã chấp thuận khiếu nại ${selected.id} và ghi nhận bồi hoàn / khôi phục cho ${selected.userEmail}.`,
        "Đã chấp thuận khiếu nại"
      );
    } else {
      toast.info(
        `Đã từ chối khiếu nại ${selected.id}. Người dùng có thể xem phản hồi chi tiết.`,
        "Đã từ chối khiếu nại"
      );
    }
    setResolutionText("");
  };

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Giải quyết khiếu nại người dùng</h1>
          <p>
            Tiếp nhận các đơn khiếu nại gửi mới hoặc gửi lại từ người dùng, xem xét khôi phục tin và phê duyệt bồi hoàn gói đẩy tin.
          </p>
        </div>
      </div>

      <div className="staff-subtabs">
        <button
          type="button"
          className={`staff-subtab-btn ${filterStatus === "ALL" ? "active" : ""}`}
          onClick={() => setFilterStatus("ALL")}
        >
          Tất cả khiếu nại ({complaints.length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${filterStatus === "cho_giai_quyet" ? "active" : ""}`}
          onClick={() => setFilterStatus("cho_giai_quyet")}
        >
          Chờ giải quyết ({complaints.filter((c) => c.status === "cho_giai_quyet").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${filterStatus === "chap_thuan" ? "active" : ""}`}
          onClick={() => setFilterStatus("chap_thuan")}
        >
          Đã chấp thuận & Bồi hoàn ({complaints.filter((c) => c.status === "chap_thuan").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${filterStatus === "tu_choi" ? "active" : ""}`}
          onClick={() => setFilterStatus("tu_choi")}
        >
          Đã từ chối ({complaints.filter((c) => c.status === "tu_choi").length})
        </button>
      </div>

      <div className="staff-split-grid">
        <div className="staff-card">
          <div className="staff-card-header">
            <div>
              <h3 className="staff-card-title">Danh sách hồ sơ khiếu nại</h3>
              <p className="staff-card-subtitle">Chọn một hồ sơ để thẩm tra và ra quyết định bên phải</p>
            </div>
          </div>

          <div className="staff-table-wrap">
            <table className="staff-table">
              <thead>
                <tr>
                  <th>Mã KN / Lần gửi</th>
                  <th>Tiêu đề & Người gửi</th>
                  <th>Mức bồi hoàn yêu cầu</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((item) => (
                  <tr
                    key={item.id}
                    className={selected?.id === item.id ? "selected-row" : ""}
                    onClick={() => {
                      setSelectedId(item.id);
                      setResolutionText(item.resolutionNote || "");
                    }}
                    style={{ cursor: "pointer" }}
                  >
                    <td>
                      <div style={{ fontWeight: 700 }}>{item.id}</div>
                      <span className="staff-badge slate" style={{ marginTop: "3px" }}>
                        Lần gửi #{item.submissionCount}
                      </span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600, color: "#0f172a" }}>{item.subject}</div>
                      <div style={{ fontSize: "0.78rem", color: "#64748b" }}>
                        {item.userName} ({item.userEmail}) • {item.relatedRef}
                      </div>
                    </td>
                    <td style={{ fontWeight: 700, color: "#0f3d32" }}>{item.requestedRefund}</td>
                    <td>
                      {item.status === "cho_giai_quyet" && (
                        <span className="staff-badge amber">Chờ giải quyết</span>
                      )}
                      {item.status === "chap_thuan" && (
                        <span className="staff-badge green">Đã chấp thuận</span>
                      )}
                      {item.status === "tu_choi" && (
                        <span className="staff-badge red">Đã từ chối</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Cột phải: Xử lý khiếu nại */}
        <div className="staff-card">
          {selected ? (
            <>
              <div className="staff-card-header">
                <div>
                  <span style={{ fontSize: "0.75rem", fontWeight: 700, color: "#0f172a" }}>
                    CHI TIẾT HỒ SƠ KHIẾU NẠI • {selected.id} (Gửi lần #{selected.submissionCount})
                  </span>
                  <h3 className="staff-card-title" style={{ marginTop: "4px" }}>
                    {selected.subject}
                  </h3>
                </div>
              </div>

              <div style={{ padding: "1.25rem", display: "flex", flexDirection: "column", gap: "1rem" }}>
                <div
                  style={{
                    backgroundColor: "#f8fafc",
                    border: "1px solid #e2e8f0",
                    borderRadius: "8px",
                    padding: "0.85rem",
                    fontSize: "0.85rem",
                    display: "grid",
                    gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                    gap: "0.65rem",
                  }}
                >
                  <div>
                    <div style={{ fontSize: "0.75rem", color: "#64748b" }}>Người gửi khiếu nại</div>
                    <div style={{ fontWeight: 700 }}>{selected.userName}</div>
                    <div>{selected.userEmail}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: "0.75rem", color: "#64748b" }}>Mã tin / Giao dịch liên quan</div>
                    <div style={{ fontWeight: 700, color: "#0f3d32" }}>{selected.relatedRef}</div>
                    <div style={{ fontSize: "0.78rem", color: "#64748b" }}>{selected.submittedAt}</div>
                  </div>
                  <div style={{ gridColumn: "1 / -1" }}>
                    <div style={{ fontSize: "0.75rem", color: "#64748b" }}>Yêu cầu bồi hoàn / khôi phục</div>
                    <div style={{ fontWeight: 700, color: "#d97706" }}>{selected.requestedRefund}</div>
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.8rem", fontWeight: 700, color: "#334155", marginBottom: "4px" }}>
                    Nội dung giải trình từ người dùng
                  </div>
                  <div
                    style={{
                      backgroundColor: "#ffffff",
                      border: "1px solid #e2e8f0",
                      borderRadius: "8px",
                      padding: "0.85rem",
                      fontSize: "0.86rem",
                      lineHeight: 1.55,
                    }}
                  >
                    {selected.content}
                  </div>
                </div>

                <div>
                  <label
                    htmlFor="complaint-resolution-note"
                    style={{ display: "block", fontSize: "0.82rem", fontWeight: 700, marginBottom: "6px" }}
                  >
                    Quyết định & Phản hồi của Quản trị viên *
                  </label>
                  <textarea
                    id="complaint-resolution-note"
                    rows={4}
                    className="staff-textarea"
                    placeholder="Nhập kết luận xác minh, quyết định khôi phục tin hoặc số tiền bồi hoàn vào ví..."
                    value={resolutionText}
                    onChange={(e) => setResolutionText(e.target.value)}
                  />
                </div>

                <div style={{ display: "flex", gap: "0.75rem" }}>
                  <button
                    type="button"
                    className="staff-btn staff-btn-success"
                    style={{ flex: 1 }}
                    onClick={() => handleResolve("chap_thuan")}
                  >
                    ✓ Chấp thuận & Bồi hoàn
                  </button>
                  <button
                    type="button"
                    className="staff-btn staff-btn-danger"
                    style={{ flex: 1 }}
                    onClick={() => handleResolve("tu_choi")}
                  >
                    ✕ Từ chối khiếu nại
                  </button>
                </div>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
