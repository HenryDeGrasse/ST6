import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { RcdoRollupPanel } from "../components/RcdoRollupPanel.js";
import type { RcdoRollupResponse } from "@weekly-commitments/contracts";

describe("RcdoRollupPanel", () => {
  it("renders nothing when loading", () => {
    const { container } = render(
      <RcdoRollupPanel rollup={null} loading={true} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders empty state when no items", () => {
    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [],
      nonStrategicCount: 0,
    };
    render(<RcdoRollupPanel rollup={rollup} loading={false} />);
    expect(screen.getByTestId("rollup-empty")).toBeInTheDocument();
  });

  it("renders non-strategic count", () => {
    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [],
      nonStrategicCount: 3,
    };
    render(<RcdoRollupPanel rollup={rollup} loading={false} />);
    expect(screen.getByTestId("non-strategic-count")).toBeInTheDocument();
    expect(screen.getByText(/3 non-strategic/)).toBeInTheDocument();
  });

  it("renders rollup items in a table", () => {
    const rollup: RcdoRollupResponse = {
      weekStart: "2026-03-09",
      items: [
        {
          outcomeId: "oc-1",
          outcomeName: "Revenue Growth",
          objectiveId: "obj-1",
          objectiveName: "Scale ARR",
          rallyCryId: "rc-1",
          rallyCryName: "Win Market",
          commitCount: 5,
          kingCount: 1,
          queenCount: 2,
          rookCount: 1,
          bishopCount: 0,
          knightCount: 0,
          pawnCount: 1,
        },
      ],
      nonStrategicCount: 0,
    };
    render(<RcdoRollupPanel rollup={rollup} loading={false} />);
    expect(screen.getByTestId("rollup-table")).toBeInTheDocument();
    expect(screen.getByTestId("rollup-row-oc-1")).toBeInTheDocument();
    expect(screen.getByText("Win Market")).toBeInTheDocument();
    expect(screen.getByText("Scale ARR")).toBeInTheDocument();
    expect(screen.getByText("Revenue Growth")).toBeInTheDocument();
  });
});
