import React from "react";
import {
  getWeekStart,
  getNextWeekStart,
  getPrevWeekStart,
  formatWeekLabel,
  isCurrentWeek,
} from "../utils/week.js";

export interface WeekSelectorProps {
  selectedWeek: string;
  onWeekChange: (weekStart: string) => void;
}

/**
 * Week navigation bar. Users can move between weeks and
 * jump to the current week. Only current + next week are
 * creatable, but past weeks remain viewable.
 */
export const WeekSelector: React.FC<WeekSelectorProps> = ({
  selectedWeek,
  onWeekChange,
}) => {
  const currentWeek = getWeekStart();
  const nextWeek = getNextWeekStart(currentWeek);
  const isCurrent = isCurrentWeek(selectedWeek);
  const canGoNext = selectedWeek < nextWeek;

  return (
    <div data-testid="week-selector" style={{ display: "flex", alignItems: "center", gap: "0.75rem", padding: "0.5rem 0" }}>
      <button
        data-testid="week-prev"
        onClick={() => onWeekChange(getPrevWeekStart(selectedWeek))}
        aria-label="Previous week"
      >
        ← Prev
      </button>
      <span data-testid="week-label" style={{ fontWeight: 600, minWidth: "180px", textAlign: "center" }}>
        {formatWeekLabel(selectedWeek)}
      </span>
      <button
        data-testid="week-next"
        onClick={() => onWeekChange(getNextWeekStart(selectedWeek))}
        disabled={!canGoNext}
        aria-label="Next week"
      >
        Next →
      </button>
      {!isCurrent && (
        <button
          data-testid="week-today"
          onClick={() => onWeekChange(currentWeek)}
        >
          Today
        </button>
      )}
    </div>
  );
};
