import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from "react";
import styles from "./ToastContext.module.css";

export interface ToastMessage {
  id: number;
  text: string;
  type: "success" | "error" | "info";
}

export interface ToastContextValue {
  toasts: ToastMessage[];
  showToast: (text: string, type?: ToastMessage["type"]) => void;
}

const ToastContext = createContext<ToastContextValue>({
  toasts: [],
  showToast: () => {},
});

export const useToast = (): ToastContextValue => useContext(ToastContext);

const TOAST_DURATION_MS = 3000;

/** Map toast type to the corresponding CSS module class. */
const TYPE_CLASS: Record<ToastMessage["type"], string> = {
  success: styles.toastSuccess,
  error: styles.toastError,
  info: styles.toastInfo,
};

/**
 * Lightweight toast notification provider.
 * Renders a stack of auto-dismissing messages at the bottom-right of the viewport.
 */
export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const nextId = useRef(0);
  const dismissTimers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const dismiss = useCallback((id: number) => {
    const timer = dismissTimers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      dismissTimers.current.delete(id);
    }
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback(
    (text: string, type: ToastMessage["type"] = "success") => {
      const id = nextId.current++;
      setToasts((prev) => [...prev, { id, text, type }]);
      dismissTimers.current.set(
        id,
        setTimeout(() => {
          dismiss(id);
        }, TOAST_DURATION_MS),
      );
    },
    [dismiss],
  );

  useEffect(() => {
    const timers = dismissTimers.current;
    return () => {
      for (const timer of timers.values()) {
        clearTimeout(timer);
      }
      timers.clear();
    };
  }, []);

  return (
    <ToastContext.Provider value={{ toasts, showToast }}>
      {children}
      {toasts.length > 0 && (
        <div data-testid="toast-container" role="status" aria-live="polite" className={styles.container}>
          {toasts.map((toast) => (
            <div
              key={toast.id}
              data-testid="toast-message"
              role="alert"
              className={`${styles.toast} ${TYPE_CLASS[toast.type]}`}
              onClick={() => dismiss(toast.id)}
            >
              {toast.text}
            </div>
          ))}
        </div>
      )}
    </ToastContext.Provider>
  );
};
