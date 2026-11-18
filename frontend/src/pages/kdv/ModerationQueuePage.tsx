import { useState } from "react";
import { toast } from "@/stores/useToastStore";

interface ModerationItem {
  id: string;
  versionId: string;
  title: string;
  category: string;
  submissionType: "moi" | "nop_lai";
  status: "cho_kiem_duyet" | "da_duyet" | "bi_tu_choi";
  price: string;
  areaSize: string;
  address: string;
  legalDoc: string;
  ownerName: string;
  ownerEmail: string;
  ownerPhone: string;
  submittedAt: string;
  description: string;
  previousRejectionReason?: string;
  rejectionReason?: string;
  imageCount: number;
}

const initialQueue: ModerationItem[] = [
  {
    id: "TIN-2026-1042",
    versionId: "VER-9012-A",
    title: "Căn hộ 2PN Vinhomes Grand Park phân khu Origami tầng 12A view công viên",
    category: "Căn hộ chung cư",
    submissionType: "moi",
    status: "cho_kiem_duyet",
    price: "2,85 tỷ",
    areaSize: "68 m²",
    address: "Đường Nguyễn Xiển, Phường Long Thạnh Mỹ, TP. Thủ Đức, TP.HCM",
    legalDoc: "Hợp đồng mua bán / Sổ hồng sở hữu lâu dài",
    ownerName: "Nguyễn Minh Tuấn",
    ownerEmail: "tuan.nm@gmail.com",
    ownerPhone: "0908341289",
    submittedAt: "18/11/2026 09:40",
    description:
      "Bán căn hộ 2 phòng ngủ, 2 WC tại phân khu The Origami. Nhà trang bị đầy đủ nội thất gỗ An Cường, máy lạnh âm trần, ban công hướng Đông Nam thoáng mát. Hỗ trợ sang tên nhanh.",
    imageCount: 6,
  },
  {
    id: "TIN-2026-1039",
    versionId: "VER-8940-B",
    title: "Nhà phố mặt tiền đường số 7 Khu dân cư Trung Sơn 4x20m xây 1 trệt 3 lầu",
    category: "Nhà phố",
    submissionType: "nop_lai",
    status: "cho_kiem_duyet",
    price: "11,2 tỷ",
    areaSize: "80 m² (DTSD 280 m²)",
    address: "Đường số 7, KDC Trung Sơn, Xã Bình Hưng, Bình Chánh, TP.HCM",
    legalDoc: "Sổ hồng riêng hoàn công đầy đủ 2024",
    ownerName: "Lê Hoàng Nam",
    ownerEmail: "namle.bds@gmail.com",
    ownerPhone: "0913456890",
    submittedAt: "18/11/2026 09:15",
    previousRejectionReason: "Ảnh chụp mặt tiền bị mờ và thiếu thông tin diện tích sàn sử dụng.",
    description:
      "Đã bổ sung lại bộ 8 ảnh chụp thực tế mặt tiền và sổ hồng hoàn công rõ nét theo yêu cầu của Kiểm duyệt viên. Nhà gồm 4 phòng ngủ lớn, sân thượng trước sau, gara ô tô.",
    imageCount: 8,
  },
  {
    id: "TIN-2026-1035",
    versionId: "VER-8891-A",
    title: "Đất nền sổ hồng riêng KĐT Nam Hòa Xuân B2-45 diện tích 105m² đường 7m5",
    category: "Đất nền",
    submissionType: "moi",
    status: "cho_kiem_duyet",
    price: "3,45 tỷ",
    areaSize: "105 m² (5x21m)",
    address: "Khu đô thị Nam Hòa Xuân, Phường Hòa Quý, Quận Ngũ Hành Sơn, Đà Nẵng",
    legalDoc: "Sổ đỏ đất ở đô thị lâu dài",
    ownerName: "Trần Thị Mai",
    ownerEmail: "maitran88@gmail.com",
    ownerPhone: "0987612340",
    submittedAt: "18/11/2026 08:30",
    description:
      "Lô đất sạch không vướng cống hay tụ điện, gần trục đường chính Quảng Nam, khu dân cư hiện hữu đông đúc, thích hợp xây ở hoặc đầu tư lâu dài.",
    imageCount: 4,
  },
  {
    id: "TIN-2026-1028",
    versionId: "VER-8812-C",
    title: "Chung cư cao cấp The Zei Mỹ Đình 3PN 108m² căn góc 2 mặt thoáng",
    category: "Căn hộ chung cư",
    submissionType: "nop_lai",
    status: "cho_kiem_duyet",
    price: "6,9 tỷ",
    areaSize: "108 m²",
    address: "Số 8 Lê Đức Thọ, Phường Mỹ Đình 2, Quận Nam Từ Liêm, Hà Nội",
    legalDoc: "Sổ hồng chính chủ",
    ownerName: "Phạm Quốc Bảo",
    ownerEmail: "baopq.hn@gmail.com",
    ownerPhone: "0934889102",
    submittedAt: "18/11/2026 07:50",
    previousRejectionReason: "Tiêu đề chứa ký tự viết hoa toàn bộ và số điện thoại trực tiếp.",
    description:
      "Đã chỉnh sửa lại tiêu đề đúng quy chuẩn đăng tin của sàn HomeFinder. Căn góc tầng 19, nội thất nhập khẩu Đức, có suất đỗ ô tô định danh dưới hầm.",
    imageCount: 7,
  },
];

