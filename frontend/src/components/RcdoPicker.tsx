import React, { useState, useEffect, useCallback, useRef } from "react";
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

/** Breadcrumb browse state */
type BrowseLevel =
  | { type: "root" }
  | { type: "cry"; cryId: string; cryName: string }
  | { type: "objective"; cryId: string; cryName: string; objectiveId: string; objectiveName: string };

/**
 * RCDO outcome picker.
 *
 * Browse is the default mode — shows a flat drill-down list with breadcrumb
 * navigation. A small search icon in the panel header expands an inline search
 * input for power users who know the outcome name. Pressing Escape or clicking
 * × collapses back to browse.
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
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [browseLevel, setBrowseLevel] = useState<BrowseLevel>({ type: "root" });
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Stabilize callback refs to prevent infinite useEffect loops
  const onSearchRef = useRef(onSearch);
  onSearchRef.current = onSearch;
  const onClearSearchRef = useRef(onClearSearch);
  onClearSearchRef.current = onClearSearch;

  // Focus input when search expands
  useEffect(() => {
    if (searchOpen) {
      setTimeout(() => searchInputRef.current?.focus(), 50);
    }
  }, [searchOpen]);

  // Debounced search
  useEffect(() => {
    if (query.length >= 2) {
      const timer = setTimeout(() => onSearchRef.current(query), 300);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [query]);

  // Find current selection label from tree
  const findOutcomeLabel = useCallback((): string => {
    if (!value) return "";
    for (const cry of tree) {
      for (const obj of cry.objectives) {
        for (const outcome of obj.outcomes) {
          if (outcome.id === value) {
            return `${cry.name} › ${obj.name} › ${outcome.name}`;
          }
        }
      }
    }
    return value;
  }, [value, tree]);

  const closeSearch = useCallback(() => {
    setSearchOpen(false);
    setQuery("");
    onClearSearchRef.current();
  }, []);

  const handleQueryChange = (newQuery: string) => {
    setQuery(newQuery);
    if (newQuery.length === 0) onClearSearchRef.current();
  };

  const handleSearchKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") closeSearch();
  };

  const handleSearchSelect = (result: RcdoSearchResult) => {
    onChange({
      outcomeId: result.id,
      outcomeName: result.name,
      objectiveId: result.objectiveId,
      objectiveName: result.objectiveName,
      rallyCryId: result.rallyCryId,
      rallyCryName: result.rallyCryName,
    });
    closeSearch();
  };

  // ─── Breadcrumb browse helpers ─────────────────────────────────────

  const drillIntoCry = (cry: RcdoCry) =>
    setBrowseLevel({ type: "cry", cryId: cry.id, cryName: cry.name });

  const drillIntoObjective = (
    cry: { id: string; name: string },
    obj: { id: string; name: string },
  ) =>
    setBrowseLevel({
      type: "objective",
      cryId: cry.id,
      cryName: cry.name,
      objectiveId: obj.id,
      objectiveName: obj.name,
    });

  const goToRoot = () => setBrowseLevel({ type: "root" });
  const goToCry = (cryId: string, cryName: string) =>
    setBrowseLevel({ type: "cry", cryId, cryName });

  const currentCry =
    browseLevel.type !== "root"
      ? tree.find((c) => c.id === browseLevel.cryId)
      : null;

  const currentObjective =
    browseLevel.type === "objective" && currentCry
      ? currentCry.objectives.find((o) => o.id === browseLevel.objectiveId)
      : null;

  return (
    <div data-testid="rcdo-picker" className={styles.container}>
      {/* ── Current selection chip ── */}
      {value && (
        <div data-testid="rcdo-current" className={styles.currentChip}>
          <span className={styles.currentLabel}>{findOutcomeLabel()}</span>
          {!disabled && (
            <button
              type="button"
              onClick={() => onChange(null)}
              className={styles.clearButton}
              data-testid="rcdo-clear"
              aria-label="Clear outcome link"
            >
              ×
            </button>
          )}
        </div>
      )}

      {/* ── Picker (hidden when disabled) ── */}
      {!disabled && (
        <div data-testid="rcdo-tree-browser" className={styles.browsePanel}>

          {/* ── Panel header: breadcrumb left, search icon right ── */}
          <div className={styles.panelHeader}>
            <nav className={styles.breadcrumb} data-testid="rcdo-breadcrumb">
              {browseLevel.type === "root" ? (
                <span className={styles.breadcrumbCurrent}>All rally cries</span>
              ) : (
                <>
                  <button
                    type="button"
                    data-testid="rcdo-breadcrumb-root"
                    onClick={goToRoot}
                    className={styles.breadcrumbLink}
                  >
                    All
                  </button>
                  <span className={styles.breadcrumbSep}>›</span>
                  {browseLevel.type === "cry" ? (
                    <span className={styles.breadcrumbCurrent}>{browseLevel.cryName}</span>
                  ) : (
                    <>
                      <button
                        type="button"
                        data-testid="rcdo-breadcrumb-cry"
                        onClick={() => goToCry(browseLevel.cryId, browseLevel.cryName)}
                        className={styles.breadcrumbLink}
                      >
                        {browseLevel.cryName}
                      </button>
                      <span className={styles.breadcrumbSep}>›</span>
                      <button
                        type="button"
                        data-testid="rcdo-breadcrumb-objective"
                        onClick={() => goToCry(browseLevel.cryId, browseLevel.cryName)}
                        className={styles.breadcrumbLink}
                      >
                        {browseLevel.objectiveName}
                      </button>
                    </>
                  )}
                </>
              )}
            </nav>

            {/* Search toggle icon */}
            {!searchOpen && (
              <button
                type="button"
                className={styles.searchToggleBtn}
                onClick={() => setSearchOpen(true)}
                aria-label="Search outcomes"
                data-testid="rcdo-search-toggle"
                title="Search outcomes by name"
              >
                <span className={styles.searchToggleGlyph} aria-hidden="true">⌕</span>
              </button>
            )}
          </div>

          {/* ── Inline search (expanded on demand) ── */}
          {searchOpen && (
            <div className={styles.searchRow} data-testid="rcdo-search-panel">
              <input
                ref={searchInputRef}
                data-testid="rcdo-search-input"
                type="text"
                placeholder="Search outcomes…"
                aria-label="Search outcomes"
                value={query}
                onChange={(e) => handleQueryChange(e.target.value)}
                onKeyDown={handleSearchKeyDown}
                className={styles.searchInput}
              />
              <button
                type="button"
                className={styles.searchCloseBtn}
                onClick={closeSearch}
                aria-label="Close search"
                data-testid="rcdo-search-close"
              >
                ×
              </button>
            </div>
          )}

          {/* ── Search results (when search is open and has results) ── */}
          {searchOpen && searchResults.length > 0 && (
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
                      {r.rallyCryName} › {r.objectiveName}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {/* ── Browse list (always visible unless search has results) ── */}
          {(!searchOpen || searchResults.length === 0) && (
            <div className={styles.browseList}>
              {/* Root level: rally cries */}
              {browseLevel.type === "root" &&
                tree.map((cry) => (
                  <button
                    type="button"
                    key={cry.id}
                    onClick={() => drillIntoCry(cry)}
                    className={styles.browseItem}
                  >
                    <span className={styles.browseItemChevron}>›</span>
                    {cry.name}
                  </button>
                ))}

              {/* Cry level: objectives */}
              {browseLevel.type === "cry" &&
                currentCry?.objectives.map((obj) => (
                  <button
                    type="button"
                    key={obj.id}
                    onClick={() =>
                      drillIntoObjective(
                        { id: browseLevel.cryId, name: browseLevel.cryName },
                        { id: obj.id, name: obj.name },
                      )
                    }
                    className={styles.browseItem}
                  >
                    <span className={styles.browseItemChevron}>›</span>
                    {obj.name}
                  </button>
                ))}

              {/* Objective level: outcomes (leaf nodes) */}
              {browseLevel.type === "objective" &&
                currentObjective?.outcomes.map((outcome) => (
                  <button
                    type="button"
                    key={outcome.id}
                    onClick={() =>
                      onChange({
                        outcomeId: outcome.id,
                        outcomeName: outcome.name,
                        objectiveId: browseLevel.objectiveId,
                        objectiveName: browseLevel.objectiveName,
                        rallyCryId: browseLevel.cryId,
                        rallyCryName: browseLevel.cryName,
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
      )}
    </div>
  );
};
