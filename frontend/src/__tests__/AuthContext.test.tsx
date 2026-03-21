import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { AuthProvider, useAuth } from "../context/AuthContext.js";
import React from "react";

const TestConsumer: React.FC = () => {
  const { user, token, getToken } = useAuth();
  return (
    <div>
      <span data-testid="user-id">{user.userId}</span>
      <span data-testid="display-name">{user.displayName}</span>
      <span data-testid="token">{token}</span>
      <span data-testid="get-token">{getToken()}</span>
    </div>
  );
};

describe("AuthContext", () => {
  const testUser = {
    userId: "user-123",
    orgId: "org-456",
    displayName: "Test User",
    roles: ["IC"],
    timezone: "UTC",
  };

  it("provides user and token to consumers", () => {
    render(
      <AuthProvider user={testUser} token="my-jwt">
        <TestConsumer />
      </AuthProvider>,
    );
    expect(screen.getByTestId("user-id")).toHaveTextContent("user-123");
    expect(screen.getByTestId("display-name")).toHaveTextContent("Test User");
    expect(screen.getByTestId("token")).toHaveTextContent("my-jwt");
    expect(screen.getByTestId("get-token")).toHaveTextContent("my-jwt");
  });

  it("throws when used outside AuthProvider", () => {
    // Suppress React error boundary console output
    const spy = vi.spyOn(console, "error").mockImplementation(() => undefined);

    expect(() => render(<TestConsumer />)).toThrow("useAuth must be used within an AuthProvider");

    spy.mockRestore();
  });
});
