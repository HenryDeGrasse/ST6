import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { EffortTypeChart, EFFORT_TYPE_COLORS } from "../components/charts/EffortTypeChart.js";

describe("EffortTypeChart", () => {
  it("renders an SVG donut with effort type arcs", () => {
    render(
      <EffortTypeChart
        data={{ BUILD: 0.4, MAINTAIN: 0.3, COLLABORATE: 0.2, LEARN: 0.1 }}
      />,
    );

    const svg = screen.getByTestId("effort-type-chart");
    expect(svg).toBeInTheDocument();
    expect(svg).toHaveAttribute("role", "img");
    expect(svg).toHaveAttribute("aria-label", "Effort type distribution donut chart");

    // Each entry should produce a circle arc
    const circles = svg.querySelectorAll("circle");
    expect(circles).toHaveLength(4);
  });

  it("renders legend labels for each effort type", () => {
    render(
      <EffortTypeChart
        data={{ BUILD: 1, MAINTAIN: 1, COLLABORATE: 1, LEARN: 1 }}
      />,
    );

    expect(screen.getByText("Build")).toBeInTheDocument();
    expect(screen.getByText("Maintain")).toBeInTheDocument();
    expect(screen.getByText("Collaborate")).toBeInTheDocument();
    expect(screen.getByText("Learn")).toBeInTheDocument();
  });

  it("shows percentages in the legend", () => {
    render(<EffortTypeChart data={{ BUILD: 3, MAINTAIN: 1 }} />);
    // BUILD = 75%, MAINTAIN = 25%
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByText("25%")).toBeInTheDocument();
  });

  it("uses the correct color for BUILD effort type", () => {
    render(<EffortTypeChart data={{ BUILD: 1 }} />);
    const svg = screen.getByTestId("effort-type-chart");
    const circle = svg.querySelector("circle");
    expect(circle).toHaveAttribute("stroke", EFFORT_TYPE_COLORS.BUILD);
  });

  it("returns null when data is empty", () => {
    const { container } = render(<EffortTypeChart data={{}} />);
    expect(container.firstChild).toBeNull();
  });

  it("returns null when all values are zero", () => {
    const { container } = render(<EffortTypeChart data={{ BUILD: 0, MAINTAIN: 0 }} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders with custom size prop", () => {
    render(<EffortTypeChart data={{ BUILD: 1 }} size={80} />);
    const svg = screen.getByTestId("effort-type-chart");
    expect(svg).toHaveAttribute("width", "80");
    expect(svg).toHaveAttribute("height", "80");
  });

  it("handles unknown effort types gracefully with fallback color and label", () => {
    render(<EffortTypeChart data={{ CUSTOM_TYPE: 1 }} />);
    const svg = screen.getByTestId("effort-type-chart");
    const circle = svg.querySelector("circle");
    // Should use fallback color for unknown types
    expect(circle).toHaveAttribute("stroke", "#94a3b8");
    // Label should be title-cased
    expect(screen.getByText("Custom_type")).toBeInTheDocument();
  });

  it("exports EFFORT_TYPE_COLORS palette with all four effort types", () => {
    expect(EFFORT_TYPE_COLORS).toHaveProperty("BUILD");
    expect(EFFORT_TYPE_COLORS).toHaveProperty("MAINTAIN");
    expect(EFFORT_TYPE_COLORS).toHaveProperty("COLLABORATE");
    expect(EFFORT_TYPE_COLORS).toHaveProperty("LEARN");
  });
});
