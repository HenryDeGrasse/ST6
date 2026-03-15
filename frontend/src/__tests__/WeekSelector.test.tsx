import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { WeekSelector } from "../components/WeekSelector.js";

describe("WeekSelector", () => {
  const defaultProps = {
    selectedWeek: "2026-03-09",
    onWeekChange: vi.fn(),
  };

  it("displays the formatted week label", () => {
    render(<WeekSelector {...defaultProps} />);
    expect(screen.getByTestId("week-label")).toHaveTextContent("Mar 9 – 15, 2026");
  });

  it("navigates to previous week on prev click", () => {
    const onWeekChange = vi.fn();
    render(<WeekSelector {...defaultProps} onWeekChange={onWeekChange} />);
    fireEvent.click(screen.getByTestId("week-prev"));
    expect(onWeekChange).toHaveBeenCalledWith("2026-03-02");
  });

  it("navigates to next week on next click", () => {
    const onWeekChange = vi.fn();
    render(<WeekSelector {...defaultProps} onWeekChange={onWeekChange} />);
    fireEvent.click(screen.getByTestId("week-next"));
    expect(onWeekChange).toHaveBeenCalledWith("2026-03-16");
  });

  it("shows Today button when not on current week", () => {
    render(<WeekSelector selectedWeek="2026-01-05" onWeekChange={vi.fn()} />);
    expect(screen.getByTestId("week-today")).toBeInTheDocument();
  });
});
