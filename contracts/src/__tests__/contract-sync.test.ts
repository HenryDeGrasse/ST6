import { describe, it, expect } from "vitest";
import {
  PlanState,
  ReviewStatus,
  ChessPriority,
  CompletionStatus,
  LockType,
  CommitCategory,
  ErrorCode,
  ERROR_HTTP_STATUS,
  EventType,
  AggregateType,
  NotificationType,
} from "../index.js";

/**
 * Module-boundary tests: verify that the shared contract package
 * exports everything that frontend and backend need to stay in sync.
 * If these fail, it means the contracts package is missing exports
 * and the frontend/backend will drift.
 */

describe("contracts public API completeness", () => {
  it("exports all enum types", () => {
    // Each enum should be importable and have the expected member count
    expect(Object.values(PlanState)).toHaveLength(5);
    expect(Object.values(ReviewStatus)).toHaveLength(4);
    expect(Object.values(ChessPriority)).toHaveLength(6);
    expect(Object.values(CompletionStatus)).toHaveLength(4);
    expect(Object.values(LockType)).toHaveLength(2);
    expect(Object.values(CommitCategory)).toHaveLength(7);
    expect(Object.keys(ErrorCode)).toHaveLength(21);
    expect(Object.values(EventType)).toHaveLength(10);
    expect(Object.values(AggregateType)).toHaveLength(3);
    expect(Object.values(NotificationType)).toHaveLength(5);
  });

  it("exports ERROR_HTTP_STATUS with entries for every ErrorCode", () => {
    const errorCodeValues = Object.values(ErrorCode);
    for (const code of errorCodeValues) {
      expect(ERROR_HTTP_STATUS).toHaveProperty(code);
    }
  });

  it("PlanState values match backend enum names exactly", () => {
    // These must match the Java PlanState enum in com.weekly.plan.domain
    const expected = ["DRAFT", "LOCKED", "RECONCILING", "RECONCILED", "CARRY_FORWARD"];
    expect(Object.values(PlanState)).toEqual(expected);
  });

  it("ReviewStatus values match backend enum names exactly", () => {
    const expected = ["REVIEW_NOT_APPLICABLE", "REVIEW_PENDING", "CHANGES_REQUESTED", "APPROVED"];
    expect(Object.values(ReviewStatus)).toEqual(expected);
  });

  it("ChessPriority values match backend enum names exactly", () => {
    const expected = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
    expect(Object.values(ChessPriority)).toEqual(expected);
  });

  it("CompletionStatus values match backend enum names exactly", () => {
    const expected = ["DONE", "PARTIALLY", "NOT_DONE", "DROPPED"];
    expect(Object.values(CompletionStatus)).toEqual(expected);
  });

  it("CommitCategory values match backend enum names exactly", () => {
    const expected = ["DELIVERY", "OPERATIONS", "CUSTOMER", "GTM", "PEOPLE", "LEARNING", "TECH_DEBT"];
    expect(Object.values(CommitCategory)).toEqual(expected);
  });

  it("ErrorCode values match backend enum names exactly", () => {
    const expected = [
      "MISSING_IDEMPOTENCY_KEY",
      "UNAUTHORIZED",
      "NOT_FOUND",
      "FORBIDDEN",
      "CONFLICT",
      "FIELD_FROZEN",
      "PLAN_NOT_IN_DRAFT",
      "CARRY_FORWARD_ALREADY_EXECUTED",
      "VALIDATION_ERROR",
      "MISSING_CHESS_PRIORITY",
      "MISSING_RCDO_OR_REASON",
      "CONFLICTING_LINK",
      "CHESS_RULE_VIOLATION",
      "MISSING_DELTA_REASON",
      "MISSING_COMPLETION_STATUS",
      "INVALID_WEEK_START",
      "PAST_WEEK_CREATION_BLOCKED",
      "RCDO_VALIDATION_STALE",
      "IDEMPOTENCY_KEY_REUSE",
      "INTERNAL_SERVER_ERROR",
      "SERVICE_UNAVAILABLE",
    ];
    expect(Object.values(ErrorCode)).toEqual(expected);
  });

  it("EventType values match backend event type strings exactly", () => {
    const expected = [
      "plan.created", "plan.locked", "plan.reconciliation_started",
      "plan.reconciled", "plan.carry_forward", "review.submitted",
      "commit.created", "commit.updated", "commit.deleted",
      "commit.actual_updated",
    ];
    expect(Object.values(EventType)).toEqual(expected);
  });
});
