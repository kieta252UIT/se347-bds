import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { moderationService } from "@/services/moderationService";
import type { TinQuanLyDto } from "@/types/moderation";
import {
  formatDienTichM2,
  formatGiaGocVaDonVi,
  formatLoaiBdsLabel,
} from "@/utils/priceFormatter";

interface PersistedDecidedItem {
  id: string;
  versionId?: string;
  versionNumber?: number;
  title: string;
  category: string;
  submissionType: "moi" | "nop_lai";
  status: "da_duyet" | "bi_tu_choi";
  priceDisplay: string;
  areaSize: string;
  province: string;
  ward: string;
  submittedAt: string;
  reviewedBy?: string;
  reviewedAt?: string;
  rejectionReason?: string;
}

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

function loadDecidedItems(): PersistedDecidedItem[] {
  return [];
}

function formatInstantVi(isoStr?: string | null): string {
  if (!isoStr) return "Đang cập nhật";
  const dt = new Date(isoStr);
  if (Number.isNaN(dt.getTime())) return isoStr;
  const dd = String(dt.getDate()).padStart(2, "0");
  const mm = String(dt.getMonth() + 1).padStart(2, "0");
  const yyyy = dt.getFullYear();
  const hh = String(dt.getHours()).padStart(2, "0");
  const min = String(dt.getMinutes()).padStart(2, "0");
  return `${dd}/${mm}/${yyyy} ${hh}:${min}`;
}

