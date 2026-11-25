import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import locations from "@/data/locations.json";
import { PropertyCard, type PropertyItem } from "@/components/property/PropertyCard";
import { useAuth } from "@/hooks/useAuth";
import {
  mapTinTomTatToPropertyItem,
  searchService,
} from "@/services/searchService";
import { favoriteService } from "@/services/favoriteService";
import type {
  CheDoGia,
  SapXepTimKiem,
  SearchQueryParams,
} from "@/types/search";
import type { LoaiBds, LoaiGiaoDich } from "@/utils/priceFormatter";
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
  priceMode: CheDoGia;
  priceMin: string;
  priceMax: string;
  area: string;
};

type FilterKey =
  | "transaction"
  | "city"
  | "ward"
  | "propertyType"
  | "price"
  | "area";

type SearchQuery = {
  transaction?: string;
  city?: string;
  propertyType?: string;
  priceMode?: string;
  priceMin?: string;
  priceMax?: string;
  tuKhoa?: string;
};

type MapBounds = {
  nam: number;
  bac: number;
  tay: number;
  dong: number;
  label: string;
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

const initialFilters: Filters = {
  transaction: "Tất cả",
  city: "Tất cả",
  ward: "Tất cả",
  propertyType: "Tất cả",
  priceMode: "tong",
  priceMin: "",
  priceMax: "",
  area: "Tất cả",
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
const areaOptions = [
  "Tất cả",
  "Dưới 50 m²",
  "50 – 80 m²",
  "80 – 120 m²",
  "Trên 120 m²",
];
const maxPrice = 100_000_000_000;

/**
 * Hai chế độ sắp xếp hợp lệ trong Sprint 1 theo TK-08 & openapi-sprint-1.yaml
 */
const sortOptions: { value: SapXepTimKiem; label: string }[] = [
  { value: "mac_dinh", label: "Phù hợp nhất" },
  { value: "moi_nhat", label: "Mới nhất" },
];

const MAP_BOUNDS_PRESETS: MapBounds[] = [
  {
    label: "Khu vực TP. Hồ Chí Minh",
    nam: 10.65,
    bac: 10.92,
    tay: 106.58,
    dong: 106.88,
  },
  {
    label: "Khu vực Hà Nội",
    nam: 20.95,
    bac: 21.12,
    tay: 105.72,
    dong: 105.92,
  },
  {
    label: "Khu vực Đà Nẵng",
    nam: 15.98,
    bac: 16.12,
    tay: 108.15,
    dong: 108.28,
  },
];

const formatFilterPrice = (vnd: number, mode: CheDoGia = "tong") => {
  const suffix = mode === "m2" ? "/m²" : "";
  for (const [unit, label] of [
    [1_000_000_000, "tỷ"],
    [1_000_000, "triệu"],
  ] as const) {
    const scaled = vnd / unit;
    if (scaled >= 1 && Number.isInteger(scaled * 100)) {
      return `${new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: 2,
      }).format(scaled)} ${label}${suffix}`;
    }
  }
  return `${vnd.toLocaleString("vi-VN")} VNĐ${suffix}`;
};

function formatPriceRangeLabel(
  priceMin: string,
  priceMax: string,
  priceMode: CheDoGia
): string {
  const hasMin = Boolean(priceMin.trim());
  const hasMax = Boolean(priceMax.trim());
  if (!hasMin && !hasMax) {
    return priceMode === "m2" ? "Tất cả (theo m²)" : "Tất cả mức giá";
  }
  const minVal = Number(priceMin) || 0;
  const maxVal = Number(priceMax) || 0;
  if (hasMin && hasMax) {
    return `${formatFilterPrice(minVal, priceMode)} - ${formatFilterPrice(
      maxVal,
      priceMode
    )}`;
  }
  if (hasMin) {
    return `Từ ${formatFilterPrice(minVal, priceMode)}`;
  }
  return `Đến ${formatFilterPrice(maxVal, priceMode)}`;
}

function getInitialFilters(query?: SearchQuery): Filters {
  if (!query || !Object.values(query).some(Boolean)) return initialFilters;

  const transaction =
    query.transaction && transactionOptions.includes(query.transaction)
      ? query.transaction
      : "Tất cả";

  const requestedCity = query.city;
  const city =
    requestedCity &&
    (requestedCity === "Tất cả" ||
      locations.provinces.some(({ name }) => name === requestedCity))
      ? requestedCity
      : "Tất cả";

  const propertyType =
    query.propertyType && propertyTypeOptions.includes(query.propertyType)
      ? query.propertyType
      : "Tất cả";

  const priceMode: CheDoGia = query.priceMode === "m2" ? "m2" : "tong";

  // TK-03: Chỉ nhận cận giá khi đã chọn loại giao dịch
  let priceMin = "";
  let priceMax = "";
  if (transaction !== "Tất cả") {
    const rawMin = query.priceMin ? Number(query.priceMin) : NaN;
    const rawMax = query.priceMax ? Number(query.priceMax) : NaN;
    const hasCustomRange =
      (!Number.isNaN(rawMin) && rawMin > 0) ||
      (!Number.isNaN(rawMax) && rawMax > 0 && rawMax < maxPrice);

    if (hasCustomRange) {
      if (!Number.isNaN(rawMin) && rawMin > 0) {
        priceMin = String(Math.min(Math.max(rawMin, 0), maxPrice));
      }
      if (!Number.isNaN(rawMax) && rawMax > 0) {
        priceMax = String(Math.min(Math.max(rawMax, 0), maxPrice));
      }
    }
  }

  return {
    ...initialFilters,
    transaction,
    city,
    ward: "Tất cả",
    propertyType,
    priceMode,
    priceMin,
    priceMax,
  };
}

function mapTransactionToApi(transaction: string): LoaiGiaoDich | undefined {
  if (transaction === "Mua bán") return "ban";
  if (transaction === "Cho thuê") return "cho_thue";
  return undefined;
}

function mapPropertyTypeToApi(propertyType: string): LoaiBds | undefined {
  switch (propertyType) {
    case "Căn hộ chung cư":
      return "can_ho";
    case "Nhà riêng":
      return "nha_rieng";
    case "Đất nền":
      return "dat";
    case "Phòng trọ":
      return "phong_tro";
    case "Khác":
      return "khac";
    default:
      return undefined;
  }
}

function mapAreaToApi(area: string): {
  dienTichMin?: string;
  dienTichMax?: string;
} {
  if (area === "Dưới 50 m²") return { dienTichMax: "50" };
  if (area === "50 – 80 m²") return { dienTichMin: "50", dienTichMax: "80" };
  if (area === "80 – 120 m²") return { dienTichMin: "80", dienTichMax: "120" };
  if (area === "Trên 120 m²") return { dienTichMin: "120" };
  return {};
}

function mapKhuVucToApi(city: string, ward: string): string | undefined {
  if (ward !== "Tất cả") {
    return ward;
  }
  if (city !== "Tất cả") {
    return city.replace(/^(Thành phố|Tỉnh)\s+/i, "").trim();
  }
  return undefined;
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
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const initialQueryFromUrl = useMemo<SearchQuery>(
    () => ({
      transaction: searchParams.get("transaction") || undefined,
      city: searchParams.get("city") || undefined,
      propertyType: searchParams.get("propertyType") || undefined,
      priceMode: searchParams.get("cheDoGia") || undefined,
      priceMin: searchParams.get("priceMin") || undefined,
      priceMax: searchParams.get("priceMax") || undefined,
      tuKhoa: searchParams.get("tuKhoa") || undefined,
    }),
    [searchParams]
  );

  const [draftFilters, setDraftFilters] = useState(() =>
    getInitialFilters(initialQueryFromUrl)
  );
  const [appliedFilters, setAppliedFilters] = useState(() =>
    getInitialFilters(initialQueryFromUrl)
  );
  const [searchText, setSearchText] = useState(
    () => initialQueryFromUrl.tuKhoa || ""
  );
  const [appliedSearchText, setAppliedSearchText] = useState(
    () => initialQueryFromUrl.tuKhoa || ""
  );
  const [sortOrder, setSortOrder] = useState<SapXepTimKiem>("mac_dinh");
  const [sortOpen, setSortOpen] = useState(false);
  const [favorites, setFavorites] = useState<(string | number)[]>([]);
  const [sponsoredPage, setSponsoredPage] = useState(0);
  const [page, setPage] = useState(1);
  const [mapView, setMapView] = useState(false);
  const [mapBounds, setMapBounds] = useState<MapBounds | null>(null);
  const [layout, setLayout] = useState<"grid" | "list">("grid");
  const [activeFilter, setActiveFilter] = useState<FilterKey | null>(null);
  const [citySearchText, setCitySearchText] = useState("");
  const [wardSearchText, setWardSearchText] = useState("");

  // State kết quả từ API GET /api/listings
  const [apiSponsored, setApiSponsored] = useState<PropertyItem[]>([]);
  const [apiRegular, setApiRegular] = useState<PropertyItem[]>([]);
  const [apiTotalRegular, setApiTotalRegular] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [apiError, setApiError] = useState<string | null>(null);

  const filterPanelRef = useRef<HTMLElement>(null);
  const sortControlRef = useRef<HTMLDivElement>(null);
  const sponsoredViewportRef = useRef<HTMLDivElement>(null);
  const requestSeqRef = useRef<number>(0);

  const pageSize = 8;

  // Đồng bộ đầy đủ danh sách yêu thích (kể cả ngoài 50 mục đầu - M14) từ GET /api/me/favorites khi đã đăng nhập
  useEffect(() => {
    if (!isAuthenticated) return;
    let active = true;
    favoriteService
      .getAllFavoriteIds()
      .then((ids) => {
        if (!active) return;
        setFavorites((prev) => {
          const demoNumeric = prev.filter((id) => typeof id === "number");
          return [...demoNumeric, ...ids];
        });
      })
      .catch(() => {
        // Bỏ qua nếu chưa có dữ liệu hoặc lỗi mạng
      });
    return () => {
      active = false;
    };
  }, [isAuthenticated]);

  // Gọi API GET /api/listings với AbortController & requestSeqRef (TK-11)
  useEffect(() => {
    const controller = new AbortController();
    const currentSeq = ++requestSeqRef.current;

    const areaBounds = mapAreaToApi(appliedFilters.area);
    const loaiGiaoDich = mapTransactionToApi(appliedFilters.transaction);

    const queryParams: SearchQueryParams = {
      tuKhoa: appliedSearchText.trim() || undefined,
      loaiGiaoDich,
      loaiBds: mapPropertyTypeToApi(appliedFilters.propertyType),
      khuVuc: mapKhuVucToApi(appliedFilters.city, appliedFilters.ward),
      cheDoGia: loaiGiaoDich ? appliedFilters.priceMode : undefined,
      giaMin: loaiGiaoDich ? appliedFilters.priceMin || undefined : undefined,
      giaMax: loaiGiaoDich ? appliedFilters.priceMax || undefined : undefined,
      dienTichMin: areaBounds.dienTichMin,
      dienTichMax: areaBounds.dienTichMax,
      nam: mapBounds?.nam,
      bac: mapBounds?.bac,
      tay: mapBounds?.tay,
      dong: mapBounds?.dong,
      sapXep: sortOrder,
      trang: Math.max(0, page - 1),
      kichThuoc: pageSize,
    };

    setIsLoading(true);
    setApiError(null);

    searchService
      .searchListings(queryParams, controller.signal)
      .then((data) => {
        if (currentSeq !== requestSeqRef.current) return;
        setApiSponsored(data.taiTro.map(mapTinTomTatToPropertyItem));
        setApiRegular(data.tuNhien.noiDung.map(mapTinTomTatToPropertyItem));
        setApiTotalRegular(data.tuNhien.tongSo);
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted || currentSeq !== requestSeqRef.current) {
          return;
        }
        const message = (
          err as { response?: { data?: { message?: string } } }
        )?.response?.data?.message;

        setApiError(
          message || "Không thể tải kết quả tìm kiếm từ máy chủ. Vui lòng thử lại."
        );
        setIsLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, [
    appliedFilters,
    appliedSearchText,
    sortOrder,
    page,
    mapBounds,
  ]);

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
    if (
      wardObj &&
      (!selectedProvince || selectedProvince.id !== wardObj.province_id)
    ) {
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
      label:
        draftFilters.priceMode === "m2"
          ? "Khoảng giá (Đơn giá / m²)"
          : "Khoảng giá (Tổng giá)",
      value: formatPriceRangeLabel(
        draftFilters.priceMin,
        draftFilters.priceMax,
        draftFilters.priceMode
      ),
      options: [],
    },
    {
      key: "area",
      label: "Diện tích",
      value: draftFilters.area,
      options: areaOptions,
    },
  ];

  const sponsoredProperties: PropertyItem[] = apiSponsored.slice(0, 5);
  const regularTotalCount: number = apiTotalRegular;
  const visibleProperties: PropertyItem[] = apiRegular;

  const sponsoredPageSize = 2;
  const sponsoredLastPage = Math.max(
    0,
    sponsoredProperties.length - sponsoredPageSize
  );
  const pageCount = Math.max(1, Math.ceil(regularTotalCount / pageSize));

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
    return () =>
      document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [activeFilter, sortOpen]);

  const applySearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setAppliedFilters(draftFilters);
    setAppliedSearchText(searchText);
    setPage(1);
    setSponsoredPage(0);
  };

  const clearFilters = () => {
    setDraftFilters({ ...initialFilters });
    setAppliedFilters({ ...initialFilters });
    setSearchText("");
    setAppliedSearchText("");
    setMapBounds(null);
    setPage(1);
    setSponsoredPage(0);
  };

  const activeChips: { key: FilterKey | "bounds"; label: string }[] = [];
  if (appliedFilters.transaction !== "Tất cả") {
    activeChips.push({
      key: "transaction",
      label: appliedFilters.transaction,
    });
  }
  if (appliedFilters.city !== "Tất cả") {
    activeChips.push({ key: "city", label: appliedFilters.city });
  }
  if (appliedFilters.ward !== "Tất cả") {
    activeChips.push({ key: "ward", label: appliedFilters.ward });
  }
  if (appliedFilters.propertyType !== "Tất cả") {
    activeChips.push({
      key: "propertyType",
      label: appliedFilters.propertyType,
    });
  }
  if (
    appliedFilters.transaction !== "Tất cả" &&
    (appliedFilters.priceMin.trim() !== "" ||
      appliedFilters.priceMax.trim() !== "")
  ) {
    activeChips.push({
      key: "price",
      label: formatPriceRangeLabel(
        appliedFilters.priceMin,
        appliedFilters.priceMax,
        appliedFilters.priceMode
      ),
    });
  }
  if (appliedFilters.area !== "Tất cả") {
    activeChips.push({ key: "area", label: appliedFilters.area });
  }
  if (mapBounds) {
    activeChips.push({
      key: "bounds",
      label: `Khung bản đồ: ${mapBounds.label}`,
    });
  }

  const removeChip = (key: FilterKey | "bounds") => {
    if (key === "bounds") {
      setMapBounds(null);
      setPage(1);
      return;
    }
    if (key === "price") {
      const next = {
        ...appliedFilters,
        priceMin: "",
        priceMax: "",
      };
      setDraftFilters(next);
      setAppliedFilters(next);
      setPage(1);
      setSponsoredPage(0);
      return;
    }
    const next = { ...appliedFilters, [key]: "Tất cả" };
    if (key === "city") next.ward = "Tất cả";
    if (key === "transaction") {
      next.priceMin = "";
      next.priceMax = "";
    }
    setDraftFilters(next);
    setAppliedFilters(next);
    setPage(1);
    setSponsoredPage(0);
  };

  const selectFilterOption = (
    key: Exclude<FilterKey, "price">,
    value: string
  ) => {
    setDraftFilters((current) => ({
      ...current,
      [key]: value,
      ...(key === "city" ? { ward: "Tất cả" } : {}),
      ...(key === "transaction" && value === "Tất cả"
        ? { priceMin: "", priceMax: "" }
        : {}),
    }));
    setActiveFilter(null);
  };

  const toggleFavorite = async (id: string | number) => {
    const exists = favorites.includes(id);

    if (typeof id === "string") {
      if (!isAuthenticated) {
        navigate("/dang-nhap", {
          state: { from: { pathname: "/tim-kiem" } },
        });
        return;
      }

      setFavorites((current) =>
        exists ? current.filter((item) => item !== id) : [...current, id]
      );
      try {
        if (exists) {
          await favoriteService.removeFavorite(id);
        } else {
          await favoriteService.addFavorite(id);
        }
      } catch {
        // Hoàn tác nếu API lỗi
        setFavorites((current) =>
          exists ? [...current, id] : current.filter((item) => item !== id)
        );
      }
      return;
    }

    setFavorites((current) =>
      exists ? current.filter((item) => item !== id) : [...current, id]
    );
  };

  const mapMarkers = useMemo(() => {
    const combined = [...sponsoredProperties, ...visibleProperties];
    return combined.slice(0, 8);
  }, [sponsoredProperties, visibleProperties]);

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
          <button
            type="button"
            className="sp-clear-button"
            onClick={clearFilters}
          >
            Xóa tất cả
          </button>
        )}
      </div>

      <div className="sp-results-layout">
        <aside
          ref={filterPanelRef}
          className="sp-sidebar"
          aria-label="Bộ lọc tìm kiếm"
        >
          <h2>Bộ lọc tìm kiếm</h2>
          {filterDefinitions.map(({ key, label, value, options }) => (
            <div className="sp-filter-field" key={key}>
              <button
                type="button"
                className={`sp-filter-toggle ${
                  activeFilter === key ? "active" : ""
                }`}
                onClick={() =>
                  setActiveFilter(activeFilter === key ? null : key)
                }
                aria-expanded={activeFilter === key}
                aria-label={`${label}: ${value}`}
              >
                <small>{label}</small>
                <strong>{value}</strong>
                <Icon name="chevron" />
              </button>

              {activeFilter === key && (
                <div
                  className="sp-filter-menu"
                  role="group"
                  aria-label={label}
                >
                  {key === "price" ? (
                    <div className="sp-price-popover">
                      <div
                        className="sp-price-mode-tabs"
                        role="group"
                        aria-label="Chế độ lọc giá"
                      >
                        <button
                          type="button"
                          className={`sp-price-mode-tab ${
                            draftFilters.priceMode === "tong" ? "active" : ""
                          }`}
                          onClick={() =>
                            setDraftFilters((curr) => ({
                              ...curr,
                              priceMode: "tong",
                            }))
                          }
                        >
                          Tổng giá (VNĐ)
                        </button>
                        <button
                          type="button"
                          className={`sp-price-mode-tab ${
                            draftFilters.priceMode === "m2" ? "active" : ""
                          }`}
                          onClick={() =>
                            setDraftFilters((curr) => ({
                              ...curr,
                              priceMode: "m2",
                            }))
                          }
                        >
                          Đơn giá / m²
                        </button>
                      </div>

                      {draftFilters.transaction === "Tất cả" ? (
                        <div className="sp-price-require-tx">
                          <p>
                            Vui lòng chọn nhu cầu giao dịch trước khi lọc theo
                            khoảng giá:
                          </p>
                          <div className="sp-price-tx-quick-btns">
                            <button
                              type="button"
                              onClick={() =>
                                setDraftFilters((curr) => ({
                                  ...curr,
                                  transaction: "Mua bán",
                                }))
                              }
                            >
                              Mua bán
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                setDraftFilters((curr) => ({
                                  ...curr,
                                  transaction: "Cho thuê",
                                }))
                              }
                            >
                              Cho thuê
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="sp-price-input-wrap">
                          <label className="sp-price-input-control">
                            <span>
                              Từ (
                              {draftFilters.priceMode === "m2"
                                ? "VNĐ/m²"
                                : "VNĐ"}
                              )
                            </span>
                            <input
                              type="text"
                              inputMode="numeric"
                              placeholder="Tối thiểu"
                              value={draftFilters.priceMin}
                              onChange={(event) => {
                                const digits = event.target.value.replace(
                                  /\D/g,
                                  ""
                                );
                                setDraftFilters((current) => ({
                                  ...current,
                                  priceMin: digits,
                                }));
                              }}
                              aria-label="Giá thấp nhất"
                            />
                          </label>
                          <label className="sp-price-input-control">
                            <span>
                              Đến (
                              {draftFilters.priceMode === "m2"
                                ? "VNĐ/m²"
                                : "VNĐ"}
                              )
                            </span>
                            <input
                              type="text"
                              inputMode="numeric"
                              placeholder="Tối đa"
                              value={draftFilters.priceMax}
                              onChange={(event) => {
                                const digits = event.target.value.replace(
                                  /\D/g,
                                  ""
                                );
                                setDraftFilters((current) => ({
                                  ...current,
                                  priceMax: digits,
                                }));
                              }}
                              aria-label="Giá cao nhất"
                            />
                          </label>
                        </div>
                      )}
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
                            onChange={(event) =>
                              setCitySearchText(event.target.value)
                            }
                            className="sp-filter-search-input"
                            aria-label="Tìm kiếm tỉnh, thành phố"
                            autoFocus
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                event.preventDefault();
                                if (filteredCityOptions.length > 0) {
                                  const match =
                                    filteredCityOptions.find(
                                      (o) => o !== "Tất cả"
                                    ) || filteredCityOptions[0];
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
                            onChange={(event) =>
                              setWardSearchText(event.target.value)
                            }
                            className="sp-filter-search-input"
                            aria-label="Tìm kiếm xã, phường"
                            autoFocus
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                event.preventDefault();
                                if (filteredWardOptions.length > 0) {
                                  const match =
                                    filteredWardOptions.find(
                                      (o) => o !== "Tất cả"
                                    ) || filteredWardOptions[0];
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
              setActiveFilter(null);
            }}
          >
            Áp dụng bộ lọc
          </button>
        </aside>

        <section className="sp-results" aria-label="Kết quả tìm kiếm">
          {apiError && (
            <div className="sp-error-banner" role="alert">
              <span>{apiError}</span>
              <button
                type="button"
                onClick={() => {
                  setApiError(null);
                  setAppliedFilters({ ...appliedFilters });
                }}
              >
                Thử lại
              </button>
            </div>
          )}

          {/* Vùng 1: Khu tin tài trợ độc lập (tối đa 5 tin theo TK-10 & TK-12) */}
          {sponsoredProperties.length > 0 && (
            <section
              className="sp-sponsored-section"
              aria-label="Tin được tài trợ"
            >
              <div className="sp-sponsored-header">
                <div>
                  <p className="sp-sponsored-eyebrow">
                    Tin được tài trợ ({sponsoredProperties.length})
                  </p>
                  <h2 className="sp-sponsored-title">
                    Lựa chọn đang được ưu tiên hiển thị
                  </h2>
                </div>
                <div className="sp-sponsored-actions">
                  <p className="sp-sponsored-disclosure">
                    Vị trí quảng bá trả phí
                  </p>
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
                          setSponsoredPage((current) =>
                            Math.max(0, current - 1)
                          )
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

          {/* Vùng 2: Khu kết quả tự nhiên độc lập có phân trang */}
          <div className="sp-results-toolbar">
            <div className="sp-results-heading">
              <strong className="sp-result-count">
                {regularTotalCount.toLocaleString("vi-VN")} kết quả tự nhiên
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
                  {
                    sortOptions.find((option) => option.value === sortOrder)
                      ?.label
                  }
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
              <div className="sp-map-bounds-bar">
                <span>Khung bản đồ (TK-05):</span>
                {MAP_BOUNDS_PRESETS.map((preset) => (
                  <button
                    key={preset.label}
                    type="button"
                    className={`sp-map-bounds-btn ${
                      mapBounds?.label === preset.label ? "active" : ""
                    }`}
                    onClick={() => {
                      setMapBounds(
                        mapBounds?.label === preset.label ? null : preset
                      );
                      setPage(1);
                    }}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
              <div className="sp-map-road" />
              {mapMarkers.length > 0 ? (
                mapMarkers.map((item, idx) => {
                  const positions = [
                    { top: "24%", left: "22%" },
                    { top: "48%", left: "58%" },
                    { top: "66%", left: "34%" },
                    { top: "30%", left: "68%" },
                    { top: "60%", left: "72%" },
                    { top: "38%", left: "38%" },
                    { top: "74%", left: "52%" },
                    { top: "22%", left: "48%" },
                  ];
                  const pos = positions[idx % positions.length];
                  return (
                    <Link
                      to={`/tin-dang/${item.id}`}
                      key={`marker-${item.id}`}
                      className="sp-map-marker"
                      style={{
                        top: pos.top,
                        left: pos.left,
                        textDecoration: "none",
                      }}
                    >
                      <strong>{item.price}</strong>
                      <span>{item.place}</span>
                    </Link>
                  );
                })
              ) : (
                <div className="sp-map-marker sp-marker-one">
                  <strong>Chưa có điểm ghim</strong>
                  <span>Không có tin trong vùng lọc hiện tại</span>
                </div>
              )}
              <p>
                <Icon name="map" /> Nhấn vào điểm ghim giá trên bản đồ để mở chi
                tiết bất động sản
              </p>
            </div>
          ) : isLoading ? (
            <div className="sp-empty-state" aria-live="polite">
              <p>Đang tải danh sách bất động sản...</p>
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
          ) : sponsoredProperties.length > 0 ? (
            <div className="sp-no-regular-results">
              Không có kết quả tự nhiên cho bộ lọc hiện tại. Các tin được tài
              trợ phù hợp vẫn được hiển thị ở khu vực phía trên.
            </div>
          ) : (
            <div className="sp-empty-state">
              <span>
                <Icon name="search" />
              </span>
              <h2>Chưa tìm thấy bất động sản phù hợp</h2>
              <p>
                Thử thay đổi từ khóa hoặc nới rộng bộ lọc để xem thêm kết quả.
              </p>
              <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
                <button type="button" onClick={clearFilters}>
                  Xóa bộ lọc
                </button>
              </div>
            </div>
          )}

          {!mapView && regularTotalCount > pageSize && (
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

      {/* Mục Gợi ý cho bạn (chỉ hiển thị từ dữ liệu thật nếu có) */}
      {apiRegular.length > 0 && (
        <section className="sp-recommendations">
          <p className="sp-eyebrow">Gợi ý cho bạn</p>
          <h2>Có thể bạn sẽ thích</h2>
          <p>Dựa trên kết quả bất động sản mới nhất trên hệ thống.</p>
          <div className="sp-recommendation-grid">
            {apiRegular.slice(0, 3).map((property) => (
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
      )}
    </div>
  );
}
