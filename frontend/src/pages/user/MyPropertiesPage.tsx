import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import locations from "@/data/locations.json";
import { toast } from "@/stores/useToastStore";
import { listingService } from "@/services/listingService";
import type { ListingStatus, MyListingItem } from "@/types/listing";
import "./manage-property.css";

function formatListingAddress(listing: MyListingItem): string {
  if (listing.address) return listing.address;
  if (!listing.wardId) return "Địa chỉ đang cập nhật";
  const ward = locations.wards.find((w) => w.id === listing.wardId);
  if (!ward) return "Địa chỉ đang cập nhật";
  const province = locations.provinces.find((p) => p.id === ward.province_id);
  return province ? `${ward.name}, ${province.name}` : ward.name;
}

type ListingFilter = "all" | "pending" | "approved" | "rejected" | "violation";

const initialListings: MyListingItem[] = [];

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
  onEdit,
  onRequestDelete,
}: {
  listing: MyListingItem;
  onEdit: (listing: MyListingItem) => void;
  onRequestDelete: (listing: MyListingItem) => void;
}) {
  const canEdit = listing.status === "approved" || listing.status === "rejected";
  const canDelete = listing.status !== "deleted" && listing.status !== "violation";

  return (
    <article className="mp-listing-card">
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
        {listing.rejectionReason && listing.status === "rejected" && (
          <p className="mp-rejection-reason">
            <strong>!</strong> Lý do từ chối: {listing.rejectionReason}
          </p>
        )}
      </div>

      <span className={`mp-status ${listing.status}`}>
        {statusLabels[listing.status] || listing.status}
      </span>

      <div className="mp-listing-actions">
        {/* Chỉ cho phép sửa tin khi đã duyệt hoặc bị từ chối (theo BR-02 / UC-07) */}
        {canEdit && (
          <button
            type="button"
            className="mp-action-btn edit-btn"
            onClick={() => onEdit(listing)}
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
            onClick={() => onRequestDelete(listing)}
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
  const [listings, setListings] = useState<MyListingItem[]>(() =>
    listingService.getMyListings(initialListings)
  );
  const [isFetching, setIsFetching] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) return;
    let isCancelled = false;
    setIsFetching(true);
    listingService
      .fetchMyListings()
      .then((items) => {
        if (!isCancelled) {
          setListings(items);
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

      // Trường hợp tin demo hoặc backend DB cục bộ chưa có bản ghi (404/network)
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
              Quản lý và theo dõi trạng thái các bài đăng bất động sản của bạn
            </p>
          </div>
          <Link to="/dang-tin" className="mp-new-listing-btn">
            <AddIcon />
            Đăng tin mới
          </Link>
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