export function KdvDashboardPage() {
  const { user } = useAuth();
  const [pendingListings, setPendingListings] = useState<TinQuanLyDto[]>([]);
  const [totalPending, setTotalPending] = useState<number>(0);
  const [decidedItems, setDecidedItems] = useState<PersistedDecidedItem[]>(() =>
    loadDecidedItems()
  );
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchDashboardData = useCallback(async () => {
    setIsLoading(true);
    try {
      const pageData = await moderationService.getPendingListings(0, 50);
      const list = Array.isArray(pageData?.noiDung) ? pageData.noiDung : [];
      setPendingListings(list);
      setTotalPending(pageData?.tongSo ?? list.length);

      const pendingIds = new Set(list.map((x) => x.id));
      const storedDecided = loadDecidedItems().filter(
        (d) => !pendingIds.has(d.id)
      );
      setDecidedItems(storedDecided);
    } catch {
      setDecidedItems(loadDecidedItems());
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchDashboardData();
  }, [fetchDashboardData]);

  const stats = useMemo(() => {
    const newSubmissions = pendingListings.filter(
      (item) => (item.noiDung?.soPhienBan ?? 1) <= 1
    ).length;
    const resubmissions = pendingListings.filter(
      (item) => (item.noiDung?.soPhienBan ?? 1) > 1
    ).length;
    const pendingReports = urgentReportsPreview.filter(
      (r) => r.status === "Chờ xử lý"
    ).length;

    return {
      pendingTotal: totalPending,
      newSubmissions,
      resubmissions,
      pendingReports,
      totalReports: urgentReportsPreview.length,
    };
  }, [pendingListings, totalPending]);

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Bàn làm việc Kiểm duyệt viên</h1>
          <p>
            Xin chào{" "}
            <strong>
              {user?.hoTen || user?.email || "Kiểm duyệt viên"}
            </strong>
            . Theo dõi số lượng tin chờ duyệt và xử lý báo cáo vi phạm từ người dùng.
          </p>
        </div>
        <div className="staff-header-actions">
          <button
            type="button"
            className="staff-btn staff-btn-outline"
            onClick={() => void fetchDashboardData()}
            disabled={isLoading}
          >
            {isLoading ? "Đang đồng bộ..." : "Làm mới số liệu"}
          </button>
          <Link
            to="/kdv/tham-dinh"
            className="staff-btn staff-btn-primary"
            style={{ textDecoration: "none" }}
          >
            Vào hàng chờ thẩm định ({stats.pendingTotal}) →
          </Link>
        </div>
      </div>

      {/* 2 KPI Cards: Tin đang chờ kiểm duyệt & Báo cáo đang chờ xử lý */}
      <div
        className="staff-kpi-grid"
        style={{ gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))" }}
      >
        <Link
          to="/kdv/tham-dinh"
          className="staff-kpi-card"
          style={{ textDecoration: "none", color: "inherit" }}
        >
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Tin đang chờ kiểm duyệt</span>
            <span className="staff-badge amber">Chờ kiểm duyệt</span>
          </div>
          <div className="staff-kpi-value">{stats.pendingTotal}</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-warn">
              {stats.newSubmissions} tin đăng mới
            </span>
            <span>• {stats.resubmissions} tin sửa nộp lại</span>
          </div>
        </Link>

        <Link
          to="/kdv/bao-cao"
          className="staff-kpi-card"
          style={{ textDecoration: "none", color: "inherit" }}
        >
          <div className="staff-kpi-top">
            <span className="staff-kpi-label">Báo cáo đang chờ xử lý</span>
            <span className="staff-badge red">Cần kiểm tra</span>
          </div>
          <div className="staff-kpi-value">{stats.pendingReports}</div>
          <div className="staff-kpi-sub">
            <span className="staff-kpi-trend-danger">
              Tổng {stats.totalReports} báo cáo trong danh sách theo dõi
            </span>
          </div>
        </Link>
      </div>

      {/* Bảng 1: Hàng chờ thẩm định mới nhất từ API */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">
              Hàng chờ thẩm định tin đăng mới nhất ({stats.pendingTotal})
            </h3>
            <p className="staff-card-subtitle">
              Danh sách các tin đăng mới và tin chỉnh sửa đang chờ Kiểm duyệt
              viên xét duyệt từ hệ thống
            </p>
          </div>
          <Link
            to="/kdv/tham-dinh?status=ALL"
            className="staff-btn staff-btn-outline staff-btn-sm"
            style={{ textDecoration: "none" }}
          >
            Xem toàn bộ danh sách tin →
          </Link>
        </div>

        <div className="staff-table-wrap">
          {isLoading ? (
            <div style={{ padding: "28px", textAlign: "center", color: "#64748b" }}>
              Đang tải danh sách tin chờ kiểm duyệt...
            </div>
          ) : pendingListings.length > 0 ? (
            <table className="staff-table">
              <thead>
                <tr>
                  <th>Mã tin</th>
                  <th>Tiêu đề & Phân loại</th>
                  <th>Khu vực</th>
                  <th>Nguồn nộp</th>
                  <th>Mức giá & Diện tích</th>
                  <th>Ngày gửi</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {pendingListings.slice(0, 8).map((item) => {
                  const nd = item.noiDung;
                  const isResubmission = (nd?.soPhienBan ?? 1) > 1;
                  return (
                    <tr key={item.id}>
                      <td style={{ fontWeight: 700, color: "#0f3d32" }}>
                        {item.id.slice(0, 8).toUpperCase()}
                      </td>
                      <td>
                        <div style={{ fontWeight: 600, color: "#0f172a" }}>
                          {nd?.tieuDe}
                        </div>
                        <div style={{ fontSize: "0.78rem", color: "#64748b" }}>
                          {formatLoaiBdsLabel(nd?.loaiBds)}
                        </div>
                      </td>
                      <td>{nd?.khuVuc}</td>
                      <td>
                        <span
                          className={`staff-badge ${
                            isResubmission ? "blue" : "amber"
                          }`}
                        >
                          {isResubmission
                            ? `Tin nộp lại (Lần ${nd.soPhienBan})`
                            : "Tin đăng mới"}
                        </span>
                      </td>
                      <td>
                        <div style={{ fontWeight: 700, color: "#08775f" }}>
                          {formatGiaGocVaDonVi(nd?.giaGoc, nd?.donViGia)}
                        </div>
                        <div style={{ fontSize: "0.76rem", color: "#64748b" }}>
                          {formatDienTichM2(nd?.dienTichM2)}
                        </div>
                      </td>
                      <td style={{ color: "#64748b" }}>
                        {formatInstantVi(nd?.taoLuc || item.ngayDang)}
                      </td>
                      <td>
                        <Link
                          to={`/kdv/tham-dinh?openId=${encodeURIComponent(
                            item.id
                          )}`}
                          className="staff-btn staff-btn-primary staff-btn-sm"
                          style={{ textDecoration: "none" }}
                        >
                          Xem và thẩm định
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <div style={{ padding: "32px 20px", textAlign: "center" }}>
              <p style={{ margin: "0 0 12px", color: "#475569", fontWeight: 600 }}>
                Hiện không có tin đăng nào đang ở trạng thái chờ kiểm duyệt.
              </p>
              {decidedItems.length > 0 && (
                <Link
                  to="/kdv/tham-dinh?status=ALL"
                  className="staff-btn staff-btn-outline staff-btn-sm"
                  style={{ textDecoration: "none" }}
                >
                  Xem lại {decidedItems.length} tin đã thẩm định (Đã duyệt / Đã
                  từ chối)
                </Link>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Bảng 2: Báo cáo vi phạm cần ưu tiên */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">
              Báo cáo vi phạm cần xử lý & Đề xuất khóa
            </h3>
            <p className="staff-card-subtitle">
              Kiểm tra bằng chứng phản ánh, gỡ tin vi phạm và gửi đề xuất khóa
              tài khoản lên Quản trị viên nếu tái phạm
            </p>
          </div>
          <Link
            to="/kdv/bao-cao"
            className="staff-btn staff-btn-outline staff-btn-sm"
            style={{ textDecoration: "none" }}
          >
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
                  <td style={{ fontWeight: 600, color: "#0f3d32" }}>
                    {r.listingId}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{r.reason}</div>
                    <div style={{ fontSize: "0.76rem", color: "#64748b" }}>
                      Người báo cáo: {r.reporter}
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{r.accusedOwner}</div>
                    <div
                      style={{
                        fontSize: "0.75rem",
                        color: "#dc2626",
                        fontWeight: 600,
                      }}
                    >
                      Lịch sử vi phạm: {r.violationCount} lần
                    </div>
                  </td>
                  <td>
                    <span
                      className={`staff-badge ${
                        r.status.includes("đề xuất") ? "purple" : "red"
                      }`}
                    >
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
