import { createBrowserRouter, Navigate } from "react-router";
import { MainLayout } from "@/layouts/MainLayout";
import { AuthLayout } from "@/layouts/AuthLayout";
import { AdminLayout } from "@/layouts/AdminLayout";

import { ProtectedRoute } from "@/components/guards/ProtectedRoute";
import { RoleGuard } from "@/components/guards/RoleGuard";
import { GuestOnlyRoute } from "@/components/guards/GuestOnlyRoute";

import { HomePage } from "@/pages/public/HomePage";
import { SearchPage } from "@/pages/public/SearchPage";
import { PropertyDetailPage } from "@/pages/public/PropertyDetailPage";

import { LoginPage } from "@/pages/auth/LoginPage";
import { RegisterPage } from "@/pages/auth/RegisterPage";
import { VerifyEmailPage } from "@/pages/auth/VerifyEmailPage";
import { ForgotPasswordPage } from "@/pages/auth/ForgotPasswordPage";
import { StaffLoginPage } from "@/pages/auth/StaffLoginPage";

import { CreatePropertyPage } from "@/pages/user/CreatePropertyPage";
import { MyPropertiesPage } from "@/pages/user/MyPropertiesPage";
import { SavedPropertiesPage } from "@/pages/user/SavedPropertiesPage";
import { WalletPage } from "@/pages/user/WalletPage";

import { KdvDashboardPage } from "@/pages/kdv/KdvDashboardPage";
import { ModerationQueuePage } from "@/pages/kdv/ModerationQueuePage";
import { ReportHandlingPage } from "@/pages/kdv/ReportHandlingPage";

import { AdminOverviewPage } from "@/pages/admin/AdminOverviewPage";
import { AdminDashboardPage } from "@/pages/admin/AdminDashboardPage";
import { UserManagementPage } from "@/pages/admin/UserManagementPage";
import { ReconciliationPage } from "@/pages/admin/ReconciliationPage";
import { ComplaintResolutionPage } from "@/pages/admin/ComplaintResolutionPage";
import { ActivityMonitoringPage } from "@/pages/admin/ActivityMonitoringPage";

import { NotFoundPage } from "@/pages/error/NotFoundPage";
import { ForbiddenPage } from "@/pages/error/ForbiddenPage";

export const router = createBrowserRouter([
  // 1. Nhóm Route công khai & giao dịch BĐS (MainLayout)
  {
    path: "/",
    element: <MainLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "tim-kiem", element: <SearchPage /> },
      { path: "tin-dang/:id", element: <PropertyDetailPage /> },
      { path: "quan-ly-tin", element: <MyPropertiesPage /> },

      // Route yêu cầu đăng nhập (Người dùng)
      {
        element: <ProtectedRoute />,
        children: [
          { path: "dang-tin", element: <CreatePropertyPage /> },
          { path: "tin-da-luu", element: <SavedPropertiesPage /> },
          { path: "vi-tien", element: <WalletPage /> },
        ],
      },
    ],
  },

  // 2. Nhóm Route xác thực (AuthLayout)
  {
    element: (
      <GuestOnlyRoute>
        <AuthLayout />
      </GuestOnlyRoute>
    ),
    children: [
      { path: "dang-nhap", element: <LoginPage /> },
      { path: "dang-ky", element: <RegisterPage /> },
      { path: "staff/login", element: <StaffLoginPage /> },
    ],
  },
  {
    element: <AuthLayout />,
    children: [
      { path: "xac-minh-email", element: <VerifyEmailPage /> },
      { path: "auth/verify-email", element: <VerifyEmailPage /> },
      { path: "quen-mat-khau", element: <ForgotPasswordPage /> },
      { path: "dat-lai-mat-khau", element: <ForgotPasswordPage /> },
      { path: "auth/reset-password", element: <ForgotPasswordPage /> },
    ],
  },

  // 3. Nhóm Route Kiểm duyệt viên & Quản trị viên (AdminLayout - Tách biệt quyền theo MaTranQuyenSprint1)
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AdminLayout />,
        children: [
          // Chỉ dành riêng cho Kiểm duyệt viên (UC-13, UC-14)
          {
            element: <RoleGuard allowedRoles={["KDV"]} />,
            children: [
              { path: "kdv", element: <Navigate to="/kdv/dashboard" replace /> },
              { path: "kdv/dashboard", element: <KdvDashboardPage /> },
              { path: "kdv/tham-dinh", element: <ModerationQueuePage /> },
              { path: "kdv/bao-cao", element: <ReportHandlingPage /> },
            ],
          },
          // Chỉ dành riêng cho Quản trị viên (UC-15 -> UC-24)
          {
            element: <RoleGuard allowedRoles={["ADMIN"]} />,
            children: [
              { path: "admin", element: <Navigate to="/admin/dashboard" replace /> },
              { path: "admin/dashboard", element: <AdminOverviewPage /> },
              { path: "admin/thong-ke", element: <AdminDashboardPage /> },
              { path: "admin/tai-khoan", element: <UserManagementPage /> },
              { path: "admin/doi-soat", element: <ReconciliationPage /> },
              { path: "admin/khieu-nai", element: <ComplaintResolutionPage /> },
              { path: "admin/giam-sat", element: <ActivityMonitoringPage /> },
            ],
          },
        ],
      },
    ],
  },

  // 4. Các trang lỗi (403, 404)
  { path: "403", element: <ForbiddenPage /> },
  { path: "*", element: <NotFoundPage /> },
]);
