import { describe, it, expect } from "vitest";
import {
  getWeekStart,
  getNextWeekStart,
  getPrevWeekStart,
  formatWeekLabel,
  isCurrentWeek,
  isPastWeek,
  isFutureWeek,
  isCreateAllowedForWeek,
  isValidMonday,
  formatDate,
  parseDate,
} from "../utils/week.js";

describe("week utilities", () => {
  describe("getWeekStart", () => {
    it("returns Monday for a Wednesday", () => {
      // 2026-03-11 is a Wednesday
      const wed = new Date(2026, 2, 11, 12, 0, 0);
      expect(getWeekStart(wed)).toBe("2026-03-09");
    });

    it("returns Monday for a Monday", () => {
      const mon = new Date(2026, 2, 9, 12, 0, 0);
      expect(getWeekStart(mon)).toBe("2026-03-09");
    });

    it("returns Monday for a Sunday", () => {
      // 2026-03-15 is a Sunday
      const sun = new Date(2026, 2, 15, 12, 0, 0);
      expect(getWeekStart(sun)).toBe("2026-03-09");
    });

    it("returns Monday for a Saturday", () => {
      // 2026-03-14 is a Saturday
      const sat = new Date(2026, 2, 14, 12, 0, 0);
      expect(getWeekStart(sat)).toBe("2026-03-09");
    });
  });

  describe("getNextWeekStart", () => {
    it("returns the next Monday", () => {
      expect(getNextWeekStart("2026-03-09")).toBe("2026-03-16");
    });

    it("handles month boundaries", () => {
      expect(getNextWeekStart("2026-03-30")).toBe("2026-04-06");
    });
  });

  describe("getPrevWeekStart", () => {
    it("returns the previous Monday", () => {
      expect(getPrevWeekStart("2026-03-09")).toBe("2026-03-02");
    });
  });

  describe("formatWeekLabel", () => {
    it("formats same-month week", () => {
      expect(formatWeekLabel("2026-03-09")).toBe("Mar 9 – 15, 2026");
    });

    it("formats cross-month week", () => {
      expect(formatWeekLabel("2026-03-30")).toBe("Mar 30 – Apr 5, 2026");
    });
  });

  describe("isCurrentWeek", () => {
    it("returns true for the current week", () => {
      const current = getWeekStart();
      expect(isCurrentWeek(current)).toBe(true);
    });

    it("returns false for a past week", () => {
      expect(isCurrentWeek("2020-01-06")).toBe(false);
    });
  });

  describe("isPastWeek", () => {
    it("returns true for a past week", () => {
      expect(isPastWeek("2020-01-06")).toBe(true);
    });

    it("returns false for the current week", () => {
      const current = getWeekStart();
      expect(isPastWeek(current)).toBe(false);
    });
  });

  describe("isFutureWeek", () => {
    it("returns true for a week far in the future", () => {
      expect(isFutureWeek("2099-01-05")).toBe(true);
    });

    it("returns false for the current week", () => {
      const current = getWeekStart();
      expect(isFutureWeek(current)).toBe(false);
    });

    it("returns false for next week", () => {
      const current = getWeekStart();
      const next = getNextWeekStart(current);
      expect(isFutureWeek(next)).toBe(false);
    });

    it("returns true for two weeks from now", () => {
      const current = getWeekStart();
      const next = getNextWeekStart(current);
      const twoWeeks = getNextWeekStart(next);
      expect(isFutureWeek(twoWeeks)).toBe(true);
    });

    it("returns false for a past week", () => {
      expect(isFutureWeek("2020-01-06")).toBe(false);
    });
  });

  describe("isCreateAllowedForWeek", () => {
    it("returns true for the current week", () => {
      const current = getWeekStart();
      expect(isCreateAllowedForWeek(current)).toBe(true);
    });

    it("returns true for next week", () => {
      const current = getWeekStart();
      const next = getNextWeekStart(current);
      expect(isCreateAllowedForWeek(next)).toBe(true);
    });

    it("returns false for a past week", () => {
      expect(isCreateAllowedForWeek("2020-01-06")).toBe(false);
    });

    it("returns false for two weeks from now", () => {
      const current = getWeekStart();
      const next = getNextWeekStart(current);
      const twoWeeks = getNextWeekStart(next);
      expect(isCreateAllowedForWeek(twoWeeks)).toBe(false);
    });
  });

  describe("isValidMonday", () => {
    it("returns true for a Monday date string", () => {
      expect(isValidMonday("2026-03-09")).toBe(true);
    });

    it("returns false for a non-Monday date string", () => {
      expect(isValidMonday("2026-03-10")).toBe(false);
    });
  });

  describe("formatDate / parseDate", () => {
    it("round-trips correctly", () => {
      const original = "2026-03-09";
      const parsed = parseDate(original);
      expect(formatDate(parsed)).toBe(original);
    });

    it("formats with zero-padded month and day", () => {
      const d = new Date(2026, 0, 5, 12, 0, 0); // Jan 5
      expect(formatDate(d)).toBe("2026-01-05");
    });
  });
});
