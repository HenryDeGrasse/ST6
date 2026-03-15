import { describe, it, expect, vi } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { ToastProvider, useToast } from "../context/ToastContext.js";

/** Helper component that triggers a toast on button click */
const TestConsumer: React.FC = () => {
  const { showToast } = useToast();
  return (
    <button data-testid="trigger" onClick={() => showToast("Action completed")}>
      Trigger
    </button>
  );
};

describe("ToastProvider", () => {
  it("renders a toast message when showToast is called", () => {
    render(
      <ToastProvider>
        <TestConsumer />
      </ToastProvider>,
    );

    act(() => {
      screen.getByTestId("trigger").click();
    });

    expect(screen.getByTestId("toast-container")).toBeInTheDocument();
    expect(screen.getByText("Action completed")).toBeInTheDocument();
  });

  it("auto-dismisses toast after timeout", () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <TestConsumer />
      </ToastProvider>,
    );

    act(() => {
      screen.getByTestId("trigger").click();
    });
    expect(screen.getByText("Action completed")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(3500);
    });

    expect(screen.queryByText("Action completed")).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});
