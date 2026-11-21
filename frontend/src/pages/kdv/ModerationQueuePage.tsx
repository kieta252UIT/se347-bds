import { useEffect, useMemo, useRef, useState } from "react";
import { toast } from "@/stores/useToastStore";

type SubmissionType = "moi" | "nop_lai";
type ModerationStatus = "cho_kiem_duyet" | "da_duyet" | "bi_tu_choi";
type PropertyCategory =
  | "Căn hộ chung cư"
  | "Nhà riêng"
  | "Đất nền"
  | "Phòng trọ"
  | "Khác";

interface VersionHistoryEntry {
  attempt: number;
  decision: "da_duyet" | "bi_tu_choi";
  reviewerName: string;
  reviewedAt: string;
  reason?: string;
}

interface ModerationItem {
  id: string;
  versionId: string;
  versionNumber: number;
  title: string;
  transactionType: "ban" | "cho_thue";
  rentPeriod?: "Theo tháng" | "Theo năm";
  category: PropertyCategory;
  submissionType: SubmissionType;
  status: ModerationStatus;
  priceDisplay: string;
  rawPriceVnd: string;
  areaSize: string;
  province: string;
  ward: string;
  streetAddress: string;
  latitude: string;
  longitude: string;
  ownerName: string;
  ownerEmail: string;
  ownerPhone: string;
  submittedAt: string; // Format: DD/MM/YYYY HH:mm
  description: string;
  versionHistory: VersionHistoryEntry[];
  rejectionReason?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  images: string[];
}

const PROPERTY_TYPE_OPTIONS: { value: "ALL" | PropertyCategory; label: string }[] = [
  { value: "ALL", label: "Tất cả loại BĐS" },
  { value: "Căn hộ chung cư", label: "Căn hộ chung cư" },
  { value: "Nhà riêng", label: "Nhà riêng" },
  { value: "Đất nền", label: "Đất nền" },
  { value: "Phòng trọ", label: "Phòng trọ" },
  { value: "Khác", label: "Khác" },
];

const STATUS_OPTIONS: { value: "ALL" | ModerationStatus; label: string }[] = [
  { value: "cho_kiem_duyet", label: "Chờ kiểm duyệt" },
  { value: "da_duyet", label: "Đã phê duyệt" },
  { value: "bi_tu_choi", label: "Đã từ chối" },
  { value: "ALL", label: "Tất cả trạng thái" },
];

const SUBMISSION_OPTIONS: { value: "ALL" | SubmissionType; label: string }[] = [
  { value: "ALL", label: "Tất cả nguồn nộp" },
  { value: "moi", label: "Tin đăng mới" },
  { value: "nop_lai", label: "Tin sửa & nộp lại" },
];

