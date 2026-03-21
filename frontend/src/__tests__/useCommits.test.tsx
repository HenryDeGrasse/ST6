import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useCommits } from "../hooks/useCommits.js";

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

describe("useCommits", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("passes estimatedHours through createCommit request bodies", async () => {
    mockClient.POST.mockResolvedValue({
      data: { id: "commit-1", title: "New task", estimatedHours: 6.5 },
      response: { status: 201 },
    });

    const { result } = renderHook(() => useCommits());

    await act(async () => {
      await result.current.createCommit("plan-1", {
        title: "New task",
        estimatedHours: 6.5,
      });
    });

    expect(mockClient.POST).toHaveBeenCalledWith("/plans/{planId}/commits", {
      params: { path: { planId: "plan-1" } },
      body: expect.objectContaining({
        title: "New task",
        estimatedHours: 6.5,
      }),
    });
  });

  it("passes estimatedHours through updateCommit request bodies", async () => {
    mockClient.PATCH.mockResolvedValue({
      data: { id: "commit-1", title: "Updated task", estimatedHours: 7.5 },
      response: { status: 200 },
    });

    const { result } = renderHook(() => useCommits());

    await act(async () => {
      await result.current.updateCommit("commit-1", 3, {
        estimatedHours: 7.5,
      });
    });

    expect(mockClient.PATCH).toHaveBeenCalledWith("/commits/{commitId}", {
      params: {
        path: { commitId: "commit-1" },
        header: { "If-Match": 3 },
      },
      body: expect.objectContaining({
        estimatedHours: 7.5,
      }),
    });
  });
});
