import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { searchService } from "@/services/searchService";
import { favoriteService } from "@/services/favoriteService";
import { listingService } from "@/services/listingService";
import { mockProperties } from "@/data/mockProperties";
import type { MyListingItem } from "@/types/listing";
import type { TinCongKhaiDto } from "@/types/search";
import {
  formatDienTichM2,
  formatDonViGiaLabel,
  formatGiaGocVaDonVi,
  formatGiaQuyDoi,
  formatLoaiBdsLabel,
  formatLoaiGiaoDichLabel,
} from "@/utils/priceFormatter";
import "./property-detail-page.css";

const DEFAULT_FALLBACK_IMAGE =
  "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=1200&q=82";

function buildTinFromMyListing(
  myItem: MyListingItem,
  ownerFallback?: { hoTen?: string | null; soDienThoai?: string | null }
): TinCongKhaiDto {
  const raw = myItem.rawPayload;
  const loaiGiaoDich =
    raw?.loaiGiaoDich === "cho_thue" || myItem.price?.includes("/")
      ? "cho_thue"
      : "ban";
  const donViGia =
    raw?.donViGia || (loaiGiaoDich === "cho_thue" ? "vnd_thang" : "vnd_tong");
  const loaiBds = raw?.loaiBds || "can_ho";
  const giaGoc = String(raw?.giaGoc ?? myItem.priceVnd ?? 0);
  const dienTichM2 = String(raw?.dienTichM2 ?? myItem.area ?? 0);
  const imageList =
    raw?.anhIds && raw.anhIds.length > 0
      ? raw.anhIds.map((imgId: string, idx: number) => ({
          id: imgId,
          thuTu: idx + 1,
          duongDanDoc: `/api/images/${imgId}/content`,
        }))
      : [
          {
            id: "my-img-1",
            thuTu: 1,
            duongDanDoc: myItem.image || DEFAULT_FALLBACK_IMAGE,
          },
        ];

  return {
    id: String(myItem.id),
    trangThai: "da_duyet",
    ngayDang: new Date().toISOString(),
    noiDung: {
      id: myItem.phienBanHienTaiId || `ver-${myItem.id}`,
      soPhienBan: 1,
      tieuDe: raw?.tieuDe || myItem.title,
      moTa:
        raw?.moTa ||
        `${myItem.title}. Địa chỉ: ${myItem.address || "Đang cập nhật"}.`,
      loaiGiaoDich,
      loaiBds,
      giaGoc,
      donViGia,
      giaQuyDoiHienThi: null,
      dienTichM2,
      diaChi: raw?.diaChi || myItem.address || "Đang cập nhật",
      khuVuc:
        raw?.khuVuc ||
        [myItem.ward, myItem.city].filter(Boolean).join(", ") ||
        myItem.address ||
        "Đang cập nhật",
      viDo: String(raw?.viDo ?? "10.7769"),
      kinhDo: String(raw?.kinhDo ?? "106.7009"),
      taoLuc: new Date().toISOString(),
      anh: imageList,
    },
    lienHe: {
      hoTen: ownerFallback?.hoTen || "Chủ sở hữu tin đăng",
      soDienThoai: ownerFallback?.soDienThoai || null,
    },
  };
}

