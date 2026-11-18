import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import locations from "@/data/locations.json";
import { toast } from "@/stores/useToastStore";

function formatListingAddress(wardId: number): string {
  const ward = locations.wards.find((w) => w.id === wardId);
  if (!ward) return "Địa chỉ đang cập nhật";
  const province = locations.provinces.find((p) => p.id === ward.province_id);
  return province ? `${ward.name}, ${province.name}` : ward.name;
}
import "./manage-property.css";

type ListingStatus = "approved" | "pending" | "rejected" | "hidden" | "sold";
type ListingFilter = "all" | ListingStatus | "inactive";

type Listing = {
  id: number;
  title: string;
  price: string;
  area: number;
  wardId: number;
  date: string;
  photos: number;
  image: string;
  status: ListingStatus;
  rejectionReason?: string;
};

const initialListings: Listing[] = [
  {
    id: 1,
    title: "Nhà phố 3 tầng Quận 7, mặt tiền đường lớn thuận tiện kinh doanh",
    price: "5.2 tỷ",
    area: 70,
    wardId: 27475,
    date: "15/09/2026",
    photos: 8,
    image:
      "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=420&q=82",
    status: "approved",
  },
  {
    id: 2,
    title: "Căn hộ cao cấp 2PN Vinhomes Central Park, view sông Sài Gòn",
    price: "3.8 tỷ",
    area: 85,
    wardId: 26956,
    date: "14/09/2026",
    photos: 12,
    image:
      "https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=420&q=82",
    status: "pending",
  },
  {
    id: 3,
    title: "Bán đất nền thổ cư sổ đỏ riêng, mặt tiền đường nội bộ Quận 9",
    price: "2.4 tỷ",
    area: 100,
    wardId: 26860,
    date: "12/09/2026",
    photos: 5,
    image:
      "https://images.unsplash.com/photo-1600047509807-ba8f99d2cdde?auto=format&fit=crop&w=420&q=82",
    status: "rejected",
    rejectionReason: "Hình ảnh mờ, cần cung cấp hình ảnh rõ nét.",
  },
  {
    id: 4,
    title: "Biệt thự nghỉ dưỡng mini phong cách Châu Âu tuyệt đẹp tại Đà Lạt",
    price: "12.5 tỷ",
    area: 250,
    wardId: 24781,
    date: "10/09/2026",
    photos: 12,
    image:
      "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=420&q=82",
    status: "approved",
  },
  {
    id: 5,
    title: "Nhà phố hiện đại gần công viên và trường học tại Bình Thạnh",
    price: "6.7 tỷ",
    area: 92,
    wardId: 26956,
    date: "09/09/2026",
    photos: 9,
    image:
      "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=420&q=82",
    status: "approved",
  },
  {
    id: 6,
    title: "Căn hộ 2 phòng ngủ ban công rộng, nội thất đầy đủ",
    price: "4.1 tỷ",
    area: 78,
    wardId: 27094,
    date: "08/09/2026",
    photos: 10,
    image:
      "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=420&q=82",
    status: "pending",
  },
  {
    id: 7,
    title: "Đất nền khu dân cư yên tĩnh, đường xe hơi rộng rãi",
    price: "3.2 tỷ",
    area: 90,
    wardId: 27475,
    date: "06/09/2026",
    photos: 6,
    image:
      "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=420&q=82",
    status: "approved",
  },
  {
    id: 8,
    title: "Mặt bằng kinh doanh khu dân cư đông đúc",
    price: "8.6 tỷ",
    area: 120,
    wardId: 27094,
    date: "03/09/2026",
    photos: 7,
    image:
      "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=420&q=82",
    status: "hidden",
  },
];

const filters: { id: ListingFilter; label: string }[] = [
  { id: "all", label: "Tất cả" },
  { id: "pending", label: "Chờ duyệt" },
  { id: "approved", label: "Đã duyệt" },
  { id: "rejected", label: "Bị từ chối" },
  { id: "inactive", label: "Đã bán / Ẩn" },
];

