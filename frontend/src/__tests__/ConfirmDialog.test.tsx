import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ConfirmDialog } from "../components/ConfirmDialog.js";

describe("ConfirmDialog", () => {
  const defaultProps = {
    title: "Confirm Action",
    message: "Are you sure?",
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  };

  it("renders with title, message, and buttons", () => {
    render(<ConfirmDialog {...defaultProps} />);

    expect(screen.getByTestId("confirm-dialog")).toBeInTheDocument();
    expect(screen.getByText("Confirm Action")).toBeInTheDocument();
    expect(screen.getByText("Are you sure?")).toBeInTheDocument();
    expect(screen.getByTestId("confirm-dialog-confirm")).toHaveTextContent("Confirm");
    expect(screen.getByTestId("confirm-dialog-cancel")).toHaveTextContent("Cancel");
  });

  it("has correct aria attributes for accessibility", () => {
    render(<ConfirmDialog {...defaultProps} />);

    const dialog = screen.getByTestId("confirm-dialog");
    expect(dialog).toHaveAttribute("role", "dialog");
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAttribute("aria-labelledby", "confirm-dialog-title");
    expect(dialog).toHaveAttribute("aria-describedby", "confirm-dialog-message");
  });

  it("focuses the cancel button on open", () => {
    render(<ConfirmDialog {...defaultProps} />);

    expect(screen.getByTestId("confirm-dialog-cancel")).toHaveFocus();
  });

  it("traps focus within the dialog", () => {
    render(<ConfirmDialog {...defaultProps} />);

    const cancelButton = screen.getByTestId("confirm-dialog-cancel");
    const confirmButton = screen.getByTestId("confirm-dialog-confirm");

    expect(cancelButton).toHaveFocus();

    confirmButton.focus();
    fireEvent.keyDown(document, { key: "Tab" });
    expect(cancelButton).toHaveFocus();

    cancelButton.focus();
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true });
    expect(confirmButton).toHaveFocus();
  });

  it("calls onConfirm when confirm button is clicked", () => {
    const onConfirm = vi.fn();
    render(<ConfirmDialog {...defaultProps} onConfirm={onConfirm} />);

    fireEvent.click(screen.getByTestId("confirm-dialog-confirm"));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it("calls onCancel when cancel button is clicked", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);

    fireEvent.click(screen.getByTestId("confirm-dialog-cancel"));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("calls onCancel when Escape is pressed", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);

    fireEvent.keyDown(document, { key: "Escape" });
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("calls onCancel when backdrop is clicked", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);

    fireEvent.click(screen.getByTestId("confirm-dialog-overlay"));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("does not call onCancel when dialog body is clicked", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} />);

    fireEvent.click(screen.getByTestId("confirm-dialog"));
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("uses custom confirm and cancel labels", () => {
    render(
      <ConfirmDialog
        {...defaultProps}
        confirmLabel="Delete"
        cancelLabel="Keep"
      />,
    );

    expect(screen.getByTestId("confirm-dialog-confirm")).toHaveTextContent("Delete");
    expect(screen.getByTestId("confirm-dialog-cancel")).toHaveTextContent("Keep");
  });

  it("disables buttons and blocks dismissal while loading", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...defaultProps} onCancel={onCancel} loading={true} />);

    expect(screen.getByTestId("confirm-dialog-confirm")).toBeDisabled();
    expect(screen.getByTestId("confirm-dialog-cancel")).toBeDisabled();

    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.click(screen.getByTestId("confirm-dialog-overlay"));

    expect(onCancel).not.toHaveBeenCalled();
    expect(screen.getByTestId("confirm-dialog")).toHaveAttribute("aria-busy", "true");
  });
});