const presetRejectionReasons = [
  "Thông tin giá bán hoặc diện tích không khớp với mô tả thực tế",
  "Hình ảnh mờ, chứa đóng dấu (watermark) hoặc số điện thoại đè lên ảnh",
  "Địa chỉ bất động sản chưa cụ thể hoặc không thể xác thực",
  "Nội dung mô tả vi phạm quy chuẩn cộng đồng hoặc trùng lặp tin đã đăng",
];

export function ModerationQueuePage() {
  const [items, setItems] = useState<ModerationItem[]>(initialQueue);
  const [selectedId, setSelectedId] = useState<string>(initialQueue[0].id);
  const [filterStatus, setFilterStatus] = useState<"ALL" | "cho_kiem_duyet" | "da_duyet" | "bi_tu_choi">("cho_kiem_duyet");
  const [filterSubmission, setFilterSubmission] = useState<"ALL" | "moi" | "nop_lai">("ALL");
  const [searchQuery, setSearchQuery] = useState("");

  // Modal từ chối tin
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const filteredItems = items.filter((item) => {
    if (filterStatus !== "ALL" && item.status !== filterStatus) return false;
    if (filterSubmission !== "ALL" && item.submissionType !== filterSubmission) return false;
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      return (
        item.id.toLowerCase().includes(q) ||
        item.title.toLowerCase().includes(q) ||
        item.ownerName.toLowerCase().includes(q) ||
        item.ownerEmail.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const selectedItem = items.find((i) => i.id === selectedId) || filteredItems[0] || null;

  const handleApprove = (item: ModerationItem) => {
    setItems((prev) =>
      prev.map((entry) =>
        entry.id === item.id ? { ...entry, status: "da_duyet", rejectionReason: undefined } : entry
      )
    );
    toast.success(
      `Đã phê duyệt hiển thị tin ${item.id} (${item.versionId}). Tin đã được xuất bản công khai.`,
      "Phê duyệt tin đăng"
    );
  };

  const handleConfirmReject = () => {
    if (!selectedItem) return;
    if (!rejectReason.trim()) {
      toast.error("Vui lòng nhập hoặc chọn lý do từ chối để thông báo cho người đăng tin.", "Thiếu lý do từ chối");
      return;
    }

    setItems((prev) =>
      prev.map((entry) =>
        entry.id === selectedItem.id
          ? { ...entry, status: "bi_tu_choi", rejectionReason: rejectReason.trim() }
          : entry
      )
    );
    toast.info(
      `Đã từ chối tin ${selectedItem.id}. Người dùng có thể chỉnh sửa và gửi duyệt lại.`,
      "Từ chối tin đăng"
    );
    setRejectModalOpen(false);
    setRejectReason("");
  };

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Thẩm định tin đăng bất động sản</h1>
          <p>
            Kiểm tra nội dung tin đăng, hình ảnh và thông tin pháp lý trước khi cho phép hiển thị công khai trên sàn.
          </p>
        </div>
      </div>

      {/* Bộ lọc */}
      <div className="staff-filter-bar">
        <div className="staff-filter-field" style={{ flex: 2 }}>
          <label htmlFor="mod-search">Tìm kiếm mã tin, tiêu đề, chủ tin</label>
          <input
            id="mod-search"
            type="text"
            className="staff-input"
            placeholder="Nhập mã TIN-2026..., email hoặc tên người đăng..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className="staff-filter-field">
          <label htmlFor="mod-status">Trạng thái thẩm định</label>
          <select
            id="mod-status"
            className="staff-select"
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value as typeof filterStatus)}
          >
            <option value="cho_kiem_duyet">Chờ kiểm duyệt</option>
            <option value="da_duyet">Đã phê duyệt</option>
            <option value="bi_tu_choi">Đã từ chối</option>
            <option value="ALL">Tất cả trạng thái</option>
          </select>
        </div>

        <div className="staff-filter-field">
          <label htmlFor="mod-sub-type">Nguồn gửi duyệt</label>
          <select
            id="mod-sub-type"
            className="staff-select"
            value={filterSubmission}
            onChange={(e) => setFilterSubmission(e.target.value as typeof filterSubmission)}
          >
            <option value="ALL">Tất cả nguồn nộp</option>
            <option value="moi">Tin đăng mới</option>
            <option value="nop_lai">Tin sửa & nộp lại</option>
          </select>
        </div>
      </div>

      {/* Bố cục 2 cột: Danh sách bên trái, Chi tiết thẩm định bên phải */}
      <div className="staff-split-grid">
        <div className="staff-card">
          <div className="staff-card-header">
            <div>
              <h3 className="staff-card-title">Danh sách phiên bản tin ({filteredItems.length})</h3>
              <p className="staff-card-subtitle">Chọn một tin đăng trong bảng để đối chiếu chi tiết bên phải</p>
            </div>
          </div>

          <div className="staff-table-wrap">
            <table className="staff-table">
              <thead>
                <tr>
                  <th>Mã tin / Phiên bản</th>
                  <th>Tiêu đề & Phân loại</th>
                  <th>Nguồn nộp</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((item) => (
                  <tr
                    key={item.id}
                    className={selectedItem?.id === item.id ? "selected-row" : ""}
                    onClick={() => setSelectedId(item.id)}
                    style={{ cursor: "pointer" }}
                  >
                    <td>
                      <div style={{ fontWeight: 700, color: "#0f3d32" }}>{item.id}</div>
                      <div style={{ fontSize: "0.74rem", color: "#64748b" }}>{item.versionId}</div>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600, color: "#0f172a" }}>{item.title}</div>
                      <div style={{ fontSize: "0.78rem", color: "#64748b", marginTop: "2px" }}>
                        {item.category} • <strong>{item.price}</strong> • {item.areaSize}
                      </div>
                    </td>
                    <td>
                      <span className={`staff-badge ${item.submissionType === "nop_lai" ? "blue" : "slate"}`}>
                        {item.submissionType === "nop_lai" ? "Tin nộp lại" : "Tin đăng mới"}
                      </span>
                    </td>
                    <td>
                      {item.status === "cho_kiem_duyet" && (
                        <span className="staff-badge amber">Chờ kiểm duyệt</span>
                      )}
                      {item.status === "da_duyet" && (
                        <span className="staff-badge green">Đã phê duyệt</span>
                      )}
                      {item.status === "bi_tu_choi" && (
                        <span className="staff-badge red">Đã từ chối</span>
                      )}
                    </td>
                  </tr>
                ))}
                {filteredItems.length === 0 && (
                  <tr>
                    <td colSpan={4} style={{ textAlign: "center", padding: "2rem", color: "#64748b" }}>
                      Không có tin đăng nào khớp với bộ lọc hiện tại.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Cột phải: Khung kiểm tra chi tiết tin đăng */}
        <div className="staff-card">
          {selectedItem ? (
            <>
              <div className="staff-card-header">
                <div>
                  <span style={{ fontSize: "0.75rem", fontWeight: 700, color: "#0f3d32" }}>
                    PHIẾU THẨM ĐỊNH CHI TIẾT • {selectedItem.id} ({selectedItem.versionId})
                  </span>
                  <h3 className="staff-card-title" style={{ marginTop: "4px" }}>
                    {selectedItem.title}
                  </h3>
                </div>
              </div>

              <div style={{ padding: "1.25rem", display: "flex", flexDirection: "column", gap: "1rem" }}>
                {selectedItem.previousRejectionReason && (
                  <div
                    style={{
                      backgroundColor: "#eff6ff",
                      border: "1px solid #bfdbfe",
                      borderRadius: "8px",
                      padding: "0.75rem 1rem",
                      fontSize: "0.84rem",
                      color: "#1e3a8a",
                    }}
                  >
                    <strong>Lịch sử chỉnh sửa:</strong> Phiên bản trước từng bị từ chối với lý do:{" "}
                    <em>"{selectedItem.previousRejectionReason}"</em>
                  </div>
                )}

                {selectedItem.rejectionReason && (
                  <div
                    style={{
                      backgroundColor: "#fef2f2",
                      border: "1px solid #fecaca",
                      borderRadius: "8px",
                      padding: "0.75rem 1rem",
                      fontSize: "0.84rem",
                      color: "#991b1b",
                    }}
                  >
                    <strong>Lý do từ chối đã ghi nhận:</strong> {selectedItem.rejectionReason}
                  </div>
                )}

                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                    gap: "0.75rem",
                    backgroundColor: "#f8fafc",
                    padding: "0.9rem",
                    borderRadius: "8px",
                    border: "1px solid #e2e8f0",
                    fontSize: "0.85rem",
                  }}
                >
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.76rem" }}>Mức giá niêm yết</div>
                    <div style={{ fontWeight: 700, color: "#0f3d32", fontSize: "1rem" }}>{selectedItem.price}</div>
                  </div>
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.76rem" }}>Diện tích</div>
                    <div style={{ fontWeight: 700 }}>{selectedItem.areaSize}</div>
                  </div>
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.76rem" }}>Loại bất động sản</div>
                    <div style={{ fontWeight: 600 }}>{selectedItem.category}</div>
                  </div>
                  <div>
                    <div style={{ color: "#64748b", fontSize: "0.76rem" }}>Số lượng ảnh đính kèm</div>
                    <div style={{ fontWeight: 600 }}>{selectedItem.imageCount} ảnh hợp lệ (JPEG/WebP)</div>
                  </div>
                  <div style={{ gridColumn: "1 / -1" }}>
                    <div style={{ color: "#64748b", fontSize: "0.76rem" }}>Địa chỉ thực tế</div>
                    <div style={{ fontWeight: 600 }}>{selectedItem.address}</div>
                  </div>
                  <div style={{ gridColumn: "1 / -1" }}>
                    <div style={{ color: "#64748b", fontSize: "0.76rem" }}>Tình trạng pháp lý</div>
                    <div style={{ fontWeight: 600 }}>{selectedItem.legalDoc}</div>
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.8rem", fontWeight: 700, color: "#334155", marginBottom: "4px" }}>
                    Nội dung mô tả chi tiết
                  </div>
                  <p
                    style={{
                      margin: 0,
                      fontSize: "0.86rem",
                      lineHeight: 1.55,
                      color: "#334155",
                      backgroundColor: "#ffffff",
                      border: "1px solid #e2e8f0",
                      borderRadius: "8px",
                      padding: "0.75rem",
                    }}
                  >
                    {selectedItem.description}
                  </p>
                </div>

                <div
                  style={{
                    borderTop: "1px solid #e2e8f0",
                    paddingTop: "0.85rem",
                    fontSize: "0.84rem",
                    display: "flex",
                    justifyContent: "space-between",
                    flexWrap: "wrap",
                    gap: "8px",
                  }}
                >
                  <div>
                    <span style={{ color: "#64748b" }}>Người đăng tin: </span>
                    <strong>{selectedItem.ownerName}</strong> ({selectedItem.ownerEmail} • {selectedItem.ownerPhone})
                  </div>
                  <div style={{ color: "#64748b" }}>Gửi lúc: {selectedItem.submittedAt}</div>
                </div>

                {/* Các nút quyết định thẩm định */}
                <div style={{ display: "flex", gap: "0.75rem", marginTop: "0.25rem" }}>
                  <button
                    type="button"
                    className="staff-btn staff-btn-success"
                    style={{ flex: 1 }}
                    onClick={() => handleApprove(selectedItem)}
                    disabled={selectedItem.status === "da_duyet"}
                  >
                    ✓ Phê duyệt hiển thị
                  </button>
                  <button
                    type="button"
                    className="staff-btn staff-btn-danger"
                    style={{ flex: 1 }}
                    onClick={() => {
                      setRejectReason(selectedItem.rejectionReason || "");
                      setRejectModalOpen(true);
                    }}
                  >
                    ✕ Từ chối & Yêu cầu chỉnh sửa
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div style={{ padding: "2rem", textAlign: "center", color: "#64748b" }}>
              Chọn một tin đăng bên trái để xem hồ sơ thẩm định.
            </div>
          )}
        </div>
      </div>

      {/* Modal nhập lý do từ chối tin */}
      {rejectModalOpen && selectedItem && (
        <div className="staff-modal-backdrop" role="dialog" aria-modal="true">
          <div className="staff-modal">
            <div className="staff-modal-header">
              <h3>Từ chối duyệt tin {selectedItem.id}</h3>
              <button
                type="button"
                className="staff-btn staff-btn-outline staff-btn-sm"
                onClick={() => setRejectModalOpen(false)}
              >
                Đóng
              </button>
            </div>
            <div className="staff-modal-body">
              <div style={{ fontSize: "0.86rem", color: "#475569" }}>
                Tin đăng: <strong>{selectedItem.title}</strong>
              </div>

              <div>
                <label style={{ display: "block", fontSize: "0.8rem", fontWeight: 600, marginBottom: "6px" }}>
                  Chọn nhanh mẫu lý do từ chối:
                </label>
                <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                  {presetRejectionReasons.map((reason) => (
                    <button
                      key={reason}
                      type="button"
                      className="staff-btn staff-btn-outline staff-btn-sm"
                      style={{ justifyContent: "flex-start", textAlign: "left", whiteSpace: "normal" }}
                      onClick={() => setRejectReason(reason)}
                    >
                      + {reason}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label
                  htmlFor="reject-reason-input"
                  style={{ display: "block", fontSize: "0.82rem", fontWeight: 600, marginBottom: "6px" }}
                >
                  Lý do từ chối chi tiết gửi tới người đăng *
                </label>
                <textarea
                  id="reject-reason-input"
                  rows={4}
                  className="staff-textarea"
                  placeholder="Nhập rõ điểm chưa đạt để chủ tin chỉnh sửa và gửi duyệt lại..."
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                />
              </div>
            </div>
            <div className="staff-modal-footer">
              <button
                type="button"
                className="staff-btn staff-btn-outline"
                onClick={() => setRejectModalOpen(false)}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="staff-btn staff-btn-danger"
                onClick={handleConfirmReject}
              >
                Xác nhận từ chối
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
