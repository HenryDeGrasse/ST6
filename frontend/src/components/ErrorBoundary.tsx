import React from "react";
import styles from "./ErrorBoundary.module.css";

export interface ErrorBoundaryProps {
  children: React.ReactNode;
}

export interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * Top-level error boundary for the Weekly Commitments micro-frontend.
 *
 * Catches render errors so the micro-frontend never crashes the host app.
 * Displays a user-friendly fallback UI with a "Try again" button that
 * resets the error state and re-renders children.
 */
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          data-testid="wc-error-boundary"
          role="alert"
          className={styles.card}
        >
          <h2 className={styles.title}>
            Weekly Commitments encountered an error
          </h2>
          <p className={styles.detail}>
            {this.state.error?.message ?? "An unexpected error occurred."}
          </p>
          <button
            data-testid="wc-error-boundary-reset"
            onClick={this.handleReset}
            className={styles.resetBtn}
          >
            Try again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
