import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { EffortTypePicker } from "../components/EffortTypePicker.js";
import { EffortType } from "@weekly-commitments/contracts";

describe("EffortTypePicker", () => {
  it("renders all four effort type chips", () => {
    render(<EffortTypePicker value={null} onChange={vi.fn()} />);
    expect(screen.getByTestId("effort-type-chip-BUILD")).toBeInTheDocument();
    expect(screen.getByTestId("effort-type-chip-MAINTAIN")).toBeInTheDocument();
    expect(screen.getByTestId("effort-type-chip-COLLABORATE")).toBeInTheDocument();
    expect(screen.getByTestId("effort-type-chip-LEARN")).toBeInTheDocument();
  });

  it("marks the selected chip as pressed", () => {
    render(<EffortTypePicker value={EffortType.BUILD} onChange={vi.fn()} />);
    const buildChip = screen.getByTestId("effort-type-chip-BUILD");
    expect(buildChip).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("effort-type-chip-MAINTAIN")).toHaveAttribute("aria-pressed", "false");
  });

  it("calls onChange with the clicked effort type", () => {
    const onChange = vi.fn();
    render(<EffortTypePicker value={null} onChange={onChange} />);
    fireEvent.click(screen.getByTestId("effort-type-chip-LEARN"));
    expect(onChange).toHaveBeenCalledWith(EffortType.LEARN);
  });

  it("toggles off when the selected chip is clicked again", () => {
    const onChange = vi.fn();
    render(<EffortTypePicker value={EffortType.BUILD} onChange={onChange} />);
    fireEvent.click(screen.getByTestId("effort-type-chip-BUILD"));
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("shows clear button when value is selected", () => {
    render(<EffortTypePicker value={EffortType.MAINTAIN} onChange={vi.fn()} />);
    expect(screen.getByTestId("effort-type-clear")).toBeInTheDocument();
  });

  it("does not show clear button when no value is selected", () => {
    render(<EffortTypePicker value={null} onChange={vi.fn()} />);
    expect(screen.queryByTestId("effort-type-clear")).toBeNull();
  });

  it("calls onChange(null) when clear button is clicked", () => {
    const onChange = vi.fn();
    render(<EffortTypePicker value={EffortType.COLLABORATE} onChange={onChange} />);
    fireEvent.click(screen.getByTestId("effort-type-clear"));
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("shows AI label on the suggested chip when no value is selected", () => {
    render(
      <EffortTypePicker value={null} onChange={vi.fn()} aiSuggestion={EffortType.BUILD} />,
    );
    const buildChip = screen.getByTestId("effort-type-chip-BUILD");
    expect(buildChip).toHaveTextContent("AI");
  });

  it("does not show AI label when a value is already selected", () => {
    render(
      <EffortTypePicker
        value={EffortType.LEARN}
        onChange={vi.fn()}
        aiSuggestion={EffortType.BUILD}
      />,
    );
    const buildChip = screen.getByTestId("effort-type-chip-BUILD");
    expect(buildChip).not.toHaveTextContent("AI");
  });

  it("disables all chips when disabled prop is set", () => {
    render(<EffortTypePicker value={null} onChange={vi.fn()} disabled />);
    expect(screen.getByTestId("effort-type-chip-BUILD")).toBeDisabled();
    expect(screen.getByTestId("effort-type-chip-LEARN")).toBeDisabled();
  });

  it("does not call onChange when disabled and chip is clicked", () => {
    const onChange = vi.fn();
    render(<EffortTypePicker value={null} onChange={onChange} disabled />);
    fireEvent.click(screen.getByTestId("effort-type-chip-BUILD"));
    expect(onChange).not.toHaveBeenCalled();
  });
});
