import { useState } from "react";
import { toast } from "@/stores/useToastStore";

interface ViolationReportItem {
  id: string;
  listingId: string;
  listingTitle: string;
  ownerName: string;
  ownerEmail: string;
  priorViolationCount: number;
  reporterEmail: string;
  category: string;
  evidenceNote: string;
  createdAt: string;
  status: "cho_xu_ly" | "da_go_tin" | "da_bac_bo";
  lockProposalStatus: "khong" | "cho_admin";
  lockProposalNote?: string;
}

const initialReports: ViolationReportItem[] = [
  {
    id: "BC-8821",
    listingId: "TIN-2026-0912",
    listingTitle: "Bán gấp nhà mặt tiền Quận 1 chỉ 1,9 tỷ sổ hồng công chứng ngay",
    ownerName: "Đặng Quốc Hưng",
    ownerEmail: "bds.giare.sg@gmail.com",
    priorViolationCount: 3,
    reporterEmail: "hoang.vu92@gmail.com",
    category: "Giá rao ảo / Lừa đảo đặt cọc",
    evidenceNote:
      "Khi liên hệ qua số điện thoại thì môi giới báo nhà Quận 1 đã bán và dẫn khách xuống dự án ở Long An, yêu cầu chuyển khoản giữ chỗ trước.",
    createdAt: "18/11/2026 08:45",
    status: "cho_xu_ly",
    lockProposalStatus: "khong",
  },
  {
    id: "BC-8819",
    listingId: "TIN-2026-0894",
    listingTitle: "Đất nền thổ cư 100% trung tâm Thủ Đức 80m² giá 1,5 tỷ",
    ownerName: "Võ Văn Phát",
    ownerEmail: "nhadat.phatloc@gmail.com",
    priorViolationCount: 2,
    reporterEmail: "thanh.nguyen@gmail.com",
    category: "Giấy tờ pháp lý giả mạo / Cắt ghép",
    evidenceNote:
      "Ảnh chụp sổ hồng trong bài đăng bị chỉnh sửa số thửa và tờ bản đồ, thực tế khu đất này thuộc hành lang quy hoạch cây xanh.",
    createdAt: "18/11/2026 07:20",
    status: "cho_xu_ly",
    lockProposalStatus: "khong",
  },
  {
    id: "BC-8810",
    listingId: "TIN-2026-0840",
    listingTitle: "Căn hộ Sunrise City Quận 7 2PN nội thất cao cấp giá tốt",
    ownerName: "Trương Hoàng Long",
    ownerEmail: "canho.dongnam@gmail.com",
    priorViolationCount: 4,
    reporterEmail: "minhchau.le@gmail.com",
    category: "Spam / Đăng trùng lặp nhiều lần",
    evidenceNote:
      "Tài khoản này dùng cùng một bộ ảnh đăng lặp lại 5 tin liên tiếp để chiếm trang đầu tìm kiếm.",
    createdAt: "17/11/2026 21:10",
    status: "da_go_tin",
    lockProposalStatus: "cho_admin",
    lockProposalNote: "Tài khoản tái phạm đăng trùng lặp và tin ảo lần thứ 4 trong tháng. Đề xuất Quản trị viên khóa tài khoản.",
  },
];

