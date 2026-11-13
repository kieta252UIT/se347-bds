import axios, { type AxiosError } from "axios";
import type { ApiErrorResponse } from "@/types/api";
import { useAuthStore } from "@/stores/useAuthStore";

export const API_BASE_URL =
  import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || "/api";

/**
 * Axios instance tập trung dùng chung cho toàn bộ ứng dụng (SDD-24, ADR-0013).
 * Cấu hình:
 * - baseURL: '/api' cùng nguồn HTTPS (đi qua Vite proxy ở local, hoặc Reverse Proxy ở production)
 * - withCredentials: true (bắt buộc để gửi/nhận cookie phiên HttpOnly cùng nguồn)
 */
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

// Request Interceptor: Chuẩn bị header nếu cần
api.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Xử lý tập trung mã lỗi HTTP và chuẩn hóa dữ liệu lỗi
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error: AxiosError<ApiErrorResponse & { maLoi?: string; thongDiep?: string }>) => {
    const status = error.response?.status;
    const responseData = error.response?.data;

    const normalizedError: ApiErrorResponse = {
      status: status || 500,
      code:
        responseData?.code ||
        responseData?.maLoi ||
        (status === 401 ? "UNAUTHORIZED" : status === 403 ? "FORBIDDEN" : "INTERNAL_ERROR"),
      message:
        responseData?.message ||
        responseData?.thongDiep ||
        error.message ||
        "Đã có lỗi xảy ra. Vui lòng thử lại sau.",
      details: responseData?.details,
      timestamp: responseData?.timestamp || new Date().toISOString(),
    };

    // Khi nhận 401: phiên không hợp lệ hoặc đã bị thu hồi
    if (status === 401) {
      const { isAuthenticated, clearAuth } = useAuthStore.getState();
      if (isAuthenticated) {
        clearAuth();
      }
    }

    return Promise.reject(normalizedError);
  }
);

export default api;
