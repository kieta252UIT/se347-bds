import { useState } from "react";
import { useSearchParams, Link } from "react-router";

export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  // Đọc params từ URL
  const [tuKhoa, setTuKhoa] = useState(searchParams.get("q") || "");
  const [nhuCau, setNhuCau] = useState(searchParams.get("nhuCau") || "BAN");
  const [loaiBds, setLoaiBds] = useState(searchParams.get("loaiBds") || "CAN_HO");
  const [khuVuc, setKhuVuc] = useState(searchParams.get("khuVuc") || "TPHCM");
  const [mucGia, setMucGia] = useState(searchParams.get("mucGia") || "ALL");

  const handleApplyFilter = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchParams({
      q: tuKhoa,
      nhuCau,
      loaiBds,
      khuVuc,
      mucGia,
    });
  };

  // Mock data cho 1. Tin được tài trợ (VIP) - Ở TRÊN CÙNG
  const tinTaiTroList = [
    {
      id: "vip-1",
      tieuDe: "💎 [VIP] Căn Penthouse The River Thủ Thiêm view trực diện sông Sài Gòn",
      gia: "45 tỷ",
      dienTich: "280 m²",
      diaChi: "Bán đảo Thủ Thiêm, TP. Thủ Đức",
      hinhAnh: "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=600&auto=format&fit=crop&q=80",
      loaiGoi: "VIP Kim Cương",
    },
    {
      id: "vip-2",
      tieuDe: "⭐ [VIP] Shophouse mặt tiền đường Nguyễn Cơ Thạch kinh doanh sầm uất",
      gia: "32 tỷ",
      dienTich: "140 m²",
      diaChi: "Đô thị Sala, TP. Thủ Đức",
      hinhAnh: "https://images.unsplash.com/photo-1577495508048-b635879837f1?w=600&auto=format&fit=crop&q=80",
      loaiGoi: "VIP Vàng",
    },
  ];

  // Mock data cho 2. Tin tìm kiếm tự nhiên - Ở GIỮA
  const tinTuNhienList = [
    {
      id: "201",
      tieuDe: "Căn hộ 2PN 2WC The Sun Avenue bàn giao full nội thất cao cấp",
      gia: "3.9 tỷ",
      dienTich: "75 m²",
      diaChi: "Mai Chí Thọ, An Phú, TP. Thủ Đức",
      hinhAnh: "https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=600&auto=format&fit=crop&q=80",
      ngayDang: "12/11/2026",
    },
    {
      id: "202",
      tieuDe: "Bán nhà 1 trệt 2 lầu hẻm xe hơi đường Nguyễn Thị Định",
      gia: "5.5 tỷ",
      dienTich: "64 m²",
      diaChi: "Cát Lái, TP. Thủ Đức",
      hinhAnh: "https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=600&auto=format&fit=crop&q=80",
      ngayDang: "11/11/2026",
    },
    {
      id: "203",
      tieuDe: "Căn hộ studio dịch vụ tiện nghi đầy đủ gần Landmark 81",
      gia: "8.5 triệu/tháng",
      dienTich: "35 m²",
      diaChi: "Bình Thạnh, TP.HCM",
      hinhAnh: "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=600&auto=format&fit=crop&q=80",
      ngayDang: "10/11/2026",
    },
  ];

  // Mock data cho 3. Tin gợi ý cho bạn - Ở CUỐI CÙNG
  const tinGoiYList = [
    {
      id: "rec-1",
      tieuDe: "Gợi ý: Căn hộ Akari City Bình Tân giá tốt cho gia đình trẻ",
      gia: "2.4 tỷ",
      dienTich: "56 m²",
      diaChi: "Võ Văn Kiệt, Bình Tân, TP.HCM",
      lyDoGoiY: "Phù hợp mức giá tìm kiếm gần đây của bạn",
    },
    {
      id: "rec-2",
      tieuDe: "Gợi ý: Nhà phố khu dân cư Nam Long an ninh 24/7",
      gia: "4.8 tỷ",
      dienTich: "85 m²",
      diaChi: "Quận 7, TP.HCM",
      lyDoGoiY: "Gần khu vực bạn thường xuyên theo dõi",
    },
  ];

  return (
    <div>
      {/* THANH BỘ LỌC TÌM KIẾM */}
      <section style={{
        backgroundColor: "#ffffff",
        padding: "1.5rem",
        borderRadius: "0.5rem",
        border: "1px solid #e5e7eb",
        marginBottom: "2rem"
      }}>
        <form onSubmit={handleApplyFilter}>
          <div style={{ display: "flex", gap: "0.75rem", marginBottom: "1rem" }}>
            <input
              type="text"
              placeholder="Nhập từ khóa tìm kiếm (VD: căn hộ 2 phòng ngủ, nhà mặt tiền...)"
              value={tuKhoa}
              onChange={(e) => setTuKhoa(e.target.value)}
              style={{ flex: 1, padding: "0.6rem 1rem", borderRadius: "0.375rem", border: "1px solid #d1d5db", fontSize: "0.95rem" }}
            />
            <button
              type="submit"
              style={{
                padding: "0.6rem 1.5rem",
                backgroundColor: "#2563eb",
                color: "#ffffff",
                border: "none",
                borderRadius: "0.375rem",
                fontWeight: 600,
                cursor: "pointer"
              }}
            >
              Áp dụng lọc
            </button>
          </div>

          <div style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
            gap: "0.75rem"
          }}>
            <select
              value={nhuCau}
              onChange={(e) => setNhuCau(e.target.value)}
              style={{ padding: "0.45rem", borderRadius: "0.25rem", border: "1px solid #d1d5db", fontSize: "0.85rem" }}
            >
              <option value="BAN">Mua bán</option>
              <option value="THUE">Cho thuê</option>
            </select>

            <select
              value={loaiBds}
              onChange={(e) => setLoaiBds(e.target.value)}
              style={{ padding: "0.45rem", borderRadius: "0.25rem", border: "1px solid #d1d5db", fontSize: "0.85rem" }}
            >
              <option value="CAN_HO">Căn hộ chung cư</option>
              <option value="NHA_RIENG">Nhà riêng</option>
              <option value="DAT_NEN">Đất nền</option>
            </select>

            <select
              value={khuVuc}
              onChange={(e) => setKhuVuc(e.target.value)}
              style={{ padding: "0.45rem", borderRadius: "0.25rem", border: "1px solid #d1d5db", fontSize: "0.85rem" }}
            >
              <option value="TPHCM">TP.HCM</option>
              <option value="HA_NOI">Hà Nội</option>
              <option value="BINH_DUONG">Bình Dương</option>
            </select>

            <select
              value={mucGia}
              onChange={(e) => setMucGia(e.target.value)}
              style={{ padding: "0.45rem", borderRadius: "0.25rem", border: "1px solid #d1d5db", fontSize: "0.85rem" }}
            >
              <option value="ALL">Tất cả mức giá</option>
              <option value="UNDER_1B">Dưới 1 tỷ</option>
              <option value="1B_3B">1 - 3 tỷ</option>
              <option value="3B_5B">3 - 5 tỷ</option>
              <option value="ABOVE_5B">Trên 5 tỷ</option>
            </select>
          </div>
        </form>
      </section>

      {/* 1. KHU VỰC TIN ĐƯỢC TÀI TRỢ (Ở TRÊN CÙNG THEO YÊU CẦU BR-06) */}
      <section style={{
        marginBottom: "2.5rem",
        backgroundColor: "#fffbeb",
        padding: "1.5rem",
        borderRadius: "0.5rem",
        border: "1px solid #fde68a"
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.5rem" }}>
          <span style={{
            backgroundColor: "#d97706",
            color: "#ffffff",
            fontSize: "0.75rem",
            fontWeight: "bold",
            padding: "0.25rem 0.6rem",
            borderRadius: "0.25rem",
            textTransform: "uppercase"
          }}>
            ⭐ Được tài trợ
          </span>
          <span style={{ fontSize: "0.85rem", color: "#92400e" }}>
            (Các tin đã mua gói đẩy tin VIP thỏa bộ lọc, hiển thị ưu tiên tại đây - BR-06)
          </span>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem", marginTop: "1rem" }}>
          {tinTaiTroList.map((item) => (
            <div key={item.id} style={{
              backgroundColor: "#ffffff",
              borderRadius: "0.375rem",
              border: "1px solid #fcd34d",
              padding: "1rem",
              display: "flex",
              gap: "1rem"
            }}>
              <img
                src={item.hinhAnh}
                alt={item.tieuDe}
                style={{ width: "100px", height: "80px", objectFit: "cover", borderRadius: "0.25rem" }}
              />
              <div style={{ flex: 1 }}>
                <h4 style={{ margin: "0 0 0.35rem", fontSize: "0.9rem", color: "#1e3a8a" }}>
                  {item.tieuDe}
                </h4>
                <div style={{ color: "#dc2626", fontWeight: "bold", fontSize: "0.95rem" }}>
                  {item.gia} • <span style={{ color: "#4b5563", fontSize: "0.85rem", fontWeight: 400 }}>{item.dienTich}</span>
                </div>
                <p style={{ color: "#6b7280", fontSize: "0.75rem", margin: "0.25rem 0 0" }}>
                  📍 {item.diaChi}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 2. KHU VỰC TIN TÌM KIẾM TỰ NHIÊN (Ở GIỮA) */}
      <section style={{ marginBottom: "3rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
          <h3 style={{ margin: 0, fontSize: "1.25rem", color: "#111827" }}>
            Kết Quả Tìm Kiếm Tự Nhiên ({tinTuNhienList.length} tin)
          </h3>
          <span style={{ fontSize: "0.85rem", color: "#6b7280" }}>
            Sắp xếp: Mới nhất trước (FR-02)
          </span>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          {tinTuNhienList.map((item) => (
            <div key={item.id} style={{
              backgroundColor: "#ffffff",
              padding: "1.25rem",
              borderRadius: "0.5rem",
              border: "1px solid #e5e7eb",
              display: "flex",
              gap: "1.25rem",
              alignItems: "center"
            }}>
              <img
                src={item.hinhAnh}
                alt={item.tieuDe}
                style={{ width: "160px", height: "110px", objectFit: "cover", borderRadius: "0.375rem" }}
              />
              <div style={{ flex: 1 }}>
                <h4 style={{ margin: "0 0 0.5rem", fontSize: "1rem", color: "#111827" }}>
                  <Link to={`/tin-dang/${item.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                    {item.tieuDe}
                  </Link>
                </h4>
                <div style={{ color: "#dc2626", fontWeight: "bold", fontSize: "1.1rem", marginBottom: "0.35rem" }}>
                  {item.gia} <span style={{ color: "#6b7280", fontSize: "0.9rem", fontWeight: "normal" }}>({item.dienTich})</span>
                </div>
                <p style={{ color: "#6b7280", fontSize: "0.85rem", margin: "0 0 0.35rem" }}>
                  📍 {item.diaChi}
                </p>
                <span style={{ fontSize: "0.75rem", color: "#9ca3af" }}>
                  Đăng ngày: {item.ngayDang}
                </span>
              </div>
              <div>
                <Link
                  to={`/tin-dang/${item.id}`}
                  style={{
                    padding: "0.5rem 1rem",
                    backgroundColor: "#f3f4f6",
                    color: "#2563eb",
                    borderRadius: "0.375rem",
                    textDecoration: "none",
                    fontSize: "0.85rem",
                    fontWeight: 500,
                    whiteSpace: "nowrap"
                  }}
                >
                  Xem tin
                </Link>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 3. KHU VỰC GỢI Ý DÀNH RIÊNG CHO BẠN (Ở CUỐI CÙNG THEO YÊU CẦU FR-03 / UC-03) */}
      <section style={{
        backgroundColor: "#f8fafc",
        padding: "1.75rem",
        borderRadius: "0.5rem",
        border: "1px solid #cbd5e1"
      }}>
        <div style={{ marginBottom: "1rem" }}>
          <h3 style={{ margin: 0, fontSize: "1.15rem", color: "#0f172a", display: "flex", alignItems: "center", gap: "0.5rem" }}>
            💡 Gợi Ý Dành Riêng Cho Bạn
          </h3>
          <p style={{ margin: "0.25rem 0 0", color: "#64748b", fontSize: "0.85rem" }}>
            Dựa trên nhu cầu tìm kiếm và vị trí đề xuất phù hợp (FR-03 / UC-03)
          </p>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "1rem" }}>
          {tinGoiYList.map((item) => (
            <div key={item.id} style={{
              backgroundColor: "#ffffff",
              padding: "1rem",
              borderRadius: "0.375rem",
              border: "1px solid #e2e8f0"
            }}>
              <span style={{
                display: "inline-block",
                backgroundColor: "#eff6ff",
                color: "#1d4ed8",
                fontSize: "0.75rem",
                padding: "0.2rem 0.5rem",
                borderRadius: "0.25rem",
                marginBottom: "0.5rem"
              }}>
                {item.lyDoGoiY}
              </span>
              <h5 style={{ margin: "0 0 0.35rem", fontSize: "0.9rem", color: "#1e293b" }}>
                {item.tieuDe}
              </h5>
              <div style={{ color: "#dc2626", fontWeight: "bold", fontSize: "0.95rem" }}>
                {item.gia} • <span style={{ color: "#64748b", fontSize: "0.85rem", fontWeight: "normal" }}>{item.dienTich}</span>
              </div>
              <p style={{ color: "#64748b", fontSize: "0.75rem", margin: "0.25rem 0 0" }}>
                📍 {item.diaChi}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
