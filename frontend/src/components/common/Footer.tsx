import { Link } from "react-router";

function HomeIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m3 11 9-7 9 7" />
      <path d="M5 10v10h14V10M9 20v-6h6v6" />
    </svg>
  );
}

export function Footer() {
  return (
    <footer className="hf-footer">
      <div className="hf-footer-brand">
        <Link className="hf-brand" to="/">
          <span>
            <HomeIcon />
          </span>
          HomeFinder
        </Link>
        <p>
          Nền tảng tìm kiếm bất động sản đáng tin cậy, giúp bạn tìm đúng nơi và chọn đúng nhà.
        </p>
      </div>
      <div className="hf-footer-col">
        <strong>Khám phá</strong>
        <Link to="/tim-kiem">Tìm kiếm bất động sản</Link>
        <Link to="/">Gợi ý cho bạn</Link>
      </div>
      <div className="hf-footer-col">
        <strong>Hỗ trợ</strong>
        <a href="mailto:hotro@homefinder.vn?subject=Trung%20t%C3%A2m%20tr%E1%BB%A3%20gi%C3%BAp">
          Trung tâm trợ giúp
        </a>
        <a href="mailto:hotro@homefinder.vn?subject=%C4%90i%E1%BB%81u%20kho%E1%BA%A3n%20s%E1%BB%AD%20d%E1%BB%A5ng">
          Điều khoản sử dụng
        </a>
        <a href="mailto:hotro@homefinder.vn?subject=Ch%C3%ADnh%20s%C3%A1ch%20b%E1%BA%A3o%20m%E1%BA%ADt">
          Chính sách bảo mật
        </a>
      </div>
      <div className="hf-footer-col">
        <strong>Liên hệ</strong>
        <a href="tel:19000000">1900 0000</a>
        <a href="mailto:hotro@homefinder.vn">hotro@homefinder.vn</a>
        <Link to="/tim-kiem?city=Th%C3%A0nh%20ph%E1%BB%91%20H%E1%BB%93%20Ch%C3%AD%20Minh">
          TP. Hồ Chí Minh
        </Link>
      </div>
      <small>© 2026 HomeFinder.</small>
    </footer>
  );
}
