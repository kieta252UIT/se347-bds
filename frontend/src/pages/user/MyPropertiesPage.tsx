import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import locations from "@/data/locations.json";
import { toast } from "@/stores/useToastStore";
import { listingService } from "@/services/listingService";
import {
  generatePurchaseIntentKey,
  walletService,
} from "@/services/walletService";
import type { ListingStatus, MyListingItem } from "@/types/listing";
import type {
  KetQuaMuaGoiResponse,
  ThongTinMuaGoiResponse,
} from "@/types/wallet";
import "./manage-property.css";

function formatListingAddress(listing: MyListingItem): string {
  if (listing.address) return listing.address;
  if (!listing.wardId) return "Địa chỉ đang cập nhật";
  const ward = locations.wards.find((w) => w.id === listing.wardId);
  if (!ward) return "Địa chỉ đang cập nhật";
  const province = locations.provinces.find((p) => p.id === ward.province_id);
  return province ? `${ward.name}, ${province.name}` : ward.name;
}

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

type ListingFilter = "all" | "pending" | "approved" | "rejected" | "violation";

const filters: { id: ListingFilter; label: string }[] = [
  { id: "all", label: "Tất cả" },
  { id: "pending", label: "Chờ duyệt" },
  { id: "approved", label: "Đã duyệt" },
  { id: "rejected", label: "Bị từ chối" },
  { id: "violation", label: "Bị gỡ vi phạm" },
];

const statusLabels: Record<ListingStatus, string> = {
  approved: "Đã duyệt",
  pending: "Chờ duyệt",
  rejected: "Bị từ chối",
  violation: "Bị gỡ vi phạm",
  hidden: "Đã ẩn",
  sold: "Đã bán",
  deleted: "Đã xóa",
};

function HomeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m3 11 9-7 9 7" />
      <path d="M5 10v10h14V10M9 20v-6h6v6" />
    </svg>
  );
}

function PinIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}

function AddIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function RocketBoostIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09Z" />
      <path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2Z" />
      <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0" />
      <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5" />
    </svg>
  );
}

function EditIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
      <path d="m15 5 4 4" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 6h18" />
      <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6" />
      <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2" />
      <line x1="10" x2="10" y1="11" y2="17" />
      <line x1="14" x2="14" y1="11" y2="17" />
    </svg>
  );
}

function ListingCard({
  listing,
  boostInfo,
  onView,
  onEdit,
  onRequestDelete,
  onRequestBoost,
}: {
  listing: MyListingItem;
  boostInfo?: ThongTinMuaGoiResponse["goiHienTai"] | null;
  onView: (listing: MyListingItem) => void;
  onEdit: (listing: MyListingItem) => void;
  onRequestDelete: (listing: MyListingItem) => void;
  onRequestBoost: (listing: MyListingItem) => void;
}) {
  const canEdit = listing.status === "approved" || listing.status === "rejected";
  const canDelete = listing.status !== "deleted" && listing.status !== "violation";
  const canBoost = listing.status === "approved";

  return (
    <article
      className="mp-listing-card"
      onClick={() => onView(listing)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onView(listing);
        }
      }}
      role="link"
      tabIndex={0}
      style={{ cursor: "pointer" }}
      title="Nhấn để xem thông tin chi tiết bất động sản"
    >
      <div
        className="mp-listing-image"
        style={{ backgroundImage: `url("${listing.image}")` }}
      >
        <span className="mp-photo-count">{listing.photos} ảnh</span>
      </div>

      <div className="mp-listing-details">
        <h2>{listing.title}</h2>
        <div className="mp-listing-meta">
          <strong>{listing.price}</strong>
          <span>Diện tích: {listing.area} m²</span>
        </div>
        <p className="mp-location">
          <PinIcon />
          {formatListingAddress(listing)}
        </p>
        <p className="mp-date">Đăng ngày: {listing.date}</p>
        {boostInfo?.coGoi && (
          <div className="mp-boost-status-pill">
            <span>★ Đang đẩy tin</span>
            <span>•</span>
            <strong>
              {formatRemainingDurationVi(
                boostInfo.thoiLuongConLaiGiay,
                boostInfo.hetHanLuc
              )}
            </strong>
            {boostInfo.hetHanLuc && (
              <span>(đến {formatDateTimeVi(boostInfo.hetHanLuc)})</span>
            )}
          </div>
        )}
        {listing.rejectionReason && listing.status === "rejected" && (
          <p className="mp-rejection-reason">
            <strong>!</strong> Lý do từ chối: {listing.rejectionReason}
          </p>
        )}
      </div>

      <span className={`mp-status ${listing.status}`}>
        {statusLabels[listing.status] || listing.status}
      </span>

      <div
        className="mp-listing-actions"
        onClick={(e) => e.stopPropagation()}
      >
        {canBoost && (
          <button
            type="button"
            className="mp-action-btn boost-btn"
            onClick={(e) => {
              e.stopPropagation();
              onRequestBoost(listing);
            }}
            title="Mua / Cộng dồn gói đẩy tin tài trợ VIP"
            aria-label="Mua gói đẩy tin tài trợ VIP"
          >
            <RocketBoostIcon />
          </button>
        )}
        {/* Chỉ cho phép sửa tin khi đã duyệt hoặc bị từ chối (theo BR-02 / UC-07) */}
        {canEdit && (
          <button
            type="button"
            className="mp-action-btn edit-btn"
            onClick={(e) => {
              e.stopPropagation();
              onEdit(listing);
            }}
            title={listing.status === "rejected" ? "Sửa & Nộp lại" : "Chỉnh sửa tin"}
            aria-label={listing.status === "rejected" ? "Sửa & Nộp lại" : "Chỉnh sửa tin"}
          >
            <EditIcon />
          </button>
        )}
        {canDelete && (
          <button
            type="button"
            className="mp-action-btn delete-btn"
            onClick={(e) => {
              e.stopPropagation();
              onRequestDelete(listing);
            }}
            title="Xóa tin đăng"
            aria-label="Xóa tin đăng"
          >
            <TrashIcon />
          </button>
        )}
      </div>
    </article>
  );
}

