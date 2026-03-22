import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { CategoryDonut, DONUT_COLORS } from "../components/charts/CategoryDonut.js";

describe("CategoryDonut", () => {
  it("renders an SVG donut with category arcs", () => {
    render(<CategoryDonut data={{ DELIVERY: 3, TECH_DEBT: 1 }} />);

    const svg = screen.getByTestId("category-donut");
    expect(svg).toBeInTheDocument();
    expect(svg).toHaveAttribute("role", "img");
    expect(svg).toHaveAttribute("aria-label", "Category distribution donut chart");
    expect(svg.querySelectorAll("circle")).toHaveLength(2);
  });

  it("uses friendly labels for known category names", () => {
    render(<CategoryDonut data={{ GTM: 1, TECH_DEBT: 1 }} />);

    expect(screen.getByText("GTM")).toBeInTheDocument();
    expect(screen.getByText("Tech Debt")).toBeInTheDocument();
  });

  it("exports the updated enterprise palette", () => {
    expect(DONUT_COLORS).toMatchObject({
      DELIVERY: "#2563eb",
      OPERATIONS: "#0f766e",
      CUSTOMER: "#7c3aed",
      PEOPLE: "#059669",
      LEARNING: "#d97706",
      GTM: "#dc2626",
      TECH_DEBT: "#94a3b8",
    });
  });
});
