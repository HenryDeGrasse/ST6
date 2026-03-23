import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { RcdoPicker } from "../components/RcdoPicker.js";
import type { RcdoCry, RcdoSearchResult } from "@weekly-commitments/contracts";

// ─── Fixtures ──────────────────────────────────────────────────────────────

const TREE: RcdoCry[] = [
  {
    id: "cry-1",
    name: "Scale to $500M ARR",
    objectives: [
      {
        id: "obj-1",
        name: "Accelerate enterprise pipeline",
        rallyCryId: "cry-1",
        outcomes: [
          { id: "out-1", name: "Close 10 enterprise deals in Q1", objectiveId: "obj-1" },
          { id: "out-2", name: "Launch enterprise demo environment", objectiveId: "obj-1" },
        ],
      },
      {
        id: "obj-2",
        name: "Expand into new verticals",
        rallyCryId: "cry-1",
        outcomes: [
          { id: "out-3", name: "Sign 3 healthcare pilot customers", objectiveId: "obj-2" },
        ],
      },
    ],
  },
  {
    id: "cry-2",
    name: "World-class engineering culture",
    objectives: [
      {
        id: "obj-3",
        name: "Ship reliable software faster",
        rallyCryId: "cry-2",
        outcomes: [
          { id: "out-4", name: "Achieve 99.9% API uptime", objectiveId: "obj-3" },
          { id: "out-5", name: "Increase unit test coverage to 85%", objectiveId: "obj-3" },
        ],
      },
    ],
  },
];

const SEARCH_RESULTS: RcdoSearchResult[] = [
  {
    id: "out-1",
    name: "Close 10 enterprise deals in Q1",
    objectiveId: "obj-1",
    objectiveName: "Accelerate enterprise pipeline",
    rallyCryId: "cry-1",
    rallyCryName: "Scale to $500M ARR",
  },
];