export function ReportHandlingPage() {
  const [reports, setReports] = useState<ViolationReportItem[]>(initialReports);
  const [selectedId, setSelectedId] = useState<string>(initialReports[0].id);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "cho_xu_ly" | "da_go_tin" | "da_bac_bo" | "cho_admin">("ALL");

  // Modal đề xuất khóa tài khoản
  const [proposalModalOpen, setProposalModalOpen] = useState(false);
  const [proposalNote, setProposalNote] = useState("");

  const filteredReports = reports.filter((r) => {
    if (statusFilter === "cho_admin") return r.lockProposalStatus === "cho_admin";
    if (statusFilter !== "ALL" && r.status !== statusFilter) return false;
    return true;
  });

  const selectedReport = reports.find((r) => r.id === selectedId) || filteredReports[0] || null;

  const handleRemoveViolatingPost = (report: ViolationReportItem) => {
    setReports((prev) =>
      prev.map((item) => (item.id === report.id ? { ...item, status: "da_go_tin" } : item))
    );
    toast.success(
      `Đã gỡ tin ${report.listingId} khỏi trang công khai và hoàn tất xử lý báo cáo ${report.id}.`,
      "Đã gỡ tin vi phạm"
    );
  };

  const handleDismissReport = (report: ViolationReportItem) => {
    setReports((prev) =>
      prev.map((item) => (item.id === report.id ? { ...item, status: "da_bac_bo" } : item))
    );
    toast.info(
      `Đã bác bỏ báo cáo ${report.id}. Tin đăng ${report.listingId} được giữ nguyên hiển thị.`,
      "Đã bác bỏ báo cáo"
    );
  };

  const handleConfirmLockProposal = () => {
    if (!selectedReport) return;
    if (!proposalNote.trim()) {
      toast.error("Vui lòng nhập căn cứ vi phạm để Quản trị viên xét duyệt khóa tài khoản.", "Thiếu căn cứ đề xuất");
      return;
    }

    setReports((prev) =>
      prev.map((item) =>
        item.id === selectedReport.id
          ? {
              ...item,
              status: "da_go_tin",
              lockProposalStatus: "cho_admin",
              lockProposalNote: proposalNote.trim(),
            }
          : item
      )
    );
    toast.warning(
      `Đã gửi đề xuất khóa tài khoản ${selectedReport.ownerEmail} sang Quản trị viên xét duyệt.`,
      "Đã gửi đề xuất khóa"
    );
    setProposalModalOpen(false);
    setProposalNote("");
  };

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Xử lý báo cáo vi phạm & Đề xuất khóa</h1>
          <p>
            Xác minh báo cáo phản ánh từ người dùng, gỡ tin vi phạm quy chuẩn và gửi đề xuất khóa tài khoản tái phạm lên Quản trị viên.
          </p>
        </div>
      </div>

      {/* Sub-tabs lọc nhanh */}
      <div className="staff-subtabs">
        <button
          type="button"
          className={`staff-subtab-btn ${statusFilter === "ALL" ? "active" : ""}`}
          onClick={() => setStatusFilter("ALL")}
        >
          Tất cả báo cáo ({reports.length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${statusFilter === "cho_xu_ly" ? "active" : ""}`}
          onClick={() => setStatusFilter("cho_xu_ly")}
        >
          Chờ xử lý ({reports.filter((r) => r.status === "cho_xu_ly").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${statusFilter === "da_go_tin" ? "active" : ""}`}
          onClick={() => setStatusFilter("da_go_tin")}
        >
          Đã gỡ tin vi phạm ({reports.filter((r) => r.status === "da_go_tin").length})
        </button>
        <button
          type="button"
          className={`staff-subtab-btn ${statusFilter === "cho_admin" ? "active" : ""}`}
          onClick={() => setStatusFilter("cho_admin")}
        >
          Đã đề xuất khóa gửi Quản trị viên ({reports.filter((r) => r.lockProposalStatus === "cho_admin").length})
        </button>
      </div>

      <div className="staff-split-grid">
        {/* Cột trái: Danh sách báo cáo */}
        <div className="staff-card">
          <div className="staff-card-header">
            <div>
              <h3 className="staff-card-title">Danh sách báo cáo vi phạm</h3>
              <p className="staff-card-subtitle">Chọn một báo cáo để xem bằng chứng và thực hiện xử lý</p>
            </div>
          </div>

          <div className="staff-table-wrap">
            <table className="staff-table">
              <thead>
                <tr>
                  <th>Mã BC / Tin</th>
                  <th>Nhóm vi phạm & Chủ tin</th>
                  <th>Trạng thái xử lý</th>
                  <th>Đề xuất khóa</th>
                </tr>
              </thead>
              <tbody>
                {filteredReports.map((r) => (
                  <tr
                    key={r.id}
                    className={selectedReport?.id === r.id ? "selected-row" : ""}
                    onClick={() => setSelectedId(r.id)}
                    style={{ cursor: "pointer" }}
                  >
                    <td>
                      <div style={{ fontWeight: 700 }}>{r.id}</div>
                      <div style={{ fontSize: "0.75rem", color: "#0f3d32", fontWeight: 600 }}>{r.listingId}</div>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600, color: "#0f172a" }}>{r.category}</div>
                      <div style={{ fontSize: "0.78rem", color: "#64748b" }}>
                        Chủ tin: {r.ownerEmail} ({r.priorViolationCount} lần vi phạm)
                      </div>
                    </td>
                    <td>
                      {r.status === "cho_xu_ly" && <span className="staff-badge amber">Chờ xử lý</span>}
                      {r.status === "da_go_tin" && <span className="staff-badge red">Đã gỡ do vi phạm</span>}
                      {r.status === "da_bac_bo" && <span className="staff-badge slate">Đã bác bỏ</span>}
                    </td>
                    <td>
                      {r.lockProposalStatus === "cho_admin" ? (
                        <span className="staff-badge purple">Chờ Quản trị viên duyệt</span>
                      ) : (
                        <span style={{ fontSize: "0.78rem", color: "#94a3b8" }}>Chưa đề xuất</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Cột phải: Chi tiết báo cáo & Quyết định của KDV */}
        <div className="staff-card">
          {selectedReport ? (
            <>
              <div className="staff-card-header">
                <div>
                  <span style={{ fontSize: "0.75rem", fontWeight: 700, color: "#dc2626" }}>
                    HỒ SƠ BÁO CÁO VI PHẠM • {selectedReport.id}
                  </span>
                  <h3 className="staff-card-title" style={{ marginTop: "4px" }}>
                    {selectedReport.listingTitle}
                  </h3>
                </div>
              </div>

              <div style={{ padding: "1.25rem", display: "flex", flexDirection: "column", gap: "1rem" }}>
                <div
                  style={{
                    backgroundColor: "#f8fafc",
                    border: "1px solid #e2e8f0",
                    borderRadius: "8px",
                    padding: "0.9rem",
                    display: "grid",
                    gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                    gap: "0.75rem",
                    fontSize: "0.84rem",
                  }}
                >
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.75rem" }}>Mã tin bị báo cáo</div>
                    <div style={{ fontWeight: 700, color: "#0f3d32" }}>{selectedReport.listingId}</div>
                  </div>
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.75rem" }}>Thời điểm gửi báo cáo</div>
                    <div style={{ fontWeight: 600 }}>{selectedReport.createdAt}</div>
                  </div>
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.75rem" }}>Tài khoản người đăng tin</div>
                    <div style={{ fontWeight: 700 }}>{selectedReport.ownerName}</div>
                    <div style={{ fontSize: "0.78rem", color: "#475569" }}>{selectedReport.ownerEmail}</div>
                  </div>
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.75rem" }}>Lịch sử vi phạm của chủ tin</div>
                    <div style={{ fontWeight: 700, color: "#dc2626" }}>
                      {selectedReport.priorViolationCount} lần vi phạm đã xác nhận
                    </div>
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.8rem", fontWeight: 700, color: "#334155", marginBottom: "4px" }}>
                    Bằng chứng & Mô tả từ người báo cáo ({selectedReport.reporterEmail})
                  </div>
                  <div
                    style={{
                      backgroundColor: "#fffbeb",
                      border: "1px solid #fde68a",
                      borderRadius: "8px",
                      padding: "0.85rem",
                      fontSize: "0.86rem",
                      color: "#78350f",
                      lineHeight: 1.5,
                    }}
                  >
                    <strong>[{selectedReport.category}]:</strong> {selectedReport.evidenceNote}
                  </div>
                </div>

                {selectedReport.lockProposalStatus === "cho_admin" && (
                  <div
                    style={{
                      backgroundColor: "#f5f3ff",
                      border: "1px solid #ddd6fe",
                      borderRadius: "8px",
                      padding: "0.85rem",
                      fontSize: "0.84rem",
                      color: "#5b21b6",
                    }}
                  >
                    <strong>Đã chuyển đề xuất khóa tài khoản lên Quản trị viên:</strong>
                    <div style={{ marginTop: "4px" }}>{selectedReport.lockProposalNote}</div>
                  </div>
                )}

                <div style={{ display: "flex", gap: "0.65rem", flexWrap: "wrap", marginTop: "0.25rem" }}>
                  <button
                    type="button"
                    className="staff-btn staff-btn-danger"
                    onClick={() => handleRemoveViolatingPost(selectedReport)}
                  >
                    Gỡ tin vi phạm
                  </button>

                  <button
                    type="button"
                    className="staff-btn staff-btn-outline"
                    onClick={() => handleDismissReport(selectedReport)}
                  >
                    Bác bỏ báo cáo
                  </button>

                  <button
                    type="button"
                    className="staff-btn staff-btn-warning"
                    onClick={() => {
                      setProposalNote(
                        selectedReport.lockProposalNote ||
                          `Tài khoản ${selectedReport.ownerEmail} đăng tin vi phạm (${selectedReport.category}), đã tái phạm ${selectedReport.priorViolationCount} lần. Đề xuất Quản trị viên khóa tài khoản.`
                      );
                      setProposalModalOpen(true);
                    }}
                  >
                    Đề xuất khóa tài khoản
                  </button>
                </div>
              </div>
            </>
          ) : null}
        </div>
      </div>

      {/* Modal gửi đề xuất khóa tài khoản sang Quản trị viên */}
      {proposalModalOpen && selectedReport && (
        <div className="staff-modal-backdrop" role="dialog" aria-modal="true">
          <div className="staff-modal">
            <div className="staff-modal-header">
              <h3>Đề xuất Quản trị viên khóa tài khoản</h3>
              <button
                type="button"
                className="staff-btn staff-btn-outline staff-btn-sm"
                onClick={() => setProposalModalOpen(false)}
              >
                Đóng
              </button>
            </div>
            <div className="staff-modal-body">
              <div style={{ fontSize: "0.85rem", color: "#334155", lineHeight: 1.5 }}>
                Kiểm duyệt viên lập tờ trình đề xuất khóa đối với tài khoản vi phạm nhiều lần để chuyển sang danh sách chờ Quản trị viên phê duyệt.
              </div>
              <div
                style={{
                  backgroundColor: "#f8fafc",
                  padding: "0.75rem",
                  borderRadius: "8px",
                  border: "1px solid #e2e8f0",
                  fontSize: "0.84rem",
                }}
              >
                <div>
                  Tài khoản bị đề xuất khóa: <strong>{selectedReport.ownerName}</strong> ({selectedReport.ownerEmail})
                </div>
                <div>Số lần vi phạm ghi nhận: {selectedReport.priorViolationCount} lần</div>
              </div>
              <div>
                <label
                  htmlFor="lock-proposal-note"
                  style={{ display: "block", fontSize: "0.82rem", fontWeight: 600, marginBottom: "6px" }}
                >
                  Tờ trình / Lý do đề xuất khóa gửi Quản trị viên *
                </label>
                <textarea
                  id="lock-proposal-note"
                  rows={4}
                  className="staff-textarea"
                  value={proposalNote}
                  onChange={(e) => setProposalNote(e.target.value)}
                />
              </div>
            </div>
            <div className="staff-modal-footer">
              <button
                type="button"
                className="staff-btn staff-btn-outline"
                onClick={() => setProposalModalOpen(false)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="staff-btn staff-btn-warning"
                onClick={handleConfirmLockProposal}
              >
                Gửi đề xuất khóa
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
