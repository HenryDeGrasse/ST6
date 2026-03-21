import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { PredictionAlerts } from "../components/StrategicIntelligence/PredictionAlerts.js";
import { FeatureFlagProvider } from "../context/FeatureFlagContext.js";
import type { Prediction } from "@weekly-commitments/contracts";

/* ── Helpers ──────────────────────────────────────────────────────────────── */

function renderWithFlags(
  ui: React.ReactElement,
  flags: { predictions: boolean } = { predictions: true },
) {
  return render(<FeatureFlagProvider flags={flags}>{ui}</FeatureFlagProvider>);
}

function makePrediction(overrides: Partial<Prediction> = {}): Prediction {
  return {
    type: "CARRY_FORWARD_RISK",
    likely: true,
    confidence: "HIGH",
    reason: "This user has carried forward commits 3 weeks in a row.",
    subjectId: "user-1",
    ...overrides,
  };
}

const defaultProps = {
  predictions: [] as Prediction[],
  loading: false,
};

/* ── Tests ────────────────────────────────────────────────────────────────── */

describe("PredictionAlerts", () => {
  it("renders nothing when the predictions flag is disabled", () => {
    const { container } = renderWithFlags(
      <PredictionAlerts {...defaultProps} />,
      { predictions: false },
    );

    expect(container.innerHTML).toBe("");
  });

  it("renders the prediction alerts panel when the flag is enabled", () => {
    renderWithFlags(<PredictionAlerts {...defaultProps} />);

    expect(screen.getByTestId("prediction-alerts")).toBeInTheDocument();
    expect(screen.getByText("Prediction Alerts")).toBeInTheDocument();
  });

  it("shows loading state when loading=true", () => {
    renderWithFlags(<PredictionAlerts {...defaultProps} loading={true} />);

    expect(screen.getByTestId("prediction-alerts-loading")).toBeInTheDocument();
    expect(screen.getByText("Loading predictions…")).toBeInTheDocument();
  });

  it("shows empty state when there are no likely predictions", () => {
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={[]} />);

    expect(screen.getByTestId("prediction-alerts-empty")).toBeInTheDocument();
    expect(screen.getByText("No predictions for this period")).toBeInTheDocument();
  });

  it("shows empty state when all predictions have likely=false", () => {
    const predictions = [
      makePrediction({ likely: false, confidence: "LOW" }),
      makePrediction({ likely: false, confidence: "MEDIUM" }),
    ];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByTestId("prediction-alerts-empty")).toBeInTheDocument();
  });

  it("shows only likely predictions (filters out likely=false)", () => {
    const predictions = [
      makePrediction({ likely: true, type: "CARRY_FORWARD_RISK", subjectId: "user-1" }),
      makePrediction({ likely: false, type: "LOW_PLAN_QUALITY", subjectId: "user-2" }),
      makePrediction({ likely: true, type: "OVERCOMMIT_RISK", subjectId: "user-3" }),
    ];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByTestId("prediction-0")).toBeInTheDocument();
    expect(screen.getByTestId("prediction-1")).toBeInTheDocument();
    expect(screen.queryByTestId("prediction-2")).not.toBeInTheDocument();
  });

  it("renders prediction type as a human-readable label", () => {
    const predictions = [makePrediction({ type: "CARRY_FORWARD_RISK" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByText("Carry Forward Risk")).toBeInTheDocument();
  });

  it("formats multi-word prediction type labels correctly", () => {
    const predictions = [makePrediction({ type: "LOW_PLAN_QUALITY_RISK" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByText("Low Plan Quality Risk")).toBeInTheDocument();
  });

  it("renders confidence badge with HIGH label", () => {
    const predictions = [makePrediction({ confidence: "HIGH" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    const badge = screen.getByTestId("prediction-confidence-0");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent("High");
    expect(badge).toHaveAttribute("aria-label", "Confidence: High");
  });

  it("renders confidence badge with MEDIUM label", () => {
    const predictions = [makePrediction({ confidence: "MEDIUM" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    const badge = screen.getByTestId("prediction-confidence-0");
    expect(badge).toHaveTextContent("Medium");
    expect(badge).toHaveAttribute("aria-label", "Confidence: Medium");
  });

  it("renders confidence badge with LOW label", () => {
    const predictions = [makePrediction({ confidence: "LOW" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    const badge = screen.getByTestId("prediction-confidence-0");
    expect(badge).toHaveTextContent("Low");
    expect(badge).toHaveAttribute("aria-label", "Confidence: Low");
  });

  it("applies HIGH confidence style class", () => {
    const predictions = [makePrediction({ confidence: "HIGH" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    const badge = screen.getByTestId("prediction-confidence-0");
    expect(badge.className).toMatch(/confidenceHigh/);
  });

  it("applies MEDIUM confidence style class", () => {
    const predictions = [makePrediction({ confidence: "MEDIUM" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    const badge = screen.getByTestId("prediction-confidence-0");
    expect(badge.className).toMatch(/confidenceMedium/);
  });

  it("applies LOW confidence style class", () => {
    const predictions = [makePrediction({ confidence: "LOW" })];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    const badge = screen.getByTestId("prediction-confidence-0");
    expect(badge.className).toMatch(/confidenceLow/);
  });

  it("renders prediction reason text", () => {
    const predictions = [
      makePrediction({ reason: "This user has carried forward commits 3 weeks in a row." }),
    ];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(
      screen.getByText("This user has carried forward commits 3 weeks in a row."),
    ).toBeInTheDocument();
  });

  it("renders the Likely badge on each prediction", () => {
    const predictions = [makePrediction()];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByText("Likely")).toBeInTheDocument();
  });

  it("renders multiple likely predictions in order", () => {
    const predictions = [
      makePrediction({
        type: "CARRY_FORWARD_RISK",
        subjectId: "user-1",
        reason: "First reason",
      }),
      makePrediction({
        type: "OVERCOMMIT_RISK",
        subjectId: "user-2",
        reason: "Second reason",
      }),
    ];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByTestId("prediction-0")).toBeInTheDocument();
    expect(screen.getByTestId("prediction-1")).toBeInTheDocument();
    expect(screen.getByText("First reason")).toBeInTheDocument();
    expect(screen.getByText("Second reason")).toBeInTheDocument();
  });

  it("shows count badge when there are likely predictions", () => {
    const predictions = [
      makePrediction({ subjectId: "user-1" }),
      makePrediction({ subjectId: "user-2" }),
    ];
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={predictions} />);

    expect(screen.getByLabelText("2 prediction alerts")).toBeInTheDocument();
  });

  it("does not show count badge when there are no likely predictions", () => {
    renderWithFlags(<PredictionAlerts {...defaultProps} predictions={[]} />);

    expect(screen.queryByLabelText(/prediction alerts/)).not.toBeInTheDocument();
  });
});
