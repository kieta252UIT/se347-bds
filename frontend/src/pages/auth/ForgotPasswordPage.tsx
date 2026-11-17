import { useNavigate } from "react-router";
import ForgotPasswordModal from "./ForgotPasswordModal";

export function ForgotPasswordPage() {
  const navigate = useNavigate();

  return (
    <ForgotPasswordModal
      onClose={() => navigate("/dang-nhap")}
      onSwitchToRegister={() => navigate("/dang-ky")}
    />
  );
}

export default ForgotPasswordPage;
