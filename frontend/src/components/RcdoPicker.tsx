import React, { useState, useEffect, useCallback } from "react";
import type { RcdoCry, RcdoSearchResult } from "@weekly-commitments/contracts";
import styles from "./RcdoPicker.module.css";

export interface RcdoSelection {
  outcomeId: string;
  outcomeName: string;
  objectiveId: string;
  objectiveName: string;
  rallyCryId: string;
  rallyCryName: string;
}

export interface RcdoPickerProps {
  value: string | null; // outcomeId
  onChange: (selection: RcdoSelection | null) => void;
  tree: RcdoCry[];
  searchResults: RcdoSearchResult[];
  onSearch: (query: string) => void;
  onClearSearch: () => void;
  disabled?: boolean;
}

/**
 * RCDO outcome picker with typeahead search and tree browse.
 * Supports both search-based and tree-based selection.
 */
export const RcdoPicker: React.FC<RcdoPickerProps> = ({
  value,
  onChange,
  tree,
  searchResults,
  onSearch,
  onClearSearch,
  disabled = false,
}) => {
  const [mode, setMode] = useState<"search" | "browse">("search");
  const [query, setQuery] = useState("");
  const [expandedCries, setExpandedCries] = useState<Set<string>>(new Set());
  const [expandedObjectives, setExpandedObjectives] = useState<Set<string>>(new Set());

  // Find current selection label from tree
  const findOutcomeLabel = useCallback((): string => {
    if (!value) return "";
    for (const cry of tree) {
      for (const obj of cry.objectives) {
        for (const outcome of obj.outcomes) {
          if (outcome.id === value) {
            return `${cry.name} → ${obj.name} → ${outcome.name}`;
          }
        }
      }
    }
    return value;
  }, [value, tree]);

  useEffect(() => {
    if (query.length >= 2) {
      const timer = setTimeout(() => onSearch(query), 300);
      return () => clearTimeout(timer);
    }
    onClearSearch();
    return undefined;
  }, [query, onSearch, onClearSearch]);

  const handleSearchSelect = (result: RcdoSearchResult) => {
    onChange({
      outcomeId: result.id,
      outcomeName: result.name,
      objectiveId: result.objectiveId,
      objectiveName: result.objectiveName,
      rallyCryId: result.rallyCryId,
      rallyCryName: result.rallyCryName,
    });
    setQuery("");
    onClearSearch();
  };

  const toggleCry = (id: string) => {
    setExpandedCries((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleObjective = (id: string) => {
    setExpandedObjectives((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div data-testid="rcdo-picker" className={styles.container}>
      {/* ── Current selection chip ── */}
      {value && (
        <div data-testid="rcdo-current" className={styles.currentChip}>
          <span className={styles.currentLabel}>Linked: {findOutcomeLabel()}</span>
          {!disabled && (
            <button
              type="button"
              onClick={() => onChange(null)}
              className={styles.clearButton}
              data-testid="rcdo-clear"
              aria-label="Clear RCDO selection"
            >
              ✕
            </button>
          )}
        </div>
      )}

      {/* ── Mode toggle + picker UI (hidden when disabled) ── */}
      {!disabled && (
        <>
          {/* ── Mode toggle bar ── */}
          <div className={styles.modeBar}>
            <button
              type="button"
              data-testid="rcdo-mode-search"
              onClick={() => setMode("search")}
              aria-pressed={mode === "search"}
              className={[styles.modeButton, mode === "search" ? styles.modeButtonActive : ""].join(" ").trim()}
            >
              Search
            </button>
            <button
              type="button"
              data-testid="rcdo-mode-browse"
              onClick={() => setMode("browse")}
              aria-pressed={mode === "browse"}
              className={[styles.modeButton, mode === "browse" ? styles.modeButtonActive : ""].join(" ").trim()}
            >
              Browse
            </button>
          </div>

          {/* ── Search panel ── */}
          {mode === "search" && (
            <div className={styles.searchPanel}>
              <input
                data-testid="rcdo-search-input"
                type="text"
                placeholder="Search outcomes…"
                aria-label="Search outcomes"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className={styles.searchInput}
              />
              {searchResults.length > 0 && (
                <ul data-testid="rcdo-search-results" className={styles.resultsList}>
                  {searchResults.map((r) => (
                    <li key={r.id} className={styles.resultsItem}>
                      <button
                        type="button"
                        onClick={() => handleSearchSelect(r)}
                        className={styles.resultButton}
                        data-testid={`rcdo-result-${r.id}`}
                      >
                        <strong className={styles.resultName}>{r.name}</strong>
                        <span className={styles.resultMeta}>
                          {r.rallyCryName} → {r.objectiveName}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {/* ── Browse panel ── */}
          {mode === "browse" && (
            <div data-testid="rcdo-tree-browser" className={styles.browsePanel}>
              {tree.map((cry) => (
                <div key={cry.id} className={styles.cryNode}>
                  <button
                    type="button"
                    onClick={() => toggleCry(cry.id)}
                    aria-expanded={expandedCries.has(cry.id)}
                    className={styles.cryToggle}
                  >
                    <span className={styles.chevron}>{expandedCries.has(cry.id) ? "▾" : "▸"}</span>
                    {cry.name}
                  </button>
                  {expandedCries.has(cry.id) && (
                    <div className={styles.objectiveList}>
                      {cry.objectives.map((obj) => (
                        <div key={obj.id} className={styles.objectiveNode}>
                          <button
                            type="button"
                            onClick={() => toggleObjective(obj.id)}
                            aria-expanded={expandedObjectives.has(obj.id)}
                            className={styles.objectiveToggle}
                          >
                            <span className={styles.chevron}>{expandedObjectives.has(obj.id) ? "▾" : "▸"}</span>
                            {obj.name}
                          </button>
                          {expandedObjectives.has(obj.id) && (
                            <div className={styles.outcomeList}>
                              {obj.outcomes.map((outcome) => (
                                <button
                                  type="button"
                                  key={outcome.id}
                                  onClick={() =>
                                    onChange({
                                      outcomeId: outcome.id,
                                      outcomeName: outcome.name,
                                      objectiveId: obj.id,
                                      objectiveName: obj.name,
                                      rallyCryId: cry.id,
                                      rallyCryName: cry.name,
                                    })
                                  }
                                  className={[
                                    styles.outcomeButton,
                                    value === outcome.id ? styles.outcomeButtonSelected : "",
                                  ]
                                    .join(" ")
                                    .trim()}
                                  data-testid={`rcdo-outcome-${outcome.id}`}
                                >
                                  {outcome.name}
                                </button>
                              ))}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
};
