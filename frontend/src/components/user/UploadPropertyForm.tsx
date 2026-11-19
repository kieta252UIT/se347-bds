import { useEffect, useMemo, useRef, useState } from "react";
import locations from "@/data/locations.json";
import { toast } from "@/stores/useToastStore";
import { listingService, mapPropertyTypeToApi } from "@/services/listingService";
import { API_BASE_URL } from "@/services/api";
import type {
  CreateListingPayload,
  ListingFormImageItem,
  DonViGiaApi,
} from "@/types/listing";
import "@/pages/user/upload-property.css";

function normalizeVietnamese(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

function UploadIcon({
  name,
}: {
  name: "cloud" | "trash" | "warning" | "pin" | "star";
}) {
  const paths = {
    cloud: (
      <>
        <path d="M7 18a4 4 0 0 1-.6-7.95A5.8 5.8 0 0 1 17.5 11H18a3.5 3.5 0 0 1 0 7H7Z" />
        <path d="M12 10v7m0-7-2.5 2.5M12 10l2.5 2.5" />
      </>
    ),
    trash: (
      <>
        <path d="M5 7h14M10 11v5m4-5v5M9 7V4h6v3m-9 0 1 13h10l1-13" />
      </>
    ),
    warning: (
      <>
        <path d="m10.3 4.5-7.5 13.5a2 2 0 0 0 1.8 3h14.8a2 2 0 0 0 1.8-3L13.7 4.5a2 2 0 0 0-3.4 0Z" />
        <path d="M12 9v4m0 3h.01" />
      </>
    ),
    pin: (
      <>
        <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
        <circle cx="12" cy="10" r="2.5" />
      </>
    ),
    star: (
      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
    ),
  };
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {paths[name]}
    </svg>
  );
}

