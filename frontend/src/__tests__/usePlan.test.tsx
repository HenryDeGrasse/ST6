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

  it("auto-retries lockPlan with server's currentVersion on 409 and succeeds", async () => {
    const lockedPlan = { id: "plan-1", version: 3, state: "LOCKED" };
    mockClient.POST
      .mockResolvedValueOnce({
        data: undefined,
        error: { error: { code: "CONFLICT", message: "modified", details: [{ currentVersion: 3 }] } },
        response: { status: 409 },
      })
      .mockResolvedValueOnce({
        data: lockedPlan,
        response: { status: 200 },
      });

    const { result } = renderHook(() => usePlan());

    let returned: unknown;
    await act(async () => {
      returned = await result.current.lockPlan("plan-1", 2);
    });

    expect(returned).toEqual(lockedPlan);
    expect(result.current.plan).toEqual(lockedPlan);
    expect(result.current.error).toBeNull();
    // Retry call must use the server's currentVersion (3)
    expect(mockClient.POST).toHaveBeenCalledTimes(2);
    expect(mockClient.POST).toHaveBeenNthCalledWith(2, "/plans/{planId}/lock", {
      params: {
        path: { planId: "plan-1" },
        header: expect.objectContaining({ "If-Match": 3 }),
      },
    });
  });

  it("shows error when lockPlan auto-retry also gets 409", async () => {
    const conflict409 = {
      data: undefined,
      error: { error: { code: "CONFLICT", message: "modified", details: [{ currentVersion: 3 }] } },
      response: { status: 409 },
    };
    mockClient.POST.mockResolvedValue(conflict409);

    const { result } = renderHook(() => usePlan());

    await act(async () => {
      await result.current.lockPlan("plan-1", 2);
    });

    expect(result.current.error).toBe("Conflict: the plan was modified. Please try again.");
    expect(mockClient.POST).toHaveBeenCalledTimes(2);
  });

  it("shows error when 409 has no currentVersion in details", async () => {
    mockClient.POST.mockResolvedValue({
      data: undefined,
      error: { error: { code: "CONFLICT", message: "modified" } },
      response: { status: 409 },
    });

    const { result } = renderHook(() => usePlan());

    await act(async () => {
      await result.current.lockPlan("plan-1", 2);
    });

    expect(result.current.error).toBe("Conflict: the plan was modified. Please try again.");
    // No retry when currentVersion is missing
    expect(mockClient.POST).toHaveBeenCalledTimes(1);
  });

  it("auto-retries startReconciliation with server's currentVersion on 409 and succeeds", async () => {
    const reconcilingPlan = { id: "plan-1", version: 4, state: "RECONCILING" };
    mockClient.POST
      .mockResolvedValueOnce({
        data: undefined,
        error: { error: { code: "CONFLICT", message: "modified", details: [{ currentVersion: 4 }] } },
        response: { status: 409 },
      })
      .mockResolvedValueOnce({
        data: reconcilingPlan,
        response: { status: 200 },
      });

    const { result } = renderHook(() => usePlan());

    let returned: unknown;
    await act(async () => {
      returned = await result.current.startReconciliation("plan-1", 3);
    });

    expect(returned).toEqual(reconcilingPlan);
    expect(result.current.plan).toEqual(reconcilingPlan);
    expect(result.current.error).toBeNull();
    expect(mockClient.POST).toHaveBeenCalledTimes(2);
    expect(mockClient.POST).toHaveBeenNthCalledWith(2, "/plans/{planId}/start-reconciliation", {
      params: {
        path: { planId: "plan-1" },
        header: expect.objectContaining({ "If-Match": 4 }),
      },
    });
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
