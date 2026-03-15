import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from "react";

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

  const showToast = useCallback((text: string, type: ToastMessage["type"] = "success") => {
    const id = nextId.current++;
    setToasts((prev) => [...prev, { id, text, type }]);
    dismissTimers.current.set(
      id,
      setTimeout(() => {
        dismiss(id);
      }, TOAST_DURATION_MS),
    );
  }, [dismiss]);

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
        <div
          data-testid="toast-container"
          role="status"
          aria-live="polite"
          style={{
            position: "fixed",
            bottom: "1rem",
            right: "1rem",
            display: "flex",
            flexDirection: "column",
            gap: "0.5rem",
            zIndex: 9999,
          }}
        >
          {toasts.map((toast) => (
            <div
              key={toast.id}
              data-testid="toast-message"
              role="alert"
              style={{
                padding: "0.75rem 1rem",
                borderRadius: "6px",
                color: "#fff",
                fontWeight: 500,
                fontSize: "0.9rem",
                boxShadow: "0 2px 8px rgba(0,0,0,0.2)",
                cursor: "pointer",
                background:
                  toast.type === "success"
                    ? "#16a34a"
                    : toast.type === "error"
                      ? "#dc2626"
                      : "#2563eb",
              }}
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