const statusLabels: Record<ListingStatus, string> = {
  approved: "Đã duyệt",
  pending: "Chờ duyệt",
  rejected: "Bị từ chối",
  hidden: "Đã ẩn",
  sold: "Đã bán",
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

function ListingCard({
  listing,
  onEdit,
  onToggleHidden,
  onToggleSold,
  onRequestDelete,
}: {
  listing: Listing;
  onEdit: (listing: Listing) => void;
  onToggleHidden: (id: number) => void;
  onToggleSold: (id: number) => void;
  onRequestDelete: (listing: Listing) => void;
}) {
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
          {formatListingAddress(listing.wardId)}
        </p>
        <p className="mp-date">Đăng ngày: {listing.date}</p>
        {listing.rejectionReason && listing.status === "rejected" && (
          <p className="mp-rejection-reason">
            <strong>!</strong> Lý do từ chối: {listing.rejectionReason}
          </p>
        )}
      </div>

      <span className={`mp-status ${listing.status}`}>
        {statusLabels[listing.status]}
      </span>

      <div className="mp-listing-actions">
        {/* Chỉ cho phép sửa tin khi đã duyệt hoặc bị từ chối (theo BR-02 / UC-07) */}
        {(listing.status === "approved" || listing.status === "rejected") && (
          <button type="button" onClick={() => onEdit(listing)}>
            {listing.status === "rejected" ? "Sửa & Nộp lại" : "Chỉnh sửa"}
          </button>
        )}
        <button type="button" onClick={() => onToggleHidden(listing.id)}>
          {listing.status === "hidden" ? "Hiện tin" : "Ẩn tin"}
        </button>
        {(listing.status === "approved" || listing.status === "sold") && (
          <button type="button" onClick={() => onToggleSold(listing.id)}>
            {listing.status === "sold" ? "Đang bán" : "Đã bán"}
          </button>
        )}
        <button
          type="button"
          className="delete-btn"
          onClick={() => onRequestDelete(listing)}
        >
          Xóa tin
        </button>
      </div>
    </article>
  );
}

export function MyPropertiesPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();
  const [previewSignedIn, setPreviewSignedIn] = useState(false);
  const [listings, setListings] = useState<Listing[]>(initialListings);
  const [activeFilter, setActiveFilter] = useState<ListingFilter>("all");
  const [page, setPage] = useState(1);
  const [deleteTarget, setDeleteTarget] = useState<Listing | null>(null);

  const filteredListings = useMemo(
    () =>
      listings.filter((listing) => {
        if (activeFilter === "all") return true;
        if (activeFilter === "inactive")
          return listing.status === "hidden" || listing.status === "sold";
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

  const toggleListingStatus = (id: number, status: "hidden" | "sold") => {
    setListings((current) =>
      current.map((listing) => {
        if (listing.id !== id) return listing;
        if (listing.status === status) return { ...listing, status: "approved" };
        return { ...listing, status };
      })
    );
  };

  const handleConfirmDelete = () => {
    if (!deleteTarget) return;
    setListings((current) =>
      current.filter((item) => item.id !== deleteTarget.id)
    );
    toast.info(
      `Đã xóa tin đăng “${deleteTarget.title}”.`,
      "Đã xóa tin đăng"
    );
    setDeleteTarget(null);
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
                : filter.id === "inactive"
                ? listings.filter(
                    (listing) =>
                      listing.status === "hidden" || listing.status === "sold"
                  ).length
                : listings.filter((listing) => listing.status === filter.id)
                    .length;
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
                        area: item.area,
                        wardId: item.wardId,
                      },
                    },
                  });
                }}
                onToggleHidden={(id) => toggleListingStatus(id, "hidden")}
                onToggleSold={(id) => toggleListingStatus(id, "sold")}
                onRequestDelete={(item) => setDeleteTarget(item)}
              />
            ))
          ) : (
            <div className="mp-empty-state">
              Chưa có tin đăng nào trong mục này.
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
                onClick={() => setDeleteTarget(null)}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="mp-confirm-delete"
                onClick={handleConfirmDelete}
              >
                Xác nhận xóa
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
