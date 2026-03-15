import React from "react";

export interface ErrorBannerProps {
  message: string | null;
  onDismiss?: () => void;
}

/**
 * Inline error banner for transient API errors. Dismissible.
 */
export const ErrorBanner: React.FC<ErrorBannerProps> = ({ message, onDismiss }) => {
  if (!message) return null;

  return (
    <div
      data-testid="error-banner"
      role="alert"
      style={{
        padding: "0.75rem 1rem",
        background: "#ffebee",
        color: "#b71c1c",
        borderRadius: "4px",
        marginBottom: "0.75rem",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
      }}
    >
      <span>{message}</span>
      {onDismiss && (
        <button
          data-testid="error-dismiss"
          onClick={onDismiss}
          style={{ background: "none", border: "none", cursor: "pointer", fontSize: "1.1rem" }}
          aria-label="Dismiss error"
        >
          ✕
        </button>
      )}
    </div>
  );
};
