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
  xsrfCookieName: "se214_csrf",
  xsrfHeaderName: "X-CSRF-Token",
});

let cachedCsrfToken: string | null = null;
let csrfRequestPromise: Promise<string> | null = null;

async function fetchCsrfToken(): Promise<string> {
  if (cachedCsrfToken) return cachedCsrfToken;
  if (!csrfRequestPromise) {
    csrfRequestPromise = axios
      .get<{ csrfToken: string }>("/api/csrf", { withCredentials: true })
      .then((res) => {
        cachedCsrfToken = res.data.csrfToken;
        csrfRequestPromise = null;
        return cachedCsrfToken;
      })
      .catch((err) => {
        csrfRequestPromise = null;
        throw err;
      });
  }
  return csrfRequestPromise;
}

// Request Interceptor: Tự động đính kèm X-CSRF-Token cho các phương thức thay đổi trạng thái
api.interceptors.request.use(
  async (config) => {
    const method = config.method?.toUpperCase();
    if (method && ["POST", "PUT", "PATCH", "DELETE"].includes(method)) {
      const url = config.url || "";
      if (!url.includes("/csrf")) {
        try {
          const token = await fetchCsrfToken();
          if (token && config.headers) {
            if (typeof config.headers.set === "function") {
              config.headers.set("X-CSRF-Token", token);
            } else {
              config.headers["X-CSRF-Token"] = token;
            }
          }
        } catch (e) {
          console.warn("Chưa thể lấy CSRF token:", e);
        }
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Xử lý tập trung mã lỗi HTTP và chuẩn hóa dữ liệu lỗi
api.interceptors.response.use(
  (response) => {
    if (response.data && typeof response.data === "object" && "csrfToken" in response.data) {
      cachedCsrfToken = (response.data as { csrfToken: string }).csrfToken;
    }
    return response;
  },
  (error: AxiosError<ApiErrorResponse & { maLoi?: string; thongDiep?: string }>) => {
    const status = error.response?.status;
    const responseData = error.response?.data;

    if (status === 403 && responseData?.maLoi === "CSRF_KHONG_HOP_LE") {
      cachedCsrfToken = null;
    }

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
