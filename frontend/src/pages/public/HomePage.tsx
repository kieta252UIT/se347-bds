import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router";
import api from "@/services/api";

export function HomePage() {
  const navigate = useNavigate();

  // State bộ lọc nhanh tại trang chủ
  const [nhuCau, setNhuCau] = useState("BAN");
  const [loaiBds, setLoaiBds] = useState("CAN_HO");
  const [khuVuc, setKhuVuc] = useState("TPHCM");
  const [mucGia, setMucGia] = useState("ALL");
  const [backendStatus, setBackendStatus] = useState<string>("Đang kiểm tra kết nối Spring Boot...");

  useEffect(() => {
    api.get<string>("/hello")
      .then((res) => setBackendStatus(`Spring Boot API: "${res.data}"`))
      .catch((err) => setBackendStatus(`Spring Boot API (chưa chạy): ${err.message}`));
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams({
      nhuCau,
      loaiBds,
      khuVuc,
      mucGia,
    });
    navigate(`/tim-kiem?${params.toString()}`);
  };

  // Dữ liệu mockup mẫu cho tin nổi bật
  const tinNoiBatList = [
    {
      id: "101",
      tieuDe: "Căn hộ cao cấp Vinhomes Grand Park 2PN view công viên",
      gia: "2.8 tỷ",
      dienTich: "68 m²",
      diaChi: "Quận 9, TP. Thủ Đức, TP.HCM",
      hinhAnh: "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?w=600&auto=format&fit=crop&q=80",
      loaiGiaoDich: "Bán",
    },
    {
      id: "102",
      tieuDe: "Nhà phố liền kề Khu đô thị Sala hiện đại, full nội thất",
      gia: "18.5 tỷ",
      dienTich: "110 m²",
      diaChi: "Quận 2, TP. Thủ Đức, TP.HCM",
      hinhAnh: "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=600&auto=format&fit=crop&q=80",
      loaiGiaoDich: "Bán",
    },
    {
      id: "103",
      tieuDe: "Cho thuê chung cư Masteri Thảo Điền 1PN view sông thoáng mát",
      gia: "15 triệu/tháng",
      dienTich: "52 m²",
      diaChi: "Thảo Điền, TP. Thủ Đức, TP.HCM",
      hinhAnh: "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=600&auto=format&fit=crop&q=80",
      loaiGiaoDich: "Cho thuê",
    },
    {
      id: "104",
      tieuDe: "Đất nền sổ đỏ thổ cư 100% gần trung tâm hành chính",
      gia: "3.2 tỷ",
      dienTich: "100 m²",
      diaChi: "Thành phố Dĩ An, Bình Dương",
      hinhAnh: "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=600&auto=format&fit=crop&q=80",
      loaiGiaoDich: "Bán",
    },
  ];

  return (
    <div>
      {/* 1. KHUNG CHỌN TIÊU CHÍ BỘ LỌC TÌM KIẾM TẠI TRANG CHỦ */}
      <section style={{
        backgroundColor: "#ffffff",
        padding: "2rem",
        borderRadius: "0.75rem",
        boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.05)",
        border: "1px solid #e5e7eb",
        marginBottom: "2.5rem"
      }}>
        <div style={{ marginBottom: "1.25rem" }}>
          <h1 style={{ margin: 0, fontSize: "1.65rem", color: "#1e3a8a", fontWeight: "bold" }}>
            Khám Phá Bất Động Sản Phù Hợp
          </h1>
          <p style={{ margin: "0.35rem 0 0", color: "#4b5563", fontSize: "0.95rem" }}>
            Chọn tiêu chí tìm kiếm theo nhu cầu của bạn để chuyển sang trang danh sách kết quả.
          </p>
        </div>

        <form onSubmit={handleSearch}>
          <div style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
            gap: "1rem",
            marginBottom: "1.25rem"
          }}>
            {/* Tiêu chí: Nhu cầu */}
            <div>
              <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#374151", marginBottom: "0.35rem" }}>
                Nhu cầu
              </label>
              <select
                value={nhuCau}
                onChange={(e) => setNhuCau(e.target.value)}
                style={{ width: "100%", padding: "0.55rem", borderRadius: "0.375rem", border: "1px solid #d1d5db" }}
              >
                <option value="BAN">Mua / Bán nhà đất</option>
                <option value="THUE">Thuê / Cho thuê</option>
              </select>
            </div>

            {/* Tiêu chí: Loại BĐS */}
            <div>
              <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#374151", marginBottom: "0.35rem" }}>
                Loại bất động sản
              </label>
              <select
                value={loaiBds}
                onChange={(e) => setLoaiBds(e.target.value)}
                style={{ width: "100%", padding: "0.55rem", borderRadius: "0.375rem", border: "1px solid #d1d5db" }}
              >
                <option value="CAN_HO">Căn hộ chung cư</option>
                <option value="NHA_RIENG">Nhà riêng / Nhà phố</option>
                <option value="DAT_NEN">Đất nền dự án</option>
                <option value="BIET_THU">Biệt thự</option>
                <option value="MAT_BANG">Mặt bằng kinh doanh</option>
              </select>
            </div>

            {/* Tiêu chí: Khu vực */}
            <div>
              <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#374151", marginBottom: "0.35rem" }}>
                Khu vực
              </label>
              <select
                value={khuVuc}
                onChange={(e) => setKhuVuc(e.target.value)}
                style={{ width: "100%", padding: "0.55rem", borderRadius: "0.375rem", border: "1px solid #d1d5db" }}
              >
                <option value="TPHCM">TP. Hồ Chí Minh</option>
                <option value="HA_NOI">Hà Nội</option>
                <option value="DA_NANG">Đà Nẵng</option>
                <option value="BINH_DUONG">Bình Dương</option>
                <option value="DONG_NAI">Đồng Nai</option>
              </select>
            </div>

            {/* Tiêu chí: Khoảng giá */}
            <div>
              <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#374151", marginBottom: "0.35rem" }}>
                Khoảng giá
              </label>
              <select
                value={mucGia}
                onChange={(e) => setMucGia(e.target.value)}
                style={{ width: "100%", padding: "0.55rem", borderRadius: "0.375rem", border: "1px solid #d1d5db" }}
              >
                <option value="ALL">Tất cả mức giá</option>
                <option value="UNDER_1B">Dưới 1 tỷ</option>
                <option value="1B_3B">1 - 3 tỷ</option>
                <option value="3B_5B">3 - 5 tỷ</option>
                <option value="5B_10B">5 - 10 tỷ</option>
                <option value="ABOVE_10B">Trên 10 tỷ</option>
              </select>
            </div>
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            <button
              type="submit"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "0.5rem",
                padding: "0.65rem 1.75rem",
                backgroundColor: "#2563eb",
                color: "#ffffff",
                border: "none",
                borderRadius: "0.375rem",
                fontWeight: 600,
                fontSize: "0.95rem",
                cursor: "pointer",
                boxShadow: "0 2px 4px rgba(37, 99, 235, 0.2)"
              }}
            >
              🔍 Tìm kiếm ngay
            </button>
          </div>
        </form>
      </section>

      {/* 2. KHU VỰC HIỂN THỊ CÁC TIN NỔI BẬT */}
      <section style={{ marginBottom: "2.5rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
          <div>
            <h2 style={{ margin: 0, fontSize: "1.35rem", color: "#111827" }}>
              Bất Động Sản Nổi Bật
            </h2>
            <p style={{ margin: "0.2rem 0 0", color: "#6b7280", fontSize: "0.875rem" }}>
              Các tin bất động sản đã kiểm duyệt và được người mua quan tâm nhiều nhất
            </p>
          </div>

          <Link to="/tim-kiem" style={{ color: "#2563eb", textDecoration: "none", fontSize: "0.875rem", fontWeight: 500 }}>
            Xem tất cả tin →
          </Link>
        </div>

        {/* Lưới các tin nổi bật */}
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
          gap: "1.5rem"
        }}>
          {tinNoiBatList.map((item) => (
            <div
              key={item.id}
              style={{
                backgroundColor: "#ffffff",
                borderRadius: "0.5rem",
                border: "1px solid #e5e7eb",
                overflow: "hidden",
                boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
                display: "flex",
                flexDirection: "column"
              }}
            >
              <div style={{ position: "relative", height: "160px", backgroundColor: "#e5e7eb" }}>
                <img
                  src={item.hinhAnh}
                  alt={item.tieuDe}
                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                />
                <span style={{
                  position: "absolute",
                  top: "0.5rem",
                  left: "0.5rem",
                  backgroundColor: "#dc2626",
                  color: "#ffffff",
                  fontSize: "0.75rem",
                  fontWeight: "bold",
                  padding: "0.2rem 0.5rem",
                  borderRadius: "0.25rem"
                }}>
                  Nổi bật
                </span>
                <span style={{
                  position: "absolute",
                  bottom: "0.5rem",
                  right: "0.5rem",
                  backgroundColor: "rgba(0,0,0,0.65)",
                  color: "#ffffff",
                  fontSize: "0.75rem",
                  padding: "0.2rem 0.5rem",
                  borderRadius: "0.25rem"
                }}>
                  {item.loaiGiaoDich}
                </span>
              </div>

              <div style={{ padding: "1rem", flex: 1, display: "flex", flexDirection: "column" }}>
                <h3 style={{
                  fontSize: "0.95rem",
                  fontWeight: 600,
                  color: "#111827",
                  margin: "0 0 0.5rem",
                  lineHeight: "1.4"
                }}>
                  {item.tieuDe}
                </h3>

                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
                  <span style={{ color: "#dc2626", fontWeight: "bold", fontSize: "1.05rem" }}>
                    {item.gia}
                  </span>
                  <span style={{ color: "#4b5563", fontSize: "0.85rem" }}>
                    {item.dienTich}
                  </span>
                </div>

                <p style={{ color: "#6b7280", fontSize: "0.8rem", margin: "0 0 1rem" }}>
                  📍 {item.diaChi}
                </p>

                <div style={{ marginTop: "auto" }}>
                  <Link
                    to={`/tin-dang/${item.id}`}
                    style={{
                      display: "block",
                      textAlign: "center",
                      padding: "0.45rem",
                      backgroundColor: "#f3f4f6",
                      color: "#1d4ed8",
                      borderRadius: "0.375rem",
                      textDecoration: "none",
                      fontSize: "0.85rem",
                      fontWeight: 500
                    }}
                  >
                    Xem chi tiết tin
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* 3. THÔNG BÁO KẾT NỐI API BACKEND (PHỤ TRỢ CHO DEV) */}
      <div style={{
        padding: "0.75rem 1rem",
        backgroundColor: "#f9fafb",
        border: "1px dashed #d1d5db",
        borderRadius: "0.375rem",
        fontSize: "0.8rem",
        color: "#6b7280"
      }}>
        ⚙️ <strong>Hạ tầng API client:</strong> {backendStatus}
      </div>
    </div>
  );
}
