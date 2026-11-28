import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { listingService } from "@/services/listingService";
import {
  generatePurchaseIntentKey,
  walletService,
} from "@/services/walletService";
import { toast } from "@/stores/useToastStore";
import type { MyListingItem } from "@/types/listing";
import type {
  GiaoDichViItemDto,
  KetQuaMuaGoiResponse,
  ThongTinMuaGoiResponse,
  ViDienTuResponse,
} from "@/types/wallet";
import "./manage-property.css";

function formatVnd(amount?: number | string | null): string {
  const num = Number(amount ?? 0);
  if (Number.isNaN(num)) return "0 VNĐ";
  return `${num.toLocaleString("vi-VN")} VNĐ`;
}

function formatDateTimeVi(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(d);
}

function formatRemainingDurationVi(
  thoiLuongConLaiGiay?: number | null,
  hetHanLuc?: string | null
): string {
  let totalSec =
    typeof thoiLuongConLaiGiay === "number" && thoiLuongConLaiGiay > 0
      ? thoiLuongConLaiGiay
      : 0;
  if (!totalSec && hetHanLuc) {
    const diffMs = new Date(hetHanLuc).getTime() - Date.now();
    if (diffMs > 0) {
      totalSec = Math.floor(diffMs / 1000);
    }
  }
  if (totalSec <= 0) return "Sắp hết hạn";
  const days = Math.floor(totalSec / 86400);
  const hours = Math.floor((totalSec % 86400) / 3600);
  const minutes = Math.floor((totalSec % 3600) / 60);
  if (days > 0) {
    return hours > 0 ? `Còn ${days} ngày ${hours} giờ` : `Còn ${days} ngày`;
  }
  if (hours > 0) {
    return minutes > 0 ? `Còn ${hours} giờ ${minutes} phút` : `Còn ${hours} giờ`;
  }
  return `Còn ${Math.max(1, minutes)} phút`;
}

function formatLoaiGiaoDichVi(loai: string): string {
  switch (loai) {
    case "mua_goi":
      return "Mua gói đẩy tin VIP";
    case "khoi_tao_thu_nghiem":
      return "Khởi tạo số dư thử nghiệm";
    case "nap_tien":
      return "Nạp tiền vào ví";
    case "hoan_tien":
    case "boi_hoan":
      return "Bồi hoàn giao dịch";
    case "dieu_chinh_admin":
    case "dieu_chinh":
      return "Điều chỉnh số dư (QTV)";
    default:
      return loai;
  }
}

function formatTrangThaiGiaoDich(trangThai: string): string {
  switch (trangThai) {
    case "thanh_cong":
      return "Thành công";
    case "cho_thanh_toan":
      return "Chờ thanh toán";
    case "that_bai":
      return "Thất bại";
    case "da_huy":
      return "Đã hủy";
    default:
      return trangThai;
  }
}

