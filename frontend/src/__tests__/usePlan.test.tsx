import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { usePlan } from "../hooks/usePlan.js";

const mockClient = {
  GET: vi.fn(),
  POST: vi.fn(),
  PATCH: vi.fn(),
  DELETE: vi.fn(),
  use: vi.fn(),
};

vi.mock("../api/ApiContext.js", () => ({
  useApiClient: () => mockClient,
}));

describe("usePlan", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("treats 404 on fetchPlan as no-plan, not an error", async () => {
    mockClient.GET.mockResolvedValue({
      data: undefined,
      error: { error: { code: "NOT_FOUND", message: "Plan not found" } },
      response: { status: 404 },
    });

    const { result } = renderHook(() => usePlan());

    await act(async () => {
      await result.current.fetchPlan("2026-03-02");
    });

    expect(result.current.plan).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it("sends If-Match for startReconciliation", async () => {
    mockClient.POST.mockResolvedValue({
      data: { id: "plan-1", version: 2, state: "RECONCILING" },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlan());

    await act(async () => {
      await result.current.startReconciliation("plan-1", 1);
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/plans/{planId}/start-reconciliation", {
      params: {
        path: { planId: "plan-1" },
        header: expect.objectContaining({ "If-Match": 1 }),
      },
    });
  });

  it("sends If-Match for submitReconciliation", async () => {
    mockClient.POST.mockResolvedValue({
      data: { id: "plan-1", version: 3, state: "RECONCILED" },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlan());

    await act(async () => {
      await result.current.submitReconciliation("plan-1", 2);
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/plans/{planId}/submit-reconciliation", {
      params: {
        path: { planId: "plan-1" },
        header: expect.objectContaining({ "If-Match": 2 }),
      },
    });
  });

  it("sends If-Match for carryForward", async () => {
    mockClient.POST.mockResolvedValue({
      data: { id: "plan-1", version: 4, state: "CARRY_FORWARD" },
      response: { status: 200 },
    });

    const { result } = renderHook(() => usePlan());

    await act(async () => {
      await result.current.carryForward("plan-1", 3, ["commit-1"]);
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/plans/{planId}/carry-forward", {
      params: {
        path: { planId: "plan-1" },
        header: expect.objectContaining({ "If-Match": 3 }),
      },
      body: { commitIds: ["commit-1"] },
    });
  });
});
