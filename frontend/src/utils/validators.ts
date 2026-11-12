/**
 * Các hàm kiểm tra tính hợp lệ cơ bản
 */
export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export function isPositiveNumber(value: number): boolean {
  return Number.isFinite(value) && value > 0;
}