const initialQueue: ModerationItem[] = [
  {
    id: "TIN-2026-1042",
    versionId: "VER-9012-A",
    versionNumber: 1,
    title: "Căn hộ 2PN Vinhomes Grand Park phân khu Origami tầng 12A view công viên",
    transactionType: "ban",
    category: "Căn hộ chung cư",
    submissionType: "moi",
    status: "cho_kiem_duyet",
    priceDisplay: "2,85 tỷ",
    rawPriceVnd: "2.850.000.000 VNĐ",
    areaSize: "68 m²",
    province: "Thành phố Hồ Chí Minh",
    ward: "Phường Long Thạnh Mỹ",
    streetAddress: "Đường Nguyễn Xiển, Khu đô thị Vinhomes Grand Park",
    latitude: "10.843215",
    longitude: "106.837420",
    ownerName: "Nguyễn Minh Tuấn",
    ownerEmail: "tuan.nm@gmail.com",
    ownerPhone: "0908341289",
    submittedAt: "20/11/2026 14:30",
    versionHistory: [],
    description:
      "Bán căn hộ 2 phòng ngủ, 2 WC tại phân khu The Origami, Vinhomes Grand Park. Nhà trang bị đầy đủ nội thất gỗ An Cường cao cấp, máy lạnh âm trần Daikin, ban công hướng Đông Nam đón gió thoáng mát, tầm nhìn trực diện công viên nội khu. Hỗ trợ khách vay ngân hàng đến 70% và sang tên nhanh trong tuần.",
    images: [
      "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600210492486-724fe5c67fb0?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600566753086-00f18fb6b3ea?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600585154526-990dced4db0d?auto=format&fit=crop&w=1000&q=82",
    ],
  },
  {
    id: "TIN-2026-1039",
    versionId: "VER-8940-B",
    versionNumber: 2,
    title: "Nhà riêng mặt tiền đường số 7 Khu dân cư Trung Sơn 4x20m xây 1 trệt 3 lầu",
    transactionType: "ban",
    category: "Nhà riêng",
    submissionType: "nop_lai",
    status: "cho_kiem_duyet",
    priceDisplay: "11,2 tỷ",
    rawPriceVnd: "11.200.000.000 VNĐ",
    areaSize: "80 m²",
    province: "Thành phố Hồ Chí Minh",
    ward: "Xã Bình Hưng",
    streetAddress: "Đường số 7, Khu dân cư Trung Sơn",
    latitude: "10.734190",
    longitude: "106.689412",
    ownerName: "Lê Hoàng Nam",
    ownerEmail: "namle.bds@gmail.com",
    ownerPhone: "0913456890",
    submittedAt: "19/11/2026 09:15",
    versionHistory: [
      {
        attempt: 1,
        decision: "bi_tu_choi",
        reviewerName: "KDV. Trần Minh Khoa",
        reviewedAt: "18/11/2026 15:20",
        reason: "Ảnh chụp mặt tiền bị mờ và thiếu thông tin diện tích sàn sử dụng.",
      },
    ],
    description:
      "Đã bổ sung lại bộ ảnh chụp thực tế mặt tiền rõ nét theo yêu cầu của Kiểm duyệt viên. Diện tích đất 80m² (4x20m), tổng diện tích sàn sử dụng 280m². Nhà gồm 4 phòng ngủ lớn, 5 WC, sân thượng trước sau, gara ô tô 7 chỗ trong nhà. Đường trước nhà 12m có vỉa hè cây xanh.",
    images: [
      "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600585152220-90363fe7e115?auto=format&fit=crop&w=1000&q=82",
    ],
  },
  {
    id: "TIN-2026-1035",
    versionId: "VER-8891-A",
    versionNumber: 1,
    title: "Đất nền sổ hồng riêng KĐT Nam Hòa Xuân B2-45 diện tích 105m² đường 7m5",
    transactionType: "ban",
    category: "Đất nền",
    submissionType: "moi",
    status: "cho_kiem_duyet",
    priceDisplay: "3,45 tỷ",
    rawPriceVnd: "3.450.000.000 VNĐ",
    areaSize: "105 m²",
    province: "Thành phố Đà Nẵng",
    ward: "Phường Hòa Quý",
    streetAddress: "Lô B2-45 Khu đô thị sinh thái Nam Hòa Xuân",
    latitude: "15.998412",
    longitude: "108.241530",
    ownerName: "Trần Thị Mai",
    ownerEmail: "maitran88@gmail.com",
    ownerPhone: "0987612340",
    submittedAt: "18/11/2026 16:20",
    versionHistory: [],
    description:
      "Chính chủ cần bán lô đất B2-45 khu đô thị sinh thái Nam Hòa Xuân. Lô đất sạch hoàn toàn không vướng cống hay tủ điện, kích thước ngang 5m dài 21m, đường nhựa 7m5 lề 4m, gần trục đường chính Quảng Nam và công viên ven sông. Khu dân cư hiện hữu văn minh, hạ tầng điện âm 100%.",
    images: [
      "https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600047509807-ba8f99d2cdde?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=1000&q=82",
    ],
  },
  {
    id: "TIN-2026-1028",
    versionId: "VER-8812-C",
    versionNumber: 3,
    title: "Chung cư cao cấp The Zei Mỹ Đình 3PN 108m² căn góc 2 mặt thoáng",
    transactionType: "ban",
    category: "Căn hộ chung cư",
    submissionType: "nop_lai",
    status: "cho_kiem_duyet",
    priceDisplay: "6,9 tỷ",
    rawPriceVnd: "6.900.000.000 VNĐ",
    areaSize: "108 m²",
    province: "Thành phố Hà Nội",
    ward: "Phường Mỹ Đình 2",
    streetAddress: "Số 8 Lê Đức Thọ",
    latitude: "21.031820",
    longitude: "105.768910",
    ownerName: "Phạm Quốc Bảo",
    ownerEmail: "baopq.hn@gmail.com",
    ownerPhone: "0934889102",
    submittedAt: "17/11/2026 07:50",
    versionHistory: [
      {
        attempt: 1,
        decision: "bi_tu_choi",
        reviewerName: "KDV. Nguyễn Hoàng Yến",
        reviewedAt: "15/11/2026 10:15",
        reason: "Địa chỉ bất động sản chưa ghi rõ số nhà và tên đường.",
      },
      {
        attempt: 2,
        decision: "bi_tu_choi",
        reviewerName: "KDV. Trần Minh Khoa",
        reviewedAt: "16/11/2026 14:40",
        reason: "Tiêu đề chứa ký tự viết hoa toàn bộ và chèn số điện thoại trực tiếp vào ảnh.",
      },
    ],
    description:
      "Đã chỉnh sửa lại tiêu đề đúng quy chuẩn đăng tin của sàn HomeFinder và thay bộ ảnh gốc không dính chữ. Căn góc tầng 19, 3 phòng ngủ sáng, nội thất nhập khẩu Đức, có suất đỗ ô tô định danh dưới hầm.",
    images: [
      "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600566753190-17f0baa2a6c3?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1600210491892-03d54c0aaf87?auto=format&fit=crop&w=1000&q=82",
    ],
  },
  {
    id: "TIN-2026-1021",
    versionId: "VER-8790-A",
    versionNumber: 1,
    title: "Cho thuê phòng trọ studio ban công rộng gần ĐH Bách Khoa đầy đủ nội thất",
    transactionType: "cho_thue",
    rentPeriod: "Theo tháng",
    category: "Phòng trọ",
    submissionType: "moi",
    status: "bi_tu_choi",
    priceDisplay: "4,5 triệu/tháng",
    rawPriceVnd: "4.500.000 VNĐ/tháng",
    areaSize: "28 m²",
    province: "Thành phố Hồ Chí Minh",
    ward: "Phường 14",
    streetAddress: "Hẻm 268 Lý Thường Kiệt",
    latitude: "10.773105",
    longitude: "106.659812",
    ownerName: "Hoàng Gia Huy",
    ownerEmail: "huy.hoang10@gmail.com",
    ownerPhone: "0972115678",
    submittedAt: "16/11/2026 15:10",
    versionHistory: [
      {
        attempt: 1,
        decision: "bi_tu_choi",
        reviewerName: "KDV. Trần Minh Khoa",
        reviewedAt: "16/11/2026 16:45",
        reason: "Hình ảnh mờ, chứa đóng dấu (watermark) hoặc số điện thoại đè lên ảnh.",
      },
    ],
    rejectionReason: "Hình ảnh mờ, chứa đóng dấu (watermark) hoặc số điện thoại đè lên ảnh.",
    reviewedBy: "KDV. Trần Minh Khoa",
    reviewedAt: "16/11/2026 16:45",
    description:
      "Phòng studio mới xây 100%, có thang máy, khóa vân tay, máy giặt riêng từng phòng, giờ giấc tự do không chung chủ. Cách cổng trường ĐH Bách Khoa 300m.",
    images: [
      "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1598928506311-c55ded91a20c?auto=format&fit=crop&w=1000&q=82",
    ],
  },
  {
    id: "TIN-2026-1015",
    versionId: "VER-8720-A",
    versionNumber: 1,
    title: "Mặt bằng văn phòng & showroom góc 2 mặt tiền khu đô thị Sala Thủ Thiêm",
    transactionType: "cho_thue",
    rentPeriod: "Theo tháng",
    category: "Khác",
    submissionType: "moi",
    status: "da_duyet",
    priceDisplay: "65 triệu/tháng",
    rawPriceVnd: "65.000.000 VNĐ/tháng",
    areaSize: "145 m²",
    province: "Thành phố Hồ Chí Minh",
    ward: "Phường An Lợi Đông",
    streetAddress: "Số 10 Đường Nguyễn Cơ Thạch, Khu đô thị Sala",
    latitude: "10.771940",
    longitude: "106.722410",
    ownerName: "Đỗ Thùy Linh",
    ownerEmail: "linh.do@salarealty.vn",
    ownerPhone: "0903118999",
    submittedAt: "15/11/2026 11:05",
    versionHistory: [
      {
        attempt: 1,
        decision: "da_duyet",
        reviewerName: "KDV. Trần Minh Khoa",
        reviewedAt: "15/11/2026 13:20",
      },
    ],
    reviewedBy: "KDV. Trần Minh Khoa",
    reviewedAt: "15/11/2026 13:20",
    description:
      "Cho thuê mặt bằng thương mại góc 2 mặt tiền đường Nguyễn Cơ Thạch, vỉa hè rộng 8m đậu xe thoải mái. Phù hợp mở showroom nội thất, phòng khám quốc tế, ngân hàng hoặc văn phòng đại diện.",
    images: [
      "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=1000&q=82",
      "https://images.unsplash.com/photo-1497215842964-222b430dc094?auto=format&fit=crop&w=1000&q=82",
    ],
  },
];

const presetRejectionReasons = [
  "Thông tin giá bán hoặc diện tích không khớp với mô tả thực tế",
  "Hình ảnh mờ, chứa đóng dấu (watermark) hoặc số điện thoại đè lên ảnh",
  "Địa chỉ bất động sản chưa cụ thể hoặc không thể xác thực",
  "Nội dung mô tả vi phạm quy chuẩn cộng đồng hoặc trùng lặp tin đã đăng",
];

/** Chuyển chuỗi "DD/MM/YYYY HH:mm" hoặc "DD/MM/YYYY" thành timestamp (ms) để so sánh */
function parseSubmittedDateTime(dateTimeStr: string): number {
  const [datePart, timePart = "00:00"] = dateTimeStr.trim().split(" ");
  const [d, m, y] = datePart.split("/").map(Number);
  const [hh, mm] = timePart.split(":").map(Number);
  if (!d || !m || !y) return 0;
  return new Date(y, m - 1, d, hh || 0, mm || 0).getTime();
}

/** Chuyển chuỗi "DD/MM/YYYY" người dùng nhập thành Date (00:00:00) */
function parseDdMmYyyy(input: string): Date | null {
  const cleaned = input.trim();
  const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(cleaned);
  if (!match) return null;
  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1900 || year > 2100) {
    return null;
  }
  const dt = new Date(year, month - 1, day);
  if (dt.getFullYear() !== year || dt.getMonth() !== month - 1 || dt.getDate() !== day) {
    return null;
  }
  return dt;
}