export function WalletPage() {
  const { isAuthenticated } = useAuth();

  const [wallet, setWallet] = useState<ViDienTuResponse | null>(null);
  const [approvedListings, setApprovedListings] = useState<MyListingItem[]>([]);
  const [isLoadingWallet, setIsLoadingWallet] = useState<boolean>(true);
  const [selectedListingId, setSelectedListingId] = useState<string>("");
  const [quote, setQuote] = useState<ThongTinMuaGoiResponse | null>(null);
  const [isLoadingQuote, setIsLoadingQuote] = useState<boolean>(false);
  const [selectedPackageCode, setSelectedPackageCode] =
    useState<string>("GOI_7_NGAY");
  const [agreeAccumulate, setAgreeAccumulate] = useState<boolean>(false);
  const [intentKey, setIntentKey] = useState<string>(() =>
    generatePurchaseIntentKey()
  );
  const [isPurchasing, setIsPurchasing] = useState<boolean>(false);
  const [purchaseResult, setPurchaseResult] =
    useState<KetQuaMuaGoiResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadWalletAndListings = useCallback(async () => {
    if (!isAuthenticated) return;
    setIsLoadingWallet(true);
    try {
      const [walletResult, listingsResult] = await Promise.allSettled([
        walletService.getMyWallet(),
        listingService.fetchMyListings(),
      ]);

      if (listingsResult.status === "fulfilled") {
        const approved = listingsResult.value.filter(
          (item) => item.status === "approved"
        );
        setApprovedListings(approved);
        setSelectedListingId((prev) => {
          if (prev && approved.some((x) => x.id === prev)) return prev;
          return approved[0]?.id || "";
        });
      }

      if (walletResult.status === "fulfilled") {
        setWallet(walletResult.value);
      } else {
        const apiErr = walletResult.reason as { message?: string };
        toast.error(
          apiErr?.message || "Không thể tải thông tin ví điện tử.",
          "Lỗi tải ví"
        );
      }
    } finally {
      setIsLoadingWallet(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    void loadWalletAndListings();
  }, [loadWalletAndListings]);

  // Lấy báo giá gói đẩy tin mỗi khi đổi tin đăng được chọn
  useEffect(() => {
    if (!selectedListingId) {
      setQuote(null);
      return;
    }
    let cancelled = false;
    setIsLoadingQuote(true);
    setErrorMessage(null);
    walletService
      .getBoostQuote(selectedListingId)
      .then((res) => {
        if (cancelled) return;
        setQuote(res);
        setAgreeAccumulate(false);
        setIntentKey(generatePurchaseIntentKey());
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const apiErr = err as { message?: string };
        setQuote(null);
        setErrorMessage(
          apiErr.message || "Không thể lấy báo giá gói đẩy tin cho tin này."
        );
      })
      .finally(() => {
        if (!cancelled) setIsLoadingQuote(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedListingId]);

  const packages = useMemo(() => {
    if (quote?.cacGoiHoTro && quote.cacGoiHoTro.length > 0) {
      return quote.cacGoiHoTro;
    }
    return [
      {
        maGoi: "GOI_7_NGAY",
        tenGoi: "Gói đẩy tin 7 ngày",
        giaVnd: 49000,
        thoiLuongNgay: 7,
      },
      {
        maGoi: "GOI_30_NGAY",
        tenGoi: "Gói đẩy tin 30 ngày",
        giaVnd: 179000,
        thoiLuongNgay: 30,
      },
    ];
  }, [quote]);

  const selectedPackage = useMemo(
    () =>
      packages.find((p) => p.maGoi === selectedPackageCode) || packages[0],
    [packages, selectedPackageCode]
  );

  const currentBalance = Number(
    quote?.soDuHienTaiVnd ?? wallet?.soDuVnd ?? 0
  );
  const hasActivePackage = Boolean(
    quote?.canXacNhanCongDon || quote?.goiHienTai?.coGoi
  );
  const insufficientBalance =
    selectedPackage && currentBalance < Number(selectedPackage.giaVnd);

  const handleSelectPackage = (maGoi: string) => {
    setSelectedPackageCode(maGoi);
    setPurchaseResult(null);
    setErrorMessage(null);
    setIntentKey(generatePurchaseIntentKey());
  };

  const handleConfirmPurchase = async () => {
    if (!selectedListingId || !selectedPackage) return;
    if (hasActivePackage && !agreeAccumulate) {
      setErrorMessage(
        "Tin đang có gói còn hiệu lực. Vui lòng tích xác nhận đồng ý cộng dồn thời lượng trước khi mua."
      );
      return;
    }
    setIsPurchasing(true);
    setErrorMessage(null);
    try {
      const res = await walletService.purchaseBoostPackage(
        selectedListingId,
        {
          maGoi: selectedPackage.maGoi,
          maYDinh: intentKey,
          dongYCongDon: agreeAccumulate,
        },
        intentKey
      );
      setPurchaseResult(res);
      toast.success(
        res.thongDiep || "Đã kích hoạt gói đẩy tin thành công!",
        "Mua gói thành công"
      );
      // Tải lại số dư ví, sổ giao dịch và báo giá mới của tin
      const [updatedWallet, updatedQuote] = await Promise.all([
        walletService.getMyWallet(),
        walletService.getBoostQuote(selectedListingId),
      ]);
      setWallet(updatedWallet);
      setQuote(updatedQuote);
      setAgreeAccumulate(false);
      setIntentKey(generatePurchaseIntentKey());
    } catch (err: unknown) {
      const apiErr = err as { code?: string; message?: string };
      setErrorMessage(
        apiErr.message || "Không thể thực hiện giao dịch mua gói đẩy tin."
      );
      toast.error(
        apiErr.message || "Giao dịch mua gói không thành công.",
        apiErr.code || "Lỗi giao dịch"
      );
    } finally {
      setIsPurchasing(false);
    }
  };

  const transactions: GiaoDichViItemDto[] = wallet?.giaoDichGanDay ?? [];

  return (
    <section className="mp-manage-content">
      <div className="wp-page-header">
        <div>
          <p className="wp-eyebrow">Tài chính cá nhân</p>
          <h1>Ví điện tử &amp; Gói đẩy tin</h1>
          <p>
            Quản lý số dư ví, chọn mua hoặc cộng dồn gói đẩy tin tài trợ và đối
            chiếu lịch sử sổ giao dịch.
          </p>
        </div>
        <Link to="/quan-ly-tin" className="mp-new-listing-btn">
          Quản lý tin của tôi
        </Link>
      </div>

      {/* Khu vực 1: Thẻ số dư ví & Mua gói đẩy tin nhanh */}
      <div className="wp-top-grid">
        <div className="wp-balance-card">
          <div className="wp-balance-header">
            <span className="wp-balance-badge">Số dư khả dụng</span>
            <button
              type="button"
              className="wp-refresh-btn"
              onClick={() => void loadWalletAndListings()}
              disabled={isLoadingWallet}
            >
              {isLoadingWallet ? "Đang tải..." : "Làm mới"}
            </button>
          </div>
          <div className="wp-balance-amount">
            {isLoadingWallet ? "..." : formatVnd(wallet?.soDuVnd)}
          </div>
          <p className="wp-balance-sub">
            Cập nhật lần cuối: {formatDateTimeVi(wallet?.capNhatLuc)}
          </p>
        </div>

        {/* Form chọn tin, chọn gói, xác nhận và hiển thị kết quả mua */}
        <div className="wp-purchase-card">
          <h2>Đăng ký / Cộng dồn Gói Đẩy Tin</h2>

          {approvedListings.length === 0 ? (
            <div className="wp-empty-listings">
              <p>
                Bạn hiện chưa có tin đăng nào ở trạng thái{" "}
                <strong>Đã duyệt</strong> để kích hoạt gói đẩy tin.
              </p>
              <Link to="/quan-ly-tin" className="mp-secondary-btn">
                Đến trang Quản lý tin đăng
              </Link>
            </div>
          ) : (
            <>
              <div className="wp-field">
                <label htmlFor="wp-select-listing">
                  1. Chọn tin đăng đã duyệt
                </label>
                <select
                  id="wp-select-listing"
                  value={selectedListingId}
                  onChange={(e) => {
                    setSelectedListingId(e.target.value);
                    setPurchaseResult(null);
                  }}
                >
                  {approvedListings.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.title} — {item.price}
                    </option>
                  ))}
                </select>
              </div>

              {isLoadingQuote ? (
                <p className="wp-loading-inline">
                  Đang tải báo giá gói và kiểm tra trạng thái gói hiện tại...
                </p>
              ) : (
                <>
                  {hasActivePackage && quote?.goiHienTai && (
                    <div className="wp-active-pkg-banner">
                      <strong>★ Đang đẩy tin • </strong>
                      <strong>
                        {formatRemainingDurationVi(
                          quote.goiHienTai.thoiLuongConLaiGiay,
                          quote.goiHienTai.hetHanLuc
                        )}
                      </strong>{" "}
                      (đến{" "}
                      <span>
                        {formatDateTimeVi(quote.goiHienTai.hetHanLuc)}
                      </span>
                      ). Nếu mua thêm, thời lượng gói mới sẽ được{" "}
                      <strong>cộng dồn trực tiếp</strong> vào hạn hiện tại.
                    </div>
                  )}

                  <div className="wp-field">
                    <label>2. Chọn gói đẩy tin</label>
                    <div className="wp-pkg-options">
                      {packages.map((pkg) => {
                        const active = pkg.maGoi === selectedPackage.maGoi;
                        return (
                          <button
                            key={pkg.maGoi}
                            type="button"
                            className={`wp-pkg-option ${active ? "selected" : ""}`}
                            onClick={() => handleSelectPackage(pkg.maGoi)}
                          >
                            <div className="wp-pkg-title">{pkg.tenGoi}</div>
                            <div className="wp-pkg-price">
                              {formatVnd(pkg.giaVnd)}
                            </div>
                            <div className="wp-pkg-days">
                              Thời lượng: +{pkg.thoiLuongNgay} ngày tài trợ VIP
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div className="wp-summary-box">
                    <div className="wp-summary-row">
                      <span>Số dư ví hiện tại:</span>
                      <strong>{formatVnd(currentBalance)}</strong>
                    </div>
                    <div className="wp-summary-row">
                      <span>Chi phí gói ({selectedPackage?.tenGoi}):</span>
                      <strong className="wp-price-minus">
                        -{formatVnd(selectedPackage?.giaVnd)}
                      </strong>
                    </div>
                    <div className="wp-summary-row total">
                      <span>Số dư dự kiến sau thanh toán:</span>
                      <strong
                        style={{
                          color: insufficientBalance ? "#dc2626" : "#0d9c8d",
                        }}
                      >
                        {insufficientBalance
                          ? "Số dư không đủ"
                          : formatVnd(
                              currentBalance - Number(selectedPackage?.giaVnd ?? 0)
                            )}
                      </strong>
                    </div>
                  </div>

                  {hasActivePackage && (
                    <label className="wp-accumulate-check">
                      <input
                        type="checkbox"
                        checked={agreeAccumulate}
                        onChange={(e) => setAgreeAccumulate(e.target.checked)}
                      />
                      <span>
                        Tôi xác nhận đồng ý <strong>cộng dồn thêm {selectedPackage?.thoiLuongNgay} ngày</strong> vào gói đang còn hiệu lực của tin đăng này.
                      </span>
                    </label>
                  )}

                  {errorMessage && (
                    <div className="wp-error-box" role="alert">
                      {errorMessage}
                    </div>
                  )}

                  <div className="wp-purchase-actions">
                    <button
                      type="button"
                      className="mp-primary-btn"
                      disabled={
                        isPurchasing ||
                        insufficientBalance ||
                        (hasActivePackage && !agreeAccumulate)
                      }
                      onClick={() => void handleConfirmPurchase()}
                    >
                      {isPurchasing
                        ? "Đang xử lý giao dịch..."
                        : hasActivePackage
                        ? `Xác nhận mua & Cộng dồn (${formatVnd(selectedPackage?.giaVnd)})`
                        : `Xác nhận mua gói (${formatVnd(selectedPackage?.giaVnd)})`}
                    </button>
                  </div>

                  {purchaseResult && (
                    <div className="wp-result-box" role="status">
                      <h3>✓ Kết quả giao dịch mua gói</h3>
                      <p>{purchaseResult.thongDiep}</p>
                      <ul>
                        <li>
                          <span>Mã giao dịch ví:</span>
                          <code>{purchaseResult.maGiaoDich}</code>
                        </li>
                        <li>
                          <span>Trạng thái gói:</span>
                          <strong>
                            {purchaseResult.goi?.trangThai === "dang_hoat_dong"
                              ? "Đang hoạt động (Khu vực Được tài trợ)"
                              : purchaseResult.goi?.trangThai}
                          </strong>
                        </li>
                        <li>
                          <span>Hiệu lực đến:</span>
                          <strong>
                            {formatDateTimeVi(purchaseResult.goi?.hetHanLuc)}
                          </strong>
                        </li>
                        <li>
                          <span>Số dư ví còn lại:</span>
                          <strong>
                            {formatVnd(purchaseResult.soDuConLaiVnd)}
                          </strong>
                        </li>
                      </ul>
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </div>
      </div>

      {/* Khu vực 2: Bảng Sổ Giao Dịch Ví (Transaction Ledger) */}
      <div className="wp-ledger-card">
        <div className="wp-ledger-header">
          <div>
            <h2>Sổ Giao Dịch Ví (Transaction Ledger)</h2>
            <p>
              Mọi biến động cộng/trừ đều được ghi sổ nguyên tử cùng số dư ví
            </p>
          </div>
          <span className="wp-ledger-count">
            {transactions.length} bút toán
          </span>
        </div>

        {transactions.length === 0 ? (
          <div className="mp-empty-state">
            Chưa có giao dịch nào được ghi nhận trong sổ giao dịch ví của bạn.
          </div>
        ) : (
          <div className="wp-ledger-table-wrap">
            <table className="wp-ledger-table">
              <thead>
                <tr>
                  <th>Thời điểm</th>
                  <th>Loại giao dịch</th>
                  <th>Biến động</th>
                  <th>Số dư sau</th>
                  <th>Trạng thái</th>
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((gd) => {
                  const isPlus = gd.chieuBienDong === "cong";
                  return (
                    <tr key={gd.id}>
                      <td>{formatDateTimeVi(gd.thoiDiemGhiNhan)}</td>
                      <td>
                        <strong>{formatLoaiGiaoDichVi(gd.loai)}</strong>
                      </td>
                      <td>
                        <span
                          className={`wp-amount-pill ${isPlus ? "plus" : "minus"}`}
                        >
                          {isPlus ? "+" : "-"}
                          {formatVnd(gd.soTien)}
                        </span>
                      </td>
                      <td>
                        <strong>{formatVnd(gd.soDuSau)}</strong>
                      </td>
                      <td>
                        <span className="wp-status-tag">
                          {formatTrangThaiGiaoDich(gd.trangThai)}
                        </span>
                      </td>
                      <td className="wp-note-cell">{gd.ghiChu || "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
