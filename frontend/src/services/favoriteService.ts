import api from "@/services/api";
import type { TrangTinCongKhaiDto } from "@/types/search";

export const favoriteService = {
  /**
   * GET /api/me/favorites
   * Lấy danh sách tin yêu thích của người dùng hiện tại (chỉ gồm tin công khai đủ điều kiện).
   */
  async getFavorites(
    trang: number = 0,
    kichThuoc: number = 20
  ): Promise<TrangTinCongKhaiDto> {
    const response = await api.get<TrangTinCongKhaiDto>("/me/favorites", {
      params: { trang, kichThuoc },
    });
    return response.data;
  },

  /**
   * PUT /api/me/favorites/{listingId}
   * Lưu tin vào danh sách yêu thích (idempotent -> 204 No Content).
   */
  async addFavorite(listingId: string): Promise<void> {
    await api.put(`/me/favorites/${encodeURIComponent(listingId)}`);
  },

  /**
   * DELETE /api/me/favorites/{listingId}
   * Bỏ lưu tin khỏi danh sách yêu thích (idempotent -> 204 No Content).
   */
  async removeFavorite(listingId: string): Promise<void> {
    await api.delete(`/me/favorites/${encodeURIComponent(listingId)}`);
  },
};
