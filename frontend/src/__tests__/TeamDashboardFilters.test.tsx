import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { TeamDashboardFiltersPanel } from "../components/TeamDashboardFilters.js";

describe("TeamDashboardFiltersPanel", () => {
  it("renders the outcome ID filter", () => {
    render(
      <TeamDashboardFiltersPanel filters={{}} onFiltersChange={vi.fn()} />,
    );

    expect(screen.getByTestId("filter-outcome-id")).toBeInTheDocument();
  });

  it("applies a valid outcome ID filter", async () => {
    const onFiltersChange = vi.fn();
    render(
      <TeamDashboardFiltersPanel filters={{}} onFiltersChange={onFiltersChange} />,
    );

    await userEvent.type(
      screen.getByTestId("filter-outcome-id"),
      "123e4567-e89b-12d3-a456-426614174000",
    );

    expect(onFiltersChange).toHaveBeenLastCalledWith({
      outcomeId: "123e4567-e89b-12d3-a456-426614174000",
    });
  });

  it("shows validation feedback and does not apply an invalid outcome ID", async () => {
    const onFiltersChange = vi.fn();
    render(
      <TeamDashboardFiltersPanel filters={{}} onFiltersChange={onFiltersChange} />,
    );

    await userEvent.type(screen.getByTestId("filter-outcome-id"), "not-a-uuid");

    expect(onFiltersChange).not.toHaveBeenCalled();
    expect(screen.getByTestId("filter-outcome-id-error")).toBeInTheDocument();
  });
});
