import axios, { type AxiosError } from "axios";
import type { ApiErrorResponse } from "@/types/api";
import { useAuthStore } from "@/stores/useAuthStore";

/**
 * Axios instance tập trung dùng chung cho toàn bộ ứng dụng (SDD-24).
 * Cấu hình:
 * - baseURL: '/api' (đi qua Vite proxy ở local, hoặc reverse proxy ở production)
 * - withCredentials: true (bắt buộc để gửi/nhận cookie session HttpOnly - SDD-07, SDD-19)
 * - CSRF token: đọc từ cookie XSRF-TOKEN và gửi kèm header X-XSRF-TOKEN (SDD-20)
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
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
  (error: AxiosError<ApiErrorResponse>) => {
    const status = error.response?.status;
    const responseData = error.response?.data;

    const normalizedError: ApiErrorResponse = {
      status: status || 500,
      code: responseData?.code || (status === 401 ? "UNAUTHORIZED" : status === 403 ? "FORBIDDEN" : "INTERNAL_ERROR"),
      message: responseData?.message || error.message || "Đã có lỗi xảy ra. Vui lòng thử lại sau.",
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