function buildDemoTinCongKhai(numericId: number): TinCongKhaiDto | null {
  const found = mockProperties.find((item) => Number(item.id) === numericId);
  if (!found) return null;

  const isRent = found.transaction === "Cho thuê";
  const loaiGiaoDich = isRent ? "cho_thue" : "ban";
  const donViGia = isRent ? "vnd_thang" : "vnd_tong";
  const loaiBds =
    found.type === "Căn hộ chung cư"
      ? "can_ho"
      : found.type === "Nhà riêng"
      ? "nha_rieng"
      : found.type === "Đất nền"
      ? "dat"
      : found.type === "Phòng trọ"
      ? "phong_tro"
      : "khac";

  const sizeNum = Number(found.size) || 75;
  const pricePerM2 = Math.round(found.priceValue / sizeNum);

  return {
    id: String(found.id),
    trangThai: "da_duyet",
    ngayDang: "2026-11-20T08:30:00Z",
    noiDung: {
      id: `demo-ver-${found.id}`,
      soPhienBan: 1,
      tieuDe: found.title,
      moTa: `${found.title} tọa lạc tại ${found.place}. Không gian thiết kế thoáng đãng, kết nối giao thông thuận tiện đến các tiện ích trung tâm, trường học và khu thương mại. Thông tin đã được kiểm duyệt công khai trên hệ thống HomeFinder.`,
      loaiGiaoDich,
      loaiBds,
      giaGoc: String(found.priceValue),
      donViGia,
      giaQuyDoiHienThi: {
        giaTri: `${pricePerM2}.00`,
        donVi: isRent ? "vnd_m2_thang" : "vnd_m2",
        chuThich: "Giá quy đổi tham khảo; không dùng để lọc.",
      },
      dienTichM2: String(sizeNum),
      diaChi: `${found.ward || found.place}`,
      khuVuc: found.place,
      viDo: "10.7885",
      kinhDo: "106.7425",
      taoLuc: "2026-11-20T08:00:00Z",
      anh: [
        {
          id: `img-${found.id}-1`,
          thuTu: 1,
          duongDanDoc: found.image || DEFAULT_FALLBACK_IMAGE,
        },
        {
          id: `img-${found.id}-2`,
          thuTu: 2,
          duongDanDoc:
            "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1200&q=82",
        },
        {
          id: `img-${found.id}-3`,
          thuTu: 3,
          duongDanDoc:
            "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1200&q=82",
        },
      ],
    },
    lienHe: {
      hoTen: "Nguyễn Minh Tuấn",
      soDienThoai: "0908341289",
    },
  };
}

