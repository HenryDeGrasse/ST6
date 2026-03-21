import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { CarryForwardHeatmap } from "../components/StrategicIntelligence/CarryForwardHeatmap.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { CarryForwardHeatmap as CarryForwardHeatmapData } from "@weekly-commitments/contracts";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function renderWithFlags(
  ui: React.ReactElement,
  flags: { strategicIntelligence: boolean } = { strategicIntelligence: true },
) {
  return render(<FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>);
}

function makeData(overrides: Partial<CarryForwardHeatmapData> = {}): CarryForwardHeatmapData {
  return {
    weekStarts: ["2026-01-05", "2026-01-12", "2026-01-19"],
    users: [
      {
        userId: "user-1",
        displayName: "Alice Smith",
        cells: [
          { weekStart: "2026-01-05", carriedCount: 0 },
          { weekStart: "2026-01-12", carriedCount: 1 },
          { weekStart: "2026-01-19", carriedCount: 4 },
        ],
      },
      {
        userId: "user-2",
        displayName: "Bob Jones",
        cells: [
          { weekStart: "2026-01-05", carriedCount: 2 },
          { weekStart: "2026-01-12", carriedCount: 0 },
          { weekStart: "2026-01-19", carriedCount: 3 },
        ],
      },
    ],
    ...overrides,
  };
}

const defaultProps = {
  data: null as CarryForwardHeatmapData | null,
  loading: false,
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("CarryForwardHeatmap", () => {
  it("renders nothing when the strategicIntelligence flag is disabled", () => {
    const { container } = renderWithFlags(
      <CarryForwardHeatmap {...defaultProps} />,
      { strategicIntelligence: false },
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders the heatmap panel when the flag is enabled", () => {
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} />);

    expect(screen.getByTestId("carry-forward-heatmap")).toBeInTheDocument();
    expect(screen.getByText("Carry-Forward Heatmap")).toBeInTheDocument();
  });

  it("shows loading state when loading=true", () => {
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} loading={true} />);

    expect(screen.getByTestId("carry-forward-loading")).toBeInTheDocument();
    expect(screen.getByText("Loading heatmap data…")).toBeInTheDocument();
  });

  it("shows empty state when data is null and not loading", () => {
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={null} loading={false} />);

    expect(screen.getByTestId("carry-forward-empty")).toBeInTheDocument();
    expect(screen.getByText("No heatmap data available.")).toBeInTheDocument();
  });

  it("handles empty user list", () => {
    const emptyData = makeData({ users: [] });
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={emptyData} />);

    expect(screen.getByTestId("carry-forward-no-users")).toBeInTheDocument();
    expect(screen.getByText("No team members found.")).toBeInTheDocument();
  });

  it("renders a row for each user", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    expect(screen.getByTestId("heatmap-row-user-1")).toBeInTheDocument();
    expect(screen.getByTestId("heatmap-row-user-2")).toBeInTheDocument();
  });

  it("displays user display names in row headers", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    expect(screen.getByText("Bob Jones")).toBeInTheDocument();
  });

  it("renders week column headers from weekStarts", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    // Displays MM-DD portion of ISO dates
    expect(screen.getByText("01-05")).toBeInTheDocument();
    expect(screen.getByText("01-12")).toBeInTheDocument();
    expect(screen.getByText("01-19")).toBeInTheDocument();
  });

  it("renders cells with correct data-testid values", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    expect(screen.getByTestId("heatmap-cell-user-1-2026-01-05")).toBeInTheDocument();
    expect(screen.getByTestId("heatmap-cell-user-1-2026-01-12")).toBeInTheDocument();
    expect(screen.getByTestId("heatmap-cell-user-2-2026-01-05")).toBeInTheDocument();
  });

  it("displays the carried count value in each cell", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    // Alice row: 0, 1, 4
    expect(screen.getByTestId("heatmap-cell-user-1-2026-01-05")).toHaveTextContent("0");
    expect(screen.getByTestId("heatmap-cell-user-1-2026-01-12")).toHaveTextContent("1");
    expect(screen.getByTestId("heatmap-cell-user-1-2026-01-19")).toHaveTextContent("4");

    // Bob row: 2, 0, 3
    expect(screen.getByTestId("heatmap-cell-user-2-2026-01-05")).toHaveTextContent("2");
    expect(screen.getByTestId("heatmap-cell-user-2-2026-01-12")).toHaveTextContent("0");
    expect(screen.getByTestId("heatmap-cell-user-2-2026-01-19")).toHaveTextContent("3");
  });

  it("applies cellNeutral class for count = 0", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    const cell = screen.getByTestId("heatmap-cell-user-1-2026-01-05");
    // count=0 → neutral (green) class
    expect(cell.className).toMatch(/cellNeutral/);
  });

  it("applies cellWarn class for count between 1 and 2 (inclusive)", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    // count=1 → warn (yellow) class
    const cell1 = screen.getByTestId("heatmap-cell-user-1-2026-01-12");
    expect(cell1.className).toMatch(/cellWarn/);

    // count=2 → warn (yellow) class
    const cell2 = screen.getByTestId("heatmap-cell-user-2-2026-01-05");
    expect(cell2.className).toMatch(/cellWarn/);
  });

  it("applies cellHigh class for count >= 3", () => {
    const data = makeData();
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    // count=4 → high (red) class
    const cell1 = screen.getByTestId("heatmap-cell-user-1-2026-01-19");
    expect(cell1.className).toMatch(/cellHigh/);

    // count=3 → high (red) class
    const cell2 = screen.getByTestId("heatmap-cell-user-2-2026-01-19");
    expect(cell2.className).toMatch(/cellHigh/);
  });

  it("defaults missing cells to 0 and cellNeutral class", () => {
    // User has no cell entry for a week listed in weekStarts
    const data = makeData({
      weekStarts: ["2026-01-05", "2026-01-26"],
      users: [
        {
          userId: "user-3",
          displayName: "Carol White",
          // Only one of two weeks present in cells
          cells: [{ weekStart: "2026-01-05", carriedCount: 0 }],
        },
      ],
    });
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    const missingCell = screen.getByTestId("heatmap-cell-user-3-2026-01-26");
    expect(missingCell).toHaveTextContent("0");
    expect(missingCell.className).toMatch(/cellNeutral/);
  });

  it("provides correct aria-labels on cells", () => {
    const data = makeData({
      weekStarts: ["2026-01-05"],
      users: [
        {
          userId: "user-1",
          displayName: "Alice Smith",
          cells: [{ weekStart: "2026-01-05", carriedCount: 2 }],
        },
      ],
    });
    renderWithFlags(<CarryForwardHeatmap {...defaultProps} data={data} />);

    const cell = screen.getByTestId("heatmap-cell-user-1-2026-01-05");
    expect(cell).toHaveAttribute(
      "aria-label",
      "Alice Smith, week 2026-01-05: 2 carried forward",
    );
  });
});