describe("RcdoPicker", () => {
  let onChange: ReturnType<typeof vi.fn>;
  let onSearch: ReturnType<typeof vi.fn>;
  let onClearSearch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onChange = vi.fn();
    onSearch = vi.fn();
    onClearSearch = vi.fn();
    vi.useFakeTimers();
  });

  function renderPicker(overrides: Partial<React.ComponentProps<typeof RcdoPicker>> = {}) {
    return render(
      <RcdoPicker
        value={null}
        onChange={onChange}
        tree={TREE}
        searchResults={[]}
        onSearch={onSearch}
        onClearSearch={onClearSearch}
        {...overrides}
      />,
    );
  }

  // ─── Clear button ────────────────────────────────────────────────────

  it("renders × button to clear selection, not the word 'Close'", () => {
    renderPicker({ value: "out-1" });
    const clearBtn = screen.getByTestId("rcdo-clear");
    expect(clearBtn.textContent).toBe("×");
    expect(clearBtn.textContent).not.toBe("Close");
  });

  it("clears selection when × is clicked", () => {
    renderPicker({ value: "out-1" });
    fireEvent.click(screen.getByTestId("rcdo-clear"));
    expect(onChange).toHaveBeenCalledWith(null);
  });

  // ─── Browse is default ───────────────────────────────────────────────

  it("shows browse panel by default without any interaction", () => {
    renderPicker();
    // The browse tree should be immediately visible
    expect(screen.getByTestId("rcdo-tree-browser")).toBeInTheDocument();
    // Top-level rally cries should show immediately
    expect(screen.getByText("Scale to $500M ARR")).toBeInTheDocument();
    expect(screen.getByText("World-class engineering culture")).toBeInTheDocument();
  });

  it("does not show search input by default", () => {
    renderPicker();
    expect(screen.queryByTestId("rcdo-search-input")).not.toBeInTheDocument();
  });

  it("shows search toggle icon button in the panel header", () => {
    renderPicker();
    const btn = screen.getByTestId("rcdo-search-toggle");
    expect(btn).toBeInTheDocument();
    expect(btn).toHaveTextContent("⌕");
  });

  // ─── Search toggle ───────────────────────────────────────────────────

  it("clicking search toggle reveals the search input", () => {
    renderPicker();
    fireEvent.click(screen.getByTestId("rcdo-search-toggle"));
    expect(screen.getByTestId("rcdo-search-input")).toBeInTheDocument();
    // Toggle button should be hidden once search is open
    expect(screen.queryByTestId("rcdo-search-toggle")).not.toBeInTheDocument();
  });

  it("pressing Escape closes search and returns to browse", () => {
    renderPicker();
    fireEvent.click(screen.getByTestId("rcdo-search-toggle"));
    const input = screen.getByTestId("rcdo-search-input");
    fireEvent.keyDown(input, { key: "Escape" });
    expect(screen.queryByTestId("rcdo-search-input")).not.toBeInTheDocument();
    expect(screen.getByTestId("rcdo-search-toggle")).toBeInTheDocument();
  });

  it("clicking × in search row closes search", () => {
    renderPicker();
    fireEvent.click(screen.getByTestId("rcdo-search-toggle"));
    fireEvent.click(screen.getByTestId("rcdo-search-close"));
    expect(screen.queryByTestId("rcdo-search-input")).not.toBeInTheDocument();
    expect(screen.getByTestId("rcdo-search-toggle")).toBeInTheDocument();
  });

  // ─── No infinite loop ────────────────────────────────────────────────

  it("does not call onSearch or onClearSearch on mount", () => {
    renderPicker();
    act(() => { vi.advanceTimersByTime(500); });
    expect(onSearch).not.toHaveBeenCalled();
    expect(onClearSearch).not.toHaveBeenCalled();
  });

  it("does not infinite-loop when parent re-creates onSearch reference", () => {
    const { rerender } = render(
      <RcdoPicker value={null} onChange={onChange} tree={TREE}
        searchResults={[]} onSearch={vi.fn()} onClearSearch={vi.fn()} />,
    );
    rerender(
      <RcdoPicker value={null} onChange={onChange} tree={TREE}
        searchResults={[]} onSearch={vi.fn()} onClearSearch={vi.fn()} />,
    );
    act(() => { vi.advanceTimersByTime(500); });
    expect(screen.getByTestId("rcdo-picker")).toBeInTheDocument();
  });

  // ─── Breadcrumb browse navigation ───────────────────────────────────

  it("clicking a rally cry drills in and shows its objectives", () => {
    renderPicker();
    fireEvent.click(screen.getByText("Scale to $500M ARR"));

    expect(screen.getByText("Accelerate enterprise pipeline")).toBeInTheDocument();
    expect(screen.getByText("Expand into new verticals")).toBeInTheDocument();
    expect(screen.queryByText("World-class engineering culture")).not.toBeInTheDocument();
    expect(screen.getByTestId("rcdo-breadcrumb")).toHaveTextContent("Scale to $500M ARR");
  });

  it("clicking an objective shows its outcomes and full breadcrumb", () => {
    renderPicker();
    fireEvent.click(screen.getByText("Scale to $500M ARR"));
    fireEvent.click(screen.getByText("Accelerate enterprise pipeline"));

    expect(screen.getByText("Close 10 enterprise deals in Q1")).toBeInTheDocument();
    expect(screen.getByText("Launch enterprise demo environment")).toBeInTheDocument();
    expect(screen.queryByText("Expand into new verticals")).not.toBeInTheDocument();

    const bc = screen.getByTestId("rcdo-breadcrumb");
    expect(bc).toHaveTextContent("Scale to $500M ARR");
    expect(bc).toHaveTextContent("Accelerate enterprise pipeline");
  });

  it("clicking objective in breadcrumb collapses back to objectives level", () => {
    renderPicker();
    fireEvent.click(screen.getByText("Scale to $500M ARR"));
    fireEvent.click(screen.getByText("Accelerate enterprise pipeline"));
    fireEvent.click(screen.getByTestId("rcdo-breadcrumb-objective"));

    expect(screen.getByText("Accelerate enterprise pipeline")).toBeInTheDocument();
    expect(screen.getByText("Expand into new verticals")).toBeInTheDocument();
    expect(screen.queryByText("Close 10 enterprise deals in Q1")).not.toBeInTheDocument();
  });

  it("clicking rally cry in breadcrumb returns to objectives level", () => {
    renderPicker();
    fireEvent.click(screen.getByText("Scale to $500M ARR"));
    fireEvent.click(screen.getByText("Accelerate enterprise pipeline"));
    fireEvent.click(screen.getByTestId("rcdo-breadcrumb-cry"));

    expect(screen.getByText("Accelerate enterprise pipeline")).toBeInTheDocument();
    expect(screen.queryByText("Close 10 enterprise deals in Q1")).not.toBeInTheDocument();
  });

  it("clicking 'All' in breadcrumb returns to root", () => {
    renderPicker();
    fireEvent.click(screen.getByText("Scale to $500M ARR"));
    fireEvent.click(screen.getByTestId("rcdo-breadcrumb-root"));

    expect(screen.getByText("Scale to $500M ARR")).toBeInTheDocument();
    expect(screen.getByText("World-class engineering culture")).toBeInTheDocument();
  });

  it("selecting an outcome calls onChange with full RCDO path", () => {
    renderPicker();
    fireEvent.click(screen.getByText("Scale to $500M ARR"));
    fireEvent.click(screen.getByText("Accelerate enterprise pipeline"));
    fireEvent.click(screen.getByText("Close 10 enterprise deals in Q1"));

    expect(onChange).toHaveBeenCalledWith({
      outcomeId: "out-1",
      outcomeName: "Close 10 enterprise deals in Q1",
      objectiveId: "obj-1",
      objectiveName: "Accelerate enterprise pipeline",
      rallyCryId: "cry-1",
      rallyCryName: "Scale to $500M ARR",
    });
  });

  // ─── Search (secondary action) ───────────────────────────────────────

  it("debounces search input and calls onSearch", () => {
    renderPicker();
    fireEvent.click(screen.getByTestId("rcdo-search-toggle"));
    const input = screen.getByTestId("rcdo-search-input");
    fireEvent.change(input, { target: { value: "enterprise" } });

    expect(onSearch).not.toHaveBeenCalled();
    act(() => { vi.advanceTimersByTime(350); });
    expect(onSearch).toHaveBeenCalledWith("enterprise");
  });

  it("renders search results and allows selection", () => {
    renderPicker({ searchResults: SEARCH_RESULTS });
    fireEvent.click(screen.getByTestId("rcdo-search-toggle"));
    expect(screen.getByTestId("rcdo-search-results")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("rcdo-result-out-1"));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ outcomeId: "out-1" }));
  });
});
