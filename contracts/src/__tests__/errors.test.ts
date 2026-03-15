import { describe, it, expect } from "vitest";
import { ErrorCode, ERROR_HTTP_STATUS } from "../errors.js";

describe("ErrorCode enum", () => {
  it("contains all PRD Appendix A error codes", () => {
    const codes = Object.keys(ErrorCode);
    expect(codes).toContain("MISSING_IDEMPOTENCY_KEY");
    expect(codes).toContain("UNAUTHORIZED");
    expect(codes).toContain("FORBIDDEN");
    expect(codes).toContain("NOT_FOUND");
    expect(codes).toContain("CONFLICT");
    expect(codes).toContain("FIELD_FROZEN");
    expect(codes).toContain("PLAN_NOT_IN_DRAFT");
    expect(codes).toContain("CARRY_FORWARD_ALREADY_EXECUTED");
    expect(codes).toContain("VALIDATION_ERROR");
    expect(codes).toContain("MISSING_CHESS_PRIORITY");
    expect(codes).toContain("MISSING_RCDO_OR_REASON");
    expect(codes).toContain("CONFLICTING_LINK");
    expect(codes).toContain("CHESS_RULE_VIOLATION");
    expect(codes).toContain("MISSING_DELTA_REASON");
    expect(codes).toContain("MISSING_COMPLETION_STATUS");
    expect(codes).toContain("INVALID_WEEK_START");
    expect(codes).toContain("PAST_WEEK_CREATION_BLOCKED");
    expect(codes).toContain("RCDO_VALIDATION_STALE");
    expect(codes).toContain("IDEMPOTENCY_KEY_REUSE");
    expect(codes).toContain("INTERNAL_SERVER_ERROR");
    expect(codes).toContain("SERVICE_UNAVAILABLE");
  });

  it("has 21 error codes total", () => {
    // 1 (400) + 1 (401) + 1 (404) + 1 (403) + 4 (409) + 11 (422) + 1 (500) + 1 (503) = 21
    expect(Object.keys(ErrorCode)).toHaveLength(21);
  });
});

describe("ERROR_HTTP_STATUS mapping", () => {
  it("maps every ErrorCode to an HTTP status", () => {
    for (const code of Object.values(ErrorCode)) {
      expect(ERROR_HTTP_STATUS[code]).toBeDefined();
      expect(typeof ERROR_HTTP_STATUS[code]).toBe("number");
    }
  });

  it("maps 400 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.MISSING_IDEMPOTENCY_KEY]).toBe(400);
  });

  it("maps 401 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.UNAUTHORIZED]).toBe(401);
  });

  it("maps 404 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.NOT_FOUND]).toBe(404);
  });

  it("maps 403 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.FORBIDDEN]).toBe(403);
  });

  it("maps all 409 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.CONFLICT]).toBe(409);
    expect(ERROR_HTTP_STATUS[ErrorCode.FIELD_FROZEN]).toBe(409);
    expect(ERROR_HTTP_STATUS[ErrorCode.PLAN_NOT_IN_DRAFT]).toBe(409);
    expect(ERROR_HTTP_STATUS[ErrorCode.CARRY_FORWARD_ALREADY_EXECUTED]).toBe(409);
  });

  it("maps all 422 codes correctly", () => {
    const codes422 = [
      ErrorCode.VALIDATION_ERROR,
      ErrorCode.MISSING_CHESS_PRIORITY,
      ErrorCode.MISSING_RCDO_OR_REASON,
      ErrorCode.CONFLICTING_LINK,
      ErrorCode.CHESS_RULE_VIOLATION,
      ErrorCode.MISSING_DELTA_REASON,
      ErrorCode.MISSING_COMPLETION_STATUS,
      ErrorCode.INVALID_WEEK_START,
      ErrorCode.PAST_WEEK_CREATION_BLOCKED,
      ErrorCode.RCDO_VALIDATION_STALE,
      ErrorCode.IDEMPOTENCY_KEY_REUSE,
    ];
    for (const code of codes422) {
      expect(ERROR_HTTP_STATUS[code]).toBe(422);
    }
  });

  it("maps 500 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.INTERNAL_SERVER_ERROR]).toBe(500);
  });

  it("maps 503 codes correctly", () => {
    expect(ERROR_HTTP_STATUS[ErrorCode.SERVICE_UNAVAILABLE]).toBe(503);
  });
});
