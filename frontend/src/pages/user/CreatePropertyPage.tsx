import { useLocation, useNavigate } from "react-router";
import {
  UploadPropertyForm,
  type UploadPropertyInitialData,
} from "@/components/user/UploadPropertyForm";

export function CreatePropertyPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const initialData =
    (location.state as { initialData?: UploadPropertyInitialData } | null)
      ?.initialData || null;

  return (
    <UploadPropertyForm
      initialData={initialData}
      onCancel={() => navigate("/quan-ly-tin")}
      onSubmitSuccess={() => navigate("/quan-ly-tin")}
    />
  );
}
