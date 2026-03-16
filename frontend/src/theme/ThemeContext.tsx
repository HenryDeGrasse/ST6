/**
 * ThemeContext – Academia / Classical single-theme provider.
 *
 * CRITICAL for Module Federation:
 *   tokens.css and global.css are imported here (not in main.tsx) so they are
 *   included in the exported module graph:
 *     index.ts → App.tsx → ThemeProvider → ThemeContext.tsx
 *   This guarantees styles load in both standalone dev and PA-host MFE contexts.
 */
import "./tokens.css";
import "./global.css";

import React, {
  createContext,
  useCallback,
  useContext,
  useState,
} from "react";

// ─── Types ────────────────────────────────────────────────────────────────────

export type Theme = "dark" | "light";

export interface ThemeContextValue {
  theme: Theme;
  toggleTheme: () => void;
}

// ─── Context ──────────────────────────────────────────────────────────────────

const ThemeContext = createContext<ThemeContextValue | null>(null);

// ─── Provider ─────────────────────────────────────────────────────────────────

export interface ThemeProviderProps {
  children: React.ReactNode;
  /** @deprecated Single-theme. Retained for API compatibility. */
  initialTheme?: Theme;
}

/**
 * Provides the Academia theme. The dark/light toggle API is retained
 * for backward compatibility but has no visual effect — all tokens
 * live on `.wc-theme` without a mode suffix.
 */
export const ThemeProvider: React.FC<ThemeProviderProps> = ({
  children,
}) => {
  const [theme, setTheme] = useState<Theme>("dark");

  const toggleTheme = useCallback(() => {
    setTheme((prev) => (prev === "dark" ? "light" : "dark"));
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <div
        className="wc-theme"
        data-testid="wc-theme-root"
        style={{ position: "relative", minHeight: "100vh" }}
      >
        {children}
      </div>
    </ThemeContext.Provider>
  );
};

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return ctx;
}
