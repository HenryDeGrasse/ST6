import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { FeatureFlagProvider, useFeatureFlags } from "../context/FeatureFlagContext.js";

const FlagDisplay: React.FC = () => {
  const flags = useFeatureFlags();
  return (
    <div>
      <span data-testid="suggest">{String(flags.suggestRcdo)}</span>
      <span data-testid="draft">{String(flags.draftReconciliation)}</span>
      <span data-testid="insights">{String(flags.managerInsights)}</span>
    </div>
  );
};

describe("FeatureFlagContext", () => {
  it("provides correct defaults (suggestRcdo enabled, beta disabled)", () => {
    render(
      <FeatureFlagProvider>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggest").textContent).toBe("true");
    expect(screen.getByTestId("draft").textContent).toBe("false");
    expect(screen.getByTestId("insights").textContent).toBe("false");
  });

  it("merges partial overrides", () => {
    render(
      <FeatureFlagProvider flags={{ draftReconciliation: true }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggest").textContent).toBe("true");
    expect(screen.getByTestId("draft").textContent).toBe("true");
    expect(screen.getByTestId("insights").textContent).toBe("false");
  });

  it("allows disabling MVP feature", () => {
    render(
      <FeatureFlagProvider flags={{ suggestRcdo: false }}>
        <FlagDisplay />
      </FeatureFlagProvider>,
    );
    expect(screen.getByTestId("suggest").textContent).toBe("false");
  });
});
