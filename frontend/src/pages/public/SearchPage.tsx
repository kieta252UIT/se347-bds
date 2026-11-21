import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router";
import locations from "@/data/locations.json";
import { PropertyCard } from "@/components/property/PropertyCard";
import { mockProperties } from "@/data/mockProperties";
import "./search-page.css";

type IconName =
  | "home"
  | "search"
  | "chevron"
  | "next"
  | "pin"
  | "area"
  | "bed"
  | "bath"
  | "heart"
  | "map"
  | "grid"
  | "list";

type Filters = {
  transaction: string;
  city: string;
  ward: string;
  propertyType: string;
  priceMin: string;
  priceMax: string;
  area: string;
  bedrooms: string;
  bathrooms: string;
};

type FilterKey = Exclude<keyof Filters, "priceMin" | "priceMax"> | "price";
type SortOrder = "newest" | "oldest" | "price-asc" | "price-desc";

type SearchQuery = {
  transaction?: string;
  city?: string;
  propertyType?: string;
  priceMin?: string;
  priceMax?: string;
};

function Icon({ name }: { name: IconName }) {
  const paths: Record<IconName, React.ReactNode> = {
    home: (
      <>
        <path d="m3 11 9-7 9 7" />
        <path d="M5 10v10h14V10M9 20v-6h6v6" />
      </>
    ),
    search: (
      <>
        <circle cx="10.8" cy="10.8" r="5.7" />
        <path d="m15.2 15.2 4 4" />
      </>
    ),
    chevron: <path d="m7 10 5 5 5-5" />,
    next: <path d="M5 12h14m-6-6 6 6-6 6" />,
    pin: (
      <>
        <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
        <circle cx="12" cy="10" r="2.5" />
      </>
    ),
    area: <path d="M5 9V5h4M15 5h4v4M19 15v4h-4M9 19H5v-4" />,
    bed: (
      <>
        <path d="M3 17v-6h18v6M5 17v2M19 17v2M3 14h18" />
        <path d="M5 11V8h5a3 3 0 0 1 3 3" />
      </>
    ),
    bath: (
      <>
        <path d="M4 12h16v4a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4v-4Z" />
        <path d="M6 12V6a2 2 0 0 1 4 0v1M4 20v1m16-1v1" />
      </>
    ),
    heart: (
      <path d="M20.8 8.5c0 5.5-8.8 10.4-8.8 10.4S3.2 14 3.2 8.5A4.3 4.3 0 0 1 11 6l1 1.2L13 6a4.3 4.3 0 0 1 7.8 2.5Z" />
    ),
    map: (
      <>
        <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z" />
        <path d="M9 3v15m6-12v15" />
      </>
    ),
    grid: (
      <>
        <rect x="4" y="4" width="6" height="6" rx="1" />
        <rect x="14" y="4" width="6" height="6" rx="1" />
        <rect x="4" y="14" width="6" height="6" rx="1" />
        <rect x="14" y="14" width="6" height="6" rx="1" />
      </>
    ),
    list: (
      <>
        <path d="M9 6h11M9 12h11M9 18h11" />
        <path d="M4 6h.01M4 12h.01M4 18h.01" />
      </>
    ),
  };

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
      {paths[name]}
    </svg>
  );
}

const properties = mockProperties;

const sponsoredPropertyIds = new Set([1, 3, 6, 9]);

const initialFilters: Filters = {
  transaction: "Tất cả",
  city: "Tất cả",
  ward: "Tất cả",
  propertyType: "Tất cả",
  priceMin: "0",
  priceMax: "100000000000",
  area: "Tất cả",
  bedrooms: "Tất cả",
  bathrooms: "Tất cả",
};

