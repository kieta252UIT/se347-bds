import { create } from "zustand";

export type ToastType = "success" | "error" | "info" | "warning";

export interface ToastItem {
  id: string;
  type: ToastType;
  title?: string;
  message: string;
  duration?: number;
}

interface ToastState {
  toasts: ToastItem[];
  showToast: (toast: Omit<ToastItem, "id">) => void;
  removeToast: (id: string) => void;
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  showToast: (toast) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
    const duration = toast.duration ?? 4000;
    set((state) => ({
      toasts: [...state.toasts, { ...toast, id }],
    }));

    if (duration > 0) {
      setTimeout(() => {
        set((state) => ({
          toasts: state.toasts.filter((t) => t.id !== id),
        }));
      }, duration);
    }
  },
  removeToast: (id) =>
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    })),
}));

export const toast = {
  success: (message: string, title: string = "Thành công") =>
    useToastStore.getState().showToast({ type: "success", title, message }),
  error: (message: string, title: string = "Thất bại") =>
    useToastStore.getState().showToast({ type: "error", title, message }),
  info: (message: string, title: string = "Thông báo") =>
    useToastStore.getState().showToast({ type: "info", title, message }),
  warning: (message: string, title: string = "Cảnh báo") =>
    useToastStore.getState().showToast({ type: "warning", title, message }),
};
