/**
 * Định dạng số tiền VND theo chuẩn Việt Nam
 */
export function formatPriceVND(amount: number): string {
  if (amount >= 1_000_000_000) {
    const ty = amount / 1_000_000_000;
    return `${ty.toFixed(1).replace(/\.0$/, "")} tỷ`;
  }
  if (amount >= 1_000_000) {
    const trieu = amount / 1_000_000;
    return `${trieu.toFixed(1).replace(/\.0$/, "")} triệu`;
  }
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);
}