const transactionOptions = ["Tất cả", "Mua bán", "Cho thuê"];
const propertyTypeOptions = [
  "Tất cả",
  "Căn hộ chung cư",
  "Nhà riêng",
  "Đất nền",
  "Phòng trọ",
  "Khác",
];
const areaOptions = ["Tất cả", "Dưới 50 m²", "50 – 80 m²", "80 – 120 m²", "Trên 120 m²"];
const bedroomOptions = ["Tất cả", "1 phòng ngủ", "2 phòng ngủ", "3 phòng ngủ", "4+ phòng ngủ"];
const bathroomOptions = ["Tất cả", "1 phòng tắm", "2 phòng tắm", "3 phòng tắm", "4+ phòng tắm"];
const maxPrice = 100_000_000_000;

const formatFilterPrice = (vnd: number) => {
  for (const [unit, label] of [
    [1_000_000_000, "tỷ"],
    [1_000_000, "triệu"],
  ] as const) {
    const scaled = vnd / unit;
    if (scaled >= 1 && Number.isInteger(scaled * 100)) {
      return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(scaled)} ${label} VNĐ`;
    }
  }
  return `${vnd.toLocaleString("vi-VN")} VNĐ`;
};

function getInitialFilters(query?: SearchQuery): Filters {
  if (!query || !Object.values(query).some(Boolean)) return initialFilters;

  const requestedMin = query.priceMin === undefined ? 0 : Number(query.priceMin);
  const requestedMax = query.priceMax === undefined ? maxPrice : Number(query.priceMax);
  const requestedCity = query.city;
  const priceMin = Math.min(Math.max(requestedMin || 0, 0), maxPrice);
  const priceMax = Math.min(Math.max(requestedMax || 0, priceMin), maxPrice);

  return {
    ...initialFilters,
    transaction:
      query.transaction && transactionOptions.includes(query.transaction)
        ? query.transaction
        : "Tất cả",
    city:
      requestedCity &&
      (requestedCity === "Tất cả" ||
        locations.provinces.some(({ name }) => name === requestedCity))
        ? requestedCity
        : "Tất cả",
    propertyType:
      query.propertyType && propertyTypeOptions.includes(query.propertyType)
        ? query.propertyType
        : "Tất cả",
    ward: "Tất cả",
    priceMin: String(priceMin),
    priceMax: String(priceMax),
  };
}

const sortOptions: { value: SortOrder; label: string }[] = [
  { value: "newest", label: "Mới nhất" },
  { value: "oldest", label: "Cũ nhất" },
  { value: "price-asc", label: "Giá tăng dần" },
  { value: "price-desc", label: "Giá giảm dần" },
];

function matchesArea(value: number, range: string) {
  if (range === "Tất cả") return true;
  if (range === "Dưới 50 m²") return value < 50;
  if (range === "50 – 80 m²") return value >= 50 && value <= 80;
  if (range === "80 – 120 m²") return value > 80 && value <= 120;
  return value > 120;
}

function normalizeVietnamese(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

export function SearchPage() {
  const [searchParams] = useSearchParams();

  const initialQueryFromUrl = useMemo<SearchQuery>(
    () => ({
      transaction: searchParams.get("transaction") || undefined,
      city: searchParams.get("city") || undefined,
      propertyType: searchParams.get("propertyType") || undefined,
      priceMin: searchParams.get("priceMin") || undefined,
      priceMax: searchParams.get("priceMax") || undefined,
    }),
    [searchParams]
  );

  const [draftFilters, setDraftFilters] = useState(() =>
    getInitialFilters(initialQueryFromUrl)
  );
  const [appliedFilters, setAppliedFilters] = useState(() =>
    getInitialFilters(initialQueryFromUrl)
  );
  const [searchText, setSearchText] = useState("");
  const [appliedSearchText, setAppliedSearchText] = useState("");
  const [sortOrder, setSortOrder] = useState<SortOrder>("newest");
  const [sortOpen, setSortOpen] = useState(false);
  const [favorites, setFavorites] = useState<number[]>([]);
  const [sponsoredPage, setSponsoredPage] = useState(0);
  const [page, setPage] = useState(1);
  const [mapView, setMapView] = useState(false);
  const [layout, setLayout] = useState<"grid" | "list">("grid");
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null);
  const [citySearchText, setCitySearchText] = useState("");
  const [wardSearchText, setWardSearchText] = useState("");

  const filterPanelRef = useRef<HTMLElement>(null);
  const sortControlRef = useRef<HTMLDivElement>(null);
  const sponsoredViewportRef = useRef<HTMLDivElement>(null);

  const selectedProvince = locations.provinces.find(
    (province) => province.name === draftFilters.city
  );

  const cityOptions = useMemo(
    () => ["Tất cả", ...locations.provinces.map((province) => province.name)],
    []
  );

  const filteredCityOptions = useMemo(() => {
    if (!citySearchText.trim()) return cityOptions;
    const norm = normalizeVietnamese(citySearchText);
    return cityOptions.filter((option) =>
      normalizeVietnamese(option).includes(norm)
    );
  }, [cityOptions, citySearchText]);

  const wardOptions = useMemo(() => {
    if (selectedProvince) {
      return [
        "Tất cả",
        ...locations.wards
          .filter((ward) => ward.province_id === selectedProvince.id)
          .map((ward) => ward.name),
      ];
    }
    return ["Tất cả", ...locations.wards.map((ward) => ward.name)];
  }, [selectedProvince]);

  const filteredWardOptions = useMemo(() => {
    if (!wardSearchText.trim()) {
      if (!selectedProvince) {
        return ["Tất cả", ...locations.wards.slice(0, 30).map((w) => w.name)];
      }
      return wardOptions;
    }
    const norm = normalizeVietnamese(wardSearchText);
    return wardOptions
      .filter((name) => normalizeVietnamese(name).includes(norm))
      .slice(0, 60);
  }, [wardOptions, wardSearchText, selectedProvince]);

  const handleSelectWard = (wardName: string) => {
    if (wardName === "Tất cả") {
      selectFilterOption("ward", "Tất cả");
      setWardSearchText("");
      return;
    }
    const wardObj = locations.wards.find((w) => w.name === wardName);
    if (wardObj && (!selectedProvince || selectedProvince.id !== wardObj.province_id)) {
      const prov = locations.provinces.find((p) => p.id === wardObj.province_id);
      if (prov) {
        setDraftFilters((current) => ({
          ...current,
          city: prov.name,
          ward: wardName,
        }));
        setWardSearchText("");
        setActiveFilter(null);
        return;
      }
    }
    selectFilterOption("ward", wardName);
    setWardSearchText("");
  };

  const filterDefinitions: {
    key: FilterKey;
    label: string;
    value: string;
    options: string[];
  }[] = [
    {
      key: "transaction",
      label: "Nhu cầu",
      value: draftFilters.transaction,
      options: transactionOptions,
    },
    {
      key: "city",
      label: "Tỉnh / Thành phố",
      value: draftFilters.city,
      options: filteredCityOptions,
    },
    {
      key: "ward",
      label: "Xã / Phường",
      value: draftFilters.ward,
      options: filteredWardOptions,
    },
    {
      key: "propertyType",
      label: "Loại bất động sản",
      value: draftFilters.propertyType,
      options: propertyTypeOptions,
    },
    {
      key: "price",
      label: "Khoảng giá",
      value: `${formatFilterPrice(Number(draftFilters.priceMin) || 0)} - ${formatFilterPrice(
        Number(draftFilters.priceMax) || 0
      )}`,
      options: [],
    },
    { key: "area", label: "Diện tích", value: draftFilters.area, options: areaOptions },
    {
      key: "bedrooms",
      label: "Số phòng ngủ",
      value: draftFilters.bedrooms,
      options: bedroomOptions,
    },
    {
      key: "bathrooms",
      label: "Số phòng tắm",
      value: draftFilters.bathrooms,
      options: bathroomOptions,
    },
  ];

  const filteredProperties = useMemo(() => {
    const query = appliedSearchText.trim().toLocaleLowerCase("vi");
    const filtered = properties.filter((property) => {
      const matchesTransaction =
        appliedFilters.transaction === "Tất cả" ||
        property.transaction === appliedFilters.transaction;
      const matchesCity =
        appliedFilters.city === "Tất cả" || property.city === appliedFilters.city;
      const matchesWard =
        appliedFilters.ward === "Tất cả" || property.ward === appliedFilters.ward;
      const matchesType =
        appliedFilters.propertyType === "Tất cả" ||
        property.type === appliedFilters.propertyType;
      const matchesBedrooms =
        appliedFilters.bedrooms === "Tất cả" ||
        property.beds === Number.parseInt(appliedFilters.bedrooms, 10) ||
        (appliedFilters.bedrooms === "4+ phòng ngủ" && property.beds >= 4);
      const matchesBathrooms =
        appliedFilters.bathrooms === "Tất cả" ||
        property.baths === Number.parseInt(appliedFilters.bathrooms, 10) ||
        (appliedFilters.bathrooms === "4+ phòng tắm" && property.baths >= 4);
      const minPriceFilter = Number(appliedFilters.priceMin) || 0;
      const maxPriceFilter = Number(appliedFilters.priceMax) || maxPrice;
      const matchesQuery =
        !query ||
        `${property.title} ${property.place} ${property.type}`
          .toLocaleLowerCase("vi")
          .includes(query);

      return (
        matchesTransaction &&
        matchesCity &&
        matchesWard &&
        matchesType &&
        property.priceValue >= minPriceFilter &&
        property.priceValue <= maxPriceFilter &&
        matchesArea(property.size, appliedFilters.area) &&
        matchesBedrooms &&
        matchesBathrooms &&
        matchesQuery
      );
    });

    return sortOrder === "price-asc"
      ? [...filtered].sort((a, b) => a.priceValue - b.priceValue)
      : sortOrder === "price-desc"
      ? [...filtered].sort((a, b) => b.priceValue - a.priceValue)
      : sortOrder === "oldest"
      ? [...filtered].sort((a, b) => b.id - a.id)
      : [...filtered].sort((a, b) => a.id - b.id);
  }, [appliedFilters, appliedSearchText, sortOrder]);

  const pageSize = 8;
  const sponsoredProperties = filteredProperties.filter((property) =>
    sponsoredPropertyIds.has(property.id)
  );
  const regularProperties = filteredProperties.filter(
    (property) => !sponsoredPropertyIds.has(property.id)
  );
  const sponsoredPageSize = 2;
  const sponsoredLastPage = Math.max(0, sponsoredProperties.length - sponsoredPageSize);
  const pageCount = Math.max(1, Math.ceil(regularProperties.length / pageSize));
  const visibleProperties = regularProperties.slice(
    (page - 1) * pageSize,
    page * pageSize
  );

  useEffect(() => {
    const target = sponsoredViewportRef.current?.children[sponsoredPage] as
      | HTMLElement
      | undefined;
    if (target) {
      sponsoredViewportRef.current?.scrollTo({
        left: target.offsetLeft,
        behavior: "smooth",
      });
    }
  }, [sponsoredPage]);

  useEffect(() => {
    if (!activeFilter && !sortOpen) return;

    const closeOnOutsideClick = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!filterPanelRef.current?.contains(target)) setActiveFilter(null);
      if (!sortControlRef.current?.contains(target)) setSortOpen(false);
    };

    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [activeFilter, sortOpen]);

  const applySearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAppliedFilters(draftFilters);
    setAppliedSearchText(searchText);
    setPage(1);
    setSponsoredPage(0);
  };

  const clearFilters = () => {
    setDraftFilters({ ...initialFilters, city: "Tất cả" });
    setAppliedFilters({ ...initialFilters, city: "Tất cả" });
    setSearchText("");
    setAppliedSearchText("");
    setPage(1);
    setSponsoredPage(0);
  };

  const activeFilterKeys = (Object.keys(appliedFilters) as (keyof Filters)[]).filter(
    (key): key is Exclude<keyof Filters, "priceMin" | "priceMax"> =>
      key !== "priceMin" && key !== "priceMax" && appliedFilters[key] !== "Tất cả"
  );
  const activeChips: { key: FilterKey; label: string }[] = activeFilterKeys.map((key) => ({
    key,
    label: appliedFilters[key],
  }));
  if (
    appliedFilters.priceMin !== initialFilters.priceMin ||
    appliedFilters.priceMax !== initialFilters.priceMax
  ) {
    activeChips.push({
      key: "price",
      label: `${formatFilterPrice(Number(appliedFilters.priceMin) || 0)} - ${formatFilterPrice(
        Number(appliedFilters.priceMax) || 0
      )}`,
    });
  }

  const removeChip = (key: FilterKey) => {
    if (key === "price") {
      const next = {
        ...appliedFilters,
        priceMin: initialFilters.priceMin,
        priceMax: initialFilters.priceMax,
      };
      setDraftFilters(next);
      setAppliedFilters(next);
      setPage(1);
      setSponsoredPage(0);
      return;
    }
    const next = { ...appliedFilters, [key]: "Tất cả" };
    if (key === "city") next.ward = "Tất cả";
    setDraftFilters(next);
    setAppliedFilters(next);
    setPage(1);
    setSponsoredPage(0);
  };

  const selectFilterOption = (key: Exclude<FilterKey, "price">, value: string) => {
    setDraftFilters((current) => ({
      ...current,
      [key]: value,
      ...(key === "city" ? { ward: "Tất cả" } : {}),
    }));
    setActiveFilter(null);
  };

  const toggleFavorite = (id: number) => {
    setFavorites((current) =>
      current.includes(id)
        ? current.filter((favorite) => favorite !== id)
        : [...current, id]
    );
  };

  return (
    <div className="sp-content">
      <div className="sp-title-row">
        <div>
          <h1>Tìm bất động sản phù hợp</h1>
        </div>
        <button
          type="button"
          className="sp-map-button"
          onClick={() => setMapView((value) => !value)}
          aria-pressed={mapView}
        >
          <Icon name={mapView ? "grid" : "map"} />
          {mapView ? "Xem danh sách" : "Xem bản đồ"}
        </button>
      </div>

      <form className="sp-search-bar" onSubmit={applySearch}>
        <Icon name="search" />
        <input
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="Nhập khu vực, tên dự án hoặc từ khóa bất động sản..."
          aria-label="Tìm khu vực, tên dự án hoặc từ khóa"
        />
        <button type="submit">
          <Icon name="search" />
          Tìm kiếm
        </button>
      </form>

      <div className="sp-chip-row" aria-label="Bộ lọc đang áp dụng">
        {activeChips.map(({ key, label }) => (
          <button
            type="button"
            className="sp-chip"
            key={key}
            onClick={() => removeChip(key)}
          >
            {label}
            <span aria-hidden="true">×</span>
          </button>
        ))}
        {activeChips.length > 0 && (
          <button type="button" className="sp-clear-button" onClick={clearFilters}>
            Xóa tất cả
          </button>
        )}
      </div>

      <div className="sp-results-layout">
        <aside ref={filterPanelRef} className="sp-sidebar" aria-label="Bộ lọc tìm kiếm">
          <h2>Bộ lọc tìm kiếm</h2>
          {filterDefinitions.map(({ key, label, value, options }) => (
            <div className="sp-filter-field" key={key}>
              <button
                type="button"
                className={`sp-filter-toggle ${activeFilter === key ? "active" : ""}`}
                onClick={() => setActiveFilter(activeFilter === key ? null : key)}
                aria-expanded={activeFilter === key}
                aria-label={`${label}: ${value}`}
              >
                <small>{label}</small>
                <strong>{value}</strong>
                <Icon name="chevron" />
              </button>

              {activeFilter === key && (
                <div className="sp-filter-menu" role="group" aria-label={label}>
                  {key === "price" ? (
                    <div className="sp-price-input-wrap">
                      <label className="sp-price-input-control">
                        <span>Từ (VNĐ)</span>
                        <input
                          type="text"
                          inputMode="numeric"
                          pattern="[0-9]*"
                          value={draftFilters.priceMin}
                          onChange={(event) => {
                            const digits = event.target.value.replace(/\D/g, "");
                            const amount = Math.min(Number(digits) || 0, maxPrice);
                            setDraftFilters((current) => ({
                              ...current,
                              priceMin: String(
                                Math.min(amount, Number(current.priceMax) || 0)
                              ),
                            }));
                          }}
                          aria-label="Giá thấp nhất, đơn vị VNĐ"
                        />
                      </label>
                      <label className="sp-price-input-control">
                        <span>Đến (VNĐ)</span>
                        <input
                          type="text"
                          inputMode="numeric"
                          pattern="[0-9]*"
                          value={draftFilters.priceMax}
                          onChange={(event) => {
                            const digits = event.target.value.replace(/\D/g, "");
                            const amount = Math.min(Number(digits) || 0, maxPrice);
                            setDraftFilters((current) => ({
                              ...current,
                              priceMax: String(
                                Math.max(amount, Number(current.priceMin) || 0)
                              ),
                            }));
                          }}
                          aria-label="Giá cao nhất, đơn vị VNĐ"
                        />
                      </label>
                    </div>
                  ) : (
                    <>
                      {key === "city" && (
                        <div
                          className="sp-filter-search-box"
                          onClick={(event) => event.stopPropagation()}
                        >
                          <input
                            type="text"
                            placeholder="Tìm tỉnh, thành phố..."
                            value={citySearchText}
                            onChange={(event) => setCitySearchText(event.target.value)}
                            className="sp-filter-search-input"
                            aria-label="Tìm kiếm tỉnh, thành phố"
                            autoFocus
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                event.preventDefault();
                                if (filteredCityOptions.length > 0) {
                                  const match =
                                    filteredCityOptions.find((o) => o !== "Tất cả") ||
                                    filteredCityOptions[0];
                                  selectFilterOption("city", match);
                                  setCitySearchText("");
                                }
                              }
                            }}
                          />
                          <button
                            type="button"
                            className="sp-filter-search-btn"
                            aria-label="Tìm kiếm"
                            tabIndex={-1}
                          >
                            <Icon name="search" />
                          </button>
                        </div>
                      )}

                      {key === "ward" && (
                        <div
                          className="sp-filter-search-box"
                          onClick={(event) => event.stopPropagation()}
                        >
                          <input
                            type="text"
                            placeholder="Tìm xã, phường..."
                            value={wardSearchText}
                            onChange={(event) => setWardSearchText(event.target.value)}
                            className="sp-filter-search-input"
                            aria-label="Tìm kiếm xã, phường"
                            autoFocus
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                event.preventDefault();
                                if (filteredWardOptions.length > 0) {
                                  const match =
                                    filteredWardOptions.find((o) => o !== "Tất cả") ||
                                    filteredWardOptions[0];
                                  handleSelectWard(match);
                                }
                              }
                            }}
                          />
                          <button
                            type="button"
                            className="sp-filter-search-btn"
                            aria-label="Tìm kiếm"
                            tabIndex={-1}
                          >
                            <Icon name="search" />
                          </button>
                        </div>
                      )}

                      <div className="sp-filter-options">
                        {options.map((option) => (
                          <button
                            key={option}
                            type="button"
                            className={`sp-filter-option ${
                              option === value ? "active" : ""
                            }`}
                            onClick={() => {
                              if (key === "ward") {
                                handleSelectWard(option);
                              } else {
                                selectFilterOption(key, option);
                                if (key === "city") setCitySearchText("");
                              }
                            }}
                          >
                            {option}
                          </button>
                        ))}
                        {options.length === 0 && (
                          <div className="sp-filter-empty">
                            Không tìm thấy kết quả phù hợp
                          </div>
                        )}
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>
          ))}

          <button
            type="button"
            className="sp-apply-button"
            onClick={() => {
              setAppliedFilters(draftFilters);
              setPage(1);
              setSponsoredPage(0);
            }}
          >
            Áp dụng bộ lọc
          </button>
        </aside>

        <section className="sp-results" aria-label="Kết quả tìm kiếm">
          {sponsoredProperties.length > 0 && (
            <section className="sp-sponsored-section" aria-label="Tin được tài trợ">
              <div className="sp-sponsored-header">
                <div>
                  <p className="sp-sponsored-eyebrow">Tin được tài trợ</p>
                  <h2 className="sp-sponsored-title">
                    Lựa chọn đang được ưu tiên hiển thị
                  </h2>
                </div>
                <div className="sp-sponsored-actions">
                  <p className="sp-sponsored-disclosure">Vị trí quảng bá trả phí</p>
                  {sponsoredProperties.length > sponsoredPageSize && (
                    <div
                      className="sp-sponsored-navigation"
                      role="group"
                      aria-label="Điều hướng tin được tài trợ"
                    >
                      <button
                        type="button"
                        className="sp-sponsored-arrow"
                        onClick={() =>
                          setSponsoredPage((current) => Math.max(0, current - 1))
                        }
                        disabled={sponsoredPage === 0}
                        aria-label="Xem tin được tài trợ trước"
                        title="Xem tin trước"
                      >
                        <Icon name="next" />
                      </button>
                      <button
                        type="button"
                        className="sp-sponsored-arrow"
                        onClick={() =>
                          setSponsoredPage((current) =>
                            Math.min(sponsoredLastPage, current + 1)
                          )
                        }
                        disabled={sponsoredPage === sponsoredLastPage}
                        aria-label="Xem tin được tài trợ tiếp theo"
                        title="Xem tin tiếp theo"
                      >
                        <Icon name="next" />
                      </button>
                    </div>
                  )}
                </div>
              </div>

              <div
                ref={sponsoredViewportRef}
                className="sp-sponsored-viewport"
                aria-live="polite"
              >
                {sponsoredProperties.map((property) => (
                  <PropertyCard
                    key={`sponsored-${property.id}`}
                    property={property}
                    isSponsored
                    tag="Được tài trợ"
                    isFavorite={favorites.includes(property.id)}
                    onToggleFavorite={toggleFavorite}
                  />
                ))}
              </div>
            </section>
          )}

          <div className="sp-results-toolbar">
            <div className="sp-results-heading">
              <strong className="sp-result-count">
                {filteredProperties.length.toLocaleString("vi-VN")} kết quả
              </strong>
            </div>

            <div className="sp-result-controls">
              <div className="sp-sort-control" ref={sortControlRef}>
                <button
                  type="button"
                  className="sp-sort-button"
                  onClick={() => setSortOpen((open) => !open)}
                  aria-expanded={sortOpen}
                  aria-haspopup="listbox"
                >
                  {sortOptions.find((option) => option.value === sortOrder)?.label}
                  <Icon name="chevron" />
                </button>

                {sortOpen && (
                  <div
                    className="sp-sort-menu"
                    role="listbox"
                    aria-label="Sắp xếp bất động sản"
                  >
                    {sortOptions.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        role="option"
                        aria-selected={sortOrder === option.value}
                        className={`sp-filter-option ${
                          sortOrder === option.value ? "active" : ""
                        }`}
                        onClick={() => {
                          setSortOrder(option.value);
                          setPage(1);
                          setSponsoredPage(0);
                          setSortOpen(false);
                        }}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div
                className="sp-layout-toggle"
                role="group"
                aria-label="Kiểu hiển thị bất động sản"
              >
                <button
                  type="button"
                  className={layout === "grid" ? "active" : ""}
                  onClick={() => setLayout("grid")}
                  aria-label="Hiển thị dạng lưới"
                  aria-pressed={layout === "grid"}
                >
                  <Icon name="grid" />
                </button>
                <button
                  type="button"
                  className={layout === "list" ? "active" : ""}
                  onClick={() => setLayout("list")}
                  aria-label="Hiển thị dạng danh sách"
                  aria-pressed={layout === "list"}
                >
                  <Icon name="list" />
                </button>
              </div>
            </div>
          </div>

          {mapView ? (
            <div
              className="sp-map-panel"
              role="region"
              aria-label="Bản đồ vị trí bất động sản"
            >
              <div className="sp-map-road" />
              <div className="sp-map-marker sp-marker-one">
                <strong>4,2 tỷ</strong>
                <span>Thủ Đức</span>
              </div>
              <div className="sp-map-marker sp-marker-two">
                <strong>8,9 tỷ</strong>
                <span>Bình Thạnh</span>
              </div>
              <div className="sp-map-marker sp-marker-three">
                <strong>18 tr/th</strong>
                <span>An Phú</span>
              </div>
              <p>
                <Icon name="map" /> Vị trí hiển thị minh họa theo tin đăng
              </p>
            </div>
          ) : visibleProperties.length > 0 ? (
            <div
              className={`sp-property-grid ${
                layout === "list" ? "sp-property-list" : ""
              }`}
            >
              {visibleProperties.map((property) => (
                <PropertyCard
                  key={property.id}
                  property={property}
                  isFavorite={favorites.includes(property.id)}
                  onToggleFavorite={toggleFavorite}
                />
              ))}
            </div>
          ) : regularProperties.length > 0 ? (
            <div className="sp-empty-state">
              <span>
                <Icon name="search" />
              </span>
              <h2>Chưa tìm thấy bất động sản phù hợp</h2>
              <p>Thử thay đổi từ khóa hoặc nới rộng bộ lọc để xem thêm kết quả.</p>
              <button type="button" onClick={clearFilters}>
                Xóa bộ lọc
              </button>
            </div>
          ) : filteredProperties.length > 0 ? (
            <div className="sp-no-regular-results">
              Các tin phù hợp hiện đang được hiển thị trong mục tài trợ phía trên.
            </div>
          ) : (
            <div className="sp-empty-state">
              <span>
                <Icon name="search" />
              </span>
              <h2>Chưa tìm thấy bất động sản phù hợp</h2>
              <p>Thử thay đổi từ khóa hoặc nới rộng bộ lọc để xem thêm kết quả.</p>
              <button type="button" onClick={clearFilters}>
                Xóa bộ lọc
              </button>
            </div>
          )}

          {!mapView && regularProperties.length > pageSize && (
            <nav className="sp-pagination" aria-label="Phân trang kết quả">
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
                    type="button"
                    key={pageNumber}
                    className={page === pageNumber ? "current" : ""}
                    onClick={() => setPage(pageNumber)}
                    aria-current={page === pageNumber ? "page" : undefined}
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
                aria-label="Trang sau"
              >
                ›
              </button>
            </nav>
          )}
        </section>
      </div>

      {/* Mục Gợi ý cho bạn */}
      <section className="sp-recommendations">
        <p className="sp-eyebrow">Gợi ý cho bạn</p>
        <h2>Có thể bạn sẽ thích</h2>
        <p>Dựa trên lịch sử xem và tin yêu thích của bạn.</p>
        <div className="sp-recommendation-grid">
          {properties.slice(0, 3).map((property) => (
            <PropertyCard
              key={`suggested-${property.id}`}
              property={property}
              tag="Gợi ý cho bạn"
              isFavorite={favorites.includes(property.id)}
              onToggleFavorite={toggleFavorite}
            />
          ))}
        </div>
      </section>
    </div>
  );
}
