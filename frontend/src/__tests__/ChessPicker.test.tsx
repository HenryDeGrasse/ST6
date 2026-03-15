import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ChessPicker } from "../components/ChessPicker.js";
import { ChessPriority } from "@weekly-commitments/contracts";

describe("ChessPicker", () => {
  it("renders with no selection", () => {
    render(<ChessPicker value={null} onChange={vi.fn()} />);
    const select = screen.getByTestId("chess-picker") as HTMLSelectElement;
    expect(select.value).toBe("");
  });

  it("shows all chess priorities", () => {
    render(<ChessPicker value={null} onChange={vi.fn()} />);
    for (const p of Object.values(ChessPriority)) {
      expect(screen.getByRole("option", { name: new RegExp(p, "i") })).toBeInTheDocument();
    }
  });

  it("calls onChange when a priority is selected", () => {
    const onChange = vi.fn();
    render(<ChessPicker value={null} onChange={onChange} />);
    fireEvent.change(screen.getByTestId("chess-picker"), { target: { value: "KING" } });
    expect(onChange).toHaveBeenCalledWith("KING");
  });

  it("renders the current value", () => {
    render(<ChessPicker value={ChessPriority.QUEEN} onChange={vi.fn()} />);
    const select = screen.getByTestId("chess-picker") as HTMLSelectElement;
    expect(select.value).toBe("QUEEN");
  });

  it("disables the select when disabled prop is true", () => {
    render(<ChessPicker value={null} onChange={vi.fn()} disabled />);
    expect(screen.getByTestId("chess-picker")).toBeDisabled();
  });
});
