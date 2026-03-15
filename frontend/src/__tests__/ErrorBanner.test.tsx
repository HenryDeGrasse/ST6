import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ErrorBanner } from "../components/ErrorBanner.js";

describe("ErrorBanner", () => {
  it("renders nothing when message is null", () => {
    const { container } = render(<ErrorBanner message={null} />);
    expect(container.firstChild).toBeNull();
  });

  it("displays the error message", () => {
    render(<ErrorBanner message="Something went wrong" />);
    expect(screen.getByTestId("error-banner")).toHaveTextContent("Something went wrong");
  });

  it("calls onDismiss when dismiss button is clicked", () => {
    const onDismiss = vi.fn();
    render(<ErrorBanner message="Error" onDismiss={onDismiss} />);
    fireEvent.click(screen.getByTestId("error-dismiss"));
    expect(onDismiss).toHaveBeenCalledOnce();
  });

  it("does not show dismiss button when no onDismiss prop", () => {
    render(<ErrorBanner message="Error" />);
    expect(screen.queryByTestId("error-dismiss")).not.toBeInTheDocument();
  });
});
