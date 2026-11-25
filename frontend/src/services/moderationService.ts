import api from "@/services/api";
import type {
  QuyetDinhKiemDuyetPayload,
  TinGhiKetQuaDto,
  TinQuanLyDto,
  TrangTinQuanLyDto,
} from "@/types/moderation";

function formatStrongETag(version: number | string): string {
  const str = String(version ?? 0).trim();
  if (str.startsWith('"') && str.endsWith('"')) {
    return str;
  }
  return `"${str}"`;
}

function generateIdempotencyKey(): string {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `mod-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

const decisionIdempotencyKeys = new Map<string, string>();

function buildDecisionIntentSignature(
  listingId: string,
  ifMatchHeader: string,
  cleanBody: Record<string, string>
): string {
  return JSON.stringify({
    listingId,
    ifMatch: ifMatchHeader,
    phienBanDaXemId: cleanBody.phienBanDaXemId,
    quyetDinh: cleanBody.quyetDinh,
    lyDo: cleanBody.quyetDinh === "tu_choi" ? cleanBody.lyDo ?? "" : null,
  });
}

function getOrCreateDecisionIdempotencyKey(
  signature: string,
  explicitKey?: string
): string {
  if (explicitKey) {
    decisionIdempotencyKeys.set(signature, explicitKey);
    return explicitKey;
  }
  const existingKey = decisionIdempotencyKeys.get(signature);
  if (existingKey) {
    return existingKey;
  }
  const newKey = generateIdempotencyKey();
  decisionIdempotencyKeys.set(signature, newKey);
  return newKey;
}

/**
 * Service kết nối 3 API Kiểm duyệt tin đăng của KDV theo openapi-sprint-1.yaml (FR-07 / UC-13):
 * 1. GET /api/moderation/listings (layDanhSachChoDuyet)
 * 2. GET /api/moderation/listings/{listingId} (layBanCanDuyet)
 * 3. POST /api/moderation/listings/{listingId}/decision (quyetDinhKiemDuyet)
 */
export const moderationService = {
  /**
   * Lấy danh sách tin đang chờ kiểm duyệt (GET /api/moderation/listings)
   */
  async getPendingListings(trang = 0, kichThuoc = 50): Promise<TrangTinQuanLyDto> {
    const response = await api.get<TrangTinQuanLyDto>("/moderation/listings", {
      params: { trang, kichThuoc },
    });
    return response.data;
  },

  /**
   * Đọc đúng phiên bản hiện tại cần kiểm duyệt của một tin (GET /api/moderation/listings/{listingId})
   */
  async getListingToModerate(
    listingId: string
  ): Promise<{ data: TinQuanLyDto; etag: string }> {
    const response = await api.get<TinQuanLyDto>(`/moderation/listings/${listingId}`);
    const headerEtag = response.headers?.etag || response.headers?.ETag;
    const etag = headerEtag
      ? String(headerEtag)
      : formatStrongETag(response.data.phienBanCapNhat);
    return {
      data: response.data,
      etag,
    };
  },

  /**
   * Gửi quyết định phê duyệt hoặc từ chối phiên bản đang xem (POST /api/moderation/listings/{listingId}/decision)
   * Lưu ý: theo KiemDuyetService.docYeuCauQuyetDinh, khi quyetDinh === "phe_duyet" thì KHÔNG gửi key "lyDo" trong body.
   */
  async submitDecision(
    listingId: string,
    payload: QuyetDinhKiemDuyetPayload,
    options: {
      ifMatch: number | string;
      idempotencyKey?: string;
    }
  ): Promise<TinGhiKetQuaDto> {
    const ifMatchHeader = formatStrongETag(options.ifMatch);

    const cleanBody: Record<string, string> = {
      phienBanDaXemId: payload.phienBanDaXemId,
      quyetDinh: payload.quyetDinh,
    };
    if (payload.quyetDinh === "tu_choi" && payload.lyDo !== undefined) {
      cleanBody.lyDo = payload.lyDo;
    }

    const intentSignature = buildDecisionIntentSignature(
      listingId,
      ifMatchHeader,
      cleanBody
    );
    const idempotencyKey = getOrCreateDecisionIdempotencyKey(
      intentSignature,
      options.idempotencyKey
    );

    const response = await api.post<TinGhiKetQuaDto>(
      `/moderation/listings/${listingId}/decision`,
      cleanBody,
      {
        headers: {
          "If-Match": ifMatchHeader,
          "Idempotency-Key": idempotencyKey,
        },
      }
    );
    return response.data;
  },
};
