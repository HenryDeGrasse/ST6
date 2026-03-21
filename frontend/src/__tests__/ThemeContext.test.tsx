import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import React from "react";
import { ThemeProvider, useTheme } from "../theme/ThemeContext.js";

// ─── localStorage mock ────────────────────────────────────────────────────────

const localStorageMock = (() => {
  let store: Map<string, string> = new Map();
  return {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, value);
    }),
    removeItem: vi.fn((key: string) => {
      store.delete(key);
    }),
    clear: vi.fn(() => {
      store = new Map();
    }),
  };
})();

Object.defineProperty(globalThis, "localStorage", {
  value: localStorageMock,
  writable: true,
});

// ─── Helper components ────────────────────────────────────────────────────────

const ThemeConsumer: React.FC = () => {
  const { theme, toggleTheme } = useTheme();
  return (
    <div>
      <span data-testid="current-theme">{theme}</span>
      <button data-testid="toggle-btn" onClick={toggleTheme}>
        Toggle
      </button>
    </div>
  );
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("ThemeContext", () => {
  beforeEach(() => {
    localStorageMock.clear();
    localStorageMock.getItem.mockClear();
    localStorageMock.setItem.mockClear();
  });

  it("defaults to dark theme", () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );
    expect(screen.getByTestId("current-theme")).toHaveTextContent("dark");
  });

  it("toggleTheme switches internal state from dark to light", () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );

    expect(screen.getByTestId("current-theme")).toHaveTextContent("dark");

    act(() => {
      screen.getByTestId("toggle-btn").click();
    });

    expect(screen.getByTestId("current-theme")).toHaveTextContent("light");
  });

  it("toggleTheme switches internal state from light back to dark", () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );

    // Toggle to light
    act(() => {
      screen.getByTestId("toggle-btn").click();
    });
    expect(screen.getByTestId("current-theme")).toHaveTextContent("light");

    // Toggle back to dark
    act(() => {
      screen.getByTestId("toggle-btn").click();
    });
    expect(screen.getByTestId("current-theme")).toHaveTextContent("dark");
  });

  it("wrapper div has wc-theme className (single Academia theme)", () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );
    const root = screen.getByTestId("wc-theme-root");
    expect(root.className).toContain("wc-theme");
  });

  it("wrapper div always uses wc-theme regardless of toggle state", () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );
    const root = screen.getByTestId("wc-theme-root");
    expect(root.className).toBe("wc-theme");

    act(() => {
      screen.getByTestId("toggle-btn").click();
    });

    // Class stays the same — single theme, no dark/light suffix
    expect(root.className).toBe("wc-theme");
  });

  it("provides toggleTheme and theme via useTheme hook", () => {
    render(
      <ThemeProvider>
        <ThemeConsumer />
      </ThemeProvider>,
    );

    expect(screen.getByTestId("current-theme")).toBeInTheDocument();
    expect(screen.getByTestId("toggle-btn")).toBeInTheDocument();
  });

  it("throws when useTheme is used outside ThemeProvider", () => {
    const spy = vi.spyOn(console, "error").mockImplementation(() => undefined);

    expect(() => render(<ThemeConsumer />)).toThrow("useTheme must be used within a ThemeProvider");

    spy.mockRestore();
  });
});
