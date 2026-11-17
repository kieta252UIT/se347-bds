import axios, { type AxiosError } from "axios";
import type { ApiErrorResponse } from "@/types/api";
import { useAuthStore } from "@/stores/useAuthStore";

export const API_BASE_URL =
  import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || "/api";

// Bộ nhớ lưu CSRF token tại client
let inMemoryCsrfToken: string | null = null;
let csrfPromise: Promise<string> | null = null;

export const setCsrfToken = (token: string | null) => {
  inMemoryCsrfToken = token;
};

export const getCsrfToken = (): string | null => {
  return inMemoryCsrfToken;
};

/**
 * Đảm bảo luôn có CSRF token hợp lệ trước khi gửi các request thay đổi trạng thái (POST/PUT/DELETE/PATCH).
 * Nếu chưa có trong bộ nhớ, gọi GET /api/csrf để backend cấp token và set cookie __Host-se214_csrf.
 * Tái sử dụng cùng một Promise nếu có nhiều request cùng gọi đồng thời để chống gọi trùng lặp.
 */
export const ensureCsrfToken = async (): Promise<string> => {
  if (inMemoryCsrfToken) {
    return inMemoryCsrfToken;
  }
  if (!csrfPromise) {
    csrfPromise = axios
      .get<{ csrfToken: string }>(`${API_BASE_URL}/csrf`, {
        withCredentials: true,
      })
      .then((res) => {
        inMemoryCsrfToken = res.data.csrfToken;
        return inMemoryCsrfToken;
      })
      .finally(() => {
        csrfPromise = null;
      });
  }
  return csrfPromise;
};

/**
 * Axios instance tập trung dùng chung cho toàn bộ ứng dụng (SDD-24, ADR-0013).
 * Cấu hình:
 * - baseURL: '/api' cùng nguồn HTTPS (đi qua Vite proxy ở local, hoặc Reverse Proxy ở production)
 * - withCredentials: true (bắt buộc để gửi/nhận cookie phiên HttpOnly và cookie CSRF cùng nguồn)
 */
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
});

// Request Interceptor: Tự động đính kèm header X-CSRF-Token cho các yêu cầu thay đổi dữ liệu (POST, PUT, DELETE, PATCH)
api.interceptors.request.use(
  async (config) => {
    const method = config.method?.toUpperCase();
    const isStateChanging = method && ["POST", "PUT", "DELETE", "PATCH"].includes(method);

    // Bỏ qua nếu chính là request lấy token /csrf
    const isCsrfEndpoint = config.url?.endsWith("/csrf") || config.url?.includes("/csrf?");
    if (isStateChanging && !isCsrfEndpoint) {
      try {
        const token = await ensureCsrfToken();
        if (token) {
          if (config.headers && typeof config.headers.set === "function") {
            config.headers.set("X-CSRF-Token", token);
          } else {
            config.headers = config.headers || {};
            config.headers["X-CSRF-Token"] = token;
          }
        }
      } catch (error) {
        console.warn("Không thể lấy CSRF token trước khi gửi request:", error);
      }
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Tự động cập nhật CSRF token mới (nếu có trong response) và chuẩn hóa dữ liệu lỗi
api.interceptors.response.use(
  (response) => {
    // Nếu API trả về csrfToken mới (ví dụ: login, logout, verify email), tự động cập nhật
    if (
      response.data &&
      typeof response.data === "object" &&
      "csrfToken" in response.data
    ) {
      const newToken = (response.data as { csrfToken?: string }).csrfToken;
      if (newToken) {
        setCsrfToken(newToken);
      }
    }
    return response;
  },
  (error: AxiosError<ApiErrorResponse & { maLoi?: string; thongDiep?: string }>) => {
    const status = error.response?.status;
    const responseData = error.response?.data;
    const requestUrl = error.config?.url || "";
    const isLoginEndpoint = requestUrl.includes("/auth/login");
    const rawCode = responseData?.code || responseData?.maLoi || "";

    // Nếu gặp lỗi CSRF không hợp lệ (403), xóa token cũ để lần sau tự động xin lại token mới
    if (status === 403) {
      const isCsrfError =
        rawCode === "CSRF_INVALID" ||
        rawCode === "CSRF_KHONG_HOP_LE" ||
        responseData?.message?.toLowerCase().includes("csrf") ||
        responseData?.thongDiep?.toLowerCase().includes("csrf");
      if (isCsrfError) {
        setCsrfToken(null);
      }
    }

    const normalizedError: ApiErrorResponse = {
      status: status || 500,
      code:
        rawCode ||
        (status === 401 ? "UNAUTHORIZED" : status === 403 ? "FORBIDDEN" : "INTERNAL_ERROR"),
      message:
        responseData?.message ||
        responseData?.thongDiep ||
        error.message ||
        "Đã có lỗi xảy ra. Vui lòng thử lại sau.",
      details: responseData?.details,
      timestamp: responseData?.timestamp || new Date().toISOString(),
    };

    // Xử lý phiên mất quyền (Session Revoked / Expired / Account Locked / Role Changed):
    if (status === 401 && !isLoginEndpoint) {
      setCsrfToken(null);
      const { isAuthenticated, handleSessionInvalidated } = useAuthStore.getState();
      if (isAuthenticated) {
        handleSessionInvalidated(
          responseData?.thongDiep ||
            responseData?.message ||
            "Phiên đăng nhập đã hết hạn hoặc đã bị thu hồi. Vui lòng đăng nhập lại."
        );
      }
    } else if (status === 403 && !isLoginEndpoint) {
      if (rawCode === "TAI_KHOAN_BI_KHOA") {
        setCsrfToken(null);
        useAuthStore.getState().handleSessionInvalidated(
          responseData?.thongDiep ||
            responseData?.message ||
            "Tài khoản của bạn đã bị khóa bởi Quản trị viên."
        );
      } else if (rawCode === "EMAIL_CHUA_XAC_MINH") {
        setCsrfToken(null);
        const currentUserEmail = useAuthStore.getState().user?.email;
        useAuthStore.getState().handleSessionInvalidated(
          responseData?.thongDiep ||
            responseData?.message ||
            "Tài khoản chưa hoàn tất xác minh email. Vui lòng xác minh trước khi tiếp tục.",
          currentUserEmail
        );
      } else if (rawCode === "KHONG_DU_QUYEN") {
        const { isAuthenticated, checkSession } = useAuthStore.getState();
        if (isAuthenticated && !requestUrl.endsWith("/me")) {
          // Đồng bộ lại vai trò mới nhất từ GET /api/me nếu quyền của phiên vừa bị thay đổi
          checkSession().catch(() => {});
        }
      }
    }

    return Promise.reject(normalizedError);
  }
);

export default api;
