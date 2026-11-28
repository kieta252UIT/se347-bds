import api from "@/services/api";
import type {
  KetQuaMuaGoiResponse,
  ThongTinMuaGoiResponse,
  ViDienTuResponse,
  YeuCauMuaGoiRequest,
} from "@/types/wallet";

export function generatePurchaseIntentKey(): string {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `BOOST-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

export const walletService = {
  /**
   * Lấy thông tin ví điện tử và sổ giao dịch gần đây của người dùng đăng nhập (GET /api/me/wallet)
   */
  async getMyWallet(): Promise<ViDienTuResponse> {
    const response = await api.get<ViDienTuResponse>("/me/wallet");
    return response.data;
  },

  /**
   * Khởi tạo số dư thử nghiệm có ghi sổ giao dịch minh bạch trong môi trường kiểm thử Sprint 2
   * (POST /api/me/wallet/seed-test-balance)
   */
  async seedTestBalance(soTien?: number, ghiChu?: string): Promise<ViDienTuResponse> {
    const response = await api.post<ViDienTuResponse>("/me/wallet/seed-test-balance", {
      soTien: soTien ?? 500000,
      ghiChu: ghiChu ?? "Khởi tạo số dư thử nghiệm phục vụ kiểm thử Sprint 2",
    });
    return response.data;
  },

  /**
   * Lấy báo giá gói đẩy tin, trạng thái gói hiện tại của tin và số dư ví (GET /api/listings/{listingId}/boost-quote)
   */
  async getBoostQuote(listingId: string): Promise<ThongTinMuaGoiResponse> {
    const response = await api.get<ThongTinMuaGoiResponse>(
      `/listings/${listingId}/boost-quote`
    );
    return response.data;
  },

  /**
   * Mua mới hoặc cộng dồn gói đẩy tin với một mã ý định duy nhất (POST /api/listings/{listingId}/boost-purchases)
   */
  async purchaseBoostPackage(
    listingId: string,
    payload: YeuCauMuaGoiRequest,
    idempotencyKey?: string
  ): Promise<KetQuaMuaGoiResponse> {
    const key = idempotencyKey || payload.maYDinh || generatePurchaseIntentKey();
    const body: YeuCauMuaGoiRequest = {
      maGoi: payload.maGoi,
      maYDinh: key,
      dongYCongDon: Boolean(payload.dongYCongDon),
    };

    const response = await api.post<KetQuaMuaGoiResponse>(
      `/listings/${listingId}/boost-purchases`,
      body,
      {
        headers: {
          "Idempotency-Key": key,
        },
      }
    );
    return response.data;
  },
};