export function MyPropertiesPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();
  const [previewSignedIn, setPreviewSignedIn] = useState(false);
  const [listings, setListings] = useState<MyListingItem[]>([]);
  const [isFetching, setIsFetching] = useState(true);

  // State cho Modal Chọn gói, Xác nhận & Hiển thị kết quả mua
  const [boostInfoMap, setBoostInfoMap] = useState<
    Record<string, ThongTinMuaGoiResponse["goiHienTai"]>
  >({});
  const [boostTarget, setBoostTarget] = useState<MyListingItem | null>(null);
  const [boostQuote, setBoostQuote] = useState<ThongTinMuaGoiResponse | null>(
    null
  );
  const [isLoadingQuote, setIsLoadingQuote] = useState(false);
  const [selectedPkgCode, setSelectedPkgCode] = useState("GOI_7_NGAY");
  const [agreeAccumulate, setAgreeAccumulate] = useState(false);
  const [purchaseIntentKey, setPurchaseIntentKey] = useState("");
  const [isPurchasing, setIsPurchasing] = useState(false);
  const [boostResult, setBoostResult] = useState<KetQuaMuaGoiResponse | null>(
    null
  );
  const [boostError, setBoostError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) return;
    let isCancelled = false;
    setIsFetching(true);
    listingService
      .fetchMyListings()
      .then(async (items) => {
        if (isCancelled) return;
        setListings(items);
        const approvedItems = items.filter((it) => it.status === "approved");
        if (approvedItems.length > 0) {
          const quoteResults = await Promise.allSettled(
            approvedItems.map(async (it) => ({
              id: String(it.id),
              quote: await walletService.getBoostQuote(String(it.id)),
            }))
          );
          if (isCancelled) return;
          const nextMap: Record<string, ThongTinMuaGoiResponse["goiHienTai"]> =
            {};
          for (const res of quoteResults) {
            if (res.status === "fulfilled" && res.value.quote?.goiHienTai) {
              nextMap[res.value.id] = res.value.quote.goiHienTai;
            }
          }
          setBoostInfoMap(nextMap);
        }
      })
      .finally(() => {
        if (!isCancelled) {
          setIsFetching(false);
        }
      });
    return () => {
      isCancelled = true;
    };
  }, [isAuthenticated]);

  const [activeFilter, setActiveFilter] = useState<ListingFilter>("all");
  const [page, setPage] = useState(1);
  const [deleteTarget, setDeleteTarget] = useState<MyListingItem | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const filteredListings = useMemo(
    () =>
      listings.filter((listing) => {
        if (activeFilter === "all") return true;
        return listing.status === activeFilter;
      }),
    [activeFilter, listings]
  );

  const pageSize = 4;
  const pageCount = Math.max(1, Math.ceil(filteredListings.length / pageSize));
  const visibleListings = filteredListings.slice(
    (page - 1) * pageSize,
    page * pageSize
  );

  const selectFilter = (filter: ListingFilter) => {
    setActiveFilter(filter);
    setPage(1);
  };

  const openBoostModal = async (item: MyListingItem) => {
    setBoostTarget(item);
    setBoostQuote(null);
    setBoostResult(null);
    setBoostError(null);
    setSelectedPkgCode("GOI_7_NGAY");
    setAgreeAccumulate(false);
    setPurchaseIntentKey(generatePurchaseIntentKey());
    setIsLoadingQuote(true);
    try {
      const quoteData = await walletService.getBoostQuote(String(item.id));
      setBoostQuote(quoteData);
      if (quoteData?.goiHienTai) {
        setBoostInfoMap((prev) => ({
          ...prev,
          [String(item.id)]: quoteData.goiHienTai,
        }));
      }
    } catch (err: unknown) {
      const apiErr = err as { message?: string };
      setBoostError(
        apiErr.message ||
          "Không thể tải báo giá gói đẩy tin. Vui lòng kiểm tra trạng thái tin đăng."
      );
    } finally {
      setIsLoadingQuote(false);
    }
  };

  const closeBoostModal = () => {
    if (isPurchasing) return;
    setBoostTarget(null);
    setBoostQuote(null);
    setBoostResult(null);
    setBoostError(null);
  };

  const availablePackages = useMemo(() => {
    if (boostQuote?.cacGoiHoTro && boostQuote.cacGoiHoTro.length > 0) {
      return boostQuote.cacGoiHoTro;
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
  }, [boostQuote]);

  const activePkg = useMemo(
    () =>
      availablePackages.find((p) => p.maGoi === selectedPkgCode) ||
      availablePackages[0],
    [availablePackages, selectedPkgCode]
  );

  const currentWalletBalance = Number(boostQuote?.soDuHienTaiVnd ?? 0);
  const needsAccumulateConfirm = Boolean(
    boostQuote?.canXacNhanCongDon || boostQuote?.goiHienTai?.coGoi
  );
  const isBalanceInsufficient =
    activePkg && currentWalletBalance < Number(activePkg.giaVnd);

  const handleConfirmBoostPurchase = async () => {
    if (!boostTarget || !activePkg) return;
    if (needsAccumulateConfirm && !agreeAccumulate) {
      setBoostError(
        "Tin đang có gói còn hiệu lực. Vui lòng tích xác nhận đồng ý cộng dồn thời lượng."
      );
      return;
    }

    setIsPurchasing(true);
    setBoostError(null);
    try {
      const result = await walletService.purchaseBoostPackage(
        String(boostTarget.id),
        {
          maGoi: activePkg.maGoi,
          maYDinh: purchaseIntentKey,
          dongYCongDon: agreeAccumulate,
        },
        purchaseIntentKey
      );
      setBoostResult(result);
      toast.success(
        result.thongDiep || "Đã mua và kích hoạt gói đẩy tin thành công!",
        "Kích hoạt gói VIP"
      );
      const refreshedQuote = await walletService.getBoostQuote(
        String(boostTarget.id)
      );
      setBoostQuote(refreshedQuote);
      if (refreshedQuote?.goiHienTai) {
        setBoostInfoMap((prev) => ({
          ...prev,
          [String(boostTarget.id)]: refreshedQuote.goiHienTai,
        }));
      }
      setPurchaseIntentKey(generatePurchaseIntentKey());
    } catch (err: unknown) {
      const apiErr = err as { code?: string; message?: string };
      setBoostError(
        apiErr.message || "Không thể hoàn tất giao dịch mua gói đẩy tin."
      );
      toast.error(
        apiErr.message || "Mua gói đẩy tin thất bại.",
        apiErr.code || "Lỗi mua gói"
      );
    } finally {
      setIsPurchasing(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await listingService.deleteListing(deleteTarget.id, {
        ifMatch: deleteTarget.phienBanCapNhat ?? 0,
      });
      setListings((current) =>
        current.filter((item) => String(item.id) !== String(deleteTarget.id))
      );
      listingService.removeUserListing(deleteTarget.id);
      toast.success(
        `Đã xóa tin đăng “${deleteTarget.title}”.`,
        "Xóa thành công"
      );
      setDeleteTarget(null);
    } catch (err: unknown) {
      const apiErr = err as { code?: string; message?: string; status?: number };
      if (apiErr.code === "PHIEN_BAN_CU" || apiErr.status === 412) {
        toast.error(
          "Tin đăng đã được cập nhật bởi một phiên làm việc khác. Vui lòng tải lại trang.",
          "Xung đột phiên bản (412)"
        );
        return;
      }
      if (apiErr.code === "TRANG_THAI_KHONG_HOP_LE" || apiErr.status === 409) {
        toast.error(
          apiErr.message || "Trạng thái tin đăng hiện tại không cho phép xóa.",
          "Không thể xóa (409)"
        );
        return;
      }
      if (apiErr.code === "THIEU_IF_MATCH" || apiErr.status === 428) {
        toast.error(
          "Thiếu thông tin phiên bản If-Match. Vui lòng tải lại trang.",
          "Lỗi tiền điều kiện (428)"
        );
        return;
      }

      setListings((current) =>
        current.filter((item) => String(item.id) !== String(deleteTarget.id))
      );
      listingService.removeUserListing(deleteTarget.id);
      toast.info(
        `Đã xóa tin đăng “${deleteTarget.title}”.`,
        "Đã xóa tin đăng"
      );
      setDeleteTarget(null);
    } finally {
      setIsDeleting(false);
    }
  };

  if (isLoading) {
    return (
      <section className="mp-message-stage" aria-live="polite">
        <div className="mp-message-card">
          <span className="mp-spinner" />
          <p>Đang kiểm tra trạng thái đăng nhập...</p>
        </div>
      </section>
    );
  }

  if (!isAuthenticated && !previewSignedIn) {
    return (
      <section className="mp-guest-stage">
        <div className="mp-guest-card">
          <span className="mp-icon-badge">
            <HomeIcon />
          </span>
          <h1>Quản lý tin đăng của bạn</h1>
          <p>
            Đăng nhập để theo dõi trạng thái kiểm duyệt, chỉnh sửa và tạo tin đăng mới.
          </p>
          <div className="mp-guest-actions">
            <Link className="mp-primary-btn" to="/dang-nhap">
              Đăng nhập để tiếp tục
            </Link>
            <Link className="mp-secondary-btn" to="/tim-kiem">
              Tiếp tục tìm kiếm
            </Link>
            <button
              type="button"
              className="mp-secondary-btn"
              onClick={() => setPreviewSignedIn(true)}
            >
              Xem trước danh sách mẫu
            </button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <>
      <section className="mp-manage-content">
        <div className="mp-title-row">
          <div>
            <h1>Quản lý tin đăng</h1>
            <p>
              Quản lý, theo dõi trạng thái kiểm duyệt và kích hoạt gói đẩy tin
              tài trợ cho bất động sản của bạn
            </p>
          </div>
          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
            <Link to="/vi-tien" className="mp-secondary-btn">
              Ví & Sổ giao dịch
            </Link>
            <Link to="/dang-tin" className="mp-new-listing-btn">
              <AddIcon />
              Đăng tin mới
            </Link>
          </div>
        </div>

        <div className="mp-tabs" role="tablist" aria-label="Lọc tin đăng">
          {filters.map((filter) => {
            const count =
              filter.id === "all"
                ? listings.length
                : listings.filter((listing) => listing.status === filter.id).length;
            return (
              <button
                key={filter.id}
                type="button"
                role="tab"
                aria-selected={activeFilter === filter.id}
                className={activeFilter === filter.id ? "active" : ""}
                onClick={() => selectFilter(filter.id)}
              >
                {filter.label} ({count})
              </button>
            );
          })}
        </div>

        <div className="mp-listings" role="tabpanel">
          {visibleListings.length > 0 ? (
            visibleListings.map((listing) => (
              <ListingCard
                key={listing.id}
                listing={listing}
                boostInfo={boostInfoMap[String(listing.id)]}
                onView={(item) => {
                  navigate(`/tin-dang/${item.id}`, {
                    state: { previewMyListing: item },
                  });
                }}
                onEdit={(item) => {
                  navigate("/dang-tin", {
                    state: {
                      initialData: {
                        id: item.id,
                        title: item.title,
                        price: item.price,
                        priceVnd: item.priceVnd,
                        area: item.area,
                        wardId: item.wardId,
                        city: item.city,
                        ward: item.ward,
                        address: item.address,
                        image: item.image,
                        photos: item.photos,
                        trangThai: item.status,
                        rejectionReason: item.rejectionReason,
                        phienBanHienTaiId: item.phienBanHienTaiId,
                        phienBanCapNhat: item.phienBanCapNhat,
                        rawPayload: item.rawPayload,
                      },
                    },
                  });
                }}
                onRequestDelete={(item) => setDeleteTarget(item)}
                onRequestBoost={(item) => void openBoostModal(item)}
              />
            ))
          ) : (
            <div className="mp-empty-state">
              {isFetching
                ? "Đang tải danh sách tin đăng từ hệ thống..."
                : "Chưa có tin đăng nào trong mục này."}
            </div>
          )}
        </div>

        {pageCount > 1 && (
          <nav className="mp-pagination" aria-label="Phân trang tin đăng">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(1, current - 1))}
              disabled={page === 1}
              aria-label="Trang trước"
            >
              ‹
            </button>
            {Array.from({ length: pageCount }, (_, index) => index + 1).map(
              (pageNumber) => (
                <button
                  key={pageNumber}
                  type="button"
                  className={page === pageNumber ? "current" : ""}
                  aria-current={page === pageNumber ? "page" : undefined}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber}
                </button>
              )
            )}
            <button
              type="button"
              onClick={() =>
                setPage((current) => Math.min(pageCount, current + 1))
              }
              disabled={page === pageCount}
              aria-label="Trang tiếp theo"
            >
              ›
            </button>
          </nav>
        )}
      </section>

      {/* Modal Chọn gói, Xác nhận & Hiển thị kết quả mua gói đẩy tin */}
      {boostTarget && (
        <div
          className="mp-confirm-backdrop"
          role="dialog"
          aria-modal="true"
          aria-label="Mua gói đẩy tin tài trợ"
          onClick={closeBoostModal}
        >
          <div
            className="mp-boost-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mp-boost-header">
              <div>
                <h3>Mua / Cộng dồn Gói Đẩy Tin</h3>
                <p>{boostTarget.title}</p>
              </div>
              <button
                type="button"
                className="mp-boost-close"
                onClick={closeBoostModal}
                aria-label="Đóng hộp thoại"
              >
                ✕
              </button>
            </div>

            {isLoadingQuote ? (
              <div className="mp-boost-loading">
                <span className="mp-spinner" />
                <p>Đang lấy báo giá gói đẩy tin và số dư ví...</p>
              </div>
            ) : (
              <>
                {needsAccumulateConfirm && boostQuote?.goiHienTai && (
                  <div className="wp-active-pkg-banner">
                    <strong>★ Đang đẩy tin:</strong>{" "}
                    <strong>
                      {formatRemainingDurationVi(
                        boostQuote.goiHienTai.thoiLuongConLaiGiay,
                        boostQuote.goiHienTai.hetHanLuc
                      )}
                    </strong>{" "}
                    (đến{" "}
                    <span>
                      {formatDateTimeVi(boostQuote.goiHienTai.hetHanLuc)}
                    </span>
                    ). Mua thêm gói mới sẽ được cộng dồn trực tiếp thời lượng.
                  </div>
                )}

                <div className="wp-pkg-options">
                  {availablePackages.map((pkg) => {
                    const selected = pkg.maGoi === activePkg?.maGoi;
                    return (
                      <button
                        key={pkg.maGoi}
                        type="button"
                        className={`wp-pkg-option ${selected ? "selected" : ""}`}
                        onClick={() => {
                          setSelectedPkgCode(pkg.maGoi);
                          setBoostResult(null);
                          setBoostError(null);
                          setPurchaseIntentKey(generatePurchaseIntentKey());
                        }}
                      >
                        <div className="wp-pkg-title">{pkg.tenGoi}</div>
                        <div className="wp-pkg-price">
                          {formatVnd(pkg.giaVnd)}
                        </div>
                        <div className="wp-pkg-days">
                          +{pkg.thoiLuongNgay} ngày tại khu vực Được tài trợ
                        </div>
                      </button>
                    );
                  })}
                </div>

                <div className="wp-summary-box">
                  <div className="wp-summary-row">
                    <span>Số dư ví hiện tại:</span>
                    <strong>{formatVnd(currentWalletBalance)}</strong>
                  </div>
                  <div className="wp-summary-row">
                    <span>Giá gói đã chọn:</span>
                    <strong className="wp-price-minus">
                      -{formatVnd(activePkg?.giaVnd)}
                    </strong>
                  </div>
                  <div className="wp-summary-row total">
                    <span>Số dư còn lại sau mua:</span>
                    <strong
                      style={{
                        color: isBalanceInsufficient ? "#dc2626" : "#0d9c8d",
                      }}
                    >
                      {isBalanceInsufficient
                        ? "Không đủ số dư ví"
                        : formatVnd(
                            currentWalletBalance - Number(activePkg?.giaVnd ?? 0)
                          )}
                    </strong>
                  </div>
                </div>

                {needsAccumulateConfirm && (
                  <label className="wp-accumulate-check">
                    <input
                      type="checkbox"
                      checked={agreeAccumulate}
                      onChange={(e) => setAgreeAccumulate(e.target.checked)}
                    />
                    <span>
                      Tôi xác nhận đồng ý{" "}
                      <strong>
                        cộng dồn thêm {activePkg?.thoiLuongNgay} ngày
                      </strong>{" "}
                      vào thời hạn gói hiện tại của tin này.
                    </span>
                  </label>
                )}

                {boostError && (
                  <div className="wp-error-box" role="alert">
                    {boostError}
                  </div>
                )}

                {boostResult && (
                  <div className="wp-result-box" role="status">
                    <h3>✓ Kích hoạt gói thành công</h3>
                    <p>{boostResult.thongDiep}</p>
                    <ul>
                      <li>
                        <span>Mã giao dịch:</span>
                        <code>{boostResult.maGiaoDich}</code>
                      </li>
                      <li>
                        <span>Trạng thái gói:</span>
                        <strong>Đang hoạt động (Được tài trợ)</strong>
                      </li>
                      <li>
                        <span>Hết hạn lúc:</span>
                        <strong>
                          {formatDateTimeVi(boostResult.goi?.hetHanLuc)}
                        </strong>
                      </li>
                      <li>
                        <span>Số dư ví còn lại:</span>
                        <strong>{formatVnd(boostResult.soDuConLaiVnd)}</strong>
                      </li>
                    </ul>
                  </div>
                )}

                <div className="mp-confirm-actions">
                  <Link to="/vi-tien" className="mp-secondary-btn">
                    Mở trang Ví
                  </Link>
                  <button
                    type="button"
                    className="mp-primary-btn"
                    disabled={
                      isPurchasing ||
                      isBalanceInsufficient ||
                      (needsAccumulateConfirm && !agreeAccumulate)
                    }
                    onClick={() => void handleConfirmBoostPurchase()}
                  >
                    {isPurchasing
                      ? "Đang thanh toán..."
                      : needsAccumulateConfirm
                      ? "Xác nhận Cộng dồn"
                      : "Xác nhận Mua gói"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Modal Xác nhận xóa tin đăng */}
      {deleteTarget && (
        <div
          className="mp-confirm-backdrop"
          role="dialog"
          aria-modal="true"
          aria-label="Xác nhận xóa tin đăng"
          onClick={() => setDeleteTarget(null)}
        >
          <div
            className="mp-confirm-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>Xác nhận xóa tin đăng?</h3>
            <p>
              Bạn có chắc chắn muốn xóa tin đăng{" "}
              <strong>“{deleteTarget.title}”</strong> không? Tin sau khi xóa sẽ ngừng hiển thị trên hệ thống.
            </p>
            <div className="mp-confirm-actions">
              <button
                type="button"
                className="mp-confirm-cancel"
                disabled={isDeleting}
                onClick={() => setDeleteTarget(null)}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="mp-confirm-delete"
                disabled={isDeleting}
                onClick={handleConfirmDelete}
              >
                {isDeleting ? "Đang xóa..." : "Xác nhận xóa"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
