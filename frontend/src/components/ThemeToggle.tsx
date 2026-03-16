import React from "react";
import { useTheme } from "../theme/ThemeContext.js";
import styles from "./ThemeToggle.module.css";

/**
 * Circular icon button that toggles between dark and light themes.
 * Renders a sun SVG in dark mode, a moon SVG in light mode.
 */
export const ThemeToggle: React.FC = () => {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      data-testid="theme-toggle"
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      className={styles.button}
      onClick={toggleTheme}
    >
      <span className={styles.iconStack} aria-hidden="true">
        <svg
          data-testid="theme-toggle-icon-sun"
          className={`${styles.icon} ${isDark ? styles.iconVisible : styles.iconHidden}`}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <circle cx="12" cy="12" r="5" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
        <svg
          data-testid="theme-toggle-icon-moon"
          className={`${styles.icon} ${isDark ? styles.iconHidden : styles.iconVisible}`}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      </span>
    </button>
  );
};
