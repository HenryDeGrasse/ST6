import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CategoryPicker } from "../components/CategoryPicker.js";

describe("CategoryPicker", () => {
  it("renders with no selection", () => {
    render(<CategoryPicker value={null} onChange={vi.fn()} />);
    const select = screen.getByTestId("category-picker") as HTMLSelectElement;
    expect(select.value).toBe("");
  });

  it("shows all categories", () => {
    render(<CategoryPicker value={null} onChange={vi.fn()} />);
    expect(screen.getByRole("option", { name: "Delivery" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Tech Debt" })).toBeInTheDocument();
  });

  it("calls onChange when category is selected", () => {
    const onChange = vi.fn();
    render(<CategoryPicker value={null} onChange={onChange} />);
    fireEvent.change(screen.getByTestId("category-picker"), { target: { value: "DELIVERY" } });
    expect(onChange).toHaveBeenCalledWith("DELIVERY");
  });

  it("clears the selection when the placeholder option is chosen", () => {
    const onChange = vi.fn();
    render(<CategoryPicker value={null} onChange={onChange} />);
    fireEvent.change(screen.getByTestId("category-picker"), { target: { value: "" } });
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("disables when disabled prop is true", () => {
    render(<CategoryPicker value={null} onChange={vi.fn()} disabled />);
    expect(screen.getByTestId("category-picker")).toBeDisabled();
  });
});
