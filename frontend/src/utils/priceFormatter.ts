/**
 * Tiện ích định dạng giá gốc và giá quy đổi hiển thị theo đúng hợp đồng TK-06, TK-07 (hop-dong-tim-kiem-sprint-1.md).
 * - Không ép kiểu qua JavaScript Number (float/double) để chia/lọc nhằm bảo toàn độ chính xác chuỗi thập phân từ BigDecimal.
 * - Chỉ nhóm chữ số hàng nghìn bằng dấu chấm (.) và phần thập phân bằng dấu phẩy (,) cho giao diện tiếng Việt.
 * - Nếu số dương làm tròn thành "0.00", hiển thị "< 0,01 [đơn vị]" (không hiển thị miễn phí).
 */

export type LoaiGiaoDich = "ban" | "cho_thue";
export type LoaiBds = "can_ho" | "nha_rieng" | "dat" | "phong_tro" | "khac";
export type DonViGia =
  | "vnd_tong"
  | "vnd_m2"
  | "vnd_ngay"
  | "vnd_thang"
  | "vnd_nam"
  | "vnd_m2_ngay"
  | "vnd_m2_thang"
  | string;

export interface GiaQuyDoiHienThiDto {
  giaTri: string;
  donVi: string;
  chuThich: string;
}

const DON_VI_GIA_LABELS: Record<string, string> = {
  vnd_tong: "VNĐ",
  vnd_m2: "VNĐ/m²",
  vnd_ngay: "VNĐ/ngày",
  vnd_thang: "VNĐ/tháng",
  vnd_nam: "VNĐ/năm",
  vnd_m2_ngay: "VNĐ/m²/ngày",
  vnd_m2_thang: "VNĐ/m²/tháng",
};

const LOAI_BDS_LABELS: Record<string, string> = {
  can_ho: "Căn hộ chung cư",
  nha_rieng: "Nhà riêng",
  dat: "Đất nền",
  phong_tro: "Phòng trọ",
  khac: "Khác",
};

export function formatDonViGiaLabel(donViGia?: string | null): string {
  if (!donViGia) return "VNĐ";
  return DON_VI_GIA_LABELS[donViGia] || donViGia;
}

export function formatLoaiBdsLabel(loaiBds?: string | null): string {
  if (!loaiBds) return "Khác";
  return LOAI_BDS_LABELS[loaiBds] || loaiBds;
}

export function formatLoaiGiaoDichLabel(loaiGiaoDich?: string | null): string {
  if (loaiGiaoDich === "cho_thue") return "Cho thuê";
  return "Mua bán";
}

/**
 * Nhóm chữ số từ chuỗi số thập phân (dấu chấm) sang định dạng hiển thị tiếng Việt (1.234.567,89)
 * hoàn toàn bằng thao tác chuỗi, không qua Number.
 */
export function formatDecimalStringVi(
  decimalStr?: string | null,
  keepTwoDecimals = false
): string {
  if (!decimalStr) return "0";
  const cleaned = String(decimalStr).trim();
  if (!cleaned) return "0";

  const isNegative = cleaned.startsWith("-");
  const unsigned = isNegative ? cleaned.slice(1) : cleaned;
  const [rawInt = "0", rawFrac = ""] = unsigned.split(".");

  const normalizedInt = rawInt.replace(/^0+(?=\d)/, "") || "0";
  const groupedInt = normalizedInt.replace(/\B(?=(\d{3})+(?!\d))/g, ".");

  if (keepTwoDecimals) {
    const frac2 = (rawFrac + "00").slice(0, 2);
    if (frac2 === "00") {
      return `${isNegative ? "-" : ""}${groupedInt}`;
    }
    return `${isNegative ? "-" : ""}${groupedInt},${frac2}`;
  }

  const trimmedFrac = rawFrac.replace(/0+$/, "");
  if (!trimmedFrac) {
    return `${isNegative ? "-" : ""}${groupedInt}`;
  }
  return `${isNegative ? "-" : ""}${groupedInt},${trimmedFrac}`;
}

/**
 * Định dạng giá gốc kèm đơn vị gốc (ví dụ: "2.850.000.000 VNĐ" hoặc "15.000.000 VNĐ/tháng")
 */
export function formatGiaGocVaDonVi(
  giaGoc?: string | null,
  donViGia?: string | null
): string {
  if (!giaGoc) return "Đang cập nhật";
  const formattedNumber = formatDecimalStringVi(giaGoc, false);
  const unitLabel = formatDonViGiaLabel(donViGia);
  return `${formattedNumber} ${unitLabel}`;
}

/**
 * Định dạng giá quy đổi hiển thị theo TK-07:
 * - Nếu giaTri === "0.00", hiển thị "< 0,01 [đơn vị]"
 * - Ngược lại hiển thị chuỗi số đã nhóm chữ số kèm đơn vị
 */
export function formatGiaQuyDoi(giaQuyDoi?: GiaQuyDoiHienThiDto | null): string | null {
  if (!giaQuyDoi || !giaQuyDoi.giaTri) return null;
  const unitLabel = formatDonViGiaLabel(giaQuyDoi.donVi);
  const trimmed = giaQuyDoi.giaTri.trim();
  if (trimmed === "0.00" || trimmed === "0") {
    return `< 0,01 ${unitLabel}`;
  }
  const formatted = formatDecimalStringVi(trimmed, true);
  return `${formatted} ${unitLabel}`;
}

/**
 * Định dạng diện tích m² từ chuỗi thập phân
 */
export function formatDienTichM2(dienTichM2?: string | null): string {
  if (!dienTichM2) return "0 m²";
  return `${formatDecimalStringVi(dienTichM2, false)} m²`;
}
