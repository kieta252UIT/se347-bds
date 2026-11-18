import { useRef, useState } from "react";
import locations from "@/data/locations.json";
import { toast } from "@/stores/useToastStore";
import "@/pages/user/upload-property.css";

function UploadIcon({ name }: { name: "cloud" | "trash" | "warning" | "pin" }) {
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

const defaultPhotos = [
  "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=600&q=80",
  "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=600&q=80",
];

export interface UploadPropertyInitialData {
  id?: number;
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

  const [photos, setPhotos] = useState<string[]>(defaultPhotos);
  const [listingType, setListingType] = useState<"sale" | "rent">("sale");
  const [rentPeriod, setRentPeriod] = useState<"month" | "year">("month");
  const [rentPriceUnit, setRentPriceUnit] = useState<"million" | "billion">("million");
  const [propertyType, setPropertyType] = useState<string>(isEditing ? "Nhà phố" : "");
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

  const fileInputRef = useRef<HTMLInputElement>(null);

  const rentPeriodLabel = rentPeriod === "month" ? "tháng" : "năm";
  const rentPriceUnitLabel = rentPriceUnit === "million" ? "triệu" : "tỷ";

  const activeProvinceObj = locations.provinces.find((p) => p.name === selectedCity);
  const availableWards = activeProvinceObj
    ? locations.wards.filter((w) => w.province_id === activeProvinceObj.id)
    : locations.wards.slice(0, 8);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files || files.length === 0) return;
    const newUrls = Array.from(files).map((file) => URL.createObjectURL(file));
    setPhotos((current) => [...current, ...newUrls].slice(0, 12));
    event.target.value = "";
  };

  const removePhoto = (indexToRemove: number) => {
    setPhotos((current) => current.filter((_, index) => index !== indexToRemove));
  };

  const handleSaveDraft = () => {
    const msg = isEditing
      ? "Đã lưu bản nháp chỉnh sửa tin đăng."
      : "Đã lưu bản nháp tin đăng bất động sản.";
    toast.info(msg, "Đã lưu nháp");
    onSubmitSuccess?.(msg);
  };

  const handleSubmitListing = () => {
    const msg = isEditing
      ? `Đã cập nhật và gửi duyệt lại tin “${title || "Bất động sản"}”.`
      : `Đã gửi tin đăng “${title || "Bất động sản mới"}” vào hàng chờ kiểm duyệt.`;
    toast.success(msg, isEditing ? "Cập nhật tin đăng" : "Đã gửi duyệt tin");
    onSubmitSuccess?.(msg);
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
              onChange={handleFileChange}
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
            >
              <div>
                <span className="upload-cloud">
                  <UploadIcon name="cloud" />
                </span>
                <p>Kéo thả hình ảnh vào đây hoặc nhấn để chọn ảnh</p>
                <small>Tải lên từ 1 đến 12 hình ảnh (JPEG, PNG, WebP - tối thiểu 1 ảnh)</small>
              </div>
            </div>

            {photos.length > 0 && (
              <div className="upload-thumbs">
                {photos.map((photo, index) => (
                  <div className="upload-thumb" key={`${photo}-${index}`}>
                    <img
                      src={photo}
                      alt={`Ảnh bất động sản ${index + 1}`}
                    />
                    <span className={`upload-thumb-label ${index === 0 ? "featured" : ""}`}>
                      {index === 0 ? "★ Ảnh bìa" : `Ảnh ${index + 1}`}
                    </span>
                    <button
                      type="button"
                      className="upload-thumb-delete"
                      aria-label={`Xóa ảnh ${index + 1}`}
                      onClick={() => removePhoto(index)}
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
                    <select
                      id="rent-period"
                      value={rentPeriod}
                      onChange={(event) => setRentPeriod(event.target.value as "month" | "year")}
                    >
                      <option value="month">Theo tháng</option>
                      <option value="year">Theo năm</option>
                    </select>
                  </div>
                )}
              </div>

              <div className="upload-field">
                <label htmlFor="property-type">
                  Loại bất động sản <span className="required">*</span>
                </label>
                <select
                  id="property-type"
                  value={propertyType}
                  onChange={(e) => setPropertyType(e.target.value)}
                >
                  <option value="" disabled>
                    Chọn loại bất động sản
                  </option>
                  <option value="Nhà riêng">Nhà riêng</option>
                  <option value="Căn hộ chung cư">Căn hộ chung cư</option>
                  <option value="Đất nền">Đất nền</option>
                  <option value="Biệt thự">Biệt thự</option>
                  <option value="Nhà phố">Nhà phố</option>
                  <option value="Mặt bằng kinh doanh">Mặt bằng kinh doanh</option>
                  <option value="Phòng trọ">Phòng trọ</option>
                </select>
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
                  placeholder="Nhập thông tin mô tả chi tiết về dự án, căn hộ, số phòng ngủ, phòng tắm, hướng nhà, tình trạng nội thất, tiện ích xung quanh, pháp lý..."
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
                    `Giá cho thuê (${rentPriceUnitLabel}/${rentPeriodLabel})`
                  ) : (
                    <>
                      Giá bán (VNĐ) <span className="required">*</span>
                    </>
                  )}
                </label>
                {listingType === "rent" ? (
                  <div className="upload-price-control">
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
                    <select
                      value={rentPriceUnit}
                      onChange={(event) =>
                        setRentPriceUnit(event.target.value as "million" | "billion")
                      }
                      aria-label="Đơn vị giá cho thuê"
                    >
                      <option value="million">Triệu</option>
                      <option value="billion">Tỷ</option>
                    </select>
                    <span>/{rentPeriodLabel}</span>
                  </div>
                ) : (
                  <input
                    id="price"
                    type="number"
                    min="0"
                    step="1"
                    value={price}
                    onChange={(e) => setPrice(e.target.value)}
                    placeholder="Ví dụ: 5200000000"
                  />
                )}
              </div>

              <div className="upload-field">
                <label htmlFor="area">
                  Diện tích (m²) <span className="required">*</span>
                </label>
                <input
                  id="area"
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
                  <select
                    id="city"
                    value={selectedCity}
                    onChange={(e) => {
                      setSelectedCity(e.target.value);
                      setSelectedWard("");
                    }}
                  >
                    <option value="" disabled>
                      Chọn tỉnh/thành phố
                    </option>
                    {locations.provinces.map((prov) => (
                      <option key={prov.id} value={prov.name}>
                        {prov.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="upload-field">
                  <label htmlFor="ward">
                    Phường/Xã <span className="required">*</span>
                  </label>
                  <select
                    id="ward"
                    value={selectedWard}
                    onChange={(e) => setSelectedWard(e.target.value)}
                  >
                    <option value="" disabled>
                      Chọn phường/xã
                    </option>
                    {availableWards.map((ward) => (
                      <option key={ward.id} value={ward.name}>
                        {ward.name}
                      </option>
                    ))}
                  </select>
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

              <div className="upload-field">
                <label>
                  Xác định vị trí trên bản đồ{" "}
                  <span className="upload-help">(kéo pin để định vị chính xác)</span>
                </label>
                <div className="upload-map">
                  <span className="upload-pin">
                    <UploadIcon name="pin" />
                  </span>
                </div>
              </div>
            </div>
          </section>

          <footer className="upload-actions">
            <button
              type="button"
              className="upload-button"
              onClick={() => onCancel?.()}
            >
              Hủy bỏ
            </button>
            <div className="upload-actions-right">
              <button
                type="button"
                className="upload-button"
                onClick={handleSaveDraft}
              >
                Lưu nháp
              </button>
              <button
                type="button"
                className="upload-button primary"
                onClick={handleSubmitListing}
              >
                {isEditing ? "Cập nhật & Gửi duyệt lại" : "Gửi duyệt tin"}
              </button>
            </div>
          </footer>
        </section>
      </div>
    </div>
  );
}

export default UploadPropertyForm;
