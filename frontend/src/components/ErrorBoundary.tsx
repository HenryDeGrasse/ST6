import React from "react";

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
          style={{
            padding: "2rem",
            textAlign: "center",
            color: "#b71c1c",
            background: "#ffebee",
            borderRadius: "8px",
            margin: "1rem",
          }}
        >
          <h2 style={{ marginBottom: "0.5rem" }}>
            Weekly Commitments encountered an error
          </h2>
          <p style={{ marginBottom: "1rem", color: "#555" }}>
            {this.state.error?.message ?? "An unexpected error occurred."}
          </p>
          <button
            data-testid="wc-error-boundary-reset"
            onClick={this.handleReset}
            style={{
              padding: "0.5rem 1.5rem",
              cursor: "pointer",
              borderRadius: "4px",
              border: "1px solid #b71c1c",
              background: "#fff",
              color: "#b71c1c",
              fontWeight: "bold",
            }}
          >
            Try again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
