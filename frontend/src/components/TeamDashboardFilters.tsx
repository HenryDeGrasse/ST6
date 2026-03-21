import React, { useEffect, useMemo, useState } from "react";
import { PlanState, ChessPriority, CommitCategory } from "@weekly-commitments/contracts";
import type { TeamDashboardFilters as Filters } from "../hooks/useTeamDashboard.js";
import styles from "./TeamDashboardFilters.module.css";

export interface TeamDashboardFiltersProps {
  filters: Filters;
  onFiltersChange: (filters: Filters) => void;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * Filter controls for the manager team dashboard.
 * Filters: state, outcomeId, priority, category, incomplete, nonStrategic.
 */
export const TeamDashboardFiltersPanel: React.FC<TeamDashboardFiltersProps> = ({ filters, onFiltersChange }) => {
  const [outcomeInput, setOutcomeInput] = useState(filters.outcomeId ?? "");

  useEffect(() => {
    setOutcomeInput(filters.outcomeId ?? "");
  }, [filters.outcomeId]);

  const outcomeIdIsValid = useMemo(
    () => outcomeInput.trim() === "" || UUID_PATTERN.test(outcomeInput.trim()),
    [outcomeInput],
  );

  const update = (patch: Partial<Filters>) => {
    onFiltersChange({ ...filters, ...patch });
  };

  const handleOutcomeChange = (value: string) => {
    setOutcomeInput(value);

    const normalized = value.trim();
    if (normalized === "") {
      update({ outcomeId: undefined });
      return;
    }

    if (UUID_PATTERN.test(normalized)) {
      update({ outcomeId: normalized });
    }
  };

  return (
    <div data-testid="team-filters" className={styles.filterRow}>
      <select
        data-testid="filter-state"
        aria-label="Filter by plan state"
        value={filters.state ?? ""}
        onChange={(e) => update({ state: e.target.value || undefined })}
        className={styles.select}
      >
        <option value="">All States</option>
        {Object.values(PlanState).map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>

      <input
        data-testid="filter-outcome-id"
        aria-label="Filter by outcome ID"
        type="text"
        value={outcomeInput}
        onChange={(e) => handleOutcomeChange(e.target.value)}
        placeholder="Outcome ID (UUID)"
        aria-invalid={!outcomeIdIsValid}
        className={`${styles.input}${outcomeIdIsValid ? "" : ` ${styles.inputError}`}`}
      />

      <select
        data-testid="filter-priority"
        aria-label="Filter by chess priority"
        value={filters.priority ?? ""}
        onChange={(e) => update({ priority: e.target.value || undefined })}
        className={styles.select}
      >
        <option value="">All Priorities</option>
        {Object.values(ChessPriority).map((p) => (
          <option key={p} value={p}>
            {p}
          </option>
        ))}
      </select>

      <select
        data-testid="filter-category"
        aria-label="Filter by commit category"
        value={filters.category ?? ""}
        onChange={(e) => update({ category: e.target.value || undefined })}
        className={styles.select}
      >
        <option value="">All Categories</option>
        {Object.values(CommitCategory).map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>

      <label className={styles.checkboxLabel}>
        <input
          data-testid="filter-incomplete"
          type="checkbox"
          checked={filters.incomplete ?? false}
          onChange={(e) => update({ incomplete: e.target.checked || undefined })}
        />
        Incomplete only
      </label>

      <label className={styles.checkboxLabel}>
        <input
          data-testid="filter-non-strategic"
          type="checkbox"
          checked={filters.nonStrategic ?? false}
          onChange={(e) => update({ nonStrategic: e.target.checked || undefined })}
        />
        Non-strategic only
      </label>

      {!outcomeIdIsValid && (
        <span data-testid="filter-outcome-id-error" className={styles.errorMessage}>
          Enter a valid outcome UUID to apply this filter.
        </span>
      )}
    </div>
  );
};
