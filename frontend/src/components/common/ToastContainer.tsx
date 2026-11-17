import React from "react";
import { useToastStore, type ToastItem } from "@/stores/useToastStore";

const toastStyles: Record<
  ToastItem["type"],
  { border: string; bg: string; iconBg: string; iconColor: string; icon: string }
> = {
  success: {
    border: "#10b981",
    bg: "#ffffff",
    iconBg: "#ecfdf5",
    iconColor: "#059669",
    icon: "✓",
  },
  error: {
    border: "#ef4444",
    bg: "#ffffff",
    iconBg: "#fef2f2",
    iconColor: "#dc2626",
    icon: "✕",
  },
  warning: {
    border: "#f59e0b",
    bg: "#ffffff",
    iconBg: "#fffbeb",
    iconColor: "#d97706",
    icon: "⚠",
  },
  info: {
    border: "#3b82f6",
    bg: "#ffffff",
    iconBg: "#eff6ff",
    iconColor: "#2563eb",
    icon: "ℹ",
  },
};

export const ToastContainer: React.FC = () => {
  const { toasts, removeToast } = useToastStore();

  if (toasts.length === 0) return null;

  return (
    <div
      style={{
        position: "fixed",
        top: "1.25rem",
        right: "1.25rem",
        zIndex: 99999,
        display: "flex",
        flexDirection: "column",
        gap: "0.6rem",
        maxWidth: "24rem",
        width: "calc(100vw - 2.5rem)",
        pointerEvents: "none",
      }}
    >
      <style>{`
        @keyframes toastSlideInRight {
          from {
            transform: translateX(100%);
            opacity: 0;
          }
          to {
            transform: translateX(0);
            opacity: 1;
          }
        }
      `}</style>
      {toasts.map((toast) => {
        const style = toastStyles[toast.type];
        return (
          <div
            key={toast.id}
            role="status"
            aria-live="polite"
            style={{
              pointerEvents: "auto",
              animation: "toastSlideInRight 0.28s cubic-bezier(0.16, 1, 0.3, 1)",
              backgroundColor: style.bg,
              borderLeft: `4px solid ${style.border}`,
              borderRadius: "0.5rem",
              boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.12), 0 8px 10px -6px rgba(0, 0, 0, 0.08)",
              padding: "0.75rem 1rem",
              display: "flex",
              alignItems: "flex-start",
              gap: "0.75rem",
              borderTop: "1px solid #f1f5f9",
              borderRight: "1px solid #f1f5f9",
              borderBottom: "1px solid #f1f5f9",
              color: "#1e293b",
            }}
          >
            {/* Icon circle */}
            <div
              style={{
                width: "1.75rem",
                height: "1.75rem",
                borderRadius: "50%",
                backgroundColor: style.iconBg,
                color: style.iconColor,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: 700,
                fontSize: "0.9rem",
                flexShrink: 0,
                marginTop: "0.1rem",
              }}
            >
              {style.icon}
            </div>

            {/* Content */}
            <div style={{ flex: 1, minWidth: 0 }}>
              {toast.title && (
                <div style={{ fontWeight: 600, fontSize: "0.88rem", marginBottom: "0.15rem", color: "#0f172a" }}>
                  {toast.title}
                </div>
              )}
              <div style={{ fontSize: "0.82rem", color: "#475569", lineHeight: 1.45, wordBreak: "break-word" }}>
                {toast.message}
              </div>
            </div>

            {/* Close button */}
            <button
              type="button"
              onClick={() => removeToast(toast.id)}
              aria-label="Đóng thông báo"
              style={{
                background: "transparent",
                border: "none",
                cursor: "pointer",
                padding: "0.2rem",
                color: "#94a3b8",
                fontSize: "1.1rem",
                lineHeight: 1,
                marginLeft: "0.25rem",
                borderRadius: "0.25rem",
                transition: "color 0.15s ease",
              }}
              onMouseEnter={(e) => ((e.target as HTMLElement).style.color = "#334155")}
              onMouseLeave={(e) => ((e.target as HTMLElement).style.color = "#94a3b8")}
            >
              ✕
            </button>
          </div>
        );
      })}
    </div>
  );
};
