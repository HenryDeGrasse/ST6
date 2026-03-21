import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  FEATURE_FLAGS_STORAGE_KEY,
  FeatureFlagProvider,
  useFeatureFlags,
} from "../context/FeatureFlagContext.js";

const FlagDisplay: React.FC = () => {
  const flags = useFeatureFlags();
  return (
    <div>
      <span data-testid="suggest">{String(flags.suggestRcdo)}</span>
      <span data-testid="draft">{String(flags.draftReconciliation)}</span>
      <span data-testid="insights">{String(flags.managerInsights)}</span>
      <span data-testid="trends">{String(flags.icTrends)}</span>
      <span data-testid="quality">{String(flags.planQualityNudge)}</span>
      <span data-testid="startMyWeek">{String(flags.startMyWeek)}</span>
      <span data-testid="suggestNextWork">{String(flags.suggestNextWork)}</span>
      <span data-testid="dailyCheckIn">{String(flags.dailyCheckIn)}</span>
      <span data-testid="capacityTracking">{String(flags.capacityTracking)}</span>
      <span data-testid="estimationCoaching">{String(flags.estimationCoaching)}</span>
      <span data-testid="strategicIntelligence">{String(flags.strategicIntelligence)}</span>
      <span data-testid="predictions">{String(flags.predictions)}</span>
      <span data-testid="outcomeUrgency">{String(flags.outcomeUrgency)}</span>
      <span data-testid="strategicSlack">{String(flags.strategicSlack)}</span>
    </div>
  );
};

describe("FeatureFlagContext", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it("provides correct defaults (MVP features enabled, beta AI features disabled)", () => {
    render(
      <FeatureFlagProvider>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggest").textContent).toBe("true");
    expect(screen.getByTestId("draft").textContent).toBe("false");
    expect(screen.getByTestId("insights").textContent).toBe("false");
    expect(screen.getByTestId("trends").textContent).toBe("true");
    expect(screen.getByTestId("quality").textContent).toBe("false");
    expect(screen.getByTestId("startMyWeek").textContent).toBe("false");
    expect(screen.getByTestId("suggestNextWork").textContent).toBe("false");
    expect(screen.getByTestId("dailyCheckIn").textContent).toBe("false");
    expect(screen.getByTestId("capacityTracking").textContent).toBe("false");
    expect(screen.getByTestId("estimationCoaching").textContent).toBe("false");
    expect(screen.getByTestId("strategicIntelligence").textContent).toBe("false");
    expect(screen.getByTestId("predictions").textContent).toBe("false");
    expect(screen.getByTestId("outcomeUrgency").textContent).toBe("false");
    expect(screen.getByTestId("strategicSlack").textContent).toBe("false");
  });

  it("loads persisted overrides from localStorage", () => {
    window.localStorage.setItem(
      FEATURE_FLAGS_STORAGE_KEY,
      JSON.stringify({
        suggestRcdo: false,
        draftReconciliation: true,
        dailyCheckIn: true,
        capacityTracking: true,
        strategicIntelligence: true,
        predictions: true,
        outcomeUrgency: true,
        strategicSlack: true,
      }),
    );

    render(
      <FeatureFlagProvider>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );

    expect(screen.getByTestId("suggest").textContent).toBe("false");
    expect(screen.getByTestId("draft").textContent).toBe("true");
    expect(screen.getByTestId("dailyCheckIn").textContent).toBe("true");
    expect(screen.getByTestId("capacityTracking").textContent).toBe("true");
    expect(screen.getByTestId("estimationCoaching").textContent).toBe("false");
    expect(screen.getByTestId("strategicIntelligence").textContent).toBe("true");
    expect(screen.getByTestId("predictions").textContent).toBe("true");
    expect(screen.getByTestId("outcomeUrgency").textContent).toBe("true");
    expect(screen.getByTestId("strategicSlack").textContent).toBe("true");
    expect(screen.getByTestId("trends").textContent).toBe("true");
  });

  it("ignores malformed persisted values", () => {
    window.localStorage.setItem(
      FEATURE_FLAGS_STORAGE_KEY,
      JSON.stringify({
        suggestRcdo: "nope",
        draftReconciliation: true,
      }),
    );

    render(
      <FeatureFlagProvider>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );

    expect(screen.getByTestId("suggest").textContent).toBe("true");
    expect(screen.getByTestId("draft").textContent).toBe("true");
  });

  it("merges partial overrides", () => {
    render(
      <FeatureFlagProvider flags={{ draftReconciliation: true, planQualityNudge: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggest").textContent).toBe("true");
    expect(screen.getByTestId("draft").textContent).toBe("true");
    expect(screen.getByTestId("insights").textContent).toBe("false");
    expect(screen.getByTestId("trends").textContent).toBe("true");
    expect(screen.getByTestId("quality").textContent).toBe("true");
  });

  it("allows explicit props to override persisted values", () => {
    window.localStorage.setItem(
      FEATURE_FLAGS_STORAGE_KEY,
      JSON.stringify({ suggestRcdo: false, draftReconciliation: false }),
    );

    render(
      <FeatureFlagProvider flags={{ suggestRcdo: true, draftReconciliation: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );

    expect(screen.getByTestId("suggest").textContent).toBe("true");
    expect(screen.getByTestId("draft").textContent).toBe("true");
  });

  it("allows disabling MVP feature", () => {
    render(
      <FeatureFlagProvider flags={{ suggestRcdo: false }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggest").textContent).toBe("false");
  });

  it("enables suggestNextWork when overridden", () => {
    render(
      <FeatureFlagProvider flags={{ suggestNextWork: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggestNextWork").textContent).toBe("true");
  });

  it("enables dailyCheckIn when overridden", () => {
    render(
      <FeatureFlagProvider flags={{ dailyCheckIn: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("dailyCheckIn").textContent).toBe("true");
  });

  it("enables capacityTracking and estimationCoaching when overridden", () => {
    render(
      <FeatureFlagProvider flags={{ capacityTracking: true, estimationCoaching: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("capacityTracking").textContent).toBe("true");
    expect(screen.getByTestId("estimationCoaching").textContent).toBe("true");
  });

  it("enables strategicIntelligence and predictions when overridden", () => {
    render(
      <FeatureFlagProvider flags={{ strategicIntelligence: true, predictions: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("strategicIntelligence").textContent).toBe("true");
    expect(screen.getByTestId("predictions").textContent).toBe("true");
  });

  it("enables outcomeUrgency and strategicSlack when overridden", () => {
    render(
      <FeatureFlagProvider flags={{ outcomeUrgency: true, strategicSlack: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("outcomeUrgency").textContent).toBe("true");
    expect(screen.getByTestId("strategicSlack").textContent).toBe("true");
  });
});
