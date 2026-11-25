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
   * Đọc đủ toàn bộ danh sách ID tin yêu thích qua các trang (hỗ trợ người dùng có > 50 mục yêu thích - ca M14).
   */
  async getAllFavoriteIds(kichThuoc: number = 50): Promise<string[]> {
    const safePageSize = Math.min(Math.max(1, kichThuoc), 50);
    const firstPage = await this.getFavorites(0, safePageSize);
    const firstIds = Array.isArray(firstPage?.noiDung)
      ? firstPage.noiDung.map((item) => item.id)
      : [];
    const tongSo = Number(firstPage?.tongSo ?? firstIds.length);
    const totalPages = Math.ceil(tongSo / safePageSize);

    if (totalPages <= 1) {
      return Array.from(new Set(firstIds));
    }

    const pagePromises: Promise<TrangTinCongKhaiDto>[] = [];
    for (let page = 1; page < totalPages; page++) {
      pagePromises.push(this.getFavorites(page, safePageSize));
    }

    const remainingPages = await Promise.all(pagePromises);
    const allIds = [...firstIds];
    for (const pageData of remainingPages) {
      if (Array.isArray(pageData?.noiDung)) {
        for (const item of pageData.noiDung) {
          allIds.push(item.id);
        }
      }
    }
    return Array.from(new Set(allIds));
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
