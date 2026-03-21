import { describe, it, expect, vi, beforeEach } from "vitest";
import { markPerformance, measurePerformance, reportError, trackMetric, withTiming } from "../utils/observability";

describe("observability utilities", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe("markPerformance", () => {
    it("calls performance.mark with the given name", () => {
      const spy = vi.spyOn(performance, "mark");
      markPerformance("test-mark");
      expect(spy).toHaveBeenCalledWith("test-mark");
    });
  });

  describe("measurePerformance", () => {
    it("returns duration between marks", () => {
      performance.mark("start");
      performance.mark("end");
      const result = measurePerformance("test-measure", "start", "end");
      expect(result).toBeGreaterThanOrEqual(0);
    });

    it("returns null for invalid marks", () => {
      const result = measurePerformance("bad", "nonexistent-start", "nonexistent-end");
      expect(result).toBeNull();
    });
  });

  describe("reportError", () => {
    it("logs error in non-production", () => {
      const spy = vi.spyOn(console, "error").mockImplementation(() => {});
      reportError(new Error("test error"), { context: "unit-test" });
      expect(spy).toHaveBeenCalled();
    });
  });

  describe("trackMetric", () => {
    it("logs metric in non-production", () => {
      const spy = vi.spyOn(console, "debug").mockImplementation(() => {});
      trackMetric("test.metric", 42, { tag: "value" });
      expect(spy).toHaveBeenCalled();
    });
  });

  describe("withTiming", () => {
    it("returns result and duration", async () => {
      const { result, durationMs } = await withTiming("test-op", async () => {
        return "done";
      });
      expect(result).toBe("done");
      expect(durationMs).toBeGreaterThanOrEqual(0);
    });
  });
});
