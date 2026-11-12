/**
 * Cấu trúc phản hồi API và mã lỗi chuẩn của hệ thống (SDD-25)
 */

export interface ApiResponse<T = unknown> {
  data: T;
  message?: string;
  status?: number;
}

export interface ApiErrorResponse {
  status: number;
  code: string; // Mã lỗi định danh nghiệp vụ ổn định (VD: UNAUTHORIZED, ACCOUNT_LOCKED, VALIDATION_ERROR)
  message: string;
  details?: Record<string, string>;
  timestamp?: string;
}

export interface PaginationParams {
  page?: number;
  limit?: number;
  sortBy?: string;
  sortDirection?: 'ASC' | 'DESC';
}

export interface PaginatedResult<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}