function formatDateToDdMmYyyy(date: Date): string {
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = date.getFullYear();
  return `${dd}/${mm}/${yyyy}`;
}

/** Tự động chèn dấu '/' khi người dùng gõ số ngày/tháng/năm */
function autoFormatDateInput(raw: string): string {
  const cleaned = raw.replace(/[^0-9/]/g, "");
  if (cleaned.includes("/")) {
    return cleaned.slice(0, 10);
  }
  const digits = cleaned.replace(/\D/g, "").slice(0, 8);
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4, 8)}`;
}

/** Thứ tự ưu tiên trạng thái: 1. cho_kiem_duyet -> 2. bi_tu_choi -> 3. da_duyet */
const STATUS_PRIORITY: Record<ModerationStatus, number> = {
  cho_kiem_duyet: 1,
  bi_tu_choi: 2,
  da_duyet: 3,
};

function SearchIcon() {
  return (
    <svg
      className="kdv-search-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg
      className="kdv-dropdown-chevron"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function CalendarIcon() {
  return (
    <svg
      width="15"
      height="15"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.9"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <rect x="3" y="4" width="18" height="18" rx="2" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </svg>
  );
}

function RefreshIcon({ spinning }: { spinning?: boolean }) {
  return (
    <svg
      width="15"
      height="15"
      className={spinning ? "kdv-spin" : ""}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
      <path d="M3 3v5h5" />
      <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
      <path d="M16 16h5v5" />
    </svg>
  );
}

function TrashIcon() {
  return (
    <svg
      width="15"
      height="15"
      viewBox="0 0 24 24"
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

function ArrowLeftIcon() {
  return (
    <svg
      width="17"
      height="17"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m12 19-7-7 7-7" />
      <path d="M19 12H5" />
    </svg>
  );
}

function ExternalMapIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}

type ActiveDropdown = "status" | "submission" | "category" | "date" | null;

export function ModerationQueuePage() {
  const [items, setItems] = useState<ModerationItem[]>(initialQueue);

  // Bộ lọc (Mặc định trạng thái là "cho_kiem_duyet")
  const [searchQuery, setSearchQuery] = useState("");
  const [filterStatus, setFilterStatus] = useState<"ALL" | ModerationStatus>("cho_kiem_duyet");
  const [filterSubmission, setFilterSubmission] = useState<"ALL" | SubmissionType>("ALL");
  const [filterCategory, setFilterCategory] = useState<"ALL" | PropertyCategory>("ALL");
  const [fromDateText, setFromDateText] = useState("");
  const [toDateText, setToDateText] = useState("");

  // Quản lý mở/đóng dropdown
  const [activeDropdown, setActiveDropdown] = useState<ActiveDropdown>(null);
  const [activeDateInput, setActiveDateInput] = useState<"from" | "to" | null>("from");
  const [calendarMonth, setCalendarMonth] = useState<Date>(() => new Date(2026, 10, 1)); // Tháng 11/2026
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Chế độ xem chi tiết (null = đang ở bảng Danh sách tin đăng; khác null = mở Phiếu thẩm định chi tiết)
  const [viewingItemId, setViewingItemId] = useState<string | null>(null);
  const [activeImageIdx, setActiveImageIdx] = useState(0);

  // Dialog nhập lý do từ chối
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const filterBarRef = useRef<HTMLDivElement>(null);
  const rejectTextareaRef = useRef<HTMLTextAreaElement>(null);

  // Đóng dropdown khi click ra ngoài
  useEffect(() => {
    if (!activeDropdown) return;
    const handleOutsideClick = (event: PointerEvent) => {
      if (!filterBarRef.current?.contains(event.target as Node)) {
        setActiveDropdown(null);
      }
    };
    document.addEventListener("pointerdown", handleOutsideClick);
    return () => document.removeEventListener("pointerdown", handleOutsideClick);
  }, [activeDropdown]);

  const fromDateObj = useMemo(() => parseDdMmYyyy(fromDateText), [fromDateText]);
  const toDateObj = useMemo(() => parseDdMmYyyy(toDateText), [toDateText]);

  // Lọc và sắp xếp theo đúng thứ tự ưu tiên:
  // 1. Chờ duyệt mới nhất -> cũ nhất
  // 2. Bị từ chối mới nhất -> cũ nhất
  // 3. Đã duyệt mới nhất -> cũ nhất
  const filteredAndSortedItems = useMemo(() => {
    const filtered = items.filter((item) => {
      if (filterStatus !== "ALL" && item.status !== filterStatus) return false;
      if (filterSubmission !== "ALL" && item.submissionType !== filterSubmission) return false;
      if (filterCategory !== "ALL" && item.category !== filterCategory) return false;

      if (fromDateObj || toDateObj) {
        const itemTs = parseSubmittedDateTime(item.submittedAt);
        if (fromDateObj) {
          const startOfDay = new Date(
            fromDateObj.getFullYear(),
            fromDateObj.getMonth(),
            fromDateObj.getDate(),
            0,
            0,
            0,
            0
          ).getTime();
          if (itemTs < startOfDay) return false;
        }
        if (toDateObj) {
          const endOfDay = new Date(
            toDateObj.getFullYear(),
            toDateObj.getMonth(),
            toDateObj.getDate(),
            23,
            59,
            59,
            999
          ).getTime();
          if (itemTs > endOfDay) return false;
        }
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        return (
          item.id.toLowerCase().includes(q) ||
          item.versionId.toLowerCase().includes(q) ||
          item.title.toLowerCase().includes(q) ||
          item.ownerName.toLowerCase().includes(q) ||
          item.ownerEmail.toLowerCase().includes(q)
        );
      }
      return true;
    });

    return [...filtered].sort((a, b) => {
      const priorityDiff = STATUS_PRIORITY[a.status] - STATUS_PRIORITY[b.status];
      if (priorityDiff !== 0) return priorityDiff;
      return parseSubmittedDateTime(b.submittedAt) - parseSubmittedDateTime(a.submittedAt);
    });
  }, [items, filterStatus, filterSubmission, filterCategory, fromDateObj, toDateObj, searchQuery]);

  const viewingItem = useMemo(
    () => (viewingItemId ? items.find((i) => i.id === viewingItemId) || null : null),
    [items, viewingItemId]
  );

  // Nhãn hiển thị trên nút Thời gian gửi
  const dateFilterLabel = useMemo(() => {
    if (fromDateText && toDateText) return `${fromDateText} - ${toDateText}`;
    if (fromDateText) return `Từ ${fromDateText}`;
    if (toDateText) return `Đến ${toDateText}`;
    return "Tất cả thời gian";
  }, [fromDateText, toDateText]);

  // Dữ liệu lưới lịch (Calendar Grid)
  const calendarDays = useMemo(() => {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const firstDayOfMonth = new Date(year, month, 1);
    const startOffset = (firstDayOfMonth.getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const cells: { date: Date; inCurrentMonth: boolean }[] = [];
    for (let i = startOffset - 1; i >= 0; i--) {
      cells.push({ date: new Date(year, month, -i), inCurrentMonth: false });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ date: new Date(year, month, d), inCurrentMonth: true });
    }
    while (cells.length % 7 !== 0) {
      const nextDay = cells.length - (startOffset + daysInMonth) + 1;
      cells.push({ date: new Date(year, month + 1, nextDay), inCurrentMonth: false });
    }
    return cells;
  }, [calendarMonth]);

  const handlePickCalendarDate = (date: Date) => {
    const formatted = formatDateToDdMmYyyy(date);
    if (activeDateInput === "from" || !activeDateInput) {
      setFromDateText(formatted);
      if (toDateObj && date.getTime() > toDateObj.getTime()) {
        setToDateText("");
      }
      setActiveDateInput("to");
    } else {
      if (fromDateObj && date.getTime() < fromDateObj.getTime()) {
        setToDateText(fromDateText);
        setFromDateText(formatted);
      } else {
        setToDateText(formatted);
      }
    }
  };

  const handleClearFilters = () => {
    setSearchQuery("");
    setFilterStatus("cho_kiem_duyet");
    setFilterSubmission("ALL");
    setFilterCategory("ALL");
    setFromDateText("");
    setToDateText("");
    setActiveDropdown(null);
    toast.info("Đã đặt lại toàn bộ bộ lọc về mặc định (Chờ kiểm duyệt).", "Đã xóa bộ lọc");
  };

  const handleRefresh = () => {
    setIsRefreshing(true);
    setActiveDropdown(null);
    setTimeout(() => {
      setIsRefreshing(false);
      toast.success("Đã đồng bộ dữ liệu tin đăng mới nhất từ hệ thống.", "Làm mới thành công");
    }, 350);
  };

  const handleOpenDetail = (item: ModerationItem) => {
    setViewingItemId(item.id);
    setActiveImageIdx(0);
    setRejectModalOpen(false);
    setRejectReason(item.rejectionReason || "");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const handleBackToList = () => {
    setRejectModalOpen(false);
    setViewingItemId(null);
  };

  const handleApprove = (item: ModerationItem) => {
    const nowStr = "21/11/2026 19:35";
    const reviewer = "KDV. Trần Minh Khoa";
    setItems((prev) =>
      prev.map((entry) => {
        if (entry.id !== item.id) return entry;
        const updatedHistory: VersionHistoryEntry[] = [
          ...entry.versionHistory.filter((h) => h.attempt !== entry.versionNumber),
          {
            attempt: entry.versionNumber,
            decision: "da_duyet",
            reviewerName: reviewer,
            reviewedAt: nowStr,
          },
        ];
        return {
          ...entry,
          status: "da_duyet",
          rejectionReason: undefined,
          versionHistory: updatedHistory,
          reviewedBy: reviewer,
          reviewedAt: nowStr,
        };
      })
    );
    toast.success(
      `Đã phê duyệt hiển thị tin ${item.id} (${item.versionId}). Tin đã được xuất bản công khai.`,
      "Phê duyệt thành công"
    );
  };

  const handleOpenRejectDialog = (item: ModerationItem) => {
    setRejectReason(item.rejectionReason || "");
    setRejectModalOpen(true);
    setTimeout(() => {
      rejectTextareaRef.current?.focus();
    }, 50);
  };

  const handleConfirmReject = () => {
    if (!viewingItem) return;
    if (!rejectReason.trim()) {
      toast.error(
        "Vui lòng chọn hoặc nhập lý do từ chối chi tiết trước khi xác nhận.",
        "Thiếu lý do từ chối"
      );
      rejectTextareaRef.current?.focus();
      return;
    }

    const nowStr = "21/11/2026 19:35";
    const reviewer = "KDV. Trần Minh Khoa";
    const trimmedReason = rejectReason.trim();
    setItems((prev) =>
      prev.map((entry) => {
        if (entry.id !== viewingItem.id) return entry;
        const updatedHistory: VersionHistoryEntry[] = [
          ...entry.versionHistory.filter((h) => h.attempt !== entry.versionNumber),
          {
            attempt: entry.versionNumber,
            decision: "bi_tu_choi",
            reviewerName: reviewer,
            reviewedAt: nowStr,
            reason: trimmedReason,
          },
        ];
        return {
          ...entry,
          status: "bi_tu_choi",
          rejectionReason: trimmedReason,
          versionHistory: updatedHistory,
          reviewedBy: reviewer,
          reviewedAt: nowStr,
        };
      })
    );
    setRejectModalOpen(false);
    toast.info(
      `Đã từ chối tin ${viewingItem.id}. Lý do từ chối đã được gửi để chủ tin chỉnh sửa và nộp lại.`,
      "Đã từ chối tin đăng"
    );
  };

  // ============================================================================
  // GIAO DIỆN 1: PHIẾU THẨM ĐỊNH CHI TIẾT (TOÀN MÀN HÌNH KHI BẤM XEM & THẨM ĐỊNH)
  // ============================================================================
  if (viewingItem) {
    const safeImgIndex =
      activeImageIdx < viewingItem.images.length ? activeImageIdx : 0;
    const googleMapsUrl = `https://www.google.com/maps?q=${encodeURIComponent(
      `${viewingItem.latitude},${viewingItem.longitude}`
    )}`;
    const isPendingModeration = viewingItem.status === "cho_kiem_duyet";

    return (
      <div className="kdv-detail-view">
        {/* Thanh điều hướng & Nút Back */}
        <div className="kdv-detail-topbar">
          <div style={{ display: "flex", alignItems: "center", gap: "1rem", flexWrap: "wrap" }}>
            <button type="button" className="kdv-back-btn" onClick={handleBackToList}>
              <ArrowLeftIcon />
            </button>
            <div>
              <div style={{ fontSize: "0.76rem", fontWeight: 700, color: "#14775d", letterSpacing: "0.03em" }}>
                PHIẾU THẨM ĐỊNH CHI TIẾT • {viewingItem.id} ({viewingItem.versionId})
              </div>
              <h2 style={{ margin: "2px 0 0", fontSize: "1.18rem", fontWeight: 800, color: "#0f172a" }}>
                {viewingItem.title}
              </h2>
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "0.6rem", flexWrap: "wrap" }}>
            <span className={`staff-badge ${viewingItem.submissionType === "nop_lai" ? "blue" : "slate"}`}>
              {viewingItem.submissionType === "nop_lai"
                ? `Tin sửa & nộp lại (Lần gửi thứ ${viewingItem.versionNumber})`
                : "Tin đăng mới (Lần gửi thứ 1)"}
            </span>
            {viewingItem.status === "cho_kiem_duyet" && (
              <span className="staff-badge amber">Chờ kiểm duyệt</span>
            )}
            {viewingItem.status === "da_duyet" && (
              <span className="staff-badge green">Đã phê duyệt</span>
            )}
            {viewingItem.status === "bi_tu_choi" && (
              <span className="staff-badge red">Đã từ chối</span>
            )}
          </div>
        </div>

        {/* Bố cục: Nếu đang chờ duyệt thì hiện 2 cột (có cột Quyết định bên phải); nếu Đã duyệt / Đã từ chối thì ẩn phần Quyết định bên phải và mở rộng toàn chiều ngang */}
        <div
          className="kdv-detail-layout"
          style={isPendingModeration ? undefined : { gridTemplateColumns: "1fr" }}
        >
          {/* CỘT TRÁI: TOÀN BỘ THÔNG TIN BẤT ĐỘNG SẢN & HÌNH ẢNH */}
          <div className="kdv-detail-main-col">
            {/* 1. Hình ảnh đính kèm */}
            <div className="staff-card" style={{ marginBottom: 0 }}>
              <div className="staff-card-header">
                <div>
                  <h3 className="staff-card-title">
                    Hình ảnh bất động sản ({viewingItem.images.length} / 12 ảnh)
                  </h3>
                  <p className="staff-card-subtitle">
                    Kiểm tra độ rõ nét, tính xác thực, không chứa đóng dấu (watermark) hoặc số điện thoại đè lên ảnh
                  </p>
                </div>
              </div>

              <div style={{ padding: "1.25rem" }}>
                <div className="kdv-gallery-stage">
                  <img
                    src={viewingItem.images[safeImgIndex]}
                    alt={`${viewingItem.title} - ảnh ${safeImgIndex + 1}`}
                  />
                  <span className="kdv-gallery-counter">
                    Ảnh {safeImgIndex + 1} / {viewingItem.images.length}
                  </span>
                </div>

                <div className="kdv-gallery-thumbs">
                  {viewingItem.images.map((imgUrl, idx) => (
                    <button
                      key={imgUrl + idx}
                      type="button"
                      className={`kdv-gallery-thumb ${safeImgIndex === idx ? "active" : ""}`}
                      onClick={() => setActiveImageIdx(idx)}
                      title={`Xem ảnh ${idx + 1}`}
                    >
                      <img src={imgUrl} alt={`Thumb ${idx + 1}`} />
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* 2. Thông tin chi tiết tin đăng (Khớp với form đăng tin của người dùng) */}
            <div className="staff-card" style={{ marginBottom: 0 }}>
              <div className="staff-card-header">
                <div>
                  <h3 className="staff-card-title">Thông tin chi tiết bất động sản</h3>
                  <p className="staff-card-subtitle">
                    Toàn bộ thông tin người dùng đã kê khai khi đăng tin
                  </p>
                </div>
              </div>

              <div style={{ padding: "1.25rem", display: "flex", flexDirection: "column", gap: "1.1rem" }}>
                <div className="kdv-info-grid">
                  <div className="kdv-info-box">
                    <div className="kdv-info-box-label">
                      {viewingItem.transactionType === "cho_thue" ? "Giá cho thuê" : "Giá bán niêm yết"}
                    </div>
                    <div className="kdv-info-box-value highlight">{viewingItem.priceDisplay}</div>
                    <div style={{ fontSize: "0.76rem", color: "#64748b", marginTop: "3px" }}>
                      Thành tiền: {viewingItem.rawPriceVnd}
                    </div>
                  </div>

                  <div className="kdv-info-box">
                    <div className="kdv-info-box-label">Diện tích</div>
                    <div className="kdv-info-box-value">{viewingItem.areaSize}</div>
                    <div style={{ fontSize: "0.76rem", color: "#64748b", marginTop: "3px" }}>
                      Diện tích mặt sàn sử dụng
                    </div>
                  </div>

                  <div className="kdv-info-box">
                    <div className="kdv-info-box-label">Loại hình & Loại bất động sản</div>
                    <div className="kdv-info-box-value">{viewingItem.category}</div>
                    <div style={{ fontSize: "0.76rem", color: "#14775d", fontWeight: 600, marginTop: "3px" }}>
                      Loại hình:{" "}
                      {viewingItem.transactionType === "ban"
                        ? "Bán"
                        : `Cho thuê (${viewingItem.rentPeriod || "Theo tháng"})`}
                    </div>
                  </div>

                  <div className="kdv-info-box" style={{ gridColumn: "span 2" }}>
                    <div className="kdv-info-box-label">Vị trí & Địa chỉ</div>
                    <div className="kdv-info-box-value">
                      {viewingItem.streetAddress}, {viewingItem.ward}, {viewingItem.province}
                    </div>
                    <div style={{ fontSize: "0.77rem", color: "#64748b", marginTop: "4px" }}>
                      Tỉnh/Thành phố: <strong>{viewingItem.province}</strong> • Phường/Xã:{" "}
                      <strong>{viewingItem.ward}</strong>
                    </div>
                  </div>

                  <div className="kdv-info-box">
                    <div className="kdv-info-box-label">Tọa độ ghim trên bản đồ</div>
                    <div className="kdv-info-box-value" style={{ fontSize: "0.86rem" }}>
                      Vĩ độ: {viewingItem.latitude} • Kinh độ: {viewingItem.longitude}
                    </div>
                    <div style={{ marginTop: "7px" }}>
                      <a
                        href={googleMapsUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="staff-btn staff-btn-outline staff-btn-sm"
                        style={{
                          textDecoration: "none",
                          color: "#14775d",
                          borderColor: "#a7f3d0",
                          backgroundColor: "#ecfdf5",
                          fontWeight: 600,
                          fontSize: "0.75rem",
                          padding: "4px 9px",
                        }}
                      >
                        <ExternalMapIcon />
                        Đối chiếu trên Google Maps ↗
                      </a>
                    </div>
                  </div>
                </div>

                {/* Nội dung mô tả chi tiết */}
                <div>
                  <div style={{ fontSize: "0.84rem", fontWeight: 700, color: "#334155", marginBottom: "6px" }}>
                    Mô tả chi tiết
                  </div>
                  <div
                    style={{
                      fontSize: "0.9rem",
                      lineHeight: 1.65,
                      color: "#1e293b",
                      backgroundColor: "#f8fafc",
                      border: "1px solid #e2e8f0",
                      borderRadius: "10px",
                      padding: "1rem 1.1rem",
                      whiteSpace: "pre-line",
                    }}
                  >
                    {viewingItem.description}
                  </div>
                </div>

                {/* Phần thông tin Chủ sở hữu, Thời điểm gửi duyệt & Lịch sử phiên bản */}
                <div
                  style={{
                    backgroundColor: "#f0fdf4",
                    border: "1px solid #bbf7d0",
                    borderRadius: "10px",
                    padding: "1rem 1.15rem",
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))",
                    gap: "1rem",
                    fontSize: "0.84rem",
                  }}
                >
                  <div>
                    <div style={{ color: "#166534", fontSize: "0.76rem", fontWeight: 600 }}>
                      Chủ sở hữu tin đăng
                    </div>
                    <div style={{ fontWeight: 700, color: "#0f172a", marginTop: "3px", fontSize: "0.92rem" }}>
                      {viewingItem.ownerName}
                    </div>
                    <div style={{ color: "#475569", fontSize: "0.8rem", marginTop: "2px" }}>
                      {viewingItem.ownerEmail} • {viewingItem.ownerPhone}
                    </div>
                  </div>

                  <div>
                    <div style={{ color: "#166534", fontSize: "0.76rem", fontWeight: 600 }}>
                      Thời điểm gửi duyệt
                    </div>
                    <div style={{ fontWeight: 700, color: "#0f172a", marginTop: "3px", fontSize: "0.92rem" }}>
                      {viewingItem.submittedAt}
                    </div>
                    <div style={{ color: "#475569", fontSize: "0.8rem", marginTop: "2px" }}>
                      {viewingItem.submissionType === "nop_lai" ? "Gửi duyệt sau chỉnh sửa" : "Gửi duyệt lần đầu"}
                    </div>
                  </div>

                  <div style={{ gridColumn: viewingItem.versionHistory.length > 0 ? "span 2" : undefined }}>
                    <div style={{ color: "#166534", fontSize: "0.76rem", fontWeight: 600 }}>
                      Lịch sử phiên bản
                    </div>
                    <div style={{ fontWeight: 700, color: "#0f172a", marginTop: "3px", fontSize: "0.92rem" }}>
                      Số lần gửi nộp: {viewingItem.versionNumber} lần
                    </div>

                    {viewingItem.versionHistory.length > 0 ? (
                      <div style={{ marginTop: "7px", display: "flex", flexDirection: "column", gap: "6px" }}>
                        {viewingItem.versionHistory.map((hist) =>
                          hist.decision === "da_duyet" ? (
                            <div
                              key={hist.attempt}
                              style={{
                                fontSize: "0.8rem",
                                color: "#065f46",
                                backgroundColor: "#ecfdf5",
                                border: "1px solid #a7f3d0",
                                borderRadius: "7px",
                                padding: "6px 10px",
                                lineHeight: 1.45,
                              }}
                            >
                              <div>
                                <strong>Lần {hist.attempt} — Đã phê duyệt</strong>
                              </div>
                              <div style={{ fontSize: "0.76rem", color: "#047857", marginTop: "2px" }}>
                                Kiểm duyệt viên: <strong>{hist.reviewerName}</strong> • Thời điểm:{" "}
                                <strong>{hist.reviewedAt}</strong>
                              </div>
                            </div>
                          ) : (
                            <div
                              key={hist.attempt}
                              style={{
                                fontSize: "0.8rem",
                                color: "#991b1b",
                                backgroundColor: "#fef2f2",
                                border: "1px solid #fecaca",
                                borderRadius: "7px",
                                padding: "6px 10px",
                                lineHeight: 1.45,
                              }}
                            >
                              <div>
                                <strong>Lần {hist.attempt} bị từ chối:</strong> {hist.reason}
                              </div>
                              <div style={{ fontSize: "0.76rem", color: "#b91c1c", marginTop: "2px" }}>
                                Kiểm duyệt viên: <strong>{hist.reviewerName}</strong> • Thời điểm:{" "}
                                <strong>{hist.reviewedAt}</strong>
                              </div>
                            </div>
                          )
                        )}
                      </div>
                    ) : (
                      <div style={{ color: "#475569", fontSize: "0.8rem", marginTop: "2px" }}>
                        Chưa có lịch sử kiểm duyệt trước đó
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* CỘT PHẢI: QUYẾT ĐỊNH CỦA KIỂM DUYỆT VIÊN (CHỈ HIỆN KHI TIN ĐANG CHỜ KIỂM DUYỆT) */}
          {isPendingModeration && (
            <div className="kdv-detail-side-col">
              <div className="staff-card" style={{ marginBottom: 0 }}>
                <div className="staff-card-header">
                  <div>
                    <h3 className="staff-card-title">Quyết định của Kiểm duyệt viên</h3>
                    <p className="staff-card-subtitle">
                      Phê duyệt hiển thị công khai hoặc từ chối yêu cầu chỉnh sửa
                    </p>
                  </div>
                </div>

                <div style={{ padding: "1.25rem", display: "flex", flexDirection: "column", gap: "1rem" }}>
                  {/* 2 nút Phê duyệt & Từ chối nằm trên cùng */}
                  <div style={{ display: "flex", flexDirection: "column", gap: "0.65rem" }}>
                    <button
                      type="button"
                      className="staff-btn staff-btn-success"
                      style={{ width: "100%", padding: "0.75rem 1rem", fontSize: "0.9rem" }}
                      onClick={() => handleApprove(viewingItem)}
                    >
                      ✓ Phê duyệt hiển thị công khai
                    </button>

                    <button
                      type="button"
                      className="staff-btn staff-btn-danger"
                      style={{ width: "100%", padding: "0.7rem 1rem", fontSize: "0.88rem" }}
                      onClick={() => handleOpenRejectDialog(viewingItem)}
                    >
                      ✕ Từ chối & Yêu cầu chỉnh sửa
                    </button>
                  </div>

                  <div
                    style={{
                      backgroundColor: "#fffbeb",
                      border: "1px solid #fde68a",
                      borderRadius: "9px",
                      padding: "0.85rem 1rem",
                      fontSize: "0.83rem",
                      color: "#92400e",
                      lineHeight: 1.5,
                    }}
                  >
                    <strong>Trạng thái hiện tại:</strong> Tin đang chờ kiểm duyệt. Nhấn{" "}
                    <strong>Phê duyệt</strong> để xuất bản ngay hoặc nhấn <strong>Từ chối</strong> để mở hộp thoại nhập lý do từ chối.
                  </div>

                  {viewingItem.versionHistory.length > 0 && (
                    <div
                      style={{
                        backgroundColor: "#eff6ff",
                        border: "1px solid #bfdbfe",
                        borderRadius: "9px",
                        padding: "0.85rem 1rem",
                        fontSize: "0.83rem",
                        color: "#1e3a8a",
                        lineHeight: 1.5,
                      }}
                    >
                      <div style={{ fontWeight: 700, marginBottom: "4px" }}>
                        Lịch sử các lần kiểm duyệt trước:
                      </div>
                      {viewingItem.versionHistory.map((hist) => (
                        <div key={hist.attempt} style={{ marginTop: "4px" }}>
                          <div>
                            • <strong>Lần {hist.attempt}:</strong>{" "}
                            {hist.reason ? <em>“{hist.reason}”</em> : "Đã phê duyệt"}
                          </div>
                          <div style={{ fontSize: "0.75rem", color: "#1d4ed8", paddingLeft: "10px" }}>
                            ({hist.reviewerName} • {hist.reviewedAt})
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* DIALOG (MODAL) CHỌN NHANH & NHẬP LÝ DO TỪ CHỐI KÈM 2 NÚT HỦY VÀ XÁC NHẬN */}
        {rejectModalOpen && (
          <div
            className="staff-modal-backdrop"
            role="dialog"
            aria-modal="true"
            aria-labelledby="reject-dialog-title"
            onClick={() => setRejectModalOpen(false)}
          >
            <div className="staff-modal" onClick={(e) => e.stopPropagation()}>
              <div className="staff-modal-header">
                <h3 id="reject-dialog-title">Từ chối duyệt tin đăng {viewingItem.id}</h3>
                <button
                  type="button"
                  className="staff-btn staff-btn-outline staff-btn-sm"
                  onClick={() => setRejectModalOpen(false)}
                >
                  Đóng
                </button>
              </div>

              <div className="staff-modal-body">
                <div style={{ fontSize: "0.86rem", color: "#475569", lineHeight: 1.45 }}>
                  Tin đăng: <strong style={{ color: "#0f172a" }}>{viewingItem.title}</strong>
                </div>

                <div>
                  <div style={{ fontSize: "0.8rem", fontWeight: 600, color: "#334155", marginBottom: "7px" }}>
                    Chọn nhanh mẫu lý do từ chối:
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                    {presetRejectionReasons.map((reason) => (
                      <button
                        key={reason}
                        type="button"
                        className={`kdv-preset-reason-btn ${rejectReason === reason ? "active" : ""}`}
                        onClick={() => setRejectReason(reason)}
                      >
                        + {reason}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label
                    htmlFor="dialog-reject-reason"
                    style={{
                      display: "block",
                      fontSize: "0.82rem",
                      fontWeight: 600,
                      color: "#0f172a",
                      marginBottom: "6px",
                    }}
                  >
                    Nội dung lý do từ chối chi tiết gửi tới người đăng <span style={{ color: "#dc2626" }}>*</span>
                  </label>
                  <textarea
                    ref={rejectTextareaRef}
                    id="dialog-reject-reason"
                    rows={4}
                    className="staff-textarea"
                    placeholder="Nhập rõ điểm chưa đạt để chủ tin chỉnh sửa và gửi duyệt lại..."
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                  />
                </div>
              </div>

              <div className="staff-modal-footer">
                <button
                  type="button"
                  className="staff-btn staff-btn-outline"
                  onClick={() => setRejectModalOpen(false)}
                >
                  Hủy
                </button>
                <button
                  type="button"
                  className="staff-btn staff-btn-danger"
                  onClick={handleConfirmReject}
                >
                  Xác nhận
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ============================================================================
  // GIAO DIỆN 2: MÀN HÌNH DANH SÁCH TIN ĐĂNG (RỘNG NGANG TOÀN MÀN HÌNH)
  // ============================================================================
  const currentStatusLabel =
    STATUS_OPTIONS.find((o) => o.value === filterStatus)?.label || "Chờ kiểm duyệt";
  const currentSubmissionLabel =
    SUBMISSION_OPTIONS.find((o) => o.value === filterSubmission)?.label || "Tất cả nguồn nộp";
  const currentCategoryLabel =
    PROPERTY_TYPE_OPTIONS.find((o) => o.value === filterCategory)?.label || "Tất cả loại BĐS";

  return (
    <div>
      <div className="staff-page-header">
        <div className="staff-page-title-group">
          <h1>Thẩm định tin đăng bất động sản</h1>
          <p>
            Kiểm tra nội dung tin đăng, hình ảnh và thông tin bất động sản trước khi cho phép hiển thị công khai trên sàn.
          </p>
        </div>
      </div>

      {/* THANH BỘ LỌC THIẾT KẾ ĐỒNG BỘ VỚI TRANG CHỦ NGƯỜI DÙNG */}
      <div className="kdv-filter-bar" ref={filterBarRef}>
        {/* 1. Ô tìm kiếm có kính lúp ở lề trước */}
        <div className="kdv-filter-search-col">
          <label className="kdv-filter-label" htmlFor="mod-search">
            Tìm kiếm mã tin, tiêu đề, chủ tin
          </label>
          <div className="kdv-search-input-wrap">
            <SearchIcon />
            <input
              id="mod-search"
              type="text"
              className="kdv-search-input"
              placeholder="Nhập mã TIN-2026..., email hoặc tên người đăng..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>

        {/* 2. Bộ lọc Trạng thái thẩm định */}
        <div className="kdv-dropdown-field">
          <span className="kdv-filter-label">Trạng thái thẩm định</span>
          <button
            type="button"
            className={`kdv-dropdown-btn ${activeDropdown === "status" ? "active" : ""}`}
            onClick={() => setActiveDropdown(activeDropdown === "status" ? null : "status")}
            aria-expanded={activeDropdown === "status"}
          >
            <span className="kdv-dropdown-btn-text">{currentStatusLabel}</span>
            <ChevronIcon />
          </button>
          {activeDropdown === "status" && (
            <div className="kdv-dropdown-menu" role="listbox" aria-label="Trạng thái thẩm định">
              <div className="kdv-dropdown-options">
                {STATUS_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    className={`kdv-dropdown-option ${filterStatus === opt.value ? "active" : ""}`}
                    onClick={() => {
                      setFilterStatus(opt.value);
                      setActiveDropdown(null);
                    }}
                  >
                    <span>{opt.label}</span>
                    {filterStatus === opt.value && <span>✓</span>}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* 3. Bộ lọc Nguồn gửi duyệt */}
        <div className="kdv-dropdown-field">
          <span className="kdv-filter-label">Nguồn gửi duyệt</span>
          <button
            type="button"
            className={`kdv-dropdown-btn ${activeDropdown === "submission" ? "active" : ""}`}
            onClick={() => setActiveDropdown(activeDropdown === "submission" ? null : "submission")}
            aria-expanded={activeDropdown === "submission"}
          >
            <span className="kdv-dropdown-btn-text">{currentSubmissionLabel}</span>
            <ChevronIcon />
          </button>
          {activeDropdown === "submission" && (
            <div className="kdv-dropdown-menu" role="listbox" aria-label="Nguồn gửi duyệt">
              <div className="kdv-dropdown-options">
                {SUBMISSION_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    className={`kdv-dropdown-option ${filterSubmission === opt.value ? "active" : ""}`}
                    onClick={() => {
                      setFilterSubmission(opt.value);
                      setActiveDropdown(null);
                    }}
                  >
                    <span>{opt.label}</span>
                    {filterSubmission === opt.value && <span>✓</span>}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* 4. Bộ lọc Loại bất động sản */}
        <div className="kdv-dropdown-field">
          <span className="kdv-filter-label">Loại bất động sản</span>
          <button
            type="button"
            className={`kdv-dropdown-btn ${activeDropdown === "category" ? "active" : ""}`}
            onClick={() => setActiveDropdown(activeDropdown === "category" ? null : "category")}
            aria-expanded={activeDropdown === "category"}
          >
            <span className="kdv-dropdown-btn-text">{currentCategoryLabel}</span>
            <ChevronIcon />
          </button>
          {activeDropdown === "category" && (
            <div className="kdv-dropdown-menu" role="listbox" aria-label="Loại bất động sản">
              <div className="kdv-dropdown-options">
                {PROPERTY_TYPE_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    className={`kdv-dropdown-option ${filterCategory === opt.value ? "active" : ""}`}
                    onClick={() => {
                      setFilterCategory(opt.value);
                      setActiveDropdown(null);
                    }}
                  >
                    <span>{opt.label}</span>
                    {filterCategory === opt.value && <span>✓</span>}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* 5. Bộ lọc Thời gian gửi duyệt */}
        <div className="kdv-dropdown-field">
          <span className="kdv-filter-label">Thời gian gửi duyệt</span>
          <button
            type="button"
            className={`kdv-dropdown-btn ${activeDropdown === "date" ? "active" : ""}`}
            onClick={() => {
              const nextState = activeDropdown === "date" ? null : "date";
              setActiveDropdown(nextState);
              if (nextState === "date" && !activeDateInput) {
                setActiveDateInput("from");
              }
            }}
            aria-expanded={activeDropdown === "date"}
          >
            <span className="kdv-dropdown-btn-text">{dateFilterLabel}</span>
            <ChevronIcon />
          </button>

          {activeDropdown === "date" && (
            <div
              className="kdv-dropdown-menu kdv-date-menu align-right"
              role="dialog"
              aria-label="Chọn khoảng thời gian gửi duyệt"
            >
              <div className="kdv-date-inputs-grid">
                <label className="kdv-date-input-group">
                  <span>Từ ngày (dd/mm/yyyy)</span>
                  <div className={`kdv-date-input-box ${activeDateInput === "from" ? "focused" : ""}`}>
                    <input
                      type="text"
                      placeholder="dd/mm/yyyy"
                      value={fromDateText}
                      onFocus={() => setActiveDateInput("from")}
                      onClick={() => setActiveDateInput("from")}
                      onChange={(e) => {
                        const formatted = autoFormatDateInput(e.target.value);
                        setFromDateText(formatted);
                        const parsed = parseDdMmYyyy(formatted);
                        if (parsed) {
                          setCalendarMonth(new Date(parsed.getFullYear(), parsed.getMonth(), 1));
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="kdv-date-cal-trigger"
                      onClick={() => setActiveDateInput("from")}
                      title="Chọn từ lịch"
                    >
                      <CalendarIcon />
                    </button>
                  </div>
                </label>

                <label className="kdv-date-input-group">
                  <span>Đến ngày (dd/mm/yyyy)</span>
                  <div className={`kdv-date-input-box ${activeDateInput === "to" ? "focused" : ""}`}>
                    <input
                      type="text"
                      placeholder="dd/mm/yyyy"
                      value={toDateText}
                      onFocus={() => setActiveDateInput("to")}
                      onClick={() => setActiveDateInput("to")}
                      onChange={(e) => {
                        const formatted = autoFormatDateInput(e.target.value);
                        setToDateText(formatted);
                        const parsed = parseDdMmYyyy(formatted);
                        if (parsed) {
                          setCalendarMonth(new Date(parsed.getFullYear(), parsed.getMonth(), 1));
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="kdv-date-cal-trigger"
                      onClick={() => setActiveDateInput("to")}
                      title="Chọn từ lịch"
                    >
                      <CalendarIcon />
                    </button>
                  </div>
                </label>
              </div>

              {activeDateInput && (
                <div className="kdv-calendar-widget">
                  <div className="kdv-cal-header">
                    <button
                      type="button"
                      className="kdv-cal-nav-btn"
                      onClick={() =>
                        setCalendarMonth(
                          new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() - 1, 1)
                        )
                      }
                      aria-label="Tháng trước"
                    >
                      ‹
                    </button>
                    <span className="kdv-cal-title">
                      Tháng {String(calendarMonth.getMonth() + 1).padStart(2, "0")} /{" "}
                      {calendarMonth.getFullYear()}
                      <small style={{ fontWeight: 500, color: "#14775d", marginLeft: "6px" }}>
                        ({activeDateInput === "from" ? "Chọn Từ ngày" : "Chọn Đến ngày"})
                      </small>
                    </span>
                    <button
                      type="button"
                      className="kdv-cal-nav-btn"
                      onClick={() =>
                        setCalendarMonth(
                          new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 1)
                        )
                      }
                      aria-label="Tháng sau"
                    >
                      ›
                    </button>
                  </div>

                  <div className="kdv-cal-weekdays">
                    <span>T2</span>
                    <span>T3</span>
                    <span>T4</span>
                    <span>T5</span>
                    <span>T6</span>
                    <span>T7</span>
                    <span>CN</span>
                  </div>

                  <div className="kdv-cal-days">
                    {calendarDays.map(({ date, inCurrentMonth }) => {
                      const dStr = formatDateToDdMmYyyy(date);
                      const isSelectedFrom = fromDateText === dStr;
                      const isSelectedTo = toDateText === dStr;
                      const isSelected = isSelectedFrom || isSelectedTo;
                      const isInRange =
                        fromDateObj &&
                        toDateObj &&
                        date.getTime() > fromDateObj.getTime() &&
                        date.getTime() < toDateObj.getTime();

                      return (
                        <button
                          key={dStr}
                          type="button"
                          className={`kdv-cal-day ${!inCurrentMonth ? "muted" : ""} ${
                            isSelected ? "selected" : ""
                          } ${isInRange ? "in-range" : ""}`}
                          onClick={() => handlePickCalendarDate(date)}
                        >
                          {date.getDate()}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              <div className="kdv-date-menu-footer">
                <button
                  type="button"
                  className="staff-btn staff-btn-outline staff-btn-sm"
                  onClick={() => {
                    setFromDateText("");
                    setToDateText("");
                  }}
                >
                  Xóa ngày
                </button>
                <button
                  type="button"
                  className="staff-btn staff-btn-primary staff-btn-sm"
                  style={{ backgroundColor: "#14775d" }}
                  onClick={() => setActiveDropdown(null)}
                >
                  Áp dụng
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 6. Hai nút: Xóa bộ lọc (Icon sọt rác) & Làm mới */}
        <div className="kdv-filter-actions">
          <button
            type="button"
            className="kdv-filter-action-btn clear-btn"
            onClick={handleClearFilters}
            title="Xóa toàn bộ bộ lọc"
          >
            <TrashIcon />
            Xóa bộ lọc
          </button>

          <button
            type="button"
            className="kdv-filter-action-btn refresh-btn"
            onClick={handleRefresh}
            title="Lấy dữ liệu tin đăng mới nhất"
          >
            <RefreshIcon spinning={isRefreshing} />
            Làm mới
          </button>
        </div>
      </div>

      {/* BẢNG DANH SÁCH TIN ĐĂNG (RỘNG NGANG TOÀN MÀN HÌNH) */}
      <div className="staff-card">
        <div className="staff-card-header">
          <div>
            <h3 className="staff-card-title">
              Danh sách tin đăng ({filteredAndSortedItems.length})
            </h3>
            <p className="staff-card-subtitle">
              Sắp xếp ưu tiên: Chờ kiểm duyệt (mới nhất → cũ nhất) → Đã từ chối (mới nhất → cũ nhất) → Đã phê duyệt (mới nhất → cũ nhất)
            </p>
          </div>
        </div>

        <div className="staff-table-wrap">
          <table className="staff-table">
            <thead>
              <tr>
                <th>Mã tin / Phiên bản</th>
                <th>Tiêu đề & Phân loại</th>
                <th>Nguồn nộp</th>
                <th>Ngày gửi</th>
                <th>Trạng thái</th>
                <th style={{ textAlign: "right" }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredAndSortedItems.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div style={{ fontWeight: 700, color: "#0f3d32" }}>{item.id}</div>
                    <div style={{ fontSize: "0.74rem", color: "#64748b" }}>
                      {item.versionId} (Lần gửi {item.versionNumber})
                    </div>
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: "#0f172a", lineHeight: 1.4 }}>
                      {item.title}
                    </div>
                    <div style={{ fontSize: "0.78rem", color: "#64748b", marginTop: "3px" }}>
                      <span style={{ color: "#14775d", fontWeight: 600 }}>{item.category}</span> •{" "}
                      <strong>{item.priceDisplay}</strong> • {item.areaSize} • Chủ tin: {item.ownerName}
                    </div>
                  </td>
                  <td>
                    <span
                      className={`staff-badge ${
                        item.submissionType === "nop_lai" ? "blue" : "slate"
                      }`}
                    >
                      {item.submissionType === "nop_lai" ? "Tin nộp lại" : "Tin đăng mới"}
                    </span>
                  </td>
                  <td style={{ whiteSpace: "nowrap", fontWeight: 500, color: "#334155" }}>
                    {item.submittedAt}
                  </td>
                  <td>
                    {item.status === "cho_kiem_duyet" && (
                      <span className="staff-badge amber">Chờ kiểm duyệt</span>
                    )}
                    {item.status === "da_duyet" && (
                      <span className="staff-badge green">Đã phê duyệt</span>
                    )}
                    {item.status === "bi_tu_choi" && (
                      <span className="staff-badge red">Đã từ chối</span>
                    )}
                  </td>
                  <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {item.status === "cho_kiem_duyet" ? (
                      <button
                        type="button"
                        className="staff-btn staff-btn-primary staff-btn-sm"
                        style={{ backgroundColor: "#14775d", padding: "0.45rem 0.85rem" }}
                        onClick={() => handleOpenDetail(item)}
                      >
                        Xem và thẩm định
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="staff-btn staff-btn-outline staff-btn-sm"
                        style={{ padding: "0.45rem 0.95rem" }}
                        onClick={() => handleOpenDetail(item)}
                      >
                        Xem
                      </button>
                    )}
                  </td>
                </tr>
              ))}

              {filteredAndSortedItems.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", padding: "2.5rem", color: "#64748b" }}>
                    Không có tin đăng nào khớp với điều kiện lọc hiện tại.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
