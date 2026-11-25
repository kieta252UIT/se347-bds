import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "@/hooks/useAuth";
import { searchService } from "@/services/searchService";
import type { LoaiBds } from "@/utils/priceFormatter";
import locations from "@/data/locations.json";
import "./home-page.css";

type IconName =
  | "home"
  | "user"
  | "search"
  | "chevron"
  | "heart"
  | "pin"
  | "area"
  | "bed"
  | "arrow"
  | "plus";

function Icon({ name }: { name: IconName }) {
  const paths: Record<IconName, React.ReactNode> = {
    home: (
      <>
        <path d="m3 11 9-7 9 7" />
        <path d="M5 10v10h14V10M9 20v-6h6v6" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="8" r="3.5" />
        <path d="M5.5 20a6.5 6.5 0 0 1 13 0" />
      </>
    ),
    search: (
      <>
        <circle cx="10.8" cy="10.8" r="5.7" />
        <path d="m15.2 15.2 4 4" />
      </>
    ),
    chevron: <path d="m7 10 5 5 5-5" />,
    heart: (
      <path d="M20.8 8.5c0 5.5-8.8 10.4-8.8 10.4S3.2 14 3.2 8.5A4.3 4.3 0 0 1 11 6l1 1.2L13 6a4.3 4.3 0 0 1 7.8 2.5Z" />
    ),
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
    arrow: <path d="M5 12h14m-5-5 5 5-5 5" />,
    plus: <path d="M12 5v14M5 12h14" />,
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

const categories: { number: string; name: string; apiType: LoaiBds }[] = [
  { number: "01", name: "Căn hộ chung cư", apiType: "can_ho" },
  { number: "02", name: "Nhà riêng", apiType: "nha_rieng" },
  { number: "03", name: "Đất nền", apiType: "dat" },
  { number: "04", name: "Phòng trọ", apiType: "phong_tro" },
  { number: "05", name: "Khác", apiType: "khac" },
];

const properties = [
  {
    tag: "Nổi bật",
    price: "4,2 tỷ",
    title: "Căn hộ 2 phòng ngủ gần trung tâm TP. Thủ Đức",
    place: "Thảo Điền, TP. Thủ Đức",
    size: "78 m²",
    beds: "2 phòng ngủ",
    image:
      "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=900&q=82",
  },
  {
    tag: "Nổi bật",
    price: "8,9 tỷ",
    title: "Nhà phố 1 trệt 2 lầu tại Bình Thạnh",
    place: "Phường 25, Bình Thạnh",
    size: "92 m²",
    beds: "3 phòng ngủ",
    image:
      "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=900&q=82",
  },
  {
    tag: "Nổi bật",
    price: "18 triệu/tháng",
    title: "Căn hộ cho thuê đầy đủ nội thất",
    place: "An Phú, TP. Thủ Đức",
    size: "84 m²",
    beds: "2 phòng ngủ",
    image:
      "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=900&q=82",
  },
];

const cities = [
  {
    name: "TP. Hồ Chí Minh",
    apiKhuVuc: "Hồ Chí Minh",
    image:
      "https://images.unsplash.com/photo-1583417319070-4a69db38a482?auto=format&fit=crop&w=900&q=80",
  },
  {
    name: "Hà Nội",
    apiKhuVuc: "Hà Nội",
    image:
      "https://images.unsplash.com/photo-1689080541115-5acffbf0a083?q=80&w=686&auto=format&fit=crop",
  },
  {
    name: "Đà Nẵng",
    apiKhuVuc: "Đà Nẵng",
    image:
      "https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=900&q=80",
  },
];

const demandOptions = ["Tất cả", "Mua bán", "Cho thuê"];
const provinceOptions = ["Tất cả", ...locations.provinces.map(({ name }) => name)];
const propertyTypeOptions = [
  "Tất cả",
  "Căn hộ chung cư",
  "Nhà riêng",
  "Đất nền",
  "Phòng trọ",
  "Khác",
];

const formatProvinceLabel = (province: string) =>
  province.replace(/^Thành phố /, "TP. ").replace(/^Tỉnh /, "");

function normalizeVietnamese(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

const normalizeProvinceName = (province: string) =>
  province
    .replace(/^(Thành phố|Tỉnh|TP\.)\s*/, "")
    .trim()
    .toLocaleLowerCase("vi");

export function HomePage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [liked, setLiked] = useState<number[]>([]);
  const [activeFilter, setActiveFilter] = useState<
    "demand" | "province" | "propertyType" | null
  >(null);
  const [selectedDemand, setSelectedDemand] = useState("Tất cả");
  const [selectedProvince, setSelectedProvince] = useState("Tất cả");
  const [provinceSearchText, setProvinceSearchText] = useState("");
  const [selectedPropertyType, setSelectedPropertyType] = useState(propertyTypeOptions[0]);
  const [categoryCounts, setCategoryCounts] = useState<Partial<Record<LoaiBds, number>>>({});
  const [cityCounts, setCityCounts] = useState<Record<string, number>>({});

  const filterCardRef = useRef<HTMLElement>(null);

  const filteredProvinceOptions = useMemo(() => {
    if (!provinceSearchText.trim()) return provinceOptions;
    const norm = normalizeVietnamese(provinceSearchText);
    return provinceOptions.filter((option) =>
      normalizeVietnamese(option).includes(norm)
    );
  }, [provinceSearchText]);

  const toggleLiked = (index: number) =>
    setLiked((current) =>
      current.includes(index) ? current.filter((item) => item !== index) : [...current, index]
    );

  const handleSearch = () => {
    const params = new URLSearchParams({
      transaction: selectedDemand,
      city: selectedProvince,
      propertyType:
        selectedPropertyType === "Tất cả" ? "Tất cả" : selectedPropertyType,
    });

    navigate(`/tim-kiem?${params.toString()}`);
  };

  useEffect(() => {
    const controller = new AbortController();

    Promise.all(
      categories.map(async (cat) => {
        const res = await searchService.searchListings(
          { loaiBds: cat.apiType, trang: 0, kichThuoc: 1 },
          controller.signal
        );
        return [cat.apiType, res.tuNhien.tongSo] as const;
      })
    )
      .then((entries) => {
        if (controller.signal.aborted) return;
        const nextCounts: Partial<Record<LoaiBds, number>> = {};
        for (const [apiType, total] of entries) {
          nextCounts[apiType] = total;
        }
        setCategoryCounts(nextCounts);
      })
      .catch(() => {
        // Giữ nguyên khi hủy request hoặc lỗi mạng
      });

    Promise.all(
      cities.map(async (city) => {
        const res = await searchService.searchListings(
          { khuVuc: city.apiKhuVuc, trang: 0, kichThuoc: 1 },
          controller.signal
        );
        return [city.name, res.tuNhien.tongSo] as const;
      })
    )
      .then((entries) => {
        if (controller.signal.aborted) return;
        const nextCityCounts: Record<string, number> = {};
        for (const [cityName, total] of entries) {
          nextCityCounts[cityName] = total;
        }
        setCityCounts(nextCityCounts);
      })
      .catch(() => {
        // Giữ nguyên khi hủy request hoặc lỗi mạng
      });

    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!activeFilter) return;

    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!filterCardRef.current?.contains(event.target as Node)) setActiveFilter(null);
    };

    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [activeFilter]);

  const filterDefinitions = [
    { key: "demand", label: "Nhu cầu", value: selectedDemand, options: demandOptions },
    {
      key: "province",
      label: "Khu vực",
      value: formatProvinceLabel(selectedProvince),
      options: provinceOptions,
    },
    {
      key: "propertyType",
      label: "Loại bất động sản",
      value: selectedPropertyType,
      options: propertyTypeOptions,
    },
  ] as const;

  return (
    <div>
      {/* 1. Hero & Thanh tìm kiếm nhanh */}
      <section className="home-hero" id="top">
        <div className="home-hero-copy">
          <p className="home-eyebrow">Tìm đúng nơi, chọn đúng nhà</p>
          <h1>
            Không gian sống <em>phù hợp với bạn.</em>
          </h1>
          <p className="home-description">
            Khám phá hàng nghìn bất động sản đã được kiểm duyệt và nhận gợi ý dựa trên nhu cầu thực tế của bạn.
          </p>
        </div>

        <div
          className="home-hero-image"
          role="img"
          aria-label="Không gian phòng khách hiện đại"
        />

        <section
          ref={filterCardRef}
          className="home-filter-card"
          aria-label="Tìm kiếm bất động sản"
        >
          {filterDefinitions.map((filter) => (
            <div className="home-filter-field" key={filter.key}>
              <button
                type="button"
                className={`home-filter-btn ${activeFilter === filter.key ? "active" : ""}`}
                onClick={() =>
                  setActiveFilter(activeFilter === filter.key ? null : filter.key)
                }
                aria-expanded={activeFilter === filter.key}
              >
                <small>{filter.label}</small>
                <strong>{filter.value}</strong>
                <Icon name="chevron" />
              </button>

              {activeFilter === filter.key && (
                <div className="home-filter-menu" role="group" aria-label={filter.label}>
                  {filter.key === "province" && (
                    <div
                      className="home-filter-search-box"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <input
                        type="text"
                        placeholder="Tìm tỉnh, thành phố..."
                        value={provinceSearchText}
                        onChange={(event) => setProvinceSearchText(event.target.value)}
                        className="home-filter-search-input"
                        aria-label="Tìm kiếm tỉnh, thành phố"
                        autoFocus
                        onKeyDown={(event) => {
                          if (event.key === "Enter") {
                            event.preventDefault();
                            if (filteredProvinceOptions.length > 0) {
                              const match =
                                filteredProvinceOptions.find((o) => o !== "Tất cả") ||
                                filteredProvinceOptions[0];
                              setSelectedProvince(match);
                              setProvinceSearchText("");
                              setActiveFilter(null);
                            }
                          }
                        }}
                      />
                      <button
                        type="button"
                        className="home-filter-search-btn"
                        aria-label="Tìm kiếm"
                        tabIndex={-1}
                      >
                        <Icon name="search" />
                      </button>
                    </div>
                  )}
                  <div className="home-filter-options">
                    {(filter.key === "province" ? filteredProvinceOptions : filter.options).map(
                      (option) => (
                        <button
                          key={option}
                          type="button"
                          className={`home-filter-option ${
                            (filter.key === "demand" && option === selectedDemand) ||
                            (filter.key === "province" && option === selectedProvince) ||
                            (filter.key === "propertyType" &&
                              option === selectedPropertyType)
                              ? "active"
                              : ""
                          }`}
                          onClick={() => {
                            if (filter.key === "demand") setSelectedDemand(option);
                            if (filter.key === "province") {
                              setSelectedProvince(option);
                              setProvinceSearchText("");
                            }
                            if (filter.key === "propertyType")
                              setSelectedPropertyType(option);
                            setActiveFilter(null);
                          }}
                        >
                          {option}
                        </button>
                      )
                    )}
                    {filter.key === "province" && filteredProvinceOptions.length === 0 && (
                      <div className="home-filter-empty">
                        Không tìm thấy tỉnh / thành phố phù hợp
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}

          <button
            className="home-search-button"
            type="button"
            onClick={handleSearch}
          >
            <Icon name="search" />
            Tìm kiếm
          </button>
        </section>
      </section>

      {/* 2. Danh mục loại bất động sản */}
      <section className="home-section">
        <p className="home-eyebrow">Khám phá</p>
        <h2>Bạn đang tìm kiếm điều gì?</h2>
        <div className="home-category-grid">
          {categories.map(({ number, name, apiType }) => {
            const countValue = categoryCounts[apiType];
            const countLabel =
              countValue !== undefined
                ? `${countValue.toLocaleString("vi-VN")} tin`
                : "Đang cập nhật";
            return (
              <Link
                to={`/tim-kiem?propertyType=${encodeURIComponent(name)}`}
                className="home-category"
                key={name}
              >
                <small>{number}</small>
                <div>
                  <strong>{name}</strong>
                  <span>{countLabel}</span>
                </div>
                <Icon name="arrow" />
              </Link>
            );
          })}
        </div>
      </section>

      {/* 3. Bất động sản nổi bật */}
      <section className="home-section home-featured">
        <div className="home-section-heading">
          <div>
            <p className="home-eyebrow">Được quan tâm</p>
            <h2>Bất động sản nổi bật</h2>
          </div>
          <Link to="/tim-kiem">
            Xem tất cả <Icon name="arrow" />
          </Link>
        </div>

        <div className="home-property-grid">
          {properties.map((property, index) => (
            <article className="home-property" key={property.title}>
              <div
                className="home-property-image"
                style={{ backgroundImage: `url("${property.image}")` }}
              >
                <span>{property.tag}</span>
                <button
                  type="button"
                  className={liked.includes(index) ? "liked" : ""}
                  onClick={() => toggleLiked(index)}
                  aria-label="Yêu thích"
                >
                  <Icon name="heart" />
                </button>
              </div>
              <div className="home-property-body">
                <strong className="home-price">{property.price}</strong>
                <h3>{property.title}</h3>
                <p>
                  <Icon name="pin" />
                  {property.place}
                </p>
                <div className="home-meta">
                  <span>
                    <Icon name="area" />
                    {property.size}
                  </span>
                  <span>
                    <Icon name="bed" />
                    {property.beds}
                  </span>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>

      {/* 4. Các thành phố lớn */}
      <section className="home-section">
        <div className="home-section-heading">
          <div>
            <p className="home-eyebrow">Khám phá theo khu vực</p>
            <h2>Bất động sản tại các thành phố lớn</h2>
          </div>
        </div>
        <div className="home-city-grid">
          {cities.map(({ name: city, image }) => {
            const province = locations.provinces.find(
              ({ name }) => normalizeProvinceName(name) === normalizeProvinceName(city)
            );
            const searchHref = province
              ? `/tim-kiem?city=${encodeURIComponent(province.name)}`
              : "/tim-kiem";
            const countVal = cityCounts[city];
            const countLabel =
              countVal !== undefined
                ? `${countVal.toLocaleString("vi-VN")} bất động sản`
                : "Đang cập nhật";
            return (
              <Link to={searchHref} className="home-city" key={city}>
                <div style={{ backgroundImage: `url("${image}")` }} />
                <strong>{city}</strong>
                <span>{countLabel}</span>
                <Icon name="arrow" />
              </Link>
            );
          })}
        </div>
      </section>

      {/* 5. Callout Đăng tin ngay */}
      <section
        className="home-callout"
        aria-label="Thông tin dành cho chủ bất động sản"
      >
        <span>
          <Icon name="home" />
        </span>
        <div>
          <h2>Bạn có bất động sản cần bán hoặc cho thuê?</h2>
          <p>
            Đăng tin dễ dàng, tiếp cận người tìm kiếm phù hợp và quản lý tin ngay trên HomeFinder.
          </p>
        </div>
        <button
          type="button"
          className="home-post-button"
          onClick={() => {
            if (isAuthenticated) {
              navigate("/dang-tin");
            } else {
              navigate("/dang-nhap", { state: { from: { pathname: "/dang-tin" } } });
            }
          }}
        >
          <Icon name="plus" /> Đăng tin ngay
        </button>
      </section>
    </div>
  );
}
