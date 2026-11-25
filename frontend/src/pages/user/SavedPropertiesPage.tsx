import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { PropertyCard, type PropertyItem } from "@/components/property/PropertyCard";
import { favoriteService } from "@/services/favoriteService";
import { mapTinTomTatToPropertyItem } from "@/services/searchService";
import "@/pages/public/search-page.css";
import "@/pages/public/property-detail-page.css";

export function SavedPropertiesPage() {
  const [items, setItems] = useState<PropertyItem[]>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [page, setPage] = useState<number>(1);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const pageSize = 12;

  const fetchFavorites = useCallback(async (targetPage: number) => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      const data = await favoriteService.getFavorites(
        Math.max(0, targetPage - 1),
        pageSize
      );
      setItems(data.noiDung.map(mapTinTomTatToPropertyItem));
      setTotalCount(data.tongSo);
    } catch {
      setErrorMsg("Không thể tải danh sách tin yêu thích. Vui lòng thử lại.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchFavorites(page);
  }, [fetchFavorites, page]);

  const handleRemoveFavorite = async (id: string | number) => {
    const idStr = String(id);
    const previousItems = items;
    const previousTotal = totalCount;

    setItems((curr) => curr.filter((item) => String(item.id) !== idStr));
    setTotalCount((curr) => Math.max(0, curr - 1));

    try {
      await favoriteService.removeFavorite(idStr);
    } catch {
      setItems(previousItems);
      setTotalCount(previousTotal);
    }
  };

  const pageCount = Math.max(1, Math.ceil(totalCount / pageSize));

  return (
    <div className="spp-container">
      <div className="spp-header">
        <div>
          <p className="sp-eyebrow">Danh sách cá nhân</p>
          <h1>Tin bất động sản đã lưu ({totalCount})</h1>
          <p>
            Các tin đăng công khai bạn đã đánh dấu yêu thích để theo dõi và so
            sánh.
          </p>
        </div>

        <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
          <button
            type="button"
            className="pdp-back-btn"
            onClick={() => void fetchFavorites(page)}
          >
            Làm mới danh sách
          </button>
          <Link to="/tim-kiem" className="pdp-back-btn">
            + Tìm thêm bất động sản
          </Link>
        </div>
      </div>

      {errorMsg && (
        <div className="sp-error-banner" role="alert">
          <span>{errorMsg}</span>
          <button type="button" onClick={() => void fetchFavorites(page)}>
            Thử lại
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="sp-empty-state" aria-live="polite">
          <p>Đang tải danh sách tin yêu thích...</p>
        </div>
      ) : items.length > 0 ? (
        <>
          <div className="spp-grid">
            {items.map((property) => (
              <PropertyCard
                key={property.id}
                property={property}
                isFavorite={true}
                onToggleFavorite={handleRemoveFavorite}
              />
            ))}
          </div>

          {totalCount > pageSize && (
            <nav
              className="sp-pagination"
              aria-label="Phân trang tin yêu thích"
            >
              <button
                type="button"
                onClick={() => setPage((curr) => Math.max(1, curr - 1))}
                disabled={page === 1}
                aria-label="Trang trước"
              >
                ‹
              </button>
              {Array.from({ length: pageCount }, (_, i) => i + 1).map(
                (pageNumber) => (
                  <button
                    key={pageNumber}
                    type="button"
                    className={page === pageNumber ? "current" : ""}
                    onClick={() => setPage(pageNumber)}
                  >
                    {pageNumber}
                  </button>
                )
              )}
              <button
                type="button"
                onClick={() =>
                  setPage((curr) => Math.min(pageCount, curr + 1))
                }
                disabled={page === pageCount}
                aria-label="Trang sau"
              >
                ›
              </button>
            </nav>
          )}
        </>
      ) : (
        <div className="sp-empty-state">
          <h2>Bạn chưa lưu bất động sản nào</h2>
          <p>
            Nhấn vào biểu tượng trái tim trên các tin đăng khi tìm kiếm để lưu
            vào danh sách theo dõi của bạn.
          </p>
          <Link
            to="/tim-kiem"
            className="pdp-back-btn"
            style={{ marginTop: "16px" }}
          >
            Khám phá bất động sản ngay
          </Link>
        </div>
      )}
    </div>
  );
}
