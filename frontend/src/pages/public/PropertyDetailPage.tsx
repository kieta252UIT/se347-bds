import { useParams } from "react-router";

export function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <div style={{ backgroundColor: "#ffffff", padding: "2rem", borderRadius: "0.5rem", border: "1px solid #e5e7eb" }}>
      <h2>Chi Tiết Bất Động Sản</h2>
      <p style={{ color: "#6b7280" }}>
        Đang xem tin mã: <strong>#{id}</strong>.
      </p>
      <p style={{ color: "#6b7280" }}>
        Hiển thị chi tiết, thông tin liên hệ và lưu/bỏ lưu tin (task nối UI ngày 20/11).
      </p>
    </div>
  );
}
