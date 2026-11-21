import { Link } from "react-router";

export interface PropertyItem {
  id: number;
  title: string;
  price: string;
  priceValue: number;
  place: string;
  size: number;
  beds: number;
  baths: number;
  image: string;
  tag?: string;
  city?: string;
  ward?: string;
  type?: string;
  transaction?: string;
}

export interface PropertyCardProps {
  property: PropertyItem;
  tag?: string;
  isSponsored?: boolean;
  isFavorite?: boolean;
  onToggleFavorite?: (id: number) => void;
  className?: string;
}

export function PropertyCard({
  property,
  tag,
  isSponsored = false,
  isFavorite = false,
  onToggleFavorite,
  className = "",
}: PropertyCardProps) {
  const displayTag = tag ?? property.tag;

  return (
    <article className={`sp-property ${className}`.trim()}>
      <div
        className="sp-property-image"
        style={{ backgroundImage: `url("${property.image}")` }}
      >
        {isSponsored ? (
          <span className="sp-sponsored-tag">{displayTag || "Được tài trợ"}</span>
        ) : (
          displayTag && <span className="sp-property-tag">{displayTag}</span>
        )}

        {onToggleFavorite && (
          <button
            type="button"
            className={`sp-favorite-button ${isFavorite ? "favorited" : ""}`}
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onToggleFavorite(property.id);
            }}
            aria-label={isFavorite ? "Bỏ yêu thích" : "Thêm vào yêu thích"}
            aria-pressed={isFavorite}
          >
            <svg
              viewBox="0 0 24 24"
              fill={isFavorite ? "currentColor" : "none"}
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M20.8 8.5c0 5.5-8.8 10.4-8.8 10.4S3.2 14 3.2 8.5A4.3 4.3 0 0 1 11 6l1 1.2L13 6a4.3 4.3 0 0 1 7.8 2.5Z" />
            </svg>
          </button>
        )}
      </div>

      <div className="sp-property-body">
        <strong className="sp-price">{property.price}</strong>
        <h3>
          <Link
            to={`/tin-dang/${property.id}`}
            style={{ color: "inherit", textDecoration: "none" }}
          >
            {property.title}
          </Link>
        </h3>
        <p className="sp-location">
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
          {property.place}
        </p>
        <div className="sp-property-meta">
          <span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M5 9V5h4M15 5h4v4M19 15v4h-4M9 19H5v-4" />
            </svg>
            {property.size} {typeof property.size === "number" ? "m²" : ""}
          </span>
          <span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M3 17v-6h18v6M5 17v2M19 17v2M3 14h18" />
              <path d="M5 11V8h5a3 3 0 0 1 3 3" />
            </svg>
            {property.beds} {typeof property.beds === "number" ? "phòng ngủ" : ""}
          </span>
          {property.baths !== undefined && (
            <span>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M4 12h16v4a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4v-4Z" />
                <path d="M6 12V6a2 2 0 0 1 4 0v1M4 20v1m16-1v1" />
              </svg>
              {property.baths} {typeof property.baths === "number" ? "phòng tắm" : ""}
            </span>
          )}
        </div>
      </div>
    </article>
  );
}
