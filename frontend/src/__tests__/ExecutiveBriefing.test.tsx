import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ExecutiveBriefing } from "../components/Phase5/ExecutiveBriefing.js";

const mockFetchBriefing = vi.fn();
const mockUseExecutiveDashboard = vi.fn();
let mockExecutiveDashboard = true;

vi.mock("../hooks/useExecutiveDashboard.js", () => ({ useExecutiveDashboard: () => mockUseExecutiveDashboard() }));
vi.mock("../context/FeatureFlagContext.js", () => ({ useFeatureFlags: () => ({ executiveDashboard: mockExecutiveDashboard }) }));

describe("ExecutiveBriefing", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockExecutiveDashboard = true;
    mockUseExecutiveDashboard.mockReturnValue({ briefing: null, briefingStatus: "idle", errorBriefing: null, fetchBriefing: mockFetchBriefing });
  });

  it("renders briefing content and refreshes", () => {
    mockUseExecutiveDashboard.mockReturnValue({ briefing: { status: "ok", headline: "Healthy overall", insights: [{ title: "Watch coverage", detail: "One bucket is lagging", severity: "WARNING" }] }, briefingStatus: "ok", errorBriefing: null, fetchBriefing: mockFetchBriefing });
    render(<ExecutiveBriefing weekStart="2026-03-16" />);
    expect(screen.getByTestId("executive-briefing-content")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("executive-briefing-refresh"));
    expect(mockFetchBriefing).toHaveBeenCalledWith("2026-03-16");
  });

  it("shows a concrete error without also showing the generic unavailable state", () => {
    mockUseExecutiveDashboard.mockReturnValue({ briefing: null, briefingStatus: "unavailable", errorBriefing: "Request failed (500)", fetchBriefing: mockFetchBriefing });
    render(<ExecutiveBriefing weekStart="2026-03-16" />);
    expect(screen.getByTestId("executive-briefing-error")).toHaveTextContent("Request failed (500)");
    expect(screen.queryByTestId("executive-briefing-unavailable")).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("executive-briefing-refresh"));
    expect(mockFetchBriefing).toHaveBeenCalledWith("2026-03-16");
  });
});