function formatDateTimeVi(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, user } = useAuth();

  const [listing, setListing] = useState<TinCongKhaiDto | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [notFound, setNotFound] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [activeImageIdx, setActiveImageIdx] = useState<number>(0);
  const [isLightboxOpen, setIsLightboxOpen] = useState<boolean>(false);
  const [isFavorite, setIsFavorite] = useState<boolean>(false);
  const [isTogglingFav, setIsTogglingFav] = useState<boolean>(false);
  const [copiedPhone, setCopiedPhone] = useState<boolean>(false);

  useEffect(() => {
    if (!id) {
      setNotFound(true);
      setIsLoading(false);
      return;
    }

    let active = true;
    setIsLoading(true);
    setNotFound(false);
    setErrorMsg(null);
    setActiveImageIdx(0);

    searchService
      .getPublicListingDetail(id)
      .then((data) => {
        if (!active) return;
        setListing(data);
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        const status = (err as { response?: { status?: number } })?.response
          ?.status;

        // 1. Kiểm tra nếu người dùng bấm xem từ trang Quản lý tin đăng cá nhân (/quan-ly-tin)
        const statePreview = (
          location.state as { previewMyListing?: MyListingItem } | null
        )?.previewMyListing;
        const matchedMyListing =
          statePreview && String(statePreview.id) === String(id)
            ? statePreview
            : listingService
                .getMyListings([])
                .find((item) => String(item.id) === String(id));

        if (matchedMyListing) {
          setListing(
            buildTinFromMyListing(matchedMyListing, {
              hoTen: user?.hoTen,
              soDienThoai: user?.soDienThoai,
            })
          );
          setIsLoading(false);
          return;
        }

        // 2. Nếu là ID số của dữ liệu mẫu minh họa khi xem demo
        if (/^\d+$/.test(id)) {
          const demo = buildDemoTinCongKhai(Number(id));
          if (demo) {
            setListing(demo);
            setIsLoading(false);
            return;
          }
        }

        if (status === 404) {
          setNotFound(true);
        } else {
          setErrorMsg(
            "Không thể tải thông tin bất động sản lúc này. Vui lòng thử lại sau."
          );
        }
        setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id, location.state, user?.hoTen, user?.soDienThoai]);

  // Kiểm tra trạng thái đã lưu trong danh sách yêu thích
  useEffect(() => {
    if (!isAuthenticated || !id || /^\d+$/.test(id)) return;
    let active = true;
    favoriteService
      .getFavorites(0, 50)
      .then((pageData) => {
        if (!active) return;
        setIsFavorite(pageData.noiDung.some((item) => item.id === id));
      })
      .catch(() => {
        // Bỏ qua lỗi phụ
      });
    return () => {
      active = false;
    };
  }, [isAuthenticated, id]);

  const images = useMemo(() => {
    if (!listing?.noiDung?.anh || listing.noiDung.anh.length === 0) {
      return [
        { id: "fallback", thuTu: 1, duongDanDoc: DEFAULT_FALLBACK_IMAGE },
      ];
    }
    return [...listing.noiDung.anh].sort((a, b) => a.thuTu - b.thuTu);
  }, [listing]);

  useEffect(() => {
    if (!isLightboxOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsLightboxOpen(false);
      } else if (e.key === "ArrowLeft" && images.length > 1) {
        setActiveImageIdx((curr) =>
          curr === 0 ? images.length - 1 : curr - 1
        );
      } else if (e.key === "ArrowRight" && images.length > 1) {
        setActiveImageIdx((curr) =>
          curr === images.length - 1 ? 0 : curr + 1
        );
      }
    };

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isLightboxOpen, images.length]);

  const handleToggleFavorite = async () => {
    if (!listing) return;

    if (/^\d+$/.test(listing.id)) {
      setIsFavorite((prev) => !prev);
      return;
    }

    if (!isAuthenticated) {
      navigate("/dang-nhap", {
        state: { from: { pathname: `/tin-dang/${listing.id}` } },
      });
      return;
    }

    if (isTogglingFav) return;
    const nextState = !isFavorite;
    setIsFavorite(nextState);
    setIsTogglingFav(true);

    try {
      if (nextState) {
        await favoriteService.addFavorite(listing.id);
      } else {
        await favoriteService.removeFavorite(listing.id);
      }
    } catch {
      setIsFavorite(!nextState);
    } finally {
      setIsTogglingFav(false);
    }
  };

  const handleCopyPhone = async (phone: string) => {
    try {
      await navigator.clipboard.writeText(phone);
      setCopiedPhone(true);
      window.setTimeout(() => setCopiedPhone(false), 2500);
    } catch {
      setCopiedPhone(false);
    }
  };

  if (isLoading) {
    return (
      <div className="pdp-container">
        <div
          className="pdp-card"
          style={{ textAlign: "center", padding: "48px 24px" }}
        >
          <p style={{ margin: 0, color: "#5c7268", fontSize: "15px" }}>
            Đang tải thông tin chi tiết bất động sản...
          </p>
        </div>
      </div>
    );
  }

  if (notFound || !listing) {
    return (
      <div className="pdp-container">
        <div
          className="pdp-card"
          style={{ textAlign: "center", padding: "54px 24px" }}
        >
          <h2 style={{ margin: "0 0 10px", color: "#142c23" }}>
            {errorMsg
              ? "Không thể tải dữ liệu"
              : "Không tìm thấy tin đăng công khai"}
          </h2>
          <p
            style={{
              margin: "0 auto 22px",
              maxWidth: "520px",
              color: "#647a70",
              fontSize: "14px",
              lineHeight: 1.6,
            }}
          >
            {errorMsg ||
              "Tin đăng này không tồn tại, đang chờ kiểm duyệt, đã bị gỡ hoặc tài khoản chủ sở hữu hiện không đủ điều kiện hiển thị công khai."}
          </p>
          <Link to="/tim-kiem" className="pdp-back-btn">
            ← Quay lại trang tìm kiếm
          </Link>
        </div>
      </div>
    );
  }

  const { noiDung, lienHe } = listing;
  const originalPriceText = formatGiaGocVaDonVi(
    noiDung.giaGoc,
    noiDung.donViGia
  );
  const convertedPriceText = formatGiaQuyDoi(noiDung.giaQuyDoiHienThi);
  const activeImage = images[activeImageIdx] || images[0];
  const latNum = Number(noiDung.viDo) || 10.7769;
  const lngNum = Number(noiDung.kinhDo) || 106.7009;
  const googleMapsUrl = `https://www.google.com/maps?q=${latNum},${lngNum}`;
  const ownerName = lienHe?.hoTen?.trim() || "Chủ sở hữu bất động sản";
  const ownerPhone = lienHe?.soDienThoai?.trim() || null;
  const ownerInitial = ownerName.charAt(0).toUpperCase();

  return (
    <div className="pdp-container">
      <div className="pdp-top-nav">
        <button
          type="button"
          className="pdp-back-btn"
          onClick={() => navigate(-1)}
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M19 12H5m6-6-6 6 6 6" />
          </svg>
          Quay lại
        </button>

        <nav className="pdp-breadcrumb" aria-label="Đường dẫn điều hướng">
          <Link to="/">Trang chủ</Link>
          <span>/</span>
          <Link to="/tim-kiem">Tìm kiếm</Link>
          <span>/</span>
          <span>{formatLoaiBdsLabel(noiDung.loaiBds)}</span>
        </nav>
      </div>

      {/* Khu vực bộ sưu tập ảnh (lazy loading) */}
      <section className="pdp-gallery" aria-label="Hình ảnh bất động sản">
        <div
          className="pdp-main-image-wrap"
          onClick={() => setIsLightboxOpen(true)}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              setIsLightboxOpen(true);
            }
          }}
          aria-label="Nhấn để phóng to xem toàn bộ hình ảnh"
          title="Nhấn vào hình để xem đầy đủ"
          style={{ cursor: "zoom-in" }}
        >
          <img
            src={activeImage.duongDanDoc}
            alt={`${noiDung.tieuDe} - Ảnh ${activeImageIdx + 1}`}
            className="pdp-main-image"
            loading="lazy"
          />

          <div className="pdp-gallery-badges">
            <span className="pdp-badge pdp-badge-primary">
              {formatLoaiGiaoDichLabel(noiDung.loaiGiaoDich)}
            </span>
            <span className="pdp-badge pdp-badge-secondary">
              {formatLoaiBdsLabel(noiDung.loaiBds)}
            </span>
          </div>

          {images.length > 1 && (
            <>
              <button
                type="button"
                className="pdp-gallery-nav-btn prev"
                onClick={(e) => {
                  e.stopPropagation();
                  setActiveImageIdx((curr) =>
                    curr === 0 ? images.length - 1 : curr - 1
                  );
                }}
                aria-label="Ảnh trước"
              >
                ‹
              </button>
              <button
                type="button"
                className="pdp-gallery-nav-btn next"
                onClick={(e) => {
                  e.stopPropagation();
                  setActiveImageIdx((curr) =>
                    curr === images.length - 1 ? 0 : curr + 1
                  );
                }}
                aria-label="Ảnh tiếp theo"
              >
                ›
              </button>
            </>
          )}

          <span className="pdp-image-counter">
            🔍 Xem đầy đủ • {activeImageIdx + 1} / {images.length} ảnh
          </span>
        </div>

        {images.length > 1 && (
          <div
            className="pdp-thumb-row"
            role="tablist"
            aria-label="Chọn ảnh xem chi tiết"
          >
            {images.map((img, idx) => (
              <button
                key={img.id}
                type="button"
                className={`pdp-thumb-btn ${
                  idx === activeImageIdx ? "active" : ""
                }`}
                onClick={() => setActiveImageIdx(idx)}
                aria-label={`Xem ảnh ${idx + 1}`}
                aria-selected={idx === activeImageIdx}
              >
                <img
                  src={img.duongDanDoc}
                  alt={`Ảnh thu nhỏ ${idx + 1}`}
                  loading="lazy"
                />
              </button>
            ))}
          </div>
        )}
      </section>

      <div className="pdp-grid">
        {/* Cột trái: Thông tin chi tiết, giá gốc, giá quy đổi, mô tả và vị trí */}
        <div className="pdp-main">
          <section className="pdp-card">
            <h1 className="pdp-title">{noiDung.tieuDe}</h1>
            <p className="pdp-address-line">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.9"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
                <circle cx="12" cy="10" r="2.5" />
              </svg>
              <span>
                {noiDung.diaChi} • <strong>{noiDung.khuVuc}</strong>
              </span>
            </p>

            <div className="pdp-key-metrics">
              <div className="pdp-metric-box">
                <span className="pdp-metric-label">
                  Giá gốc niêm yết ({formatDonViGiaLabel(noiDung.donViGia)})
                </span>
                <strong className="pdp-metric-primary">
                  {originalPriceText}
                </strong>
                <span className="pdp-metric-sub">
                  Hình thức: {formatLoaiGiaoDichLabel(noiDung.loaiGiaoDich)}
                </span>
              </div>

              <div className="pdp-metric-box">
                <span className="pdp-metric-label">Giá quy đổi tham chiếu</span>
                <strong className="pdp-metric-primary">
                  {convertedPriceText
                    ? `≈ ${convertedPriceText}`
                    : originalPriceText}
                </strong>
                <span className="pdp-metric-sub">
                  {noiDung.giaQuyDoiHienThi?.chuThich ||
                    "Giá quy đổi tham khảo; không dùng để lọc."}
                </span>
              </div>

              <div className="pdp-metric-box">
                <span className="pdp-metric-label">Diện tích sử dụng</span>
                <strong className="pdp-metric-primary">
                  {formatDienTichM2(noiDung.dienTichM2)}
                </strong>
                <span className="pdp-metric-sub">
                  Phân loại: {formatLoaiBdsLabel(noiDung.loaiBds)}
                </span>
              </div>
            </div>
          </section>

          <section className="pdp-card">
            <h2 className="pdp-section-title">Mô tả chi tiết</h2>
            <p className="pdp-description">{noiDung.moTa}</p>
          </section>

          <section className="pdp-card">
            <h2 className="pdp-section-title">Thông số bất động sản</h2>
            <div className="pdp-specs-grid">
              <div className="pdp-spec-item">
                <span>Loại giao dịch</span>
                <strong>{formatLoaiGiaoDichLabel(noiDung.loaiGiaoDich)}</strong>
              </div>
              <div className="pdp-spec-item">
                <span>Loại bất động sản</span>
                <strong>{formatLoaiBdsLabel(noiDung.loaiBds)}</strong>
              </div>
              <div className="pdp-spec-item">
                <span>Diện tích</span>
                <strong>{formatDienTichM2(noiDung.dienTichM2)}</strong>
              </div>
              <div className="pdp-spec-item">
                <span>Đơn vị giá gốc</span>
                <strong>{formatDonViGiaLabel(noiDung.donViGia)}</strong>
              </div>
              <div className="pdp-spec-item">
                <span>Khu vực hành chính</span>
                <strong>{noiDung.khuVuc}</strong>
              </div>
              <div className="pdp-spec-item">
                <span>Ngày đăng công khai</span>
                <strong>{formatDateTimeVi(listing.ngayDang)}</strong>
              </div>
            </div>
          </section>

          <section className="pdp-card">
            <div className="pdp-map-header">
              <div>
                <h2
                  className="pdp-section-title"
                  style={{ marginBottom: "4px" }}
                >
                  Vị trí trên bản đồ
                </h2>
                <span style={{ color: "#687e74", fontSize: "12.5px" }}>
                  Tọa độ GPS: {noiDung.viDo}, {noiDung.kinhDo}
                </span>
              </div>
              <a
                href={googleMapsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="pdp-ext-map-link"
              >
                Mở trên Google Maps ↗
              </a>
            </div>

            <iframe
              title="Bản đồ vị trí bất động sản"
              className="pdp-map-frame"
              loading="lazy"
              src={`https://www.openstreetmap.org/export/embed.html?bbox=${
                lngNum - 0.008
              }%2C${latNum - 0.006}%2C${lngNum + 0.008}%2C${
                latNum + 0.006
              }&layer=mapnik&marker=${latNum}%2C${lngNum}`}
            />
          </section>
        </div>

        {/* Cột phải: Thẻ liên hệ chủ tin & Lưu yêu thích */}
        <aside className="pdp-sidebar" aria-label="Thông tin liên hệ chủ tin">
          <div className="pdp-contact-card">
            <div className="pdp-owner-row">
              <div className="pdp-owner-avatar" aria-hidden="true">
                {ownerInitial}
              </div>
              <div className="pdp-owner-info">
                <small>Người đăng tin</small>
                <strong>{ownerName}</strong>
                <span className="pdp-owner-verified">✓ Tin đã kiểm duyệt</span>
              </div>
            </div>

            <div className="pdp-contact-actions">
              {ownerPhone ? (
                <>
                  <a
                    href={`tel:${ownerPhone}`}
                    className="pdp-phone-call-btn"
                  >
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
                      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92Z" />
                    </svg>
                    Gọi ngay: {ownerPhone}
                  </a>

                  <button
                    type="button"
                    className="pdp-copy-phone-btn"
                    onClick={() => handleCopyPhone(ownerPhone)}
                  >
                    {copiedPhone
                      ? "✓ Đã sao chép số điện thoại"
                      : "Sao chép số điện thoại"}
                  </button>
                </>
              ) : (
                <div
                  style={{
                    padding: "12px",
                    borderRadius: "10px",
                    background: "#f6faf8",
                    color: "#576e64",
                    fontSize: "13px",
                    textAlign: "center",
                  }}
                >
                  Chủ tin chưa cập nhật số điện thoại liên hệ công khai.
                </div>
              )}

              <button
                type="button"
                className={`pdp-fav-action-btn ${
                  isFavorite ? "favorited" : ""
                }`}
                onClick={handleToggleFavorite}
                disabled={isTogglingFav}
                aria-pressed={isFavorite}
              >
                <svg
                  viewBox="0 0 24 24"
                  width="18"
                  height="18"
                  fill={isFavorite ? "currentColor" : "none"}
                  stroke="currentColor"
                  strokeWidth="1.9"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M20.8 8.5c0 5.5-8.8 10.4-8.8 10.4S3.2 14 3.2 8.5A4.3 4.3 0 0 1 11 6l1 1.2L13 6a4.3 4.3 0 0 1 7.8 2.5Z" />
                </svg>
                {isFavorite
                  ? "Đã lưu vào tin yêu thích (Bỏ lưu)"
                  : "Lưu vào danh sách yêu thích"}
              </button>
            </div>

            <div className="pdp-safety-note">
              <strong>Lưu ý giao dịch an toàn:</strong> Đối chiếu giấy tờ pháp
              lý và hiện trạng thực tế của bất động sản trước khi đặt cọc.
            </div>
          </div>
        </aside>
      </div>

      {/* Lightbox xem đầy đủ hình ảnh */}
      {isLightboxOpen && (
        <div
          className="pdp-lightbox-backdrop"
          role="dialog"
          aria-modal="true"
          aria-label="Xem đầy đủ hình ảnh bất động sản"
          onClick={() => setIsLightboxOpen(false)}
        >
          <div
            className="pdp-lightbox-topbar"
            onClick={(e) => e.stopPropagation()}
          >
            <span className="pdp-lightbox-counter">
              Ảnh {activeImageIdx + 1} / {images.length} — {noiDung.tieuDe}
            </span>
            <button
              type="button"
              className="pdp-lightbox-close"
              onClick={() => setIsLightboxOpen(false)}
              aria-label="Đóng trình xem ảnh"
            >
              ✕
            </button>
          </div>

          <div
            className="pdp-lightbox-stage"
            onClick={(e) => e.stopPropagation()}
          >
            {images.length > 1 && (
              <button
                type="button"
                className="pdp-lightbox-nav prev"
                onClick={() =>
                  setActiveImageIdx((curr) =>
                    curr === 0 ? images.length - 1 : curr - 1
                  )
                }
                aria-label="Xem hình trước"
                title="Hình trước (←)"
              >
                ‹
              </button>
            )}

            <img
              src={activeImage.duongDanDoc}
              alt={`${noiDung.tieuDe} - Ảnh đầy đủ ${activeImageIdx + 1}`}
              className="pdp-lightbox-img"
            />

            {images.length > 1 && (
              <button
                type="button"
                className="pdp-lightbox-nav next"
                onClick={() =>
                  setActiveImageIdx((curr) =>
                    curr === images.length - 1 ? 0 : curr + 1
                  )
                }
                aria-label="Xem hình tiếp theo"
                title="Hình tiếp theo (→)"
              >
                ›
              </button>
            )}
          </div>

          {images.length > 1 && (
            <div
              className="pdp-lightbox-thumbs"
              onClick={(e) => e.stopPropagation()}
            >
              {images.map((img, idx) => (
                <button
                  key={`lb-${img.id}`}
                  type="button"
                  className={`pdp-lightbox-thumb ${
                    idx === activeImageIdx ? "active" : ""
                  }`}
                  onClick={() => setActiveImageIdx(idx)}
                  aria-label={`Chọn xem ảnh ${idx + 1}`}
                >
                  <img src={img.duongDanDoc} alt={`Ảnh ${idx + 1}`} />
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
