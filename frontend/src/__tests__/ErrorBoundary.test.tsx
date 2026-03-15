import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ErrorBoundary } from "../components/ErrorBoundary.js";

/** A component that throws on render to trigger the error boundary. */
const ThrowingChild: React.FC<{ shouldThrow?: boolean }> = ({ shouldThrow = true }) => {
  if (shouldThrow) {
    throw new Error("Test render error");
  }
  return <div data-testid="child-content">All good</div>;
};

describe("ErrorBoundary", () => {
  // Suppress React error boundary console.error noise during tests
  const originalConsoleError = console.error;
  beforeEach(() => {
    console.error = vi.fn();
  });
  afterEach(() => {
    console.error = originalConsoleError;
  });

  it("renders children when no error occurs", () => {
    render(
      <ErrorBoundary>
        <div data-testid="child-content">Hello</div>
      </ErrorBoundary>,
    );
    expect(screen.getByTestId("child-content")).toHaveTextContent("Hello");
    expect(screen.queryByTestId("wc-error-boundary")).not.toBeInTheDocument();
  });

  it("catches a thrown error and renders fallback UI", () => {
    render(
      <ErrorBoundary>
        <ThrowingChild />
      </ErrorBoundary>,
    );
    expect(screen.getByTestId("wc-error-boundary")).toBeInTheDocument();
    expect(screen.getByText("Weekly Commitments encountered an error")).toBeInTheDocument();
    expect(screen.getByText("Test render error")).toBeInTheDocument();
    expect(screen.getByTestId("wc-error-boundary-reset")).toHaveTextContent("Try again");
  });

  it("resets the error state when 'Try again' is clicked", () => {
    let shouldThrow = true;
    const Toggler: React.FC = () => {
      if (shouldThrow) {
        throw new Error("Boom");
      }
      return <div data-testid="child-content">Recovered</div>;
    };

    render(
      <ErrorBoundary>
        <Toggler />
      </ErrorBoundary>,
    );

    // Verify fallback is shown
    expect(screen.getByTestId("wc-error-boundary")).toBeInTheDocument();

    // Stop throwing before clicking reset
    shouldThrow = false;
    fireEvent.click(screen.getByTestId("wc-error-boundary-reset"));

    // Fallback should be gone, children re-rendered
    expect(screen.queryByTestId("wc-error-boundary")).not.toBeInTheDocument();
    expect(screen.getByTestId("child-content")).toHaveTextContent("Recovered");
  });

  it("has role='alert' on the fallback for accessibility", () => {
    render(
      <ErrorBoundary>
        <ThrowingChild />
      </ErrorBoundary>,
    );
    expect(screen.getByTestId("wc-error-boundary")).toHaveAttribute("role", "alert");
  });
});
