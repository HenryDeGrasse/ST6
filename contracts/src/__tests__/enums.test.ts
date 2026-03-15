import { describe, it, expect } from "vitest";
import {
  PlanState,
  ReviewStatus,
  ChessPriority,
  CompletionStatus,
  LockType,
  CommitCategory,
} from "../enums.js";

describe("PlanState enum", () => {
  it("contains all lifecycle states", () => {
    expect(Object.values(PlanState)).toEqual([
      "DRAFT",
      "LOCKED",
      "RECONCILING",
      "RECONCILED",
      "CARRY_FORWARD",
    ]);
  });
});

describe("ReviewStatus enum", () => {
  it("contains all review statuses", () => {
    expect(Object.values(ReviewStatus)).toEqual([
      "REVIEW_NOT_APPLICABLE",
      "REVIEW_PENDING",
      "CHANGES_REQUESTED",
      "APPROVED",
    ]);
  });
});

describe("ChessPriority enum", () => {
  it("contains all six chess pieces", () => {
    expect(Object.values(ChessPriority)).toHaveLength(6);
    expect(ChessPriority.KING).toBe("KING");
    expect(ChessPriority.PAWN).toBe("PAWN");
  });
});

describe("CompletionStatus enum", () => {
  it("contains reconciliation statuses", () => {
    expect(Object.values(CompletionStatus)).toEqual([
      "DONE",
      "PARTIALLY",
      "NOT_DONE",
      "DROPPED",
    ]);
  });
});

describe("LockType enum", () => {
  it("distinguishes on-time from late lock", () => {
    expect(LockType.ON_TIME).toBe("ON_TIME");
    expect(LockType.LATE_LOCK).toBe("LATE_LOCK");
  });
});

describe("CommitCategory enum", () => {
  it("contains expected categories", () => {
    const values = Object.values(CommitCategory);
    expect(values).toContain("DELIVERY");
    expect(values).toContain("TECH_DEBT");
    expect(values).toHaveLength(7);
  });
});
