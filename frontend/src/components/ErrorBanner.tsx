import React from "react";
import { StatusIcon } from "./icons/StatusIcon.js";
import styles from "./ErrorBanner.module.css";

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
    <div data-testid="error-banner" role="alert" className={styles.banner}>
      <span className={styles.message}>{message}</span>
      {onDismiss && (
        <button
          data-testid="error-dismiss"
          onClick={onDismiss}
          className={styles.dismissBtn}
          aria-label="Dismiss error"
        >
          <StatusIcon icon="error-x" size={16} />
        </button>
      )}
    </div>
  );
};
