import { Outlet } from "react-router";
import { Header } from "@/components/common/Header";
import { Footer } from "@/components/common/Footer";

export function MainLayout() {
  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100vh", backgroundColor: "#f3f4f6" }}>
      <Header />
      <main style={{ flex: 1, maxWidth: "1200px", width: "100%", margin: "0 auto", padding: "1.5rem" }}>
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
