import React from "react";
import {
  getWeekStart,
  getNextWeekStart,
  getPrevWeekStart,
  formatWeekLabel,
  isCurrentWeek,
} from "../utils/week.js";
import styles from "./WeekSelector.module.css";

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
    <div data-testid="week-selector" className={styles.nav}>
      <button
        type="button"
        data-testid="week-prev"
        onClick={() => onWeekChange(getPrevWeekStart(selectedWeek))}
        aria-label="Previous week"
        className={styles.arrowButton}
      >
        ←
      </button>

      <span
        data-testid="week-label"
        className={[styles.weekLabel, isCurrent ? styles.weekLabelCurrent : ""].join(" ").trim()}
      >
        {formatWeekLabel(selectedWeek)}
      </span>

      <button
        type="button"
        data-testid="week-next"
        onClick={() => onWeekChange(getNextWeekStart(selectedWeek))}
        disabled={!canGoNext}
        aria-label="Next week"
        className={styles.arrowButton}
      >
        →
      </button>

      {!isCurrent && (
        <button
          type="button"
          data-testid="week-today"
          onClick={() => onWeekChange(currentWeek)}
          className={styles.todayButton}
        >
          Today
        </button>
      )}
    </div>
  );
};
