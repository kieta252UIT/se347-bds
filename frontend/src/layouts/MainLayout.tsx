import { Outlet } from "react-router";
import { Header } from "@/components/common/Header";
import { Footer } from "@/components/common/Footer";
import "./user-shell.css";

export function MainLayout() {
  return (
    <div className="hf-page-shell">
      <Header />
      <div className="hf-main-stage">
        <Outlet />
      </div>
      <Footer />
    </div>
  );
}