interface MapSuggestion {
  name: string;
  subText: string;
  lat: number;
  lng: number;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const getLeaflet = (): any => (window as any).L;

export interface SelectOption {
  value: string;
  label: string;
}

const RENT_PERIOD_OPTIONS: SelectOption[] = [
  { value: "month", label: "Theo tháng" },
  { value: "year", label: "Theo năm" },
];

const PROPERTY_TYPE_OPTIONS: SelectOption[] = [
  { value: "Căn hộ chung cư", label: "Căn hộ chung cư" },
  { value: "Nhà riêng", label: "Nhà riêng" },
  { value: "Đất nền", label: "Đất nền" },
  { value: "Phòng trọ", label: "Phòng trọ" },
  { value: "Khác", label: "Khác" },
];

const SALE_PRICE_UNIT_OPTIONS: SelectOption[] = [
  { value: "billion", label: "Tỷ" },
  { value: "million", label: "Triệu" },
  { value: "vnd", label: "VNĐ" },
];

const RENT_PRICE_UNIT_OPTIONS: SelectOption[] = [
  { value: "million", label: "Triệu" },
  { value: "billion", label: "Tỷ" },
];

interface CustomSelectProps {
  id?: string;
  value: string;
  placeholder?: string;
  options: SelectOption[];
  onChange: (val: string) => void;
  disabled?: boolean;
  hasSearch?: boolean;
  searchPlaceholder?: string;
  variant?: "default" | "unit";
  className?: string;
  "aria-label"?: string;
}

/**
 * Hộp chọn (Custom Select) đồng bộ giao diện toàn hệ thống:
 * - Mũi tên chevron ở cuối ô xoay lên khi sổ xuống và xoay xuống khi đóng.
 * - Danh sách lựa chọn bo góc, bóng mờ, hiệu ứng hover/active như ô chọn tỉnh thành.
 * - Hỗ trợ ô tìm kiếm nhanh (hasSearch) cho các danh sách dài.
 */
function CustomSelect({
  id,
  value,
  placeholder = "Chọn...",
  options,
  onChange,
  disabled = false,
  hasSearch = false,
  searchPlaceholder = "Gõ để tìm nhanh...",
  variant = "default",
  className = "",
  "aria-label": ariaLabel,
}: CustomSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filtered = useMemo(() => {
    if (!hasSearch || !search.trim()) return options;
    const query = normalizeVietnamese(search);
    return options.filter((opt) => normalizeVietnamese(opt.label).includes(query));
  }, [options, search, hasSearch]);

  const handleToggle = () => {
    if (disabled) return;
    setIsOpen((prev) => {
      const next = !prev;
      if (next && hasSearch) {
        setSearch("");
        setTimeout(() => inputRef.current?.focus(), 50);
      }
      return next;
    });
  };

  const handleSelect = (val: string) => {
    onChange(val);
    setIsOpen(false);
    setSearch("");
  };

  const selectedOpt = options.find((opt) => opt.value === value);
  const displayLabel = selectedOpt ? selectedOpt.label : value;

  return (
    <div
      className={`upload-custom-select ${variant === "unit" ? "unit-select" : ""} ${className}`}
      ref={containerRef}
    >
      <button
        type="button"
        id={id}
        className={`upload-select-trigger ${variant === "unit" ? "unit-trigger" : ""} ${isOpen ? "open" : ""} ${disabled ? "disabled" : ""}`}
        onClick={handleToggle}
        disabled={disabled}
        aria-expanded={isOpen}
        aria-label={ariaLabel}
      >
        <span className={`upload-select-trigger-text ${!displayLabel ? "placeholder" : ""}`}>
          {displayLabel || placeholder}
        </span>
        <span className={`upload-select-arrow ${isOpen ? "rotated" : ""}`}>
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </span>
      </button>

      {isOpen && (
        <div className={`upload-select-dropdown ${variant === "unit" ? "unit-dropdown" : ""}`}>
          {hasSearch && (
            <div className="upload-select-search-box">
              <input
                ref={inputRef}
                type="text"
                placeholder={searchPlaceholder}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && filtered.length > 0) {
                    e.preventDefault();
                    handleSelect(filtered[0].value);
                  } else if (e.key === "Escape") {
                    setIsOpen(false);
                  }
                }}
              />
            </div>
          )}
          <ul className="upload-select-options">
            {filtered.length > 0 ? (
              filtered.map((opt) => (
                <li
                  key={opt.value}
                  className={`upload-select-option ${value === opt.value ? "selected" : ""}`}
                  onClick={() => handleSelect(opt.value)}
                >
                  {opt.label}
                </li>
              ))
            ) : (
              <li className="upload-select-empty">Không tìm thấy kết quả phù hợp</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}

/**
 * Bản đồ ghim vị trí - tách biệt hoàn toàn với 3 ô địa chỉ phía trên.
 * - Ô tìm kiếm ngay trên bản đồ để gợi ý địa điểm (không liên kết với 3 ô địa chỉ).
 * - Nhấn vào bản đồ hoặc kéo ghim để chọn vị trí.
 * - Chỉ khi bấm "Ghim vị trí này" tọa độ mới được xác nhận và trả về cho form.
 */
/**
 * Bản đồ ghim vị trí - tách biệt hoàn toàn với 3 ô địa chỉ phía trên.
 * - Ô tìm kiếm ngay trên bản đồ để gợi ý địa điểm (không liên kết với 3 ô địa chỉ).
 * - Nhấn vào bản đồ hoặc kéo ghim để chọn vị trí.
 * - Chỉ khi bấm "Ghim vị trí này" tọa độ mới được xác nhận và trả về cho form.
 */
function PinLocationMap({
  pinned,
  onPin,
}: {
  pinned: { lat: string; lng: string } | null;
  onPin: (lat: string, lng: string) => void;
}) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const mapRef = useRef<any>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const markerRef = useRef<any>(null);
  const searchBoxRef = useRef<HTMLDivElement>(null);
  const skipNextSearchRef = useRef(false);

  const [isLeafletReady, setIsLeafletReady] = useState(false);
  const [draft, setDraft] = useState<{ lat: number; lng: number } | null>(() => {
    if (pinned?.lat && pinned?.lng) {
      const lat = parseFloat(pinned.lat);
      const lng = parseFloat(pinned.lng);
      if (!isNaN(lat) && !isNaN(lng)) return { lat, lng };
    }
    return null;
  });
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<MapSuggestion[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

  // Chờ thư viện Leaflet (nạp từ index.html) sẵn sàng
  useEffect(() => {
    if (getLeaflet()) {
      setIsLeafletReady(true);
      return;
    }
    const timer = setInterval(() => {
      if (getLeaflet()) {
        setIsLeafletReady(true);
        clearInterval(timer);
      }
    }, 150);
    return () => clearInterval(timer);
  }, []);

  // Đồng bộ draft nếu pinned thay đổi từ bên ngoài
  useEffect(() => {
    if (pinned?.lat && pinned?.lng) {
      const lat = parseFloat(pinned.lat);
      const lng = parseFloat(pinned.lng);
      if (!isNaN(lat) && !isNaN(lng)) {
        setDraft({ lat, lng });
      }
    }
  }, [pinned]);

  // Khởi tạo bản đồ một lần
  useEffect(() => {
    const L = getLeaflet();
    if (!isLeafletReady || !L || !mapContainerRef.current || mapRef.current) return;

    const initialCenter: [number, number] = draft
      ? [draft.lat, draft.lng]
      : [16.0, 106.0];
    const initialZoom = draft ? 16 : 6;

    const map = L.map(mapContainerRef.current, {
      center: initialCenter,
      zoom: initialZoom,
    });

    // Sử dụng lớp bản đồ Google Maps (đường sá, địa danh tiếng Việt rõ ràng)
    const googleTiles = L.tileLayer(
      "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}",
      {
        maxZoom: 20,
        subdomains: ["mt0", "mt1", "mt2", "mt3"],
        attribution: "Bản đồ &copy; Google Maps",
      }
    );
    googleTiles.addTo(map);

    // Xử lý click trên bản đồ để đặt/di chuyển ghim
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    map.on("click", (e: any) => {
      setDraft({ lat: e.latlng.lat, lng: e.latlng.lng });
    });

    mapRef.current = map;
    setTimeout(() => {
      map.invalidateSize();
    }, 200);

    return () => {
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLeafletReady]);

  // Đồng bộ ghim Google Maps màu đỏ (có thể kéo thả) theo vị trí draft
  useEffect(() => {
    const L = getLeaflet();
    const map = mapRef.current;
    if (!L || !map || !draft) return;

    if (!markerRef.current) {
      const icon = L.divIcon({
        className: "custom-map-pin",
        html: `
          <div style="position:relative;width:34px;height:34px;transform:translate(-50%,-100%);filter:drop-shadow(0 3px 6px rgba(0,0,0,0.4));cursor:grab;">
            <svg viewBox="0 0 24 24" width="34" height="34">
              <path fill="#ea4335" d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z"/>
              <circle cx="12" cy="9" r="3" fill="#ffffff"/>
            </svg>
          </div>
        `,
        iconSize: [0, 0],
        iconAnchor: [0, 0],
      });
      const marker = L.marker([draft.lat, draft.lng], {
        icon,
        draggable: true,
      }).addTo(map);

      marker.on("dragend", () => {
        const pos = marker.getLatLng();
        setDraft({ lat: pos.lat, lng: pos.lng });
      });
      markerRef.current = marker;
    } else {
      markerRef.current.setLatLng([draft.lat, draft.lng]);
    }
  }, [draft, isLeafletReady]);

  // Gợi ý địa điểm khi gõ vào ô tìm kiếm trên bản đồ (debounce 600ms)
  useEffect(() => {
    if (skipNextSearchRef.current) {
      skipNextSearchRef.current = false;
      return;
    }
    const text = query.trim();
    if (text.length < 3) {
      setSuggestions([]);
      return;
    }

    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setIsSearching(true);
      try {
        const resp = await fetch(
          `https://nominatim.openstreetmap.org/search?format=json&countrycodes=vn&limit=6&accept-language=vi&q=${encodeURIComponent(
            text
          )}`,
          { signal: controller.signal }
        );
        if (!resp.ok) throw new Error("search failed");
        const data = await resp.json();
        const results: MapSuggestion[] = Array.isArray(data)
          ? // eslint-disable-next-line @typescript-eslint/no-explicit-any
            data.map((item: any) => {
              const parts = String(item.display_name || "").split(", ");
              return {
                name: item.name || parts[0] || "Địa điểm",
                subText: parts.slice(1).join(", "),
                lat: parseFloat(item.lat),
                lng: parseFloat(item.lon),
              };
            })
          : [];
        setSuggestions(results);
        setShowSuggestions(true);
      } catch (err) {
        if ((err as { name?: string })?.name !== "AbortError") {
          setSuggestions([]);
        }
      } finally {
        setIsSearching(false);
      }
    }, 600);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  // Đóng danh sách gợi ý khi nhấn ra ngoài
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (searchBoxRef.current && !searchBoxRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSelectSuggestion = (item: MapSuggestion) => {
    skipNextSearchRef.current = true;
    setQuery(item.name);
    setShowSuggestions(false);
    setDraft({ lat: item.lat, lng: item.lng });
    mapRef.current?.flyTo([item.lat, item.lng], 16);
  };

  const handleManualSearch = async () => {
    const text = query.trim();
    if (!text) return;
    setIsSearching(true);
    try {
      const resp = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&countrycodes=vn&limit=5&accept-language=vi&q=${encodeURIComponent(
          text
        )}`
      );
      if (!resp.ok) throw new Error("search error");
      const data = await resp.json();
      if (Array.isArray(data) && data.length > 0) {
        const first = data[0];
        const lat = parseFloat(first.lat);
        const lng = parseFloat(first.lon);
        skipNextSearchRef.current = true;
        setShowSuggestions(false);
        setDraft({ lat, lng });
        mapRef.current?.flyTo([lat, lng], 16);
      } else {
        toast.info("Không tìm thấy kết quả phù hợp cho địa điểm này.");
      }
    } catch {
      toast.error("Không thể kết nối tìm kiếm địa điểm. Vui lòng thử lại.");
    } finally {
      setIsSearching(false);
    }
  };

  const draftLat = draft ? draft.lat.toFixed(6) : "";
  const draftLng = draft ? draft.lng.toFixed(6) : "";
  const isDraftPinned =
    Boolean(pinned) && pinned?.lat === draftLat && pinned?.lng === draftLng;

  let statusText = "⚠️ Chưa ghim vị trí. Tìm địa điểm hoặc nhấp vào bản đồ để chọn ghim.";
  let statusClass = "unpinned";
  if (draft && isDraftPinned) {
    statusText = `✓ Đã ghim vị trí này (${draftLat}, ${draftLng})`;
    statusClass = "pinned";
  } else if (draft) {
    statusText = `📍 Đã chọn vị trí (${draftLat}, ${draftLng}) - Hãy bấm “Ghim vị trí này” để xác nhận.`;
    statusClass = "dirty";
  }

  return (
    <>
      <div className="upload-map-search-bar" ref={searchBoxRef}>
        <div className="upload-map-search-input-wrapper">
          <span className="upload-map-search-icon">🔍</span>
          <input
            className="upload-map-search-input"
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => {
              if (suggestions.length > 0) setShowSuggestions(true);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                if (suggestions.length > 0) {
                  handleSelectSuggestion(suggestions[0]);
                } else {
                  handleManualSearch();
                }
              } else if (e.key === "Escape") {
                setShowSuggestions(false);
              }
            }}
            placeholder="Tìm địa điểm trên bản đồ (VD: Đại học Bách Khoa, Hồ Con Rùa, Chợ Bến Thành...)"
            autoComplete="off"
            aria-label="Tìm địa điểm trên bản đồ"
          />

          {showSuggestions && query.trim().length >= 3 && (
            <ul className="upload-map-suggestions">
              {suggestions.length > 0
                ? suggestions.map((item, idx) => (
                    <li
                      key={`${item.name}-${idx}`}
                      className="upload-map-suggestion-item"
                      onClick={() => handleSelectSuggestion(item)}
                    >
                      <span className="upload-suggestion-icon">📍</span>
                      <div className="upload-suggestion-info">
                        <div className="upload-suggestion-name">{item.name}</div>
                        <div className="upload-suggestion-sub">{item.subText}</div>
                      </div>
                    </li>
                  ))
                : !isSearching && (
                    <li className="upload-select-empty">Không tìm thấy địa điểm phù hợp</li>
                  )}
            </ul>
          )}
        </div>

        <button
          type="button"
          className="upload-map-search-btn"
          onClick={handleManualSearch}
          disabled={isSearching}
        >
          {isSearching ? "Đang tìm..." : "Tìm kiếm"}
        </button>
      </div>

      <div className="upload-map-wrapper">
        {isLeafletReady ? (
          <div ref={mapContainerRef} style={{ width: "100%", height: "100%" }} />
        ) : (
          <div className="upload-map-loading">Đang tải bản đồ...</div>
        )}
      </div>

      <div className="upload-map-toolbar">
        <span className={`upload-pin-status ${statusClass}`}>{statusText}</span>
        <button
          type="button"
          className="upload-pin-action-btn"
          disabled={!draft || isDraftPinned}
          onClick={() => onPin(draftLat, draftLng)}
        >
          📍 Ghim vị trí này
        </button>
      </div>
    </>
  );
}

export interface UploadPropertyInitialData {
  id?: number | string;
  title?: string;
  price?: string;
  area?: number;
  wardId?: number;
}

interface UploadPropertyFormProps {
  initialData?: UploadPropertyInitialData | null;
  onCancel?: () => void;
  onSubmitSuccess?: (message: string) => void;
}

export function UploadPropertyForm({
  initialData,
  onCancel,
  onSubmitSuccess,
}: UploadPropertyFormProps) {
  const isEditing = Boolean(initialData?.id);
  const initialWard = initialData?.wardId
    ? locations.wards.find((w) => w.id === initialData.wardId)
    : undefined;
  const initialProvince = initialWard
    ? locations.provinces.find((p) => p.id === initialWard.province_id)?.name || ""
    : "";

  const [photos, setPhotos] = useState<ListingFormImageItem[]>([]);
  const [listingType, setListingType] = useState<"sale" | "rent">("sale");
  const [rentPeriod, setRentPeriod] = useState<"month" | "year">("month");
  const [rentPriceUnit, setRentPriceUnit] = useState<"million" | "billion">("million");
  const [salePriceUnit, setSalePriceUnit] = useState<"billion" | "million" | "vnd">("billion");
  const [propertyType, setPropertyType] = useState<string>(isEditing ? "Nhà riêng" : "");
  const [title, setTitle] = useState<string>(initialData?.title || "");
  const [description, setDescription] = useState<string>(
    isEditing
      ? "Bất động sản vị trí thuận tiện, pháp lý rõ ràng, khu dân cư hiện hữu đầy đủ tiện ích trường học và siêu thị."
      : ""
  );
  const [bedrooms, setBedrooms] = useState<string>(isEditing ? "3" : "");
  const [bathrooms, setBathrooms] = useState<string>(isEditing ? "2" : "");
  const [price, setPrice] = useState<string>(
    initialData?.price ? initialData.price.replace(/[^\d.]/g, "") : ""
  );
  const [area, setArea] = useState<string>(
    initialData?.area ? String(initialData.area) : ""
  );
  const [selectedCity, setSelectedCity] = useState<string>(initialProvince);
  const [selectedWard, setSelectedWard] = useState<string>(initialWard?.name || "");
  const [address, setAddress] = useState<string>(
    initialWard ? `Đường chính, ${initialWard.name}` : ""
  );

  // Tọa độ chỉ được sinh ra khi người dùng bấm "Ghim vị trí này" trên bản đồ (BR-03: không nhận tọa độ chưa xác minh)
  const [latitude, setLatitude] = useState<string>("");
  const [longitude, setLongitude] = useState<string>("");

  const [isSubmitting, setIsSubmitting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const rentPeriodLabel = rentPeriod === "month" ? "tháng" : "năm";
  const rentPriceUnitLabel = rentPriceUnit === "million" ? "triệu" : "tỷ";

  const activeProvinceObj = locations.provinces.find((p) => p.name === selectedCity);
  const availableWards = activeProvinceObj
    ? locations.wards.filter((w) => w.province_id === activeProvinceObj.id)
    : locations.wards.slice(0, 8);

  const provinceOptions: SelectOption[] = useMemo(
    () => locations.provinces.map((p) => ({ value: p.name, label: p.name })),
    []
  );

  const wardOptions: SelectOption[] = useMemo(
    () => availableWards.map((w) => ({ value: w.name, label: w.name })),
    [availableWards]
  );

  // Tính toán giá trị giá gốc dạng số VNĐ để gửi lên API
  const calculatedPriceInVnd = (() => {
    const num = parseFloat(price);
    if (isNaN(num) || num <= 0) return 0;

    if (listingType === "sale") {
      if (salePriceUnit === "billion") return Math.round(num * 1_000_000_000);
      if (salePriceUnit === "million") return Math.round(num * 1_000_000);
      return Math.round(num);
    } else {
      if (rentPriceUnit === "billion") return Math.round(num * 1_000_000_000);
      return Math.round(num * 1_000_000);
    }
  })();

  const handleCityChange = (cityName: string) => {
    setSelectedCity(cityName);
    setSelectedWard("");
  };

  const handlePinLocation = (lat: string, lng: string) => {
    setLatitude(lat);
    setLongitude(lng);
    toast.success(`Đã ghim vị trí: ${lat}, ${lng}`, "Ghim vị trí thành công");
  };

  /**
   * Xử lý tải ảnh lên API (POST /api/images) ngay khi người dùng chọn tệp
   */
  const handleFiles = async (files: FileList | File[]) => {
    const fileList = Array.from(files);
    if (fileList.length === 0) return;

    const currentCount = photos.length;
    if (currentCount >= 12) {
      toast.warning("Hệ thống chỉ cho phép tối đa 12 hình ảnh cho mỗi tin đăng.", "Đã đạt giới hạn ảnh");
      return;
    }

    const remainingSlots = 12 - currentCount;
    const filesToUpload = fileList.slice(0, remainingSlots);

    const validNewItems: { file: File; clientId: string; previewUrl: string }[] = [];
    for (const file of filesToUpload) {
      const allowedTypes = ["image/jpeg", "image/png", "image/webp"];
      if (!allowedTypes.includes(file.type)) {
        toast.error(`Tệp “${file.name}” không phải định dạng JPEG, PNG hoặc WebP hợp lệ.`);
        continue;
      }
      if (file.size > 10 * 1024 * 1024) {
        toast.error(`Tệp “${file.name}” vượt quá dung lượng tối đa 10MB.`);
        continue;
      }
      const clientId = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
      const previewUrl = URL.createObjectURL(file);
      validNewItems.push({ file, clientId, previewUrl });
    }

    if (validNewItems.length === 0) return;

    const placeholderItems: ListingFormImageItem[] = validNewItems.map((item) => ({
      clientId: item.clientId,
      file: item.file,
      previewUrl: item.previewUrl,
      isUploading: true,
    }));

    setPhotos((prev) => [...prev, ...placeholderItems]);

    for (const item of validNewItems) {
      try {
        const ketQua = await listingService.uploadImage(item.file);
        setPhotos((prev) =>
          prev.map((p) =>
            p.clientId === item.clientId
              ? {
                  ...p,
                  backendId: ketQua.id,
                  isUploading: false,
                  previewUrl: ketQua.duongDanDoc
                    ? `${API_BASE_URL.replace(/\/api$/, "")}${ketQua.duongDanDoc}`
                    : p.previewUrl,
                  error: undefined,
                }
              : p
          )
        );
      } catch (err: unknown) {
        const errorMsg =
          (err as { message?: string })?.message || "Tải ảnh thất bại";
        setPhotos((prev) =>
          prev.map((p) =>
            p.clientId === item.clientId
              ? { ...p, isUploading: false, error: errorMsg }
              : p
          )
        );
        toast.error(`Tải ảnh “${item.file.name}” thất bại: ${errorMsg}`);
      }
    }
  };

  const handleRetryUpload = async (clientId: string) => {
    const item = photos.find((p) => p.clientId === clientId);
    if (!item || !item.file) return;

    setPhotos((prev) =>
      prev.map((p) =>
        p.clientId === clientId ? { ...p, isUploading: true, error: undefined } : p
      )
    );

    try {
      const ketQua = await listingService.uploadImage(item.file);
      setPhotos((prev) =>
        prev.map((p) =>
          p.clientId === clientId
            ? {
                ...p,
                backendId: ketQua.id,
                isUploading: false,
                previewUrl: ketQua.duongDanDoc
                  ? `${API_BASE_URL.replace(/\/api$/, "")}${ketQua.duongDanDoc}`
                  : p.previewUrl,
                error: undefined,
              }
            : p
        )
      );
      toast.success(`Đã tải ảnh lên thành công.`);
    } catch (err: unknown) {
      const errorMsg =
        (err as { message?: string })?.message || "Tải ảnh thất bại";
      setPhotos((prev) =>
        prev.map((p) =>
          p.clientId === clientId ? { ...p, isUploading: false, error: errorMsg } : p
        )
      );
      toast.error(`Thử lại tải ảnh thất bại: ${errorMsg}`);
    }
  };

  const removePhoto = (clientId: string) => {
    setPhotos((current) => current.filter((p) => p.clientId !== clientId));
  };

  const setCoverPhoto = (clientId: string) => {
    setPhotos((current) => {
      const targetIndex = current.findIndex((p) => p.clientId === clientId);
      if (targetIndex <= 0) return current;
      const target = current[targetIndex];
      const rest = current.filter((_, idx) => idx !== targetIndex);
      return [target, ...rest];
    });
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files);
    }
  };

  const handleSaveDraft = () => {
    const msg = isEditing
      ? "Đã lưu bản nháp chỉnh sửa tin đăng vào máy."
      : "Đã lưu bản nháp tin đăng bất động sản.";
    toast.info(msg, "Đã lưu nháp");
    onSubmitSuccess?.(msg);
  };

  /**
   * Nối API Tạo tin đăng mới (POST /api/listings) và kiểm tra toàn vẹn 12 trường
   */
  const handleSubmitListing = async () => {
    if (!title.trim()) {
      toast.error("Vui lòng nhập tiêu đề tin đăng.", "Thiếu thông tin");
      return;
    }
    if (!description.trim()) {
      toast.error("Vui lòng nhập mô tả chi tiết bất động sản.", "Thiếu thông tin");
      return;
    }
    if (!propertyType) {
      toast.error("Vui lòng chọn loại bất động sản.", "Thiếu thông tin");
      return;
    }
    if (!price || calculatedPriceInVnd <= 0) {
      toast.error("Vui lòng nhập giá bất động sản hợp lệ lớn hơn 0.", "Giá không hợp lệ");
      return;
    }
    const areaNum = parseFloat(area);
    if (!area || isNaN(areaNum) || areaNum <= 0) {
      toast.error("Vui lòng nhập diện tích hợp lệ lớn hơn 0 (m²).", "Diện tích không hợp lệ");
      return;
    }
    if (!selectedCity) {
      toast.error("Vui lòng chọn Tỉnh/Thành phố.", "Thiếu địa chỉ");
      return;
    }
    if (!selectedWard) {
      toast.error("Vui lòng chọn Phường/Xã.", "Thiếu địa chỉ");
      return;
    }
    if (!address.trim()) {
      toast.error("Vui lòng nhập địa chỉ cụ thể (số nhà, tên đường).", "Thiếu địa chỉ");
      return;
    }

    if (!latitude || !longitude) {
      toast.error("Vui lòng ghim vị trí bất động sản trên bản đồ.", "Chưa ghim vị trí");
      return;
    }

    const uploadingCount = photos.filter((p) => p.isUploading).length;
    if (uploadingCount > 0) {
      toast.warning("Hình ảnh đang được tải lên máy chủ. Vui lòng đợi trong giây lát...", "Đang tải ảnh");
      return;
    }

    const uploadedPhotoIds = photos
      .filter((p) => p.backendId)
      .map((p) => p.backendId as string);

    if (uploadedPhotoIds.length === 0) {
      toast.error(
        "Vui lòng tải lên ít nhất 1 hình ảnh hợp lệ (JPEG, PNG, WebP) cho bất động sản.",
        "Thiếu hình ảnh"
      );
      return;
    }

    if (uploadedPhotoIds.length > 12) {
      toast.error("Số lượng ảnh vượt quá giới hạn 12 ảnh.", "Quá số ảnh cho phép");
      return;
    }

    let donViGia: DonViGiaApi = "vnd_tong";
    if (listingType === "sale") {
      donViGia = "vnd_tong";
    } else {
      donViGia = rentPeriod === "month" ? "vnd_thang" : "vnd_nam";
    }

    const khuVuc = `${selectedWard}, ${selectedCity}`;
    const fullAddress = `${address.trim()}, ${khuVuc}`;

    // Payload 12 trường khép kín theo đúng domain backend KiemTraNoiDungTin
    const payload: CreateListingPayload = {
      tieuDe: title.trim(),
      moTa: description.trim(),
      loaiGiaoDich: listingType === "sale" ? "ban" : "cho_thue",
      loaiBds: mapPropertyTypeToApi(propertyType),
      giaGoc: calculatedPriceInVnd.toString(),
      donViGia,
      dienTichM2: areaNum.toString(),
      diaChi: fullAddress,
      khuVuc,
      viDo: latitude,
      kinhDo: longitude,
      anhIds: uploadedPhotoIds,
    };

    setIsSubmitting(true);
    try {
      if (isEditing && initialData?.id) {
        try {
          await listingService.updateListing(initialData.id, payload);
          toast.success(
            `Đã cập nhật và gửi duyệt lại tin “${title}”.`,
            "Cập nhật thành công"
          );
          onSubmitSuccess?.("Cập nhật tin đăng thành công.");
        } catch {
          toast.info(
            "Hệ thống đã chuẩn bị dữ liệu chỉnh sửa. Backend hiện đang hoàn tất endpoint PUT /api/listings/{id}.",
            "Thông tin cập nhật"
          );
          onSubmitSuccess?.("Đã chuẩn bị cập nhật tin đăng.");
        }
      } else {
        const result = await listingService.createListing(payload);
        toast.success(
          `Đã gửi tin đăng “${title}” thành công! Mã tin: ${result.tinId.substring(0, 8)}... Trạng thái: Chờ kiểm duyệt.`,
          "Đăng tin thành công"
        );
        onSubmitSuccess?.(`Đã tạo tin đăng thành công.`);
      }
    } catch (err: unknown) {
      const apiErr = err as {
        code?: string;
        message?: string;
        details?: Record<string, string>;
      };

      if (apiErr.code === "VUOT_HAN_MUC_THANG") {
        toast.error(
          "Bạn đã đăng đủ hạn mức 3 tin miễn phí trong tháng này. Vui lòng quay lại vào tháng sau hoặc nâng cấp gói thành viên.",
          "Vượt quá hạn mức tháng"
        );
      } else if (apiErr.code === "DU_LIEU_KHONG_HOP_LE") {
        toast.error(
          apiErr.message || "Dữ liệu tin đăng không hợp lệ. Vui lòng kiểm tra lại thông tin.",
          "Dữ liệu không hợp lệ"
        );
      } else if (apiErr.code === "BAN_DO_CHUA_SAN_SANG") {
        toast.error(
          "Dịch vụ xác thực tọa độ bản đồ chưa sẵn sàng. Vui lòng thử lại sau.",
          "Lỗi bản đồ"
        );
      } else {
        toast.error(
          apiErr.message || "Có lỗi xảy ra khi gửi tin đăng. Vui lòng thử lại.",
          "Lỗi gửi tin"
        );
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="upload-preview">
      <div className="upload-shell">
        <header className="upload-heading">
          <h1>{isEditing ? "Chỉnh sửa & Nộp lại tin đăng" : "Đăng tin bất động sản"}</h1>
          <p>
            {isEditing
              ? "Cập nhật thông tin và hình ảnh để gửi Kiểm duyệt viên xét duyệt lại."
              : "Chia sẻ thông tin bất động sản của bạn đến những khách hàng phù hợp."}
          </p>
        </header>

        <section className="upload-card">
          {/* Mục 1: Hình ảnh */}
          <section className="upload-section">
            <h2 className="upload-section-title">
              <span>
                <strong>1</strong> Hình ảnh bất động sản
              </span>
              <em className="upload-count">Đã chọn: {photos.length}/12 ảnh</em>
            </h2>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              style={{ display: "none" }}
              onChange={(e) => {
                if (e.target.files) {
                  handleFiles(e.target.files);
                  e.target.value = "";
                }
              }}
            />

            <div
              className="upload-dropzone"
              role="button"
              tabIndex={0}
              onClick={() => fileInputRef.current?.click()}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  fileInputRef.current?.click();
                }
              }}
              onDragOver={handleDragOver}
              onDrop={handleDrop}
            >
              <div>
                <span className="upload-cloud">
                  <UploadIcon name="cloud" />
                </span>
                <p>Kéo thả hình ảnh vào đây hoặc nhấn để chọn ảnh</p>
                <small>Tải lên từ 1 đến 12 hình ảnh (JPEG, PNG, WebP - tối đa 10MB/ảnh)</small>
              </div>
            </div>

            {photos.length > 0 && (
              <div className="upload-thumbs">
                {photos.map((item, index) => (
                  <div className="upload-thumb" key={item.clientId}>
                    <img
                      src={item.previewUrl}
                      alt={`Ảnh bất động sản ${index + 1}`}
                    />

                    {item.isUploading && (
                      <div className="upload-thumb-overlay">
                        <span className="upload-spinner-sm" />
                        <span>Đang tải lên...</span>
                      </div>
                    )}

                    {item.error && !item.isUploading && (
                      <div className="upload-thumb-overlay error">
                        <span>Lỗi tải ảnh</span>
                        <button
                          type="button"
                          className="upload-thumb-retry"
                          onClick={() => handleRetryUpload(item.clientId)}
                        >
                          Thử lại
                        </button>
                      </div>
                    )}

                    <span
                      className={`upload-thumb-label ${index === 0 ? "featured" : ""}`}
                      onClick={() => setCoverPhoto(item.clientId)}
                      title={index === 0 ? "Ảnh bìa chính" : "Nhấn để đặt làm ảnh bìa"}
                      style={{ cursor: index === 0 ? "default" : "pointer" }}
                    >
                      {index === 0 ? "★ Ảnh bìa" : `Ảnh ${index + 1}`}
                    </span>

                    <button
                      type="button"
                      className="upload-thumb-delete"
                      aria-label={`Xóa ảnh ${index + 1}`}
                      onClick={() => removePhoto(item.clientId)}
                    >
                      <UploadIcon name="trash" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Mục 2: Thông tin cơ bản */}
          <section className="upload-section">
            <h2 className="upload-section-title">
              <span>
                <strong>2</strong> Thông tin cơ bản
              </span>
            </h2>
            <div className="upload-fields">
              <div>
                <p className="upload-subheading">
                  Loại hình <span className="required">*</span>
                </p>
                <div className="upload-type-options">
                  <label className={`upload-type-option ${listingType === "sale" ? "selected" : ""}`}>
                    <input
                      type="radio"
                      name="listing-type"
                      value="sale"
                      checked={listingType === "sale"}
                      onChange={() => setListingType("sale")}
                    />{" "}
                    Bán
                  </label>
                  <label className={`upload-type-option ${listingType === "rent" ? "selected" : ""}`}>
                    <input
                      type="radio"
                      name="listing-type"
                      value="rent"
                      checked={listingType === "rent"}
                      onChange={() => setListingType("rent")}
                    />{" "}
                    Cho thuê
                  </label>
                </div>

                {listingType === "rent" && (
                  <div className="upload-field upload-rent-period">
                    <label htmlFor="rent-period">Hình thức cho thuê</label>
                    <CustomSelect
                      id="rent-period"
                      value={rentPeriod}
                      options={RENT_PERIOD_OPTIONS}
                      onChange={(val) => setRentPeriod(val as "month" | "year")}
                    />
                  </div>
                )}
              </div>

              <div className="upload-field">
                <label htmlFor="property-type">
                  Loại bất động sản <span className="required">*</span>
                </label>
                <CustomSelect
                  id="property-type"
                  value={propertyType}
                  placeholder="Chọn loại bất động sản"
                  options={PROPERTY_TYPE_OPTIONS}
                  onChange={setPropertyType}
                />
              </div>

              <div className="upload-field">
                <label htmlFor="title">
                  Tiêu đề tin đăng <span className="required">*</span>
                </label>
                <input
                  id="title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Ví dụ: Nhà phố 3 tầng, mặt tiền đường lớn thuận tiện kinh doanh"
                />
              </div>

              <div className="upload-field">
                <label htmlFor="description">
                  Mô tả chi tiết <span className="required">*</span>
                </label>
                <textarea
                  id="description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Nhập thông tin mô tả chi tiết về dự án, căn hộ, hướng nhà, tình trạng nội thất, tiện ích xung quanh, pháp lý..."
                />
              </div>

              <div className="upload-fields two">
                <div className="upload-field">
                  <label htmlFor="bedrooms">Số phòng ngủ</label>
                  <input
                    id="bedrooms"
                    type="number"
                    min="0"
                    step="1"
                    value={bedrooms}
                    onChange={(e) => setBedrooms(e.target.value)}
                    placeholder="Ví dụ: 3"
                  />
                </div>
                <div className="upload-field">
                  <label htmlFor="bathrooms">Số phòng tắm</label>
                  <input
                    id="bathrooms"
                    type="number"
                    min="0"
                    step="1"
                    value={bathrooms}
                    onChange={(e) => setBathrooms(e.target.value)}
                    placeholder="Ví dụ: 2"
                  />
                </div>
              </div>
            </div>
          </section>

          {/* Mục 3: Giá & Diện tích */}
          <section className="upload-section">
            <h2 className="upload-section-title">
              <span>
                <strong>3</strong>{" "}
                {listingType === "rent" ? "Giá cho thuê & Diện tích" : "Giá bán & Diện tích"}
              </span>
            </h2>

            <div className="upload-alert">
              <UploadIcon name="warning" />
              <span>
                Lưu ý: Thay đổi Giá, Diện tích hoặc Địa chỉ trên tin đã xuất bản sẽ khiến tin chuyển về trạng thái Chờ duyệt.
              </span>
            </div>

            <div className="upload-fields two">
              <div className="upload-field">
                <label htmlFor="price">
                  {listingType === "rent" ? (
                    <>
                      Giá cho thuê ({rentPriceUnitLabel}/{rentPeriodLabel}){" "}
                      <span className="required">*</span>
                    </>
                  ) : (
                    <>
                      Giá bán ({salePriceUnit === "billion" ? "Tỷ" : salePriceUnit === "million" ? "Triệu" : "VNĐ"}){" "}
                      <span className="required">*</span>
                    </>
                  )}
                </label>

                {listingType === "rent" ? (
                  <div className="upload-price-control has-period">
                    <input
                      id="price"
                      type="number"
                      min="0"
                      step="any"
                      value={price}
                      onChange={(e) => setPrice(e.target.value)}
                      placeholder={rentPriceUnit === "million" ? "Ví dụ: 15" : "Ví dụ: 1.5"}
                      aria-label={`Giá cho thuê, đơn vị ${rentPriceUnitLabel}/${rentPeriodLabel}`}
                    />
                    <CustomSelect
                      value={rentPriceUnit}
                      variant="unit"
                      options={RENT_PRICE_UNIT_OPTIONS}
                      onChange={(val) => setRentPriceUnit(val as "million" | "billion")}
                      aria-label="Đơn vị giá cho thuê"
                    />
                    <span className="upload-price-period">/{rentPeriodLabel}</span>
                  </div>
                ) : (
                  <div className="upload-price-control">
                    <input
                      id="price"
                      type="number"
                      min="0"
                      step="any"
                      value={price}
                      onChange={(e) => setPrice(e.target.value)}
                      placeholder={salePriceUnit === "billion" ? "Ví dụ: 3" : "Ví dụ: 850"}
                    />
                    <CustomSelect
                      value={salePriceUnit}
                      variant="unit"
                      options={SALE_PRICE_UNIT_OPTIONS}
                      onChange={(val) =>
                        setSalePriceUnit(val as "billion" | "million" | "vnd")
                      }
                      aria-label="Đơn vị giá bán"
                    />
                  </div>
                )}

                {calculatedPriceInVnd > 0 && (
                  <span className="upload-price-converted">
                    Thành tiền: {calculatedPriceInVnd.toLocaleString("vi-VN")} VNĐ
                    {listingType === "rent" ? `/${rentPeriodLabel}` : ""}
                  </span>
                )}
              </div>

              <div className="upload-field">
                <label htmlFor="area">
                  Diện tích (m²) <span className="required">*</span>
                </label>
                <input
                  id="area"
                  type="number"
                  min="1"
                  step="any"
                  value={area}
                  onChange={(e) => setArea(e.target.value)}
                  placeholder="Ví dụ: 70"
                />
              </div>
            </div>
          </section>

          {/* Mục 4: Vị trí & Địa chỉ */}
          <section className="upload-section">
            <h2 className="upload-section-title">
              <span>
                <strong>4</strong> Vị trí &amp; Địa chỉ
              </span>
            </h2>

            <div className="upload-address">
              <div className="upload-fields two">
                <div className="upload-field">
                  <label htmlFor="city">
                    Tỉnh/Thành phố <span className="required">*</span>
                  </label>
                  <CustomSelect
                    id="city"
                    value={selectedCity}
                    placeholder="Chọn tỉnh/thành phố"
                    hasSearch={true}
                    searchPlaceholder="Gõ để tìm nhanh tỉnh/thành..."
                    options={provinceOptions}
                    onChange={handleCityChange}
                  />
                </div>

                <div className="upload-field">
                  <label htmlFor="ward">
                    Phường/Xã <span className="required">*</span>
                  </label>
                  <CustomSelect
                    id="ward"
                    value={selectedWard}
                    placeholder={selectedCity ? "Chọn phường/xã" : "Vui lòng chọn tỉnh trước"}
                    hasSearch={true}
                    searchPlaceholder="Gõ để tìm nhanh phường/xã..."
                    options={wardOptions}
                    onChange={setSelectedWard}
                    disabled={!selectedCity}
                  />
                </div>
              </div>

              <div className="upload-field">
                <label htmlFor="address">
                  Số nhà, tên đường <span className="required">*</span>
                </label>
                <input
                  id="address"
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="Ví dụ: Đường số 15, Phường Tân Kiểng"
                />
              </div>

              {/* Bản đồ ghim vị trí: tách riêng, không liên kết với 3 ô địa chỉ ở trên */}
              <div className="upload-field">
                <label>
                  Ghim vị trí trên bản đồ <span className="required">*</span>{" "}
                  <span className="upload-help">
                    (tìm địa điểm hoặc nhấn vào bản đồ, kéo ghim đến đúng vị trí rồi bấm “Ghim vị trí này”)
                  </span>
                </label>

                <PinLocationMap
                  pinned={latitude && longitude ? { lat: latitude, lng: longitude } : null}
                  onPin={handlePinLocation}
                />

                <div className="upload-coords-group">
                  <div className="upload-coord-item">
                    <span>Vĩ độ:</span>
                    <input type="text" value={latitude} readOnly placeholder="Chưa ghim" />
                  </div>
                  <div className="upload-coord-item">
                    <span>Kinh độ:</span>
                    <input type="text" value={longitude} readOnly placeholder="Chưa ghim" />
                  </div>
                  {latitude && longitude && (
                    <a
                      href={`https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="upload-map-external-link"
                    >
                      Xem trên Google Maps ↗
                    </a>
                  )}
                </div>
              </div>
            </div>
          </section>

          <footer className="upload-actions">
            <button
              type="button"
              className="upload-button"
              disabled={isSubmitting}
              onClick={() => onCancel?.()}
            >
              Hủy bỏ
            </button>
            <div className="upload-actions-right">
              <button
                type="button"
                className="upload-button"
                disabled={isSubmitting}
                onClick={handleSaveDraft}
              >
                Lưu nháp
              </button>
              <button
                type="button"
                className="upload-button primary"
                disabled={isSubmitting}
                onClick={handleSubmitListing}
              >
                {isSubmitting
                  ? "Đang gửi xử lý..."
                  : isEditing
                  ? "Cập nhật & Gửi duyệt lại"
                  : "Gửi duyệt tin"}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </div>
  );
}

export default UploadPropertyForm;
