import locations from "@/data/locations.json";

export interface LocationResolveInput {
  wardId?: number;
  city?: string;
  ward?: string;
  address?: string;
  khuVuc?: string;
  diaChi?: string;
  rawPayload?: {
    khuVuc?: string;
    diaChi?: string;
  };
}

export interface ResolvedLocation {
  provinceName: string;
  provinceId?: number;
  wardName: string;
  wardId?: number;
  cleanAddress: string;
}

const CITY_ALIASES: Record<string, string> = {
  hcm: "Thành phố Hồ Chí Minh",
  "tp hcm": "Thành phố Hồ Chí Minh",
  "tp.hcm": "Thành phố Hồ Chí Minh",
  tphcm: "Thành phố Hồ Chí Minh",
  "sai gon": "Thành phố Hồ Chí Minh",
  saigon: "Thành phố Hồ Chí Minh",
  "ho chi minh": "Thành phố Hồ Chí Minh",
  hn: "Thành phố Hà Nội",
  "tp hn": "Thành phố Hà Nội",
  "tp.hn": "Thành phố Hà Nội",
  tphn: "Thành phố Hà Nội",
  "ha noi": "Thành phố Hà Nội",
  "da nang": "Thành phố Đà Nẵng",
  "tp da nang": "Thành phố Đà Nẵng",
  danang: "Thành phố Đà Nẵng",
  "can tho": "Thành phố Cần Thơ",
  "tp can tho": "Thành phố Cần Thơ",
  "hai phong": "Thành phố Hải Phòng",
  "tp hai phong": "Thành phố Hải Phòng",
  hue: "Thành phố Huế",
  "thua thien hue": "Thành phố Huế",
};

