import React, { useState, useEffect, useCallback } from "react";
import type { RcdoCry, RcdoSearchResult } from "@weekly-commitments/contracts";

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
    <div data-testid="rcdo-picker">
      {value && (
        <div data-testid="rcdo-current" style={{ marginBottom: "0.5rem", fontSize: "0.85rem", color: "#555" }}>
          Linked: {findOutcomeLabel()}
          {!disabled && (
            <button
              onClick={() => onChange(null)}
              style={{ marginLeft: "0.5rem", fontSize: "0.8rem" }}
              data-testid="rcdo-clear"
            >
              ✕
            </button>
          )}
        </div>
      )}
      {!disabled && (
        <>
          <div style={{ marginBottom: "0.5rem" }}>
            <button
              data-testid="rcdo-mode-search"
              onClick={() => setMode("search")}
              style={{ fontWeight: mode === "search" ? 700 : 400, marginRight: "0.5rem" }}
            >
              Search
            </button>
            <button
              data-testid="rcdo-mode-browse"
              onClick={() => setMode("browse")}
              style={{ fontWeight: mode === "browse" ? 700 : 400 }}
            >
              Browse
            </button>
          </div>
          {mode === "search" && (
            <div>
              <input
                data-testid="rcdo-search-input"
                type="text"
                placeholder="Search outcomes…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                style={{ width: "100%", padding: "0.25rem" }}
              />
              {searchResults.length > 0 && (
                <ul data-testid="rcdo-search-results" style={{ listStyle: "none", padding: 0, margin: "0.25rem 0" }}>
                  {searchResults.map((r) => (
                    <li key={r.id} style={{ padding: "0.25rem 0", cursor: "pointer" }}>
                      <button
                        onClick={() => handleSearchSelect(r)}
                        style={{ textAlign: "left", background: "none", border: "none", cursor: "pointer", width: "100%" }}
                        data-testid={`rcdo-result-${r.id}`}
                      >
                        <strong>{r.name}</strong>
                        <br />
                        <span style={{ fontSize: "0.8rem", color: "#666" }}>
                          {r.rallyCryName} → {r.objectiveName}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
          {mode === "browse" && (
            <div data-testid="rcdo-tree-browser" style={{ fontSize: "0.9rem" }}>
              {tree.map((cry) => (
                <div key={cry.id} style={{ marginBottom: "0.25rem" }}>
                  <button onClick={() => toggleCry(cry.id)} style={{ fontWeight: 600, cursor: "pointer", background: "none", border: "none" }}>
                    {expandedCries.has(cry.id) ? "▾" : "▸"} {cry.name}
                  </button>
                  {expandedCries.has(cry.id) && (
                    <div style={{ marginLeft: "1rem" }}>
                      {cry.objectives.map((obj) => (
                        <div key={obj.id}>
                          <button onClick={() => toggleObjective(obj.id)} style={{ cursor: "pointer", background: "none", border: "none" }}>
                            {expandedObjectives.has(obj.id) ? "▾" : "▸"} {obj.name}
                          </button>
                          {expandedObjectives.has(obj.id) && (
                            <div style={{ marginLeft: "1rem" }}>
                              {obj.outcomes.map((outcome) => (
                                <button
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
                                  style={{
                                    display: "block",
                                    background: value === outcome.id ? "#e0e7ff" : "none",
                                    border: "none",
                                    cursor: "pointer",
                                    padding: "0.15rem 0.5rem",
                                    width: "100%",
                                    textAlign: "left",
                                  }}
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