export function normalizeLocString(s: string): string {
  return (s || "")
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[.,]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Trích xuất và suy luận tỉnh/thành phố và phường/xã từ nhiều nguồn dữ liệu:
 * - wardId hoặc city/ward chỉ định sẵn
 * - khuVuc ("Phường Tân Hưng, Thành phố Hồ Chí Minh")
 * - diaChi ("Số 10 Nguyễn Huệ, Phường Tân Hưng, Thành phố Hồ Chí Minh")
 * - address hoặc rawPayload
 */
export function resolveLocation(data?: LocationResolveInput | null): ResolvedLocation {
  if (!data) {
    return { provinceName: "", wardName: "", cleanAddress: "" };
  }

  let province: (typeof locations.provinces)[number] | undefined = undefined;
  let ward: (typeof locations.wards)[number] | undefined = undefined;
  let rawProvinceStr = data.city || "";
  let rawWardStr = data.ward || "";

  // 1. Tìm qua wardId trực tiếp nếu có
  if (data.wardId) {
    ward = locations.wards.find((w) => w.id === data.wardId);
    if (ward) {
      province = locations.provinces.find((p) => p.id === ward!.province_id);
    }
  }

  // 2. Trích từ khuVuc nếu có (định dạng chuẩn: "Phường/Xã, Tỉnh/Thành phố")
  const khuVucStr = data.khuVuc || data.rawPayload?.khuVuc || "";
  if (khuVucStr) {
    const parts = khuVucStr
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    if (parts.length >= 2) {
      if (!rawWardStr) rawWardStr = parts[0];
      if (!rawProvinceStr) rawProvinceStr = parts[parts.length - 1];
    } else if (parts.length === 1 && !rawProvinceStr) {
      rawProvinceStr = parts[0];
    }
  }

  // 3. Trích từ diaChi / address nếu vẫn thiếu
  const fullAddr = data.address || data.diaChi || data.rawPayload?.diaChi || "";
  if ((!rawWardStr || !rawProvinceStr) && fullAddr) {
    const addrParts = fullAddr
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    if (addrParts.length >= 3) {
      if (!rawProvinceStr) rawProvinceStr = addrParts[addrParts.length - 1];
      if (!rawWardStr) rawWardStr = addrParts[addrParts.length - 2];
    } else if (addrParts.length === 2) {
      if (!rawProvinceStr) rawProvinceStr = addrParts[1];
      if (!rawWardStr) rawWardStr = addrParts[0];
    }
  }

  // 4. Tìm tỉnh/thành chuẩn trong cơ sở dữ liệu locations
  if (!province && rawProvinceStr) {
    const norm = normalizeLocString(rawProvinceStr);
    province = locations.provinces.find(
      (p) => p.name === rawProvinceStr || normalizeLocString(p.name) === norm
    );
    if (!province) {
      const alias = CITY_ALIASES[norm];
      if (alias) {
        province = locations.provinces.find((p) => p.name === alias);
      }
    }
    if (!province) {
      for (const p of locations.provinces) {
        const pCleanNorm = normalizeLocString(
          p.name.replace(/^(Thành phố|Tỉnh)\s+/i, "")
        );
        if (
          pCleanNorm.length >= 3 &&
          (norm.includes(pCleanNorm) || pCleanNorm.includes(norm))
        ) {
          province = p;
          break;
        }
      }
    }
  }

  // Tìm tỉnh trên toàn văn bản nếu vẫn chưa tìm ra
  const fullText = [khuVucStr, fullAddr].filter(Boolean).join(" , ");
  if (!province && fullText) {
    const textNorm = ` ${normalizeLocString(fullText)} `;
    for (const p of locations.provinces) {
      const pFullNorm = ` ${normalizeLocString(p.name)} `;
      const pCleanNorm = ` ${normalizeLocString(p.name.replace(/^(Thành phố|Tỉnh)\s+/i, ""))} `;
      if (
        textNorm.includes(pFullNorm) ||
        (pCleanNorm.trim().length >= 3 && textNorm.includes(pCleanNorm))
      ) {
        province = p;
        break;
      }
    }
    if (!province) {
      for (const [alias, provName] of Object.entries(CITY_ALIASES)) {
        const aliasNorm = ` ${normalizeLocString(alias)} `;
        if (textNorm.includes(aliasNorm)) {
          province = locations.provinces.find((p) => p.name === provName);
          if (province) break;
        }
      }
    }
  }

  // 5. Tìm phường/xã chuẩn trong tỉnh đã xác định (hoặc toàn bộ)
  const wardsPool = province
    ? locations.wards.filter((w) => w.province_id === province!.id)
    : locations.wards;

  if (!ward && rawWardStr) {
    const wardNorm = normalizeLocString(rawWardStr);
    ward = wardsPool.find(
      (w) => w.name === rawWardStr || normalizeLocString(w.name) === wardNorm
    );
    if (!ward) {
      const wClean = normalizeLocString(
        rawWardStr.replace(/^(Phường|Xã|Thị trấn)\s+/i, "")
      );
      if (wClean.length >= 2) {
        ward = wardsPool.find(
          (w) =>
            normalizeLocString(
              w.name.replace(/^(Phường|Xã|Thị trấn)\s+/i, "")
            ) === wClean
        );
      }
    }
  }

  if (!ward && fullText) {
    const sortedWards = [...wardsPool].sort(
      (a, b) => b.name.length - a.name.length
    );
    const textNorm = ` ${normalizeLocString(fullText)} `;
    for (const w of sortedWards) {
      const wFullNorm = ` ${normalizeLocString(w.name)} `;
      const wCleanNorm = ` ${normalizeLocString(
        w.name.replace(/^(Phường|Xã|Thị trấn)\s+/i, "")
      )} `;
      if (
        textNorm.includes(wFullNorm) ||
        (wCleanNorm.trim().length >= 3 &&
          isNaN(Number(wCleanNorm.trim())) &&
          textNorm.includes(wCleanNorm))
      ) {
        ward = w;
        if (!province) {
          province = locations.provinces.find((p) => p.id === w.province_id);
        }
        break;
      }
    }
  }

  const finalProvinceName = province ? province.name : rawProvinceStr || "";
  const finalWardName = ward ? ward.name : rawWardStr || "";

  // 6. Tách địa chỉ cụ thể (số nhà, tên đường) bằng cách loại bỏ phần phường/xã và tỉnh/thành
  let cleanAddress = fullAddr;
  if (finalWardName) {
    const idx = cleanAddress.toLowerCase().indexOf(finalWardName.toLowerCase());
    if (idx !== -1) {
      cleanAddress = cleanAddress.substring(0, idx);
    }
  } else if (finalProvinceName) {
    const idx = cleanAddress
      .toLowerCase()
      .indexOf(finalProvinceName.toLowerCase());
    if (idx !== -1) {
      cleanAddress = cleanAddress.substring(0, idx);
    }
  }
  cleanAddress = cleanAddress.replace(/,\s*$/, "").trim();

  return {
    provinceName: finalProvinceName,
    provinceId: province ? province.id : undefined,
    wardName: finalWardName,
    wardId: ward ? ward.id : undefined,
    cleanAddress: cleanAddress || fullAddr,
  };
}
